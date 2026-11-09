# Node 아바타 중계 연동 — S15P31A207-274

## 2026-11-05 우측 얼굴 분리 레이어 추가

기존 정면 필드와 `rightImage`는 유지한다. 생성 결과, 작업 결과, 최근 얼굴 조회와 우측 프로필 생성·조회 응답에 아래 PNG Data URL을 추가한다.

| 필드 | 내용 |
|---|---|
| `rightFaceImage` | 우측 얼굴·코·입·귀·목·쌍꺼풀 선 |
| `rightEyesImage` | 우측 프로필에서 보이는 눈 하나·속눈썹 (쌍꺼풀 선 제외) |
| `rightHairImage` | 머리카락·눈썹·안경 |
| `rightLayerLayout` | `version: runkk-right-head-layers-v1`, `direction: right`, `canvas: [1024,1024]`, `coordinateSpace: shared-source`, `independentTransforms: false` |

우측 완성 PNG의 픽셀을 서버 코드로 분류한다. 세 PNG는 1024×1024 투명 배경이며 개별 크롭·이동·확대를 하지 않는다. 앱은 얼굴 → 눈 → 헤어 순서로 같은 좌표·배율에 그린다. 얼굴 파츠에는 원래 목이 포함된다. 정면용 `mobile.anchor` 및 `renderFaceImage`는 우측 좌표가 아니므로 우측에 재사용하지 않는다.

기존 우측 PNG만 저장되어 있으면 조회 시 세 파츠를 분리해 사용자 폴더에 저장한다. 추가 이미지 생성·분석 API 호출은 없다. 분리에 실패하면 HTTP 502 `RIGHT_LAYER_EXTRACTION_FAILED`로 반환하며 자동 재생성하지 않는다. ai 중계의 현재 규칙에서는 이 코드를 503으로 변환하므로 세부 오류 노출이 필요하면 전달 목록을 추가해야 한다.

웹 데모는 우측 보기에서 세 파츠 미리보기와 개별 PNG 저장을 제공한다. 쌍꺼풀 선은 얼굴 파츠에 남기고 눈 파츠에는 눈과 속눈썹만 둔다. 새 레이어 필드는 기존 JSON에 추가되는 항목이다.

확인일: 2026-11-04. 사용자 결정에 따라 `feature/selfie-face-api`와 `avatar-ai/`를 유지한다.
최신 확인 develop `52686f7e`, AI 중계 구현 `d086e2b9`의 `ai/runkk_ai/face/{client,schemas}.py`와 대조했다.

## 컨테이너

`infra/deploy.sh`는 `../avatar-ai/Dockerfile`을 빌드한다. `.env`와 `data/`, `node_modules/`는 Git·이미지에 넣지 않는다.
Node 22, `npm ci --omit=dev`, 비루트 `node`, `/app/data` 볼륨, 외부 포트 공개 없음.
필수 값은 `GMS_KEY`뿐이다. `HOST=0.0.0.0`, `PORT=3000`, `GMS_BASE_URL=https://gms.ssafy.io/gmsapi/api.openai.com/v1`가 기본이다.
모델 기본값: `gpt-image-2.5-sunburst`, `gpt-4.1-mini`. `AI_CALLBACK_URL`은 v1 배포에서 설정하지 않는다.

기존 배포 환경에 `COMPOSE_PROFILES=writer,avatar`, `AVATAR_GMS_KEY`, `RUNKK_FACE_API_URL=http://avatar:3000/api/v1`을 설정한다.
운영 비밀 값은 배포 담당자가 주입한다. 이 변경에서 운영 배포나 키 등록은 수행하지 않는다.
헬스체크: `GET /api/health` → 200. `configured=false`면 서버는 살아 있지만 유료 생성은 503이다.

## AI가 호출하는 계약

기준 경로 `/api/v1`. 인증은 상위 API가 담당하며 Node는 전달된 `userId`로 저장·조회를 분리한다.

| 메소드·경로 | 계약 |
|---|---|
| POST /head-jobs | multipart `userId`, UUID `clientRequestId`, `selfie`. 생성 완료를 기다리지 않고 202. 최초 파일 정규화·영속 접수만 기다림 |
| GET /head-jobs/{id}?userId= | 200, `QUEUED/RUNNING/SUCCEEDED/FAILED`, 실패는 `error.code/message` |
| GET /head-jobs/{id}/result?userId= | 완료 200, 미완료 409 `JOB_NOT_READY` |
| GET /heads/latest?userId= | 사용자별 최근 완료 결과, 없으면 404 `HEAD_NOT_FOUND` |
| GET /bodies/{id} | 프리셋 `daily/hoodie/windbreaker/singlet`, 없으면 404 `BODY_NOT_FOUND` |

같은 프로세스에서 같은 사용자·요청 ID의 동시 접수도 같은 작업을 돌려준다. 입력이 다르면 409 `IDEMPOTENCY_CONFLICT`다.
생성 전에 반환된 409 `GENERATION_BUSY`만 다시 대기한다. 그 밖의 409와 불명확한 결과·시간 초과는 자동 재생성하지 않는다.
시간 초과 작업은 `FAILED` + `UPSTREAM_TIMEOUT`이다. 콜백 미설정 완료 작업은 `notification.status=NOT_CONFIGURED`, `attempts=0`이다.
새 사유 코드는 추가하지 않았다. 기존 `SELFIE_QUALITY_REJECTED`, `FACE_ASSETS_INCOMPLETE`, `STORAGE_FAILED`,
`GENERATION_OUTCOME_UNKNOWN`, `GENERATION_INTERRUPTED` 등은 작업 상태 200의 `error.code`에 담긴다.

## 앱 합성 — v1.2 필드 유지

`faceImage`, `eyesImage`, `hairImage`는 같은 1024×1024 원본 좌표계의 PNG Data URL이다. 각각 trim하면 안 된다.
`mobile.canvas`, `anchor`, `renderFaceImage`, `skinColor`를 유지한다. `renderFaceImage`는 코드로 머리 쪽 목만 제거한 PNG다.
검출에 실패하면 원본 얼굴을 제공한다. 기존 v2 저장 결과에 이 필드가 없으면 코드로 다시 계산하며 AI는 호출하지 않는다.
`neckMask`, `scene`, `frontImage`, `rightImage`는 추가 필드다. 몸 PNG는 얼굴 결과에 넣지 않고 `/bodies/{id}`의 `pack`으로 받는다.

Android 전달용 예제: `examples/AvatarComposer.kt`. 얼굴은 `mobile.renderFaceImage`를 쓰고,
`anchor`와 `pack.mobile.neck`를 각각 이미지 원본 좌표로 환산하여 정렬한다.
두 기준점은 0..1 비율, `neckWidth/neckHeight/neckTop`은 몸 원본 픽셀 단위다.
몸 → 얼굴 → 눈 → 헤어 순서로 그리며 모든 얼굴 레이어에 같은 위치·배율을 적용한다.
`skinColor`는 RGB 0..255 세 값이며 null이면 몸 피부색을 유지한다.
이 Kotlin 파일은 앱에 자동 적용하지 않은 전달용 예제다. Android 프로젝트에서 시각·기기 검증이 필요하다.

## 크레딧과 시간

현재 기본 파이프라인은 정면과 우측을 모두 생성한다. 캐시 없는 성공 1회는 이미지 2회·분석 4회다.
제공받은 평균(이미지 788, 분석 8)으로 계산하면 약 1,608크레딧이다.
약 804크레딧·54초는 예전 정면만 생성하던 흐름의 수치이며 현재 전체 결과의 실측값이 아니다.
이번 자동 시험은 가짜 생성기를 사용하므로 실제 생성 크레딧을 사용하지 않는다.
배포 후 팀원 셀카로 접수 지연·전체 완료 시간·실제 차감량을 따로 기록한다.

## 이번 검증

Node 코드 검사·자동 테스트 13개 및 Linux Docker 빌드·컨테이너 테스트 13개 통과.
`test/container-smoke.mjs`는 외부 네트워크 없이 실제 서버를 실행하고 분석 응답만 가짜로 제공한다.
1024² 기본 PNG 동시 접수 3건은 약 105ms에 모두 202를 반환했고 가짜 분석 호출은 1회였다.
실패 상태 200, 미완료 결과 409, 사용자 분리, 프리셋 조회, 기존 저장 결과의 v1.2 모바일 보완을 확인했다.
이 수치는 로컬 접수 시간이며 실제 얼굴 생성 완료 시간이나 운영 부하 시험 결과가 아니다.

## 우측 정지 이미지의 바람 표현 (2026-11-05)

새로 생성하는 `rightImage`는 오른쪽을 보는 얼굴에 머리카락이 뒤쪽(이미지 왼쪽)으로 흐르는 정지 PNG다. 애니메이션이나 몸은 포함하지 않는다. 원래 헤어 색·가르마·길이·볼륨을 유지하고 바람에 따른 모양만 바꾼다. 짧거나 묶은 머리는 기존 끝부분·꼬리만 움직이는 모습으로, 삭발은 그대로 생성한다.

우측 생성 버전은 `runkk-head-profile-v3-wind`다. 이전 버전의 우측 생성 캐시는 재사용하지 않는다. 이미 완료·저장된 얼굴 세트의 `GET /heads/latest`와 작업 결과는 원래 결과를 유지한다. 새 모습을 원하면 새 `clientRequestId`로 생성하거나 `POST /heads/right-profile`에 기존 정면 PNG를 보낸다. 후자는 우측 프로필만 반환하며 기존 얼굴 세트의 `rightImage`를 교체하지 않는다.

이미지 생성 1회와 기존 검증 호출 횟수는 그대로다. 바람 표현 검증 실패는 기존 `AVATAR_VIEW_INVALID`로 처리하며 자동 유료 재생성하지 않는다. 응답 필드·PNG 규격·앱 합성 계약은 유지한다. 자동 시험은 모의 응답을 사용하므로 실제 AI의 그림 품질은 별도 생성 확인이 필요하다.

## 셀카 판정 별칭과 파츠 정합 보정 (2026-11-09, S15P31A207-300)

`traits.hair_length`가 정확히 `짧음`, `짧은 머리`이면 `short`로 정규화한다. 그 밖의 한국어 값과 품질 enum 번역 값은 계속 `SELFIE_QUALITY_UNAVAILABLE`이며, 프롬프트에 enum은 영문 그대로 쓰라고 명시했다.
`ai-layer-align.mjs`는 전체 alpha 경계 대신 vision이 보고한 같은 의미 박스끼리 비교하고, 짝이 없으면 공유 캔버스를 유지한다. 눈과 헤어는 `pixel-layer-registration.mjs`가 원본 픽셀과 정합하며 손실이 15% 이상 줄고 170 이하일 때만 적용한다. 이때 `validation.alignment.<part>`에 `method=source-pixel-registration`과 선택 필드 `scaleX`, `scaleY`, `pixelRegistration`이 붙는다. 8개 PNG 필드와 규격, `mobile` 계약은 바뀌지 않는다.
