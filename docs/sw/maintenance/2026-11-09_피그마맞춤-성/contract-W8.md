# W8 계약: 내 러너·배경 해금 팝업·상점 잠금 (S15P31A207-299)

- 범위: 피그마 H-2 내 러너, H-3 꾸미기 배경 탭, H-4 상점, H-6 배경 해금 팝업에 필요한 서버 API·응답 필드(diff-avatar.md 8절 F-2~F-5, F-8, F-9, F-11, F-12). 사용자 결정 A1~A4(decisions.md)를 따른다.
- A단계(서버)가 이 문서대로 구현했다. B단계(앱)는 이 문서만 보고 구현한다. 서버 원본: `backend/src/main/java/com/a207/runkk/web/avatar/AvatarDtos.java`, 계약 표본 `backend/src/test/resources/contract/app/dto-records.json`.
- 바뀌지 않는 것: 상점 부위는 4개(`ItemSlot` TOP·BOTTOM·SHOES·HAT, N-17 유지, 소품 없음). 꾸미기 탭 "악세서리"는 앱 문구만 바꾼다(서버 `HAT` 그대로). 꾸미기 미리보기에 고른 배경을 까는 것(FR-202)은 앱 일이다.
- 모든 일시는 `+09:00` 오프셋 ISO-8601 문자열(기존 규칙). 모든 엔드포인트는 회원 JWT(`Authorization: Bearer …`)가 필요하고 없으면 401이다.

## 1. 엔드포인트

| # | 메소드 | 경로 | 권한 | 요청 | 응답 | 오류 |
|---|---|---|---|---|---|---|
| E1 | GET | `/api/v1/avatar/runner` | 회원 | 없음 | 200 `RunnerResponse` | 401 |
| E2 | GET | `/api/v1/avatar/backgrounds` | 회원 | 없음 | 200 `BackgroundsResponse` (필드 추가) | 401 |
| E3 | PUT | `/api/v1/avatar/background` | 회원 | `BackgroundRequest {key: String?}` (기존) | 200 `BackgroundsResponse` (필드 추가) | 401, 409 `BACKGROUND_LOCKED` |
| E4 | PUT | `/api/v1/avatar/backgrounds/seen` (신규) | 회원 | `BackgroundSeenRequest {keys: List<String>}` | 200 `BackgroundsResponse` | 401, 400 `BAD_REQUEST`(keys 없음·null 원소·51개 이상) |
| E5 | GET | `/api/v1/shop?slot={ItemSlot}` | 회원 | slot 생략 = 전체 | 200 `ShopResponse` (필드 추가) | 401, 400 `BAD_REQUEST`(slot 값 오류) |
| E6 | POST | `/api/v1/shop/purchases` | 회원 | `PurchaseRequest {itemId: Long}` (기존) | 200 `PurchaseResponse {itemId, balance}` (기존) | 401, 409 `ITEM_NOT_FOUND`·`ITEM_NOT_ON_SALE`·**`ITEM_LOCKED`(신규)**·`ALREADY_OWNED`·`INSUFFICIENT_REWARD` |

오류 본문은 기존 `ErrorResponse {code, message}`다. E6 판정 순서: 목록에 없음(`ITEM_NOT_FOUND`) → 판매 중지(`ITEM_NOT_ON_SALE`) → 잠김(`ITEM_LOCKED`, 이미 가진 아이템이면 잠김 판정을 하지 않는다) → 이미 보유(`ALREADY_OWNED`) → 잔액 부족(`INSUFFICIENT_REWARD`).

E4는 멱등이다. 해금하지 않은 키, 이미 확인한 키, 모르는 키는 조용히 무시하고 200이다.

## 2. 응답 record

### 2.1 `RunnerResponse` (E1, 신규)

| 필드 | 형식 | null | 뜻 |
|---|---|---|---|
| `bodyShape` | String enum `BodyShape` (STAGE1·STAGE2·STAGE3·STAGE4) | 아님 | 지금 체형. GET /avatar의 `bodyShape`와 같은 값(매일 0시 재계산, FR-163) |
| `bodyStage` | int 1~4 | 아님 | 체형 단계 번호(STAGE1=1 … STAGE4=4). 칩 "근육 Lv.{bodyStage}"와 게이지 위치에 쓴다 |
| `skinTone` | String enum `SkinTone` (DAWN·MORNING·DAYTIME·EVENING·NIGHT) | 아님 | 지금 피부톤. GET /avatar의 `skinTone`과 같은 값(FR-164) |
| `recentRunCount` | int ≥ 0 | 아님 | 오늘을 포함한 최근 7일(오늘−6일 ~ 오늘, 한국 날짜) 유효 러닝 횟수. "이번 주 n번 달렸어요"에 쓴다. 조회 때마다 계산해 오늘 달린 것도 들어간다 |
| `restDaysBeforeDecline` | int ≥ 1 | 아님 | 운영 기준값 체형규칙의 쉼 일수(기본 4). "{n}일 넘게 쉬면 배가 나오기 시작해요" |
| `typicalStartHour` | Integer 0~23 | **null 가능** | 피부톤 근거 대표 시각(시). 최근 유효 러닝 10회 시작 시각 중 지금 피부톤 시간대에 속한 것들에서 가장 많은 시(같으면 그중 가장 최근 러닝의 시). 그런 러닝이 없으면 null |

체형 게이지는 **4칸 단계값**이다(연속값 아님). 손잡이 위치 비율 = `(bodyStage - 1) / 3` (STAGE1 왼쪽 끝 "배가 볼록", STAGE4 오른쪽 끝 "근육 탄탄"). 피그마 손잡이(137.58/200.79 ≈ 0.69)는 STAGE3(0.67)과 맞는다.

### 2.2 `BackgroundsResponse` (E2·E3·E4)

| 필드 | 형식 | null | 뜻 |
|---|---|---|---|
| `selectedKey` | String | null 가능 | 고른 배경 키, null이면 기본 배경 (기존) |
| `backgrounds` | List<`Background`> | 아님 | 기존 순서(개인 랜드마크 거리 순, 그 뒤 표에서 빠졌지만 해금한 배경) (기존) |
| `totalMeters` | long ≥ 0 | 아님 | **신규**. 내 누적 유효 거리(m). 해금 판정과 같은 값 |
| `unseenKeys` | List<String> | 아님(빈 목록 가능) | **신규**. 아직 확인하지 않은 해금 배경 키. **해금 거리 내림차순**(같으면 키 오름차순). 첫 원소가 팝업 제목 배경이다 |

`Background`:

| 필드 | 형식 | null | 뜻 |
|---|---|---|---|
| `key` | String | 아님 | 랜드마크 키(예 `DAEJEON`) (기존) |
| `name` | String | 아님 | 이름(예 "대전") (기존) |
| `stage` | String (DOMESTIC·EAST_ASIA·INTERCONTINENTAL) | 아님 | (기존) |
| `distanceMeters` | long | 아님 | 해금 기준 누적 거리(m) (기존) |
| `unlocked` | boolean | 아님 | (기존) |
| `unlockedAt` | OffsetDateTime | 잠겼으면 null | (기존) |
| `remainingMeters` | long ≥ 0 | 아님 | **신규**. 해금까지 남은 거리 = max(0, distanceMeters − totalMeters). 해금했으면 0. 잠겼는데 0이면 해금 처리가 곧 끝나는 중이다(러닝 저장 직후 비동기 해금) |
| `unseen` | boolean | 아님 | **신규**. 해금했고 아직 확인(E4)하지 않았으면 true. 잠긴 배경은 늘 false |

"미확인" 규칙(서버):
- 러닝 저장 뒤 알림과 함께 새로 해금한 배경(BACKGROUND_UNLOCKED 알림 대상)만 미확인으로 생긴다.
- 알림 없이 채우는 해금(배포 전 거리로 넘은 랜드마크를 조회 때 채움, 재판정으로 늘어난 거리)은 확인한 것으로 저장한다(팝업이 뜨지 않는다).
- V62 배포 때 이미 있던 해금 행은 모두 확인한 것으로 바꾼다(배포 직후 팝업이 쏟아지지 않게).
- 확인은 E4로만 한다. GET은 확인 처리하지 않는다.

### 2.3 `BackgroundSeenRequest` (E4, 신규)

| 필드 | 형식 | null | 뜻 |
|---|---|---|---|
| `keys` | List<String> | 아님(@NotNull, 원소 null 불가, 최대 50개) | 확인 처리할 배경 키. 팝업에 보인 키 전부를 보낸다(받은 `unseenKeys` 그대로) |

전체를 확인하는 "키 없음" 형식은 두지 않는다: 앱이 목록을 받은 뒤 새로 해금된 배경까지 확인되면 그 팝업을 놓치기 때문이다.

### 2.4 `ShopResponse.Item` (E5)

| 필드 | 형식 | null | 뜻 |
|---|---|---|---|
| `itemId` | long | 아님 | (기존) |
| `slot` | String enum `ItemSlot` (TOP·BOTTOM·SHOES·HAT) | 아님 | (기존) |
| `name` | String | 아님 | (기존) |
| `price` | int > 0 | 아님 | (기존) |
| `owned` | boolean | 아님 | (기존) |
| `onSale` | boolean | 아님 | (기존) |
| `assetKey` | String `^[a-z0-9_]{1,40}$` | **null 가능** | **신규**. 아이템 그림 키. 앱 리소스 이름과 대응(4절). null이거나 앱에 없는 키면 부위 기본 그림 |
| `locked` | boolean | 아님 | **신규**. 해금 조건이 있고 아직 채우지 못했으며 보유하지 않았으면 true. 잠긴 아이템은 살 수 없다(E6 `ITEM_LOCKED`) |
| `unlockCondition` | String (40자 이하) | **null 가능** | **신규**. 해금 조건 문구(예 "하프 완주하면 열려요"). 조건이 있는 아이템이면 잠김 여부와 상관없이 실린다. 조건이 없으면 null. 앱은 `locked`일 때 가격 자리에 이 문구를 그대로 보인다 |

`ShopResponse {items, balance}`의 나머지는 그대로다. 목록에는 판매 중이거나 보유한 아이템만 있다(기존). 잠긴 아이템도 판매 중이면 목록에 있다.

해금 조건 종류(서버 운영 기준값 SHOP_ITEMS의 `unlock`, 앱에는 문구만 간다):

| kind | threshold 뜻 | 판정 |
|---|---|---|
| `TOTAL_DISTANCE` | 누적 유효 거리(m) | 누적 유효 거리 ≥ threshold |
| `LONGEST_RUN` | 한 번 러닝 거리(m) | 최장 유효 기록 거리 ≥ threshold (하프 완주 = 21097) |
| `RUN_COUNT` | 유효 러닝 횟수 | 유효 러닝 수 ≥ threshold |

판정은 조회·구매 때마다 계산한다(따로 저장하지 않는다). 조건을 채워 산 아이템은 그 뒤 조건과 상관없이 계속 보유한다.

### 2.5 enum·오류 code 변경

| 종류 | 변경 |
|---|---|
| `ReasonCode` | `ITEM_LOCKED("아직 해금하지 않은 아이템")` 추가, HTTP 409 |
| 그 밖의 enum | 변경 없음 (`ItemSlot` 4값 그대로) |

## 3. Flyway

| 파일 | 내용 |
|---|---|
| `V62__avatar_background_seen.sql` | `avatar_background.seen_at TIMESTAMP(6) NULL` 추가. 기존 행은 `seen_at = unlocked_at`(확인함) |
| `V63__shop_items_seed.sql` | 운영 기준값 `SHOP_ITEMS` 행이 **없을 때만** 기본 상점 목록(16개, 그림 키·해금 조건 포함)을 넣는다. 운영자가 이미 넣은 목록은 건드리지 않는다. local 프로필(H2)은 `import-local.sql`에 같은 행을 넣는다 |

V63 기본 목록(앱 그림 리소스 대응표의 기준):

| itemId | slot | name | price | assetKey | 해금 조건(문구) |
|---|---|---|---|---|---|
| 1 | TOP | 라임 싱글렛 | 300 | `top_lime_singlet` | - |
| 2 | TOP | 새벽 바람막이 | 500 | `top_dawn_windbreaker` | - |
| 3 | TOP | 크루 티셔츠 | 300 | `top_crew_tee` | - |
| 4 | TOP | 겨울 후디 | 700 | `top_winter_hoodie` | - |
| 5 | TOP | 체리 조끼 | 400 | `top_cherry_vest` | - |
| 6 | TOP | 완주 기념 티 | 300 | `top_finisher_tee` | LONGEST_RUN 21097 "하프 완주하면 열려요" |
| 11 | BOTTOM | 러닝 반바지 | 300 | `bottom_running_shorts` | - |
| 12 | BOTTOM | 조거 팬츠 | 500 | `bottom_jogger_pants` | - |
| 13 | BOTTOM | 러닝 레깅스 | 400 | `bottom_leggings` | - |
| 21 | SHOES | 기본 러닝화 | 400 | `shoes_basic_runner` | - |
| 22 | SHOES | 라임 러닝화 | 600 | `shoes_lime_runner` | - |
| 23 | SHOES | 카본 레이싱화 | 900 | `shoes_carbon_racer` | TOTAL_DISTANCE 100000 "누적 100km 달리면 열려요" |
| 31 | HAT | 러닝 캡 | 200 | `hat_running_cap` | - |
| 32 | HAT | 헤어밴드 | 200 | `hat_headband` | - |
| 33 | HAT | 비니 | 300 | `hat_beanie` | - |
| 34 | HAT | 열 번 기념 캡 | 300 | `hat_ten_runs_cap` | RUN_COUNT 10 "10번 달리면 열려요" |

운영 기준값 SHOP_ITEMS 아이템 JSON(관리 화면용): 기존 `{id, name, slot, price, onSale}`에 선택 항목 `assetKey`(String, 생략·null 가능)와 `unlock`(`{kind, threshold, text}`, 생략·null 가능)을 더했다. 옛 형식 JSON도 그대로 읽는다. 모르는 항목은 거부한다.

## 4. 응답 JSON 표본

E1 `GET /api/v1/avatar/runner`

```json
{
  "bodyShape": "STAGE3",
  "bodyStage": 3,
  "skinTone": "MORNING",
  "recentRunCount": 3,
  "restDaysBeforeDecline": 4,
  "typicalStartHour": 7
}
```

러닝이 한 번도 없는 회원: `{"bodyShape":"STAGE1","bodyStage":1,"skinTone":"DAYTIME","recentRunCount":0,"restDaysBeforeDecline":4,"typicalStartHour":null}`.

E2 `GET /api/v1/avatar/backgrounds` (누적 287km, 이번 러닝으로 개성·대전을 열고 아직 확인 안 함, 앞 2개만 보임)

```json
{
  "selectedKey": "INCHEON",
  "totalMeters": 287000,
  "unseenKeys": ["DAEJEON", "KAESONG"],
  "backgrounds": [
    {"key": "INCHEON", "name": "인천", "stage": "DOMESTIC", "distanceMeters": 60000, "unlocked": true,
     "unlockedAt": "2026-11-01T08:12:00+09:00", "remainingMeters": 0, "unseen": false},
    {"key": "KAESONG", "name": "개성", "stage": "DOMESTIC", "distanceMeters": 120000, "unlocked": true,
     "unlockedAt": "2026-11-09T07:40:00+09:00", "remainingMeters": 0, "unseen": true},
    {"key": "DAEJEON", "name": "대전", "stage": "DOMESTIC", "distanceMeters": 280000, "unlocked": true,
     "unlockedAt": "2026-11-09T07:40:00+09:00", "remainingMeters": 0, "unseen": true},
    {"key": "JEONJU", "name": "전주", "stage": "DOMESTIC", "distanceMeters": 400000, "unlocked": false,
     "unlockedAt": null, "remainingMeters": 113000, "unseen": false}
  ]
}
```

E4 `PUT /api/v1/avatar/backgrounds/seen` 요청 `{"keys": ["DAEJEON", "KAESONG"]}` → 응답은 E2와 같은 모양이고 `unseenKeys: []`, 두 배경의 `unseen: false`.

E5 `GET /api/v1/shop?slot=TOP` (일부)

```json
{
  "balance": 1240,
  "items": [
    {"itemId": 1, "slot": "TOP", "name": "라임 싱글렛", "price": 300, "owned": true, "onSale": true,
     "assetKey": "top_lime_singlet", "locked": false, "unlockCondition": null},
    {"itemId": 2, "slot": "TOP", "name": "새벽 바람막이", "price": 500, "owned": false, "onSale": true,
     "assetKey": "top_dawn_windbreaker", "locked": false, "unlockCondition": null},
    {"itemId": 6, "slot": "TOP", "name": "완주 기념 티", "price": 300, "owned": false, "onSale": true,
     "assetKey": "top_finisher_tee", "locked": true, "unlockCondition": "하프 완주하면 열려요"}
  ]
}
```

E6 잠긴 아이템 구매: `409 {"code": "ITEM_LOCKED", "message": "아직 해금하지 않은 아이템"}`.

## 5. 앱(B단계) 할 일 제안

### 5.1 data·domain

| 곳 | 할 일 |
|---|---|
| `api/avatar/AvatarApi.kt` | `@GET("avatar/runner") runner(): RunnerResponse`, `@PUT("avatar/backgrounds/seen") markBackgroundsSeen(@Body BackgroundSeenRequest): BackgroundsResponse` |
| `api/avatar/RunnerResponse.kt`(신규) | 2.1 필드 그대로. `typicalStartHour: Int?` |
| `api/avatar/BackgroundsResponse.kt` | `totalMeters: Long`, `unseenKeys: List<String>`, Background에 `remainingMeters: Long`, `unseen: Boolean` (옛 서버 대비 기본값 0·빈 목록·false) |
| `api/avatar/BackgroundSeenRequest.kt`(신규) | `keys: List<String>` |
| `api/avatar/ShopResponse.kt` | Item에 `assetKey: String?`, `locked: Boolean = false`, `unlockCondition: String?` |
| `domain/avatar/AvatarRepository` | `suspend fun runner(): Outcome<Runner>`, `suspend fun markBackgroundsSeen(keys: List<String>): Outcome<Unit>` |
| `domain/avatar/Runner`(신규) | `bodyShape: BodyShape, bodyStage: Int, skinTone: SkinTone, recentRunCount: Int, restDaysBeforeDecline: Int, typicalStartHour: Int?` + 게이지 비율 `(bodyStage-1)/3f` |
| `domain/avatar/AvatarBackgrounds` | `totalDistance: Distance`, `unseenKeys: List<String>`, Item에 `remaining: Distance`, `isUnseen: Boolean` |
| `domain/avatar/Shop.Item` | `assetKey: String?`, `isLocked: Boolean`, `unlockCondition: String?` |
| `android/contract` | `python android/contract/sync_from_server.py`로 계약 자료 갱신(ReasonCode `ITEM_LOCKED`, DTO 필드) |

### 5.2 화면

| 화면 | Route 제안 | UiState·동작 |
|---|---|---|
| H-2 내 러너(신규) | `Routes.AVATAR_RUNNER = "avatar/runner"` | `RunnerUiState(avatar: Avatar?, runner: Runner?, backgroundKey, balance, loading, error)`. `avatar()`·`runner()`를 함께 읽는다. 칩 "근육 Lv.{bodyStage}"·"{톤} 피부톤", 게이지 손잡이 `(bodyStage-1)/3`, 안내 "이번 주 {recentRunCount}번 달렸어요. {restDaysBeforeDecline}일 넘게 쉬면 배가 나오기 시작해요.", 피부톤 근거는 `typicalStartHour`가 있으면 "주로 {오전/오후 h시}에 달려서 {톤} 톤이에요", null이면 "달린 시간대에 따라 피부톤이 바뀌어요". 하단 "꾸미기" → `Routes.AVATAR`, "상점 가기" → `Routes.SHOP` |
| 홈 아바타 진입 | 홈 아바타 누름 → `Routes.AVATAR_RUNNER` (지금 `Routes.AVATAR`, HomeScreen.kt:529) | 홈 잔액 알약은 그대로 상점 |
| H-3 꾸미기 배경 탭 | 기존 `Routes.AVATAR_BACKGROUND` | 잠긴 칸 문구 "{name}, {remainingMeters를 km 올림 정수}km 더 달리면 해금"(0이면 "{name}, 곧 해금돼요"). 미리보기 카드에 고른 배경을 깐다(FR-202) |
| H-4 상점 | 기존 `Routes.SHOP` | `ShopUiState.selected: Shop.Item?` 추가. 칸 누름 = 선택(잠긴 칸은 선택하지 않고 스낵바로 `unlockCondition`). 하단 구매 바: 이름 + "사면 {balance-price} 남아요", 버튼 "{price}에 사기" → H-5 확인 → `purchase`. 잔액 부족은 지금처럼 확인 없이 스낵바(FR-160). 칸 그림은 `assetKey`로 리소스를 찾고 없으면 부위 기본 그림. 잠긴 칸 opacity 0.45, 가격 자리에 `unlockCondition`. 보유 칸은 "소지 중". `ITEM_LOCKED` 응답이면 상점을 다시 읽고 스낵바 |
| H-6 배경 해금 팝업(신규) | 홈(A5) 위 대화상자, 라우트 없음 | 홈 진입·복귀(ON_RESUME) 때 아바타 꾸미기 공개(AVATAR_RELEASED)면 `backgrounds()`를 읽어 `unseenKeys`가 비어 있지 않으면 띄운다. 제목 배경 = `unseenKeys[0]`(가장 먼 배경). 2개 이상이면 "이번 러닝으로 함께 연 배경" 상자에 `unseenKeys` 순서대로 이름을 모두 적는다(1개면 상자 없음). "닫기" → `markBackgroundsSeen(unseenKeys)`. "배경 바꾸기" → `chooseBackground(unseenKeys[0])` 뒤 `markBackgroundsSeen(unseenKeys)`. 확인 실패는 조용히 넘기고 다음 진입 때 다시 뜬다 |

### 5.3 그림 리소스 키

앱 리소스 이름 제안: `shop_item_{assetKey}` (예 `R.drawable.shop_item_top_lime_singlet`). 대응표는 V63 목록(3절)이다. 운영자가 새 아이템에 새 키를 쓰면 앱 배포 전까지 부위 기본 그림으로 보인다.
