# barofit-backend

운동 보조 앱 **barofit**의 백엔드. 하나의 저장소에서 인증과 루틴을 각각 독립 Spring Boot 애플리케이션·컨테이너로 빌드합니다. PostgreSQL은 하나를 공유하고 `identity`, `routine` 스키마로 논리적으로 분리합니다.

## 커밋 컨벤셜
- feat: 새로운 기능 개발
- fix: 버그 수정
- cicd: 워크플로우 변경
- refactor: 기능 변경 없는 리팩토링
- docs: 문서 작업

```
커밋 양식

feat(#이슈번호): 로그인 기능 개발
```


## 기술 스택
- Java 21
- Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator)
- Gradle 9 (멀티 모듈)
- PostgreSQL 18 (모듈별 schema, Flyway 마이그레이션)
- Testcontainers (테스트용 PostgreSQL, Docker 필요)
- Lombok
- JUnit 5 (테스트)

## 폴더 구조
```
backend_baro/
├─ identity-app/    # 인증 서비스 실행 앱 → identity-service 컨테이너
├─ routine-app/     # 루틴 서비스 실행 앱 → routine-service 컨테이너
├─ service-runtime/ # 공용 JWT 검증·Clock·Flyway 실행 코드 (도메인 의존 없음)
├─ app/             # 두 도메인의 기존 회귀 테스트 전용 (배포/bootRun 비활성)
├─ common/          # 공용 오류 응답·스키마 등록 계약
├─ modules/         # identity-service, routine 도메인 구현
├─ deploy/          # Nginx 게이트웨이 라우팅
├─ compose.yaml     # gateway + identity-service + routine-service + postgres
└─ Dockerfile       # SERVICE_MODULE 인자로 실행 앱 선택
```

### 도메인 모듈 추가 방법 (예: workout)
도메인 하나에 `{도메인}-service` 모듈 하나를 만듭니다. 다른 도메인에 공개할 것은 모듈 안의 `api` 패키지에 둡니다.

1. `modules/workout-service/build.gradle` 생성
   ```groovy
   dependencies {
       implementation project(':common')
       implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
       // 다른 서비스 구현 모듈은 의존하지 않습니다. 서비스 간 호출은 HTTP/이벤트 계약으로 연결합니다.
   }
   ```
2. 소스는 `com.barofit.workout` 패키지 하위에 작성합니다.
   ```
   com.barofit.workout
   ├─ api/      # 공개 계약: 조회 인터페이스, 이벤트, DTO (Spring·JPA 애노테이션 없는 JDK 타입)
   └─ ...       # 내부 구현: 엔티티, 리포지토리, 서비스, 컨트롤러
   ```
3. `settings.gradle`에 `include 'modules:workout-service'` 추가
4. `workout-app` 실행 모듈을 만들고 `service-runtime`과 자기 도메인 모듈에만 의존합니다. Compose 서비스와 게이트웨이 경로를 추가합니다.


## 에러 응답
RFC 9457 Problem Details 형식에 `code` 속성을 더해 응답합니다. 앱은 `code`로 동작을 분기합니다.
```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "요청 값이 올바르지 않습니다.", "instance": "/api/v1/...", "code": "INVALID_INPUT" }
```
- 형식은 `common`이 정하고, 에러 코드 목록은 각 모듈이 `ErrorCode` enum으로 소유합니다.

## DB 마이그레이션
- 모듈마다 PostgreSQL schema를 하나씩 가지며, Flyway도 모듈마다 따로 실행됩니다. Spring Boot의 Flyway 자동설정은 사용하지 않습니다.
- 모듈은 `ModuleSchema` Bean을 등록하고, 마이그레이션 파일은 `src/main/resources/db/migration/{schema}/`에 둡니다. 버전은 모듈마다 `V1`부터 매깁니다.
- JPA `ddl-auto`는 `validate` 고정입니다. 스키마 변경은 마이그레이션 파일로만 합니다.

## 인증 API
> 상세 명세, 구현 내용, MSA 전환 참고: [docs/identity-service.md](docs/identity-service.md)

| API | 인증 | 설명 |
|---|---|---|
| `POST /api/v1/auth/social-login/code` | 없음 | `{ provider, code, redirectUri, nonce }` → social-login과 같은 응답. 앱(Expo Go)이 WebView 로그인으로 받은 인가 코드를 서버가 교환 |
| `POST /api/v1/auth/social-login` | 없음 | `{ provider: GOOGLE\|KAKAO, idToken }` → `{ accessToken, accessTokenExpiresAt, refreshToken, isOnboarded, user }`. 신규면 가입 |
| `POST /api/v1/auth/refresh` | 없음 | `{ refreshToken }` → `{ accessToken, accessTokenExpiresAt, refreshToken }`. 쓸 때마다 회전 |
| `POST /api/v1/auth/logout` | 없음 | `{ refreshToken }` → 항상 204. 해당 세션만 폐기 |
| `DELETE /api/v1/users/me` | Access Token | 탈퇴. 이미 탈퇴했어도 204 |
| `GET /.well-known/jwks.json` | 없음 | 바로핏 JWT 공개키 |

- `/api/v1/auth/**`에서는 Authorization 헤더를 무시합니다. 만료된 Access Token이 붙어 있어도 refresh할 수 있습니다.
- 401 `code`: `AUTH_TOKEN_EXPIRED`(refresh 시도), `AUTH_INVALID_TOKEN`·`AUTH_SESSION_REVOKED`·`AUTH_INVALID_REFRESH`·`AUTH_INVALID_ID_TOKEN`(재로그인), `AUTH_USER_WITHDRAWN`(로그인 화면)
- 소셜 ID Token의 aud 허용 목록은 `GOOGLE_CLIENT_IDS`, `KAKAO_APP_KEYS`(쉼표 구분)로 설정합니다. 비어 있는 provider의 로그인은 모두 거부됩니다.

## JWT 키
바로핏 Access Token은 RS256으로 서명합니다. 키는 **어떤 환경에서도 저장소에 커밋하지 않습니다.**

| 설정 | 내용 |
|---|---|
| `JWT_PRIVATE_KEY_PATH` | 서명용 개인키. PEM(PKCS#8) 파일 |
| `JWT_PUBLIC_JWKS_PATH` | 검증용 공개키 목록. JWK Set 파일. 키 교체 기간에는 이전 키와 새 키를 함께 넣습니다 |
| `JWT_CURRENT_KID` | 현재 서명에 쓰는 kid. 공개키 목록에 있어야 합니다 |

- 로컬: `./scripts/generate-jwt-keys.sh`로 `keys/`(gitignore)에 생성하고, 출력된 값을 `.env`에 넣습니다.
- Docker: `keys/`를 컨테이너에 읽기 전용으로 마운트합니다(`compose.yaml`).
- 테스트: 메모리에서 키를 생성합니다(`TestJwtKeyFiles`).
- 인증 앱은 시작할 때 개인키와 현재 kid의 공개키가 짝인지 검증합니다. 루틴 앱에는 공개 JWKS만 마운트하며 인증 서비스와 통신하지 않고 JWT 서명을 검증합니다. 파일 기반 공개 키를 교체하면 각 서비스를 재시작해야 합니다.

## 실행
```bash
./scripts/generate-jwt-keys.sh  # 최초 1회. 출력된 JWT_* 값을 .env에 설정

# Docker로 독립 서비스 + gateway + postgres 실행
docker compose up -d --build --remove-orphans   # 외부 API: PORT (기본 3002)

# JVM으로 따로 실행할 경우 (각각 별도 터미널)
cp .env.example .env    # 프로젝트 root 위치에서 터미널 명령어 실행
docker compose up -d postgres
./gradlew :identity-app:bootRun  # 기본 3004
./gradlew :routine-app:bootRun   # 기본 3005
```
> 접속 정보는 `.env`에서 수정합니다.

## 테스트 / 빌드
```bash
./gradlew test    # 테스트 (Docker 필요)
./gradlew build   # identity-app/build/libs/app.jar, routine-app/build/libs/app.jar
```
> DB가 필요한 테스트는 Testcontainers로 PostgreSQL을 띄우므로 **로컬과 CI 모두 Docker가 필요합니다.**
> 이미지 태그는 Gradle이 `compose.yaml`에서 읽어 넘기므로, IDE에서도 Gradle 테스트 러너로 실행해야 합니다.


## Routine Service

운동 3종(푸시업·스쿼트·플랭크), 가이드/TTS 원문, 템플릿 기반 사용자 루틴 CRUD, 난이도별 기본 템플릿 9개를 제공합니다. 사용자는 종목별 세트 수·반복 횟수·유지 시간만 조절할 수 있습니다. `routine` 스키마는 `ModuleSchema`로 등록하여 identity와 별도로 마이그레이션합니다.

- Swagger UI: http://localhost:3002/swagger-ui.html
- OpenAPI: http://localhost:3002/v3/api-docs
- Swagger의 Authorize에 소셜 로그인 응답의 `accessToken`을 입력합니다.
- 운동·템플릿 조회는 공개이며 사용자 루틴은 JWT 인증이 필요합니다.
- [Routine 설계·인증·프론트 연동 안내](docs/routine-service.md)

## 서비스 실행 경계

| 서비스 | 외부 직접 포트(기본, localhost만) | 소유 스키마 | 도메인 의존 |
|---|---|---|---|
| identity-service | 3004 | identity | identity-service만 |
| routine-service | 3005 | routine | routine만 |
| gateway | PORT(기본 3002, LAN 접근 가능) | 없음 | HTTP 라우팅 |

게이트웨이는 `/api/v1/auth/**`, `/api/v1/users/**`, `/.well-known/jwks.json`을 인증 서비스로, 운동·루틴 API를 루틴 서비스로 전달합니다. 프론트 `EXPO_PUBLIC_API_URL`은 게이트웨이 주소 하나를 사용합니다. 현재 로컬 `.env`는 PORT=3003입니다.

Swagger는 게이트웨이 `/swagger-ui.html`에서 Routine Service / Identity Service를 선택합니다. 원본 명세는 `/v3/api-docs`, `/identity/v3/api-docs`입니다. 직접 포트의 `/actuator/health`로 각 서비스 상태를 확인합니다. 게이트웨이 `/health`는 프록시 자체의 생존 상태만 나타냅니다.

각 서비스는 자기 도메인의 엔티티·리포지토리·Flyway 파일만 포함하며 자기 스키마만 마이그레이션합니다. 기존 DB 볼륨 및 migration 이력은 유지됩니다. 현재 DB 접속 계정은 공유하므로 DB 권한으로 접근을 차단한 구조는 아니며, 서비스 코드에서 다른 스키마를 조회하거나 FK를 만들지 않습니다.

기존 Spring 이벤트(`UserWithdrawn` 등)는 프로세스 내부 이벤트입니다. 다른 서비스로의 탈퇴 전파/루틴 정리가 필요하면 HTTP 또는 메시지 이벤트 연동을 추가해야 합니다. 현재 즉시 토큰 폐기나 루틴 데이터 삭제 전파는 구현되어 있지 않습니다.

독립 서비스 검증(로컬 개발 키만 사용):

```bash
./gradlew :identity-app:verifyServiceIsolation :routine-app:verifyServiceIsolation
SMOKE_BASE_URL=http://localhost:3003 node scripts/verify-services.cjs
```

스크립트는 분리된 Swagger/라우팅, 인증 거부, JWT 루틴 CRUD·소유권을 확인하고 생성한 테스트 루틴을 삭제합니다. 실제 소셜 로그인은 별도 실기기 확인이 필요합니다. 직접 서비스 포트를 바꾼 경우 `SMOKE_IDENTITY_URL`, `SMOKE_ROUTINE_URL`도 지정합니다.
