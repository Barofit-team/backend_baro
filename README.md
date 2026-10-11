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
- PostgreSQL (테스트는 H2 인메모리)
- Lombok
- JUnit 5 (테스트)

## 폴더 구조
```
backend_baro/
├─ app/         # 실행 모듈 (@SpringBootApplication, application.yaml) - 모든 모듈을 조립해 실행 jar 생성
├─ common/      # 모듈 전반에서 쓰는 공용 예외/응답/필터/설정
├─ modules/     # 도메인별 모듈 (user, workout 등 추가 예정)
├─ build.gradle     # 모든 모듈 공통 설정
└─ settings.gradle  # 모듈 등록
```

### 도메인 모듈 추가 방법 (예: user)
1. `modules/user/build.gradle` 생성
   ```groovy
   dependencies {
       implementation project(':common')
       implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
   }
   ```
2. 소스는 `modules/user/src/main/java/com/barofit/user/` 하위에 작성
3. `settings.gradle`에 `include 'modules:user'` 추가
4. `app/build.gradle`에 `implementation project(':modules:user')` 추가

> 추후 서비스 분리를 위해 도메인 모듈끼리는 서로 직접 의존하지 않고 `common`에만 의존합니다.


## 실행
```bash
cp .env.example .env    # 프로젝트 root 위치에서 터미널 명령어 실행
./gradlew :app:bootRun  # http://localhost:3002
```
> 로컬에 PostgreSQL이 실행 중이어야 하며, 접속 정보는 `.env`에서 수정합니다.

## 테스트 / 빌드
```bash
./gradlew test    # 테스트
./gradlew build   # 빌드 (app/build/libs/app-0.0.1-SNAPSHOT.jar)
```
