# barofit-backend

운동 보조 앱 **barofit**의 백엔드. 추후 서비스별로 레포를 분리할 예정이라, 지금은 하나의 레포 안에서 **모듈형 모놀리스**(Gradle 멀티 모듈) 구조로 개발합니다.

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
├─ app/         # 실행 모듈 (@SpringBootApplication, application.yaml) - 모든 모듈을 조립해 실행 jar 생성
├─ common/      # 모듈 전반에서 쓰는 공용 예외/응답/필터/설정
├─ modules/     # 도메인별 모듈. 도메인 하나에 {도메인}-service 모듈 하나 (현재 identity-service)
├─ build.gradle     # 모든 모듈 공통 설정
├─ settings.gradle  # 모듈 등록
├─ compose.yaml     # app + postgres. postgres 이미지 태그의 기준(테스트도 같은 태그 사용)
└─ Dockerfile       # app 모듈(모놀리스) 이미지
```

### 도메인 모듈 추가 방법 (예: workout)
도메인 하나에 `{도메인}-service` 모듈 하나를 만듭니다. 다른 도메인에 공개할 것은 모듈 안의 `api` 패키지에 둡니다.

1. `modules/workout-service/build.gradle` 생성
   ```groovy
   dependencies {
       implementation project(':common')
       implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
       // 다른 도메인이 필요하면 그 모듈에 의존하되, 코드에서는 그 도메인의 api 패키지만 참조합니다
       // implementation project(':modules:identity-service')
   }
   ```
2. 소스는 `com.barofit.workout` 패키지 하위에 작성합니다.
   ```
   com.barofit.workout
   ├─ api/      # 공개 계약: 조회 인터페이스, 이벤트, DTO (Spring·JPA 애노테이션 없는 JDK 타입)
   └─ ...       # 내부 구현: 엔티티, 리포지토리, 서비스, 컨트롤러
   ```
3. `settings.gradle`에 `include 'modules:workout-service'` 추가
4. `app/build.gradle`에 `implementation project(':modules:workout-service')` 추가


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
- 앱이 시작할 때 개인키와 현재 kid의 공개키가 짝인지 검증하고, 아니면 기동하지 않습니다.

## 실행
```bash
./scripts/generate-jwt-keys.sh  # 최초 1회. 출력된 JWT_* 값을 .env에 설정

# Docker로 app + postgres 실행
docker compose up -d --build   # http://localhost:3002

# 로컬에서 app만 실행 (postgres는 compose로 띄우거나 로컬 설치본 사용)
cp .env.example .env    # 프로젝트 root 위치에서 터미널 명령어 실행
docker compose up -d postgres
./gradlew :app:bootRun  # http://localhost:3002
```
> 접속 정보는 `.env`에서 수정합니다.

## 테스트 / 빌드
```bash
./gradlew test    # 테스트 (Docker 필요)
./gradlew build   # 빌드 (app/build/libs/app-0.0.1-SNAPSHOT.jar)
```
> DB가 필요한 테스트는 Testcontainers로 PostgreSQL을 띄우므로 **로컬과 CI 모두 Docker가 필요합니다.**
> 이미지 태그는 Gradle이 `compose.yaml`에서 읽어 넘기므로, IDE에서도 Gradle 테스트 러너로 실행해야 합니다.
