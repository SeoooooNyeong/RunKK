# 병합 대기: fix/S15P31A207-300-ranking-avatar

> 작성: 2026-11-13 | 작성자: 이성현 | 스킬: sw-design-oo(구현 반영)

개인 랭킹 줄마다 그 회원의 모습(얼굴·의상·체형·러닝량)을 붙인다. 서버만 바꾼다(앱은 따로).

## 고친 조각
- design-oo/05-30-설계-아바타요구-성.md: 12절 표 끝에 [임시:D-성-226](랭킹 줄 `look`, web.record → avatar)

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:D-성-226] | D | 개인 랭킹 줄·mine에 회원 모습 `look`을 조회 때 붙인다(크루·탈퇴는 null), web.record가 avatar.RunnerLookQuery로 붙인다 | design-oo/05-30 12절 |

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| design-oo 5.7.1 web.record API 표(`GET /rankings/personal`) | 응답 Entry에 `look`(객체 또는 null) {faceKey, outfit{ItemSlot 이름: assetKey}, bodyVariant 0~2, runAmount 0~100}. `GET /rankings/crew`는 늘 null. 필드 더하기만이라 옛 앱은 그대로 동작 |
| design-oo 12.2 패키지 의존 행렬·12.3 허용 표 | web.record → avatar 한 줄(RunnerLookQuery.looksOf, D-46 표현 계층이 붙임). `ArchitectureTest` 허용 표에 반영함 |
| design-oo 6.1.8 avatar 클래스 목록 | `RunnerLook`(record), `RunnerLookQuery`(looksOf), `AvatarRepository.findAllByMemberIds`(만들지 않음, fetch join) |
| design-oo 6.1.4 RankingView | `activeMemberIds()`(publicTiers 키 = 탈퇴하지 않은 개인 줄 회원) |
| srs 자료사전 랭킹 줄 | `look` 항목 |

## 다른 담당에게 요청
- 박재영: srs/03-03-기록-분석.md FR-070(개인 랭킹) 출력에 「줄마다 회원 모습(look)」을 「다음 판 변경」으로 더해 주세요(제공 쪽이 avatar라 서버는 이 브랜치에서 구현함, 승인 요청).
