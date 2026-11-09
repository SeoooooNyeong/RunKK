# W8 계약: 내 러너·배경 해금 팝업·상점 잠금 (S15P31A207-299)

- 범위: 피그마 H-2 내 러너, H-3 꾸미기 배경 탭, H-4 상점, H-6 배경 해금 팝업에 필요한 서버 API·응답 필드(diff-avatar.md 8절 F-2~F-5, F-8, F-9, F-11, F-12). 사용자 결정 A1~A4(decisions.md)를 따른다.
- A단계(서버)가 이 문서대로 구현했다. B단계(앱)는 이 문서만 보고 구현한다. 서버 원본: `backend/src/main/java/com/a207/runkk/web/avatar/AvatarDtos.java`, 계약 표본 `backend/src/test/resources/contract/app/dto-records.json`.
- 바뀌지 않는 것: 상점 부위는 4개(`ItemSlot` TOP·BOTTOM·SHOES·HAT, N-17 유지, 소품 없음). "악세서리"는 꾸미기 탭(HAT)의 앱 문구만 바꾸고 상점 칩은 피그마 H-4대로 "모자"다(서버 `HAT` 그대로). 상점 화면은 H-4 기준("소지 중", 가격 "300", 잔액 "1,240"). 꾸미기 미리보기에 고른 배경을 까는 것(FR-202)은 앱 일이다.
- 모든 일시는 `+09:00` 오프셋 ISO-8601 문자열(기존 규칙). 모든 엔드포인트는 회원 JWT(`Authorization: Bearer …`)가 필요하고 없으면 401이다.

## 1. 엔드포인트

| # | 메소드 | 경로 | 권한 | 요청 | 응답 | 오류 |
|---|---|---|---|---|---|---|
| E1 | GET | `/api/v1/avatar/runner` | 회원 | 없음 | 200 `RunnerResponse` | 401 |
| E2 | GET | `/api/v1/avatar/backgrounds` | 회원 | 없음 | 200 `BackgroundsResponse` (필드 추가) | 401 |
| E3 | PUT | `/api/v1/avatar/background` | 회원 | `BackgroundRequest {key: String?}` (기존) | 200 `BackgroundsResponse` (필드 추가) | 401, 409 `BACKGROUND_LOCKED` |
| E5 | GET | `/api/v1/shop?slot={ItemSlot}` | 회원 | slot 생략 = 전체 | 200 `ShopResponse` (필드 추가) | 401, 400 `BAD_REQUEST`(slot 값 오류) |
| E6 | POST | `/api/v1/shop/purchases` | 회원 | `PurchaseRequest {itemId: Long}` (기존) | 200 `PurchaseResponse {itemId, balance}` (기존) | 401, 409 `ITEM_NOT_FOUND`·`ITEM_NOT_ON_SALE`·**`ITEM_LOCKED`(신규)**·`ALREADY_OWNED`·`INSUFFICIENT_REWARD` |

오류 본문은 기존 `ErrorResponse {code, message}`다. E6 판정 순서: 목록에 없음(`ITEM_NOT_FOUND`) → 판매 중지(`ITEM_NOT_ON_SALE`) → 잠김(`ITEM_LOCKED`, 이미 가진 아이템이면 잠김 판정을 하지 않는다) → 이미 보유(`ALREADY_OWNED`) → 잔액 부족(`INSUFFICIENT_REWARD`).


## 2. 응답 record

### 2.1 `RunnerResponse` (E1, 신규)

| 필드 | 형식 | null | 뜻 |
|---|---|---|---|
| `bodyShape` | String enum `BodyShape` (STAGE1·STAGE2·STAGE3·STAGE4) | 아님 | 지금 체형. GET /avatar의 `bodyShape`와 같은 값(매일 0시 재계산, FR-163 4단계 그대로) |
| `bodyVariant` | int 0~2 | 아님 | 화면·렌더러용 3단계 체형(0 = 기본, 1 = 변화 1, 2 = 변화 2). STAGE1 → 0, STAGE2·STAGE3 → 1, STAGE4 → 2. 칩 "근육 Lv.{bodyVariant + 1}", 게이지 위치, W9 Rive 체형 입력에 쓴다(6절) |
| `skinTone` | String enum `SkinTone` (DAWN·MORNING·DAYTIME·EVENING·NIGHT) | 아님 | 지금 피부톤(시간대, FR-164). GET /avatar의 `skinTone`과 같은 값. 칩 "{톤} 피부톤"과 근거 문구용 |
| `typicalStartHour` | Integer 0~23 | **null 가능** | 피부톤 근거 대표 시각(시). 최근 유효 러닝 10회 시작 시각 중 지금 피부톤 시간대에 속한 것들에서 가장 많은 시(같으면 그중 가장 최근 러닝의 시). 그런 러닝이 없으면 null |
| `recentRunCount` | int ≥ 0 | 아님 | 오늘을 포함한 최근 7일(오늘−6일 ~ 오늘, 한국 날짜) 유효 러닝 횟수. "이번 주 n번 달렸어요"에 쓴다. 조회 때마다 계산해 오늘 달린 것도 들어간다 |
| `recentDistanceMeters` | long ≥ 0 | 아님 | 같은 7일의 유효 거리 합(m) |
| `runAmount` | int 0~100 | 아님 | W9 Rive 피부톤 입력 러닝량. min(100, ⌊recentDistanceMeters × 100 / 30000⌋), 곧 최근 7일 30km 이상이면 100. 서버가 계산한다(6절) |
| `restDaysBeforeDecline` | int ≥ 1 | 아님 | 운영 기준값 체형규칙의 쉼 일수(기본 4). "{n}일 넘게 쉬면 배가 나오기 시작해요" |

체형 게이지는 **3칸 단계값**이다(연속값 아님). 손잡이 위치 비율 = `bodyVariant / 2` (0 왼쪽 끝 "배가 볼록", 2 오른쪽 끝 "근육 탄탄"). 서버 체형은 FR-163대로 4단계로 계산·저장하고 알림도 4단계 변화로 보낸다. 화면과 렌더러만 3단계로 접는다.

### 2.2 `BackgroundsResponse` (E2·E3)

| 필드 | 형식 | null | 뜻 |
|---|---|---|---|
| `selectedKey` | String | null 가능 | 고른 배경 키, null이면 기본 배경 (기존) |
| `backgrounds` | List<`Background`> | 아님 | 기존 순서(개인 랜드마크 거리 순, 그 뒤 표에서 빠졌지만 해금한 배경) (기존) |
| `totalMeters` | long ≥ 0 | 아님 | **신규**. 내 누적 유효 거리(m). 해금 판정과 같은 값 |

`Background`:

| 필드 | 형식 | null | 뜻 |
|---|---|---|---|
| `key` | String | 아님 | 랜드마크 키(예 `DAEJEON`) (기존) |
| `name` | String | 아님 | 이름(예 "대전") (기존) |
| `stage` | String (DOMESTIC·EAST_ASIA·INTERCONTINENTAL) | 아님 | (기존) |
| `distanceMeters` | long | 아님 | 해금 기준 누적 거리(m) (기존) |
| `unlocked` | boolean | 아님 | (기존) |
| `unlockedAt` | OffsetDateTime | 잠겼으면 null | (기존). 앱 해금 팝업 판정 기준(5.2 H-6) |
| `remainingMeters` | long ≥ 0 | 아님 | **신규**. 해금까지 남은 거리 = max(0, distanceMeters − totalMeters). 해금했으면 0. 잠겼는데 0이면 해금 처리가 곧 끝나는 중이다(러닝 저장 직후 비동기 해금) |

해금 팝업(H-6) 판정은 **앱 기기 저장소**(`BackgroundUnlockSeenStore`)로 한다(설계 05-29 결정, 서버에 "확인" 상태를 두지 않는다). 서버는 위 `unlockedAt`만 준다. 앱 규칙은 5.2 H-6 행을 따른다.

### 2.3 `ShopResponse.Item` (E5)

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

### 2.4 enum·오류 code 변경

| 종류 | 변경 |
|---|---|
| `ReasonCode` | `ITEM_LOCKED("아직 해금하지 않은 아이템")` 추가, HTTP 409 |
| 그 밖의 enum | 변경 없음 (`ItemSlot` 4값 그대로) |

## 3. Flyway

| 파일 | 내용 |
|---|---|
| `V62__shop_items_seed.sql` | 운영 기준값 `SHOP_ITEMS` 행이 **없을 때만** 기본 상점 목록(16개, 그림 키·해금 조건 포함)을 넣는다. 운영자가 이미 넣은 목록은 건드리지 않는다. local 프로필(H2)은 `import-local.sql`에 같은 행을 넣는다 |

V62 기본 목록(앱 그림 리소스 대응표의 기준):

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
  "bodyVariant": 1,
  "skinTone": "MORNING",
  "typicalStartHour": 7,
  "recentRunCount": 3,
  "recentDistanceMeters": 15400,
  "runAmount": 51,
  "restDaysBeforeDecline": 4
}
```

러닝이 한 번도 없는 회원: `{"bodyShape":"STAGE1","bodyVariant":0,"skinTone":"DAYTIME","typicalStartHour":null,"recentRunCount":0,"recentDistanceMeters":0,"runAmount":0,"restDaysBeforeDecline":4}`.

E2 `GET /api/v1/avatar/backgrounds` (누적 287km, 이번 러닝으로 개성·대전을 엶, 앞 4개만 보임)

```json
{
  "selectedKey": "INCHEON",
  "totalMeters": 287000,
  "backgrounds": [
    {"key": "INCHEON", "name": "인천", "stage": "DOMESTIC", "distanceMeters": 60000, "unlocked": true,
     "unlockedAt": "2026-11-01T08:12:00+09:00", "remainingMeters": 0},
    {"key": "KAESONG", "name": "개성", "stage": "DOMESTIC", "distanceMeters": 120000, "unlocked": true,
     "unlockedAt": "2026-11-09T07:40:00+09:00", "remainingMeters": 0},
    {"key": "DAEJEON", "name": "대전", "stage": "DOMESTIC", "distanceMeters": 280000, "unlocked": true,
     "unlockedAt": "2026-11-09T07:40:00+09:00", "remainingMeters": 0},
    {"key": "JEONJU", "name": "전주", "stage": "DOMESTIC", "distanceMeters": 400000, "unlocked": false,
     "unlockedAt": null, "remainingMeters": 113000}
  ]
}
```


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
| `api/avatar/AvatarApi.kt` | `@GET("avatar/runner") runner(): RunnerResponse` |
| `api/avatar/RunnerResponse.kt`(신규) | 2.1 필드 그대로. `typicalStartHour: Int?` |
| `api/avatar/BackgroundsResponse.kt` | `totalMeters: Long = 0`, Background에 `remainingMeters: Long = 0` (옛 서버 대비 기본값) |
| `api/avatar/ShopResponse.kt` | Item에 `assetKey: String? = null`, `locked: Boolean = false`, `unlockCondition: String? = null` (JSON 이름 그대로) |
| `domain/avatar/AvatarRepository` | `suspend fun runner(): Outcome<AvatarRunner>` (설계 05-29는 `Avatar`에 `weeklyRunCount`·`skinToneHour`를 두자고 했다. 서버는 GET /avatar를 바꾸지 않고 E1로 따로 준다. 앱이 `Avatar`에 합치려면 `weeklyRunCount` ← `recentRunCount`, `skinToneHour` ← `typicalStartHour`로 옮긴다) |
| `domain/avatar/AvatarRunner`(신규) | `bodyShape: BodyShape, bodyVariant: Int, skinTone: SkinTone, typicalStartHour: Int?, recentRunCount: Int, recentDistance: Distance, runAmount: Int, restDaysBeforeDecline: Int` + 게이지 비율 `bodyVariant / 2f` |
| `domain/avatar/AvatarBackgrounds` | `totalDistance: Distance`, Item에 `remaining: Distance` |
| `domain/avatar/Shop.Item` | `imageKey: String?`(← `assetKey`), `isLocked: Boolean`(← `locked`), `unlockLabel: String?`(← `unlockCondition`) (설계 05-29 이름) |
| `android/contract` | `python android/contract/sync_from_server.py`로 계약 자료 갱신(ReasonCode `ITEM_LOCKED`, DTO 필드) |

### 5.2 화면

| 화면 | Route 제안 | UiState·동작 |
|---|---|---|
| H-2 내 러너(신규) | `Routes.AVATAR_RUNNER = "avatar/runner"` | `AvatarRunnerUiState(avatar: Avatar?, runner: AvatarRunner?, backgroundKey, balance, loading, error)`. `avatar()`·`runner()`를 함께 읽는다. 칩 "근육 Lv.{bodyVariant+1}"·"{톤} 피부톤", 게이지 손잡이 `bodyVariant / 2`, 안내 "이번 주 {recentRunCount}번 달렸어요. {restDaysBeforeDecline}일 넘게 쉬면 배가 나오기 시작해요.", 피부톤 근거는 `typicalStartHour`가 있으면 "주로 {오전/오후 h시}에 달려서 {톤} 톤이에요", null이면 "달린 시간대에 따라 피부톤이 바뀌어요". 하단 "꾸미기" → `Routes.AVATAR`, "상점 가기" → `Routes.SHOP` |
| 홈 아바타 진입 | 홈 아바타 누름 → `Routes.AVATAR_RUNNER` (지금 `Routes.AVATAR`, HomeScreen.kt:529) | 홈 잔액 알약은 그대로 상점 |
| H-3 꾸미기 배경 탭 | 기존 `Routes.AVATAR_BACKGROUND` | 잠긴 칸 문구 "{name}, {remainingMeters를 km 올림 정수}km 더 달리면 해금"(0이면 "{name}, 곧 해금돼요"). 미리보기 카드에 고른 배경을 깐다(FR-202) |
| H-4 상점 | 기존 `Routes.SHOP` | `ShopUiState.selected: Shop.Item?` 추가. 칸 누름 = 선택(잠긴 칸은 선택하지 않고 스낵바로 `unlockCondition`). 하단 구매 바: 이름 + "사면 {balance-price} 남아요", 버튼 "{price}에 사기" → H-5 확인 → `purchase`. 잔액 부족은 지금처럼 확인 없이 스낵바(FR-160). 칸 그림은 `assetKey`로 리소스를 찾고 없으면 부위 기본 그림. 잠긴 칸 opacity 0.45, 가격 자리에 `unlockCondition`. 보유 칸은 "소지 중". `ITEM_LOCKED` 응답이면 상점을 다시 읽고 스낵바 |
| H-6 배경 해금 팝업(신규) | 홈(A5) 위 대화상자, 라우트 없음 | 기기 저장소 `BackgroundUnlockSeenStore`(회원별 "확인한 해금 키 집합"과 "처음 기록 시각")로 판정한다. 홈 진입·복귀(ON_RESUME) 때 아바타 꾸미기 공개(AVATAR_RELEASED)면 `backgrounds()`를 읽어, `unlocked`이고 저장소에 없는 배경을 새 해금으로 본다. 저장소가 처음이면(설치 직후·새 기기·로그인 회원 변경) 지금 해금된 키를 모두 확인한 것으로 채우고 띄우지 않는다(배포·재설치 직후 팝업이 쏟아지지 않게). 새 해금이 있으면 **`distanceMeters` 내림차순**(같으면 `unlockedAt` 최신, 그다음 `key`)으로 정렬해 첫 배경을 제목("{name} 배경을 열었어요")으로, 2개 이상이면 "이번 러닝으로 함께 연 배경" 상자에 같은 순서로 이름을 모두 적는다(1개면 상자 없음). "닫기"·"배경 바꾸기" 모두 보인 키를 저장소에 넣는다. "배경 바꾸기" → `chooseBackground(첫 배경 key)`. 서버 알림 BACKGROUND_UNLOCKED 경로(알림함 → 배경 탭)는 그대로다 |

### 5.3 그림 리소스 키

앱 리소스 이름 제안: `shop_item_{assetKey}` (예 `R.drawable.shop_item_top_lime_singlet`). 대응표는 V62 목록(3절)이다. 운영자가 새 아이템에 새 키를 쓰면 앱 배포 전까지 부위 기본 그림으로 보인다.

## 6. W9 렌더러 입력 (S15P31A207-300 아바타 Rive 통합)

근거: `C:/Users/SSAFY/Documents/Codex/2026-11-03/new-chat/AVATAR_POC_ONBOARDING.md` 3절(제품 규칙)·5절(API·레이어 매핑), 저장소 `avatar-ai/INTEGRATION.md`. 앱 아바타는 남녀 Rive 몸체(정면 = 서기, 오른쪽 옆모습 = 달리기)에 셀카 머리 레이어를 붙여 그린다. 입력은 머리 레이어 8장 + 합성 메타, 성별, 의상 상의·하의·신발 0~6, 체형 3단계, 러닝량 0~100이다.

### 6.1 입력별 출처

| 렌더러 입력 | 출처(서버 API·필드) | 비고 |
|---|---|---|
| 머리 레이어 8장 | `GET /api/v1/avatar/heads/{ownerId}/{jobId}` (남의 아바타 포함), `GET /api/v1/avatar/selfie/latest`(내 최근), `GET /api/v1/avatar/selfie/jobs/{jobId}/result`(방금 끝난 작업) → `GeneratedHead` | GET /avatar의 `faceKey`가 `head:{ownerId}:{jobId}`이면 셀카 머리다. `default:{캐릭터}`면 셀카 머리가 없고 기본 캐릭터 얼굴이다 |
| 목 연결·피부색 | 같은 `GeneratedHead.mobile.neckMask`(객체, **null·없음 가능**), `mobile.skinColor`(RGB 0~255 세 값 배열, **null 가능**) | `mobile`은 ai가 준 객체를 그대로 넘긴다(canvas, anchor, renderFaceImage, displayHeight, neckRemoved, skinColor, neckMask, scene …). null이면 몸 피부색 유지·기본 목 연결 |
| 성별(남·여 몸체) | **아바타 응답에는 없다.** 내 아바타 = 회원 프로필의 내 성별(FR-162: 여성이면 여 몸체, 남성·밝히지 않음·미입력이면 남 몸체). 남의 아바타 = 각 응답의 `shownGender`(랭킹·크루·대결·프로필, D-327) | 이번에 서버를 바꾸지 않았다. 앱의 지금 기본 캐릭터 성별 규칙을 그대로 쓴다 |
| 의상 상의·하의·신발 0~6 | GET /avatar `equipped`(부위 → itemId) → GET /shop의 `assetKey` → 앱 대응표(6.3) | 그 부위를 안 입었거나 대응표에 없으면 0(기본 의상: 흰 반팔티, 검은 반바지, 흰 신발). 모자(HAT)는 Rive 슬롯이 없어 지금은 그리지 않는다 |
| 체형 3단계 | `GET /api/v1/avatar/runner`의 `bodyVariant` 0~2 | 0 = 기본, 1 = 변화 1, 2 = 변화 2. 서버 4단계(STAGE1 → 0, STAGE2·3 → 1, STAGE4 → 2)를 서버가 접는다. 남의 아바타는 이 값이 없으므로 0(기본)으로 그린다 |
| 러닝량 0~100(피부톤) | `GET /api/v1/avatar/runner`의 `runAmount` | **서버가 계산한다**(최근 7일 유효 거리 30km = 100, 비례, 버림). 앱 여러 화면·기기가 같은 값을 쓰게 하고 기준을 서버 한 곳에서 바꾸기 위해서다. 낮으면 밝게, 높으면 어둡게(PoC 규칙). 남의 아바타는 이 값이 없으므로 0으로 그린다 |

주의: FR-164 피부톤(`skinTone`, 달리는 시간대 5톤)과 W9 피부톤(러닝량에 따라 밝기)은 규칙이 다르다. 이번 계약은 둘 다 내준다. 내 러너 화면 칩·근거 문구는 `skinTone`·`typicalStartHour`, Rive 몸 피부 밝기는 `runAmount`다. 하나로 합칠지는 사용자 결정이 필요하다(보고서 결정 목록).

### 6.2 `GeneratedHead` (셀카 머리, 응답 필드 확장)

| 필드 | 형식 | null | 뜻 |
|---|---|---|---|
| `headId` | String | 아님 | (기존) |
| `userId` | String | 가능 | (기존) |
| `faceImage` | PNG Data URL | 아님 | 정면 피부 기반·코·입·귀·목 (기존) |
| `eyesImage` | PNG Data URL | 아님 | 정면 눈·속눈썹 (기존) |
| `hairImage` | PNG Data URL | 아님 | 정면 머리카락·눈썹·안경 (기존) |
| `frontImage` | PNG Data URL | **없음 가능** | **신규 전달**. 완성된 정면 머리 원본 |
| `rightImage` | PNG Data URL | **없음 가능** | **신규 전달**. 완성된 오른쪽 옆모습 머리 원본 |
| `rightFaceImage` | PNG Data URL | **없음 가능** | **신규 전달**. 옆모습 피부 기반·코·입·귀·목 |
| `rightEyesImage` | PNG Data URL | **없음 가능** | **신규 전달**. 옆모습 보이는 눈·속눈썹 |
| `rightHairImage` | PNG Data URL | **없음 가능** | **신규 전달**. 옆모습 머리카락·눈썹·안경 |
| `rightLayerLayout` | 객체 | **없음 가능** | **신규 전달**. ai가 준 그대로(`version`, `direction`, `canvas`, `coordinateSpace`, `independentTransforms`) |
| `traits`, `hairLength`, `alignment`, `mobile`, `createdAt` | (기존) | | `mobile`은 그대로 넘긴다(6.1) |

- 모든 PNG는 `data:image/png;base64,…`, **1024×1024 공통 좌표**이고 잘라 내지 않았다. 서버는 PNG 머리표지와 크기(1024 이하)를 검사하고 맞지 않으면 503 `UPSTREAM_FAILED`다.
- 신규 5개 이미지와 `rightLayerLayout`은 **값이 없으면 JSON에서 빠진다**(null을 싣지 않는다). 예전에 만든 얼굴이나 ai가 아직 옆모습을 주지 않으면 없다. 앱은 `rightFaceImage`·`rightEyesImage`·`rightHairImage` 셋이 모두 있을 때만 옆모습(달리기)에 셀카 머리를 쓰고, 하나라도 없으면 달리기 장면은 정면 레이어를 쓰거나 기본 머리로 그린다.
- 그리는 순서(PoC 규칙): 몸 피부(톤 적용) → 의상 → 얼굴 → 눈 → 헤어. 같은 위치·배율. 피부톤 필터는 눈·헤어·안경·옷에 적용하지 않는다. 정면용 `mobile.anchor`·`renderFaceImage`는 옆모습 좌표가 아니므로 옆모습에 다시 쓰지 않는다.
- 서버는 이미지를 저장하지 않고 ai(avatar-ai)가 저장한 결과를 전달만 한다(기존 패턴, D-59). 응답은 `Cache-Control: no-store`다.

응답 표본 `GET /api/v1/avatar/heads/{ownerId}/{jobId}` (PNG는 줄임, 실제는 1024×1024 PNG 전체 Data URL):

```json
{
  "headId": "head-7f3c",
  "userId": "42",
  "faceImage": "data:image/png;base64,iVBORw0KGgo…",
  "eyesImage": "data:image/png;base64,iVBORw0KGgo…",
  "hairImage": "data:image/png;base64,iVBORw0KGgo…",
  "traits": {"hair": "short", "glasses": true},
  "hairLength": "short",
  "alignment": {"pivotX": 0.5},
  "mobile": {
    "canvas": [1024, 1024],
    "anchor": [0.5, 0.88],
    "renderFaceImage": "data:image/png;base64,iVBORw0KGgo…",
    "displayHeight": 0.42,
    "neckRemoved": true,
    "skinColor": [236, 196, 170],
    "neckMask": {"top": 0.8}
  },
  "createdAt": "2026-11-09T08:12:00Z",
  "frontImage": "data:image/png;base64,iVBORw0KGgo…",
  "rightImage": "data:image/png;base64,iVBORw0KGgo…",
  "rightFaceImage": "data:image/png;base64,iVBORw0KGgo…",
  "rightEyesImage": "data:image/png;base64,iVBORw0KGgo…",
  "rightHairImage": "data:image/png;base64,iVBORw0KGgo…",
  "rightLayerLayout": {"version": "runkk-right-head-layers-v1", "direction": "right", "canvas": [1024, 1024],
                       "coordinateSpace": "shared-source", "independentTransforms": false}
}
```

옆모습이 없는 예전 얼굴은 위에서 `frontImage`~`rightLayerLayout` 6개가 통째로 빠진다. `mobile.skinColor`·`mobile.neckMask`는 `null`이거나 없을 수 있다(ai가 준 그대로). 앱 렌더러(`GeneratedHeadLayers.fromHeadJson`)는 `rightFaceImage`·`rightEyesImage`·`rightHairImage`가 모두 있어야 셀카 옆모습을 쓰고, `frontImage`·`rightImage`는 있으면 쓴다. 같은 모양을 `GET /avatar/selfie/latest`, `GET /avatar/selfie/jobs/{jobId}/result`도 돌려준다.

### 6.3 의상 대응표 (assetKey → Rive 의상 번호, 제안)

| 부위 | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|---|
| 상의(TOP) | 기본 흰 반팔티 | `top_lime_singlet` | `top_dawn_windbreaker` | `top_crew_tee` | `top_winter_hoodie` | `top_cherry_vest` | `top_finisher_tee` |
| 하의(BOTTOM) | 기본 검은 반바지 | `bottom_running_shorts` | `bottom_jogger_pants` | `bottom_leggings` | (비어 있음) | (비어 있음) | (비어 있음) |
| 신발(SHOES) | 기본 흰 신발 | `shoes_basic_runner` | `shoes_lime_runner` | `shoes_carbon_racer` | (비어 있음) | (비어 있음) | (비어 있음) |

Rive 의상 그림 1~6이 실제로 어떤 옷인지에 맞춰 B단계가 이 표를 고친다. 서버는 의상 번호를 모르고 `assetKey`만 준다(그림 번호를 바꿔도 서버 배포가 필요 없다).

### 6.4 내 러너 화면 예

GET /avatar(얼굴·착용·배경·잔액) + GET /avatar/runner(체형·러닝량·피부톤 근거) + 셀카 머리면 GET /avatar/heads/{ownerId}/{jobId}를 함께 읽어 Rive 정면(서기)으로 그린다. 홈의 달리는 장면은 옆모습 레이어를 쓴다.
