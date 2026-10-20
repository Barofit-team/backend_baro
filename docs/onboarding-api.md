# 온보딩 API 프론트 연동 가이드

- 작성일: 2026-10-20
- 이슈: #10 (온보딩 페이지 구현)
- 서버: identity 서비스. 게이트웨이 주소(`EXPO_PUBLIC_API_URL`) 하나로 호출한다.
- 공통 규칙(토큰, 에러 형식, refresh 처리)은 [identity-service.md 4.1](identity-service.md#41-공통-규칙)과 같다.

## 1. 화면 흐름

```
소셜 로그인(social-login/code)
  ├─ isOnboarded = true  → 홈
  └─ isOnboarded = false → 온보딩
        1) 닉네임: 진입 시 GET /nicknames/random 으로 기본값 채움
           입력이 바뀌면 GET /nicknames/availability 로 확인(디바운스 권장)
        2) 신체 정보 → 3) 운동 목표 → 4) 운동 경험 → 5) 운동 빈도 → 6) 코칭 스타일
        7) POST /me/onboarding (전체를 한 번에 전송) → 201 → 홈

앱 재시작: 저장된 토큰으로 GET /me → isOnboarded로 홈/온보딩 결정
```

- 온보딩 값은 **마지막에 한 번만** 보낸다. 단계별 임시 저장 API는 없으므로 입력 중인 값은 앱 상태에 둔다.
- 닉네임 확인과 랜덤 생성은 닉네임을 **예약하지 않는다.** 확인 뒤 저장 전에 다른 사람이 가져가면 저장 시 409가 난다.
- 온보딩 전에는 `nickname`에 SNS 닉네임(또는 null)이 들어 있다. 입력칸 기본값으로는 랜덤 닉네임을 쓴다.

## 2. API 요약

모두 `Authorization: Bearer {accessToken}`이 필요하다.

| Method | Path | 성공 | 설명 |
|---|---|---|---|
| GET | `/api/v1/users/me` | 200 | 내 정보와 온보딩 여부·프로필 |
| GET | `/api/v1/users/nicknames/random` | 200 | 사용 가능한 랜덤 닉네임 |
| GET | `/api/v1/users/nicknames/availability?nickname=` | 200 | 닉네임 사용 가능 여부 |
| POST | `/api/v1/users/me/onboarding` | 201 | 온보딩 저장. 한 번만 가능 |

## 3. 입력 항목과 값

| 필드 | 타입 | 필수 | 규칙 / 값 |
|---|---|---|---|
| `nickname` | string | O | 2~10자, 한글(완성형)·영문·숫자. 공백·특수문자·이모지 불가. 중복은 대소문자 무시 |
| `gender` | string | O | `MALE`, `FEMALE` |
| `birthYear` | number | O | 1900 ~ 올해 |
| `heightCm` | number | O | 100.0 ~ 250.0, 소수 첫째 자리까지 |
| `weightKg` | number | O | 20.0 ~ 300.0, 소수 첫째 자리까지 |
| `skeletalMuscleKg` | number \| null | X | 골격근량. 5.0 ~ 150.0, 소수 첫째 자리까지, 몸무게보다 작아야 함 |
| `bodyFatPercent` | number \| null | X | 체지방률. 1.0 ~ 70.0, 소수 첫째 자리까지 |
| `goals` | string[] | O | 운동 목표, 1개 이상 복수 선택. `WEIGHT_LOSS`(체중 감량), `MUSCLE_GAIN`(근육량 증가), `HEALTH_CARE`(건강관리) |
| `experienceLevel` | string | O | 운동 경험. `NONE`(없음), `BEGINNER`(초급), `INTERMEDIATE`(중급), `ADVANCED`(고급). NONE 외에는 루틴 난이도(`difficulty`)와 같은 값 |
| `weeklyWorkoutDays` | number | O | 희망 운동 빈도(주 n회). 1 ~ 7 |
| `coachingStyle` | string | O | `SPARTA`(스파르타), `GENTLE`(상냥한), `FRIENDLY`(친구같은) |

- 화면 문구(한글 라벨)는 앱이 가진다. 서버는 enum 값만 주고받는다.
- 목표 부위/불편 부위는 보류됐다. 나중에 필드가 추가된다.

## 4. 상세

### 4.1 `GET /api/v1/users/me`

```json
{
  "id": "0199e0a0-0000-7000-8000-000000000001",
  "nickname": "바로핏",
  "profileImageUrl": null,
  "role": "USER",
  "isOnboarded": true,
  "profile": {
    "gender": "FEMALE",
    "birthYear": 1998,
    "heightCm": 163.5,
    "weightKg": 55.2,
    "skeletalMuscleKg": 21.3,
    "bodyFatPercent": 24.8,
    "goals": ["WEIGHT_LOSS", "HEALTH_CARE"],
    "experienceLevel": "NONE",
    "weeklyWorkoutDays": 3,
    "coachingStyle": "FRIENDLY"
  }
}
```
- 온보딩 전이면 `isOnboarded: false`, `profile: null`이다.
- `goals`의 순서는 보장하지 않는다.

### 4.2 `GET /api/v1/users/nicknames/random`

```json
{ "nickname": "날쌘스쿼트42" }
```
- 형용사 + 운동·동물 이름 + 숫자 형태이며 항상 닉네임 규칙을 만족한다.
- 다시 뽑기 버튼은 이 API를 다시 호출하면 된다.

### 4.3 `GET /api/v1/users/nicknames/availability?nickname=바로핏`

```json
{ "nickname": "바로핏", "available": false, "reason": "DUPLICATED" }
```

| `reason` | 의미 | 안내 예시 |
|---|---|---|
| `null` | 사용 가능 (`available: true`) | 사용 가능한 닉네임입니다 |
| `INVALID_FORMAT` | 규칙 위반 | 2~10자의 한글, 영문, 숫자만 사용할 수 있어요 |
| `DUPLICATED` | 이미 사용 중 | 이미 사용 중인 닉네임이에요 |

- `nickname` 파라미터가 없으면 400 `INVALID_INPUT`이다. 한글은 URL 인코딩해서 보낸다(fetch의 `URLSearchParams` 사용).

### 4.4 `POST /api/v1/users/me/onboarding`

요청:
```json
{
  "nickname": "바로핏",
  "gender": "FEMALE",
  "birthYear": 1998,
  "heightCm": 163.5,
  "weightKg": 55.2,
  "skeletalMuscleKg": null,
  "bodyFatPercent": null,
  "goals": ["WEIGHT_LOSS", "HEALTH_CARE"],
  "experienceLevel": "NONE",
  "weeklyWorkoutDays": 3,
  "coachingStyle": "FRIENDLY"
}
```
응답 201: `GET /me`와 같은 형식(`isOnboarded: true`).

에러:

| 상태 | code | 상황 | 앱 동작 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | 필수값 누락, 범위·자릿수 오류, 정의되지 않은 enum 값, 골격근량 ≥ 몸무게, 미래 출생연도. 필드 오류는 `errors[{field, message}]` | 해당 입력 수정 |
| 400 | `PROFILE_NICKNAME_INVALID` | 닉네임 규칙 위반 | 닉네임 단계로 이동 |
| 409 | `PROFILE_NICKNAME_DUPLICATED` | 그사이 다른 사람이 닉네임을 확정 | 닉네임 단계로 이동, 다른 닉네임 안내 |
| 409 | `PROFILE_ALREADY_ONBOARDED` | 이미 온보딩 완료(중복 탭, 재시도) | 홈으로 이동 |
| 401 | `AUTH_TOKEN_EXPIRED` | Access Token 만료 | refresh 후 재시도 |
| 401 | `AUTH_USER_WITHDRAWN` | 탈퇴한 사용자 | 토큰 삭제 후 로그인 화면 |

- 응답이 유실돼 재시도했는데 409 `PROFILE_ALREADY_ONBOARDED`가 오면 이미 저장된 것이므로 홈으로 보내면 된다.
- 랜덤 닉네임 API는 드물게 503 `PROFILE_NICKNAME_GENERATION_FAILED`를 줄 수 있다. 직접 입력하게 하거나 다시 시도한다.

## 5. 아직 없는 것

- 온보딩 이후 프로필 수정 API(`PATCH /users/me/profile` 예정)
- 목표 부위/불편 부위 입력
- 루틴 추천에 프로필 반영(루틴 서비스는 아직 프로필을 읽지 않는다)
