# Identity 서비스 개발 문서

- 작성일: 2026-10-14
- 이슈: #2 (OAuth2.0 로그인 기능 개발), #5 (로그인 로직 변경: 인가 코드 로그인 추가), #10 (온보딩)
- 테스트: `./gradlew build` 기준 90건 통과, 실패 0건

## 목차
1. [개요](#1-개요)
2. [프로젝트 구조와 규칙](#2-프로젝트-구조와-규칙)
3. [구현 상세](#3-구현-상세)
4. [API 명세](#4-api-명세)
5. [설정](#5-설정)
6. [테스트와 검증](#6-테스트와-검증)
7. [운영 시 알아 둘 점](#7-운영-시-알아-둘-점)
---

## 1. 개요

Google·Kakao 소셜 로그인만 사용하는 회원 체계다. 현재 앱(Expo Go)은 WebView로 받은 **인가 코드**를 서버에 보내고, 서버가 client secret으로 코드를 **OIDC ID Token**으로 바꿔 검증한 뒤 바로핏 토큰(Access Token, Refresh Token)을 발급한다. SDK로 받은 ID Token을 직접 보내는 방식도 지원한다.

| 기능 | 위치 |
|---|---|
| 소셜 로그인과 자동 가입(인가 코드, 현재 앱) | `POST /api/v1/auth/social-login/code` |
| 소셜 로그인과 자동 가입(ID Token, SDK) | `POST /api/v1/auth/social-login` |
| 토큰 갱신(회전, 재사용 탐지) | `POST /api/v1/auth/refresh` |
| 로그아웃(현재 기기 세션 폐기) | `POST /api/v1/auth/logout` |
| 회원 탈퇴(멱등) | `DELETE /api/v1/users/me` |
| 공개키 노출 | `GET /.well-known/jwks.json` |
| 온보딩(닉네임 확정, 신체 정보·운동 성향 저장) | `POST /api/v1/users/me/onboarding` |
| 닉네임 랜덤 생성·중복 확인 | `GET /api/v1/users/nicknames/random`, `/availability` |
| 내 정보 조회 | `GET /api/v1/users/me` |
| 다른 도메인용 사용자 조회 | `UserQueryApi.findSummary`, `findSummaries` |
| 만료 토큰 정리 | 매일 04:00 배치 |

**범위 밖:** 회원 정보·프로필 수정 API, 온보딩 후 플랜 생성(루틴 서비스 역할). 앱 연동은 [onboarding-api.md](onboarding-api.md)를 본다.

---

## 2. 프로젝트 구조와 규칙

### 2.1 구조
인증은 `services/identity`, 루틴은 `services/routine`에 각각 실행 앱과 구현 코드를 함께 둔다. 두 서비스는 독립 컨테이너로 배포한다. PostgreSQL은 하나를 공유하고 스키마만 분리한다.

```
backend_baro/
├── services/
│   ├── identity/       # 실행 앱, 인증 API·도메인, identity 마이그레이션
│   └── routine/        # 실행 앱, 루틴 API·도메인, routine 마이그레이션
├── shared/
│   ├── common/         # 공통 에러 응답, ModuleSchema
│   └── runtime/        # 공용 JWT 검증, Flyway 실행
├── integration-tests/  # 두 서비스의 기존 통합 회귀 테스트 (배포 제외)
├── deploy/             # 게이트웨이
├── scripts/
├── compose.yaml
└── Dockerfile
```

처음에는 identity, identity-api, profile, profile-api 4개 모듈이었다. 도메인이 늘면 모듈 수가 두 배가 되고, profile은 `onboarded`를 바꾸는 역할뿐이라 `identity-service` 하나로 합쳤다. `-api` 모듈은 `api` 패키지가 됐고, profile의 이벤트(`ProfileCompleted`)는 `completeOnboarding` 직접 호출로 바뀌었다.

### 2.2 의존 규칙 (ArchUnit `ArchitectureTests`로 검사)
- `common`은 도메인 모듈에 의존하지 않는다.
- 다른 도메인은 상대 서비스의 `api` 패키지(`com.barofit.{도메인}.api`)만 참조한다. Gradle 모듈로 나누지 않아 컴파일러가 막지 못하므로 테스트가 막는다. 위반 클래스를 일부러 넣어 실제로 잡히는 것을 확인했다.
- `api` 패키지는 JDK와 다른 도메인의 `api` 패키지에만 의존한다(`common`도 참조하지 않음). 서비스 분리 후 언어 독립 계약으로 바꾸기 위해서다.
- 다른 schema를 참조하는 FK, 네이티브 쿼리, JPQL은 쓰지 않는다(코드 리뷰로 확인).

### 2.3 API 경로 규칙 (README에 정의)
게이트웨이가 경로 prefix로 독립 서비스에 라우팅한다. 그래서 `/api/v1/{리소스}`의 prefix는 **한 도메인만 소유**하고, 다른 도메인의 prefix 아래에 API를 만들지 않는다. identity가 소유한 prefix는 `/api/v1/auth/**`, `/api/v1/users/**`, `/.well-known/jwks.json`이다.

---

## 3. 구현 상세

### 3.1 인프라
- **postgres 이미지 태그의 기준은 `compose.yaml` 하나다.** 루트 `build.gradle`이 `image: postgres:...`를 읽어 테스트에 시스템 프로퍼티로 넘긴다. 찾지 못하면 빌드가 실패한다.
- **모듈별 Flyway:** 각 모듈이 `ModuleSchema`를 Bean으로 등록하면 각 실행 앱이 포함한 shared/runtime의 `ModuleMigrations`가 모듈마다 Flyway 인스턴스를 따로 실행한다. JPA는 마이그레이션이 끝난 뒤 만든다. Spring Boot Flyway 자동설정 모듈은 넣지 않는다.
- `ddl-auto: validate` 고정. 스키마는 Flyway만 변경한다.
- 테스트는 Testcontainers PostgreSQL(H2 없음). Docker 이미지는 멀티스테이지 빌드, uid 1001로 실행한다.
- 모든 모듈에 `-parameters` 컴파일 옵션을 준다(`@Query`의 named parameter에 필요).

### 3.2 공통 에러 형식
RFC 9457 Problem Details에 `code`를 더한다. 각 도메인은 `ErrorCode`를 구현한 enum으로 자기 코드를 가진다. `GlobalExceptionHandler`는 Spring MVC 표준 예외(400, 404, 405)에도 `code`를 붙이고, 자동설정(`AutoConfiguration.imports`)으로 등록한다. 보안 필터의 인증 실패도 같은 핸들러를 거쳐 같은 형식으로 응답한다.

### 3.3 테이블 (`identity` schema, `V1__create_identity_tables.sql`)

| 테이블 | 주요 컬럼 | 제약 |
|---|---|---|
| `users` | `id`(UUIDv7, 앱에서 생성), `nickname`, `profile_image_url`, `role`, `status`, `onboarded`, `last_login_at`, `withdrawn_at` | role: USER/ADMIN, status: ACTIVE/WITHDRAWN |
| `social_accounts` | `user_id`, `provider`, `provider_user_id` | `(provider, provider_user_id)` UNIQUE. 동시 가입 판정에 사용 |
| `refresh_tokens` | `user_id`, `session_id`, `token_hash`(SHA-256 hex), `expires_at`(30일 슬라이딩), `session_expires_at`(90일 절대), `revoked_at` | `token_hash` UNIQUE |
| `user_profiles` (V2) | `user_id`(PK·FK), `gender`, `birth_year`(나이로 입력받아 변환), `height_cm`, `weight_kg`, `goal`, `experience_level`, `weekly_workout_days`, `coaching_style` | 값 범위·enum CHECK. 탈퇴 시 삭제 |
| `user_profile_discomfort_areas` (V2) | `user_id`, `area` | PK `(user_id, area)`, 프로필 삭제 시 CASCADE. 해당 없음이면 행 없음 |

- schema 내부에는 FK를 건다.
- 닉네임 유니크: 부분 유니크 인덱스 `uk_users_nickname_onboarded`(`lower(nickname)`, `onboarded AND status='ACTIVE'`). 온보딩 전 SNS 닉네임끼리는 겹칠 수 있고, 탈퇴하면 닉네임이 지워져 반납된다.
- 닉네임과 프로필 사진은 **가입할 때만** SNS 값으로 채운다. 닉네임은 100자를 넘으면 자르고, 사진 URL은 2048자를 넘으면 저장하지 않는다.

### 3.4 바로핏 JWT 발급
- Access Token: RS256, 헤더에 `kid`, 수명 15분. claim은 `iss=barofit-identity`, `aud=barofit-api`, `sub`(사용자 UUID), `iat`, `exp`, `jti`, `role`. 개인정보는 넣지 않는다.
- 키는 파일로 받는다. 개인키(PKCS#8 PEM), 공개키 목록(JWK Set), 현재 kid를 받는다. 기동할 때 개인키와 현재 kid의 공개키가 짝인지 검증하고, 아니면 기동하지 않는다.
- 공개키 목록에 이전 키와 새 키를 함께 둘 수 있어 무중단으로 키를 교체할 수 있다.
- 로컬은 `scripts/generate-jwt-keys.sh`로 gitignore된 `keys/`에 만든다. Docker는 `keys/`를 `/run/secrets/jwt`에 읽기 전용으로 마운트한다. 테스트는 메모리에서 키를 만든다. **키는 어떤 환경에서도 저장소에 커밋하지 않는다.**
- Refresh Token은 256bit 랜덤 값(base64url, 43자)이다. DB에는 SHA-256 해시만 저장한다.

### 3.5 바로핏 JWT 검증
- Spring Security Resource Server를 쓴다. 서명, `exp`(오차 60초), `iat`가 미래가 아닌지, `iss`, `aud`를 검증한다.
- 401을 **만료(`AUTH_TOKEN_EXPIRED`)와 그 외(`AUTH_INVALID_TOKEN`)로 나눈다.** 앱은 만료일 때만 refresh한다. 판정은 예외 메시지가 아닌 검증기의 에러 코드로 한다.
- **공개 경로(`/api/v1/auth/**`, `/.well-known/jwks.json`)에서는 Authorization 헤더를 무시한다.** 만료된 Access Token이 붙은 채로 refresh해도 401이 나지 않게 하기 위해서다.
- `/actuator/health/**`, `/error`는 인증 없이 연다.
- `role` claim은 `ROLE_USER` 같은 권한으로 바꾼다.

### 3.6 소셜 ID Token 검증

| provider | 허용 iss | aud 설정 | 공개키 |
|---|---|---|---|
| Google | `accounts.google.com`, `https://accounts.google.com` | `GOOGLE_CLIENT_IDS` | `https://www.googleapis.com/oauth2/v3/certs` |
| Kakao | `https://kauth.kakao.com` | `KAKAO_APP_KEYS` | `https://kauth.kakao.com/.well-known/jwks.json` |

- 서명, `exp`(오차 60초), `iss`, `aud`, `sub`를 확인한다. 닉네임과 사진 claim은 Google이 `name`/`picture`, Kakao가 `nickname`/`picture`다.
- aud 목록이 비어 있는 provider는 **모든 토큰을 거부한다.** 서버는 정상 기동하고 경고 로그만 남긴다.
- 공개키 조회 타임아웃은 연결 3초, 읽기 5초다. 조회 실패는 503 `AUTH_SOCIAL_PROVIDER_UNAVAILABLE`, 토큰 문제는 401 `AUTH_INVALID_ID_TOKEN`이다.
- 소셜 디코더는 Bean으로 등록하지 않는다. Bean이 되면 Resource Server가 바로핏 JWT 검증에 잘못 쓸 수 있다. `JwtDecoder` Bean이 하나뿐인지 테스트로 확인한다.
- SDK 방식(ID Token 직접 전달)은 nonce를 검증하지 않는다. 인가 코드 방식은 앱이 만든 nonce를 ID Token의 nonce와 대조한다.
- Kakao `picture`가 `http://`이면 `https://`로 바꿔 저장한다(iOS가 http 이미지를 막음, 같은 CDN이 https 제공).

### 3.7 소셜 로그인과 가입
로그인 방식은 두 가지이고, 3번부터는 같은 로직을 쓴다.

| 방식 | API | 앱 | 쓰는 곳 |
|---|---|---|---|
| **인가 코드**(현재 앱) | `POST /api/v1/auth/social-login/code` | WebView로 카카오·구글 로그인 → `code` 전달 | Expo Go |
| ID Token(SDK) | `POST /api/v1/auth/social-login` | 네이티브 SDK로 받은 `idToken` 전달 | 개발 빌드 전환 시 |

**인가 코드 방식**
- 서버가 `redirectUri`가 허용 목록(`SOCIAL_REDIRECT_URIS`)에 있는지 확인한다. 없으면 400이고, 코드 교환을 하지 않는다.
- 서버가 client secret으로 `code`를 provider 토큰 엔드포인트에서 교환해 `id_token`을 받는다. 타임아웃은 연결 3초, 읽기 5초이고, 코드와 시크릿은 로그에 남기지 않는다.
- `id_token`을 3.6과 같이 검증하고, 앱이 보낸 `nonce`와 같은지 확인한다. 교환에 쓴 클라이언트 ID(Kakao REST API 키, Google 웹 클라이언트 ID)는 `aud`로 자동 허용된다.
- 교환 실패 처리: `invalid_grant`(만료·재사용된 코드, redirect_uri 불일치)는 401 `AUTH_INVALID_AUTHORIZATION_CODE`. `invalid_client`(시크릿 오류)·5xx·타임아웃·`id_token` 없음(OIDC 꺼짐)·설정 누락은 503 `AUTH_SOCIAL_PROVIDER_UNAVAILABLE`과 서버 ERROR/WARN 로그.

**공통**
1. ID Token을 검증한다. 네트워크 호출이 있어 트랜잭션 밖에서 한다.
2. `(provider, sub)`로 연결된 계정이 있으면 로그인한다. 마지막 로그인 시각을 갱신하고 새 세션을 만든다.
3. 없으면 가입한다. 사용자와 소셜 계정을 저장하고 `UserRegistered`를 발행한다.
4. **동시 최초 가입:** 같은 신규 사용자의 요청이 동시에 오면 한쪽은 UNIQUE 위반이 난다. 바깥에서 예외를 잡고 **새 트랜잭션에서 한 번 다시 조회해** 로그인으로 처리한다.
5. 로그인할 때마다 새 세션(`session_id`)을 만든다. 여러 기기에서 동시에 로그인할 수 있다.

### 3.8 Refresh (회전)
1. 조건부 UPDATE(`revoked_at`이 비어 있고 만료 전인 행만 폐기)를 실행한다. 영향 행이 1이면 이 요청이 회전에 성공한 것이다.
2. 성공하면 사용자 상태를 확인한 뒤 같은 세션으로 새 토큰을 발급한다. 슬라이딩 만료는 30일로 다시 계산하고, 절대 만료(90일)는 이어받는다. 실제 만료는 둘 중 이른 쪽이다.
3. 0행이고 토큰이 이미 폐기된 상태면 **재사용**으로 보고 세션 전체를 폐기한다(`AUTH_SESSION_REVOKED`). 로그아웃한 토큰도 같다.
4. 그 외(없는 토큰, 만료)는 `AUTH_INVALID_REFRESH`다.
5. 실패는 결과 값으로 반환하고, 트랜잭션 커밋 뒤 컨트롤러에서 401로 바꾼다. 그래서 세션 폐기가 롤백되지 않는다.

### 3.9 Logout
요청 body의 Refresh Token으로 세션을 찾아 폐기한다. 토큰 존재 여부를 알려 주지 않도록 결과와 상관없이 204를 응답한다.

### 3.10 탈퇴
- 사용자 행은 지우지 않고 익명화한다. 닉네임과 사진을 지우고, status=WITHDRAWN으로 바꾸고, `withdrawn_at`을 기록한다.
- 소셜 계정 연결을 삭제한다. 같은 SNS로 다시 로그인하면 **신규 가입**이 된다.
- Refresh Token을 모두 폐기한다. 이미 발급된 Access Token은 만료(최대 15분)까지 유효하다.
- `UserWithdrawn`을 발행한다. 다른 도메인은 커밋 이후(AFTER_COMMIT) 새 트랜잭션(REQUIRES_NEW)에서 자기 데이터를 정리하고, 실패하면 ERROR 로그를 남긴다. 사용자 프로필은 같은 도메인이므로 나중에 탈퇴 트랜잭션 안에서 함께 지운다.
- 이미 탈퇴했어도 204다(멱등). 응답이 유실돼 앱이 재시도해도 성공해야 SDK 연결 해제를 건너뛰지 않는다.

### 3.11 온보딩, 정리 배치, 사용자 조회
- **온보딩:** `ProfileService.onboard`가 한 트랜잭션에서 닉네임 확정, 프로필 저장, `UserAccountService.completeOnboarding(userId)`(`MANDATORY`) 호출을 한다.
  - 사용자 행을 `SELECT ... FOR UPDATE`로 잠가 같은 사용자의 동시 요청을 직렬화한다. 두 번째 요청은 409 `PROFILE_ALREADY_ONBOARDED`.
  - 닉네임은 미리 조회해 중복이면 409 `PROFILE_NICKNAME_DUPLICATED`. 다른 사용자와 동시에 같은 닉네임을 확정하면 flush 시 `uk_users_nickname_onboarded` 위반만 같은 코드로 바꾸고, 다른 제약 위반은 그대로 던진다.
  - 나이는 한국 시간 기준 올해에서 빼 `birth_year`로 저장하고, 응답할 때 다시 나이로 계산한다.
  - 닉네임 규칙(`NicknamePolicy`): 2~10자, 한글(완성형)·영문·숫자. 랜덤 닉네임(`RandomNicknameGenerator`)은 형용사+명사+숫자(1~9999)로 항상 규칙을 만족하고, 사용 중이면 최대 10회 다시 뽑는다. 예약하지 않는다.
  - 운동 경험 `experience_level`은 루틴 `Difficulty`(BEGINNER/INTERMEDIATE/ADVANCED)에 `NOVICE`(입문자)를 더한 값이다. 서비스 간 코드 의존은 없다.
- **탈퇴와 프로필:** 탈퇴 트랜잭션 안에서 프로필(불편 부위 포함)을 삭제한다.
- **정리 배치:** 매일 04:00(서버 시간대)에 절대 만료가 지난 Refresh Token 행을 지운다. 폐기된 행을 바로 지우면 재사용 탐지가 동작하지 않으므로 절대 만료 기준으로만 지운다.
- **사용자 조회:** `findSummary(userId)`는 `Optional<UserSummary(id, nickname, profileImageUrl)>`를 돌려준다. 목록처럼 여러 명이 필요하면 `findSummaries(userIds)`로 한 번에 조회한다. 결과는 ID를 키로 하는 Map이고, 탈퇴했거나 없는 사용자는 빠진다. 서비스 분리 후 HTTP 호출 한 번으로 바뀌는 자리라 단건 조회를 반복하지 않는다.

---

## 4. API 명세

| Method | Path | 인증 | 성공 |
|---|---|---|---|
| POST | `/api/v1/auth/social-login/code` | 없음 | 200 |
| POST | `/api/v1/auth/social-login` | 없음 | 200 |
| POST | `/api/v1/auth/refresh` | 없음 | 200 |
| POST | `/api/v1/auth/logout` | 없음 | 204 |
| DELETE | `/api/v1/users/me` | Access Token | 204 |
| GET | `/api/v1/users/me` | Access Token | 200 |
| POST | `/api/v1/users/me/onboarding` | Access Token | 201 |
| GET | `/api/v1/users/nicknames/random` | Access Token | 200 |
| GET | `/api/v1/users/nicknames/availability` | Access Token | 200 |
| GET | `/.well-known/jwks.json` | 없음 | 200 |

온보딩·내 정보·닉네임 API의 요청/응답과 앱 흐름은 [onboarding-api.md](onboarding-api.md)에 있다.

### 4.1 공통 규칙

**형식:** 요청과 응답은 `application/json`(UTF-8)이다. 로컬 Base URL은 `http://localhost:3002`다.

**인증:** 보호된 API는 `Authorization: Bearer {accessToken}` 헤더가 필요하다. `/api/v1/auth/**`와 `/.well-known/jwks.json`은 인증이 없고, 이 경로에서는 `Authorization` 헤더를 무시한다.

**토큰**

| 구분 | 형식 | 수명 |
|---|---|---|
| Access Token | JWT(RS256), 헤더에 `kid` | 15분(서버 간 시계 오차 60초 허용) |
| Refresh Token | 불투명 문자열(base64url, 43자) | 마지막 갱신 후 30일, 최초 로그인 후 최대 90일 중 이른 시각 |

Access Token claim(앱은 해석할 필요 없음): `iss`=`barofit-identity`, `aud`=`barofit-api`, `sub`=사용자 UUID, `role`=`USER`/`ADMIN`, `iat`/`exp`=epoch 초, `jti`=UUID.

**시각:** ISO 8601 UTC 문자열이다. 예: `"2026-10-14T05:19:54.964864Z"`. 소수점 이하 자릿수는 고정이 아니다.

**에러 응답:** RFC 9457 Problem Details + `code`이고, `Content-Type`은 `application/problem+json`이다.

```json
{
  "title": "Unauthorized",
  "status": 401,
  "detail": "로그인 세션이 만료되었습니다. 다시 로그인해 주세요.",
  "instance": "/api/v1/auth/refresh",
  "code": "AUTH_INVALID_REFRESH"
}
```

| 필드 | 설명 |
|---|---|
| `code` | **앱은 이 값으로 분기한다** ([4.7](#47-에러-코드)) |
| `status`, `title` | HTTP 상태 코드와 이름 |
| `detail` | 사람이 읽는 메시지. 문구가 바뀔 수 있어 분기에 쓰지 않는다 |
| `instance` | 요청 경로 |
| `errors` | 입력 검증 실패(400)일 때만 있다. `[{ "field", "message" }]`. `message`는 서버 Locale에 따라 언어가 바뀐다 |
| `type` | 기본값 `about:blank`라 생략된다 |

입력 검증 실패 예시:
```json
{
  "title": "Bad Request", "status": 400, "detail": "요청 값이 올바르지 않습니다.",
  "instance": "/api/v1/auth/social-login", "code": "INVALID_INPUT",
  "errors": [
    { "field": "idToken", "message": "공백일 수 없습니다" },
    { "field": "provider", "message": "널이어서는 안됩니다" }
  ]
}
```
JSON이 깨졌거나 `provider`에 정의되지 않은 값이 오면 `errors` 없이 400 `INVALID_INPUT`이다.

### 4.2 소셜 로그인 — `POST /api/v1/auth/social-login`
SDK로 받은 **OIDC ID Token**을 검증하고 바로핏 토큰을 발급한다. 연결된 계정이 없으면 자동으로 가입한다. 로그인할 때마다 새 세션이 생기고, 다른 기기의 세션은 유지된다. 닉네임과 사진은 최초 가입할 때만 SNS 정보로 채운다.

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `provider` | string | O | `GOOGLE` 또는 `KAKAO` (대문자) |
| `idToken` | string | O | SDK가 발급한 OIDC ID Token. SDK의 Access Token이 아니다 |

```json
{ "provider": "KAKAO", "idToken": "eyJraWQiOiI5ZjI1MmRhZGQ1ZjIzM2Y5M2QyZmE1MjhkMTJmZWEi..." }
```

**응답 200**

| 필드 | 타입 | 설명 |
|---|---|---|
| `accessToken` | string | 바로핏 Access Token |
| `accessTokenExpiresAt` | string | Access Token 만료 시각 |
| `refreshToken` | string | 바로핏 Refresh Token. Keychain/Keystore에 보관 |
| `isOnboarded` | boolean | 온보딩 완료 여부. `false`면 온보딩 화면 |
| `user.id` | string(UUID) | 사용자 ID |
| `user.nickname` | string \| null | SNS에서 받지 못했으면 `null` |
| `user.profileImageUrl` | string \| null | 외부 URL이라 깨질 수 있다 |
| `user.role` | string | `USER` 또는 `ADMIN` |

```json
{
  "accessToken": "eyJraWQiOiJiYXJvZml0LTIwMjYxMCIsImFsZyI6IlJTMjU2In0...",
  "accessTokenExpiresAt": "2026-10-14T05:19:54.964864Z",
  "refreshToken": "Vk8aMtqzFJ5-pDLRjWowU50bPlZAYslqNJwBwnM-BjI",
  "isOnboarded": false,
  "user": {
    "id": "0199e0a0-0000-7000-8000-000000000001",
    "nickname": "바로",
    "profileImageUrl": "https://k.kakaocdn.net/dn/.../img_640x640.jpg",
    "role": "USER"
  }
}
```
신규 가입 여부는 따로 알려 주지 않는다. 앱은 `isOnboarded`로 화면을 정한다.

**에러**

| 상태 | code | 상황 | 앱 동작 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | 필드 누락, 정의되지 않은 provider | 요청 수정 |
| 401 | `AUTH_INVALID_ID_TOKEN` | 서명·만료·iss·aud 검증 실패. 서버에 해당 provider aud가 설정되지 않은 경우 포함 | SDK 로그인부터 다시 |
| 503 | `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` | Google·Kakao 공개키 조회 실패 | 잠시 후 재시도 |

### 4.2-1 인가 코드 소셜 로그인 — `POST /api/v1/auth/social-login/code`
앱(Expo Go)이 WebView로 카카오·구글 로그인을 하고 받은 **인가 코드**로 로그인한다. 연결된 계정이 없으면 자동으로 가입한다. **응답은 4.2와 같다.**

앱이 여는 로그인 주소(공개 키만 사용):
```
카카오: https://kauth.kakao.com/oauth/authorize?client_id=<REST API 키>&redirect_uri=http://localhost:8081&response_type=code&state=<랜덤>&nonce=<랜덤>
구글:  https://accounts.google.com/o/oauth2/v2/auth?client_id=<웹 클라이언트 ID>&redirect_uri=http://localhost:8081&response_type=code&scope=openid%20profile%20email&prompt=select_account&state=<랜덤>&nonce=<랜덤>
```
- WebView가 `redirect_uri`로 이동하려는 순간 `code`를 가로챈다(페이지를 실제로 열지 않음).
- `state`는 앱이 확인한다(다른 곳에서 시작된 로그인 응답 거부). `nonce`는 서버가 확인한다(ID Token 재사용 방지). 둘 다 로그인 시도마다 새로 만든다.

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `provider` | string | O | `GOOGLE` 또는 `KAKAO` |
| `code` | string | O | redirect로 받은 인가 코드. 한 번만 쓸 수 있고 몇 분 안에 만료된다 |
| `redirectUri` | string | O | 로그인 주소에 쓴 `redirect_uri`와 같은 값. 서버 허용 목록에 있어야 한다 |
| `nonce` | string | O | 로그인 주소에 넣은 `nonce` |

```json
{ "provider": "KAKAO", "code": "Hp53ZOPB8EOwjmtTOAl6Oq...", "redirectUri": "http://localhost:8081", "nonce": "0b6f6c0e-..." }
```

**에러**

| 상태 | code | 상황 | 앱 동작 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | 필드 누락, 허용되지 않은 `redirectUri` | 요청 수정 |
| 401 | `AUTH_INVALID_AUTHORIZATION_CODE` | 코드 만료·재사용, redirect_uri 불일치 | 소셜 로그인부터 다시 |
| 401 | `AUTH_INVALID_ID_TOKEN` | ID Token 검증 실패, nonce 불일치 | 소셜 로그인부터 다시 |
| 503 | `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` | 소셜 서버 장애·타임아웃, 서버의 소셜 설정 오류 | 잠시 후 재시도 |

### 4.3 토큰 갱신 — `POST /api/v1/auth/refresh`
- **Refresh Token은 한 번만 쓸 수 있다.** 성공하면 응답의 새 값으로 반드시 교체한다.
- **이미 쓴 Refresh Token이 다시 오면 탈취로 보고 그 세션 전체를 폐기한다.** 새로 받은 토큰도 함께 무효가 된다. 다른 기기의 세션은 영향받지 않는다.
- 같은 토큰으로 동시에 요청하면 하나만 성공하고 세션이 폐기된다. **앱은 refresh를 한 번에 하나만 보낸다**([4.8](#48-앱-연동-가이드)).

**요청**
```json
{ "refreshToken": "Vk8aMtqzFJ5-pDLRjWowU50bPlZAYslqNJwBwnM-BjI" }
```

**응답 200**
```json
{
  "accessToken": "eyJraWQiOiJiYXJvZml0LTIwMjYxMCIsImFsZyI6IlJTMjU2In0...",
  "accessTokenExpiresAt": "2026-10-14T05:34:54.964864Z",
  "refreshToken": "q3Jx0b7mY1H2r8T9vLkP4sWc6ZaN5eGdUfBiOhKjMnQ"
}
```

**에러**

| 상태 | code | 상황 | 앱 동작 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | `refreshToken` 누락·공백 | 요청 수정 |
| 401 | `AUTH_INVALID_REFRESH` | 없는 토큰이거나 만료됨 | 로컬 토큰 삭제 후 재로그인 |
| 401 | `AUTH_SESSION_REVOKED` | 폐기된 토큰 재사용(로그아웃한 토큰 포함). 세션 전체 폐기 | 로컬 토큰 삭제 후 재로그인 |
| 401 | `AUTH_USER_WITHDRAWN` | 탈퇴한 사용자(탈퇴 시 토큰이 모두 폐기돼 실제로는 거의 없음) | 로컬 토큰 삭제 후 로그인 화면 |

### 4.4 로그아웃 — `POST /api/v1/auth/logout`
요청한 Refresh Token이 속한 세션(현재 기기)을 폐기한다. **토큰이 없거나, 만료됐거나, 이미 로그아웃된 경우에도 항상 204다.** 다만 body 형식이 틀리면(필드 누락·공백) 400 `INVALID_INPUT`이다. 이미 발급된 Access Token은 만료까지 유효하므로 앱은 로컬의 Access Token도 지운다.

**요청**
```json
{ "refreshToken": "q3Jx0b7mY1H2r8T9vLkP4sWc6ZaN5eGdUfBiOhKjMnQ" }
```
**응답 204**: body 없음.

### 4.5 회원 탈퇴 — `DELETE /api/v1/users/me`
- Access Token이 필요하고 body는 없다.
- 닉네임과 사진을 지우고 탈퇴 상태로 바꾼다.
- SNS 연결을 끊는다. **같은 SNS로 다시 로그인하면 새 회원으로 가입된다.**
- 모든 기기의 Refresh Token을 폐기한다. 이미 발급된 Access Token은 최대 15분 유효하다.
- 다른 기능의 데이터는 탈퇴 처리 직후 정리된다.
- **이미 탈퇴했어도 204다.** 재시도해도 안전하다.

**응답 204**: body 없음.

**에러** (401 응답에는 `WWW-Authenticate: Bearer` 헤더가 붙는다)

| 상태 | code | 상황 | 앱 동작 |
|---|---|---|---|
| 401 | `AUTH_TOKEN_EXPIRED` | Access Token 만료 | refresh 후 재시도 |
| 401 | `AUTH_INVALID_TOKEN` | Access Token 없음·위조·형식 오류 | 재로그인 |

### 4.6 공개키 목록 — `GET /.well-known/jwks.json`
바로핏 Access Token을 검증하는 공개키 목록(JWK Set)이다. 독립 서비스에서 검증에 사용할 수 있다. 현재 루틴 서비스는 공개 JWKS 파일을 읽기 전용으로 마운트하여 사용한다. **앱은 호출하지 않는다.** 키 교체 기간에는 이전 키와 새 키가 함께 있고, 토큰 헤더의 `kid`로 키를 고른다.

```json
{ "keys": [ { "kty": "RSA", "use": "sig", "alg": "RS256", "kid": "barofit-202610", "e": "AQAB", "n": "z_ciri3MB6eX81o3..." } ] }
```

### 4.7 에러 코드

| code | 상태 | 발생 | 의미 | 앱 동작 |
|---|---|---|---|---|
| `AUTH_TOKEN_EXPIRED` | 401 | 인증 필요한 API | Access Token 만료 | **refresh 후 재시도** |
| `AUTH_INVALID_TOKEN` | 401 | 인증 필요한 API | Access Token 없음·위조·형식 오류 | 재로그인 |
| `AUTH_INVALID_ID_TOKEN` | 401 | social-login | 소셜 ID Token 검증 실패 | SDK 로그인부터 다시 |
| `AUTH_INVALID_AUTHORIZATION_CODE` | 401 | social-login/code | 인가 코드 만료·재사용, redirect_uri 불일치 | 소셜 로그인부터 다시 |
| `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` | 503 | social-login, social-login/code | 소셜 서버 장애·타임아웃, 서버 소셜 설정 오류 | 잠시 후 재시도 |
| `AUTH_INVALID_REFRESH` | 401 | refresh | Refresh Token 없음·만료 | 재로그인 |
| `AUTH_SESSION_REVOKED` | 401 | refresh | 세션 폐기(재사용 탐지, 로그아웃한 토큰) | 재로그인 |
| `AUTH_USER_WITHDRAWN` | 401 | refresh, `/users/me`, 온보딩 | 탈퇴한 사용자 | 로컬 토큰 삭제 후 로그인 화면 |
| `PROFILE_NICKNAME_INVALID` | 400 | 온보딩 | 닉네임 규칙 위반 | 닉네임 수정 |
| `PROFILE_NICKNAME_DUPLICATED` | 409 | 온보딩 | 이미 사용 중인 닉네임 | 다른 닉네임 |
| `PROFILE_ALREADY_ONBOARDED` | 409 | 온보딩 | 이미 온보딩 완료 | 홈으로 |
| `PROFILE_NICKNAME_GENERATION_FAILED` | 503 | 랜덤 닉네임 | 생성 실패(사용 중인 값만 뽑힘) | 재시도 또는 직접 입력 |
| `INVALID_INPUT` | 400 | 모든 API | 요청 값 오류 | 요청 수정 |
| `NOT_FOUND` | 404 | 모든 API | 없는 경로 | - |
| `METHOD_NOT_ALLOWED` | 405 | 모든 API | 지원하지 않는 메서드 | - |
| `INTERNAL_SERVER_ERROR` | 500 | 모든 API | 서버 오류 | 잠시 후 재시도 |

### 4.8 앱 연동 가이드
**로그인 (현재 앱: Expo Go + WebView)**
1. `state`·`nonce`를 만들고 WebView로 카카오·구글 로그인 주소를 연다(4.2-1).
2. `redirect_uri`로 이동하려는 순간 `code`를 가로채고 `state`를 확인한다.
3. `social-login/code`를 호출하고, 받은 토큰 세 값을 안전한 저장소에 저장한다(앱은 `expo-secure-store`).

**로그인 (SDK 방식으로 바꿀 경우)**
1. SDK로 로그인해 **ID Token**을 받는다. Kakao는 콘솔에서 OpenID Connect를 켜야 ID Token이 나온다.
2. `social-login`을 호출하고, 받은 토큰 세 값을 안전한 저장소에 저장한다.
3. `isOnboarded`로 온보딩 화면과 홈 화면 중 하나로 보낸다.

**refresh는 한 번에 하나만(single-flight).** 여러 요청이 동시에 `AUTH_TOKEN_EXPIRED`를 받아도 refresh는 하나만 보내고, 나머지는 그 결과를 기다렸다가 재시도한다.
```
API 요청 → 401 AUTH_TOKEN_EXPIRED
  → 진행 중인 refresh가 있으면 기다림, 없으면 refresh 시작
      → 200: 토큰 두 개 모두 교체 후 원래 요청 재시도
      → 401: 로컬 토큰 삭제 후 로그인 화면
```
`accessTokenExpiresAt` 전에 미리 refresh해도 되지만, 이때도 동시에 하나만 보낸다.

**401 처리:** `AUTH_TOKEN_EXPIRED`는 refresh 후 재시도한다. `AUTH_USER_WITHDRAWN`은 로컬 토큰을 지우고 로그인 화면으로 간다. 그 외 `AUTH_*`는 로컬 토큰을 지우고 재로그인한다.

**로그아웃:** `logout`을 호출하고, 결과와 상관없이 로컬 토큰을 지운다.

**탈퇴:** `DELETE /users/me` 호출 → 204를 받으면 SDK 연결 해제(카카오 `unlink`, 구글 `revokeAccess`)를 시도한다. 실패해도 서버 탈퇴는 이미 끝났다 → 로컬 토큰을 지우고 로그인 화면으로 간다.

---

## 5. 설정

| 환경 변수 | 설명 | 기본값 |
|---|---|---|
| `PORT` | 서버 포트 | `3002` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | DB 접속 정보 | `jdbc:postgresql://localhost:5432/barofit`, `postgres`, `postgres` |
| `JWT_PRIVATE_KEY_PATH` | 서명용 개인키 파일 경로 | 필수 |
| `JWT_PUBLIC_JWKS_PATH` | 공개키 목록 파일 경로 | 필수 |
| `JWT_CURRENT_KID` | 현재 서명 키의 kid | 필수 |
| `GOOGLE_CLIENT_IDS` | Google aud 허용 목록(쉼표 구분) | 비어 있음 → Google 로그인 거부 |
| `KAKAO_APP_KEYS` | Kakao aud 허용 목록(쉼표 구분) | 비어 있음 → Kakao 로그인 거부 |
| `SOCIAL_REDIRECT_URIS` | 인가 코드 로그인의 redirect_uri 허용 목록(쉼표 구분). 콘솔 등록값과 같아야 함 | `http://localhost:8081` |
| `GOOGLE_WEB_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google 인가 코드 교환(웹 클라이언트 `barofit-web`) | 비어 있음 → Google 코드 로그인 503 |
| `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` | Kakao 인가 코드 교환(REST API 키와 클라이언트 시크릿) | 비어 있음 → Kakao 코드 로그인 503 |

시크릿 두 개는 **서버 `.env`(운영은 Secret)에만** 둔다. 저장소, 앱, 문서에 넣지 않는다.

애플리케이션 프로퍼티: `barofit.refresh-token.cleanup-cron`(기본 `0 0 4 * * *`), `barofit.scheduling.enabled`(테스트에서는 false).

**소셜 콘솔 설정 (2026-10-14 개발용 설정 완료)**

공통 값: Android 패키지명·iOS 번들 ID는 `com.barofit.app`이다. Android debug 서명은 Expo 템플릿의 debug 키스토어를 쓴다. 이 키스토어의 키 해시는 `Xo8WBi6jzSxKDVR4drqm84yr9iU=`, SHA-1은 `5E:8F:16:06:2E:A3:CD:2C:4A:0D:54:78:76:BA:A6:F3:8C:AB:F6:25`이다.

| 콘솔 | 설정한 내용 |
|---|---|
| Kakao Developers | 앱 "바로핏", Default Native AppKey에 Android(패키지명, debug 키 해시)·iOS(번들 ID) 등록, 카카오 로그인 ON, **OpenID Connect ON**, 동의항목(닉네임 필수, 프로필 사진 선택) |
| Google Cloud (`barofit` 프로젝트) | 동의 화면 외부·테스트 중, 범위 `openid`·`email`·`profile`, 클라이언트 `barofit-web`(웹)·`barofit-android-debug`(Android, debug SHA-1)·`barofit-ios`(iOS) |

| 값 | 앱 | 서버 `.env` |
|---|---|---|
| 카카오 네이티브 앱 키 | SDK 초기화 | `KAKAO_APP_KEYS` |
| Google 웹 클라이언트 ID | `webClientId` | `GOOGLE_CLIENT_IDS` |
| Google iOS 클라이언트 ID | `iosClientId` | `GOOGLE_CLIENT_IDS` |
| Google iOS URL 스킴 | config plugin | - |

- 실제 값은 콘솔과 로컬 `.env`에서 확인한다. 이 문서에는 적지 않는다.
- **현재 앱(인가 코드 방식)이 쓰는 값:** Kakao REST API 키·클라이언트 시크릿, Google 웹 클라이언트 ID·secret, 리다이렉트 URI `http://localhost:8081`(두 콘솔에 등록). 앱에는 REST API 키와 웹 클라이언트 ID만 들어간다.
- 위 표의 네이티브 앱 키, iOS 클라이언트 ID, 패키지명·키 해시·SHA-1은 SDK 방식으로 바꿀 때 쓴다(Expo Go에서는 쓰이지 않음).
- 서버 단독 테스트(브라우저로 code 받기)용 리다이렉트 URI `http://localhost:8765`도 두 콘솔에 등록되어 있다. 출시 전 삭제한다.
- `aud`에 실제로 들어오는 값은 앱 설정에 따라 다르다. Google은 웹 또는 iOS 클라이언트 ID일 수 있고, Kakao는 네이티브 앱 키로 예상한다. **실제 ID Token으로 확인한 뒤** 쓰지 않는 값을 뺀다.
- Google은 "테스트 중" 상태라 테스트 사용자로 등록한 계정만 로그인할 수 있다.
- **출시 전 할 일:** Google 브랜딩(개인정보처리방침 URL 등) 완성과 앱 게시, release·EAS·Play 서명의 SHA-1(Google Android 클라이언트 추가)과 키 해시(Kakao) 추가, 스토어 URL·App Store ID 입력.

---

## 6. 테스트와 검증

| 구분 | 테스트 | 내용 |
|---|---|---|
| 단위 | `AccessTokenIssuerTest`, `JwtKeysTest`, `RefreshTokenValuesTest`, `SocialIdTokenVerifierTest`, `UserTest` | 토큰 claim, 키 짝 검증, 해시, ID Token의 iss·aud·exp·서명·nonce, 교환 client-id의 aud 허용, Kakao 사진 https 변환, 닉네임·URL 길이 |
| 단위 | `NicknamePolicyTest`, `RandomNicknameGeneratorTest` | 닉네임 규칙, 랜덤 닉네임이 항상 규칙을 만족 |
| 단위 | `SocialAuthorizationCodeClientTest` 8건 | 코드 교환 성공, 만료 코드(401), 잘못된 시크릿·소셜 서버 5xx·`id_token` 누락·설정 누락(503), 설정 toString의 시크릿 마스킹 |
| 공통 | `GlobalExceptionHandlerTest`, `ErrorHandlingAutoConfigurationTest` | 에러 형식, 자동설정 중복 등록 방지 |
| 통합(Testcontainers) | `AuthApiTest` 29건 | 가입·로그인, 인가 코드 가입(ID Token 방식과 같은 회원), 허용 안 된 redirectUri 400, 만료 코드 401, nonce 누락 400, 동시 가입(8스레드), 회전, 재사용 탐지, 동시 refresh, 만료, logout, 탈퇴 멱등, onboarded, 사용자 단건·여러 건 조회 |
| 통합(Testcontainers) | `OnboardingApiTest` 13건 | 저장·조회, 선택 항목 생략, 재온보딩 409, 닉네임 중복(대소문자 무시)·형식 오류, 입력 검증 400, 다른 사용자 동시 같은 닉네임(6스레드), 같은 사용자 동시 온보딩, 랜덤 닉네임, 탈퇴 시 프로필 삭제·닉네임 반납, 미인증 401 |
| 통합 | `SecurityConfigTest`, `JwksEndpointTest`, `IdentityPersistenceTest`, `BaroApplicationTests` | 만료·위조 구분, 공개 경로 헤더 무시, JWKS, 제약 조건, JwtDecoder Bean 하나 |
| 구조 | `ArchitectureTests` 3건 | common 독립, api 패키지는 JDK만, 다른 도메인은 api 패키지만 참조 |

**실제 서버로 확인한 것(임시 PostgreSQL):** 각 에러 응답 형식, refresh 회전과 재사용 탐지, logout 후 refresh 거부, 탈퇴 후 익명화·소셜 계정 삭제·토큰 폐기, 탈퇴 재호출 204, Docker 이미지 빌드.

`./gradlew build` 기준 전체 90건 통과.

**실제 Google·Kakao로 확인한 것 (2026-10-14, 로컬 PostgreSQL):**
- ID Token 방식(`/social-login`): 가입, 재로그인, refresh, 재사용 탐지, logout, 탈퇴, 재가입. aud 허용 목록에서 키를 빼면 거부.
- 인가 코드 방식(`/social-login/code`), 서버 단독(redirect `http://localhost:8765`): Kakao·Google 가입, 같은 code 재사용 시 401 `AUTH_INVALID_AUTHORIZATION_CODE`, 가짜 code로 시크릿 확인(시크릿이 맞으면 401, 틀리면 503).
- Expo Go 실기기(iPhone, WebView 모달, redirect `http://localhost:8081`): Kakao·Google 로그인 → 기존 회원으로 재로그인(회원 수 변화 없음, `last_login_at` 갱신) → 온보딩 화면 이동 → 로그아웃 시 refresh token 폐기.

**확인하지 않은 것:** Android Expo Go에서의 구글 로그인(이번 범위에서 제외). Google은 WebView 로그인을 막는 정책이 있어 Android에서는 차단될 수 있다.

**알려진 제한 (프론트엔드):**
- 온보딩 저장 API와 `GET /api/v1/users/me`는 #10에서 추가됐다. 앱 연동은 아직 하지 않았다([onboarding-api.md](onboarding-api.md)).

---

## 7. 운영 시 알아 둘 점
- 동시 가입이 충돌하면 PostgreSQL·Hibernate가 `duplicate key` ERROR 로그를 남긴다. 정상 처리 과정이므로 로그 알림 규칙에서 제외해야 할 수 있다.
- 탈퇴·로그아웃 후에도 이미 발급된 Access Token은 최대 15분 유효하다.
- 정리 배치는 인스턴스마다 실행된다. 삭제가 멱등이라 문제는 없지만, replica가 2개 이상이면 ShedLock 같은 중복 실행 방지를 검토한다.
- Compose 실행 시 시작 스크립트가 읽기 전용으로 마운트된 `keys/jwt-private.pem`을 컨테이너 내부에 복사하고 앱 사용자(uid 1001)에게만 읽기 권한을 준다.
- 도메인 경계는 컴파일러가 아니라 테스트(`ArchitectureTests`)가 지킨다. CI에서 테스트를 반드시 실행한다.
- 앱은 에러의 `message`가 아니라 `code`로 분기해야 한다.
- 토큰·시크릿 유출 점검(2026-10-14): 로그에는 코드·토큰·시크릿을 남기지 않고, Refresh Token은 해시만 저장한다. 설정 객체의 `toString`은 시크릿을 가린다. Actuator는 health만 공개한다.
- 로컬 개발은 http로 통신한다. 배포 서버는 반드시 https로 연다.
- 앱 WebView에 카카오·구글 로그인 쿠키가 남아, 바로핏 로그아웃 후에도 같은 SNS 계정으로 바로 다시 로그인된다. 공용 기기 대응이 필요하면 WebView `incognito`를 검토한다.
- 출시 전: 서버 단독 테스트용 리다이렉트 URI `http://localhost:8765` 삭제, Google OAuth 동의 화면 게시.
