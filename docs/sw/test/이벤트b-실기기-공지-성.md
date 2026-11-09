# 이벤트 기능 시험 (이어서: 배포 뒤 확인, 운영자 공지 알림)

> 작성: 2026-11-09 | 버전: v0.26 | 입력: test/이벤트-성.md 8~14절을 그대로 옮김(100KB를 넘어 나눔, 장, 절, TC 번호 변경 없음), 병합 대기 docs-S15P31A207-275-notice-check.md(이성현, 2026-11-09, sw-sdlc merge) 8절 18 실행 결과

[런크크 테스트 계획 및 케이스](../test.md) 목차, 이전: [이벤트-성](이벤트-성.md)

## 8. 배포 뒤 실기기 확인 체크리스트

진입 조건: 10절 자동 시험 합격, 핫픽스 0.11.4 서버 배포와 앱(versionName 0.11.4) 설치. 기기: 폰 1대(Android 16 이상 권장), 계정 둘(응모 없는 계정, 응모 있는 계정). 순서대로 보고 「결과」 칸에 날짜와 합격 또는 실패를 적는다. 실패는 code-review 결함 표에 새 결함 번호로 남긴다. (v0.26) 2026-11-09에 에뮬레이터(Android 16)로 1~19를 실행한 결과를 「결과」 칸에 적었다. 운영 443은 읽기와 화면 확인만, 기록 저장과 응모는 8443 develop 빌드에서 했다. 판정과 결함은 [sqa/이벤트-공지-성](../sqa/이벤트-공지-성.md)에 있다. (v0.26 보완) 18은 11-09 오후에 발송 대신 8443 DB에 공지 행(id 16)을 직접 넣고 develop 앱으로 봤다(운영자 토큰 미사용, 행 시각은 [임시:DF-성-1]).

| 순서 | 확인 항목 | 방법 | 합격 기준 | TC | 결과 |
|------|-----------|------|-----------|----|------|
| 1 | 서버 헤더 | `curl -I https://<도메인>/events/coach-challenge/`, beta-1km | 200, CSP, nosniff, Referrer-Policy, max-age=60 | TC-2731 | 11-09 합격(443, 8443 curl) |
| 2 | 알림함 이벤트 항목 | 응모 없는 계정으로 홈 → 알림함 | 홈 알림함 단추에 점, 알림함 맨 위 선물 아이콘 「이벤트」 「진행 중인 이벤트 2개」 오른쪽 화살표, 점 있음, TalkBack이 새 이벤트 상태를 읽음 | TC-2674, TC-2679, TC-2694 | 11-09 합격(에뮬레이터 0.11.4, 443. TalkBack 음성은 듣지 못했고 낭독 상태는 접근성 노드와 코드로 확인) |
| 3 | 이벤트 목록 | 항목 누름, 밝게와 어둡게 | 진행 중 탭 배너 2개 옆 넘김, 코치 카드 「새 이벤트」 「11월 16일까지」 D-day, 응모 진행 표시 없음(응모 전, v0.26 변경), 베타 카드도 진행 표시 없음, 지난 탭 「지난 이벤트가 없어요」. 목록을 본 뒤 알림함과 홈 점이 꺼짐 | TC-2666~TC-2673, TC-2691~TC-2693 | 11-09 불합격: 카드에 「0 / 2」, 「0 / 1」이 없음(DF-069). v0.26에서 FR-306을 구현에 맞춰 바꿔 합격으로 본다. 배너, 칩, 기간, D-day, 지난 탭, 점 꺼짐은 합격 |
| 4 | 상세 웹뷰 Light | 밝은 테마에서 두 카드를 차례로 엶 | 상단 바 뒤로와 제목, 불러오는 중 뒤 페이지가 밝게 그려짐, 글자 잘림 없음 | TC-2681, TC-2732 | 11-09 합격(에뮬레이터 0.11.4, 443) |
| 5 | 상세 웹뷰 Dark | 앱 테마를 어둡게 바꾸고 두 카드 | 페이지가 어둡게 그려지고 글자 대비가 읽힘 | TC-2681, TC-2732 | 11-09 합격(에뮬레이터 0.11.4, 443) |
| 6 | 내 응모 | 두 계정으로 두 상세 | 응모 없는 계정 0 / 2, 0 / 1. 응모 있는 계정은 달성 항목과 날짜. 표시이름이 코치 실명 | TC-2717, TC-2718, TC-2738 | 11-09 합격(443 응모 없는 계정 0 / 2, 0 / 1. 8443 응모 있는 계정 베타 「응모 완료」 「11월 9일」 「1.15km」, 코치 1 / 2 「김선교 코치님 11월 9일 이김」. 표시이름 코치 실명. 거리 표시 차이는 DF-072) |
| 7 | 셀럽런 이동 | 코치 상세 「셀럽런 도전하기」 | 기록런 탭이 셀럽런 분절로 열리고 주세빈, 김선교 코치 기록이 전용 캐릭터(JOO, KIM)로 보임 | TC-2684, TC-2717 | 11-09 합격(에뮬레이터 0.11.4, 443) |
| 8 | 러닝 이동 | 베타 상세 「달리러 가기」, 위치 권한 있음과 없음 | 권한 있으면 자유런 시작 준비, 없으면 권한 요청 뒤 답과 상관없이 자유런 화면 | TC-2718, TC-2720 | 11-09 합격(권한 없음은 요청 → 거절 → 자유런 화면과 안내, 권한 있음은 자유런 측정이 곧바로 시작해 홈 자유런 카드와 같음. 443이라 취소) |
| 9 | CSP 뒤 페이지 정상 | PC 브라우저 개발자 도구로 두 페이지, 앱에서도 다시 봄 | 그림, 버튼 동작, 내 응모 fetch 정상, 콘솔 CSP 위반 0, 외부 요청 0 | TC-2729, TC-2732 | 11-09 합격(PC Chrome CSP 위반 0, 외부 요청 0, 앱 logcat CSP 0. 앱 30초 네트워크 기록은 미실행) |
| 10 | 코스 지도 | 베타 상세 「산책 코스 추천」의 코스 A, B, C(v0.26 변경) | 세 지도가 모두 그려지고 출발점(멀티캠퍼스 역삼), 반환점 표시가 맞는다. 지도 아래 출처 표기는 없다(v0.26 변경, 사용자 결정, R-082) | TC-2732, TC-2735 | 11-09 불합격: 코스 A, B, C 세 지도와 출발점, 반환점은 맞으나 OpenStreetMap 표기가 없음(DF-070). 코스 이름은 6d99faf9에서 바뀜(DF-071). v0.26 기대 결과 변경으로 합격으로 본다 |
| 11 | 뒤로, 닫기, 공유 | 상세에서 뒤로, 공유 단추 | 뒤로는 목록으로, 공유는 기기 공유 창 | TC-2723, TC-2725, TC-2726 | 11-09 합격(상단 바 뒤로, 시스템 뒤로, 페이지 닫기 → 목록, 공유 창) |
| 12 | 목록 복귀 갱신 | 셀럽런 승리 또는 1km 저장 뒤 뒤로 목록 | 칩이 「참여 중」, 응모 수가 바뀜 | TC-2672, TC-2717 | 11-09 합격(8443 develop 빌드, 1km 저장 뒤 베타 「참여 중」 「1 / 1」, 셀럽런 승리 뒤 코치 「참여 중」 「1 / 2」. 저장 뒤 앱이 홈으로 가서 「뒤로」 복귀 길은 없음) |
| 13 | 오류와 다시 시도 | 비행기 모드로 목록과 상세 | 목록 오류 안내와 다시 시도, 상세 오류 안내와 다시 시도 | TC-2668, TC-2722 | 11-09 합격(에뮬레이터 비행기 모드, 목록과 상세 모두 다시 시도로 회복) |
| 14 | 앱 밖 브라우저 | 폰 브라우저로 상세 주소 | 앱 단추 비활성, 내 응모 숨김 | TC-2724 | 11-09 합격(에뮬레이터 Chrome과 PC Chrome) |
| 15 | 운영 확인 | 운영자 토큰으로 응모자 명단 API, 기간 종료 직후 명단 보관 | 코치별 명단, 탈퇴 제외, 커서 쪽 넘김 | TC-2706~TC-2710, TC-2738 | 미실행(11-09, 운영자 토큰 없음, 기간 종료 전) |
| 16 | 공지 발송 (v0.25) | 8443에서 운영자 토큰으로 시험 계정 둘(0.11.4 기기, 0.11.3 기기)에 제목, 본문(짧은 주소 글 포함), 구글 폼 링크 공지. 점 조각 주소 3개도 보냄 | 정상 공지 200(요청 2, 저장 2, 건너뜀 없음), 감사 「NOTICE:제목」 1건. 점 조각 주소는 409, 알림과 감사 0건 | TC-2770, TC-2774 | 미실행(11-09, 운영자 발송 필요) |
| 17 | 공지 기기 알림 | 0.11.4 기기를 앱 앞, 뒤, 종료 상태로 두고 16 반복. 마케팅 수신 동의를 끈 계정 포함 | 세 상태 모두 기기 알림 제목이 공지 제목, 본문이 공지 본문. 누르면 앱이 열림. 마케팅 미동의여도 받음, 설정 화면에 공지 스위치 없음 | TC-2772, TC-2773 | 미실행(11-09, 운영자 발송 필요) |
| 18 | 알림함 공지와 링크 열기 | 0.11.4 알림함에서 공지 줄을 보고 누름, 링크 없는 공지도 누름 | 줄 위 글이 공지 제목(라벨 없음), 본문 보임. 누르면 읽음으로 바뀌고 바깥 브라우저(기본 브라우저)로 구글 폼이 열림. 앱 안 웹 화면이 아님. 링크 없는 공지는 알림함에 머묾 | TC-2757~TC-2759, TC-2770 | (v0.26 보완) 11-09 합격(링크 있는 공지만, 8443 DB 직접 입력). 제목(라벨 없음, 굵게), 본문, 「방금」, 바깥 열기 아이콘. 누르면 Chrome이 forms.gle/runkkTest를 엶(앱 안 웹 화면 아님), 서버 읽음, 뒤로 알림함. 링크 없는 공지는 미실행 |
| 19 | 옛 앱 공지 | 0.11.3 기기 알림함 | 제목 「알림」과 본문, 눌러도 링크가 열리지 않고 알림함에 머묾, 앱 종료 없음. 본문의 짧은 주소 글이 보임 | TC-2771 | 미실행(11-09, 운영자 발송 필요, 0.11.3 기기 필요) |

다른 출처 시험 페이지(TC-2727, TC-2730)와 60초 반영(TC-2733, TC-2734)은 시험 빌드와 8443에서 따로 본다. 운영 서버에 시험 페이지를 올리지 않는다.

## 9. 보강 필요 (자동 시험이 없는 곳, 코드는 고치지 않음)

(v0.25) 2026-11-08 공지 알림 리뷰와 함께 우선 1~6을 보강했다(처리 열). 보강 시험은 처음 실행에서 PL7-B2(동시 판정의 지급 이력 고유 위반이 실패 로그로 남음)를 드러냈고 같은 브랜치에서 고쳤다(code-review/공지알림-1차-성 2절). 남은 보강 필요는 우선 5의 TC-2636(이미 두 시험 조합으로 합격), 6의 TC-2648, 7~12다.

| 우선 | TC | 빠진 것 | 넣을 곳(제안) | 관련 결함 | 처리 |
|------|----|---------|---------------|-----------|------|
| 1 | TC-2642 | JPA sumOn의 EVENT 제외 | infra/reward JpaRewardRepositoriesIntegrationTest | PL6-B14 | 해소: EventPointThreadIntegrationTest.sumOn_excludesEventPoints(PostgreSQL) |
| 2 | TC-2651 | 같은 회원 셀럽런 판정 두 건 동시 커밋의 1회 지급 | web/event/EventControllerTest 또는 새 통합 시험(두 스레드) | 설계 R2 | 해소: EventPointThreadIntegrationTest.concurrentJudgements_grantOnce(네 스레드). PL7-B2 발견, 고침 |
| 3 | TC-2629, TC-2630, TC-2628 | battle 질의(findFirstCelebrityWins, findCelebrityWinners)의 진 판, 포기, 보류, 다른 종류 제외와 판정일시 경계, 중복 승리 최솟값 | infra/battle 통합 시험(battle 경계 계약, 박재영 승인 대기) | | 해소: EventPointThreadIntegrationTest.celebrityWinQueries_filterAndBoundaries(PostgreSQL) |
| 4 | TC-2612, TC-2613, TC-2614, TC-2616 | EventConfig.parse 독립 경로 B7, B9, B11, B12와 경곗값(셀럽 ID 0, 승리포인트 −1, 기준 거리 0) | event/EventServiceTest.admin_rejectsInvalidInput 설정 목록 | PL6-B3 계열 | 해소: EventServiceTest.admin_coachConfigBoundaries, admin_runConfigBoundaries. parse 리팩터링(V(G) 12 → 6) |
| 5 | TC-2633, TC-2634, TC-2636 | 응모 계산의 1,000m 정각, 시작과 종료 정각 | event/EventServiceTest | | 해소(TC-2633, TC-2634): EventServiceTest.runEntry_countsExactlyMinimumDistance, runEntry_includesBothEndsOfPeriod |
| 6 | TC-2646, TC-2648, TC-2650 | 포인트 지급의 기간 양 끝, 다시 공개 뒤 채우기, 승리포인트 0 | event/CoachWinPointListenerTest | | 일부 해소(TC-2646, TC-2650): CoachWinPointListenerTest.judgedAtPeriodEndsGrants, zeroPointEventGrantsNothing. TC-2648 남음 |
| 7 | TC-2703, TC-2704 | 관리 API 그림 주소 둘의 상대 경로 규칙, 필드 길이 경계 | web/event/EventControllerTest | PL6-B1 | 남음 |
| 8 | TC-2676, TC-2678 | 알림함 항목의 실패 뒤 유지와 앞 읽기 취소, 예정 → 진행 중 점 | EventTest | PL6-A3 | 남음 |
| 9 | TC-2689, TC-2690 | 공유 2,000자 경계, 로그아웃 토큰 | EventTest 브리지 시험 | | 남음 |
| 10 | TC-2605 | 종료 1초 미만 경계 | event/EventServiceTest.status_includesBothEnds | PL6-B10 | 남음 |
| 11 | TC-2618, TC-2621, TC-2622, TC-2623, TC-2637, TC-2638, TC-2658, TC-2659, TC-2671, TC-2673 | 나머지 경계와 무효 동치(기간 같음, 남의 주소이름, 없는 ID 공개 전환, 관리 목록 정렬, 모드와 집계 제외, 1.5km 표시, 쪽 기본값, 빈 명단, D-1, 빈 탭) | 각 클래스 시험 | | 남음 |
| 12 | grantOne 세 번째 경로 | 적립 거부와 내역 없음 | event/CoachWinPointListenerTest(가짜 적립 장애) | | 남음 |

## 10. 실행 결과 (2026-11-08)

| 실행 | 범위 | 결과 |
|------|------|------|
| 서버 | `./gradlew test`(JDK 21 도구 체인), 클래스 EventServiceTest 8, CoachWinPointListenerTest 6, EventEntrantServiceTest 3, EventControllerTest 5, EventMigrationIntegrationTest 1, JpaRunningRepositoriesIntegrationTest 5, RunningValuesTest 10, ArchitectureTest 16 | 54건 통과, 실패 0, 오류 0, 건너뜀 0(Docker 있음), 2분 56초. 코드 d06641bb |
| 서버 추가 | PlacementMigrationIntegrationTest 6, AppContractSnapshotTest 1 | 7건 통과, 실패 0, 건너뜀 0, 1분 17초 |
| 앱 | `-Proborazzi.test.verify=true :feature:notification:testDebugUnitTest`(EventTest 12, InboxTest 9, EventScreenshotTest 4, 기준 이미지 비교), `:data:testLocalDebugUnitTest`(EventRepositoryImplTest 5, DataStoreSettingsRepositoryTest 17), `:app:testLocalDebugUnitTest`(ArchitectureTest 21), JDK 17 | 68건 통과, 실패 0, 오류 0, 건너뜀 0, 57초. 코드 d06641bb(앱 코드는 2f99b573 뒤 바뀌지 않음) |
| 작성자 보고(코드 리뷰 5절) | 서버 `./gradlew build` 1,959건(d06641bb), 앱 `ktlintCheck lintLocalDebug testLocalDebugUnitTest assembleLocalDebug` 957건(2f99b573) | 실패 0 |
| (v0.25) 보강 2026-11-08 | 서버 EventPointThreadIntegrationTest 3(PostgreSQL), EventServiceTest 12(+4), CoachWinPointListenerTest 8(+2), NoticeServiceTest 6. 코드 2e05c391 | 29건 통과, 실패 0, 건너뜀 0. 첫 실행에서 TC-2651이 실패 로그 3건을 드러냄(PL7-B2, 62b7f7e8에서 고침), TC-2630 시험의 나노초 경계 단언 1건은 시험 쪽 오류(TIMESTAMP(6) 정밀도)라 시험을 고침 |
| (v0.25) 전체 2026-11-08 | 서버 `./gradlew build` 시험 1,979건 통과(실패 0, 건너뜀 0, Docker PostgreSQL 포함, 11-09 00:03). 11-08 23:30 첫 실행에서는 크루 주간 레이스 시험 2건(CrewThreadIntegrationTest t9, CrewControllerTest postsAndCrewRun)이 일요일 자정 직전에 시작 시각(지금 + 1분, + 30분)이 다음 주로 넘어가 실패했고 자정 뒤 다시 돌려 통과했다(이번 변경과 무관한 시각 의존 시험, 별도 보고). 앱 ktlintCheck, lintLocalDebug, testLocalDebugUnitTest(2,078건), assembleLocalDebug 통과(앱 코드 변경 없음) | 실패 0 |

| 구분 | 수 |
|------|----|
| 이 조각 케이스 | 138 |
| 자동 시험 대응 | 93(전부 대응 84, 일부 대응 9: TC-2614, TC-2618, TC-2621, TC-2623, TC-2633, TC-2634, TC-2638, TC-2658, TC-2671). (v0.25) 보강 뒤 103(전부 대응 97, 일부 대응 6: TC-2618, TC-2621, TC-2623, TC-2638, TC-2658, TC-2671) |
| 자동 시험 없음(보강 필요) | 22(TC-2605, TC-2612, TC-2613, TC-2616, TC-2622, TC-2628, TC-2629, TC-2630, TC-2637, TC-2642, TC-2646, TC-2648, TC-2650, TC-2651, TC-2659, TC-2673, TC-2676, TC-2678, TC-2689, TC-2690, TC-2703, TC-2704). (v0.25) 보강 뒤 12(TC-2605, TC-2622, TC-2637, TC-2648, TC-2659, TC-2673, TC-2676, TC-2678, TC-2689, TC-2690, TC-2703, TC-2704) |
| 수동 | 23(실기기, 배포 서버, 운영 확인 21: TC-2679, TC-2717~TC-2734, TC-2737, TC-2738. 정적 확인 2: TC-2735, TC-2736) |
| 합격(자동) | 93 중 93(일부 대응 9는 대응한 값만 합격). 실패 0. (v0.25) 보강 뒤 103 중 103 |
| 미실행 | 수동 21. 보강 필요 22는 시험이 없어 실행하지 않음. (v0.25) 보강 뒤 보강 필요 12 |

계획 완성도: 완성. 새 FR 14개, NFR 3개, 제약 1개 모두 케이스가 있고(11절, 미커버 0), 범위 조건(기간, 1,000m, 15:00/km, 승리포인트, 기준 거리, 쪽 크기, 공유 글 길이, 필드 길이, D-day)마다 경곗값 케이스가 있으며, 우선 대상 함수의 독립 경로마다 케이스가 있다(도달 불가 2: EventConfig.parse B3, grant dues == null). 시퀀스 SQ-56, SQ-57는 T-63, T-64가 덮는다. 다만 자동화되지 않은 케이스가 22건이다(9절). 실행 합격률은 자동 시험 대응분만 센다(계획 완성도와 섞지 않는다).

## 11. 추적 (FR, NFR → TC)

| 요구 | 설계 | TC | 자동 시험 |
|------|------|----|-----------|
| FR-306 이벤트 목록 조회 | D-603, D-606, D-616 | TC-2601~TC-2605, TC-2624, TC-2660, TC-2661, TC-2666~TC-2673, TC-2691~TC-2693, TC-2695, TC-2696, TC-2698, TC-2717 | 있음(일부 보강 필요) |
| FR-307 알림함 이벤트 항목 | D-617 | TC-2674~TC-2676, TC-2694, TC-2717 | 있음(실패 뒤 유지 보강 필요) |
| FR-308 새 이벤트 표시 | D-617 | TC-2663, TC-2665, TC-2666, TC-2668, TC-2674, TC-2676~TC-2679, TC-2694 | 있음(낭독기는 수동) |
| FR-309 이벤트 상세 페이지 표시 | D-603, D-604, D-615 | TC-2669, TC-2680, TC-2681, TC-2695, TC-2722~TC-2724, TC-2736 | 있음(로딩, 오류, 뒤로, 브라우저는 수동) |
| FR-310 내 이벤트 참여 조회 | D-606, D-615 | TC-2625, TC-2626, TC-2662, TC-2680, TC-2697, TC-2699, TC-2722, TC-2724 | 있음 |
| FR-311 셀럽 승리 응모 판정 | D-606, D-607 | TC-2605, TC-2626~TC-2630, TC-2699, TC-2717, TC-2738 | 있음(battle 질의 조건과 경계 보강 필요) |
| FR-312 기록 거리 응모 판정 | D-606, D-608 | TC-2605, TC-2615, TC-2631~TC-2638, TC-2711, TC-2718, TC-2719 | 있음(1,000m 정각 보강 필요) |
| FR-313 셀럽 승리 이벤트 포인트 적립 | D-609, D-610 | TC-2639~TC-2651, TC-2700, TC-2712, TC-2717 | 있음(동시 판정, sumOn 보강 필요) |
| FR-314 이벤트 관리 목록 조회 | D-611 | TC-2623, TC-2701 | 있음(정렬 보강 필요) |
| FR-315 이벤트 등록 | D-605, D-611, D-612, D-616 | TC-2606~TC-2619, TC-2696, TC-2701~TC-2705 | 있음(길이 경계 보강 필요) |
| FR-316 이벤트 수정 | D-611, D-612 | TC-2619~TC-2621, TC-2701, TC-2721 | 있음 |
| FR-317 이벤트 공개 전환 | D-611, D-612 | TC-2619, TC-2622, TC-2625, TC-2647, TC-2648, TC-2697, TC-2701 | 있음 |
| FR-318 상세 페이지의 앱 기능 사용 | D-614, D-615 | TC-2684, TC-2689, TC-2690, TC-2695, TC-2717, TC-2718, TC-2720, TC-2725, TC-2726 | 있음(공유 창, 권한은 수동) |
| FR-319 이벤트 응모자 명단 조회 | D-620 | TC-2652~TC-2659, TC-2706~TC-2711, TC-2738 | 있음 |
| NFR-055 같은 출처에서만 앱 기능 | D-614 | TC-2682~TC-2687, TC-2727, TC-2728 | 판정 함수 있음, 측정은 수동 |
| NFR-056 다른 출처 자원과 이동 0건 | D-614, D-616 | TC-2661, TC-2664, TC-2688, TC-2729~TC-2732 | 판정 함수 있음, 측정은 수동 |
| NFR-057 앱 업데이트 없이, 60초 반영 | D-603, D-604 | TC-2731, TC-2733, TC-2734, TC-2737 | 없음(수동만) |
| C-025 nginx 정적 파일, 외부 자원 없음, OSM 출처 표기 없음(v0.26) | D-604 | TC-2732, TC-2735 | 없음(정적 확인, 수동) |
| FR-159(바뀜, CR-176) | D-610 | TC-2641, TC-2642 | 단위 있음, JPA 보강 필요 |
| FR-176(바뀜, 이벤트 감사) | D-612 | TC-2619, TC-2721 | 있음 |
| FR-178(바뀜, 알림함 맨 위 항목) | D-617 | TC-2674, TC-2694, TC-2695 | 있음 |

## 12. 기존 케이스 영향

| TC | 영향 | 처리 |
|----|------|------|
| TC-6535(FR-178, Routes 목록) | Routes 상수에 EVENTS, RECORD_RUN_CELEB, 키에 EventDetail이 더해졌다 | 기대 결과에 0.11.4 목록을 더한다(v0.25 병합에서 02-02d TC-6535 행에 반영함). 코드 InboxTest의 V0114 목록이 이미 반영. TC-2695가 잇는다 |
| TC-5683(앱 구조, 폰 159) | 폰 허용 쌍이 160이 됐다(api.notification → domain.settings) | 기대 160으로(v0.25 병합에서 10-24 TC-5683 행에 반영함), TC-2715 |
| TC-9856(서버 의존 구조) | 서버 패키지가 main 기준 69, develop 역병합 뒤 72가 된다 | 역병합 MR에서 코드의 기대 수를 고친다(설계 R4). 문서는 v0.25 병합에서 10-07 TC-9856 행에 77로 적음, TC-2714 |
| TC-6151~TC-6155(FR-159 RewardWallet.accrue), TC-2019(같은 날 적립 합) | EVENT 사유는 상한을 받지 않고 그날 합계에서 빠진다. 기존 사유의 기대는 그대로다 | 바꾸지 않음. EVENT 분기는 TC-2641, TC-2642 |
| TC-6619(Flyway) | V35_2가 더해졌고 develop에서 순서 밖으로 적용된다 | TC-2712, TC-2713 |
| TC-6535(알림 이동 표) (v0.25) | 서버 Screen이 12개(EXTERNAL_LINK)가 됐고 EXTERNAL_LINK는 이동 경로가 아니라 링크로 연다 | 기대 결과에 EXTERNAL_LINK를 더한다(v0.25 병합에서 02-02d TC-6535 행에 반영함), TC-2761 |
| TC-9856, TC-2714(서버 구조) (v0.25) | 공지로 패키지 70(web.admin.notification), 시험 메서드 이름이 designPackageList_has70Names…로 바뀌었다. develop 역병합 뒤 73, MR !94(runbody 4)가 먼저면 77 | 역병합 MR에서 기대 수를 고친다, TC-2769 |

## 13. 운영자 공지 알림 (v0.25, 2026-11-09)

범위: 서버 notification(NoticeService, NoticeInput, NoticeResult, NotificationService.deliver, NotificationType.NOTICE, Screen.EXTERNAL_LINK, Notification.title), web.admin.notification(NoticeAdminController), infra.integration.push(FcmPushGateway 제목), V35_3, 앱 ui.notification(inboxLink, InboxViewModel.onItemTapped, InboxRoute), data(NotificationRemotes.toDomain), ui.me(NotificationTypes.alwaysOn). 기준 문서: 단위는 design-oo 05-27 2절, 4절과 D-621~D-628, 통합은 SQ-58과 5절 API, 검증은 srs 3.7.4와 7.13.25 S-NT-1, 시스템은 05-27 8절 판정과 사용자 결정 11~15. 우선 함수(code-review/공지알림-1차-성 8절): isFormLink(V(G) 약 12), send(8), inboxLink(3). 통합 스레드 T-67은 05-27 7절 후보에 v0.25 병합에서 정식 T 번호를 매긴 것이다.

### 13.1 단위 서버 (NoticeService, NotificationService, FcmPushGateway)

서버 시험 파일은 `backend/src/test/java/com/a207/runkk/` 아래다. FORM은 `https://forms.gle/AbCdEf123`.

| TC | 요구 | 입력 | 실행 조건 | 기대 결과 | 기법 | 자동 시험 | 결과 |
|----|------|------|-----------|-----------|------|-----------|------|
| TC-2739 | FR-320, D-624 | FORM, `https://FORMS.GLE/AbC`, `https://docs.google.com/forms/d/e/1FAIpQLS/viewform`, 같은 주소 + `?usp=sf_link`, `…/forms/d/1abc/edit/` | isFormLink, send | 다섯 모두 구글 폼으로 받음(호스트 대소문자 무관, 질의와 끝 「/」 허용), FORM 공지는 저장됨 | 동치 분할(유효 5) | notification/NoticeServiceTest.java:send_rejectsNonFormLinks 끝 단언 4, send_storesAndPushesWithTitleAndLink | 합격(11-08) |
| TC-2740 | FR-320, D-624 | `http://forms.gle/…`, `intent://forms.gle/x`, `https://evil.example/forms/x`, `https://forms.gle.evil.example/x`, `https://docs.google.com/document/d/1` | send | 모두 409 VALUE_INVALID, 알림 0건, 감사 0건(검사가 발송 앞) | 동치 분할(scheme 2, 호스트 3) | 같음(send_rejectsNonFormLinks) | 합격(11-08) |
| TC-2741 | D-624 | `https://user@forms.gle/x`, `https://forms.gle:8443/x`, `https://forms.gle/a b` | send | 409, 알림과 감사 0건 | 오류 예측(사용자 정보, 포트, 공백) | 같음 | 합격(11-08) |
| TC-2742 | D-624, PL7-B1 | `https://docs.google.com/forms/../url?q=https://evil.example`, `…/forms/%2e%2e/document/d/1`, `https://forms.gle/../x` | send | 409, 알림과 감사 0건(점 조각과 % 인코딩으로 구글 폼 밖 경로에 닿지 못함) | 오류 예측(보안 우회) | 같음(62b7f7e8에서 더함) | 합격(11-08) |
| TC-2743 | D-624 | `https://forms.gle/`(조각 0), `https://forms.gle/a/b`(조각 2), `https://docs.google.com/forms/`(조각 0), `…/forms//x`(빈 조각) | send | 409 | 경곗값(조각 수 min−1, max+1, 빈 조각) | 같음 | 합격(11-08) |
| TC-2744 | FR-320, D-626 | 링크 299, 300, 301자(forms.gle 조각 하나) | isFormLink | 299와 300자는 받고 301자는 409 | 경곗값(max−1, max, max+1) | 318자 거부만: send_rejectsNonFormLinks. 300 정각, 301 없음 | 일부 합격, 보강 필요 |
| TC-2745 | FR-320 | 관리 요청 linkUrl 「   」, 「 FORM 」, 없음 | NoticeRequest.toInput → send | 공백만과 없음은 링크 없음(EXTERNAL_LINK, 대상 ID null)으로 보냄, 앞뒤 공백은 지우고 FORM으로 받음 | 동치 분할 | 없음 | 보강 필요 |
| TC-2746 | FR-320 | 회원 ID 0개, 1개, 100개, 101개, 원소 0과 −1, 겹침 포함 101개(서로 다른 것 100) | 관리 API와 send | 0개와 101개는 400(서비스는 IllegalArgumentException), 1개와 100개는 보냄, 0과 −1은 400. 겹침 포함 101개는 형식 검사가 원소 수로 세어 400 | 경곗값(min−1, min, max, max+1), 오류 예측 | 0, 101: NoticeServiceTest.send_rejectsRecipientCount, web/admin/NoticeAdminControllerTest.java:send_rejectsInvalid. 1개는 다른 시험. 100개, 0과 −1, 겹침 101개 없음 | 일부 합격, 보강 필요 |
| TC-2747 | FR-320, D-622 | [a, b, a] | send | 결과 (요청 2, 저장 2, 건너뜀 없음), a 알림 1건(겹침은 한 번) | 동치 분할 | send_storesAndPushesWithTitleAndLink | 합격(11-08) |
| TC-2748 | FR-320, S-NT-1a | [a, 탈퇴 gone, 없는 9999] | send | (3, 1, [gone, 9999]), 저장 1건, 나머지는 보냄 | 동치 분할(무효 2) | NoticeServiceTest.send_skipsWithdrawnAndUnknown | 합격(11-08) |
| TC-2749 | FR-320 | 제목 빈 글, 공백만, 1자, 60자, 61자 | 관리 API | 빈 글과 공백만, 61자는 400, 1자와 60자는 보내고 알림함과 기기 알림 제목이 그대로 | 경곗값(min−1, min, max, max+1) | 공백만 400: send_rejectsInvalid. 나머지 없음 | 일부 합격, 보강 필요 |
| TC-2750 | FR-320 | 본문 빈 글, 1자, 500자, 501자 | 관리 API | 빈 글과 501자는 400, 1자와 500자는 보냄 | 경곗값(min−1, min, max, max+1) | 없음 | 보강 필요 |
| TC-2751 | FR-320, FR-025, D-623 | 마케팅 수신 미동의(가입 필수 동의만) 회원 a, 기기 토큰 있음 | send | 알림함 저장, 공지 제목으로 푸시 1건(마케팅 동의를 보지 않음, 사용자 결정 11) | 동치 분할 | send_storesAndPushesWithTitleAndLink(MemberFixtures는 필수 동의만). 동의 철회 회원은 실기기 TC-2773 | 합격(11-08) |
| TC-2752 | D-623 | NotificationType 22개, 회원 설정 변경 ALERT.NOTICE 끄기 | MemberSettings, NotificationType | NOTICE만 회원이 끄는 종류 목록 밖, 화면 EXTERNAL_LINK, 닫힌 기능 없음. ALERT.NOTICE 변경은 SETTING_INVALID라 끌 수 없음 | 동치 분할 | 목록과 분류: notification/NotificationServiceTest.java:notificationType_namesMatchMemberSettingsList_andHaveScreens. SETTING_INVALID 없음 | 일부 합격, 보강 필요 |
| TC-2753 | FR-176, D-625, PL7-B5 | 회원 a에게 FORM 공지 | send 뒤 감사 조회 | 1건, 행위 SETTING_CHANGE, 대상 「NOTICE:코치님 이벤트 당첨」, 행위자 운영자 ID, 변경후값에 memberIds, 링크, stored=1 | 동치 분할 | NoticeServiceTest.send_recordsAudit | 합격(11-08) |
| TC-2754 | D-621 | 링크 없는 공지, 같은 회원에게 PLAN_MISSED 알림 | send, notify | 공지 대상은 (EXTERNAL_LINK, null), PLAN_MISSED 항목 제목은 null(다른 종류는 제목 열을 쓰지 않음) | 동치 분할 | NoticeServiceTest.send_withoutLink | 합격(11-08) |
| TC-2755 | FR-175, S-NT-1d | a(토큰 있음), b(토큰 없음) | send | 둘 다 저장(stored 2), 푸시는 a 1건. b는 알림함에만 있음 | 동치 분할 | send_storesAndPushesWithTitleAndLink | 합격(11-08) |
| TC-2756 | FR-321, D-628 | 제목 「코치님 이벤트 당첨」 공지, 제목 null 공지 | FcmPushGateway.send(가짜 FCM) | FCM 메시지 notification.title은 공지 제목, null이면 「런크크」, data.type NOTICE | 동치 분할 | infra/integration/push/FcmPushGatewayTest.java:send_withTitle_usesNoticeTitle | 합격(11-08) |

### 13.2 단위 앱 (ui.notification, data, ui.me)

앱 시험 파일: InboxTest(`android/feature/notification/src/test/kotlin/…/ui/notification/`), AvatarAndRunimalAndNotificationTest(`android/data/src/test/kotlin/…/api/avatar/`), ServerContractTest(`android/feature/me/src/test/kotlin/…/ui/me/`).

| TC | 요구 | 입력 | 실행 조건 | 기대 결과 | 기법 | 자동 시험 | 결과 |
|----|------|------|-----------|-----------|------|-----------|------|
| TC-2757 | FR-321, D-624 | (EXTERNAL_LINK, FORM), (EXTERNAL_LINK, null), http 주소, intent 주소, 공백 든 주소, (CREW_HOME, FORM) | inboxLink | 첫째만 FORM, 나머지는 null(알림함에 머묾). 앱은 호스트를 보지 않는다(서버가 유일한 방어선, PL7-A1) | 동치 분할(유효 1, 무효 5) | InboxTest.kt:`공지 링크는 EXTERNAL_LINK의 https 주소만…` | 합격(11-08). (v0.26 보완) 첫째는 에뮬레이터 합격(11-09, 8절 18) |
| TC-2758 | FR-321, FR-178 | 링크 있는 공지 11, 링크 없는 공지 12를 차례로 누름 | InboxViewModel.onItemTapped | 11은 OpenLink(FORM), 12는 이벤트 없음. 둘 다 읽음 처리 요청(open 11, 12) | 동치 분할 | InboxTest.kt:`공지를 누르면 읽음으로 바꾸고 링크를 연다…` | 합격(11-08). (v0.26 보완) 11은 에뮬레이터 합격(11-09, 8절 18), 12는 미실행 |
| TC-2759 | FR-321, D-628 | 공지 한 건(제목, 본문) | InboxScreen | 줄 위 글이 종류 라벨이 아니라 「코치님 이벤트 당첨」, 본문 「구글 폼에 연락처를 남겨 주세요」 | 비교 검사 | InboxTest.kt:`공지 줄은 라벨 대신 서버가 준…` | 합격(11-08). (v0.26 보완) 에뮬레이터 합격(11-09, 8절 18) |
| TC-2760 | FR-321, D-628 | `GET /notifications` 응답: 제목 있는 공지(링크), 제목 없는 공지(대상 ID 없음) | NotificationRepositoryImpl.inbox | 제목 「코치님 이벤트 당첨」, 「안내」. 첫째 대상 ID FORM, 둘째 화면 EXTERNAL_LINK, 대상 ID null | 동치 분할 | AvatarAndRunimalAndNotificationTest.kt:`운영자 공지는 서버 제목과 링크 대상을…` | 합격(11-08). (v0.26 보완) 8443 응답으로 에뮬레이터 합격(11-09) |
| TC-2761 | FR-178, D-621 | 계약 Screen 목록 | inboxDestination, inboxLink | 서버 Screen 12 = 알림 이동 화면 11 + EXTERNAL_LINK. 11개는 이동 경로가 있고 EXTERNAL_LINK는 이동 경로 없음(링크로 엶) | 비교 검사 | InboxTest.kt:`알림 이동 표는 서버 Screen 11개를 모두 다루고…` | 합격(11-08) |
| TC-2762 | D-623, D-628 | 계약 NotificationType | NotificationTypes | allTypes 21개 + alwaysOn [NOTICE] = 서버 22개. 알림 설정 화면에 공지 스위치 없음 | 비교 검사 | ui/me ServerContractTest.kt:`알림 설정 묶음은 서버 NotificationType 21개를…` | 합격(11-08) |
| TC-2763 | FR-321 | OpenLink를 받았는데 열 수 있는 앱이 없음(openUri 예외) | InboxRoute | 앱이 끝나지 않고 알림함에 머묾(runCatching) | 오류 예측 | 없음 | 보강 필요 |

### 13.3 통합 (T-67, 마이그레이션, 구조, 계약)

| TC | 요구 | 입력 | 실행 조건 | 기대 결과 | 기법 | 자동 시험 | 결과 |
|----|------|------|-----------|-----------|------|-----------|------|
| TC-2764 | T-67, FR-320, FR-321, SQ-58 | 운영자가 [가입 회원, 99999999]에 FORM 공지 | `POST /admin/notifications/notices` → 회원 `GET /notifications` → `POST /notifications/{id}/open` | 200 (2, 1, [99999999]). 알림함 첫 항목 NOTICE, 제목, 본문, 대상 (EXTERNAL_LINK, FORM). 열기 응답 화면 EXTERNAL_LINK, 대상 FORM | 스레드 기반 | web/admin/NoticeAdminControllerTest.java:send_showsInInbox | 합격(11-08) |
| TC-2765 | FR-173, S-NT-1f | 토큰 없음, 회원 JWT | 같은 POST | 둘 다 403 | 동치 분할(무효 2) | NoticeAdminControllerTest.requiresOperator | 합격(11-08) |
| TC-2766 | S-NT-1b, S-NT-1c | 빈 명단, 101명, 공백 제목, http 링크 | 같은 POST | 앞 셋 400, http 링크 409 VALUE_INVALID, 받을 회원 알림함 0건 | 동치 분할(무효 4) | NoticeAdminControllerTest.send_rejectsInvalid | 합격(11-08) |
| TC-2767 | D-626 | V35_3까지 적용한 빈 PostgreSQL 17 | NOTICE 행(284자 대상 ID, 제목), PLAN_MISSED 행, 모르는 종류 ANNOUNCE 넣기 | 앞 둘 저장(대상 ID 그대로, 기존 종류 제목 null), ANNOUNCE 거부 | 동치 분할 | infra/NoticeMigrationIntegrationTest.java:noticeTypeTitleAndLongTarget | 합격(11-08, Docker) |
| TC-2768 | D-626 | develop 순서(36, 37 뒤 35.1~35.3) | out-of-order | 35.3이 순서 밖으로 한 번 적용(적용 목록 끝 36, 37, 35.1, 35.2, 35.3) | 비교 검사 | infra/PlacementMigrationIntegrationTest.java:developOrder_outOfOrderAppliesOnce | 합격(11-08, Docker) |
| TC-2769 | D-627 | 서버 클래스 전체, 계약 기준 파일 | ArchUnit, 계약 스냅샷 | 패키지 70, 허용 쌍 web.admin.notification → notification, notification → audit. 계약 NotificationType 22, Screen 12, GatedNotification 「NOTICE -」, InboxView.Item.title, NoticeRequest가 서버와 같음. develop 역병합 뒤 73(또는 77)은 미실행 | 구조, 비교 검사 | ArchitectureTest.java:designPackageList_has70NamesAndEachHasAnAllowList 외, AppContractSnapshotTest.serverContract_matchesBaseline | 합격(11-08), 역병합 확인 미실행 |

### 13.4 검증 (srs 7.13.25 S-NT-1, 실기기)

자동 시험이 덮는 예외 흐름: S-NT-1a → TC-2748, S-NT-1b → TC-2740~TC-2743, TC-2766, S-NT-1c → TC-2746, TC-2749, TC-2750, S-NT-1d → TC-2755, S-NT-1f → TC-2765.

| TC | 요구 | 입력 | 실행 조건 | 기대 결과 | 기법 | 자동 시험 | 결과 |
|----|------|------|-----------|-----------|------|-----------|------|
| TC-2770 | S-NT-1, FR-320, FR-321 | 8443, 0.11.4 기기 계정, 제목, 본문(짧은 주소 글 포함), FORM | 관리 API(curl, 운영자 토큰) → 실기기 | 결과 (1, 1, []) → 감사 1건 → 기기 알림 제목이 공지 제목 → 알림함 줄 제목과 본문 → 누르면 읽음, 기본 브라우저로 구글 폼이 열림(앱 안 웹 화면 아님), 돌아오면 알림함 | 시나리오 | 부분은 TC-2764 | 수동. (v0.26 보완) 앱 쪽 일부 합격(11-09, 8절 18). 관리 API 결과, 감사, 기기 알림은 미실행(운영자 발송 필요) |
| TC-2771 | S-NT-1e, FR-321 | 같은 공지를 0.11.3 기기 계정에 | 실기기 | 알림함 제목 「알림」과 본문, 눌러도 아무것도 열리지 않고 알림함에 머묾, 앱 종료 없음. 기기 알림 제목은 공지 제목(서버가 FCM 제목을 줌) | 동치 분할(옛 앱) | 없음 | 수동, 미실행(운영자 발송 필요, 0.11.3 기기 필요) |
| TC-2772 | FR-321, D-628 | 0.11.4 기기를 앱 앞, 뒤, 종료로 두고 각각 공지 | 실기기 | 세 상태 모두 기기 알림 제목과 본문이 공지 것. 기기 알림을 누르면 앱이 열림(링크로 바로 가지 않음) | 동치 분할(앱 상태 3) | 서버 제목은 TC-2756 | 수동, 미실행(운영자 발송 필요) |
| TC-2773 | D-623, FR-025 | 마케팅 수신 동의를 철회하고 알림 설정을 모두 끈 계정 | 실기기 | 공지 기기 알림과 알림함 항목을 받음. 알림 설정 화면에 공지 스위치 없음 | 동치 분할 | 미동의 회원 푸시는 TC-2751 | 수동, 미실행(운영자 발송 필요) |

### 13.5 시스템과 운영

| TC | 요구 | 입력 | 실행 조건 | 기대 결과 | 기법 | 자동 시험 | 결과 |
|----|------|------|-----------|-----------|------|-----------|------|
| TC-2774 | PL7-B1 | TC-2742의 점 조각 주소 3개 | 배포 서버 관리 API(curl) | 셋 다 409 VALUE_INVALID, 알림 0건, 감사 0건(nginx를 거친 실제 요청에서도 주소가 바뀌지 않음) | 보안 | 판정 함수는 TC-2742 | 수동, 미실행 |
| TC-2775 | PL7-B3, 사용자 결정 13 | 시험 계정 12명 공지, 발송 시각 | 8443, 응답 시간 측정 | 정상이면 60초 안에 200. 504나 시간 초과면 다시 보내지 않고 감사 기록과 알림함으로 저장 여부를 확인한다. 시스템은 21시 뒤 요청도 막지 않으므로 운영자가 08:00~21:00에 보낸다 | 운영 시나리오 | 없음 | 수동, 미실행 |
| TC-2776 | PL7-B4, 사용자 결정 15 | 8443에서 공지 뒤 서버 이미지를 0.11.3으로 되돌림 | infra/README.md 롤백 절차 | 절차대로 `DELETE FROM notification WHERE type = 'NOTICE'` 뒤 되돌리면 알림함 조회 200. 행을 남기고 되돌리면 500(알려진 문제, 절차로 막음). 감사 기록은 남음 | 운영 시나리오 | 없음 | 수동, 미실행 |
| TC-2777 | 사용자 결정 12, 14, PL7-B5 | `/legal/privacy`, 11/17 발송용 구글 폼 | 배포 뒤 브라우저 | 처리 목적에 운영 공지 알림과 이벤트, 이벤트 응모 항목, 「이벤트 응모와 경품 발송 연락처」 소절, 감사 기록에 「운영 공지 알림 발송」, 시행일 2026-11-08(배포일 즉시). 구글 폼 첫 문항이 개인정보 수집, 이용 동의 | 검사 | 없음 | 수동, 미실행 |

### 13.6 보강 필요, 실행 결과, 추적

보강 필요(자동 시험 없음 또는 일부): 우선 1 TC-2746(100명 정각, 원소 0과 −1, 겹침 포함 101개), 2 TC-2744(300, 301자), 3 TC-2749, TC-2750(제목과 본문 길이 경계), 4 TC-2745(링크 공백 다듬기), 5 TC-2752(ALERT.NOTICE SETTING_INVALID), 6 TC-2763(링크 열기 실패). 넣을 곳은 NoticeServiceTest, NoticeAdminControllerTest, 회원 설정 시험, InboxTest다. 코드와 시험 코드는 고치지 않았다.

실행: 이번에 시험을 다시 돌리지 않았다. 결과 칸의 합격은 코드 리뷰 검증(서버 `./gradlew build` 1,979건, 앱 testLocalDebugUnitTest 2,078건 통과, 11-08~11-09, 코드 2e05c391)에 이 시험들이 들어 있던 것을 옮겼다. 70eb1bf0은 문서(infra/README.md)만 바꿨다.

(v0.26 보완) 11-09 8절 18 실행: 수동 8건 중 일부 합격 1(TC-2770), 미실행 7(TC-2771~TC-2777). 근거 `C:\Users\SSAFY\runkk-work\device-check-0114\notice\`.

| 구분 | 수 |
|------|----|
| 13절 케이스 | 39(TC-2739~TC-2777) |
| 자동 대응 | 28(전부 24, 일부 4: TC-2744, TC-2746, TC-2749, TC-2752) |
| 자동 시험 없음(보강 필요) | 3(TC-2745, TC-2750, TC-2763). 일부 대응 4건의 남은 값도 보강 필요 |
| 수동 | 8(TC-2770~TC-2777) |
| 합격(자동) | 28 중 28(일부 대응은 대응한 값만). 실패 0 |

| 요구 | 설계 | TC | 자동 시험 |
|------|------|----|-----------|
| FR-320 운영자 공지 알림 발송 | D-621~D-627 | TC-2739~TC-2751, TC-2753~TC-2755, TC-2764~TC-2770, TC-2774, TC-2775 | 있음(길이 경계 보강 필요) |
| FR-321 공지 알림 표시와 링크 열기 | D-624, D-628 | TC-2756~TC-2761, TC-2763, TC-2764, TC-2770~TC-2772 | 있음(링크 열기, 옛 앱, 기기 알림은 수동) |
| FR-025(바뀜, 공지는 마케팅 동의 무관) | D-623 | TC-2751, TC-2752, TC-2762, TC-2773 | 있음(철회는 수동) |
| FR-175(바뀜, 공지 종류) | D-621, D-622 | TC-2752, TC-2755, TC-2756, TC-2767 | 있음 |
| FR-176(바뀜, 공지 감사) | D-625 | TC-2753 | 있음 |
| FR-178(바뀜, 공지 표시와 링크) | D-628 | TC-2758, TC-2761 | 있음 |
| 운영 절차(롤백, 처리방침, 야간) | 05-27 8절 판정 | TC-2775~TC-2777 | 없음(수동) |

시퀀스 SQ-58은 T-67(TC-2764)이 서버 쪽을, TC-2770이 앱 쪽을 덮는다. 계획 완성도: 새 FR 2개와 바뀐 FR 4개 모두 케이스가 있고(미커버 0), 범위 조건(회원 수, 제목, 본문, 링크 길이, 경로 조각 수)마다 경곗값 케이스가 있다. 자동화되지 않은 경계 값이 남았다(보강 필요 6항목).

## 14. 시험 문맥 시계 결함 (v0.26, 2026-11-09)

브랜치 `fix/S15P31A207-275-poll-reminder-clock`(303fd0b9)의 시험 결함이다. 운영 코드는 고치지 않았다(운영 Clock 빈은 `Clock.system(ServerZone.ID)`라 시간대가 늘 있다).

| 결함 | 내용 | 원인 | 수정 | 상태 |
|------|------|------|------|------|
| TF-BE-45(주요) | CI 서버 시험에서 `PollReminderJob.run`이 `Clock.getZone()` null로 NPE를 내고 `poll_reminder_failed`를 ERROR로 남겨, 출력에 ERROR가 없는지 보는 시험(OperatorSeedLogTest 등)을 흔들었다 | `AiPrivacyWithdrawalIntegrationTest`가 Clock 빈을 `@MockitoBean`(목)으로 바꾼 시험 문맥을 띄우는데, 1분 고정 지연 투표 마감 알림이 문맥이 뜨자마자, 그리고 목이 초기화된 뒤 문맥 캐시에서 계속 돈다. 시각 고정 작업 17개도 같은 목 문맥에서 같은 NPE를 낼 수 있다 | 목 Clock을 `@TestBean Clock` + 시간대(Asia/Seoul)를 늘 가진 `MovableClock`으로 바꿨다. 시험 시각과 내용은 그대로다. 시험 프로필에서 스케줄을 끄는 방법은 운영 코드를 고쳐야 하고 시각 고정 작업 문제가 남아 쓰지 않았다 | 해소(303fd0b9) |

| TC | 대상 | 입력 | 기대 결과 | 유형 | 대응 시험 | 결과 |
|----|------|------|-----------|------|-----------|------|
| TC-2778 | 시험 문맥 Clock 규칙(TF-BE-45 회귀) | backend 시험 클래스 전체 | `@MockitoBean`이 붙은 `Clock` 필드가 없다. 수정 전 코드에서는 `AiPrivacyWithdrawalIntegrationTest.clock` 1건으로 실패한다 | 구조(ArchUnit) | TestClockRuleTest.java | 합격(2026-11-09, backend `./gradlew build` 통과, 전체 시험 출력에 `Clock.getZone()` NPE와 `poll_reminder_failed` 0줄. TestClockRuleTest 1, AiPrivacyWithdrawalIntegrationTest 6, OperatorSeedLogTest 3 합격) |

박재영 확인 요청: `AiPrivacyWithdrawalIntegrationTest`(탈퇴 ai 파기 시험)의 Clock 교체를 MR에서 확인한다. test/ai-자료파기-박.md는 고칠 것이 없다.
