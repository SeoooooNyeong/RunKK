# W6 크루 일정 서버 계약(상세·작성·수정·반복·지난 일정) — S15P31A207-297

- 범위: 피그마 F-11~F-13e(`diff-crew-a-schedule.md`)와 사용자 결정 C3·C4·C8(`decisions.md`), 설계 05-29 결정 D-성-26(반복 배치)·D-성-28(거리)의 서버 쪽. 앱(B단계)과 W7(지난 크루런 달력 F-20)은 이 문서만 보고 구현한다.
- 공통 형식(기존과 같음): 경로 접두어 `/api/v1`, 인증 필수(Bearer). 거리는 미터, 시각은 오프셋 포함 ISO-8601(`2026-11-14T07:00:00+09:00`), 날짜는 `yyyy-MM-dd`, 월은 `yyyy-MM`. 서버 시간대는 Asia/Seoul이고 "오늘", "15일", "요일", "월"은 모두 한국 날짜로 센다.
- 오류 본문은 기존 공통 형식 `{"code": "...", "message": "..."}`이다. 업무 규칙 거부(BusinessRuleException)는 아래 표에 따로 적지 않으면 409다.

## 1. 바뀐 규칙 요약

| # | 규칙 | 값 |
|---|---|---|
| R1 | 참석 응답 | 앱 화면은 참석하기·참석 취소하기 두 가지. 참석하기 = `PUT /posts/{id}/attendance {"answer":"GOING"}`, 참석 취소하기 = `DELETE /posts/{id}/attendance`(응답을 거둔다: 응답 행을 지워 `myAnswer`가 null이 된다). 서버 enum `AttendanceAnswer`의 `NOT_GOING`·`UNDECIDED`와 기존 행은 그대로 남긴다(옛 앱 호환). 새 앱은 `GOING`만 보낸다. |
| R2 | 거리 선택지 | 제한 없음(`distanceMeters: null`), 3000, 5000, 10000, 21097(하프, `StandardDistance.HALF`). 1000(1km)은 새로 쓸 수 없다. 기존 1km 글은 그대로 읽히고, 수정 때 거리를 바꾸지 않으면 1km를 유지할 수 있다. 제한 없음 크루런의 종료 상한은 하프 거리 × km당 15분(CREW_RUN_END_RULE)이다(D-성-28). |
| R3 | 일시 | 작성 때만 검사: 한국 날짜 기준 오늘~오늘+14일(오늘 포함 15일), 분은 5의 배수, 초·나노초 0, 지금보다 뒤. 수정(PATCH)으로는 일시를 바꿀 수 없다. |
| R4 | 제목·내용 | 크루런 모집글은 `title`(공백 제외 1~30자)과 `body`(내용, 0~200자, 비우면 `""`)를 따로 둔다. 글자 수는 코드 포인트(이모지 1자). 새 앱은 늘 `title`을 보낸다. `title`이 빠진 작성 요청(옛 앱)은 `body` 첫 줄(30자까지)을 제목, 나머지 줄을 내용으로 나눈다(V57과 같은 규칙). |
| R5 | 반복 | `repeat`: `NONE` | `WEEKLY`. 매주 같은 요일·같은 시각에 다음 회차를 서버가 만든다(3절). |
| R6 | 수정 | 작성자만, 상태가 `VOTING`이고 일시가 지금보다 뒤일 때만. 제목·내용·거리·반복만 바꾼다. |

## 2. 엔드포인트

| 메소드 | 경로 | 권한 | 요청 | 응답 | 비고 |
|---|---|---|---|---|---|
| POST | `/crews/{id}/posts` (kind=`CREW_RUN`) | 크루원 | `PostWriteRequest` | 201 `PostResponse {id}` | 바뀜: `title`·`repeat` 추가, 거리·일시 규칙 R2·R3 |
| GET | `/crew-runs/{id}` | 크루원 | - | 200 `CrewRunDetailResponse` | **새로**: 일정 상세(F-13, F-11d) |
| PATCH | `/crew-runs/{id}` | 작성자(크루원) | `CrewRunEditRequest` | 200 `CrewRunDetailResponse` | **새로**: 모집글 수정(F-13d·F-13e) |
| PUT | `/posts/{id}/attendance` | 크루원 | `AttendanceRequest {answer}` | 200 `AttendanceResponse` | 그대로. 앱은 참석하기에 `GOING`만 보낸다 |
| DELETE | `/posts/{id}/attendance` | 크루원 | - | 200 `AttendanceResponse`(`myAnswer: null`) | **새로**: 참석 취소하기(응답을 거둔다). 응답이 없어도 200. 크루런만(대면 모임은 `TARGET_INVALID`) |
| GET | `/crews/{id}/crew-runs/past?month=yyyy-MM[&date=yyyy-MM-dd]` | 크루원 | 쿼리 | 200 `PastCrewRunsResponse` | **새로**: 지난 크루런 연·월(F-11b·F-11c, W7 달력) |
| DELETE | `/posts/{id}` | 작성자, 크루장·운영진 | - | 204 | 그대로. 다가오는 회차를 지우면 반복도 끝난다(3절) |
| GET | `/crews/{id}/posts` | 크루원 | 그대로 | 200 `PostListResponse` | 바뀜: `crewRun.title` 추가, `crewRun.distanceMeters` nullable |
| GET | `/crew-runs/{id}/result`, 대기실 응답 | 그대로 | 그대로 | 그대로 | 바뀜: `distanceMeters` nullable, 결과 `title`은 저장된 제목 |

### 2.1 오류 코드

| 상황 | HTTP | code |
|---|---|---|
| 본문 JSON 형식 오류, 모르는 enum 값, PATCH 필수 칸(`title`·`repeat`) 누락, `month` 쿼리 누락 | 400 | `BAD_REQUEST` |
| `month`·`date` 형식 오류, `date`가 `month` 밖 | 400 | `INVALID_YEAR_MONTH` |
| 크루원이 아님(조회·작성·응답) | 403 | `PERMISSION_DENIED` |
| 작성자가 아닌 사람의 PATCH | 403 | `PERMISSION_DENIED` |
| 크루 없음 | 409 | `CREW_NOT_FOUND` |
| 글 없음·삭제·신고 숨김·크루런이 아닌 글 ID(상세·수정) | 409 | `POST_NOT_FOUND` |
| 제목 비었거나 30자 초과, 거리 선택지 밖, 일시 규칙(R3) 위반 | 409 | `CREW_RUN_INVALID` |
| 내용 200자 초과 | 409 | `BODY_TOO_LONG` |
| 수정할 수 없는 상태(VOTING이 아님 또는 일시가 지남) | 409 | `ALREADY_STARTED` |
| 참석·참석 취소가 응답 마감 뒤(`voteDeadline` 지남) | 409 | `VOTE_CLOSED` |
| 대면 모임에 참석 취소(DELETE) | 409 | `TARGET_INVALID` |
| 같은 글 동시 수정 충돌 | 409 | `CONCURRENT_UPDATE` |

### 2.2 요청 record

`CrewDtos.PostWriteRequest`(기존 record, CREW_RUN에 쓰는 칸만)

| 이름 | 타입 | nullable | 설명 |
|---|---|---|---|
| kind | `PostKind` | 아니오 | `CREW_RUN` |
| title | String | 예(새 앱은 늘 보낸다) | 1~30자, 공백만이면 `CREW_RUN_INVALID`. null이면 옛 앱 호환(R4). 다른 종류는 무시 |
| body | String | 예 | 내용 0~200자, null이면 `""` |
| distanceMeters | Long | 예 | null = 제한 없음, 3000·5000·10000·21097 |
| scheduledAt | OffsetDateTime | 아니오 | R3 |
| voteDeadline | OffsetDateTime | 예 | 응답 마감, null이면 scheduledAt(앱은 보내지 않는다) |
| repeat | `CrewRunRepeat` | 예 | null = `NONE` |

`CrewScheduleDtos.CrewRunEditRequest`(PATCH, 수정 화면의 네 칸을 모두 보낸다. 빠진 칸을 "그대로"로 보지 않는다)

| 이름 | 타입 | nullable | 설명 |
|---|---|---|---|
| title | String | 아니오(@NotNull) | 1~30자 |
| body | String | 예 | null이면 `""` |
| distanceMeters | Long | 예 | null = 제한 없음. 선택지 밖 값은 지금 거리와 같을 때만 받는다(기존 1km 유지) |
| repeat | `CrewRunRepeat` | 아니오(@NotNull) | `NONE`이면 반복을 끝내고, `WEEKLY`이면 이 글의 요일·시각으로 반복을 시작한다(이미 반복 중이면 그대로) |

`CrewDtos.AttendanceRequest`: `answer: AttendanceAnswer`(@NotNull). 새 앱은 `GOING`만 보낸다(`NOT_GOING`·`UNDECIDED`는 옛 앱 호환으로만 받는다).

`CrewDtos.AttendanceResponse`(그대로, PUT·DELETE 공통): `postId: long`, `myAnswer: AttendanceAnswer?`(DELETE 뒤 null), `goingCount: int`, `notGoingCount: int`, `undecidedCount: int`. 새 앱은 `postId`·`myAnswer`·`goingCount`만 읽는다.

### 2.3 응답 record

`CrewScheduleDtos.CrewRunDetailResponse`

| 이름 | 타입 | nullable | 설명 |
|---|---|---|---|
| postId | long | 아니오 | |
| crewId | long | 아니오 | |
| title | String | 아니오 | 제목 |
| body | String | 아니오 | 내용, 없으면 `""` |
| authorId | Long | 예 | 탈퇴한 작성자는 null |
| authorName | String | 아니오 | 작성자 표시명(탈퇴면 "탈퇴한 사용자"). 앱은 `mine`이면 "나"로 보인다 |
| mine | boolean | 아니오 | 내가 쓴 글 |
| writtenAt | OffsetDateTime | 아니오 | 작성 일시("2시간 전" 계산용) |
| scheduledAt | OffsetDateTime | 아니오 | 일시 |
| voteDeadline | OffsetDateTime | 아니오 | 참석 응답 마감(보통 scheduledAt과 같다) |
| distanceMeters | Long | 예 | null = 제한 없음 |
| status | `CrewRunStatus` | 아니오 | `VOTING` \| `LOBBY_OPEN` \| `RUNNING` \| `FINISHED` \| `CANCELLED` |
| goingCount | int | 아니오 | 참석 인원("참석 n명"). `attendees` 길이와 같다 |
| myAnswer | `AttendanceAnswer` | 예 | 응답이 없거나 참석 취소했으면 null. `GOING`이면 참석 중(옛 앱이 남긴 `NOT_GOING`·`UNDECIDED`는 "참석 안 함"으로 본다) |
| canEdit | boolean | 아니오 | ⋯ 메뉴 "수정하기": 작성자이고 VOTING이고 일시 전 |
| canDelete | boolean | 아니오 | ⋯ 메뉴 "삭제하기": 작성자 또는 크루장·운영진(목록 canDelete와 같다) |
| repeat | `CrewRunRepeat` | 아니오 | `WEEKLY`면 "매주" 배지. 진행 중인 반복에 속한 **다가오는 회차**(일시가 지금 뒤)만 `WEEKLY`, 지난 회차(F-11d)·반복이 끝난 글은 `NONE` |
| repeatDayOfWeek | String | 예 | `MONDAY`~`SUNDAY`(java.time.DayOfWeek), repeat이 NONE이면 null. "매주 토요일" 문구용 |
| attendees | List<`Attendee`> | 아니오 | 참석자(GOING). 작성자가 참석 중이면 맨 앞, 나머지는 응답 순 |

`CrewScheduleDtos.CrewRunDetailResponse.Attendee`

| 이름 | 타입 | nullable | 설명 |
|---|---|---|---|
| memberId | Long | 예 | 탈퇴한 참석자는 null |
| displayName | String | 아니오 | 표시명(탈퇴면 "탈퇴한 사용자") |
| author | boolean | 아니오 | 작성자("작성자" 꼬리표) |
| me | boolean | 아니오 | 나("나" Bold) |
| faceKey | String | 예 | 아바타 얼굴 키(대기실과 같은 값), 없으면 null |
| shownGender | String | 예 | `MALE` \| `FEMALE`, 탈퇴·미공개면 null |

`CrewScheduleDtos.PastCrewRunsResponse`

| 이름 | 타입 | nullable | 설명 |
|---|---|---|---|
| month | String | 아니오 | 요청한 월 `yyyy-MM` |
| dates | List<String> | 아니오 | 그 달에 지난 크루런이 있는 날짜(`yyyy-MM-dd`, 오름차순, 중복 없음). W7 달력 점 |
| runs | List<`PastRun`> | 아니오 | 그 달의 지난 크루런(`date`를 주면 그날만), 일시 최신순 |
| earliestMonth | String | 예 | 이 크루의 가장 이른 지난 크루런 월 `yyyy-MM`, 하나도 없으면 null(기간 휠의 시작) |

`CrewScheduleDtos.PastCrewRunsResponse.PastRun`

| 이름 | 타입 | nullable | 설명 |
|---|---|---|---|
| postId | long | 아니오 | 상세는 `GET /crew-runs/{postId}`, 참석자별 달린 거리는 `GET /crew-runs/{postId}/result` |
| title | String | 아니오 | |
| scheduledAt | OffsetDateTime | 아니오 | |
| distanceMeters | Long | 예 | null = 제한 없음 |
| status | `CrewRunStatus` | 아니오 | `FINISHED` \| `CANCELLED` |
| goingCount | int | 아니오 | 참석 응답 인원 |
| runnerCount | int | 아니오 | 실제로 함께 달린 인원(출발 때 준비 완료). 취소면 0. F-11b "n명"은 이 값 |

"지난 크루런" = 보이는(삭제·숨김 아님) 크루런 중 상태가 `FINISHED` 또는 `CANCELLED`인 글. 월은 scheduledAt의 한국 날짜로 나눈다. 미래 월도 오류가 아니고 빈 목록이다.

바뀐 기존 응답

| record | 칸 | 전 | 후 |
|---|---|---|---|
| `PostListResponse.CrewRun` | `distanceMeters` | long | Long(null = 제한 없음) |
| `PostListResponse.CrewRun` | `title` | 없음 | String(크루런 제목). 목록 `Item.body`는 이제 내용만이다 |
| `PostListResponse.CrewRun`, `PostListResponse.Meetup`, `AttendanceResponse` | `notGoingCount`·`undecidedCount` | 불참·미정 인원 | 그대로 남는다(옛 앱 호환). 새 앱은 읽지 않는다 |
| `LobbyResponse`, `CrewRunResultResponse` | `distanceMeters` | long | Long(null = 제한 없음) |
| `CrewRunResultResponse` | `title` | 본문 첫 줄 | 저장된 제목 |

enum

| enum | 상수 |
|---|---|
| `AttendanceAnswer`(crew) | 그대로 `GOING`, `NOT_GOING`, `UNDECIDED`. 새 앱은 `GOING`만 보낸다 |
| `CrewRunRepeat`(crew, 새로) | `NONE`, `WEEKLY` |
| `StandardDistance`(common) | 크루런 쓰임새 `CREW_RUN`이 KM1에서 빠지고 HALF에 붙었다: KM3·KM5·KM10·HALF |

## 3. 반복 규칙(D-성-26)

| 항목 | 결정 |
|---|---|
| 저장 | 새 표 `crew_run_series`(크루, 작성자, 최신 회차 글 ID·일시, 진행 여부). 각 회차는 보통 크루런 글이고 `crew_post.series_id`로 묶인다. 회차의 제목·내용·거리·시각은 **최신 회차를 그대로 복사**한다(규칙을 따로 저장하지 않는다) |
| 언제 만드나 | `batch.crew.CrewRunRepeatJob`, cron `0 0 9 * * SUN`(매주 일요일 오전 9시, Asia/Seoul, 설정 `runkk.batch.crew-run-repeat-cron`). 새 회차마다 크루원에게 모집 알림이 가므로 한밤(처음 정한 0시 5분)을 피했다(코드 리뷰 M6, 2026-11-10) |
| 몇 회 앞까지 | 매주 **다음 주(월~일)에 드는 회차 1개**만 만든다. 다음 회차 = 최신 회차 일시 + 7일(같은 요일·시각). 그 값이 다음 주 끝(다다음 주 월요일 0시) 전이면 만든다. 예: 10.21(수)에 10.24(토) 07:00 반복 글을 쓰면 10.25(일) 오전 9시에 10.31(토) 07:00 회차가 생긴다. 15일 범위 끝(예: 11.4)에 쓴 반복 글은 그 주가 "다음 주"가 되는 일요일까지 기다린다 |
| 놓친 주 | 서버가 멈춰 일요일 작업을 놓쳤으면 지난 회차는 만들지 않고 지금 뒤의 첫 같은 요일·시각 회차 하나만 만든다 |
| 새 회차 | 작성자 이름으로 만들고 작성자는 참석(GOING), 상태 VOTING, 응답 마감 = 일시. 크루원(작성자 제외)에게 CREW_RUN_RECRUIT 알림을 보낸다(직접 쓴 글과 같다) |
| 중복 방지·멱등 | 반복 행을 잠가(`SELECT … FOR UPDATE`) 읽고, 최신 회차 글도 잠가 다시 읽어(삭제·숨김 확인) 조건을 본 뒤 만든다. 잠금 순서는 회차 생성·글 수정·글 삭제·크루 삭제 모두 **반복 → 글**이다(교착 방지, 코드 리뷰 M1). 만든 뒤에는 최신 회차가 바뀌어 같은 주에 다시 돌려도 만들지 않는다. `crew_post (series_id, scheduled_at)` 고유 색인이 두 번째 삽입을 막는다(서버가 여러 대여도 한 회차) |
| 수정(PATCH) | 그 글만 바뀐다. 그 글이 최신 회차면 다음 회차부터 바뀐 제목·내용·거리가 복사된다. 이미 만든 회차·지난 회차는 그대로다. `repeat: NONE`이면 그 글이 속한 반복을 끝낸다(이미 만든 회차는 남고 더 생기지 않는다, F-13e "다음 모집글이 생성되지 않아요"). `WEEKLY`면 진행 중 반복에 속하지 않은 글일 때 이 글을 최신 회차로 새 반복을 시작한다 |
| 삭제 | 반복의 최신 회차를 지우면(작성자 삭제든 크루장·운영진 삭제든) 반복도 끝난다. 그 밖의 회차를 지우면 반복에 영향이 없다 |
| 반복이 저절로 끝나는 경우 | 다음 회차를 만들 때 작성자가 더는 그 크루의 크루원이 아니거나(회원 탈퇴·크루 탈퇴·퇴장), 최신 회차가 삭제·신고 숨김이면 만들지 않고 반복을 끝낸다. 크루를 지우면 반복 행도 지운다 |
| 시간대 | Asia/Seoul. 한국은 일광 절약 시간이 없어 +7일이 늘 같은 시각이다 |
| 피그마 안내 문구 | F-12f "매주 일요일에 다음 모집글이 자동으로 생성돼요"는 이 동작과 맞는다(일요일에 다음 주 회차 생성). 반복 안 함은 "다음 모집글이 생성되지 않아요" |

## 4. Flyway

| 파일 | 내용 |
|---|---|
| `V56__crew_run_schedule.sql` | 스키마: `crew_post.title VARCHAR(200)`(nullable, 크루런만 씀), `crew_post.series_id BIGINT` FK, 새 표 `crew_run_series`, 고유 색인 `uq_crew_post_series_scheduled (series_id, scheduled_at)`, 진행 반복 부분 색인, 거리 CHECK에 `HALF` 추가 |
| `V57__crew_run_title_data.sql` | 데이터: 기존 크루런 본문 첫 줄 → `title`(200자까지), 나머지 줄 → `body`(앞뒤 공백·빈 줄 제거, 첫 줄이 비면 `title = '크루런'`). 다른 종류 글은 그대로 |

V58은 쓰지 않았다(참석 응답 값을 남기기로 해 CHECK를 바꾸지 않는다).

## 5. 응답 JSON 표본

`GET /api/v1/crew-runs/42`

```json
{
  "postId": 42,
  "crewId": 7,
  "title": "토요일 한강 같이 뛰어요",
  "body": "반포 한강공원 8번 출구, 6분대 페이스",
  "authorId": 3,
  "authorName": "달리는곰",
  "mine": true,
  "writtenAt": "2026-11-09T10:12:00+09:00",
  "scheduledAt": "2026-11-14T07:00:00+09:00",
  "voteDeadline": "2026-11-14T07:00:00+09:00",
  "distanceMeters": null,
  "status": "VOTING",
  "goingCount": 2,
  "myAnswer": "GOING",
  "canEdit": true,
  "canDelete": true,
  "repeat": "WEEKLY",
  "repeatDayOfWeek": "SATURDAY",
  "attendees": [
    {"memberId": 3, "displayName": "달리는곰", "author": true, "me": true, "faceKey": "face_03", "shownGender": "MALE"},
    {"memberId": 9, "displayName": "새벽러너", "author": false, "me": false, "faceKey": null, "shownGender": null}
  ]
}
```

`PATCH /api/v1/crew-runs/42`

```json
{"title": "토요일 한강 10km", "body": "반포 8번 출구", "distanceMeters": 10000, "repeat": "NONE"}
```

`GET /api/v1/crews/7/crew-runs/past?month=2026-10`

```json
{
  "month": "2026-10",
  "dates": ["2026-10-13", "2026-10-20"],
  "runs": [
    {"postId": 40, "title": "화요일 인터벌", "scheduledAt": "2026-10-20T19:00:00+09:00", "distanceMeters": 5000, "status": "FINISHED", "goingCount": 6, "runnerCount": 6},
    {"postId": 35, "title": "화요일 인터벌", "scheduledAt": "2026-10-13T19:00:00+09:00", "distanceMeters": 5000, "status": "CANCELLED", "goingCount": 3, "runnerCount": 0}
  ],
  "earliestMonth": "2026-09"
}
```

`GET /api/v1/crews/7/crew-runs/past?month=2026-10&date=2026-10-20` → 같은 `dates`, `runs`는 20일 것만.

`DELETE /api/v1/posts/42/attendance`

```json
{"postId": 42, "myAnswer": null, "goingCount": 1, "notGoingCount": 0, "undecidedCount": 0}
```

`POST /api/v1/crews/7/posts`

```json
{"kind": "CREW_RUN", "title": "토요일 한강 같이 뛰어요", "body": "", "distanceMeters": 21097,
 "scheduledAt": "2026-11-14T07:00:00+09:00", "repeat": "WEEKLY"}
```

## 6. 앱 쪽 할 일(B단계·W7 제안)

| 층 | 할 일 |
|---|---|
| data DTO | `CrewRunDetailDto`(+`AttendeeDto`), `CrewRunEditRequestDto`, `PastCrewRunsDto`(+`PastRunDto`) 추가. `PostWriteRequestDto`에 `title`·`repeat`, 목록 `CrewRunDto`에 `title`, `distanceMeters`를 `Long?`로. 대기실·결과 DTO `distanceMeters`도 `Long?`. `CrewRunRepeat` enum 추가. 제목·내용을 줄바꿈으로 합쳐 보내던 매퍼(`CrewMappers.kt:39`)를 없애고 목록 줄 제목은 `crewRun.title`을 쓴다. `android/contract/sync_from_server.py`의 crew enum 목록에 `CrewRunRepeat`를 더하고 기준 파일을 다시 뽑는다 |
| Repository | `CrewRepository`에 `crewRun(postId): CrewRunDetail`, `editCrewRun(postId, CrewRunEdit): CrewRunDetail`, `withdrawAttendance(postId): Attendance`(DELETE), `pastCrewRuns(crewId, YearMonth, LocalDate?): PastCrewRuns` 추가. 참석하기는 기존 `answerAttendance(postId, GOING)` |
| domain | `CrewRunDistanceChoice { NO_LIMIT, KM3, KM5, KM10, HALF }`(NO_LIMIT ↔ null, HALF ↔ 21097). 기존 1km 글을 수정 화면에 띄울 때는 선택지 밖 값으로 "1km"를 보이고 그대로 저장하면 서버가 받는다. `CrewRunRepeat`, 상세 `CrewRunDetail`, 지난 목록 `PastCrewRuns` |
| UiState | `CrewRunDetailUiState(detail, isAnswering, menuOpen, confirmDelete)`, `CrewRunWriteUiState(title, body, date: LocalDate?, time: LocalTime?, distance: CrewRunDistanceChoice?, repeat, mode = Create/Edit(postId))`(DAYS = 15: 오늘~+14일, 시간 5분 단위, 수정이면 날짜·시간 잠금), `PastCrewRunsUiState(selected: YearMonth, earliest: YearMonth?, dates, runs, selectedDate: LocalDate?)` |
| Route | `CrewRunDetail(postId)`(상세), `CrewRunEdit(postId)`(수정, 작성 화면 재사용) |
| 화면 흐름 | 목록 줄 → `CrewRunDetail` → 상세. 지난 줄 → 같은 상세(상태가 FINISHED면 `GET /crew-runs/{id}/result`를 더 불러 참석자 거리 "5.28km"를 붙인다). ⋯ 메뉴는 `canEdit`·`canDelete`로 보인다. 하단 버튼은 `myAnswer == GOING`이면 "참석 취소하기"(DELETE), 아니면 "참석하기"(PUT GOING). 수정 저장 응답이 상세 전체라 그대로 화면을 바꾼다 |
| W7 달력(F-20) | `past?month=`로 `dates`(점)와 `runs`, 날짜를 누르면 `runs`를 그날 것으로 거르거나 `&date=`로 다시 부른다. `earliestMonth`~이번 달이 월 이동 범위 |
