# 병합 대기: docs/S15P31A207-275-event-docs

> 작성: 2026-11-08 | 작성자: 이성현 | 스킬: sw-srs, sw-design-oo

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

## 고친 조각

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

번호 아닌 새 식별자(병합 때 확인): srs 절 7.13.22, 3.7.2, 3.7.3, 6.4.10, DFD 프로세스 7.9 이벤트 관리, 7.10 이벤트 조회, 7.11 이벤트 포인트 지급, 새 저장소 「이벤트 파일」(저장소 번호 D20 후보), 위험 메모 E1~E7(07-19 7.13.22.6, `/sw-plan`이 R 번호로), 리뷰 항목 R1~R9(05-24 10절, 설계 검토 R 번호와 별개라 병합 때 접두어를 정한다), 통합 시험 스레드 후보 T-EV-1~T-EV-3.

설계 결정 목록: D-성-1 목록은 앱 화면, 상세는 정적 웹 페이지 웹뷰 / D-성-2 nginx `location ^~ /events/`, 캐시 60초 / D-성-3 이벤트 종류 enum 둘과 config JSON / D-성-4 응모는 저장하지 않고 조회 때 계산, 기간 한국 시간 양 끝 포함 / D-성-5 셀럽 첫 승리 조회를 battle이 제공 / D-성-6 기록 거리는 running 기존 조회 / D-성-7 코치 승리 포인트는 BattleJudged 커밋 뒤 리스너, 실패는 로그만, 미지급 자가 보충 / D-성-8 이벤트 포인트 하루 상한 제외(AccrualReason.EVENT) / D-성-9 관리 API web.admin.event / D-성-10 감사는 SETTING_CHANGE 재사용(develop V37 audit 제약 때문) / D-성-11 V35_2 out-of-order와 시드 / D-성-12 웹 화면 보안(같은 출처 브리지, 다른 출처 차단) / D-성-13 로그인 정보 전달과 화면 열기 / D-성-14 이벤트 주소는 상대 경로만, 그림 직접 받기 / D-성-15 알림함 항목과 본 이벤트 기기 사본, 홈 점 칸 / D-성-16 패키지 배치 / D-성-17 코치 캐릭터 JOO, KIM.

## 공용 파일 반영

| 파일 | 넣을 내용 |
|------|-----------|
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
| test.md (`/sw-test`) | 07-11 이벤트 행의 테스트 열. 코드 시험(EventServiceTest 8, CoachWinPointListenerTest 5, EventControllerTest 3, EventMigrationIntegrationTest 2, 앱 EventTest 10, EventRepositoryImplTest 5, EventScreenshotTest 4, InboxTest, DataStoreSettingsRepositoryTest)을 TC로 올리고 NFR 실기기 시험을 더한다 |
| 핫픽스 → develop 역병합 MR | 서버 ArchitectureTest 패키지 수 develop 68 + 4 = 72(PACKAGES 목록 양쪽 변경 합침), 앱 허용 쌍 160 확인, V35_2가 develop에서 out-of-order로 적용되는지 확인 |

## 다른 담당에게 요청

- 박재영: srs/03-04-대결-관리.md FR-093(대결 결과 표시) 처리 끝에 "셀럽런 승리로 받는 이벤트 포인트([임시:FR-성-8])는 이 화면의 적립 합계 줄에 보이지 않는다(다음 판 변경, [임시:CR-성-3])"를 더한다.
- 박재영: srs/03-04-대결-관리.md FR-099(셀럽런 진행과 판정) 처리 끝에 "셀럽런 판정(승자, 상대 유명인 기록, 판정일시)은 이벤트의 셀럽 승리 응모([임시:FR-성-6])와 이벤트 포인트([임시:FR-성-8])가 읽는다. 판정 규칙은 바뀌지 않는다(다음 판 변경, [임시:CR-성-3])"를 더한다.
- 박재영: design-oo/06-03-클래스-battle.md에 battle 경계 계약 추가를 올린다. `BattleHistoryService.celebrityChallenges(long memberId, List<Long> celebrityIds, LocalDateTime from, LocalDateTime to): List<CelebrityChallenge>`(readOnly, celebrityIds 순서, name = CelebrityRecord.getAnonymousName() 또는 null), `BattleRepository.findFirstCelebrityWins(long memberId, Collection<Long> celebrityIds, LocalDateTime from, LocalDateTime to): Map<Long, LocalDateTime>`(FINISHED, CELEBRITY, opponentKind CELEBRITY, winnerMemberId, opponentRecordId in ids, judgedAt ∈ [from, to], 기록 ID별 min(judgedAt), 빈 ids면 빈 맵, JpaBattleRepository 구현), 새 값 `record CelebrityChallenge(long celebrityId, String name, LocalDateTime firstWonAt)`. 소비자는 event.EventService, event.CoachWinPointListener다. 이 경계 계약은 쓰는 쪽(이성현)이 코드로 먼저 넣었으므로 MR !90에서 승인하거나 고칠 점을 남긴다(OWNERS 3절 5).
- 박재영: CoachWinPointListener가 BattleJudged(AFTER_COMMIT)를 듣는다. BattleJudged의 필드(type, winnerSide, battleId, judgedAt)를 바꿀 때 이 리스너도 함께 본다.

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

design-oo/05-24 10절 R1~R9의 요약이다.

1. 관리 API는 https 절대 주소를 받는데 앱은 「/」 상대 경로만 연다(https로 넣으면 그림이 비고 카드가 안 열림).
2. 포인트 지급 이력 저장이 실패하면(동시 판정 등) 적립은 됐는데 event_point_grant 행이 끝내 안 생길 수 있다. 다음 판정에서 accrueEvent가 0이면 그냥 넘어가기 때문이다.
3. `GET /events`가 이벤트마다 응모를 다시 세고, 홈 점과 알림함이 화면 재개마다 부른다(지금 두 개라 수용).
4. 서버 ArchitectureTest 패키지 수 69는 main 기준, develop 병합 때 72.
5. 감사 행위 SETTING_CHANGE 재사용으로 감사 조회에서 기준값 변경과 섞인다.
6. 추첨 명단을 뽑는 관리 기능이 없다(응모를 저장하지 않음).
7. nginx `/events/`에 콘텐츠 보안 정책 헤더가 없다(페이지가 로그인 정보를 받음).
8. 상세 페이지의 코치 이름, 기록, 경품, 날짜 글자가 HTML에 고정되어 관리 API 설정과 어긋날 수 있다.
9. 코치 응모 항목 label이 유명인 기록의 가림 이름 열 값이다. 운영 DB 11, 12에 무엇을 넣었는지 확인(「주세빈 코치님」인지 「주세빈」인지).
