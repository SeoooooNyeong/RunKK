# W5 계약: 크루 홈·공동 목표·채팅 탭 (S15P31A207-296)

- 브랜치 `feature/S15P31A207-296-crew-home`, A단계(서버)가 정하고 B단계(앱)가 이 문서만 보고 구현한다.
- 근거: `decisions.md` C2(진행률 100% 초과), C6(채팅을 크루 홈 세 번째 탭으로), C7(반응한 사람 목록·크루장 배지·시스템 메시지·반응 CHECK), `diff-crew-a.md` F-05~F-08, `diff-crew-a-chat.md` F-09~F-10b.
- 모든 경로 앞에 `/api/v1`이 붙는다. 인증은 기존과 같다(Bearer 액세스 토큰).
- 오류 본문은 기존 공통 형식 `{"code": "...", "message": "..."}`이다.

## 1. 바뀐 것 한눈에

| # | 무엇 | 종류 | 앱 영향 |
|---|---|---|---|
| 1 | `RaceStatusResponse.goalProgressPercent`가 100에서 잘리지 않는다(105, 150 …) | 값 의미 변경 | 표시는 숫자 그대로, 막대 채움만 `min(1f, p / 100f)` |
| 2 | `ReactionKind`에 `CHECK` 추가 | enum 상수 추가 | 반응 메뉴 3종, 알약 아이콘 |
| 3 | `GET /chat-messages/{id}/reactions` 반응한 사람 목록 | 새 엔드포인트 | F-09d 시트 |
| 4 | `ChatMessageItem`·`CrewChatPosted`에 `messageKind`, `authorRole`, `runDistanceMeters` | 응답 필드 추가 | 크루장 배지, 시스템 메시지 렌더러 |
| 5 | 크루원이 러닝을 마치면 서버가 크루 채팅에 시스템 메시지(`RUN_FINISHED`)를 넣는다 | 새 동작 | 채팅 목록·실시간에 섞여 들어온다 |
| 6 | 새 enum `ChatMessageKind`(TEXT, RUN_FINISHED) | enum 추가 | 앱 계약 동기화 대상에 추가 |

레이스 카드 "참여 n명 · 내 기여 nn%"와 지난 3주 목표는 **서버 변경 없음**(5절 결정 W5-D1, W5-D2).

## 2. 엔드포인트

| 메소드 | 경로 | 권한 | 요청 | 응답 | 오류 |
|---|---|---|---|---|---|
| GET | `/crews/{id}/race?weekStart=yyyy-MM-dd` | 크루원(크루장·운영진·크루원) | 쿼리 `weekStart`(선택, 그 주 월요일, 비우면 이번 주) | 200 `RaceStatusResponse`(필드 그대로, `goalProgressPercent`만 100 초과 가능) | 409 `NOT_CREW_MEMBER` 등 기존 그대로 |
| PUT | `/crews/{id}/goal` | 크루장·운영진 | 기존 `GoalRequest` | 200 `RaceStatusResponse`(위와 같음) | 기존 그대로 |
| GET | `/crews/{id}/chat/messages?before=&after=&size=` | 크루원 | 기존 그대로 | 200 `ChatPageResponse { messages: List<ChatMessageItem>, hasMore }` — `ChatMessageItem`에 필드 3개 추가(3절) | 기존 그대로 |
| POST | `/crews/{id}/chat/messages` | 크루원 | 기존 `ChatPostRequest` | 201/200 `ChatMessageItem`(앱이 보낸 글이라 늘 `messageKind = TEXT`) | 기존 그대로 |
| PUT | `/chat-messages/{id}/reactions/{kind}` | 크루원 | 경로 `kind` ∈ `LIKE`·`HEART`·**`CHECK`** | 200 `ReactionResponse`(그대로, 맵 키에 `CHECK`가 올 수 있다) | 400 `BAD_REQUEST`(모르는 kind), 409 `CHAT_MESSAGE_NOT_FOUND`, 409 `NOT_CREW_MEMBER`, 429 `TOO_MANY_ATTEMPTS` |
| DELETE | `/chat-messages/{id}/reactions/{kind}` | 크루원 | 위와 같음 | 위와 같음 | 위와 같음 |
| **GET** | **`/chat-messages/{id}/reactions?kind=`** | 그 메시지 크루의 현재 크루원 | 쿼리 `kind`(선택, `LIKE`·`HEART`·`CHECK`, 비우면 전체) | 200 `ReactorListResponse`(3절) | 400 `BAD_REQUEST`(모르는 kind), 409 `CHAT_MESSAGE_NOT_FOUND`(없음·삭제·숨김), 409 `NOT_CREW_MEMBER`(크루 밖·떠난 회원) |

참고: 이 서버는 `BusinessRuleException` 사유 코드를 409로 준다(기존 규칙). 위 409 행의 `code`가 사유 코드다.

## 3. record 필드

표기: nullable 칸 "예"면 JSON에 `null`이 올 수 있다(필드는 늘 있다). 시각은 `+09:00` 오프셋이 붙은 ISO-8601.

### 3.1 `ChatController.ChatMessageItem` (목록·보내기 응답)

| 순서 | 필드 | 타입 | nullable | 설명 |
|---|---|---|---|---|
| 1 | messageId | long | 아니오 | 메시지 ID |
| 2 | authorId | Long | 예 | 작성자 회원 ID, 탈퇴면 null. 시스템 메시지는 러닝을 마친 크루원 |
| 3 | authorName | String | 아니오 | 표시명(탈퇴면 "탈퇴한 사용자") |
| 4 | content | String | 아니오 | 내용. 시스템 메시지는 서버가 만든 문장(예 "새벽곰님이 5.02km를 달렸어요") — 옛 앱 호환용 대체 문구 |
| 5 | sentAt | OffsetDateTime | 아니오 | 보낸 일시 |
| 6 | reactions | Map<ReactionKind, Integer> | 아니오 | 종류별 수(0인 종류는 키가 없다, 순서 LIKE → HEART → CHECK) |
| 7 | myReactions | Set<ReactionKind> | 아니오 | 내가 단 반응 |
| 8 | mine | boolean | 아니오 | 내 메시지(시스템 메시지는 러닝한 본인에게 true) |
| 9 | canDelete | boolean | 아니오 | 지울 수 있음(본인 또는 크루장·운영진) |
| 10 | clientMessageId | String | 예 | 내 메시지만 |
| 11 | **messageKind** | ChatMessageKind | 아니오 | `TEXT` 보통 메시지, `RUN_FINISHED` 러닝 완료 시스템 메시지 |
| 12 | **authorRole** | CrewRole | 예 | 작성자의 **지금** 크루 역할 `LEADER`·`MANAGER`·`MEMBER`. 탈퇴했거나 크루를 떠났으면 null |
| 13 | **runDistanceMeters** | Long | 예 | `RUN_FINISHED`일 때만 달린 거리(미터), `TEXT`면 null |

### 3.2 `ChatController.ReactorListResponse` (새)

| 필드 | 타입 | nullable | 설명 |
|---|---|---|---|
| messageId | long | 아니오 | 메시지 ID |
| reactors | List<Reactor> | 아니오 | 반응 한 건이 한 줄. 반응한 시각 오름차순(먼저 단 사람이 위), 같은 시각이면 회원 ID 오름차순. 한 사람이 두 종류를 달면 두 줄 |

### 3.3 `ChatController.Reactor` (새)

| 필드 | 타입 | nullable | 설명 |
|---|---|---|---|
| memberId | long | 아니오 | 반응한 회원 ID(탈퇴 회원의 반응은 탈퇴 때 지워져 없다) |
| displayName | String | 아니오 | 표시명 |
| kind | ReactionKind | 아니오 | 반응 종류 |
| reactedAt | OffsetDateTime | 아니오 | 반응한 일시 |
| me | boolean | 아니오 | 요청자 본인 |

### 3.4 `CrewDtos.RaceStatusResponse` (필드 그대로)

| 필드 | 바뀐 점 |
|---|---|
| goalProgressPercent | `⌊합산 × 100 ÷ 목표⌋`, **상한 없음**. 목표가 없으면 null. 예: 합산 84.23km·목표 80km → 105 |

나머지(weekStart, totalMeters, closesAt, contributors[displayName, distanceMeters, share, memberId, me], myShare, goalMeters, achievedAt, celebration[crewName, totalMeters, goalMeters, participantCount])는 그대로다.

## 4. enum 상수

| enum (서버 패키지) | 상수(선언 순서) | 비고 |
|---|---|---|
| ReactionKind (crew.chat) | LIKE, HEART, **CHECK** | CHECK 추가 |
| **ChatMessageKind** (crew.chat) | TEXT, RUN_FINISHED | 새 enum. 앱 `contract/sync_from_server.py` ENUMS의 crew.chat 목록에 더한다 |
| CrewRole (crew) | LEADER, MANAGER, MEMBER | 그대로. `authorRole`에 쓴다 |

## 5. 실시간 메시지 (서버 → 앱, `/ws`)

`CrewChatPosted`에 필드 3개가 붙는다. 나머지 채팅 메시지(`CrewChatRemoved`, `CrewChatReactionChanged`)는 모양 그대로이고 `reactions` 키에 `CHECK`가 올 수 있다. 표본 파일은 `backend/src/test/resources/contract/realtime/`에 있다(앱 `android/contract/realtime/`에 같은 내용으로 복사).

`CrewChatPosted.json` (보통 메시지, 크루장이 씀)

```json
{"type":"CrewChatPosted","crewId":7,"messageId":801,"authorId":3,"authorName":"새벽곰","content":"내일 7시 출발 🏃","sentAt":"2026-10-22T19:07:03+09:00","clientMessageId":"0f8fad5b-d9cb-469f-a165-70867728950e","messageKind":"TEXT","authorRole":"LEADER","runDistanceMeters":null}
```

`CrewChatPosted-withdrawn.json` (탈퇴한 작성자)

```json
{"type":"CrewChatPosted","crewId":7,"messageId":802,"authorId":null,"authorName":"탈퇴한 사용자","content":"먼저 갈게요","sentAt":"2026-10-22T19:07:03.500+09:00","clientMessageId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","messageKind":"TEXT","authorRole":null,"runDistanceMeters":null}
```

`CrewChatPosted-runFinished.json` (새, 러닝 완료 시스템 메시지)

```json
{"type":"CrewChatPosted","crewId":7,"messageId":803,"authorId":4,"authorName":"느긋한고래","content":"느긋한고래님이 5.02km를 달렸어요","sentAt":"2026-10-22T19:40:00+09:00","clientMessageId":"9b2f4c3e-6a1d-3c5b-8e7f-0a1b2c3d4e5f","messageKind":"RUN_FINISHED","authorRole":"MEMBER","runDistanceMeters":5021}
```

`CrewChatReactionChanged.json` (CHECK 포함)

```json
{"type":"CrewChatReactionChanged","crewId":7,"messageId":801,"reactions":{"LIKE":2,"HEART":1,"CHECK":3}}
```

## 6. 러닝 완료 시스템 메시지 규칙

| 항목 | 규칙 |
|---|---|
| 언제 | 유효 러닝 기록이 저장되어 그 기록이 크루 주간 합산에 **처음** 더해질 때(기록 시작 시각에 소속이던 크루). 크루런·대결 기록도 같다(한 기록 = 한 메시지) |
| 안 만드는 경우 | 무효 기록, 크루 소속이 없던 기록, 시작이 24시간보다 오래된 기록(늦은 업로드), 지금은 그 크루를 떠난 회원, 0시 재합산이 채운 기록 |
| 누가 쓴 것으로 | 러닝한 크루원(`authorId`). 본인은 `mine = true`라 지울 수 있고, 크루장·운영진도 지울 수 있다 |
| 개인정보 | 닉네임과 거리만 싣는다. 시간·페이스·경로·위치는 없다 |
| 중복 방지 | 기록 ID로 정한 `clientMessageId`(UUID v3)와 고유 색인 `uq_crew_chat_client`가 같은 기록의 두 번째 메시지를 막는다 |
| 전송 | 커밋 뒤 방 안 크루원에게 `CrewChatPosted`(messageKind `RUN_FINISHED`). **방 밖 크루원 푸시 알림(CREW_CHAT)은 보내지 않는다** |
| 반응·신고 | 보통 메시지와 같이 반응·신고할 수 있다(서버가 막지 않는다) |
| 탈퇴 | 러닝한 회원이 탈퇴하면 그 회원의 시스템 메시지는 지운 것으로 표시해 목록에서 빠진다(보통 메시지는 기존대로 작성자만 비운다). 이때 실시간 `CrewChatRemoved`는 보내지 않아 방을 열어 둔 앱은 다시 읽을 때 사라진다 |
| 처리 위치 | 기록 저장 요청과 따로(비동기) 돌아 업로드 응답을 늦추지 않는다. 실패는 로그만 남긴다(보정 없음) |

## 7. Flyway

| 파일 | 내용 |
|---|---|
| `V53__crew_chat_reaction_check_and_system_message.sql` | `crew_chat_reaction.ck_crew_chat_reaction_kind`를 `LIKE·HEART·CHECK`로 다시 만든다. `crew_chat_message`에 `kind VARCHAR(20) NOT NULL DEFAULT 'TEXT'`(CHECK `TEXT·RUN_FINISHED`)와 `run_distance_meters BIGINT`(RUN_FINISHED일 때만 NOT NULL, 0 이상) 추가 |

V54·V55는 쓰지 않았다.

## 8. 결정 목록

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| W5-D1 | 레이스 카드 "참여 n명" | 응답 `contributors` 가운데 `distanceMeters > 0`인 줄 수. 탈퇴자 기여는 한 줄로 묶여 1명으로 센다. 축하 카드 `celebration.participantCount`와 같은 정의. 서버 필드는 더하지 않는다 | 지금 응답으로 계산된다. 축하 카드와 숫자가 어긋나지 않는다 |
| W5-D2 | "내 기여 nn%" | `myShare`(⌊내 기여 ÷ 합계 × 100⌋, 합계 0이면 0). 막대의 내 기여 구간 폭 = `min(1, goalProgressPercent/100) × myShare/100`, 목표가 없으면 전체 폭 × `myShare/100` | 응답에 이미 있다 |
| W5-D3 | 목표 화면 지난 3주(F-07) | `GET /crews/{id}/race?weekStart=`를 지난 1·2·3주 월요일로 세 번 부른다(동시 호출). 서버 변경 없음. W7의 지난 목표 목록 API는 크루 정보 화면용으로 두고 W5는 쓰지 않는다 | 세 번 모두 가볍고 이미 있는 API다. W7과 겹치지 않는다 |
| W5-D4 | 진행률 상한 | 서버가 자르지 않는다(C2). 막대는 앱이 100%에서 꽉 채운다 | 사용자 결정 C2 |
| W5-D5 | 작성자 직책 | `authorRole`(지금 역할, 크루를 떠났거나 탈퇴면 null). 앱은 `LEADER`에만 "크루장" 배지를 붙인다(C5로 운영진은 화면에서 없앴다) | 피그마는 크루장 배지만 있다 |
| W5-D6 | 반응한 사람 목록 | 반응 한 건이 한 줄, 먼저 단 순서. 페이지 없이 전부(한 메시지의 반응은 크루 정원 × 3을 넘지 않는다). 종류 필터는 쿼리 `kind` | 피그마 F-09d 행에 아이콘이 하나다 |
| W5-D7 | 시스템 메시지 문장 | 저장 `content`는 "○○님이 n.nnkm를 달렸어요"(거리는 소수 둘째 자리 버림). 앱은 피그마대로 제목 "{authorName}님이 러닝을 마쳤어요" + 부제 "{n.nn}km"를 `authorName`·`runDistanceMeters`로 그린다. **피그마의 "mm:ss"(시간)는 싣지 않는다** | 개인정보는 닉네임·거리만(사용자 지시) |
| W5-D8 | 시스템 메시지 대상 | 크루런·대결을 포함한 모든 유효 기록, 한 기록에 한 번. 늦은 업로드(시작 24시간 초과)와 재합산은 제외 | 지금 크루런은 채팅에 따로 남기는 것이 없어 겹치지 않는다. 오래된 기록이 "방금 마쳤다"처럼 보이지 않게 |
| W5-D9 | 시스템 메시지 알림 | 방 안 실시간 전송만, 방 밖 푸시 없음 | 러닝마다 푸시가 가면 시끄럽다 |
| W5-D11 | 반응한 사람 아바타 | `faceKey`를 싣지 않는다. 앱은 채팅 줄과 같은 이름 첫 글자 아바타를 쓴다 | 지금 채팅 줄도 얼굴 키 없이 첫 글자 아바타다. web.chat → avatar 의존을 새로 만들지 않는다 |
| W5-D12 | 시스템 메시지 경로(설계 05-29와 다름) | `RunRecordSaved` → crew `WeeklyRaceService.onRunSaved`가 합산에 처음 더할 때 `RaceContributionAdded` 발행 → crew.chat `ChatRunFinishedListener`(AFTER_COMMIT, @Async) → `CrewChatService.postRunFinished` → `ChatMessagePosted` → `ChatDeliveryListener`(방 안만). 설계안의 "RunRecordSaved를 crew.chat이 직접 받기"는 쓰지 않았다 | crew.chat은 running에 의존할 수 없다(ArchitectureTest 허용표). 합산 경로를 타면 "기록 시작 시각에 소속이던 크루"·유효 기록·기록당 1회가 레이스 합산과 똑같이 맞는다 |
| W5-D13 | 채팅 탭 연결(B단계, 설계 05-29 [임시:D-성-21]과 다름) | `:feature:crew → :feature:chat` 모듈 의존을 두지 않고, 기존 칸 장치(SlotKey·LocalSlots)에 `SlotKey.CREW_CHAT_TAB`과 `LocalSlotCrewId`를 더했다. ui.chat `ChatFeatureEntry`가 칸에 `ChatTabContent(crewId, isVisible, modifier)`를 넣고 크루 홈은 칸만 그린다 | ui.crew → ui.chat을 더하면 앱 Konsist 구조 시험의 Fan-Out이 8이 되어 상한 7을 넘는다. 대결 이력 탭(RECORD_BATTLES)과 같은 방식이다 |
| W5-D14 | 시스템 메시지 메뉴 | 러닝 완료 메시지는 길게 누름 메뉴·신고가 없다(앱). 서버는 막지 않는다 | 피그마 시스템 메시지에 메뉴가 없다 |
| W5-D15 | 반응 알약 선택색 | 화면 인스턴스 값(textSecondary 채움, surfaceHigh 숫자)을 따른다 | 피그마 화면이 컴포넌트 기본값(보라)을 덮어 썼다 |
| W5-D16 | 축하 팝업 | 그 주 첫 진입 때 한 번 자동, 달성 띠 「자세히 보기」로 다시. 단추는 「확인」(닫기)만이고 공유 단추는 뺐다. 가운데 정렬 카드라 RunkkDialog 대신 같은 딤·모양의 Dialog로 그린다 | 피그마 F-08b. 축하 카드 공유는 1차 배포 숨김 목록 |
| W5-D10 | 반응 CHECK 저장 | DB CHECK 제약을 V53에서 바꾼다. kind 열 길이(10)는 그대로 | CHECK는 5자 |

## 9. 앱(B단계) 할 일

### 9.1 데이터

- `data/api/chat/ChatMessageItem.kt`: `messageKind: ChatMessageKind = TEXT`, `authorRole: CrewRole? = null`, `runDistanceMeters: Long? = null` 추가(기본값을 두어 옛 서버 응답도 읽는다).
- 실시간 `CrewChatPosted` 해독기: 위 3필드 추가.
- `ReactionKind`(domain·api): `CHECK` 추가, 문자열 `chat_reaction_check`, 아이콘(피그마 e707c·6d62e).
- 새 `ChatMessageKind`(domain), 앱 `contract/sync_from_server.py` ENUMS에 crew.chat `ChatMessageKind` 추가, 표본 `contract/realtime/` 4개를 이 문서 5절로 맞춘다.
- 새 API: `ChatApi.reactors(messageId: Long, kind: ReactionKind? = null): ReactorListResponse`.
- `ChatRepository.reactors(messageId: Long): Result<List<Reactor>>`(전체를 한 번 받아 탭은 앱에서 거른다. 서버 `kind` 필터는 필요할 때만).
- 도메인 `Reactor(memberId: Long, displayName: String, kind: ReactionKind, reactedAt: Instant, me: Boolean)`.
- 도메인 `ChatMessage`에 `kind: ChatMessageKind`, `authorRole: CrewRole?`, `runDistance: Distance?`.

### 9.2 UiState 제안

```kotlin
// ChatUiState에 더한다(설계 05-29 이름)
val reactorsTarget: Long? = null            // 시트를 연 메시지 ID, null이면 닫힘
val reactorsTab: ReactionKind? = null       // null = 전체
val reactors: List<Reactor> = emptyList()   // 전체 목록(탭은 reactorsTab으로 거른다)
val isLoadingReactors: Boolean = false

// ChatActions
fun onOpenReactors(messageId: Long)         // 반응 알약을 길게 눌러 연다(짧게 누르면 지금처럼 달기·떼기)
fun onReactorTab(kind: ReactionKind?)
fun onCloseReactors()
```

- 메시지 행: `messageKind == RUN_FINISHED`면 시스템 메시지 컴포넌트(가운데 두 줄, 배경 없음)로 그리고 아바타·이름·배지 줄을 그리지 않는다. 반응 줄 표시 여부는 앱 결정.
- 크루장 배지: `messageKind == TEXT && authorRole == LEADER`.
- 레이스 카드: 참여 인원 = `contributors.count { it.distanceMeters > 0 }`, 진행률 글자 = `"${goalProgressPercent}%"`(자르지 않음), 막대 채움 = `min(1f, goalProgressPercent / 100f)`.
- 목표 화면: `lastWeek: RaceStatus?` → `pastWeeks: List<RaceStatus>`(지난 1·2·3주, `race(crewId, weekStart)` 세 번).
- 채팅 탭 이동(C6)은 서버 변경이 없다(설계 05-29 이름: `CrewTab.CHAT`, `Routes.CrewHome(crewId, tab: String? = null)`, `Routes.CrewChat` 삭제, `ChatTabContent(crewId, isVisible, modifier)`). 연결은 W5-D13(칸 SlotKey.CREW_CHAT_TAB)이다. 방 입장 `ChatPresence`는 탭이 보일 때만 보낸다(기존 D-103 규칙).
