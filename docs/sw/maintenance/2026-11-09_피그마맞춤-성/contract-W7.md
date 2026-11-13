# W7 계약: 크루 정보·지난 크루런·지난 목표·크루원 관리 (S15P31A207-298)

- 범위: 피그마 F-18·F-20·F-21·F-22·F-22b·F-23·F-25·F-29·F-29b~d (차이 표 `diff-crew-b.md`, 결정 `decisions.md`)
- A단계(서버)가 이 문서를 고정하고, B단계(앱)는 이 문서만 보고 구현한다.
- 모든 경로 앞에 `/api/v1`이 붙는다. 인증은 기존과 같다(`Authorization: Bearer …`). 시각은 `+09:00` 붙은 ISO-8601(`CrewDtos.at`), 날짜는 `yyyy-MM-dd`.
- 오류 본문은 공통 형식 `{"code": "...", "message": "..."}`.

## 1. 바뀐 것 한눈에

| # | 메소드·경로 | 변경 | 권한 |
|---|---|---|---|
| 1 | `GET /crews/{id}/members` | 응답 항목 추가(누적 거리·가입일·레벨·나이대·성별·본인 공개 설정), **정렬 변경**(누적 거리 내림차순) | 크루원(크루장·운영진·크루원) |
| 2 | `GET /crews/{id}/join-requests` | 응답 항목 추가(레벨·나이대·성별) | 크루장·운영진 |
| 3 | `GET /crews/{id}/goals/history` | **새 API**: 지난 공동 목표 목록(주 단위, 페이지) | 크루원 |
| - | `PATCH /me/profile` | 변경 없음. F-23 나이·성별 공개 토글이 이 API를 쓴다 | 본인 |
| - | `PATCH /crews/{id}/me` | 변경 없음. 한 줄 소개(`intro`)만 | 크루원 |
| 4 | `GET /crews/{id}`, `GET /crews/me` | 응답 형식은 그대로(W4가 필드 추가). **동작 변경**: 크루원이 읽을 때 초대 코드가 만료(발급 뒤 7일)됐으면 서버가 그 자리에서 새 코드를 발급해 `inviteCode`·`inviteCodeExpiresAt`에 준다. 크루 밖 사람의 조회는 코드를 바꾸지 않는다 | 기존과 같음 |
| - | `POST /crews/{id}/invite-code`, `PUT`·`DELETE /crews/{id}/managers/{memberId}` | 변경 없음. **서버에 남기고 앱 화면에서만 뺀다**(결정 C5) | 크루장 |

Flyway: **`V59__crew_manager_to_member.sql`** — 기존 운영진(`crew_membership.role = 'MANAGER'`)을 모두 `MEMBER`로 바꾼다(운영진 지정 화면 삭제, 결정 C5). MANAGER 역할 값·CHECK 제약·운영진 API는 남긴다. 크루 가입일은 기존 `crew_membership.joined_at`(V10부터 있음, NOT NULL)을 그대로 응답에 싣는다(기존 행도 모두 값이 있어 채울 것이 없다). 누적 거리·지난 목표는 기존 `race_contribution`·`weekly_race`에서 계산한다. V60·V61은 쓰지 않았다.

## 2. `GET /crews/{id}/members` — 크루원 목록 (F-22·F-29, 팝업 F-22b·F-29b)

팝업용 단건 API는 두지 않는다. 목록 한 번에 팝업이 쓸 값까지 모두 싣는다(크루 정원 50명 이하라 무겁지 않고, 팝업이 네트워크 없이 바로 열린다).

### 응답 `CrewMembersResponse`

| 필드 | 형식 | null | 설명 |
|---|---|---|---|
| `members` | `Member[]` | 아니오 | 소속 중 크루원. **정렬: `accumulatedMeters` 내림차순 → `joinedAt` 오름차순 → `memberId` 오름차순** |

### `Member`

| 필드 | JSON 형식 | null | 설명 |
|---|---|---|---|
| `memberId` | number(long) | 아니오 | 회원 ID |
| `displayName` | string | 아니오 | 표시명 |
| `role` | string | 아니오 | `CrewRole`: `LEADER` \| `MANAGER` \| `MEMBER`. V59 뒤로 화면에서 운영진을 만들 수 없어 사실상 `LEADER`·`MEMBER`뿐이다(남은 API로 MANAGER가 생기면 배지 없이 MEMBER처럼 그린다). 피그마는 크루장 배지만 그린다 |
| `crewIntro` | string | 예 | 크루 내 한 줄 소개(40자 이하) |
| `tier` | string | 예 | `Tier`: `UNRANKED` \| `BRONZE` \| `SILVER` \| `GOLD` \| `PLATINUM` \| `MASTER` |
| `runimalType` | string | 예 | Runimal 유형(기존) |
| `me` | boolean | 아니오 | 열람자 본인인가 |
| `accumulatedMeters` | number(long) | 아니오 | **[새]** 이 크루에 반영된 이 크루원의 누적 거리(m). 주간 레이스에 더해진 기여 거리의 전체 합(`race_contribution`, 유효 러닝만). 기여가 없으면 0 |
| `joinedAt` | string(OffsetDateTime) | 아니오 | **[새]** 지금 소속의 크루 가입 일시. 팝업 "가입일"은 `yyyy. MM. dd` |
| `level` | number(int) | 예 | **[새]** 레벨(늘 공개). 회원 정보를 못 읽으면 null |
| `ageBand` | string | 예 | **[새]** 나이대 `"20대"`. 다른 크루원은 나이 공개(`ageBandVisibility = PUBLIC`)일 때만 값, 비공개·미입력이면 null. 본인은 공개 설정과 무관하게 값(미입력이면 null) |
| `gender` | string | 예 | **[새]** `Gender` 이름: `FEMALE` \| `MALE` \| `UNDISCLOSED`. 다른 크루원은 성별 공개일 때만 값, 비공개·미입력이면 null. 본인은 공개 설정과 무관하게 값 |
| `ageBandVisibility` | string | 예 | **[새]** `ProfileVisibility`: `PUBLIC` \| `PRIVATE`. **본인 줄(`me = true`)에만 값**, 다른 크루원은 null |
| `genderVisibility` | string | 예 | **[새]** 같은 규칙(본인만) |

`accumulatedMeters`·`joinedAt`·정렬은 크루 안 정보라 공개 설정과 무관하게 모든 크루원에게 보인다(크루 내 랭킹이 비공개 크루원 거리도 보이는 FR-137과 같은 범위). 공개 규칙은 회원 프로필(`ProfileDisclosure.PUBLIC`, v0.14 D-241)과 같다: 티어·레벨·소개는 늘 보이고 나이대·성별만 항목별 공개 값으로 감춘다. 표시 성별 대체값(`ShownGender`)은 그림용이라 여기서 쓰지 않는다.

### 응답 표본

```json
{
  "members": [
    {
      "memberId": 12, "displayName": "달리는곰", "role": "LEADER", "crewIntro": "매주 한강 10km",
      "tier": "GOLD", "runimalType": "CHEETAH", "me": true,
      "accumulatedMeters": 512390, "joinedAt": "2026-09-01T07:30:00+09:00",
      "level": 23, "ageBand": "20대", "gender": "FEMALE",
      "ageBandVisibility": "PUBLIC", "genderVisibility": "PRIVATE"
    },
    {
      "memberId": 31, "displayName": "새벽러너", "role": "MEMBER", "crewIntro": null,
      "tier": "UNRANKED", "runimalType": null, "me": false,
      "accumulatedMeters": 0, "joinedAt": "2026-11-02T21:10:05+09:00",
      "level": 4, "ageBand": null, "gender": null,
      "ageBandVisibility": null, "genderVisibility": null
    }
  ]
}
```

### 오류

| 상황 | HTTP | code |
|---|---|---|
| 크루 없음 | 409 | `CREW_NOT_FOUND` |
| 크루원이 아님 | 403 | `PERMISSION_DENIED` |

## 3. `GET /crews/{id}/join-requests` — 가입 신청 목록 (F-25 크루 정보 안 "가입 신청 N건")

### `Request` (응답 `{"requests": Request[]}`, 신청순)

| 필드 | JSON 형식 | null | 설명 |
|---|---|---|---|
| `requestId` | number(long) | 아니오 | 가입 신청 ID(승인 `POST /join-requests/{id}/approve`, 거절 `POST /join-requests/{id}/reject`, 기존 그대로) |
| `memberId` | number(long) | 아니오 | 신청자 |
| `displayName` | string | 아니오 | 표시명 |
| `tier` | string | 예 | `Tier`(기존. 앱 `PendingRequestsResponse`가 지금 버리고 있음 → B단계에서 받기) |
| `runimalType` | string | 예 | 기존 |
| `requestedAt` | string(OffsetDateTime) | 아니오 | 신청 일시 |
| `level` | number(int) | 예 | **[새]** 레벨 |
| `ageBand` | string | 예 | **[새]** 나이대, 나이 공개일 때만(아니면 null) |
| `gender` | string | 예 | **[새]** `Gender`, 성별 공개일 때만(아니면 null) |

```json
{
  "requests": [
    {
      "requestId": 88, "memberId": 45, "displayName": "언덕왕", "tier": "GOLD", "runimalType": null,
      "requestedAt": "2026-11-08T19:02:11+09:00", "level": 23, "ageBand": "20대", "gender": "MALE"
    }
  ]
}
```

탈퇴한 신청자(대기 중 신청이 남은 경우)는 `level`·`ageBand`·`gender`가 모두 null이다.

오류: 크루 없음 409 `CREW_NOT_FOUND`, 크루장·운영진 아님 403 `PERMISSION_DENIED`(기존과 같음).

## 4. `GET /crews/{id}/goals/history` — 지난 공동 목표 (F-21) [새]

- 이번 주(서버 시각 기준 월요일 시작 주) **앞의 주** 가운데 공동 목표가 있던 주만, 최신 주부터.
- 목표 없이 지난 주, 아무도 달리지 않아 주 행이 없는 주는 나오지 않는다(레이스 탭 `GET /crews/{id}/race?weekStart=`가 그 주를 "목표 없음"으로 보이는 것과 같은 기준).

### 요청 쿼리

| 이름 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `before` | `yyyy-MM-dd` | 아니오 | 이 날짜보다 **앞선** 주만(첫 페이지는 비움). 다음 페이지는 앞 응답의 `nextBefore`를 그대로 넣는다. 이번 주 월요일 이후 날짜면 오류 없이 첫 페이지와 같다 |
| `size` | int 1~100 | 아니오 | 한 페이지 개수, 기본 20 |

`size`가 범위 밖이거나 `before` 형식이 틀리면 400 `BAD_REQUEST`.

### 응답 `GoalHistoryResponse`

| 필드 | JSON 형식 | null | 설명 |
|---|---|---|---|
| `goals` | `Goal[]` | 아니오 | 최신 주부터 |
| `nextBefore` | string(date) | 예 | 다음 페이지 `before` 값(이 페이지 마지막 주의 `weekStart`). 마지막 페이지면 null |

### `Goal`

| 필드 | JSON 형식 | null | 설명 |
|---|---|---|---|
| `weekStart` | string(date) | 아니오 | 그 주 월요일 |
| `month` | number(int) | 아니오 | "N월" 표시용 월(1~12). **그 주 목요일이 속한 달** |
| `weekOfMonth` | number(int) | 아니오 | "N주차"(1~5). 그 주 목요일이 그 달의 몇 번째 목요일인가 |
| `goalMeters` | number(long) | 아니오 | 목표 거리(m) |
| `totalMeters` | number(long) | 아니오 | 그 주 크루 합계 거리(m) |
| `achieved` | boolean | 아니오 | 달성 여부(그 주에 달성이 기록됐는가 = `achieved_at` 있음). "달성" / "달성 실패" |
| `progressPercent` | number(int) | 아니오 | ⌊`totalMeters` × 100 ÷ `goalMeters`⌋, **100 초과 허용**(결정 C2). 막대는 100 이상이면 가득 |
| `participantCount` | number(int) | 아니오 | 참여 인원 = 그 주 기여 거리가 0보다 큰 크루원 수(탈퇴로 익명이 된 기여는 한 명으로 셈. 레이스 축하 카드와 같은 규칙) |

```json
{
  "goals": [
    {
      "weekStart": "2026-10-12", "month": 10, "weekOfMonth": 3,
      "goalMeters": 80000, "totalMeters": 72000, "achieved": false,
      "progressPercent": 90, "participantCount": 6
    },
    {
      "weekStart": "2026-10-05", "month": 10, "weekOfMonth": 2,
      "goalMeters": 50000, "totalMeters": 61500, "achieved": true,
      "progressPercent": 123, "participantCount": 7
    }
  ],
  "nextBefore": "2026-10-05"
}
```

### 오류

| 상황 | HTTP | code |
|---|---|---|
| 크루 없음 | 409 | `CREW_NOT_FOUND` |
| 크루원이 아님 | 403 | `PERMISSION_DENIED` |
| `size` 범위 밖, `before` 형식 오류 | 400 | `BAD_REQUEST` |

## 5. 다른 담당 API를 쓰는 곳 (W7은 고치지 않음)

| 화면 | 쓰는 API | 담당 | 쓰는 값 |
|---|---|---|---|
| F-18·F-25 크루 정보 머리 "[yyyy. mm. dd] · 7명", 통계 칸 "누적 거리"·"누적 크루런 횟수" | `GET /crews/{id}` (`CrewDetailResponse`) | **W4가 필드 추가**(`contract-W4.md` 1장): `createdAt`(OffsetDateTime), `totalDistanceMeters`(long, 크루 누적 거리 = weekly_race 합), `crewRunCount`(int, FINISHED 크루런 수), `recentCrewRuns` | `createdAt` → "yyyy. MM. dd", `headcount` → "7명", `totalDistanceMeters` → "1,284.67km", `crewRunCount` → "n회", `myRole`, `inviteCode`. 이번 주·지난 주 거리 칸은 크루 정보에서 쓰지 않는다 |
| F-20 지난 크루런(달력·월 목록), F-20b 기간 선택, F-20c 날짜 선택 | **W6가 만드는** `GET /crews/{id}/crew-runs/past?month=yyyy-MM[&date=yyyy-MM-dd]` → `PastCrewRunsResponse`(`contract-W6.md`) | W6 | `dates`(달력 점), `runs`(그 달 또는 그날 지난 크루런, 최신순), `earliestMonth`(월 이동·기간 휠 시작, null이면 이번 달만). 기간 휠 끝은 이번 달(결정 "기간 선택 휠은 현재 연도까지만"). 날짜를 누르면 `&date=`로 다시 부르거나 `runs`를 그날 것으로 거른다. 줄을 누르면 W6 상세 `GET /crew-runs/{postId}` |
| F-23 나이·성별 공개 토글 | `PATCH /me/profile` `{"ageBandVisibility": "PUBLIC"\|"PRIVATE", "genderVisibility": ...}` (null 항목은 그대로) | 기존(member) | 응답 무시, 저장 뒤 크루원 목록 다시 읽기 |
| F-23 한 줄 소개 | `PATCH /crews/{id}/me` `{"intro": "..."}` (204) | 기존 | |
| F-29c 크루장 넘기기 | `POST /crews/{id}/leader` `{"memberId": 31}` (204, 크루장만) | 기존 | |
| F-29d 내보내기 | `POST /crews/{id}/members/{memberId}/kick` (크루장만) | 기존 | |
| F-25 승인·거절 | `POST /join-requests/{id}/approve` · `POST /join-requests/{id}/reject` | 기존 | |
| F-26 크루 정보 수정, F-27 크루 삭제 | `PATCH /crews/{id}`, `DELETE /crews/{id}` (204) | 기존 | |

## 6. 앱(B단계) 할 일

### 6.1 data·domain

- `CrewMembersResponse.Member`(android data/api/crew)에 `accumulatedMeters: Long`, `joinedAt: String`(OffsetDateTime), `level: Int?`, `ageBand: String?`, `gender: Gender?`, `ageBandVisibility: ProfileVisibility?`, `genderVisibility: ProfileVisibility?` 추가. 도메인 `CrewMember`에도 같은 값(가입일은 `LocalDate`로 바꿔도 됨).
- 목록 순서는 **서버 순서 그대로**(누적 거리 내림차순). 앱이 레이스 기여로 계산하던 이번 주 거리(CrewMembersTab.kt:148-153)는 지우고 `accumulatedMeters`를 쓴다. 표시 `512.39km`(소수 둘째 자리).
- `PendingRequestsResponse.Request`에 `tier: Tier?`, `level: Int?`, `ageBand: String?`, `gender: Gender?` 받기. 보조 줄 `"골드 · Lv23 · 20대 · 남성"` — null 항목은 빼고 잇는다(티어가 `UNRANKED`면 "언랭").
- 새 API `GET /crews/{id}/goals/history` → `CrewGoalHistoryResponse(goals: List<Goal>, nextBefore: String?)`. Repository: `suspend fun goalHistory(crewId: Long, before: LocalDate?, size: Int = 20): Result<GoalHistoryPage>`.
- 문구 규칙: `gender` null → "비공개", `FEMALE` → "여성", `MALE` → "남성", `UNDISCLOSED` → "비공개". `ageBand` null → "비공개". 내 정보 카드 2줄은 본인 값(`me = true` 줄)으로 `"[티어] · Lv[레벨] · [나이대] · [성별]"`.

### 6.2 화면·UiState (제안)

- **크루 관리 화면 해체**(결정): `Routes.CrewManage` 계열, `CrewManageScreens`, `CrewManageViewModel`, 크루원 바텀시트 `MemberMenuSheet`, `CrewStaffConfirmDialog`(운영진 지정), 초대 코드 재발급 카드·"M.d까지 사용할 수 있어요" 문구, ⋯ 메뉴 "크루 관리" 항목을 지운다. 기능은 아래로 옮긴다. 초대 코드 팝업(F-18b/F-25b)은 `GET /crews/{id}`의 `inviteCode`만 보이면 된다(서버가 만료 전 코드를 준다).
- 새 경로(조정자 결정): `Routes.CrewPastRuns(crewId)`(F-20, W6 API), `Routes.CrewPastGoals(crewId)`(F-21, 이 문서 4장).
- 상태는 `CrewHomeUiState` 하나에 모은다(조정자 결정): `openedMember`(팝업 대상), `confirmTransfer`(F-29c 대상), `confirmKick`(F-29d 대상), `requests`(F-25 가입 신청), `isConfirmingCrewDelete`(F-27), `isAgePublic`·`isGenderPublic`(F-23 토글, 본인 줄 `ageBandVisibility`·`genderVisibility == PUBLIC`). 아래 제안 형태는 이 이름에 맞춰 읽는다.
- 크루 정보(F-18·F-25) UiState에 추가: `requests: List<JoinRequestItem>`(크루장·운영진만 채움), `onApprove(requestId)`, `onReject(requestId)`, "지난 크루런 ›"·"지난 목표 ›" 이동, 크루장만 "수정"(F-26)·"크루 삭제하기"(F-27).
- `CrewHomeUiState` 추가 필드 제안(조정자 이름 그대로):
  ```kotlin
  val members: List<CrewMember>            // 서버 순서(누적 거리 내림차순). me = true 줄이 내 정보 카드
  val requests: List<JoinRequestItem>      // F-25 가입 신청(크루장·운영진만 채움, 아니면 빈 목록)
  val openedMember: CrewMember? = null     // F-22b/F-29b 팝업 대상. 목록 값을 그대로 씀(추가 조회 없음)
  val confirmTransfer: CrewMember? = null  // F-29c "[닉네임] 님에게 크루장을 넘길까요?"
  val confirmKick: CrewMember? = null      // F-29d "[닉네임] 님을 내보낼까요?"
  val isConfirmingCrewDelete: Boolean = false // F-27
  val isAgePublic: Boolean                 // F-23 토글 초깃값 = 본인 줄 ageBandVisibility == "PUBLIC"
  val isGenderPublic: Boolean              // = 본인 줄 genderVisibility == "PUBLIC"
  ```
  팝업의 "크루장 넘기기"·"내보내기"는 `myRole == LEADER`이고 대상이 본인이 아닐 때만 보인다.
  운영진(MANAGER)에게도 넘기기·내보내기는 보이지 않는다(서버도 크루장만 허용).
- F-23 저장: 소개가 바뀌었으면 `PATCH /crews/{id}/me`, 토글이 바뀌었으면 `PATCH /me/profile`(바뀐 항목만). 둘 다 성공하면 크루원 목록 다시 읽기. 하나라도 실패하면 시트를 닫지 않고 오류 표시.
- 지난 목표(F-21) 새 화면(`Routes.CrewPastGoals`): `CrewGoalHistoryUiState(goals, nextBefore, loadingMore, error)`, 끝까지 스크롤하면 `nextBefore`로 다음 페이지. 카드 1줄 `"${month}월 ${weekOfMonth}주차"`, 2줄 `totalMeters/1000` 소수 첫째 자리 + `"/ ${goalMeters/1000} km"` + `"${progressPercent}%"`, 막대 채움 `min(progressPercent, 100)`, 3줄 `"참여 ${participantCount}명"`. 빈 목록 문구는 앱이 정한다.
- 내보내기 확인 문구는 피그마 그대로 "내보낸 크루원은 다시 가입 신청을 해야 해요."(결정 C10, 서버 24시간 재가입 제한은 그대로다).
- 계약 동기화: `python android/contract/sync_from_server.py`로 `android/contract/`를 서버 기준 파일(`backend/src/test/resources/contract/app/dto-records.json`)과 맞춘다.

## 7. 결정 (W7-A)

| # | 결정 | 이유 |
|---|---|---|
| W7-1 | 크루원 "누적 거리"는 **이 크루에 반영된 누적 기여 거리**(`race_contribution` 합) | F-22 목록은 크루 정보 "누적 거리"(크루 합계)의 크루원별 몫이다. 개인 전체 누적은 프로필에 이미 있다. 기여 거리 합은 크루 합계를 넘지 않는다 |
| W7-2 | 탈퇴했다 다시 들어온 크루원의 예전 기여도 누적에 넣는다 | 그 크루를 위해 달린 거리다. 가입일(`joinedAt`)은 지금 소속의 가입 일시 |
| W7-3 | 가입일은 마이그레이션 없이 기존 `crew_membership.joined_at`을 싣는다 | 칼럼이 V10부터 NOT NULL로 있어 기존 행도 값이 있다. Flyway V59~V61은 쓰지 않았다 |
| W7-4 | 팝업 단건 API 없이 크루원 목록에 팝업 값까지 싣는다 | 정원 50명, 팝업이 바로 열림, 앱 상태 단순 |
| W7-5 | 나이대·성별 공개 규칙은 프로필 공개 규칙(D-241)과 같다. 본인 줄에만 공개 설정 두 값을 싣는다 | 한 규칙 유지. F-23 토글 초깃값이 따로 조회 없이 나온다 |
| W7-6 | 크루원 목록 정렬을 서버가 누적 거리 내림차순으로 한다 | 피그마 F-22 순서. 앱이 따로 정렬하지 않는다 |
| W7-7 | 지난 목표는 이번 주 앞의, 목표가 있던 주만. 페이지는 `before`(주 시작일) 커서 | `weekly_race`는 ID 순서가 주 순서와 같다는 보장이 없어 날짜 커서를 쓴다 |
| W7-8 | 지난 목표 "달성"은 그 주 달성 기록(`achieved_at`) 기준, 진행률은 100 초과 허용 | 알림을 보낸 사실과 일치. 결정 C2 |
| W7-9 | "N월 N주차"는 서버가 목요일 기준으로 계산해 준다 | 월 경계 주의 표기를 앱·서버가 따로 정하지 않게 |
| W7-10 | 초대 코드 재발급·운영진 지정/해제 API는 그대로 둔다 | 결정 C5(화면에서만 삭제) |
| W7-11 | 크루원이 크루 상세·내 크루를 읽을 때 만료된 초대 코드를 새로 발급한다(`InviteCodeRenewal`, 새 쓰기 트랜잭션에서 크루 행 잠금 뒤 다시 확인) | 재발급 화면이 없어도 크루원이 늘 쓸 수 있는 코드를 본다(조정자 결정). 크루 상세 응답 형식은 W4 몫이라 조회 서비스에서만 바꿨다 |
| W7-12 | V59로 기존 운영진을 모두 크루원으로 바꾼다(퇴장 행 포함) | 운영진 지정 화면 삭제(조정자 결정). 퇴장 행의 역할은 읽는 곳이 없다 |
| W7-13 | web 응답의 `gender`·`ageBandVisibility`·`genderVisibility`는 enum 이름 문자열 | web.crew는 member 형식을 쓰지 않는다(ArchUnit 13.1 규칙 1). JSON은 같다 |

## 8. B단계(앱) 구현 메모 (2026-11-10)

| # | 내용 |
|---|---|
| B-1 | 크루 관리 화면(`CrewManage*`, `ManageAction`, `CrewStaffConfirmDialog`)과 `Routes.CrewManage`를 지웠다. `CREW_MANAGE` 알림은 `Routes.CrewHome(crewId)`로 연다. 앱 `CrewRepository`에서 `reissueInviteCode`·`appointManager`·`dismissManager`를 지웠다(서버 API·`CrewApi` 메소드는 남김). `CrewMoreButton`은 W6 일정 상세가 써서 남겼다 |
| B-2 | 새 경로 `Routes.CrewPastGoals(crewId)`와 화면 등록. `Routes.CrewPastRuns(crewId)` 경로 정의와 일정 탭 「지난 크루런 ›」은 W6 것을 쓰고 W7은 화면 등록·Repository·DTO만 둔다. 지난 크루런 줄(마친·취소 모두)은 W6 `Routes.CrewRunDetail(postId)`로 연다 |
| B-3 | 상태는 `CrewHomeUiState`에 `isMenuOpen`·`requests`·`openedMember`·`confirmTransfer`·`confirmKick`·`isConfirmingCrewDelete`·`isAgePublic`·`isGenderPublic`·`editForm`, 쪽은 `CrewHomePage.EDIT`(F-26)을 더했다. `canManage`는 크루장만, `canSetGoal`은 크루장·운영진 그대로 |
| B-4 | 지난 목표 상태는 설계 05-29의 `weeks: List<RaceStatus>, nextCursor: String?` 대신 이 계약 4장 응답을 그대로 쓴 `CrewPastGoalsUiState(goals: List<PastGoals.Goal>, nextBefore: LocalDate?)`다 |
| B-5 | F-23 공개 토글은 `ProfileVisibilitySaver`(→ `AccountRepository.editProfile`, `ProfileEdit(nickname = null, intro = null, …)`)로 저장한다. 이를 위해 `ProfileEdit.nickname`·`ProfileEditRequest.nickname`을 nullable로 바꿨다(null이면 서버가 그대로 둔다) |
| B-6 | 크루 상세의 `createdAt`·`totalDistanceMeters`·`crewRunCount`는 W4 계약 이름 그대로 앱 `CrewDetailResponse`·`CrewDetail`에 더했다(W4 앱 병합 때 같은 필드가 겹치면 W4 쪽을 남긴다) |
| B-7 | 기간 선택 휠(F-20b)은 스냅 휠 대신 고르기 열(높이 180, 줄 36, 고른 줄 막대)로 근사했다. 크루 로고 연필 덮개(F-26)는 그리기만 하고, 로고 올리기는 W4 업로드 API가 들어오면 잇는다 |
