# 병합 대기: fix/S15P31A207-300-ranking-my-face

> 작성: 2026-11-15 | 작성자: 이성현 | 스킬: sw-code-review(결함 처리), 입력: PM 보고 2026-11-15 「랭킹에 내 프로필 기본 아바타로만 나오는데?」
>
> 랭킹 화면의 「나」 얼굴 원(하단 고정 내 순위 카드, 4위부터 목록의 내 줄)이 홈 아바타와 다른 원천을 써서 셀카 머리가 있어도 기본 캐릭터 얼굴로 보였다. sqa/랭킹-다른러너-아바타-성.md 「범위 밖 지적」의 「내 순위 카드 얼굴은 여전히 기본 머리(`faceKey = null`)」를 PM이 결함으로 보고했다.

## 원인
| 곳 | 원인 |
|----|------|
| `RankingParts.kt` `RankingMineCard` | 얼굴을 `RunkkAvatar(faceKey = null, FACE, character = LocalAvatarCharacter.current)`로 그렸다. 내 얼굴 키·층을 받을 길이 없어 늘 성별 기본 머리였다 |
| `RankingParts.kt` `RankingFaceCircle`(내 줄) | 내 줄도 서버 랭킹 응답의 `entry.look?.faceKey`를 썼다. 모습은 스냅숏 단위로 하루 고정(D-858)이라 오늘 바꾼 셀카가 안 보이고, 모습이 없거나 기본 캐릭터 키면 기본 머리였다 |
| `RankingViewModel`·`RankingUiState` | 내 얼굴을 읽지 않았다 |

시상대(`PodiumStand`)는 `AvatarStillSubject(isMine = true)`로 내 아바타 저장소 모습을 써서 정상이었다(바꾸지 않음).

## 결정
| 항목 | 내용 | 근거 |
|------|------|------|
| 「나」 얼굴 원천 | 랭킹의 내 순위 카드와 목록의 내 줄은 내 아바타 얼굴로 그린다: `AvatarRepository.avatar()`의 얼굴 키(홈 아바타 칸·내 얼굴 톤을 만드는 `MyAvatarStore`와 같은 출처)와 그 셀카 층(`AvatarRepository.faceLayers`, 앱 공용 얼굴 그림 캐시 `FaceImageSource`). `RankingViewModel`이 화면을 열 때 한 번 읽어(`myRankingFace()`) `RankingUiState.myFace: RankingFace?`에 싣는다. 서버 줄 모습(하루 고정)보다 앞선다 | PM 2026-11-15 「랭킹에 내 프로필 기본 아바타로만 나오는데?」 |
| 홈의 `AccountRepository.myAvatar()`·`lastMyAvatar()`를 쓰지 않은 까닭 | `ui.record`의 허용 표(5.1.2)는 `CORE_UI` 3 + `domain.record`·`domain.run`·`domain.avatar`·`ui.share` = Fan-Out 7(상한, 12.4)이다. `domain.account`를 더하면 8이라 `ArchitectureTest`(「5·1·2 표 밖 import 0」, Fan-Out 7 이하)가 막는다. 이미 허용된 `domain.avatar`의 `AvatarRepository`로 같은 서버 얼굴을 읽는다. 대가: 기기에 남긴 마지막 얼굴(`lastMyAvatar`)이 없어 첫 그림은 읽는 동안 빈 자리다(아래 행). 얼굴 층은 공용 캐시라 홈을 거쳐 왔으면 바로 나온다 | 5.1.2, 12.4, D-335 |
| 읽는 중 | 화면을 연 직후 내 얼굴을 읽는 동안(`RankingFace.LOADING`) 두 원 모두 기본 머리 대신 빈 중립 자리(D-846 「기본 얼굴이 먼저 보였다가 바뀌지 않게」) | D-846, [임시:D-성-190] |
| 모를 때 | 아바타를 읽지 못하면 `myFace = null`: 내 순위 카드는 지금처럼 성별 기본 머리, 내 줄은 지금처럼 서버 줄 모습. 다시 시도·범위·기간 바꾸기(`refresh`)에서 한 번 더 읽고, 한 번 읽은 뒤에는 다시 읽지 않는다. 셀카 층을 받지 못하면 빈 층(성별 기본 머리) | 기존 동작 보존 |
| 그을림 | 의미는 그대로다. 내 셀카 얼굴은 앱 전체 내 톤(`LocalSelfFaceTone`, 같은 얼굴 키라 맞는다), 내 얼굴이 기본 캐릭터 키면 내 러닝량으로 그 원만 감싼다. 판정 키만 서버 줄 얼굴 키에서 그 원에 그리는 내 얼굴 키로 바꿨다(`rankingFaceTone(entry, isMine, myTone, myFaceKey)`). 내 순위 카드에도 같은 규칙을 쓴다 | D-859, PM 2026-11-14 |
| 모듈 경계 | `feature:record`는 `domain.avatar`만 더 쓴다(이미 허용, 허용 표·화살표 수 변경 없음). `feature:avatar`·`feature:home`을 가져오지 않는다 | 5.1.2 |

## 고친 곳
| 파일 | 내용 | 시험 |
|------|------|------|
| `android/feature/record/src/main/java/com/a207/runkk/app/ui/record/RankingFace.kt`(새 파일) | 얼굴 원 값 `RankingFace(faceKey, faceLayers, isLoading)`, `RankingFace.LOADING`, 줄 모습 얼굴 `RankingFace.ofRow(entry, faces)`(기존 줄 얼굴 계산을 옮김), 내 얼굴 읽기 `AvatarRepository.myRankingFace()`(아바타 못 읽으면 null, 셀카 키만 층을 받음). 한 파일 한 형식(D-112) | `AnalysisViewModelsTest` 「내 얼굴은 내 아바타의 얼굴 키와 그 층이다, 아바타를 못 읽으면 모르고 기본 캐릭터 키는 층을 받지 않는다」 |
| `.../record/RankingViewModel.kt` | 주 생성자 `internal constructor(records, clock, readMyFace: suspend () -> RankingFace?)`, Hilt `@Inject constructor(records, clock, avatars: AvatarRepository)`. 첫 상태 `myFace = LOADING`, `refresh()`마다 아직 읽지 못했으면 한 번 읽는다 | `AnalysisViewModelsTest` 2건(셀카 머리 키·층을 싣고 읽는 동안 빈 자리, 실패하면 모름·다시 시도에서 한 번 더·읽은 뒤 다시 안 읽음) |
| `.../record/RankingUiState.kt` | `myFace: RankingFace? = null` | 위와 같음 |
| `.../record/RankingParts.kt` | `RankingMineCard(mine, gapToNext, rankAbove, myFace, modifier)`가 같은 `RunkkAvatar`(42dp 원·2dp 테두리 그대로)에 내 얼굴 키·층·읽는 중을 넘긴다. `RankingListCard(rest, mine, faces, myFace, onFaceShown)`의 내 줄은 내 얼굴을 알면(읽는 중 포함) 그것을, 모르면 서버 줄 모습을 쓰고, 내 얼굴은 따로 받지 않는다. `RankingListRow`는 얼굴 원을 슬롯으로 받는다. `rankingFaceTone`에 `myFaceKey` | `RankingLookTest` 4건(내 순위 카드·내 줄 모두 셀카 머리이고 내 얼굴은 따로 받지 않음, 읽는 중이면 두 원 빈 자리, 모르면 기본 얼굴, 그을림 판정 키) |
| `.../record/AnalysisScreens.kt` | `RankingScreen`이 `state.myFace`를 두 부품에 넘긴다 | 위와 같음 |
| `android/feature/record/src/test/.../RecordFakes.kt` | `FakeAvatarRepository`에 `avatarResult`·`faceResults`·`faceCalls` | — |

스냅샷: 바뀐 것 없음. 랭킹 스냅샷(`AnalysisScreensTest`)은 상태를 직접 만들고 `myFace` 기본값 null(모름)이라 지금 그림 그대로다(`verifyRoborazziDebug` 통과).

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| 없음 | | | |

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| design-oo/05-30-설계-아바타요구-성.md D-859 끝 | 「랭킹의 내 순위 카드와 4위부터 목록의 내 줄은 내 아바타 얼굴(`AvatarRepository.avatar()` 얼굴 키, `MyAvatarStore`·홈 아바타와 같은 출처, 층은 `faceLayers`)로 그린다(`RankingViewModel`이 화면을 열 때 한 번 읽어 `RankingUiState.myFace`). 서버 줄 모습(하루 고정)보다 앞선다. 읽는 중이면 중립 자리, 읽지 못하면 내 순위 카드는 성별 기본 머리·내 줄은 서버 줄 모습이고 `refresh`에서 다시 읽는다. 그을림 판정 키는 그 원에 그리는 내 얼굴 키. `ui.record` Fan-Out이 7이라 `domain.account`(`myAvatar`·`lastMyAvatar`)는 쓰지 않는다(PM 2026-11-15, fix/S15P31A207-300-ranking-my-face)」 |
| design-oo/06-06d-클래스-앱-데이터-표현.md 랭킹 행, design-oo/05-23 D-550 시그니처 | `RankingViewModel @Inject constructor(records, clock, avatars: AvatarRepository)`, `RankingUiState.myFace: RankingFace?`, `RankingFace(faceKey, faceLayers, isLoading)`, `RankingMineCard(mine, gapToNext, rankAbove, myFace, modifier)`, `RankingListCard(rest, mine, faces, myFace, onFaceShown)` |
| sqa/랭킹-다른러너-아바타-성.md 「범위 밖 지적」 둘째 줄 | 「내 순위 카드 얼굴은 기본 머리」 → 「PM 보고(2026-11-15)로 결함 처리, fix/S15P31A207-300-ranking-my-face에서 고침(내 줄 포함)」 |
| srs/03-03 FR-070 처리 칸 | 따로 바꿀 문장 없음(「시상대와 본인·목록 행은 순위, 아바타, …」). 본인 행 아바타가 「내 아바타」임을 덧붙일지는 FR 담당(박재영) 판단. 요구 변경은 아니다 |

## 다른 담당에게 요청
- 기록·분석 담당: 이 MR 리뷰. 셀카 머리가 있는 계정으로 랭킹을 열어 하단 내 순위 카드와(4위 밖이면) 목록의 내 줄이 홈 아바타 얼굴과 같은지 실기기 확인
