# 병합 대기: docs/S15P31A207-275-event-docs

> 작성: 2026-11-08 | 작성자: 이성현 | 스킬: sw-srs, sw-design-oo, sw-code-review, sw-test, (공지 알림) sw-srs, sw-design-oo, 구현, (공지 리뷰) sw-code-review, sw-test 보강

이벤트 기능(코치님 이벤트, 베타 1km 이벤트, 알림함 진입점, 상세 웹 화면)의 요구사항과 객체지향 설계를 사후 문서화했다. 코드는 핫픽스 0.11.4 `hotfix/S15P31A207-275-v0114-coach-celeb`(MR !90, 7558f6f4..d824b2a2)에 먼저 들어갔다. 이 문서 브랜치는 origin/develop(55cfd678) 기준이다. 정식 번호와 산출물 버전(srs 다음 판, design-oo 다음 판)은 `/sw-sdlc merge` 때 매긴다.

이 파일 하나만으로 핫픽스 브랜치에 올려도 내용을 알 수 있게 사용자 결정, 계약 요약, 번호, 요청을 모두 적었다(핫픽스 브랜치에는 아래 조각 파일이 없다. 조각은 이 문서 브랜치가 develop에 합쳐질 때 들어간다).

## 사용자 결정 (2026-11-08, 대화에서 확정)

1. 이벤트는 앱 업데이트 없이 고치고 더한다. 목록은 앱 화면(서버 API), 상세는 서버 정적 웹 페이지를 앱 웹 화면으로 연다. 관리 API로 이벤트 항목을 고치고, 페이지는 서버 배포로 고친다.
2. 진입점: 홈 상단에 단추를 넣지 않는다(닉네임 칸 부족). 알림함 맨 위 「이벤트」 항목, 새 이벤트면 알림함 아이콘에 점.
3. 코치님 이벤트: 셀럽런에서 코치님(주세빈 5km 30:18, 김선교 5km 30:00)을 이기면 코치별 응모 1회(최대 2). 코치님마다 GS 상품권 1만 원 1장, 그 코치님을 이긴 사람 가운데 1명 추첨. 코치별 첫 승리에 아바타 포인트 500P 1회. 배지 보상 없음.
4. 베타 이벤트: 기간 안 유효 기록 1km 이상 1회면 자동 응모, 메가커피 쿠폰 10명 추첨. 걷기도 된다. 멀티캠퍼스(테헤란로 212) 출발 산책 코스 C(한 바퀴 1.1km), D(선릉역 방향 일자 1.1km), E(강남역 방향 일자 1.1km).
5. 기간 2026-11-08 00:00:00 ~ 2026-11-16 23:59:59(한국 시간). 배포는 핫픽스 0.11.4(MR !90).
6. 코치 실명 표시(D130, PM 책임), 셀럽 출처 「런크크」.
7. 화면 문구: 가운뎃점 금지, 보조 설명 줄 금지.
8. (코드 리뷰 뒤 추가) 추첨 명단은 관리 API로 제공한다([임시:FR-성-14], [임시:D-성-18], srs 메모 E1 해소).
9. (코드 리뷰 뒤 추가) 당첨자 연락은 앱 알림과 구글 폼으로 한다. 앱은 바꾸지 않는다(srs 메모 E2 일부 해소, 구글 폼 연락처 수집 동의와 처리방침 영향은 남음). 운영자가 앱 알림을 보내는 관리 기능은 지금 없다(code-review/이벤트-1차-성 6절).
10. (공지 알림, 2026-11-08) 당첨자 연락은 앱 알림 + 구글 폼, 운영자 알림 발송 기능을 만든다. 11/17 추첨 전에 앱이 새 알림 종류를 알아야 하므로 핫픽스 0.11.4(MR !90)에 넣는다([임시:CR-성-6]). 9번의 「앱은 바꾸지 않는다」를 이 결정으로 바꾼다.
11. (공지 리뷰, 2026-11-08) 공지 알림은 마케팅 수신 동의와 상관없이 보낸다(응모자 개인 운영 안내, srs 메모 N1과 설계 R10 확정).
12. (공지 리뷰, 2026-11-08) 구글 폼에 개인정보 수집, 이용 동의 문항을 넣고 앱 처리방침을 갱신한다(srs 메모 N5, E2. 처리방침은 코드 브랜치 322d6e15).
13. (공지 리뷰, 2026-11-08) 야간 발송은 시스템 규칙 없이 운영 절차(08:00~21:00)로만 둔다(srs 메모 N4).

## 코드 리뷰 (2026-11-08, sw-code-review, [code-review/이벤트-1차-성](../code-review/이벤트-1차-성.md))

MR !90(origin/main...d824b2a2)을 리뷰했다. 치명 0, 주요 2, 경미 10, 제안 4, 확인 필요 1. 판정 합격(조건부 배포). 코드 수정은 같은 핫픽스 브랜치에 d221fd54(서버, nginx), 2f99b573(앱), d06641bb(응모자 명단 관리 API)를 쌓았다(push 전). 검증: 서버 `./gradlew build` 시험 1,959건 통과, 앱 ktlintCheck, lintLocalDebug, testLocalDebugUnitTest(957건), assembleLocalDebug 통과.

| 결함 | 등급 | 한 줄 | 처리 |
|------|------|-------|------|
| [임시:EV-성-1] | 주요 | 관리 API가 https 주소를 받아 앱 상대 경로 계약과 어긋남(R1, E3) | 수정: 상대 경로 전용 |
| [임시:EV-성-2] | 경미 | 지급 이력 저장 실패가 남은 셀럽을 건너뛰고 이력이 끝내 안 생김(R2) | 수정: 셀럽마다 격리, 이력 보충 |
| [임시:EV-성-3] | 경미 | 이벤트 설정 JSON "null"이 500 | 수정: 409 |
| [임시:EV-성-4] | 경미 | develop out-of-order 제거 조건 주석에 35.2 없음 | 수정 |
| [임시:EV-성-5] | 주요 | 공용 에지 nginx를 develop, release 배포가 덮어 prod /events/ 설정이 사라짐 | 남김: 병합 직후 역병합, 그 전 develop 배포 보류 |
| [임시:EV-성-6] | 경미 | /events/에 콘텐츠 보안 정책 없음(R7, E7) | 수정: CSP, nosniff, Referrer-Policy |
| [임시:EV-성-7] | 경미 | 허용 출처를 못 읽으면 null끼리 같은 출처로 봄 | 수정 |
| [임시:EV-성-8] | 경미 | 이벤트 목록이 복귀 때 다시 읽지 않음 | 수정 |
| [임시:EV-성-9] | 경미 | 알림함 이벤트 항목 앞 읽기 취소 없음 | 수정 |
| [임시:EV-성-10] | 경미 | 8443, 시험 DB에 코치 기록 11, 12 보장 없음 | 남김: 결정 필요 |
| [임시:EV-성-11] | 제안 | GET /events가 화면 재개마다 응모 계산(R3) | 남김 |
| [임시:EV-성-12] | 제안 | 감사 SETTING_CHANGE 재사용(R5) | 남김 |
| [임시:EV-성-13] | 경미 | 종료 23:59:59의 1초 미만 경계 | 남김 |
| [임시:EV-성-14] | 제안 | 상세 페이지 글자 고정(R8) | 남김: 운영 절차 |
| [임시:EV-성-15] | 확인 필요 | 응모 항목 이름이 운영 DB 가림 이름 열 값(R9, E4) | 남김: 운영 DB 확인 |
| [임시:EV-성-16] | 제안 | 15:00/km보다 느린 걸음은 유효 기록이 아니라 응모 안 됨 | 남김: 결정 필요 |
| [임시:EV-성-17] | 경미 | sumOn EVENT 제외 통합 시험 없음 | 남김 |

## 공지 알림 코드 리뷰와 시험 보강 (2026-11-08, sw-code-review, [code-review/공지알림-1차-성](../code-review/공지알림-1차-성.md))

MR !90의 b26b3317(운영자 공지 알림 발송)을 리뷰했다. 치명 0, 주요 1, 경미 3, 제안 2. 판정 합격. 같은 핫픽스 브랜치에 수정 62b7f7e8, 시험 2e05c391, 처리방침 322d6e15을 쌓았다(push 전). 검증: 서버 `./gradlew build` 시험 1,979건 통과(실패 0, 건너뜀 0, Docker PostgreSQL 포함, 11-09 00:03). 11-08 23:30 첫 실행에서는 크루 주간 레이스 시험 2건(CrewThreadIntegrationTest t9, CrewControllerTest postsAndCrewRun)이 일요일 자정 직전에 시작 시각(지금 + 1분, + 30분)이 다음 주로 넘어가 실패했고 자정 뒤 다시 돌려 통과했다(이번 변경과 무관한 시각 의존 시험, 별도 보고). 앱 ktlintCheck, lintLocalDebug, testLocalDebugUnitTest(2,078건), assembleLocalDebug 통과(앱 코드 변경 없음).

| 결함 | 등급 | 한 줄 | 처리 |
|------|------|-------|------|
| [임시:EV-성-18] | 주요 | 공지 링크 검사가 경로 접두어만 봐 `docs.google.com/forms/../url?q=…`, `%2e%2e` 점 조각으로 구글 폼 밖 경로에 닿음(보안, D-성-22) | 수정: 경로 조각 영문, 숫자, 「-」, 「_」만 |
| [임시:EV-성-19] | 경미 | 같은 회원 셀럽런 판정이 동시에 커밋되면 늦은 쪽이 지급 이력 고유 위반을 event_point_failed(ERROR)로 남김(TC-성-51 보강 시험이 드러냄. 돈과 이력은 한 번) | 수정: 이미 있으면 INFO로 끝냄 |
| [임시:EV-성-20] | 경미 | 공지 푸시가 커밋 뒤 관리 요청 스레드에서 동기(R12). FCM 장애면 12명 84초로 nginx 60초를 넘어 504, 다시 보내면 중복 공지 | 남김: 운영 절차(다시 보내지 않고 감사 기록 확인), 다음 릴리스 비동기 |
| [임시:EV-성-21] | 경미 | 공지를 보낸 뒤 서버를 0.11.3으로 롤백하면 옛 enum에 NOTICE가 없어 알림함 조회 500 | 남김: 롤백 절차(공지 행 처리 먼저) |
| [임시:EV-성-22] | 제안 | 앱 inboxLink는 https만 보고 호스트를 보지 않음(D-성-22 (다)). 서버가 유일한 방어선 | 남김: 다음 앱 판 검토 |
| [임시:EV-성-23] | 제안 | 감사 변경후값에 본문 전체와 회원 ID 목록이 6개월(R13) | 해소: 처리방침 감사 기록에 「운영 공지 알림 발송」 |

결함 아님으로 확인한 것(리뷰 3절): 관리 권한(OperatorCheckInterceptor), target_id 외부 주소(CREW_CHAT 부분 색인 밖, 숫자로 읽는 곳 없음), V35_3 기존 자료와 순서 밖 적용(origin/develop V36, V37은 notification을 만지지 않음), 푸시 실패(알림함은 남고 로그만), 계약 동기화(sync_from_server.py 다시 돌려 내용 차이 0), 화면 문구(「안내」 하나).

보강한 시험(test/이벤트-성 9절 우선 1~6, 같은 조각에 직접 반영함):

| TC | 시험 | 결과 |
|----|------|------|
| [임시:TC-성-42] | EventPointThreadIntegrationTest.sumOn_excludesEventPoints(PostgreSQL) | 합격 |
| [임시:TC-성-51] | EventPointThreadIntegrationTest.concurrentJudgements_grantOnce(네 스레드, 500P 한 번, 이력 1행, 실패 로그 0) | 합격(첫 실행에서 EV-성-19 발견, 고침) |
| [임시:TC-성-28]~[임시:TC-성-30] | EventPointThreadIntegrationTest.celebrityWinQueries_filterAndBoundaries(진 판, 포기, 보류, SELF, GHOST 제외, 판정일시 양 끝, 중복 승리 최솟값, 두 질의) | 합격 |
| [임시:TC-성-12]~[임시:TC-성-14], [임시:TC-성-16] | EventServiceTest.admin_coachConfigBoundaries, admin_runConfigBoundaries | 합격 |
| [임시:TC-성-33], [임시:TC-성-34] | EventServiceTest.runEntry_countsExactlyMinimumDistance(1,000m 정각), runEntry_includesBothEndsOfPeriod | 합격 |
| [임시:TC-성-46], [임시:TC-성-50] | CoachWinPointListenerTest.judgedAtPeriodEndsGrants, zeroPointEventGrantsNothing | 합격 |

EventConfig.parse 순환 복잡도 12 → 6(종류별 검사 isValidCoachCeleb 6, isValidRunDistance 2로 분리, 동작 불변). test/이벤트-성 집계: 자동 대응 93 → 103, 보강 필요 22 → 12(TC-성-5, 22, 37, 48, 59, 73, 76, 78, 89, 90, 103, 104).

처리방침(infra/nginx/static/legal/privacy.html, 주소 /legal/privacy 그대로, 시행일 2026-11-08): 처리 목적에 운영 공지 알림과 이벤트, 항목 표에 이벤트 응모(셀럽런 승리와 판정 일시, 응모 기준 거리 이상 유효 기록, 포인트 지급 이력, 새로 받지 않음), 새 소절 「이벤트 응모와 경품 발송 연락처」(구글 폼에서 따로 동의, 발송 뒤 파기), 보관 기간 2행, 감사 기록에 운영 공지 알림 발송, 외부 서비스에 Google 설문지, 알림 수신 권리에 공지는 종류별로 끄지 않음, 10절 「현재 버전(v1.0)에는 이벤트·공지 푸시를 보내는 기능이 없습니다」 → 「광고성 정보를 보내는 기능 없음, 운영 공지 알림은 마케팅 동의와 상관없이 앱 알림」.

## 운영자 공지 알림 (2026-11-08, sw-srs → sw-design-oo → 구현)

요구사항 [srs/07-20](../srs/07-20-변경-요청-CR-공지알림-성.md)(7.13.23, 03-07 3.7.4), 설계 [design-oo/05-25](../design-oo/05-25-설계-공지알림-성.md), 코드 같은 핫픽스 브랜치 b26b3317 「feat: 운영자 공지 알림 발송」(push 전).

- API: `POST /api/v1/admin/notifications/notices`(운영자) 본문 `{"memberIds":[1~100개], "title":"1~60자", "body":"1~500자", "linkUrl":"구글 폼 https 주소(선택)"}` → 200 `{"requested", "stored", "skippedMemberIds"}`. 형식 400, 구글 폼(forms.gle, docs.google.com/forms/) https가 아닌 링크 409 VALUE_INVALID.
- 새 알림 종류 NOTICE(이동 화면 EXTERNAL_LINK, 대상 ID = 링크), notification.title 열, target_id 300자, V35_3(out-of-order, develop V36, V37은 notification을 만지지 않음).
- 저장, 푸시는 기존 NotificationService 경로(deliver). 공지는 수신 설정 단위가 아니고 마케팅 동의를 보지 않는다(당첨 안내는 광고가 아님, 기본 미동의라 따르면 당첨자 대부분이 못 받음) (2026-11-08 사용자 결정 11로 확정). 야간 규칙은 없어 운영 절차(08:00~21:00).
- 감사: 새 행위 없이 SETTING_CHANGE, 대상 「NOTICE:제목」(develop V37 audit 제약 때문, [임시:D-성-10]과 같은 판단).
- 모르는 알림 종류(옛 앱 0.11.3 이하): 알림함에 제목 「알림」과 본문이 보이고 깨지지 않는다. 눌러도 링크는 열리지 않는다(모르는 화면은 알림함에 머묾). 기기 알림은 공지 제목으로 뜬다. 0.11.4는 서버 제목을 보이고 링크를 바깥 브라우저로 연다. 본문에 짧은 주소를 글로 적는 운영 절차를 권한다(N2).
- 검증: 서버 `./gradlew build` 1,970건 통과, 앱 ktlintCheck, lintLocalDebug, testLocalDebugUnitTest(모듈 합계 2,078건), assembleLocalDebug 통과.

## 고친 조각

- (공지 리뷰) code-review/공지알림-1차-성.md: 새 조각. 공지 알림 1차 코드 리뷰(결함 [임시:EV-성-18]~[임시:EV-성-23], 보안, 마이그레이션, 푸시, 계약 점검, 수정과 검증, 설계서 갱신 항목).
- (공지 리뷰) test/이벤트-성.md: 9절 우선 1~6 보강 반영(TC-성-12, 13, 14, 16, 28, 29, 30, 33, 34, 42, 46, 50, 51 자동 시험과 결과), 4.1 리팩터링 뒤 V(G), 9절 처리 열, 10절 실행 결과와 집계.
- (공지 리뷰) srs/07-20-변경-요청-CR-공지알림-성.md: 메모 N1, N4, N5를 사용자 결정으로 닫음, 공지링크 주석에 경로 조각 규칙, 7.13.7 일관성 합격, 새 7.13.23.9.
- (공지 알림) srs/07-20-변경-요청-CR-공지알림-성.md: 새 조각. 7.13.23 [임시:CR-성-6], 도출과 동의 판단, 시나리오 S-NT-1, 새 요구, 영향, DFD 7.12와 소단위 명세, 자료사전 새 항목 7개, 모르는 알림 종류 처리 확인, 메모 N1~N5, 변경 점검.
- (공지 알림) srs/03-07-운영-관리.md: 새 3.7.4([임시:FR-성-15], [임시:FR-성-16]), FR-173 DFD 7.12, FR-175 공지 종류, FR-176 공지 감사, FR-178 공지 표시와 링크.
- (공지 알림) srs/03-01-회원-관리.md(이성현): FR-025에 운영자 공지는 마케팅 동의를 보지 않음, 처리방침 문구 확인 필요.
- (공지 알림) srs/06-04b(공용): 알림레코드, 알림사건, 알림함, 푸시요청에 (알림제목), 알림종류에 공지, 이동대상 주석, 운영결과에 공지발송결과.
- (공지 알림) srs/07-19: 다음 링크, 메모 E2 보충.
- (공지 알림) design-oo/05-25-설계-공지알림-성.md: 새 조각. [임시:D-성-19]~[임시:D-성-26], 클래스 명세, [임시:SQ-성-3], 앱, API 예, DB, 패키지, 시험, 리뷰 점 R10~R14.
- (공지 알림) design-oo/06-05-클래스-runimal-policy-operator.md(이성현): 6.1.10 notification에 「운영자 공지」 절.
- (공지 알림) design-oo/05-24: 다음 링크.
- (시험 추가) test/이벤트-성.md: 새 조각. 이벤트 시험 [임시:TC-성-1]~[임시:TC-성-138](단위 서버 59, 단위 앱 36, 통합 T-EV-1~T-EV-4와 마이그레이션, 구조, 계약 21, 검증 10, 시스템 12), 화이트박스 4함수(EventConfig.parse V(G) 12 리팩터링 권고), 배포 뒤 실기기 확인 체크리스트(8절 15항목), 보강 필요 목록(9절), 실행 결과(10절), FR → TC 추적(11절), 기존 케이스 영향(12절).
- (코드 리뷰 뒤 추가) code-review/이벤트-1차-성.md: 새 조각. 이벤트 1차 코드 리뷰(결함 [임시:EV-성-1]~[임시:EV-성-17], 수정과 검증, 응모자 명단 API, 당첨 알림 기능 확인).
- (코드 리뷰 뒤 추가) srs/03-07-운영-관리.md: [임시:FR-성-10] 주소 규칙을 상대 경로 전용으로(E3 해소), 새 [임시:FR-성-14] 이벤트 응모자 명단 조회, FR-173 DFD 행에 [임시:FR-성-14], 3.7.2, 3.7.3 제목 줄이 「응모기준거리응모기준거리」로 깨져 있던 것을 「####」로 되돌림.
- (코드 리뷰 뒤 추가) srs/07-19-변경-요청-CR-이벤트-성.md: 새 요구 목록에 [임시:FR-성-14], 구현 대조 두 줄과 메모 E1, E2, E3, E7에 처리 결과.
- (코드 리뷰 뒤 추가) design-oo/05-24-설계-이벤트-성.md: [임시:D-성-9] 주소 규칙, 새 [임시:D-성-18] 응모자 명단, 10절 끝에 R1~R9 판정 문단.

- srs/03-07-운영-관리.md: FR-173 DFD에 7.9, FR-176 감사 대상에 이벤트 등록, 수정, 공개 전환(행위종류 기준값변경, 대상ID 「EVENT:주소이름」), FR-178 알림함 맨 위 이벤트 항목. 새 3.7.1 보충(코치 실명 기록 ID 11, 12), 새 3.7.2 이벤트([임시:FR-성-1]~[임시:FR-성-13]), 새 3.7.3 이벤트 첫 등록 목록(운영 자료, 산책 코스).
- srs/03-06-캐릭터-관리.md(이성현): FR-159 처리에 이벤트 포인트는 하루 200P 한도 밖, 출처와 DFD 보충([임시:CR-성-5]).
- srs/07-19-변경-요청-CR-이벤트-성.md: 새 조각. 7.13.22 변경 요청 [임시:CR-성-1]~[임시:CR-성-5], 도출 기록과 시나리오 S-EV-1~S-EV-3, 새 요구 목록, 영향 분석, 모델 반영(DFD 7.9~7.11, 소단위 명세), 구현 대조, 위험 메모 E1~E7, 변경 점검.
- 공용 srs 조각(공용 담당 = 작성자라 직접 고침, "(다음 판 변경)" 표시): 01-개요(1.3 용어 이벤트, 응모, 이벤트 포인트, 리워드 행 보충), 02-전체-설명(2.4 [임시:C-성-1]), 04-비기능(4.3 [임시:NFR-성-1], [임시:NFR-성-2], 4.5 [임시:NFR-성-3], 4.7 출처 3행), 05-외부-인터페이스(5.1 홈 행 알림함 점, 알림함 행, 새 행 이벤트 목록, 이벤트 상세), 06-04b(운영결과에 이벤트관리결과, 행위종류 주석), 06-07(ERD 2줄과 관계 4행, 새 6.4.10 이벤트 진행상태 상태전이도), 06-08(끝에 "이벤트 자료" 29항목 + 기본 자료 3항목), 07-11(끝에 "이벤트 행" FR 13행, NFR 3행과 바뀐 기존 행 문단), 07-18(다음 링크).
- design-oo/05-24-설계-이벤트-성.md: 새 조각. [임시:D-성-1]~[임시:D-성-17], 클래스 추출, 클래스도, 서버와 앱 클래스 명세, [임시:SQ-성-1], [임시:SQ-성-2](시퀀스), 상태도 대응, 기능 모델 대응, API, DB 표, 정적 페이지, 패키지 의존, 테스트 순서, 코드와 어긋나 보이는 점 R1~R9.
- design-oo/06-05-클래스-runimal-policy-operator.md(이성현): RewardWallet.accrue EVENT 분기, RewardService.accrueEvent, 새 AccrualReason 절.
- 공용 design-oo 조각(직접 고침): 05-01(5.1.1 패키지 4행과 수 문단), 05-03(5.7.1 API 7행), 12-의존도(12.4 문단), A-추적표(A.1 13행, A.2 3행), 05-23(다음 링크).

## 새 임시 번호

| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:CR-성-1] | CR | 이벤트 기능(목록은 앱 화면, 상세는 서버 웹 페이지, 관리 API, 같은 출처 브리지) | srs/07-19 |
| [임시:CR-성-2] | CR | 진입점: 알림함 맨 위 이벤트 항목, 안 본 이벤트 점(홈 단추 없음) | srs/07-19 |
| [임시:CR-성-3] | CR | 코치님 이벤트(응모 코치별 1회, 첫 승리 500P, 배지 없음, 코치 기록 11, 12) | srs/07-19 |
| [임시:CR-성-4] | CR | 베타 1km 이벤트(걷기 포함, 메가커피 10명, 산책 코스 C, D, E) | srs/07-19 |
| [임시:CR-성-5] | CR | 이벤트 포인트는 하루 적립 한도 밖(FR-159 변경) | srs/07-19 |
| [임시:FR-성-1] | FR | 이벤트 목록 조회 | srs/03-07 3.7.2 |
| [임시:FR-성-2] | FR | 알림함 이벤트 항목 | srs/03-07 3.7.2 |
| [임시:FR-성-3] | FR | 새 이벤트 표시(알림함 항목과 홈 알림함 점) | srs/03-07 3.7.2 |
| [임시:FR-성-4] | FR | 이벤트 상세 페이지 표시 | srs/03-07 3.7.2 |
| [임시:FR-성-5] | FR | 내 이벤트 참여 조회 | srs/03-07 3.7.2 |
| [임시:FR-성-6] | FR | 셀럽 승리 응모 판정 | srs/03-07 3.7.2 |
| [임시:FR-성-7] | FR | 기록 거리 응모 판정 | srs/03-07 3.7.2 |
| [임시:FR-성-8] | FR | 셀럽 승리 이벤트 포인트 적립 | srs/03-07 3.7.2 |
| [임시:FR-성-9] | FR | 이벤트 관리 목록 조회 | srs/03-07 3.7.2 |
| [임시:FR-성-10] | FR | 이벤트 등록 | srs/03-07 3.7.2 |
| [임시:FR-성-11] | FR | 이벤트 수정 | srs/03-07 3.7.2 |
| [임시:FR-성-12] | FR | 이벤트 공개 전환 | srs/03-07 3.7.2 |
| [임시:FR-성-13] | FR | 상세 페이지의 앱 기능 사용(로그인 정보, 화면 열기, 닫기, 공유) | srs/03-07 3.7.2 |
| [임시:NFR-성-1] | NFR | 앱 기능은 같은 출처 주 문서에서만, 다른 출처 반환 0건 | srs/04 4.3 |
| [임시:NFR-성-2] | NFR | 상세 화면의 다른 출처 자원과 이동 0건 | srs/04 4.3 |
| [임시:NFR-성-3] | NFR | 앱 업데이트 없이 이벤트 추가와 수정, 페이지 반영 60초 | srs/04 4.5 |
| [임시:C-성-1] | 제약 | 상세 페이지는 nginx 정적 파일과 안드로이드 WebView, 외부 자원 없음, OSM 저작자 표시 | srs/02 2.4 |
| [임시:D-성-1]~[임시:D-성-17] | D | 이벤트 설계 결정(아래 목록) | design-oo/05-24 1.2 |
| [임시:SQ-성-1], [임시:SQ-성-2] | 시퀀스 | 목록에서 상세 열기와 내 참여, 셀럽런 승리 뒤 코치 승리 포인트 | design-oo/05-24 3 |
| [임시:FR-성-14] | FR | 이벤트 응모자 명단 조회(관리, 추첨용, 코드 리뷰 뒤 추가) | srs/03-07 3.7.2 |
| [임시:D-성-18] | D | 응모자 명단은 관리 API, 조회 때 한 번에 계산(EventEntrantService) | design-oo/05-24 1.2 |
| [임시:EV-성-1]~[임시:EV-성-17] | 결함 | 이벤트 1차 코드 리뷰 결함(위 표) | code-review/이벤트-1차-성 2절 |
| [임시:CR-성-6] | CR | 운영자 공지 알림(당첨 안내, 앱 알림 + 구글 폼 링크) | srs/07-20 |
| [임시:FR-성-15] | FR | 운영자 공지 알림 발송 | srs/03-07 3.7.4 |
| [임시:FR-성-16] | FR | 공지 알림 표시와 링크 열기 | srs/03-07 3.7.4 |
| [임시:D-성-19]~[임시:D-성-26] | D | 공지 설계 결정(NOTICE와 EXTERNAL_LINK와 제목 열, 기존 경로 재사용과 NoticeService, 수신 동의와 야간, 링크 규칙, 감사 재사용, V35_3, web.admin.notification, 앱 표시) | design-oo/05-25 1.2 |
| [임시:SQ-성-3] | 시퀀스 | 운영자 공지 발송과 받기 | design-oo/05-25 3 |
| [임시:EV-성-18]~[임시:EV-성-23] | 결함 | 공지 알림 1차 코드 리뷰 결함(공지 리뷰 표) | code-review/공지알림-1차-성 2절 |
| [임시:TC-성-1]~[임시:TC-성-138] | TC | 이벤트 시험 케이스(자동 대응 93: 전부 84, 일부 9. 자동 시험 없음 22. 수동 23) | test/이벤트-성 2~7절 |

번호 아닌 새 식별자(병합 때 확인): 통합 시험 스레드 T-EV-1~T-EV-4(test/이벤트-성 5절, 기존 최대 T-62 다음 번호로), srs 절 7.13.22, 3.7.2, 3.7.3, 6.4.10, DFD 프로세스 7.9 이벤트 관리, 7.10 이벤트 조회, 7.11 이벤트 포인트 지급, 새 저장소 「이벤트 파일」(저장소 번호 D20 후보), 위험 메모 E1~E7(07-19 7.13.22.6, `/sw-plan`이 R 번호로), 리뷰 항목 R1~R9(05-24 10절, 설계 검토 R 번호와 별개라 병합 때 접두어를 정한다), 통합 시험 스레드 후보 T-EV-1~T-EV-3.

설계 결정 목록: D-성-1 목록은 앱 화면, 상세는 정적 웹 페이지 웹뷰 / D-성-2 nginx `location ^~ /events/`, 캐시 60초 / D-성-3 이벤트 종류 enum 둘과 config JSON / D-성-4 응모는 저장하지 않고 조회 때 계산, 기간 한국 시간 양 끝 포함 / D-성-5 셀럽 첫 승리 조회를 battle이 제공 / D-성-6 기록 거리는 running 기존 조회 / D-성-7 코치 승리 포인트는 BattleJudged 커밋 뒤 리스너, 실패는 로그만, 미지급 자가 보충 / D-성-8 이벤트 포인트 하루 상한 제외(AccrualReason.EVENT) / D-성-9 관리 API web.admin.event / D-성-10 감사는 SETTING_CHANGE 재사용(develop V37 audit 제약 때문) / D-성-11 V35_2 out-of-order와 시드 / D-성-12 웹 화면 보안(같은 출처 브리지, 다른 출처 차단) / D-성-13 로그인 정보 전달과 화면 열기 / D-성-14 이벤트 주소는 상대 경로만, 그림 직접 받기 / D-성-15 알림함 항목과 본 이벤트 기기 사본, 홈 점 칸 / D-성-16 패키지 배치 / D-성-17 코치 캐릭터 JOO, KIM.

## 공용 파일 반영

| 파일 | 넣을 내용 |
|------|-----------|
| code-review.md (공지 리뷰) | 머리 헤더 입력란에 "코드 b26b3317(공지 알림), 수정 62b7f7e8, code-review/공지알림-1차-성.md". 끝의 협업 조각 표에 행 `[공지알림-1차-성](code-review/공지알림-1차-성.md) | notification 공지, web.admin.notification, V35_3, push, 앱 알림함 공지, 계약 | 치명 0, 주요 1(수정), 경미 3(1 수정, 2 운영 절차), 제안 2, 합격 | 설계 05-25 D-성-22 갱신`. 임시 결함 번호 [임시:EV-성-18]~[임시:EV-성-23]을 정식 번호로. 버전 올림 |
| test.md (공지 리뷰, 시험 보강) | 머리 헤더 입력란에 "test/이벤트-성 9절 보강(2026-11-08, 2e05c391)". 이벤트 조각 집계를 자동 대응 103, 보강 필요 12로 |
| design-oo/05-25 (공지 리뷰, 작성자 조각이나 이번 커밋 밖) | [임시:D-성-22] 링크 경로 규칙을 「경로 조각 영문, 숫자, 「-」, 「_」만, forms.gle 조각 하나, docs.google.com /forms 아래 조각 하나 이상」으로. 8절 끝에 R10 확정(결정 11), R12 → EV-성-20, R13 → EV-성-23, R14 운영 절차 판정 문단 |
| srs/06-08 자료사전 (공지 리뷰) | 공지링크 주석에 경로 조각 규칙(07-20 7.13.23.4와 같게) |
| srs.md | 머리 헤더 입력란에 "2026-11-08 사용자 결정(이벤트), 이벤트 계약, 병합 대기 docs-S15P31A207-275-event-docs.md, 코드 hotfix 7558f6f4..d824b2a2(읽기만 함), 7.13.22". 문서 상태에 이벤트 요약 한 문단(새 CR 5, FR 13, NFR 3, C 1, 바뀐 FR 4). 목차 03-07 행 하위 절에 "3.7.2 이벤트, 3.7.3 이벤트 첫 등록 목록", 06-07 행에 6.4.10, 06-08 행에 "이벤트 자료", 07-11 행에 "이벤트 행", 새 행 07-19(7.13.22). 버전 올림 |
| design-oo.md | 머리 헤더 입력과 상태, 목차에 05-24 행, 버전 올림 |
| README.md | srs, design-oo 행의 버전과 요약, 조각 수(srs 40, design-oo +1) |
| srs/06-02-DFD-Level2.md | Level 2 프로세스 7 그림과 흐름 목록에 7.9~7.11과 흐름(07-19 7.13.22.4 표), 균형 점검 표에 새 흐름 |
| srs/06-01-DFD-구성-Level1.md | 프로세스 목록에 7.9~7.11(Level 2, 최하위), 프로세스 수(최하위 +3), 저장소 「이벤트 파일」, 배경도 흐름(러너 ↔ 이벤트 흐름 8개, 운영자 → 이벤트조회요청, 이벤트변경요청), Level 1 흐름 7 → 6 이벤트적립요청 |
| srs/06-06-소단위명세-5-7.md | 7.9, 7.10, 7.11 소단위 명세(07-19 7.13.22.4)와 6.4 보충(이벤트적립요청) |
| srs/07-01, 07-01b | FR-159, FR-170, FR-171, FR-176, FR-178 행 출처, 설계 열 보탬(07-11 "이벤트 행" 끝 문단) |
| srs/07-11 | 행 수 문단(FR, NFR 행 수), 임시 번호를 정식 번호로 |
| srs/07-03 | 미결 사항: 메모 E1(추첨 명단 절차), E2(당첨자 연락과 개인정보), E4(코치 기록 동의 입력) **(확인 필요)** |
| design-oo/B-E-변경-이력-색인-가정.md | 이력 한 줄(이벤트 기능, 05-24), D 색인 [임시:D-성-1]~[임시:D-성-17] → 정식 번호 |
| design-oo/06-06 계열 | 앱 요약에 ui.notification 이벤트 화면, api.notification 이벤트 포트, core.navigation 경로 RECORD_RUN_CELEB, EVENTS, EventDetail과 SlotKey.HOME_INBOX_DOT |
| design-oo/07-11-UML-다이어그램.md | 상태 표에 이벤트 진행상태 행, 시퀀스 목록에 [임시:SQ-성-1], [임시:SQ-성-2] |
| plan.md (`/sw-plan`) | 3.1 범위에 이벤트 추가, 하지 않는 일에 경품 추첨과 발송(운영 업무), 위험 E1, E2, E6, E7 |
| test.md (`/sw-test`) | (2026-11-08 sw-test 처리함: test/이벤트-성.md. 아래 「시험 추가」 행들로 옮김) 07-11 이벤트 행의 테스트 열. 코드 시험(EventServiceTest 8, CoachWinPointListenerTest 5, EventControllerTest 3, EventMigrationIntegrationTest 2, 앱 EventTest 10, EventRepositoryImplTest 5, EventScreenshotTest 4, InboxTest, DataStoreSettingsRepositoryTest)을 TC로 올리고 NFR 실기기 시험을 더한다 |
| 핫픽스 → develop 역병합 MR | 서버 ArchitectureTest 패키지 수 develop 68 + 4 = 72(PACKAGES 목록 양쪽 변경 합침), 허용 표에 event → member(응모자 명단) 포함, 앱 허용 쌍 160 확인, V35_2가 develop에서 out-of-order로 적용되는지 확인. **MR !90 병합 직후 바로 올리고, 그 전까지 develop, release 배포를 멈춘다**(공용 에지 nginx 설정을 덮어 prod /events/가 사라짐, [임시:EV-성-5]) |
| code-review.md (코드 리뷰 뒤 추가) | 머리 헤더 입력란에 "코드 hotfix/S15P31A207-275-v0114-coach-celeb origin/main...d824b2a2(MR !90 이벤트 1차), 수정 d221fd54, 2f99b573, 응모자 명단 d06641bb, code-review/이벤트-1차-성.md". 끝의 협업 조각 표에 행 `[이벤트-1차-성](code-review/이벤트-1차-성.md) | event, infra.event, web.event, web.admin.event, battle 경계, reward EVENT, V35_2, nginx /events/, 정적 페이지, 앱 이벤트 화면과 브리지 | 치명 0, 주요 2(1 수정, 1 배포 절차), 경미 10(7 수정), 제안 4, 확인 필요 1, 합격(조건부 배포) | 설계 05-24 D-성-9 갱신, 새 D-성-18`. 임시 결함 번호 [임시:EV-성-1]~[임시:EV-성-17]을 정식 번호로(다음 PL 계열). 버전 올림 |
| srs/07-03 (코드 리뷰 뒤 추가) | 위 07-03 행에서 E1은 「관리 API 명단([임시:FR-성-14]) 확정, 기간 종료 직후 명단 보관 절차만 남음」으로, E2는 「앱 알림 + 구글 폼 확정, 구글 폼 연락처 수집 동의와 처리방침, 운영자 알림 발송 기능 없음」으로 바꾼다 |
| srs/07-11 (코드 리뷰 뒤 추가) | 이벤트 행에 [임시:FR-성-14] 1행(설계 [임시:D-성-18], 코드 EventEntrantService, EventAdminController.entrants, 시험 EventEntrantServiceTest 3, EventControllerTest entrants_listsWinnersForDraw) |
| srs/06-02, 06-06 (코드 리뷰 뒤 추가) | 7.9 이벤트 관리에 응모자명단요청 → 응모자명단 흐름(입력 대결레코드, 러닝기록, 회원), 소단위 명세 한 단락 |
| srs/06-08 (코드 리뷰 뒤 추가) | 이벤트 자료에 응모자명단요청, 응모자명단(= 이벤트ID + 주소이름 + 이벤트종류 + (셀럽기록ID) + 전체응모자수 + {응모자} + (다음커서)), 응모자(= 회원ID + 닉네임 + 응모수 + {응모항목}) |
| design-oo/A-추적표 (코드 리뷰 뒤 추가) | A.1에 [임시:FR-성-14] → [임시:D-성-18], EventEntrantService, EventAdminController.entrants 1행 |
| design-oo/05-03 (코드 리뷰 뒤 추가) | 5.7.1 API 표에 `GET /admin/events/{id}/entrants`(운영자, 쿼리 celebrityId, cursor, size, 응답 EventEntrants, 오류 400, 404 EVENT_NOT_FOUND) 1행 |
| design-oo/12-의존도 (코드 리뷰 뒤 추가) | 12.4 이벤트 문단에 event → member(MemberQueryService.activeNicknames, 데이터 결합) 화살표 |
| design-oo/06-01-클래스-common-member.md (이성현) | MemberQueryService.activeNicknames(Collection<Long>): Map<Long, String>(ACTIVE만), MemberRepository.activeNicknames. 소비자 event.EventEntrantService |
| test.md (`/sw-test`, 코드 리뷰 뒤 추가) | (2026-11-08 sw-test 처리함: test/이벤트-성.md 4절 화이트박스, 2, 3, 5절 대응, 7절 TC-성-131, TC-성-132) code-review/이벤트-1차-성 8절 우선 시험 대상, 새 시험(EventServiceTest 8, CoachWinPointListenerTest 6, EventEntrantServiceTest 3, EventControllerTest 5, JpaRunningRepositoriesIntegrationTest 보탬, 앱 EventTest 12), 실기기: CSP 아래 두 페이지 표시 |
| srs/07-11 「이벤트 행」 테스트 케이스 열 (시험 추가) | 빈 테스트 열을 채운다(임시 번호는 병합 때 정식 TC 번호로). [임시:FR-성-1] → [임시:TC-성-1]~[임시:TC-성-5], [임시:TC-성-24], [임시:TC-성-60], [임시:TC-성-61], [임시:TC-성-66]~[임시:TC-성-73], [임시:TC-성-91]~[임시:TC-성-93], [임시:TC-성-95], [임시:TC-성-96], [임시:TC-성-98], [임시:TC-성-117]. [임시:FR-성-2] → [임시:TC-성-74]~[임시:TC-성-76], [임시:TC-성-94], [임시:TC-성-117]. [임시:FR-성-3] → [임시:TC-성-63], [임시:TC-성-65], [임시:TC-성-66], [임시:TC-성-68], [임시:TC-성-74], [임시:TC-성-76]~[임시:TC-성-79], [임시:TC-성-94]. [임시:FR-성-4] → [임시:TC-성-69], [임시:TC-성-80], [임시:TC-성-81], [임시:TC-성-95], [임시:TC-성-122]~[임시:TC-성-124], [임시:TC-성-136]. [임시:FR-성-5] → [임시:TC-성-25], [임시:TC-성-26], [임시:TC-성-62], [임시:TC-성-80], [임시:TC-성-97], [임시:TC-성-99], [임시:TC-성-122], [임시:TC-성-124]. [임시:FR-성-6] → [임시:TC-성-5], [임시:TC-성-26]~[임시:TC-성-30], [임시:TC-성-99], [임시:TC-성-117], [임시:TC-성-138]. [임시:FR-성-7] → [임시:TC-성-5], [임시:TC-성-15], [임시:TC-성-31]~[임시:TC-성-38], [임시:TC-성-111], [임시:TC-성-118], [임시:TC-성-119]. [임시:FR-성-8] → [임시:TC-성-39]~[임시:TC-성-51], [임시:TC-성-100], [임시:TC-성-112], [임시:TC-성-117]. [임시:FR-성-9] → [임시:TC-성-23], [임시:TC-성-101]. [임시:FR-성-10] → [임시:TC-성-6]~[임시:TC-성-19], [임시:TC-성-96], [임시:TC-성-101]~[임시:TC-성-105]. [임시:FR-성-11] → [임시:TC-성-19]~[임시:TC-성-21], [임시:TC-성-101], [임시:TC-성-121]. [임시:FR-성-12] → [임시:TC-성-19], [임시:TC-성-22], [임시:TC-성-25], [임시:TC-성-47], [임시:TC-성-48], [임시:TC-성-97], [임시:TC-성-101]. [임시:FR-성-13] → [임시:TC-성-84], [임시:TC-성-89], [임시:TC-성-90], [임시:TC-성-95], [임시:TC-성-117], [임시:TC-성-118], [임시:TC-성-120], [임시:TC-성-125], [임시:TC-성-126]. [임시:FR-성-14](코드 리뷰 뒤 추가 행) → [임시:TC-성-52]~[임시:TC-성-59], [임시:TC-성-106]~[임시:TC-성-111], [임시:TC-성-138]. [임시:NFR-성-1] → [임시:TC-성-82]~[임시:TC-성-87], [임시:TC-성-127], [임시:TC-성-128]. [임시:NFR-성-2] → [임시:TC-성-61], [임시:TC-성-64], [임시:TC-성-88], [임시:TC-성-129]~[임시:TC-성-132]. [임시:NFR-성-3] → [임시:TC-성-131], [임시:TC-성-133], [임시:TC-성-134], [임시:TC-성-137]. 「바뀐 기존 행」 문단: FR-159 테스트 +[임시:TC-성-41], [임시:TC-성-42], FR-176 +[임시:TC-성-19], [임시:TC-성-121], FR-178 +[임시:TC-성-74], [임시:TC-성-94], [임시:TC-성-95]. 제약 [임시:C-성-1]은 07-11에 행이 없으므로 test/06 추적표에만 [임시:TC-성-132], [임시:TC-성-135]를 적는다 |
| test.md (시험 추가) | 머리 헤더 입력란에 "test/이벤트-성.md(이성현 2026-11-08, 핫픽스 0.11.4 이벤트, srs 3.7.2, design-oo 05-24, code-review/이벤트-1차-성, 코드 hotfix d06641bb 시험 재실행)". 문서 상태에 한 문단: 이벤트 138개(자동 대응 93: 전부 84, 일부 9. 자동 시험 없음 22. 수동 23), 계획 완성, 새 FR 14, NFR 3, C 1 미커버 0, EventConfig.parse V(G) 12 리팩터링 권고, 실기기 체크리스트 15항목 미실행. 목차 표에 행 `[이벤트-성](test/이벤트-성.md) | 이벤트 시험(이성현, S15P31A207-275, 2026-11-08, hotfix d06641bb) | TC-성-1~138 정식 번호(138) | 계획 완성. 자동 대응분 재실행 통과, 보강 필요 22, 수동 23 미실행`. 1장 수준표 TC 수에 단위 95, 통합 21, 검증 10, 시스템 12를 더한다. 버전 올림 |
| test/01-계획.md (시험 추가) | 번호 대역 표에 이벤트 138개의 정식 대역을 정한다(병합 때 남은 예비 대역에서). 통합 스레드 목록에 T-EV-1~T-EV-4를 정식 T 번호로 |
| test/06-추적표.md (시험 추가) | 끝에 「이벤트 행」 표를 더한다. 열은 요구 ID, 설계 요소, TC(위 srs/07-11 행과 같은 대응) 그리고 [임시:C-성-1] → [임시:TC-성-132], [임시:TC-성-135] 1행. TC 수 집계 문단에 138을 더한다 |
| test 기존 조각 (시험 추가, test/이벤트-성 12절) | TC-6535(FR-178 Routes 목록) 기대 결과에 EVENTS, RECORD_RUN_CELEB, EventDetail을 더한다. TC-5683(앱 구조) 폰 159 → 160. TC-9856(서버 의존 구조)은 develop 역병합 MR에서 패키지 수 72로. 모두 "(다음 판 변경)" |
| code-review(다음 리뷰, 시험 추가) | test/이벤트-성 4.1: EventConfig.parse V(G) 12(10 초과) 리팩터링 권고(종류별 검사 메서드 분리), 독립 경로 B3(enum 기본 갈래) 도달 불가. 4.2: CoachWinPointListener.grant의 dues == null 갈래 도달 불가. 9절 보강 필요 22건 가운데 우선 1~3(sumOn EVENT 제외, 동시 판정, battle 질의 조건)은 다음 코드 MR에서 시험부터 쓴다 |
| srs.md (공지 알림) | 머리 헤더 입력란에 「2026-11-08 사용자 결정(운영자 공지 알림), 코드 b26b3317」, 문서 상태에 공지 한 문단(새 CR 1, FR 2, 바뀐 FR 5), 목차 03-07 행 하위 절에 3.7.4, 새 행 07-20(7.13.23) |
| design-oo.md, README.md (공지 알림) | 목차에 05-25 행, 조각 수(srs +1, design-oo +1) |
| srs/06-01, 06-02 (공지 알림) | Level 2 프로세스 7에 7.12 공지 발송(운영자 → 공지발송요청, D1, D13 → 7.12, 7.12 → 7.3 알림사건, → D14 감사기록), 배경도 운영자 → 공지발송요청, 균형 표 |
| srs/06-06 (공지 알림) | 7.12 소단위 명세와 7.3 보충(공지는 수신 설정을 보지 않고 푸시요청에 알림제목, 07-20 7.13.23.4) |
| srs/06-08 (공지 알림) | 「이벤트 자료」 뒤에 07-20 7.13.23.4의 새 항목 7개(공지발송요청, 수신회원ID목록, 공지제목, 공지본문, 공지링크, 알림제목, 공지발송결과) |
| srs/07-11, 07-01 (공지 알림) | [임시:FR-성-15](출처 2026-11-08 사용자 결정, DFD 7.12, 설계 [임시:D-성-19]~[임시:D-성-25], NoticeService, NoticeAdminController), [임시:FR-성-16](DFD 7.6, 7.3, 설계 [임시:D-성-26], InboxViewModel, inboxLink) 2행, FR-025, FR-175, FR-176, FR-178 출처에 [임시:CR-성-6] |
| srs/07-03 (공지 알림) | 미결: 메모 N1(공지가 마케팅 동의를 보지 않는 판단과 처리방침 문구, PM) **(확인 필요)** |
| design-oo/05-03 (공지 알림) | 5.7.1 API 표에 `POST /admin/notifications/notices`(운영자, NoticeResult, 400, 403, 409 VALUE_INVALID) 1행, GET /notifications 항목에 title |
| design-oo/12-의존도 (공지 알림) | 새 패키지 web.admin.notification(서버 70개, develop 병합 때 73), 화살표 web.admin.notification → notification, notification → audit |
| design-oo/A-추적표, B-E, 07-11 UML (공지 알림) | A.1에 FR-성-15, FR-성-16 행, D 색인 [임시:D-성-19]~[임시:D-성-26], 시퀀스 목록 [임시:SQ-성-3] |
| test.md (`/sw-test`, 공지 알림) | 05-25 7절 시험(NoticeServiceTest 6, NoticeAdminControllerTest 3, NoticeMigrationIntegrationTest 1, FcmPushGatewayTest +1, 앱 InboxTest +3, AvatarAndRunimalAndNotificationTest +1)을 TC로, 실기기(0.11.4 기기 알림 제목과 링크 열기, 0.11.3 「알림」 표시) |
| 핫픽스 → develop 역병합 MR (공지 알림) | ArchitectureTest PACKAGES에 web.admin.notification 포함 73개, 허용 표 notification → audit, V35_3이 develop에서 out-of-order로 적용되는지, application.yml out-of-order 주석(35.1~35.3) |

## 다른 담당에게 요청

- (공지 알림) 없음. 알림 요구(03-07 공용), 알림 설정과 마케팅 동의(03-01), notification 클래스(design-oo 06-05)가 모두 이성현 담당이다.

- 박재영: srs/03-04-대결-관리.md FR-093(대결 결과 표시) 처리 끝에 "셀럽런 승리로 받는 이벤트 포인트([임시:FR-성-8])는 이 화면의 적립 합계 줄에 보이지 않는다(다음 판 변경, [임시:CR-성-3])"를 더한다.
- 박재영: srs/03-04-대결-관리.md FR-099(셀럽런 진행과 판정) 처리 끝에 "셀럽런 판정(승자, 상대 유명인 기록, 판정일시)은 이벤트의 셀럽 승리 응모([임시:FR-성-6])와 이벤트 포인트([임시:FR-성-8])가 읽는다. 판정 규칙은 바뀌지 않는다(다음 판 변경, [임시:CR-성-3])"를 더한다.
- 박재영: design-oo/06-03-클래스-battle.md에 battle 경계 계약 추가를 올린다. `BattleHistoryService.celebrityChallenges(long memberId, List<Long> celebrityIds, LocalDateTime from, LocalDateTime to): List<CelebrityChallenge>`(readOnly, celebrityIds 순서, name = CelebrityRecord.getAnonymousName() 또는 null), `BattleRepository.findFirstCelebrityWins(long memberId, Collection<Long> celebrityIds, LocalDateTime from, LocalDateTime to): Map<Long, LocalDateTime>`(FINISHED, CELEBRITY, opponentKind CELEBRITY, winnerMemberId, opponentRecordId in ids, judgedAt ∈ [from, to], 기록 ID별 min(judgedAt), 빈 ids면 빈 맵, JpaBattleRepository 구현), 새 값 `record CelebrityChallenge(long celebrityId, String name, LocalDateTime firstWonAt)`. 소비자는 event.EventService, event.CoachWinPointListener다. 이 경계 계약은 쓰는 쪽(이성현)이 코드로 먼저 넣었으므로 MR !90에서 승인하거나 고칠 점을 남긴다(OWNERS 3절 5).
- 박재영: CoachWinPointListener가 BattleJudged(AFTER_COMMIT)를 듣는다. BattleJudged의 필드(type, winnerSide, battleId, judgedAt)를 바꿀 때 이 리스너도 함께 본다.
- 박재영(코드 리뷰 뒤 추가): design-oo/06-03-클래스-battle.md에 응모자 명단용 battle 경계 계약을 더 올리고 MR !90에서 승인한다. `BattleHistoryService.celebrityWinners(List<Long> celebrityIds, LocalDateTime from, LocalDateTime to): List<CelebrityWin>`(readOnly), `BattleHistoryService.celebrityNames(List<Long> celebrityIds): Map<Long, String>`(지워진 기록은 빠짐), `BattleRepository.findCelebrityWinners(Collection<Long> celebrityIds, LocalDateTime from, LocalDateTime to): List<CelebrityWin>`(FINISHED, CELEBRITY, opponentKind CELEBRITY, winnerMemberId not null, opponentRecordId in ids, judgedAt ∈ [from, to], (회원, 기록)별 min(judgedAt), 회원 ID, 기록 ID 순, 빈 ids면 빈 목록), 새 값 `record CelebrityWin(long memberId, long celebrityId, LocalDateTime firstWonAt)`. 소비자 event.EventEntrantService.
- 박재영(코드 리뷰 뒤 추가): design-oo/06-02-클래스-running-analysis-profile.md에 running 경계 계약을 올리고 승인한다. `RunQueryService.earliestValidRunsAtLeast(long minMeters, LocalDateTime from, LocalDateTime to): Map<Long, RunSummary>`(회원 ID 순, 회원마다 가장 이른 것), `RunRecordRepository.validSummariesAtLeast(long minMeters, LocalDateTime from, LocalDateTime to): List<RunSummary>`(모든 회원, valid, distanceMeters ≥ min, startedAt ∈ [from, to], 시작 시각, 기록 ID 순). 소비자 event.EventEntrantService.

## 부록: 이벤트 계약 요약 (2026-11-08, hotfix 0.11.4)

이벤트 두 개(V35_2 시드): coach-challenge(COACH_CELEB, `{"celebrityIds":[11,12],"pointPerWin":500}`, sort 1, 그림과 상세 `/events/coach-challenge/…`), beta-1km(RUN_DISTANCE, `{"minDistanceMeters":1000}`, sort 2, `/events/beta-1km/…`). 둘 다 2026-11-08T00:00:00 ~ 2026-11-16T23:59:59, published.

앱 API(회원 인증, `/api/v1`): `GET /events` → `{events:[{id, slug, title, subtitle, status(UPCOMING|ONGOING|ENDED), startsAt, endsAt, bannerImageUrl, thumbnailImageUrl, detailUrl, entryCount, maxEntryCount, sortOrder}]}`(공개만, ONGOING, UPCOMING, ENDED 순, 같은 상태는 sortOrder), `GET /events/{slug}/me` → `{slug, entryCount, maxEntryCount, items:[{key, label, done, doneAt, distanceMeters}]}`(없거나 비공개 404 EVENT_NOT_FOUND). URL 셋은 서버 상대 경로이고 앱이 API origin에 붙인다.

관리 API(운영자, `/api/v1/admin/events`): GET 목록(비공개 포함), POST 생성(201), PUT `/{id}` 수정, PUT `/{id}/published` 공개 전환. 필드 slug, title, subtitle, kind, config, startsAt, endsAt, bannerImageUrl, thumbnailImageUrl, detailUrl, sortOrder, published. 규칙 위반 409 VALUE_INVALID, 감사 SETTING_CHANGE 「EVENT:slug」.

웹 페이지: `infra/nginx/static/events/{slug}/index.html`과 그림, nginx `location ^~ /events/`(api로 넘기지 않음, Cache-Control public, max-age=60). 앱은 상세 주소에 `?theme=light|dark`를 붙인다.

브리지(window.RunkkApp, 페이지 주 문서 origin = API origin일 때만): `getAccessToken()`(페이지가 `fetch('/api/v1/events/{slug}/me', {headers:{Authorization:'Bearer '+token}})`, 실패면 내 응모 영역만 숨김), `open("celebrity"|"run"|"events")`(셀럽런 목록, 자유런 시작 준비, 이벤트 목록), `close()`, `share(text)`. 브라우저로 열면 앱 단추는 비활성.

피그마: 파일 NMTtVMWIT22D0CwOU2y0I9, 섹션 62206:1003. 홈 62206:1008, 알림함 62209:1274, 이벤트 모아보기 62214:1243(Dark 62218:1243), 코치님 상세 62216:1243(Dark 62218:1319), 베타 상세 62217:1243(Dark 62218:1419), 셀럽런 목록 62206:1123. 베타 상세는 피그마의 「세 번 달리기」를 「1km 한 번」으로, 보상 배지는 빼고 포인트와 추첨 경품으로 바꿨다.

산책 코스(beta-1km 상세): 출발 멀티캠퍼스 역삼(서울 강남구 테헤란로 212) 앞 테헤란로 남쪽 보도 (37.50140, 127.03920). 지도는 OpenStreetMap 자료라 「지도 자료 © OpenStreetMap 기여자」 표기 필수.

- C 한 바퀴 1.1km(테헤란로 동쪽 → 테헤란로38길 남쪽 → 언주로85길, 논현로86길 서쪽 → 논현로 북쪽(역삼역) → 테헤란로 동쪽으로 출발점): (37.50140,127.03920) (37.50161,127.03988) (37.50165,127.04004) (37.50180,127.04054) (37.50204,127.04125) (37.50193,127.04131) (37.50175,127.04140) (37.50169,127.04144) (37.50143,127.04157) (37.50136,127.04160) (37.50102,127.04178) (37.50088,127.04134) (37.50058,127.04039) (37.50042,127.03991) (37.50027,127.03942) (37.50013,127.03899) (37.50005,127.03891) (37.49968,127.03856) (37.49954,127.03809) (37.49940,127.03760) (37.50071,127.03699) (37.50075,127.03712) (37.50079,127.03724) (37.50092,127.03768) (37.50117,127.03847) (37.50140,127.03920)
- D 선릉역 방향 550m 갔다 오기 1.1km(테헤란로 남쪽 보도, 언주로 사거리 한 번 건넘): (37.50140,127.03920) (37.50161,127.03988) (37.50165,127.04004) (37.50180,127.04054) (37.50204,127.04125) (37.50236,127.04234) (37.50245,127.04263) (37.50269,127.04343) (37.50300,127.04442) (37.50319,127.04501)=반환점
- E 강남역 방향 550m 갔다 오기 1.1km(테헤란로 남쪽 보도, 역삼역 사거리(논현로) 한 번 건넘): (37.50140,127.03920) (37.50117,127.03847) (37.50092,127.03768) (37.50079,127.03724) (37.50071,127.03699) (37.49963,127.03338)=반환점

운영 자료: 운영 DB 유명인 기록 ID 11(주세빈, 셀럽캐릭터키 joo, 5km 30:18, 출처 런크크), 12(김선교, kim, 5km 30:00, 런크크). 앱 CelebrityCharacter.JOO, KIM.

## 코드와 문서가 어긋나 보이는 점 (MR !90 코드 리뷰에서 볼 것)

design-oo/05-24 10절 R1~R9의 요약이다. 코드 리뷰 판정은 위 「코드 리뷰」 절과 05-24 10절 끝 문단이다(R1, R2, R7 수정, R6 사용자 결정으로 구현, R3, R5, R8 남김, R4 역병합 확인, R9 운영 DB 확인).

1. 관리 API는 https 절대 주소를 받는데 앱은 「/」 상대 경로만 연다(https로 넣으면 그림이 비고 카드가 안 열림).
2. 포인트 지급 이력 저장이 실패하면(동시 판정 등) 적립은 됐는데 event_point_grant 행이 끝내 안 생길 수 있다. 다음 판정에서 accrueEvent가 0이면 그냥 넘어가기 때문이다.
3. `GET /events`가 이벤트마다 응모를 다시 세고, 홈 점과 알림함이 화면 재개마다 부른다(지금 두 개라 수용).
4. 서버 ArchitectureTest 패키지 수 69는 main 기준, develop 병합 때 72.
5. 감사 행위 SETTING_CHANGE 재사용으로 감사 조회에서 기준값 변경과 섞인다.
6. 추첨 명단을 뽑는 관리 기능이 없다(응모를 저장하지 않음).
7. nginx `/events/`에 콘텐츠 보안 정책 헤더가 없다(페이지가 로그인 정보를 받음).
8. 상세 페이지의 코치 이름, 기록, 경품, 날짜 글자가 HTML에 고정되어 관리 API 설정과 어긋날 수 있다.
9. 코치 응모 항목 label이 유명인 기록의 가림 이름 열 값이다. 운영 DB 11, 12에 무엇을 넣었는지 확인(「주세빈 코치님」인지 「주세빈」인지).
