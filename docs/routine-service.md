# Routine Service MVP

`services/routine`은 독립 실행 가능한 루틴 서비스다. 단일 PostgreSQL의 `routine` 스키마를 소유하며 다른 서비스의 테이블을 조회하거나 FK로 참조하지 않는다.

## 실행 및 Swagger

Java 21과 PostgreSQL이 필요하다. 루트 README와 `.env.example`에 따라 DB·JWT 키·소셜 로그인 설정을 준비한다. `scripts/generate-jwt-keys.sh`로 생성한 키 경로와 kid를 `.env`에 지정한다. 설정 파일과 키는 커밋하지 않는다.

```bash
./gradlew :services:routine:bootRun
```

- Swagger UI: http://localhost:3002/swagger-ui.html
- OpenAPI JSON: http://localhost:3002/v3/api-docs
- Swagger **Authorize**에 소셜 로그인 응답의 `accessToken`을 입력한다. Bearer 접두사는 UI가 붙인다.
- 모든 프로필에서 JWT 인증을 사용한다. 기존 `X-Local-User-Id` 헤더는 더 이상 지원하지 않는다.
- 운동·템플릿 GET 및 Swagger GET은 공개이며, 만료된 토큰이 붙어도 조회할 수 있다. 사용자 루틴 API는 인증이 필요하다.

## 인증 모듈 및 프론트 연동

구글·카카오 인가 코드를 `POST /api/v1/auth/social-login/code`로 보내면 identity가 자동 가입/로그인과 토큰 발급을 처리한다. 루틴 서비스의 Spring Security가 JWT를 검증하고, `PrincipalCurrentUserProvider`가 검증된 Principal의 이름(JWT `sub`)을 내부 사용자 UUID로 읽는다. 루틴 서비스는 identity 내부 Entity/Service를 참조하지 않는다.

프론트의 공통 API 함수를 사용한다.

```typescript
const routines = await apiFetch('/api/v1/routines', { auth: true });
const templates = await apiFetch('/api/v1/routine-templates?category=FULL_BODY&difficulty=BEGINNER');
```

- 사용자 루틴 생성/수정/삭제에도 `auth: true`를 지정한다. 본문에 사용자 ID를 보내지 않는다.
- 토큰 만료는 `AUTH_TOKEN_EXPIRED`, 없거나 잘못된 토큰은 `AUTH_INVALID_TOKEN`이다. 기존 프론트의 refresh 처리와 연결된다.
- 사용자 루틴은 ID·소유자·USER 타입을 함께 조회한다. 다른 사용자 루틴과 템플릿에 대한 사용자 CRUD는 404다.
- `code`로 오류를 구분하고 `detail`을 표시한다. 필드 검증 오류는 `errors`에 포함된다.
- 로그인 후 루틴 사용에는 온보딩 완료 여부를 추가로 요구하지 않는다. 온보딩 저장 및 자동 로그인 복원은 별도 범위다.

## ERD

```text
routine.exercise (1) ─── (N) routine.routine_exercise (N) ─── (1) routine.routine
                                     │
                                    (1)
                                     │
                                    (N)
                              routine.routine_set
```

| 테이블 | 주요 필드 및 책임 |
| --- | --- |
| exercise | 문자열 ID, 이름, REPS/DURATION, 화면 가이드, intro_tts_text, 활성 여부 |
| routine | UUID, owner_user_id, template_id, category, USER/TEMPLATE, 이름, 난이도, 시각, 낙관적 잠금 버전 |
| routine_exercise | 루틴 FK, 운동 FK, exercise_order |
| routine_set | 루틴 종목 FK, set_order, target_reps 또는 target_duration_seconds, weight_kg, rest_seconds |

`owner_user_id`는 외부 도메인에 대한 FK를 갖지 않는다. 루틴 종목의 순서와 세트 순서는 각각 부모 내에서 유일하다. 세트 수는 자식 행 개수로 계산한다. 같은 운동 종목이 서로 다른 순서에 반복 등장할 수 있다.

운동 가이드는 운동 종목에 저장하며 루틴 상세에 포함한다. `intro_tts_text`는 TTS 엔진이 읽을 원문이다. 음성 합성 및 음성 파일 저장은 포함하지 않는다. 세트별 반복 수·시간 안내는 클라이언트가 목표 데이터로 조합할 수 있다.

운동 기록은 Workout 도메인이 소유한다. 기록을 구현할 때 운동 시작 시점의 계획을 스냅샷으로 저장해야 이후 루틴 수정·삭제가 과거 기록에 영향을 주지 않는다.

## API 동작

Base path: `/api/v1`

| Method | Path | 성공 | 인증 |
| --- | --- | --- | --- |
| GET | /exercises | 200 | 공개 |
| GET | /exercises/{exerciseId} | 200 | 공개 |
| GET | /routines?page=0&size=20 | 200 | 필요 |
| POST | /routines | 201 + Location | 필요 |
| GET | /routines/{routineId} | 200 | 필요 |
| PATCH | /routines/{routineId} | 200 | 필요 |
| DELETE | /routines/{routineId} | 204 | 필요 |
| GET | /routine-templates | 200 | 공개 |

- 루틴 목록은 생성일과 ID 내림차순이다. 페이지는 0부터, 크기는 1~100이다.
- 생성은 `templateId`가 필수다. 이름·난이도·종목·순서·무게·휴식은 서버가 템플릿에서 복사한다.
- 사용자는 종목별 세트 수(1~20), 반복 횟수 또는 유지 시간(양의 정수)만 변경할 수 있다.
- 생성의 `exercises`는 선택 항목이다. 생략/null/빈 배열이면 템플릿 기본값을 그대로 저장한다.
- 생성 시 `templateExerciseId`는 템플릿 응답 `exercises[].id`, 수정 시 `routineExerciseId`는 **내 루틴 응답** `exercises[].id`다. 운동 코드(`squat` 등)와 다르다.
- 요청에서 빠진 종목은 유지한다. 전달한 배열 순서로 종목을 재배치하지 않는다.
- 같은 종목 항목을 중복 지정하거나 다른 템플릿/루틴의 종목 ID를 보내면 400이다.
- 푸시업·스쿼트는 `targetReps`, 플랭크는 `targetDurationSeconds`만 변경 가능하다. 지정한 목표는 해당 종목의 모든 세트에 동일하게 적용한다.
- 항목마다 변경할 값을 하나 이상 전달한다. 생략/null인 값은 유지한다. 세트 수만 늘리면 마지막 기존 세트의 목표·무게·휴식을 복사한다.
- `name`, `difficulty`, `category`, `exerciseId`, `order`, `sets`, `weightKg`, `restSeconds` 등 허용하지 않는 요청 필드는 400으로 거부한다.
- 저장된 사용자 루틴은 템플릿에서 독립된 스냅샷이다. 이후 원본 템플릿 변경은 이미 저장된 루틴에 반영하지 않는다.

### 난이도별 기본 템플릿

전신·하체·코어 각각 초급·중급·고급으로 총 9개를 제공한다. 기존 초급 전신 템플릿은 유지하며 V3 마이그레이션에서 8개를 추가한다.

| 난이도 | 종목당 세트 | 푸시업 | 스쿼트 | 플랭크 | 세트 후 휴식 |
| --- | --- | --- | --- | --- | --- |
| BEGINNER (초급) | 2 | 10회 | 15회 | 30초 | 60초 |
| INTERMEDIATE (중급) | 3 | 12회 | 20회 | 40초 | 60초 |
| ADVANCED (고급) | 4 | 15회 | 25회 | 45초 | 60초 |

- 전신: 푸시업 → 스쿼트 → 플랭크. 하체: 스쿼트. 코어: 플랭크.
- 모두 무게 0kg이며, 위 수치는 제품의 초기 설정값이다. 사용자는 자신의 루틴으로 저장할 때 조절할 수 있다.
- `GET /api/v1/routine-templates`는 전체 9개를 반환한다.
- `GET /api/v1/routine-templates?difficulty=BEGINNER`는 초급 전신·하체·코어 3개를 반환한다. `INTERMEDIATE`, `ADVANCED`도 지원하며 잘못된 값은 400이다.
- `category=FULL_BODY|LOWER_BODY|CORE` 필터로 전신·하체·코어를 선택한다. category와 difficulty를 함께 전달하면 해당 종류·난이도의 템플릿 1개를 반환한다.
- 선택한 템플릿의 `id`를 생성 요청의 `templateId`로 보낸다. 난이도는 원본 템플릿 기준으로 유지하며 사용자가 별도로 수정할 수 없다.

### 프론트 사용자 흐름

루틴 종류 선택 → 난이도 선택 → 템플릿 조회 → 종목별 세트 수·목표 조절 → 내 루틴 저장 → Workout에서 운동 시작.

```http
GET /api/v1/routine-templates?category=FULL_BODY&difficulty=BEGINNER
```

### 생성 예시

기본값 그대로 저장:

```json
{
  "templateId": "00000000-0000-0000-0000-000000000001"
}
```

초급 전신 템플릿에서 스쿼트만 3세트 × 12회로 변경하고 다른 종목은 그대로 저장:

```json
{
  "templateId": "00000000-0000-0000-0000-000000000001",
  "exercises": [
    {
      "templateExerciseId": "00000000-0000-0000-0001-000000000002",
      "setCount": 3,
      "targetReps": 12
    }
  ]
}
```

### PATCH

`exercises`는 필수이며 빈 배열/null을 허용하지 않는다. 미지정 종목은 유지하고 요청에 있는 종목의 세트 수·목표만 수정한다. 종목 ID와 순서는 보존한다. 아래 UUID는 예시이며 실제 내 루틴 응답의 `exercises[].id`로 교체해야 한다.

```json
{
  "exercises": [
    {
      "routineExerciseId": "11111111-1111-1111-1111-111111111111",
      "setCount": 3,
      "targetDurationSeconds": 25
    }
  ]
}
```

모든 변경은 한 트랜잭션으로 처리한다. 동시 변경 충돌은 409다. 종목별로 다른 세트 목표를 지정하는 방식은 지원하지 않는다.

**이전 API와 호환되지 않는 변경:** 이름·난이도·종목 배열·세트 배열을 직접 전달하던 생성/수정 요청을 위 형식으로 변경해야 한다. 응답의 중첩 `exercises[].sets[]` 구조는 유지하고 루틴 상세·목록에 `templateId`, `category`를 추가했다.

### 오류 응답

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "요청 값이 올바르지 않습니다.",
  "code": "INVALID_INPUT",
  "errors": [{"field": "name", "message": "공백일 수 없습니다"}]
}
```

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | INVALID_INPUT | JSON, 필드 검증, 경로·쿼리 형식 오류 |
| 400 | INVALID_ROUTINE | 종목 순서·측정 방식 등 도메인 규칙 위반 |
| 401 | AUTH_INVALID_TOKEN | 토큰 없음/위조/잘못된 토큰 |
| 401 | AUTH_TOKEN_EXPIRED | 만료된 토큰. refresh 후 재시도 |
| 401 | AUTHENTICATION_REQUIRED | 인증 Principal의 내부 사용자 ID가 유효하지 않음 |
| 404 | TEMPLATE_NOT_FOUND | 없거나 TEMPLATE 타입이 아닌 원본 ID |
| 404 | EXERCISE_NOT_FOUND | 없거나 비활성인 종목 |
| 404 | ROUTINE_NOT_FOUND | 본인 소유의 USER 루틴 없음 |
| 409 | ROUTINE_CONFLICT | 동시 변경 충돌 |

## DB 마이그레이션과 테스트

`RoutineSchemaConfig`가 `ModuleSchema("routine")`를 등록한다. app의 `ModuleMigrations`가 `identity`와 `routine`에 대해 Flyway를 각각 실행하며 이력도 각 스키마의 `flyway_schema_history`에 저장한다. 기존 routine V1~V3 SQL과 이력은 그대로 사용하고 V4에서 원본 템플릿 ID와 종류를 추가한다. 기존 자유 생성 사용자 루틴은 templateId/category를 NULL로 보존한다. 원본을 임의로 연결하지 않으며 기존 구성에서 세트 수·목표 수정과 조회·삭제는 가능하다. 새로 생성되는 루틴은 반드시 템플릿을 사용한다. Hibernate는 두 모듈의 마이그레이션 완료 후 매핑을 검증한다. Spring Boot Flyway 자동설정은 사용하지 않는다.

```bash
./gradlew test
./gradlew build
```

통합 테스트는 공통 `@IntegrationTest`와 Testcontainers PostgreSQL을 사용한다. Docker가 필요하며 이미지 버전은 `compose.yaml`을 따른다. 실제 보안 필터와 서명된 JWT를 사용해 루틴 CRUD·소유권·토큰 만료·공개 경로·오류 응답·Swagger를 확인한다. `SocialLoginRoutineTests`는 소셜 제공자 통신만 대체하고 가입 → 토큰 발급 → 루틴 생성 → 토큰 갱신 → 수정·삭제를 실행한다.

루틴 복사, 오늘 계획된 운동, AI 추천 및 TTS 음성 합성은 후속 범위다.

## 로컬 프론트 연동 확인

- Expo 프론트의 `EXPO_PUBLIC_API_URL`은 백엔드 주소를 가리킨다. 현재 로컬은 `http://localhost:3003`.
- 웹 테스트 시 백엔드 `CORS_ALLOWED_ORIGINS=http://localhost:8081`을 지정한다. 여러 origin은 쉼표로 구분한다. 네이티브 앱은 CORS 대상이 아니다.
- 휴대폰에서는 localhost 대신 개발 PC의 LAN IP를 사용하고 같은 Wi-Fi에 연결한다.
- Docker DB 공개 포트는 `DB_PORT`로 지정하며 localhost에만 바인딩된다. 컨테이너 간 DB 통신은 계속 postgres:5432를 사용한다.
- JWT 키 파일 권한이 600이면 로컬 `.env`의 `APP_UID`, `APP_GID`를 `id -u`, `id -g` 결과로 맞춘다. 운영에서는 마운트 키의 소유권을 실행 사용자에 맞춘다.
- `.env`와 JWT 키는 Git에 포함하지 않는다. Expo에는 공개 client ID와 redirect URI만 넣고 client secret/private key는 넣지 않는다.

### 실기기 인수 확인 순서

1. `docker compose up -d --build` 후 `/actuator/health`와 `/swagger-ui.html`을 확인한다.
2. 프론트에서 `npm ci`, `npx expo start --clear`를 실행한다. Expo Go/개발 빌드 지원 여부는 현재 Expo SDK에 맞춘다.
3. 구글 또는 카카오 로그인 후 루틴 탭을 연다. OAuth 콘솔의 redirect URI는 프론트와 백엔드 허용 목록에 등록한 값과 정확히 같아야 한다.
4. 전신 → 초급 → 이 루틴 선택에서 푸시업 3세트·12회, 플랭크 2세트·40초로 변경해 저장한다.
5. 내 루틴에서 수정 버튼으로 다시 조회하여 값이 유지되는지 확인한다. 플랭크를 45초로 수정해 저장하고 다시 조회한다.
6. 시작 안내 미리 듣기를 눌러 종목별 준비 자세·수행 방법 음성을 확인한다. 안내 중지와 다른 탭 이동 시 정지되는지 확인한다.
7. 삭제 확인 후 목록에서 사라지는지 확인한다. 다른 계정으로 로그인했을 때 이전 계정의 루틴이 보이면 안 된다.

이 화면의 TTS는 안내 미리 듣기다. 실제 운동의 종목 시작·세트 종료·다음 종목 전환 이벤트 연결과 이어하기는 운동 실행 화면/운동 세션 서비스에서 별도로 구현한다. 자동화 테스트의 소셜 제공자 응답은 모의 응답이며 실제 구글·카카오 로그인과 실기기 음성 출력 확인을 대체하지 않는다.


## 독립 서비스 배포

`services/routine`은 루틴 실행 앱과 구현 코드를 함께 포함하고 `shared/runtime`을 사용한다. 인증 구현·소셜 시크릿·JWT 개인 키는 포함하지 않는다. Compose의 `routine-service`에서 실행하고 `routine` 스키마만 마이그레이션한다. 인증 서비스와는 사용자 UUID(`sub`) 및 JWT 공개 키 계약으로 연결된다.

프론트는 gateway 주소를 유지한다. `integration-tests` 디렉터리는 두 도메인의 회귀 테스트 전용이며 실제 배포 대상이 아니다. 회귀 테스트 외에 독립 컨테이너별 health, Swagger 경로 분리, 공개 키만으로 JWT CRUD, Flyway 이력을 실제 실행 환경에서 확인한다.
