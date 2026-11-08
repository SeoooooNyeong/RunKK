> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 이전: [01d-backend-리뷰-기준](01d-backend-리뷰-기준.md) · 다음: [02-android](02-android.md)
### 4. 객체지향 구현과 동시성·보안 점검

| 점검 | 결과 |
|------|------|
| 캡슐화 | public 필드·검사 없는 setter 없음. 새 record(PostHeader·GoalState·Slot·Content)는 불변 값이다. `Slot`은 `protected record`라 서브클래스(같은 패키지)에서만 만든다 |
| ChatPresenceRegistry 상태 | 세션별 확인 표(`checked`)는 세션이 닫힐 때 `leaveAll`에서 지운다. 정상 닫힘에서 누수 없음. 닫는 스레드와 처리 중인 프레임이 겹치면 닫은 뒤 `markChecked`·`enter`가 실행될 수 있고(이미 있던 `enter` 경쟁에 새 표가 더해짐), 한 세션당 최대 10개 항목이라 영향은 작다. 별도 결함으로 세지 않고 BE6-4에 포함해 본다. `leave(crewId, memberId)`가 같은 회원의 다른 세션 입장까지 지우는 것은 5차 이전부터의 동작이다 |
| 리액션 제한 | 회원 키 문자열로 AttemptLimiter(창 1초, 10건). 확인과 기록이 한 번에라 동시 요청도 한도를 못 넘는다. 멱등 반복(이미 달린 리액션)도 한 건으로 센다. 보내기 한도와 따로 센다. 보내기·리액션 둘 다 서비스 호출 전에 걸려 검증 실패도 소비한다(의도된 보수) |
| 배경 채우기 경합 | 판정 순서: 해금 행 없는 회원만 → 배포 전 거리로 채움(알림 없음) → 그 뒤 거리는 리스너·보정이 알림과 함께. 잠금(`findForUpdate`)과 재시도 3회는 그대로이고, 두 경로가 같은 랜드마크를 놓고 겹쳐도 `Avatar.unlock`이 새로 넣은 것만 돌려줘 알림은 한 번이다. 남은 위험은 BE6-6(기준 값 관리)뿐 |
| deploy.sh 보안 | 비밀번호가 명령줄·로그에 나오지 않는다(소켓 접속, `.env` 값은 `DB_USER`·`DB_NAME`만 읽음). `set -euo pipefail`이라 pg_dump 실패가 gzip 성공에 가려지지 않는다(확인함). 실패하면 부분 파일을 지우고 중단한다. 보존 정리의 `ls | tail | xargs rm`은 이름 규칙이 고정이라 안전하다. 문제는 BE6-1~3 |
| wiki.conf.template(develop 병합) | 80은 인증서 챌린지만 열고 301, 443은 `/console`·`/pgadmin/`·`/minio/`·`/gotrue/admin`을 404로 막는다. 변수 `proxy_pass`와 `resolver 127.0.0.11`(default.conf 전역)으로 AppFlowy 스택이 없어도 nginx는 뜬다. 주석대로 인증서 `appflowy-interim/`이 볼륨에 없으면 런크크 nginx 전체가 못 뜬다는 결합은 문서화돼 있다(경 참고, 결함으로 세지 않음). `client_max_body_size 2g`·`proxy_request_buffering off`는 위키 도메인에만 걸린다(런크크 server는 별도). 런크크 API 보안 설정에는 영향 없음 |
| 4401 만료·게스트 자격 증명·시도 제한 | 5차 점검 그대로(이번 범위에서 변경 없음) |

### 5. 시험 품질

변경 시험 20파일을 읽었다. 5차 결함마다 시험이 붙은 것: A-4(BackgroundServiceTest·TC-6763 강화), A-5(GuestServicesTest), A-2(ChatRealtimeTest 둘), B-1(CrewChatServiceTest), B-18(ChatRateGuardTest·ChatControllerTest), A-14(BootstrapConfigTest). 시그니처 변경은 시험을 따라 고쳤다(생성자 인수 4개, 6번째 `RelayEntry` 인수 등). 시험이 없는 것: A-13(BE6-8), A-15 deploy.sh(BE6-9), 새 JPQL(BE6-8). 시험 이름에 완전한 이름(`new com.a207.runkk.avatar.BackfillProperties(java.time.LocalDateTime.of(...))`, AvatarServicesTest:103)이 남은 것은 스타일 경 한 줄이라 따로 세지 않는다.

### 6. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 5차와 같다(설계 64패키지 + ArchitectureTest) |
| 버전 제어 | 예(부분) | 이력·통합 브랜치 있음. 태그 0개, `backend/build.gradle.kts:10` version `0.0.1-SNAPSHOT` 그대로(참고) |
| 변경 통제 | 아니오 | 범위 서버 커밋 26개 중 Jira 키 0개(`feat/fix … (BE5-A-n, D-nnn)`로 결함 ID는 모두 연결됨). N-14 보류(U-04)라 판정 요인에서 뺀다. 5차 결함 ID가 제목·본문에 있어 변경 요청 추적은 가능하다 |
| 형상 감사 | 예 | 기준 4ee91aef 대비 diff 재현. 설계 갱신 항목은 D-167~D-189로 설계서에 반영돼 있다 |
| 상태 보고 | 예(경 지적) | 제목에 타입 접두·결함 ID가 있다. 지적: (1) `merge:` 타입 3개(a27c914f·4844039a·82e172f7)와 GitLab 기본 병합 1개(4f51b203)가 AGENTS.md 타입 목록 밖(5차 B-22 일부와 같은 항목). (2) 한 커밋에 두 가지 일이 든 것이 6b3a8104(권한표 삭제 판정 + PostSummary 머리를 PostHeader로)와 136fcca1(배경 채우기 한정 + 선택 두 연산). 앞서 a358111e가 이미 머리를 묶었는데 6b3a8104가 다시 이름을 바꿔 같은 일이 두 번 나온다. fb2f4722(초당 5회 제한)는 2커밋 뒤 77a2fbcb에 대체돼 이력만 늘었다 |

### 7. 집계와 판정

| 구분 | 치명 | 중 | 경 | 구성 |
|------|------|----|----|------|
| 새 결함(BE6-1~9) | 0 | 1 | 8 | 중 BE6-1 / 경 BE6-2~9 |
| 5차 이월(미해결·일부·보류) | 0 | 0 | 17 | 미해결 11(A-6·9·10, B-8·9·13·14·15·17·19·20), 일부 4(B-12·16·21·22), 보류 2(A-16·17) |
| 합계 | 0 | 1 | 25 | 4차 이월 경(L4·L23·L25·L26 결정 대기 등)은 변동 없어 위 합계에 넣지 않는다 |

판정: **합격**
근거: 치명 0, 중 1(3 이하). 5차 중 7건은 모두 해결됐고 설계 일치(D-167·D-169(일부 코드 정리 남음)·D-173·D-175·D-176·D-177·D-188) 점검을 수행했으며 새 의존 화살표가 없다. 형상 관리는 변경 통제(Jira 키)만 아니오이며 N-14 보류로 판정 요인에서 뺐다(4·5차와 같다). 다만 BE6-1(백업이 다음 CI 잡에서 지워질 수 있음)은 5차 BE5-A-15를 사실상 되돌릴 수 있는 안전장치 결함이라 운영 DB에 MR을 배포하기 전에 고치는 것을 권한다.
이월 경 25건은 구조 정리(CrewPostService 분할·instanceof·N+1)와 정책 결정(B-20)이 대부분이며 MR !14를 막지 않는다.

### 8. sw-test 전달: 우선 테스트 대상

복잡도 10 초과 함수는 없다. 10에 가까운 함수: running.RunUpload.requireOrderedWithin 8, infra.integration.auth.JwtTokenIssuer.parse 10(5차와 같음), realtime.LiveSocketHandler.applyChatPresence 7, crew.JpegMetadataStripper.strip 7.

회귀·신규 시험 우선 경로:

| 함수·경로 | 이유 |
|-----------|------|
| infra.running.JpaRunRecordRepository.validDistanceSavedBefore | BE6-8: cutoff 직전·같은 시각·직후·무효 기록, savedAt 널(실 DB) |
| avatar.BackgroundService.backfillReached | 해금 행 없음 + 배포 전 거리 0/기준 직전/초과, 배포 후 기록 직후 조회·리스너 동시 실행에서 알림 정확히 1건(TC-6763 유지) |
| realtime.LiveSocketHandler.applyChatPresence + ChatPresenceRegistry | BE6-4·5: 거부 뒤 가입한 같은 세션의 재입장, 11번째 방, 세션 닫힘 직후 도착한 프레임, `checked` 항목 제거 |
| operator.OperatorBootstrap.seedIfEmpty | BE6-8: 빈 값 + 화이트리스트 있음/없음 조합의 로그 |
| infra/deploy.sh backup_database | BE6-3·9: postgres 실행 중/정지(볼륨 있음)/없음, pg_dump 실패, 보존 7개, 파일 권한 600 |
| crew.CrewPost.delete·crew.chat.CrewChatService.delete | 작성자·크루장·운영진·일반원·떠난 사람(OUTSIDER) 조합이 게시·채팅에서 같은 표를 따르는지(D-188), 403/409 구분(D-167) |
| web.chat.ChatController.react·unreact | 429 변환(움직이는 시계), 한도 경계 10/11 |
| crew.JpegMetadataStripper.strip | 리팩터링 전후 바이트 동일성(EXIF·COM·스캔 사이 APP, EOI 뒤 꼬리, EOI 없이 끝남, SOS 없이 EOI) |

### 9. 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| U6-1 | 05-02 5.4 "되돌릴 수 없는 마이그레이션", D-175 (2), implementation.md 10절 | 배포 전 덤프를 deploy.sh가 자동으로 뜬다(실패하면 배포 중단, 보존 7개, 파일 이름은 시각). D-175의 거부안("배포 스크립트가 자동 덤프")을 채택으로 바꾸고 BE6-1~3 결정(보존 위치·권한·정지 시 처리)을 적는다 | 코드가 낫다 |
| U6-2 | 06-04 CrewPermissionPolicy, 05-02 D-132 | 리액션 달기·떼기도 회원당 초당 10건(ChatRateGuard.admitReaction), 429 TOO_MANY_ATTEMPTS. 엔티티가 정책을 쓰는 방식(BE6-7 결정 뒤) | 코드 반영 뒤 |
| U6-3 | 05-02 D-176, 5.4 환경 변수 표 | `runkk.avatar.backfill-cutoff`(`AVATAR_BACKFILL_CUTOFF`): 의미, 기본값 규칙, 미래 값 금지(BE6-6 결정 뒤) | |
| U6-4 | 05-02 5.4 게시 사진 문단 | `max-age=86400`을 `max-age=300`으로(D-174 (2)와 맞춤) | 설계 내부 모순 |
| U6-5 | 06-01·06-02·06-04 | 새 이름: common.ServerZone.ID, WeeklyRace.GoalState, SchedulePost.Slot, ContentReport.Content, avatar.BackfillProperties, RunQueryService.validDistanceBefore·RunRecordRepository.validDistanceSavedBefore(06-02에는 이미 적혀 있으면 서명만 대조) | 구현 반영 |
| U6-6 | 02 동적 모델 1215·1220·1796·1808, 03 기능 모델 100 | `latestGoalBefore`를 호출하지 않는다(CREATE_WEEK 한 SQL이 복사). BE5-B-17 코드 삭제와 함께 갱신 | U5-5 이어짐 |

### 10. 다음 행동

- [ ] BE6-1: 덤프 위치를 작업 폴더 밖으로 옮기거나 CI 정리 제외를 건다(MR !14를 운영 DB에 배포하기 전)
- [ ] BE6-2·3: 덤프 권한 600·폴더 700, postgres 정지 + 볼륨 있음일 때 처리
- [ ] BE6-6: 기준 시각 검증·기본값 정리 후 배포 체크리스트(implementation.md 10절)에 "V25 적용 시각으로 설정" 항목
- [ ] BE6-4·5: 거부 캐시 정책과 초과 신호 로그를 정리하고 D-173에 반영
- [ ] BE6-7·8·9: 권한표 사본 정리, 빠진 시험 세 개, 불안정 시험 고치기
- [ ] 이월 경(미해결 11, 일부 4, 보류 2): 다음 변경 때 정리. B-8·9·13~15·17은 한 번에 정리하는 리팩터링 커밋 한 개로 묶는다(5차 B-22의 "기능별 한 가지 일" 규칙 유지)
- [ ] 설계서 갱신: `/sw-design-oo`로 U6-1~U6-6 반영
- [ ] 6차 종합 판정은 `docs/sw/code-review.md`에 앱·AI 결과와 합쳐 적는다
