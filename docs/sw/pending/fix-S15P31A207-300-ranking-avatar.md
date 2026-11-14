# 병합 대기: fix/S15P31A207-300-ranking-avatar

> 작성: 2026-11-13 | 작성자: 이성현 | 스킬: sw-design-oo(구현 반영) | (보완 2026-11-14) sw-code-review: code-review/26-랭킹-다른러너-아바타-성, sw-test: test/랭킹-다른러너-아바타-성, sw-sqa: sqa/랭킹-다른러너-아바타-성
>
> 실제 브랜치는 `feature/S15P31A207-300-ranking-look`(MR !156)이다. 파일 이름은 처음 잡은 브랜치 이름을 그대로 둔다(OWNERS 4절 이름 규칙과 다름, [임시:AR-성-92]). 병합 때 공용 담당이 정리한다.

개인 랭킹 줄마다 그 회원의 모습(얼굴·의상·체형·러닝량)을 붙인다. 처음에는 서버만 바꾸려 했으나 이 브랜치에 앱 변경(2e808410: 시상대 전신 정지 그림, 4위부터 얼굴 원, 다른 러너 그을림)도 들어 있다. look은 스냅숏 단위 Redis 캐시로 하루 고정한다(PM 2026-11-14): 같은 스냅숏 동안 모두가 같은 모습을 읽고 요청마다 DB를 다시 읽지 않는다.

## 고친 조각
- design-oo/05-30-설계-아바타요구-성.md: 12절 표 끝에 [임시:D-성-226](랭킹 줄 `look`, web.record → avatar)
- (2026-11-14) code-review/26-랭킹-다른러너-아바타-성.md: 새 조각(`/sw-code-review`). 합격(결함 0 / 0 / 6, 남은 0 / 0 / 4), [임시:AR-성-87]~[임시:AR-성-92], 순환 복잡도 10 초과 없음, 설계서 갱신 U26-1~U26-3
- (2026-11-14) test/랭킹-다른러너-아바타-성.md: 새 조각(`/sw-test`). 계획 완성, 화이트박스 다섯(`find` V(G) 7, `snapshotLooksOf` 5, `onFaceShown` 4, `rankingFaceTone` 4, `looksOf` 조건 3), [임시:TC-성-274]~[임시:TC-성-334], 시험 10건 추가(backend 7, android 3 — 기능 코드를 잠시 틀리게 바꿔 실패 먼저 확인), 실행 결과 9.2
- (2026-11-14) sqa/랭킹-다른러너-아바타-성.md: 새 조각(`/sw-sqa`). develop 병합 판정 **조건부**(1인 검토, 확인은 부분, NFR-001 미측정), [임시:DF-성-20]~[임시:DF-성-23], 새 위험 [임시:R-성-25]~[임시:R-성-30]

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:D-성-226] | D | 개인 랭킹 줄·mine에 회원 모습 `look`을 붙인다(크루·탈퇴는 null), web.record가 avatar.RunnerLookQuery로 붙인다. look은 스냅숏 단위 Redis 캐시로 하루 고정(PM 2026-11-14, 키 `ranking:look:{종류}:{기간}:{집계 시각 ms}`, 만료 25시간, 지연 채움) | design-oo/05-30 12절 |
| [임시:AR-성-87]~[임시:AR-성-92] | 코드 리뷰 결함(경 6) | 87 얼굴 키 판정 두 곳, 88 받기 실패 뒤 다시 받지 않음, 89 `SelfieHeads` 전역 잠금으로 시상대 머리 차례 받기, 90 `HSET`·`EXPIRE` 따로, 91 시험 공백(시험 추가로 해소), 92 병합 대기 파일 이름·머리말(머리말 고침) | code-review/26 2절 |
| [임시:TC-성-274]~[임시:TC-성-334] | 시험 케이스 | 단위 274~321, 통합 322~326, 검증 327~330, 시스템 331~334 | test/랭킹-다른러너-아바타-성 |
| [임시:DF-성-20] | SQA 결함(경, 모호) | PM 결정 「0시 스냅숏에 같이 저장」과 구현 「스냅숏 뒤 첫 조회 때 그 시각 모습으로 고정」의 차이, PM 확인 | sqa/랭킹-다른러너-아바타-성 2절 |
| [임시:DF-성-21] | SQA 결함(경, 누락) | srs FR-070 출력에 줄 모습 없음(박재영 승인 대기) | 같은 곳 |
| [임시:DF-성-22] | SQA 결함(경, 누락) | 앱 쪽 설계 결정 없음(U26-1) | 같은 곳 |
| [임시:DF-성-23] | SQA 결함(경, 누락) | 시스템 시험(NFR-001, Redis 장애, 실기기) 미실행 | 같은 곳 |
| [임시:R-성-25]~[임시:R-성-30] | 위험 | 25 시상대 머리 지연, 26 하루 지연 표시, 27 머리 받기 avatar-ai 부하, 28 Redis 장애 대체 부하·만료 없는 키, 29 흔들리는 시험 `AiCoachControllerTest`, 30 `SelfieHeads` 6칸 메모리 | sqa/랭킹-다른러너-아바타-성 6절 |

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| design-oo 5.7.1 web.record API 표(`GET /rankings/personal`) | 응답 Entry에 `look`(객체 또는 null) {faceKey, outfit{ItemSlot 이름: assetKey}, bodyVariant 0~2, runAmount 0~100}. `GET /rankings/crew`는 늘 null. 필드 더하기만이라 옛 앱은 그대로 동작 |
| design-oo 12.2 패키지 의존 행렬·12.3 허용 표 | web.record → avatar 한 줄(RunnerLookQuery.snapshotLooksOf, D-46 표현 계층이 붙임). `ArchitectureTest` 허용 표에 반영함 |
| design-oo 6.1.8 avatar 클래스 목록 | `RunnerLook`(record), `RunnerLookQuery`(looksOf, snapshotLooksOf), `RunnerLookSnapshotStore`(포트), `AvatarRepository.findAllByMemberIds`(만들지 않음, fetch join) |
| design-oo 6.1.13 infra 클래스 목록 | `infra.avatar.RedisRunnerLookSnapshotStore`(키 `ranking:look:{종류}:{기간}:{집계 시각 ms}` 해시, 만료 25시간, 장애면 비어 있다고 봄) |
| design-oo 5.5 장애: Redis | 랭킹 look 캐시를 못 쓰면 요청마다 DB에서 읽는다(랭킹 조회는 실패하지 않음). (2026-11-14 U26-3) 다른 러너 머리 받기가 머리마다 avatar-ai를 부르는 부하([임시:R-성-27]) |
| design-oo 6.1.4 RankingView | `activeMemberIds()`(publicTiers 키 = 탈퇴하지 않은 개인 줄 회원) |
| srs 자료사전 랭킹 줄 | `look` 항목 |
| code-review.md 목차·머리 판정 | 「리뷰 2026-11-14 (랭킹 다른 러너 아바타, S15P31A207-300 후속)」 절: 조각 [26-랭킹-다른러너-아바타-성](code-review/26-랭킹-다른러너-아바타-성.md), 범위 origin/develop..a0921cd4(MR !156), 0 / 0 / 6(남은 0 / 0 / 4), 합격, 설계 갱신 U26-1~U26-3. 정식 결함 번호 매기기 |
| test.md 목차·추적표 | 조각 [랭킹-다른러너-아바타-성](test/랭킹-다른러너-아바타-성.md) 연결, 06-추적표에 FR-070·FR-071·FR-290·FR-006 ↔ [임시:TC-성-274]~[임시:TC-성-334](조각 8절), 정식 TC 번호 |
| sqa.md 목차·머리 판정 | 조각 [랭킹-다른러너-아바타-성](sqa/랭킹-다른러너-아바타-성.md) 연결: MR !156 develop 병합 조건부, 조건 8.1(사람 리뷰, PM 실기기 확인, [임시:DF-성-20] PM 확인, NFR-001 측정 기한, 박재영 FR-070 승인) |
| plan 9절 위험 표 | [임시:R-성-25]~[임시:R-성-30](sqa 조각 6절 그대로), R-003 징후 갱신(머리 받기 호출 증가) |
| docs/sw/README.md 지도 | 세 새 조각 |

## 다른 담당에게 요청
- 박재영: srs/03-03-기록-분석.md FR-070(개인 랭킹) 출력에 「줄마다 회원 모습(look)」을 「다음 판 변경」으로 더해 주세요(제공 쪽이 avatar라 서버는 이 브랜치에서 구현함, 승인 요청). (2026-11-14) 승인되지 않으면 sqa [임시:DF-성-21]을 요구 없는 기능(중)으로 올린다.
- 박재영: (2026-11-14) backend `AiCoachControllerTest.revokingConsentClosesActiveAiStreamAndStopsDelivery`가 실행 순서에 따라 흔들린 이력이 있다([임시:R-성-29]). 원인 확인을 부탁한다(이 브랜치에서는 고치지 않음).

## 본인 담당 후속 (이성현)
- design-oo/05-30 12절: U26-1 앱 쪽 결정 한 줄(응답 look → 도메인 → 시상대 `AvatarStillSubject.Look`·`runnerLookOf` 범위 자르기, 4위부터 보이는 줄만 `onFaceShown`, 다른 러너 그을림 `rankingFaceTone`, `SelfieHeads` 6칸, 받기 실패 정책), U26-2 「집계 시각 epoch 밀리초」 → 「집계 시각을 UTC로 본 밀리초(RankingCache 판 번호와 같은 식)」
