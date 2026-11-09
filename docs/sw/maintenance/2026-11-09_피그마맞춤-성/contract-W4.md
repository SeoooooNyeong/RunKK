# W4 계약: 크루 찾기·검색·상세·초대 코드·만들기 (S15P31A207-295)

- 기준: develop cfe23f10, 브랜치 `feature/S15P31A207-295-crew-find`. 서버(A단계)가 먼저 이 계약대로 바꾸고, 앱(B단계)은 이 문서만 보고 구현한다.
- 근거: `diff-crew-a.md` F-01~F-04와 끝의 "기능·데이터가 필요한 차이" 1·4·5·6·7·8, `decisions.md` C1·C9·기본값(초대 코드 2단계).
- 공통 형식(기존과 같음): 경로 앞머리 `/api/v1`, 모든 요청에 `Authorization: Bearer <access>`(로그인 회원만). 거리는 미터 정수(long), 시각은 오프셋 포함 ISO-8601(`2026-11-09T19:30:00+09:00`, 서버 시간대 Asia/Seoul), 오류 본문은 `{"code": "<사유 코드>", "message": "<화면 문구>"}`.
- 바뀐 응답은 **기존 항목 뒤에 새 항목을 붙인다**(옛 앱 호환). 옛 앱은 모르는 항목을 버린다.
- W7(크루 정보 화면)은 이 문서의 크루 상세 응답(1절)을 그대로 쓰고 고치지 않는다.

## 0. 한눈에

| # | 메소드 | 경로 | 권한 | 요청 | 응답 | 바뀜 |
|---|---|---|---|---|---|---|
| 1 | GET | `/crews/{id}` | 공개 크루는 누구나, 비공개 크루는 크루원만 | - | `CrewDetailResponse` | 항목 5개 추가 |
| 2 | GET | `/crews` | 로그인 회원 | 쿼리 `keyword`·`tags`·`cursor`·`size` | `CrewListResponse` | `totalCount` 추가 |
| 3 | GET | `/crews/me` | 로그인 회원 | - | `MyCrewResponse` | `rejoinAvailableAt` 추가 |
| 4 | POST | `/crews/invite-preview` | 로그인 회원(시도 제한) | `InvitePreviewRequest` | `InvitePreviewResponse` | **새 API** |
| 5 | POST | `/crews/join` | 로그인 회원(시도 제한) | `JoinRequestBody` | `JoinResponse` | 바뀌지 않음(초대 코드 가입 2단계의 둘째 단계) |
| 6 | POST | `/crew-logos` | 로그인 회원(시간당 20장) | multipart 파트 `logo` | `CrewLogoResponse` (201) | **새 API** |
| 7 | GET | `/crew-logos/{logoId}` | 로그인 회원 | - | `image/jpeg` 바이트 | **새 API** |
| 8 | POST | `/crews`, PATCH `/crews/{id}` | 기존과 같음 | `CrewFormRequest` | 기존과 같음 | 크루명 2~12자, `logoUrl`에 로고 경로 허용 |

Flyway: `V50__crew_run_finished_index.sql`(마친 크루런 조회 색인), `V51__create_crew_logo.sql`(로고 바이트 표). V52는 쓰지 않았다.

## 1. 크루 상세 `GET /crews/{id}` → `CrewDetailResponse`

권한은 기존과 같다. 공개 크루는 크루 밖 사람도 보고, 비공개 크루를 크루 밖 사람이 열면 `409 CREW_NOT_FOUND`(없는 크루와 같은 응답). 크루 밖 사람에게도 새 항목 5개를 모두 준다(결정 C9, 최근 크루런에는 개인 정보 없음). `GET /crews/me`의 `crew`도 같은 형식이다.

| 항목 | 형식 | null | 뜻 |
|---|---|---|---|
| crewId | long | 아니오 | 크루 ID |
| name | String | 아니오 | 크루명 |
| intro | String | 예 | 한줄 소개 |
| tags | String[] (CrewTag) | 아니오 | 크루 태그 |
| visibility | String (CrewVisibility) | 아니오 | `PUBLIC` \| `PRIVATE` |
| joinPolicy | String (JoinPolicy) | 아니오 | `FREE` \| `APPROVAL` |
| logoUrl | String | 예 | 로고 주소. 업로드한 로고면 `/api/v1/crew-logos/{logoId}`(상대 경로, 7절로 바이트를 받는다), 예전 값이면 `https://…` |
| leaderId | long | 아니오 | 크루장 회원 ID |
| leaderName | String | 아니오 | 크루장 표시명 |
| headcount | int | 아니오 | 인원(정원 10명) |
| weeklyDistanceMeters | long | 아니오 | 이번 주 크루 거리 |
| lastWeekDistanceMeters | long | 아니오 | 지난 주 크루 거리(F-02 통계 왼쪽 칸 "지난 주 달린 거리") |
| myRole | String (CrewRole) | 예 | 열람자 역할 `LEADER`·`MANAGER`·`MEMBER`, 크루 밖이면 null |
| inviteCode | String | 예 | 초대 코드(크루원만) |
| inviteCodeExpiresAt | 시각 | 예 | 초대 코드 만료(크루원만) |
| **createdAt** | 시각 | 아니오 | 개설일(F-02·F-03c·F-18 "yyyy. mm. dd") |
| **totalDistanceMeters** | long | 아니오 | 누적 크루 거리: 개설 뒤 모든 주의 크루 주간 거리 합(weekly_race 합계). W7 F-18 "누적 거리" |
| **crewRunCount** | int | 아니오 | 누적 크루런 횟수: 마친(`FINISHED`) 크루런 수, 지운 글·취소된 크루런은 세지 않는다. F-02 통계 오른쪽 칸 "누적 크루런 횟수" |
| **recentCrewRuns** | `RecentCrewRun[]` | 아니오(빈 배열 가능) | 최근 마친 크루런 최대 3건, 약속 시각 최신순 |
| **rejoinAvailableAt** | 시각 | 예 | 열람자가 크루를 나온 뒤 24시간 가입 제한 중이면 가입(·크루 만들기) 가능 시각, 아니면 null(크루원이거나 제한 없음) |

`CrewDetailResponse.RecentCrewRun`

| 항목 | 형식 | null | 뜻 |
|---|---|---|---|
| postId | long | 아니오 | 크루런 모집글 ID(크루원은 결과 화면 `GET /crew-runs/{postId}/result`로 갈 수 있다. 크루 밖 사람은 결과를 못 본다) |
| scheduledAt | 시각 | 아니오 | 약속 시각(F-02 "10.21 (수) 저녁 7:30"). 실제 출발은 이 시각 + 3초다 |
| participantCount | int | 아니오 | 함께 달린 인원(출발한 참가자 수, F-02 "[n]명 참여") |
| totalDistanceMeters | long | 아니오 | 참가자 거리 합(F-02 "총 [n]km"). 결과 화면 `totalDistanceMeters`와 같은 규칙: 참가자마다 연결된 기록 거리 → 끝낼 때 남긴 거리 → 0 |

표본(크루 밖 열람자, 24시간 제한 중):

```json
{
  "crewId": 12,
  "name": "새벽러너스",
  "intro": "새벽에 함께 달려요",
  "tags": ["WEEKDAY_DAWN", "WEEKEND_MORNING"],
  "visibility": "PUBLIC",
  "joinPolicy": "APPROVAL",
  "logoUrl": "/api/v1/crew-logos/7",
  "leaderId": 3,
  "leaderName": "달리는곰",
  "headcount": 7,
  "weeklyDistanceMeters": 12300,
  "lastWeekDistanceMeters": 64270,
  "myRole": null,
  "inviteCode": null,
  "inviteCodeExpiresAt": null,
  "createdAt": "2026-10-01T09:00:00+09:00",
  "totalDistanceMeters": 1284670,
  "crewRunCount": 5,
  "recentCrewRuns": [
    {"postId": 88, "scheduledAt": "2026-10-21T19:30:00+09:00", "participantCount": 4, "totalDistanceMeters": 20150},
    {"postId": 80, "scheduledAt": "2026-10-18T07:00:00+09:00", "participantCount": 3, "totalDistanceMeters": 15000}
  ],
  "rejoinAvailableAt": "2026-11-10T08:15:00+09:00"
}
```

## 2. 크루 검색 `GET /crews` → `CrewListResponse`

쿼리는 기존과 같다: `keyword`(크루명·한줄 소개, 대소문자 무시), `tags`(AND, `tags=A&tags=B` 또는 `tags=A,B`), `cursor`, `size`(없으면 전부). 공개 크루만, 최근 생성순.

| 항목 | 형식 | null | 뜻 |
|---|---|---|---|
| crews | `Item[]` | 아니오 | 기존과 같음(crewId, name, intro, tags, logoUrl, leaderName, headcount, weeklyDistanceMeters, joinPolicy) |
| nextCursor | long | 예 | 다음 페이지 커서 |
| **totalCount** | long | 아니오 | 같은 조건(keyword·tags)에 맞는 공개 크루 **전체** 수. cursor·size와 상관없이 같다. F-01d "검색 결과 [n]개" |

```json
{"crews": [{"crewId": 12, "name": "새벽러너스", "intro": "새벽에 함께 달려요", "tags": ["WEEKDAY_DAWN"], "logoUrl": null, "leaderName": "달리는곰", "headcount": 7, "weeklyDistanceMeters": 12300, "joinPolicy": "APPROVAL"}], "nextCursor": null, "totalCount": 1}
```

## 3. 내 크루 `GET /crews/me` → `MyCrewResponse`

| 항목 | 형식 | null | 뜻 |
|---|---|---|---|
| crew | `CrewDetailResponse` | 예 | 소속 크루(1절 형식), 없으면 null |
| pendingRequests | `PendingJoin[]` | 아니오 | 기존과 같음(requestId, crewId, crewName, requestedAt) |
| **rejoinAvailableAt** | 시각 | 예 | 1절과 같은 값: 24시간 제한 중이면 가입·크루 만들기 가능 시각, 아니면 null |

```json
{"crew": null, "pendingRequests": [], "rejoinAvailableAt": "2026-11-10T08:15:00+09:00"}
```

## 4. 초대 코드 미리보기 `POST /crews/invite-preview` (새 API)

초대 코드 가입을 조회 → 확인 → 가입 2단계로 나눈다(결정 기본값). 이 API는 코드에 맞는 크루 요약만 주고 가입시키지 않는다. 가입은 확인 뒤 5절 `POST /crews/join {"inviteCode": …}`로 한다. 코드를 URL에 싣지 않으려고(접근 로그) GET 대신 POST 본문으로 받는다.

요청 `InvitePreviewRequest`

| 항목 | 형식 | 필수 | 뜻 |
|---|---|---|---|
| inviteCode | String | 예(빈 값이면 400 `BAD_REQUEST`) | 6자리 영문·숫자, 대소문자 무시, 앞뒤 공백 무시 |

응답 200 `InvitePreviewResponse`

| 항목 | 형식 | null | 뜻 |
|---|---|---|---|
| crewId | long | 아니오 | 크루 ID |
| name | String | 아니오 | 크루명 |
| intro | String | 예 | 한줄 소개 |
| tags | String[] (CrewTag) | 아니오 | 크루 태그 |
| logoUrl | String | 예 | 로고(1절과 같은 규칙) |
| visibility | String (CrewVisibility) | 아니오 | `PUBLIC`이면 F-03c 카드의 `›`로 크루 상세(1절)를 열 수 있다. `PRIVATE`이면 크루 밖 사람은 상세를 못 보므로 `›`를 숨긴다 |
| joinPolicy | String (JoinPolicy) | 아니오 | 참고용. 초대 코드 가입은 가입 방식과 상관없이 바로 가입이다 |
| headcount | int | 아니오 | 인원(F-03c "· 7명"). 10이면 가입 시 `CREW_FULL` |
| createdAt | 시각 | 아니오 | 개설일 |

```json
{"crewId": 12, "name": "새벽러너스", "intro": "새벽에 함께 달려요", "tags": ["WEEKDAY_DAWN", "WEEKEND_MORNING"], "logoUrl": null, "visibility": "PRIVATE", "joinPolicy": "APPROVAL", "headcount": 7, "createdAt": "2026-10-01T09:00:00+09:00"}
```

오류

| 상태 | code | 언제 | 앱 표시 |
|---|---|---|---|
| 409 | `INVALID_CODE` | 코드에 맞는 크루가 없거나 만료(7일)·형식 오류 | F-03b: 칸 오류 표시 + "일치하는 크루가 없어요. 초대 코드를 다시 확인해 주세요." |
| 429 | `TOO_MANY_ATTEMPTS` | 회원별로 10분 안에 틀린 코드 5번(미리보기와 가입이 횟수를 함께 쓴다) | 서버 문구 그대로 |
| 400 | `BAD_REQUEST` | 본문 없음·`inviteCode` 빈 값 | - |

대입 공격 가드: 기존 `CrewJoinAttemptGuard`(회원별 10분 5회, D-132)를 미리보기에도 건다. 틀린 코드는 실패 1회로 남고, 맞는 코드 미리보기는 실패 횟수를 지우지 않는다(내가 아는 코드로 횟수를 지우고 다시 맞혀 보는 우회를 막는다). 가입(5절) 성공만 실패 횟수를 지운다.

## 5. 초대 코드 가입 `POST /crews/join` (바뀌지 않음)

요청 `{"inviteCode": "AB12CD"}` → 200 `{"status": "JOINED", "crewId": 12, "requestId": null}`. 오류: `INVALID_CODE`(409, 미리보기 뒤 코드가 재발급·만료된 경우), `ALREADY_IN_CREW`(409), `COOLDOWN`(409, 24시간 제한), `CREW_FULL`(409), `TOO_MANY_ATTEMPTS`(429). 크루 ID 가입(`{"crewId": 12}`)도 그대로다.

## 6. 크루 로고 업로드 `POST /crew-logos` (새 API)

- 요청: `multipart/form-data`, 파트 이름 `logo`, JPEG 한 장, 1MB(1,048,576바이트) 이하. 게시글 사진과 같은 검사(`PhotoUpload`): 크기 → JPEG 머리(FF D8 FF) → 위치 등 메타데이터 제거 뒤 저장. PNG 등은 앱이 JPEG로 바꿔 올린다.
- 권한: 로그인 회원 누구나(크루를 만들기 전에 올린다). 회원별 1시간에 20장까지.
- 응답 201 `CrewLogoResponse`

| 항목 | 형식 | null | 뜻 |
|---|---|---|---|
| logoId | long | 아니오 | 로고 ID |
| logoUrl | String | 아니오 | `/api/v1/crew-logos/{logoId}`. 이 값을 그대로 `CrewFormRequest.logoUrl`에 넣는다 |

```json
{"logoId": 7, "logoUrl": "/api/v1/crew-logos/7"}
```

오류: 413 `TOO_LARGE`(1MB 초과), 422 `UNSUPPORTED_IMAGE`(JPEG 아님), 415 `UNSUPPORTED_IMAGE`(multipart가 아님), 400 `BAD_REQUEST`(`logo` 파트 없음), 429 `TOO_MANY_ATTEMPTS`(시간당 20장 초과).

## 7. 크루 로고 바이트 `GET /crew-logos/{logoId}` (새 API)

- 로그인 회원 누구나(공개 크루 목록·초대 미리보기에서 크루 밖 사람도 로고를 본다). 응답 `image/jpeg`, `Cache-Control: private, max-age=86400`(로고 ID마다 바이트가 바뀌지 않는다).
- 오류: 404 `CREW_LOGO_NOT_FOUND`(없는 ID). 경로 ID가 숫자가 아니면 400.

## 8. 크루 만들기·정보 변경 `CrewFormRequest`

| 항목 | 바뀜 |
|---|---|
| name | **2~12자**(앞뒤 공백 제거 뒤 글자 수, 결정 C1). 어기면 409 `CREW_NAME_INVALID`. 생성(POST)과 이름을 **바꾸는** 변경(PATCH)만 검사한다. 이미 있는 13~20자 크루는 그대로 읽히고, PATCH에 지금 이름을 그대로 보내도 통과한다 |
| logoUrl | `https://…`(기존) 또는 `/api/v1/crew-logos/{숫자}`(6절 응답 값)만 받는다. 그 밖은 400 `BAD_REQUEST`. 500자 이하 |

## 9. 사유 코드·enum 변경

- `ReasonCode`에 `CREW_LOGO_NOT_FOUND`("크루 로고 없음", 404) 추가. 앱 `android/contract/` 열거형 파일은 `python android/contract/sync_from_server.py`로 다시 만든다.
- 새 enum 상수는 없다. 쓰는 enum: `CrewTag`, `CrewVisibility`(PUBLIC·PRIVATE), `JoinPolicy`(FREE·APPROVAL), `CrewRole`(LEADER·MANAGER·MEMBER).

## 10. 앱(B단계)이 할 일

API·DTO(`data/.../api/crew/`)

- `CrewDetailResponse`에 `createdAt: String`(OffsetDateTime), `totalDistanceMeters: Long`, `crewRunCount: Int`, `recentCrewRuns: List<RecentCrewRun> = emptyList()`, `rejoinAvailableAt: String? = null`를 붙이고 `RecentCrewRun(postId: Long, scheduledAt: String, participantCount: Int, totalDistanceMeters: Long)`을 둔다.
- `CrewListResponse`에 `totalCount: Long = 0`, `MyCrewResponse`에 `rejoinAvailableAt: String? = null`.
- `CrewApi`에 `@POST("crews/invite-preview") invitePreview(@Body InvitePreviewRequest): InvitePreviewResponse`, `@Multipart @POST("crew-logos") uploadLogo(@Part logo: MultipartBody.Part): CrewLogoResponse`(파트 이름 `logo`, `image/jpeg`).
- `CrewFormDto`에 `logoUrl: String?`.

도메인·Repository 제안(`domain/.../crew/`)

```kotlin
// CrewRepository
suspend fun invitePreview(code: String): Outcome<CrewPreview>      // POST /crews/invite-preview
suspend fun uploadLogo(jpeg: ByteArray): Outcome<String>           // POST /crew-logos → logoUrl
suspend fun logo(url: String): Outcome<ByteArray>                  // GET logoUrl(상대 경로면 인증 붙여서), 크루 사진과 같은 방식
// search(...)의 결과 CrewList에 totalCount: Long

data class CrewPreview(
    val crewId: Long, val name: String, val intro: String?, val tags: Set<CrewTag>,
    val logoUrl: String?, val visibility: CrewVisibility, val joinPolicy: JoinPolicy,
    val headcount: Int, val createdAt: LocalDate,
)
// CrewDetail에: createdAt: LocalDate, totalDistance: Distance, crewRunCount: Int,
//   recentCrewRuns: List<RecentCrewRun>, rejoinAvailableAt: Instant?
data class RecentCrewRun(val postId: Long, val scheduledAt: LocalDateTime, val participantCount: Int, val totalDistance: Distance)
// MyCrew에: rejoinAvailableAt: Instant?
// CrewForm에: logoUrl: String?
```

UiState 제안

- 크루 검색 화면(F-01d·F-01e, 새 경로 예 `Routes.CrewSearch`): `keyword: String`, `results: List<CrewList.Item>`, `resultCount: Long`(= `totalCount`), `isLoading`, `nextCursor`.
- 크루 상세(F-02·F-02b·F-02c): `detail: CrewDetail?`, `rejoinAvailableAt: Instant?`, `remaining: Duration?`(1초마다 `rejoinAvailableAt - now`로 갱신, 0 이하가 되면 null로 바꾸고 가입 단추를 다시 켠다). 단추 문구 "[hh:mm:ss] 후에 가입할 수 있어요".
- 초대 코드 바텀시트(F-03·F-03b·F-03c): `code: String`, `step: Input | Preview`, `preview: CrewPreview?`, `inviteError: Boolean`(F-03b 칸 오류), `isSubmitting`. 동작은 "확인"(`invitePreview`) → "가입하기"(`applyWithInviteCode`)로 나눈다. 코드를 고치면 `preview`·`inviteError`를 비운다.
- 크루 만들기(F-04): `logo: ByteArray?`(고른 이미지 미리보기), `logoUrl: String?`(업로드 결과), `isUploadingLogo`, 크루명 상한 `NAME_MAX = 12`. 로고는 고르자마자 올리거나 "크루 만들기"를 누를 때 먼저 올린 뒤 `logoUrl`을 실어 만든다.

## 11. 서버 결정 목록

| # | 결정 | 이유 |
|---|---|---|
| S1 | 새 항목은 기존 응답 뒤에 붙인다 | 옛 앱 호환(D-510과 같은 방식) |
| S2 | 크루명 2~12자는 입력 때만(`CrewName.of`) 검사, 저장 값 읽기는 2~20자 | 기존 13~20자 크루가 읽기에서 깨지지 않게(결정 C1) |
| S3 | PATCH에 지금 이름을 그대로 보내면 길이 검사를 건너뛴다 | 기존 긴 이름 크루도 다른 항목을 고칠 수 있게 |
| S4 | 누적 크루 거리 = weekly_race 주간 합계의 합 | 크루 거리의 원본이 주간 레이스 합계다(이번 주·지난 주 거리와 같은 원본) |
| S5 | 누적 크루런 = `FINISHED`이고 지우지 않은 크루런 | 취소·진행 중은 "함께 달린" 기록이 아니다 |
| S6 | 최근 크루런 시각은 약속 시각(`scheduledAt`) | 피그마 "저녁 7:30" 표기와 맞고 출발 시각은 +3초다 |
| S7 | 최근 크루런 거리 합은 결과 화면과 같은 규칙(기록 → 끝낸 값 → 0) | 같은 크루런이 화면마다 다른 거리로 보이지 않게 |
| S8 | `rejoinAvailableAt`은 상세와 `/crews/me` 둘 다에 준다 | F-02c는 상세에서, 크루 만들기 제한은 크루 탭 첫 화면에서 쓴다 |
| S9 | 초대 미리보기는 `POST /crews/invite-preview` 본문으로 받는다 | 코드를 URL·접근 로그에 남기지 않는다 |
| S10 | 미리보기도 가입과 같은 실패 횟수(회원별 10분 5회)를 쓰고, 미리보기 성공은 횟수를 지우지 않는다 | 대입 공격 가드, 아는 코드로 횟수를 지우는 우회 차단 |
| S11 | 가입은 기존 `POST /crews/join {inviteCode}`를 그대로 쓴다 | 이미 시도 제한·자격 판정이 있다 |
| S12 | 미리보기는 비공개 크루도 보여 주고 `visibility`를 준다 | 초대 코드는 비공개 크루 가입 수단이다. 앱은 `PRIVATE`이면 상세 이동을 숨긴다 |
| S13 | 로고는 게시글 사진과 같은 검사(JPEG, 1MB, 메타데이터 제거)를 거쳐 PostgreSQL BYTEA 표 `crew_logo`에 둔다 | 기존 업로드 규칙·저장 방식 재사용(D-100, D-142) |
| S14 | 로고는 로그인 회원 누구나 읽는다 | 크루 밖 사람도 목록·미리보기에서 로고를 본다 |
| S15 | 로고 업로드는 회원별 시간당 20장 | 쓰이지 않는 로고로 저장소를 채우는 남용 방지 |
| S16 | `logoUrl`은 https 또는 로고 경로만 받는다 | 기존 https 값 호환, 그 밖의 주소 차단 |
