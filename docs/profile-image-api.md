# 프로필 사진 API · 온보딩 키/몸무게 정수화

- 작성일: 2026-10-26
- 이슈: #14
- 브랜치: `feat/profile-image-api`
- 서버: identity 서비스. 상세 명세는 [identity-service.md 3.12, 4.6-1](identity-service.md), 온보딩 연동은 [onboarding-api.md](onboarding-api.md)

## 1. 작업 범위

| 항목 | 내용 |
|---|---|
| 가입 시 SNS 프로필 사진 가져오기 | 처음 가입한 회원의 SNS 사진(ID Token `picture`)을 내려받아 바로핏 저장소로 옮긴다 |
| 프로필 사진 변경 API | `PUT /api/v1/users/me/profile-image` (multipart). 삭제 API는 만들지 않는다 |
| 저장소 껍데기 | AWS 구성이 정해지지 않아 S3 업로드는 연동하지 않는다. 임시 저장소가 임시 URL만 돌려준다 |
| 키·몸무게 정수화 | 온보딩 `heightCm`, `weightKg`를 정수만 받는다. DB 컬럼도 정수로 바꾼다 |
| Dockerfile 수정 | 컨테이너가 기동하지 않던 문제(`install: invalid group: '1001'`) 수정 |

## 2. 설계

### 2.1 저장소 추상화 (`com.barofit.identity.image`)

```
ProfileImageStorage (interface)
 ├─ TemporaryProfileImageStorage   storage=temporary (기본). 파일 보관 없이 key와 임시 URL만 만든다
 └─ S3ProfileImageStorage          storage=s3. 자리만 있음(bucket·region 필수, 호출 시 UnsupportedOperationException)
```

- key: `profile-images/{userId}/{랜덤 UUID}.{jpg|png|webp}`. 올릴 때마다 새 key라 CDN 캐시가 섞이지 않는다.
- URL: `{public-base-url}/{key}`. 기본값 `https://temp-storage.barofit.invalid`는 **실제로 열리지 않는다**(`.invalid`는 예약 도메인).
- S3 연동 시 할 일: `S3ProfileImageStorage`에 PutObject·DeleteObject 구현, AWS SDK 의존성 추가, `PROFILE_IMAGE_STORAGE=s3`, `PROFILE_IMAGE_PUBLIC_BASE_URL`을 CloudFront 등 공개 주소로 설정.

### 2.2 가입 시 SNS 사진 가져오기

```
AuthService.loginOrRegister
 ├─ 기존 계정 → 로그인 (사진 처리 없음)
 └─ 신규 → LoginTransactions.register (커밋: profile_image_url=SNS URL, source=SOCIAL)
          → ProfileImageService.importSocialImage   (트랜잭션 밖)
               SocialProfileImageFetcher.fetch(SNS URL)   https만, 연결 2초·읽기 3초, 5MB, JPEG·PNG·WebP
               ProfileImageStorage.store
               UserAccountService.changeProfileImage      (key·url 갱신)
          → 응답 user.profileImageUrl = 저장소 URL
```

- 내려받기·저장에 실패하면 SNS URL을 그대로 두고 가입은 성공한다(key NULL).
- 가입 직후 동기로 처리하므로 신규 가입 응답이 최대 수 초 늘어날 수 있다(타임아웃으로 상한).
- 동시 가입 충돌로 로그인으로 전환된 요청은 사진을 다시 가져오지 않는다.

### 2.3 사진 변경

- `ProfileImageFile.from(bytes)`: 빈 파일·형식 위반은 400 `PROFILE_IMAGE_INVALID`, 5MB 초과는 413 `PROFILE_IMAGE_TOO_LARGE`. 형식은 Content-Type이 아니라 **파일 시그니처**로 판별한다.
- 저장 후 DB 반영에 실패하면 방금 올린 객체를 지운다.
- 이전 사진은 `ProfileImageDiscarded(key)` 이벤트로, **커밋 후** `ProfileImageCleanup`이 저장소에서 지운다. 탈퇴도 같은 경로로 사진을 지운다. 삭제 실패는 WARN 로그만 남긴다.

### 2.4 크기 제한 5MB를 맞춘 곳

| 위치 | 설정 | 초과 시 |
|---|---|---|
| nginx `deploy/nginx.conf` | `client_max_body_size 6m` | 413, `error_page`로 같은 JSON(`PROFILE_IMAGE_TOO_LARGE`) |
| Spring multipart | `max-file-size: 5MB`, `max-request-size: 6MB` | `UploadSizeExceptionHandler` → 413 `PROFILE_IMAGE_TOO_LARGE` |
| 애플리케이션 | `ProfileImageFile.MAX_SIZE` | 413 `PROFILE_IMAGE_TOO_LARGE` |

### 2.5 DB 마이그레이션

| 파일 | 내용 |
|---|---|
| `V3__add_profile_image_storage.sql` | `users.profile_image_key varchar(512)`, `users.profile_image_source varchar(20)`(CHECK `SOCIAL`/`UPLOADED`) 추가. 기존 사진은 `SOCIAL`로 채움 |
| `V4__integer_height_weight.sql` | `user_profiles.height_cm`, `weight_kg`를 `integer`로 변경. 기존 소수 값은 반올림 |

`profile_image_key`가 있으면 저장소 객체, 없으면 SNS 외부 URL이다.

### 2.6 키·몸무게 정수화

- 요청 DTO는 `BigDecimal` + `@Digits(fraction = 0)`. `163.5`, `163.0`처럼 소수점 표기가 있으면 400 `INVALID_INPUT`(`errors[].field`에 필드명).
- `Integer`로 받지 않은 이유: Jackson이 `163.5`를 `163`으로 조용히 잘라 받기 때문이다.
- 응답 `profile.heightCm`, `profile.weightKg`는 정수.

## 3. API

### `PUT /api/v1/users/me/profile-image`

```bash
curl -X PUT -H "Authorization: Bearer $TOKEN" -F "file=@photo.jpg" http://localhost:3002/api/v1/users/me/profile-image
```

- 성공 200: `GET /api/v1/users/me`와 같은 형식. `profileImageUrl`이 새 주소.
- 에러: 400 `PROFILE_IMAGE_INVALID`, 413 `PROFILE_IMAGE_TOO_LARGE`, 400 `INVALID_INPUT`(`file` 파트 없음), 401.
- CORS 허용 메서드에 `PUT`을 추가했다(`shared/runtime` `SecurityConfig`). Expo Web에서만 해당한다.

## 4. 설정

| 환경 변수 | 설명 | 기본값 |
|---|---|---|
| `PROFILE_IMAGE_STORAGE` | `temporary` 또는 `s3`(미구현) | `temporary` |
| `PROFILE_IMAGE_PUBLIC_BASE_URL` | 사진 URL 앞부분 | `https://temp-storage.barofit.invalid` |
| `PROFILE_IMAGE_S3_BUCKET`, `PROFILE_IMAGE_S3_REGION` | S3 연동 시 | 비어 있음 |

`compose.yaml`, `.env.example`에 반영했다.

## 5. Dockerfile 수정

- 증상: `docker compose up --build` 시 identity·routine이 `install: invalid group: '1001'`로 재시작을 반복.
- 원인: `useradd --system --uid 1001`이 GID 1001 그룹을 만들지 않는데, `scripts/docker-entrypoint.sh`는 `-g 1001`, `--regid=1001`을 쓴다.
- 수정: `groupadd --system --gid 1001 barofit && useradd --system --uid 1001 --gid 1001 barofit`.

## 6. 검증

| 항목 | 결과 |
|---|---|
| 단위 테스트 | `UserTest`, `ProfileImageFileTest`, `SocialProfileImageFetcherTest`, `TemporaryProfileImageStorageTest` 통과 |
| 통합 테스트 | `ProfileImageApiTest`(10개: SNS 사진 이전·실패 시 유지·기존 회원 제외·업로드·교체 시 이전 사진 삭제·형식/크기/파트 오류·인증·탈퇴 시 삭제), `OnboardingApiTest`(정수 검증 추가) 통과 |
| 전체 | `./gradlew test` 중 analytics를 제외한 모든 테스트 통과, `verifyServiceIsolation` 통과 |
| compose E2E (게이트웨이 3002) | PNG 200 / SVG 400 / 5.5MB 413(Spring) / 7MB 413(nginx, 같은 JSON). V3·V4 적용, `height_cm`·`weight_kg` integer 확인 |
| 실기기 | 카카오 로그인 → 온보딩 저장 확인(키 185, 몸무게 84 정수 저장) |

- analytics 테스트 6개는 로컬 `localhost:5432/barofit_analytics_test` DB를 직접 요구하는 기존 테스트라 이번 변경과 무관하게 실패한다.
- `scripts/verify-services.cjs`는 로컬 `.env`에 `CORS_ALLOWED_ORIGINS`가 없으면 마지막 CORS 검사에서 실패한다(환경 설정 문제).

## 7. 남은 일

- S3(또는 다른 저장소) 연동. 그 전까지 신규 가입자의 `profileImageUrl`은 열리지 않는 임시 URL이다.
- 앱에 프로필 사진 표시·변경 UI가 없다. 연동 시 HEIC는 JPEG로 변환해 보내야 하고, 앱 `apiFetch`가 multipart를 보낼 수 있어야 한다(현재 `Content-Type: application/json` 고정).
- 온보딩 이후 프로필 수정 API(`PATCH /users/me/profile` 예정).
