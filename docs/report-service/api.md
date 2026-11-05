# Report Service

독립 서비스 `services/report`, 기본 포트 3008, DB 스키마 `report`. 게이트웨이는 `/api/v1/reports`를 Report로 전달하고 Swagger UI에 Report Service를 표시한다. 인증은 바로핏 Bearer JWT를 사용한다. Workout과 Routine의 사용자 데이터를 같은 JWT로 조회하며 조회 실패를 빈 운동 기록으로 취급하지 않는다.

| API | 기능 |
| --- | --- |
| `GET /api/v1/reports/calendar?year=2026&month=10` | 월별 날짜·상태, 완료/부분 실행/미실행 일수와 현재 연속 운동 일수 |
| `GET /api/v1/reports/history?year=2026&month=10&page=0&size=10` | 월간 운동 일지와 미실행 계획, `hasNext`로 다음 페이지 확인 |
| `GET /api/v1/reports/dates/2026-10-20` | 날짜별 운동 일지 |
| `GET /api/v1/reports/sessions/{sessionId}/result` | 운동별 수행 결과·점수·자세 항목 집계·사용자 피드백 |
| `PUT /api/v1/reports/sessions/{sessionId}/feedback` | 주관적 난이도와 새 불편 부위 저장/수정 |
| `GET /api/v1/reports/exercises/{exerciseId}/scores?limit=3` | 본인 종목 점수의 운동별 이력 |
| `POST /api/v1/reports/sessions/{sessionId}/sets/{setId}` | 온디바이스 `set.completed` 2.0 원문 저장 |
| `GET /api/v1/reports/sessions/{sessionId}/sets` | 세트별 원문 결과 |
| `GET /api/v1/reports/sessions/{sessionId}/summary` | 자세 항목별 집계 |
| `GET /api/v1/reports/daily?date=2026-10-20` | 일간 자세 결과 집계 |
| `GET /api/v1/reports/weekly?startDate=2026-10-19` | 지정일부터 7일간 자세 결과 집계 |

달력 상태는 `COMPLETED`(종목 완료 기준 충족), `PARTIAL`(1세트 이상 수행했으나 완료 기준 미달), `MISSED`(지난 날짜에 계획만 있고 수행 없음), `PLANNED`(오늘 또는 미래의 계획), `NONE`(계획과 수행 없음)이다. 날짜·집계는 `Asia/Seoul` 기준이다. 같은 날 여러 세션이면 완료가 부분 실행보다 우선하며 월간 카운트는 날짜 수다. 현재 연속 운동 일수는 오늘이 미완료일 때 어제까지의 연속 완료를 포함한다. 운동 이력의 페이지 단위는 세션이며, 미실행 계획은 `sessionId: null`이다.

피드백 예시는 `{"difficulty":"MODERATE","discomfortAreas":["ARM_WRIST"]}`다. 난이도는 `EASY`, `MODERATE`, `HARD`, 불편함이 없다면 빈 배열 `[]`을 보낸다. 별도 기록으로 보관하며 온보딩 프로필을 자동 변경하지 않는다. 저장은 종료된 본인 세션만 가능하다.

종목 점수는 세트별 AI 결과로 자동 계산한다. 같은 자세 항목의 모든 수행 세트에서 `compliant`와 `warning`을 각각 합산한 뒤 `100 × compliant / (compliant + warning)`을 구하고, 판정 가능한 항목들의 산술 평균을 사용한다. 항목 간 가중치는 없으며 `unknown`, `notApplicable`은 분모에서 제외한다. 판정 가능한 항목이 없거나 같은 항목의 집계 기준·단위가 세트 사이에서 서로 다르면 점수는 null이다. 운동이 종료되고 실제 수행한 모든 세트의 AI 결과가 연결되어야 종목 점수가 나온다. 모든 계획 종목에 점수가 있으면 `routineScore`는 종목 점수의 산술 평균이며, 누락되면 null과 함께 `scoredExercises`, `plannedExercises`를 반환한다. 미수행 종목은 0점으로 처리하지 않는다.

세트 업로드 시 `X-Routine-Set-Id` 헤더에 운동 세션 스냅샷의 해당 `sets[].id`를 전달한다. URL의 `setId`는 결과 업로드용 고유 ID이며 계획 세트 ID와 구별된다. 서버는 본인 세션의 세트 및 AI 종목 코드 일치를 확인하고 계획 세트당 결과 하나만 허용한다. 헤더가 없는 기존 결과는 그대로 보관하지만 점수에는 사용하지 않는다. 같은 원문·수행 시각·결과 ID로 재전송하며 헤더를 추가하면 기존 결과를 연결할 수 있다.

AI의 원본 `set.completed` JSON은 변경하지 않는다. 오프라인 결과의 실제 절대 수행 시각은 HTTP 헤더 `X-Workout-Performed-At: 2026-10-20T09:30:00+09:00`로 전달한다. 동일 세트 재전송에는 같은 시각을 사용한다. 헤더가 없으면 수신 시각을 사용하므로 오프라인 날짜 집계가 달라질 수 있다. `timestampMs`는 모델 내부 시간으로 절대 날짜로 취급하지 않는다. `frame.result`는 기기 내부 실시간 처리용이며 업로드 대상이 아니다.

Report 최초 배포 시 Flyway는 기존 `analytics.exercise_set_results`와 `analytics.posture_item_results`를 `report` 스키마에 복사하고 기존 테이블은 보존한다. 운영 전환 시 기존 리포트 쓰기를 중지하고 복사 완료를 확인한 뒤 게이트웨이 라우팅을 Report로 전환해야 복사 중 새로 기록된 데이터가 누락되지 않는다. Workout의 완성 운동 Analytics 집계와 Report 자세 상세 업로드는 별도 흐름이다.

## 운동 종료 후 리포트

1. 앱은 실제 휴식 시간을 측정해 Workout 세션 동기화 요청의 `restDurationSeconds`(초)에 담는다. 세트·종목 사이에 실제 휴식한 시간의 합계이며 앱 일시정지 시간은 제외한다. 측정하지 않은 경우 `null` 또는 생략한다. 예정 휴식이나 전체 경과 시간에서 추산하지 않는다. 종료된 세션은 수정할 수 없으므로 종료 동기화에 포함해야 한다.
2. 앱은 수행한 모든 세트에 대해 Workout `errorCounts`와 Report의 AI `set.completed`를 동기화한다. `errorCounts`는 AI가 실제로 제공한 발생 횟수만 전송한다. AI 2.0의 준수율과 경고 시간만으로 오류 발생 횟수를 유추하지 않는다. 빈 객체는 오류가 없다는 사실을 보증하지 않는다.
3. `POST /api/v1/reports/sessions/{sessionId}/generated`를 호출해 종료 세션의 리포트를 생성한다. 수행 세트가 없거나 AI 결과가 누락된 경우 `409`이며, 누락 결과를 동기화한 후 재시도한다. 중도 종료된 세션도 실제 수행한 부분을 기준으로 리포트를 만들 수 있다.
4. `GET /api/v1/reports/sessions/{sessionId}/generated`는 저장된 동일 리포트를 반환하며, 생성 전에는 `404`다. POST 재시도 역시 저장된 리포트를 반환한다. 본인 세션만 조회할 수 있다. 기존 `GET .../result`도 실제 휴식 시간을 반환한다.

응답에는 전체 피드백, 세트 수, 실제 휴식 시간, 루틴 점수와 종목별 수행 수치·점수·자세 항목별 준수율·오류 코드별 횟수·잘한 점·개선점·다음 운동 목표가 포함된다. 문장은 데이터에 근거한 `TEMPLATE_V1`로 생성하고 저장한다. 미수행 종목과 평가 불가 항목은 점수를 추정하지 않는다. 그래프는 숫자 필드를 이용하며 `null`은 미측정 또는 평가 불가로 표시한다. 최초 생성 후 리포트는 고정되므로 생성 후 변경된 원천 데이터는 자동 반영하지 않는다.
