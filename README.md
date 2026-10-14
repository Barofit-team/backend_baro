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
├─ modules/     # 도메인별 모듈 (user, workout 등 추가 예정)
├─ build.gradle     # 모든 모듈 공통 설정
├─ settings.gradle  # 모듈 등록
├─ compose.yaml     # app + postgres. postgres 이미지 태그의 기준(테스트도 같은 태그 사용)
└─ Dockerfile       # app 모듈(모놀리스) 이미지
```

### 도메인 모듈 추가 방법 (예: user)
도메인마다 구현 모듈과 공개 계약 모듈(`-api`)을 함께 만듭니다.

1. `modules/user-api/build.gradle` 생성. 공개 계약 모듈은 JDK에만 의존합니다
   ```groovy
   dependencies {
   }
   ```
   소스는 `modules/user-api/src/main/java/com/barofit/user/api/` 하위에 작성합니다. 이벤트, 조회 인터페이스, DTO를 Spring·JPA 애노테이션 없는 JDK record로 둡니다.
2. `modules/user/build.gradle` 생성
   ```groovy
   dependencies {
       api project(':modules:user-api')
       implementation project(':common')
       implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
       // 다른 도메인이 필요하면 그 도메인의 -api 모듈에만 의존
       // implementation project(':modules:identity-api')
   }
   ```
3. 소스는 `modules/user/src/main/java/com/barofit/user/` 하위에 작성합니다. 구현 모듈 안에 `api`라는 이름의 패키지는 만들지 않습니다.
4. `settings.gradle`에 `include 'modules:user'`, `include 'modules:user-api'` 추가
5. `app/build.gradle`에 `implementation project(':modules:user')` 추가

### 의존 규칙
- 도메인 구현 모듈끼리의 의존은 금지합니다. 다른 도메인의 `-api` 모듈에 대한 의존만 허용합니다.
- `-api` 모듈은 JDK에만 의존합니다. `common`도 참조하지 않습니다.
- `common`에는 도메인 코드를 넣지 않고, 어떤 도메인 모듈에도 의존하지 않습니다. `common`의 Bean은 자동설정(`AutoConfiguration.imports`)으로 등록합니다.
- 모듈 경계는 Gradle 멀티 모듈 구조가 컴파일 단계에서 강제하고, 그 밖의 규칙은 ArchUnit(`app`의 `ArchitectureTests`)으로 검사합니다.
- 다른 모듈의 schema를 참조하는 FK, 네이티브 쿼리, JPQL은 쓰지 않습니다. ArchUnit이 잡지 못하므로 코드 리뷰로 지킵니다.

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

## 실행
```bash
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
