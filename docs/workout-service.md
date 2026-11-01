# Workout Service MVP

`services/workout`은 독립 실행 서비스이며 단일 PostgreSQL의 `workout` 스키마만 소유한다. 자세 인식·횟수 판정은 기기의 모델이 수행한다. 서버는 시작 시점 루틴 스냅샷, 운동 상태, 세트별 요약 결과를 저장하고 온라인 코칭 음성을 생성한다. 원본 영상과 관절 프레임은 업로드하지 않는다.

## REST API

Base Path: `/api/v1/workout-sessions`. 모든 요청에는 바로핏 Access Token이 필요하다. Swagger는 게이트웨이의 `/workout/v3/api-docs`, 서비스 직접 접속의 `/swagger-ui.html`에서 볼 수 있다.

| Method | 경로 | 기능 |
| --- | --- | --- |
| PUT | `/{sessionId}` | 세션 생성·상태/세트 결과 동기화. 기기가 UUID 생성 |
| GET | `/{sessionId}` | 상세·이어하기 |
| GET | `/` | 최근 세션 최대 100개 |
| GET | `/weekly-progress?weekStart=YYYY-MM-DD` | 월요일 시작 주의 운동 완료 날짜 |

앱은 루틴 서비스의 `GET /api/v1/routines/{id}` 또는 `GET /api/v1/routine-templates/{id}` 응답 전체를 운동 시작 시 저장하고 `routineSnapshot`으로 보낸다. 템플릿과 내 루틴 모두 시작할 수 있다. 첫 세션 저장 시 Workout은 같은 사용자의 Access Token으로 Routine 서비스를 조회해 원본 루틴과 제출한 스냅샷이 일치하는지 확인한다. JSON 숫자의 소수 표기와 루틴 생성 직후 DB 저장 시 달라질 수 있는 `createdAt`·`updatedAt` 정밀도는 비교에서 제외하지만 종목·세트·가이드 내용은 비교한다. 이후 갱신에서는 시작 시점 스냅샷을 유지하므로 루틴이 수정·삭제돼도 이미 생성한 세션은 계속 동기화할 수 있다. 다만 최초 업로드가 오프라인 운동 종료 후에 이뤄지고 그 사이 원본 루틴이 수정·삭제됐다면 409 또는 404가 반환된다. 이 경우까지 보존하려면 운동 시작 전에 온라인으로 세션을 생성하거나, 별도 서명된 루틴 버전 기능이 필요하다.

```json
{
  "routineId": "00000000-0000-0000-0000-000000000001",
  "routineSnapshot": {"id":"00000000-0000-0000-0000-000000000001","name":"초급 전신","exercises":[{"exerciseId":"push-up","sets":[{"id":"00000000-0000-0000-0001-000000000001"}]}]},
  "status": "COMPLETED",
  "revision": 2,
  "startedAt": "2026-10-20T09:00:00Z",
  "endedAt": "2026-10-20T09:10:00Z",
  "activeDurationSeconds": 540,
  "sets": [{
    "routineSetId": "00000000-0000-0000-0001-000000000001",
    "actualReps": 12,
    "actualDurationSeconds": null,
    "feedbackCodes": ["HIP_DROP"],
    "errorCounts": {"HIP_DROP": 10},
    "feedbackText": "골반 처짐 경고가 있었어요.",
    "modelVersion": "mobile-stream-1.0",
    "referenceModelId": "bcd336890a294228fba459473b6bd7cf7cbdf0c73ca5602d00ec8cde8a9ff1d3",
    "trackingAvailablePct": 92.8,
    "completedAt": "2026-10-20T09:03:00Z"
  }]
}
```

`status`는 `ACTIVE`, `PAUSED`, `COMPLETED`, `ABANDONED`다. 종료 상태에만 `endedAt`을 지정한다. 앱은 세트·일시정지·이어하기·종료 시마다 `revision`을 1씩 올려 **전체 최신 상태**를 PUT한다. 같은 세션 ID/revision/내용의 재전송은 멱등이고, 오래된 revision 또는 같은 revision의 다른 내용은 409다. 종료 뒤 수정할 수 없다. 오프라인에서는 같은 정보를 기기에 저장하고 연결이 복구되면 최종 상태를 한 번에 PUT할 수 있다. 전송 성공 응답을 받기 전까지 기기 사본을 보관해야 한다.

세트 결과에는 `set_result.json`의 `count`, `elapsed_video_ms`, `error_counts`, `primary_feedback`, `runtime.rules_version`, `runtime.reference_model_id`, `quality.tracking_available_pct`에서 실제 횟수, 실제 시간, 오류 횟수, 간단한 피드백, 모델 버전·ID와 추적 품질을 추려 보낸다. 샘플의 `frames.jsonl`, `repetitions`, `clips`, `recording`, `source.file_name`은 이 API에 보내지 않는다.

샘플의 `exercise_id`는 `pushup`이지만 루틴 카탈로그의 종목 ID는 `push-up`이다. 앱은 현재 루틴의 `sets[].id`를 `routineSetId`로 보내고, 모델 결과를 그 세트에 연결해야 한다. WebSocket 종목 시작 이벤트는 두 푸시업 코드 모두 받아들인다.

## WebSocket 코칭

Raw WebSocket: `ws(s)://{host}/ws/coaching`. 연결 직후 첫 메시지로 인증한다.

```json
{"type":"AUTH","accessToken":"<바로핏 Access Token>","sessionId":"<PUT으로 저장된 세션 UUID>"}
```

인증 성공 시 `AUTH_OK`를 보낸다. Workout 서비스는 JWT를 Identity 서비스의 `/api/v1/users/me`에 전달해 온보딩의 `coachingStyle`을 연결당 한 번 조회한다. 응답의 `coachingStyle`은 `FRIENDLY`, `CALM`, `SPARTA`, `ANALYTICAL` 중 하나다. 프로필 조회가 실패하거나 온보딩 전이면 `CALM`을 사용하고 운동은 계속한다. 앱이 임의로 보낸 스타일 값은 사용하지 않는다. 이후 기기의 모델 또는 화면이 이벤트를 보낸다. 세션을 먼저 PUT으로 저장해야 한다.

```json
{"type":"COACH_EVENT","eventType":"EXERCISE_START","exerciseId":"push-up"}
{"type":"COACH_EVENT","eventType":"REP_FEEDBACK","feedbackCode":"HIP_DROP"}
{"type":"COACH_EVENT","eventType":"SET_FEEDBACK","feedbackCode":"HIP_DROP"}
{"type":"COACH_EVENT","eventType":"SET_FEEDBACK","feedbackCode":"KNEE_IN","actualReps":12}
```

`WORKOUT_START`, `EXERCISE_START`, `REP_FEEDBACK`, `SET_FEEDBACK`, `WORKOUT_FINISH`를 지원한다. 운동 안내는 스냅샷의 `introTtsText`에 스타일별 도입 문구를 붙인다. 반복 중 즉시 교정은 판정 코드·스타일별 고정 문장을 사용한다. 세트 종료 시에는 검증된 판정 코드의 서버 정의 의미, 온보딩 스타일, 선택적 `actualReps`(0~1000)·`actualDurationSeconds`(0~3600)만 OpenAI Responses API에 전달해 한 문장의 코칭을 만든다. 기기의 임의 자유 문장·사용자 ID·영상은 OpenAI에 보내지 않는다. 현재 지원 코드는 `HIP_DROP`, `ELBOW_FLARE`, `SHALLOW_RANGE`, `KNEE_IN`, `GOOD_FORM`이다. 미지원 코드는 세트 완료 고정 문장으로 대체한다. OpenAI 키가 없거나 호출·출력 검증이 실패하면 기존 스타일별 문장으로 대체한다. OpenAI 생성 문장은 DB의 `feedback_text`에 저장하지 않으며, 해당 필드는 기기가 동기화한 원본 세트 요약 전용이다. AI의 판정 코드와 세트 기록은 바꾸지 않는다. 같은 연결에서 동일 이벤트·문장은 4초 이내 재생성하지 않는다. 늦게 끝난 코칭 응답은 새 이벤트가 발생했다면 전송하지 않는다. 운동 종료 안내는 세션을 `COMPLETED`로 저장한 직후에도 요청할 수 있다.

서버는 ElevenLabs에 텍스트를 보내 MP3를 받은 뒤 다음 JSON을 Push한다. API 키는 서버 환경변수로만 관리한다. [ElevenLabs TTS API](https://elevenlabs.io/docs/api-reference/text-to-speech/convert)의 `xi-api-key` 헤더와 MP3 출력 형식을 사용한다.

```json
{"type":"REP_FEEDBACK","text":"골반이 아래로 처졌어요. 몸통을 곧게 유지해 주세요.","audioBase64":"<MP3 Base64>","timestamp":"2026-10-20T09:01:00Z"}
```

설정값은 `ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID`, 선택값 `ELEVENLABS_MODEL_ID`다. 미설정·외부 API 실패 시 `text`만 보낸다. 이 경우와 오프라인에서는 프런트의 Expo Speech 대체 재생이 필요하다.

OpenAI 설정값은 `OPENAI_API_KEY`와 선택값 `OPENAI_MODEL`(기본 `gpt-6-luna`)이다. 키는 Workout 서비스 환경변수로만 전달한다. Responses API 요청에 `store:false`와 JSON Schema 출력 형식을 사용한다. 우리 DB와 애플리케이션 로그에 생성 문장을 남기지 않아도 외부 제공자의 자체 보관 정책은 별도로 적용된다. [OpenAI Responses API](https://developers.openai.com/api/docs/guides/text), [OpenAI 데이터 관리](https://developers.openai.com/api/docs/guides/your-data).

프런트 `dev`(확인 시점 `3b479a5`)에는 `/ws/coaching` 수신과 `audioBase64` MP3 재생이 있지만, `AUTH`/`COACH_EVENT` 송신과 오디오 실패 시 Expo Speech 대체는 아직 없다. 프런트 담당자가 이 메시지 송신 및 대체 동작을 연결해야 실제 코칭이 들린다.

주간 완료 날짜는 종료된 세션에서 예정된 모든 세트를 수행한 종목이 전체 종목의 약 70%에 도달했을 때만 반환한다. 세트의 실제 횟수 또는 시간이 1 이상이면 그 세트를 수행한 것으로 본다. 필요한 종목 수는 `max(1, round(종목 수 × 0.7))`로 계산하므로 3종목 중 2종목을 모두 수행하면 완료다. 세트가 없는 항목은 종목 수에서 제외한다. 완료일은 세션 종료 시각의 Asia/Seoul 날짜이며 같은 날짜는 한 번만 반환한다. `ABANDONED` 세션도 기준을 충족하면 포함하고, 오프라인 운동은 동기화 후 반영된다. Routine 서비스의 `/api/v1/workout-plans/weekly-progress`는 이 날짜와 온보딩 주간 목표를 합쳐 홈 화면에 제공한다.

같은 70% 기준을 충족한 종료 세션은 Workout DB의 outbox에 넣고 Analytics Service로 자동 전달한다. Analytics는 세션 ID를 중복 방지 키로 사용해 운동 기록·일별 통계·달력을 각 한 번만 갱신한다. Analytics가 잠시 중단되면 outbox가 재시도한다. 서비스 간 요청은 `ANALYTICS_INTERNAL_TOKEN`으로 인증하며, 이 값이 없으면 전달을 시작하지 않는다. 개발 환경에서는 `SMOKE_BASE_URL=http://127.0.0.1:3003 node scripts/verify-workout.cjs`로 오프라인 업로드·재전송·개인별 조회·집계를 확인할 수 있다. 외부 음성 검증은 `SMOKE_EXPECT_GPT_AUDIO=1`을 추가한다. ElevenLabs 사용량이 부족한 환경에서는 `SMOKE_EXPECT_GPT_TEXT=1`로 GPT 문장과 자막 전용 대체 경로를 확인한다.

운동 세션 동기화에 `restDurationSeconds`를 선택적으로 전달할 수 있다. 앱이 측정한 실제 휴식 시간 합계(초)이며, 일시정지 시간을 제외한다. 값이 없으면 미측정으로 보존한다. 종료 세션은 수정할 수 없으므로 종료 동기화 시 포함한다.
