# Grafana 이벤트 현황 대시보드 코드 리뷰 (MR !133)

> 작성: 2026-11-10 | 버전: v0.29 (다음 판 변경) | 입력: 코드 feature/S15P31A207-275-grafana-event-dashboard 872fc650(MR !133, develop 대상, 13파일, 범위 `git diff origin/develop...872fc650`), 수정 ca4e996b, srs 07-25(7.13.28, [임시:CR-성-2], [임시:FR-성-1], [임시:NFR-성-2], [임시:NFR-성-3]), design-oo 05-29([임시:D-성-7]~[임시:D-성-14]), 서버 판정 코드(EventService, EventEntrantService, JpaBattleRepository.findCelebrityWinners·findFirstCelebrityWins, JpaRunRecordRepository.validSummariesAtLeast, JpaMemberRepository.activeNicknames, EventStatus), 마이그레이션 V1~V49, infra(deploy.sh, lib.sh, restore-check.sh, backup/pg-backup.sh, 두 compose 파일, .gitlab-ci.yml backend:deploy 규칙), 로컬 시험(postgres:17-alpine에 V1~V47 적용 + 운영 +9시간 상태 경계값 자료, 이어서 V48·V49 적용)

[런크크 코드 리뷰 결과서](../code-review.md) 목차, 이전: [서버시간대-성](서버시간대-성.md)

## 리뷰 2026-11-10 (Grafana 이벤트 현황, S15P31A207-275)

대상: infra/docker-compose.edge.yml(grafana 망, DB 전용 망 두 개), infra/docker-compose.yml(postgres 망과 별칭), infra/deploy.sh(비밀번호 확인, 데이터소스 on/off, provisioning 다시 읽기, 계정 맞추기), infra/monitoring/grafana/runkk-db(readonly-role.sql, datasources.yml, datasources-off.yml), dashboards/runkk-event.json(변수 3, 패널 9), edge.env.example, .gitlab-ci.yml, infra/README.md 6절, 문서 조각 2개와 병합 대기 파일. 작성자 설계와 8절 확인 결과는 참고로만 쓰고, 판정 SQL은 서버 코드와 따로 대조했고 권한과 쿼리는 로컬 DB에서 다시 돌렸다. 서버와 운영·develop DB에는 아무것도 하지 않았다.

기준(서버 시간대 리뷰와 같음): 매개변수 4 이하, 중첩 3 이하, 함수 50줄 이하, 순환 복잡도 10 이하, 결합도 스탬프 이하, 응집도 통신적 이상, Fan-Out 7(design-review 27장). 형상 관리 규칙: 커밋 제목 `{타입}: {내용} [S15P31A207-275]`, 브랜치 `feature/{Jira}-…`. 리뷰 관점: 운영 영향(postgres 재생성, 배포 순서, 공유 에지), 보안(망 노출, 계정 권한, 비밀번호 전달, 데이터소스 비밀), 대시보드 SQL 정확성(서버 판정과 같음, V48 전후, 자동 보정, 비용), 스크립트 견고성(set -e, 빈 값, 멱등, CI 변수 경로), 문서와 화면 문구. 등급은 치명, 주요(스킬의 중), 경미(경), 제안이다. 결함 번호는 임시 번호 [임시:GF-성-n]으로 쓴다.

### 1. 설계 일치

| 설계 | 코드 | 판정 |
|------|------|------|
| [임시:D-성-7] 데이터소스 두 개와 대시보드(JSON 원본) | datasources.yml(uid runkk-db-prod, runkk-db-develop, editable false), runkk-event.json(uid runkk-event, editable false), 기존 provider(path /etc/grafana/dashboards, 폴더 런크크) | 일치 |
| [임시:D-성-8] 환경마다 internal DB 전용 망, postgres만 | compose config로 확인: 에지 `runkk_db-develop`, `runkk_db-prod` 둘 다 internal, grafana는 default와 두 망. 환경 스택은 `grafana-db` 망(external, `runkk_db-${RUNKK_ENV}`)에 postgres만 붙고 별칭 `postgres-<환경>`. api, redis, ai, backup, avatar는 없음. postgres에 ports 없음 | 일치 |
| [임시:D-성-9] 계정은 배포 스크립트가 api healthy 뒤 그 환경 DB에만 | deploy.sh:181 `sync_grafana_readonly_role`이 `health_curl` 뒤, 대상은 `$APP_PROJECT-postgres-1` | 일치 |
| [임시:D-성-10] 표·열 SELECT 목록, 기본 권한 없음, 계정 기본값 | readonly-role.sql. 로컬에서 목록 밖 열과 표, 쓰기 모두 거부, 손으로 준 표·열 권한은 재실행으로 거둬짐 | 일치. 다만 거두는 범위가 표·열뿐이다([임시:GF-성-3]) |
| [임시:D-성-11] 비밀번호 전달(CI masked → edge.env → `docker exec -e 이름` → `\getenv`, Grafana는 환경 변수 → secureJsonData) | .gitlab-ci.yml, deploy.sh:21~26·160~161, datasources.yml | 일치. 실패 시 서버 로그 경로([임시:GF-성-4]) |
| [임시:D-성-12] provisioning on/off 파일 교체와 관리 API 다시 읽기 | deploy.sh:77~82, 108~115 | 일치 |
| [임시:D-성-13] 판정 SQL이 서버와 같음 | 2절 대조와 3절 시험 | 일치 |
| [임시:D-성-14] 시각 보정 변수(자동 = V48 여부) | `cfg` CTE, 패널 4·6·7·8·9 | 일치 |

설계에 없는 구성 요소는 없다. 서버(backend) 코드는 바뀌지 않았다. 결합 관점에서 대시보드 SQL은 서버 판정 규칙을 복사한 것이라 battle, run_record, member, event 표 구조에 묶인다(공통 결합에 가까운 자료 의존). 설계 9절과 병합 대기 파일의 「다른 담당에게 요청」이 이 의존을 적어 두었으므로 결함으로 세지 않는다.

### 2. 판정 SQL 대조 (결함 아님)

| 서버 판정 | 대시보드 SQL | 판정 |
|-----------|--------------|------|
| 셀럽: `status = FINISHED and type = CELEBRITY and opponentKind = CELEBRITY and winnerMemberId is not null and opponentRecordId in (:ids 문자열) and judgedAt >= from and judgedAt <= to`, (회원, 기록)마다 `min(judgedAt)` | `coach_win` 같은 조건, `opponent_record_id IN (jsonb_array_elements_text(cfg->'celebrityIds'))`, `BETWEEN starts_at AND ends_at`, `GROUP BY 1,2,3` | 같음. 서버도 ID를 `String.valueOf`로 넘기므로 문자열 비교가 같다 |
| 거리: `valid = true and distanceMeters >= :min and startedAt >= from and startedAt <= to`, 회원마다 가장 이른 기록 | `run_first` 같은 조건, `min(started_at)` | 같음 |
| 응모자: `activeNicknames`(ACTIVE이고 닉네임이 있는 회원) | `m.status = 'ACTIVE' AND m.nickname IS NOT NULL` | 같음 |
| 상태: `EventStatus.of(startsAt, endsAt, now)`(양 끝 포함) | `(now() AT TIME ZONE 'Asia/Seoul') + shift`와 저장 값 비교 | 같음. 운영 V48 전은 저장 값이 모두 +9라 now에 9를 더해 비교하는 것이 서버(읽을 때 -9)와 같다 |
| 지급: event_point_grant 전체(탈퇴 회원 몫 포함) | `grant_sum`, 코치별은 `target_key = 'celebrity-' || id`(EventService.celebrityKey와 같음) | 같음 |

### 3. 결함

| ID | 등급 | 파일:줄(리뷰 시점 872fc650) | 근거 | 수정안 | 처리 |
|----|------|------------------------------|------|--------|------|
| [임시:GF-성-1] | **주요** | infra/restore-check.sh:45 | 계정 맞추기가 끝나면 그 환경 DB 덤프에 `GRANT SELECT ... TO runkk_grafana_ro`가 들어간다(로컬 덤프에서 49줄). 복구 시험은 빈 컨테이너에 앱 계정과 runkk_ai만 만든 뒤 `pg_restore --exit-on-error`를 돌리므로 `role "runkk_grafana_ro" does not exist`로 멈춘다(로컬 재현: 두 계정만 만들고 복원하면 rc=1). develop은 이 MR 병합 다음 날 매일 덤프부터, 운영은 운영 적용 뒤부터 backup:restore-check(sqa 8.1 필수 5, NFR-018·019)가 늘 실패한다. README 7절 손 리허설도 같다 | 복원 전에 만드는 계정 목록에 runkk_grafana_ro를 더한다(NOLOGIN). README 리허설에도 계정 만드는 줄을 넣는다 | 해소(ca4e996b). 세 계정을 만든 뒤 같은 덤프 복원 rc=0, 열 권한 복원 확인 |
| [임시:GF-성-2] | **주요** | infra/docker-compose.yml:120(postgres) | 이 MR은 첫 배포 때 망이 바뀌어 postgres를 반드시 다시 만든다(운영 포함). 그 자리는 deploy.sh:147 `up -d --no-deps postgres backup`이라 옛 api가 아직 돌고 있다. postgres에 `stop_grace_period`가 없어 compose 기본 10초가 지나면 SIGKILL이다. 이미지의 STOPSIGNAL은 SIGINT(빠른 종료)라 보통 몇 초 안에 끝나지만, 쓰기가 몰린 때 종료 체크포인트가 10초를 넘으면 강제 종료되고 다음 기동이 장애 복구(WAL 재생)로 길어져 운영 중단 시간이 늘어난다. 분리 전 컨테이너를 내릴 때는 이미 60초를 쓴다(deploy.sh:69) | postgres에 `stop_grace_period: 60s`. 같은 첫 배포의 재생성에 함께 들어가므로 재생성이 더 늘지 않는다. README 6절에 옛 api 요청 실패와 정상 종료 대기를 적는다 | 해소(ca4e996b). compose config에서 `stop_grace_period: 1m0s` 확인 |
| [임시:GF-성-3] | 경미 | infra/monitoring/grafana/runkk-db/readonly-role.sql:25~29 | 「배포마다 권한을 목록대로 다시 맞춘다」가 표·열 권한만 거둔다. 손으로 준 스키마 USAGE(예: analytics), 시퀀스 권한, 역할 멤버십(pg_read_all_data 등)은 재실행 뒤에도 남는다(로컬: `GRANT USAGE ON SCHEMA analytics`, `GRANT SELECT ON ALL SEQUENCES` 뒤 재실행해도 둘 다 남음). NOINHERIT라 멤버십 권한은 바로 쓰이지 않지만 `SET ROLE`로 쓸 수 있다. 지금 코드가 주는 것은 없으므로 사람의 손 작업이 있을 때만 문제다 | 같은 트랜잭션에 역할 멤버십 회수(`pg_auth_members`, 준 사람마다 `GRANTED BY`), 사용자 스키마 전부의 `REVOKE ALL ON ALL SEQUENCES`, 스키마 USAGE·CREATE 회수를 더한다 | 해소(후속 커밋 「fix: Grafana 읽기 전용 계정 권한 정리와 리뷰 경미 결함 반영」). 멤버십 전부, 사용자 스키마의 시퀀스와 스키마 권한(public 포함, 뒤에 public USAGE만 다시 줌), 데이터베이스 권한(뒤에 CONNECT만 다시 줌)을 거둔다. 로컬: pg_read_all_data(runkk가 줌), pg_monitor(다른 계정이 줌), public·analytics 시퀀스, analytics USAGE·CREATE, public CREATE, DB CREATE·TEMP, member.provider, audit_record를 손으로 준 뒤 재실행하면 모두 사라지고 처음 상태와 같다 |
| [임시:GF-성-4] | 경미 | readonly-role.sql:16~17 | `ALTER ROLE ... PASSWORD :'ro_password'`는 psql 변수 인용이라 SQL 주입은 없고(값도 영숫자로 거름) 명령줄·ps에도 없다. 그러나 이 문이 실패하면 postgres는 기본값(log_min_error_statement = error)으로 오류 문장을 `STATEMENT:`로 서버 로그(docker logs, json-file)에 평문 비밀번호와 함께 남긴다. 실패할 경우는 드물다 | 파일 첫 부분에 `SET log_min_error_statement = panic;`(이 세션만, 슈퍼유저)을 둔다 | 해소(후속 커밋 「fix: Grafana 읽기 전용 계정 권한 정리와 리뷰 경미 결함 반영」). `log_statement = none`, `log_min_duration_statement = -1`도 함께 둔다. 로컬: 서버를 `log_statement=all`로 띄워 계정 SQL을 돌려도 로그에 비밀번호 0건, 같은 SET 뒤 실패하는 `ALTER ROLE … PASSWORD`도 0건(SET 없는 대조군은 2건) |
| [임시:GF-성-5] | 경미 | readonly-role.sql(계정 전체) | PUBLIC의 데이터베이스 TEMP 권한 때문에 이 계정도 읽기 전용 기본값을 세션에서 끈 뒤 임시 표를 만들 수 있다(로컬 확인). 다른 표에는 쓰지 못하므로 자료 영향은 없고 디스크 사용만 남는다. 설계 8절 TC-성-20의 「CREATE TEMP는 read-only 오류」는 기본값 그대로일 때만 맞다 | 설계 6절 「남는 위험」에 적거나 `ALTER ROLE runkk_grafana_ro SET temp_file_limit = '256MB'`. 데이터베이스 PUBLIC TEMP 회수는 앱에 영향이 있을 수 있어 권하지 않는다 | 남김 |
| [임시:GF-성-6] | 경미 | design-oo/05-29 7절 4, infra/README.md 6절 | 공유 에지와 환경 스택은 마지막에 배포한 브랜치 설정을 따른다. 문서는 main 배포만 적었지만 release/* 도 develop 환경(8443)에 자동 배포된다. 이 MR 병합 전에 갈라진 release/* 에 push하면 옛 compose로 develop postgres가 다시 만들어져 망에서 떨어지고(재시작), 다음 develop 배포가 또 다시 만든다. 그 파이프라인의 옛 restore-check.sh는 develop 덤프 복구 시험에도 실패한다([임시:GF-성-1]) | 7절 4에 「이 MR 병합 전에 갈라진 release/* 에 push하지 않는다(2차 배포 release는 병합 뒤에 자른다)」를 더한다 | 해소(후속 커밋 「fix: Grafana 읽기 전용 계정 권한 정리와 리뷰 경미 결함 반영」). design-oo 05-29 7절 4와 infra/README.md 6절 「배포 순서 주의」 |
| [임시:GF-성-7] | 경미 | dashboards/runkk-event.json:151(패널 2 제목) | 패널은 선택한 RUN_DISTANCE 이벤트 전부를 더하는데 제목이 「1km 응모자」로 고정이다. 3km 이벤트를 만들면 틀린 이름이 된다(설명에는 「베타는 1km」) | 제목을 「거리 이벤트 응모자」로 바꾸거나 이벤트별 표만 쓰게 한다 | 해소(후속 커밋 「fix: Grafana 읽기 전용 계정 권한 정리와 리뷰 경미 결함 반영」). 패널 제목과 SQL 열 이름을 「거리 이벤트 응모자」로, 설계 4.1 표와 README 설명도 맞춤. 가운뎃점 0건 |
| [임시:GF-성-8] | 경미 | dashboards/runkk-event.json:610(패널 9 SQL 끝) | 응모자 명단이 `LIMIT 2000`이라 넘으면 아무 표시 없이 잘린다. 추첨 원본은 관리 API라 위험은 작다 | 설명에 「2,000명까지」를 적거나 전체 수 열을 더한다 | 남김 |
| [임시:GF-성-9] | 제안 | runkk-db/datasources.yml:19, 39 | `postgresVersion: 1500`인데 DB는 postgres 17이다. Grafana 편집기 기능 판단에만 쓰여 동작 영향은 없다 | 실제 판에 맞춘다 | 남김 |

보안 점검에서 결함이 아닌 것: 망은 grafana와 각 환경 postgres만 있고 internal이라 밖으로 나가지 못한다. grafana의 기본 경로는 internal 망이 아니라 기본 망으로 잡혀 Mattermost 알림 전송은 그대로다(로컬에서 internal 망을 먼저 붙이고 기본 망을 더한 컨테이너의 `ip route`와 바깥 연결 확인). develop 배포는 에지에 `runkk_db-prod` 망만 만들 뿐 운영 postgres(프로젝트 runkk-prod)를 건드리지 않는다. 운영 계정 권한은 운영 배포(main 코드)만 맞추므로 develop 브랜치가 운영 권한을 넓힐 수 없다. 비밀번호는 저장소, 명령줄, `docker exec` 인자에 없고 CI 로그는 masked다. 대시보드 변수는 Grafana SQL 데이터소스가 작은따옴표를 이중화하고 다중 값은 따옴표로 감싸므로 변수 값으로 따옴표를 빠져나가지 못하며, 빠져나가더라도 계정 권한 범위다. secureJsonData는 Grafana 비밀 키로 암호화되어 API로 읽히지 않는다(값은 grafana 컨테이너 환경 변수에도 있어 관리자 비밀번호와 같은 수준).

### 4. 스크립트와 SQL 견고성 (결함 아님)

- `set -euo pipefail` 아래에서 계정 맞추기는 `if docker exec …; then … else WARN` 꼴이라 실패해도 배포가 멈추지 않는다. 비밀번호가 비었거나 형식이 틀리면 빈 값을 export해 compose도 빈 값을 받고, off 파일로 데이터소스를 지우며 계정은 건드리지 않는다. provisioning 다시 읽기 실패도 경고만 남긴다.
- 멱등: 계정 SQL을 같은 DB에 두 번, V48·V49 적용 뒤 한 번 더 돌려 결과가 같았다. GRANT가 실패하면(표나 열이 바뀐 경우) 트랜잭션 전체가 되돌려져 앞 권한이 그대로 남고 경고가 난다.
- `\getenv`는 psql 15 이상이 필요하고 컨테이너는 17이다. CI 변수 경로는 masked·protected 변수 → `printf`로 edge.env 끝 → `env_value` → export → `docker exec -e 이름`이며, develop, release/*, main이 모두 보호 브랜치여야 값이 보인다(.gitlab-ci.yml 26행의 기존 전제와 같음).
- 쿼리 비용: 셀럽 승리는 `idx_battle_member_win_judged_at`, 거리 기록은 `idx_run_record_valid_started`를 탈 수 있다. 코치별 대결 수(승패 모두)는 battle을 훑지만 베타 규모에서는 문제없고 계정 쿼리 15초, 연결 3개(데이터소스 2개)로 상한이 있다.

### 5. 코딩 스타일, 구조

- deploy.sh: 새 함수 `sync_grafana_readonly_role` 12줄, 분기 2. `deploy_edge`는 43줄로 50줄 안이다. 이름과 주석은 기존 규칙(한국어 주석, 영어 메시지)과 같다.
- 대시보드 SQL: 공통 CTE(ev, coach_win, run_first, entrant, cfg)가 패널마다 복사되어 있다. Grafana에 공유 쿼리가 없어 피하기 어렵고 설계 9절이 함께 고치도록 적었다. 고칠 때 9곳을 같이 바꿔야 하는 유지보수 부담은 남는다(결함으로 세지 않음).
- 화면 문구: 대시보드 제목, 패널 제목, 변수 이름과 선택지, 패널 설명에 가운뎃점(·)이 없다(JSON 전체 문자열 검사, SQL 제외 0건).
- 순환 복잡도 10 초과 함수 없음. sw-test 우선 대상 없음.

### 6. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | Jira S15P31A207-275, 브랜치 feature/S15P31A207-275-grafana-event-dashboard, MR !133 |
| 버전 제어 | 예 | 커밋 872fc650, 수정 ca4e996b |
| 변경 통제 | 예 | [임시:CR-성-2](2026-11-10 사용자 결정), srs 07-25, 설계 05-29 |
| 형상 감사 | 예 | `git diff origin/develop...872fc650`(13파일), 수정은 `git show ca4e996b` |
| 상태 보고 | 예 | 커밋 메시지 규칙 준수, 병합 대기 파일 |

### 7. 수정과 검증

| 커밋 | 내용 | 검증 |
|------|------|------|
| ca4e996b | [임시:GF-성-1] restore-check.sh 계정 목록과 주석, README 7절 리허설 계정 줄, [임시:GF-성-2] postgres `stop_grace_period: 60s`, README 6절 재생성 설명 | `bash -n deploy.sh lib.sh restore-check.sh` 통과. compose config(에지는 dummy 값, 환경은 prod와 develop 두 번): grafana 망 3개, DB 망 internal 2개, grafana-db 망에는 postgres만, 별칭 postgres-prod/postgres-develop, postgres `stop_grace_period` 1m0s. 로컬 덤프를 세 계정을 만든 빈 컨테이너에 `--no-owner --exit-on-error`로 복원 rc=0 |

| 후속 커밋(fix: Grafana 읽기 전용 계정 권한 정리와 리뷰 경미 결함 반영) | [임시:GF-성-3] readonly-role.sql 재맞춤에 멤버십, 시퀀스, 스키마, 데이터베이스 권한 회수, [임시:GF-성-4] 세션 로그 설정 세 줄, [임시:GF-성-6] design 05-29 7절 4와 README 6절 배포 순서 주의, [임시:GF-성-7] 패널 2 제목과 열 이름, 설계 3절과 4.1 표 | postgres:17-alpine(`log_statement=all`)에 V1~V49와 경계값 자료를 다시 넣고 계정 SQL을 두 번 실행(rc=0, 같은 상태), 손 권한 9종을 준 뒤 재실행해 모두 회수, 거부 시험 12건(목록 밖 열과 표, 시퀀스, analytics 스키마, `SET ROLE pg_read_all_data`, 읽기 전용을 끈 INSERT, public과 analytics에 표 만들기, pg_read_file) 모두 거부, 대시보드 변수와 패널 9개 결과가 앞 시험과 같음(열 이름만 바뀜), 서버 로그에 비밀번호 0건, 대시보드 JSON 읽기와 가운뎃점 0건 |

로컬 시험(postgres:17-alpine, 시험 뒤 컨테이너 삭제):

1. V1~V47 적용(V35 → 35.1 → 35.2 → 35.3 순), 운영 임시 보정처럼 이벤트 기간 +9시간, 대결·기록·지급을 「한국 시각 + 9시간」으로 넣었다. 경계 자료: 시작 정각 승리, 끝 정각 승리, 시작 1초 전·끝 1초 뒤 승리, 같은 셀럽 두 번 승리, 패배, 탈퇴 회원 승리, 이벤트 밖 셀럽, RESULT_PENDING, 999m·무효·기간 밖 기록, 끝 정각 기록, 더 이른 기록이 있는 회원.
2. deploy.sh와 같은 방법(`docker exec -i -e GRAFANA_DB_READONLY_PASSWORD … psql < readonly-role.sql`)으로 계정을 만들고, 그 계정의 TCP 로그인으로 변수 쿼리와 패널 9개를 돌렸다(`$event`는 Grafana 다중 값처럼 `'1','2'`, `$__timeFilter`는 BETWEEN으로 바꿈). 결과가 서버 판정 손계산과 같았다: 코치 응모 2(m1 항목 2, m2 끝 정각), 거리 응모 2(m1 시작 정각, m4는 더 이른 11-10), 코치 11 대결 4·승리 3·응모 1, 코치 12 대결 2·승리 2·응모 2, 지급 3건 1500, 이벤트 기간 11-08 00:00:00 ~ 11-16 23:59:59, 상태 진행 중.
3. V48, V49를 적용하고 계정 SQL을 다시 돌린 뒤 같은 쿼리를 자동 보정으로 돌렸다. 9개 결과가 V48 전과 같았다.
4. 권한: member·run_record 다른 열, `SELECT *`, audit_record, crew_chat_message, battle.opponent_track 거부. 읽기 전용을 끈 INSERT 거부. public에 표 만들기 거부. pg_read_file 거부. 멤버십 0. 손으로 준 member 열과 audit_record 권한은 재실행으로 거둬짐([임시:GF-성-3]의 스키마·시퀀스는 남음).
5. 대시보드 JSON 읽기, 가운뎃점 검사, 내부 망과 기본 경로 확인.

Grafana 화면 자체(provisioning, 매크로)는 작성자 확인(설계 8절 TC-성-24)을 따르고 다시 띄우지 않았다.

### 8. 판정

**■ 합격  □ 조건부  □ 불합격 (치명 0 / 주요 2 / 경미 6, 제안 1).** 주요 2건은 같은 브랜치 ca4e996b에서 고쳤다. 경미 가운데 보안과 문서 4건([임시:GF-성-3], [임시:GF-성-4], [임시:GF-성-6], [임시:GF-성-7])은 사용자 결정(2026-11-10)으로 병합 전에 후속 커밋 「fix: Grafana 읽기 전용 계정 권한 정리와 리뷰 경미 결함 반영」에서 고쳤고, [임시:GF-성-5], [임시:GF-성-8]과 제안 [임시:GF-성-9]는 남긴다.

병합과 운영 적용 전에 사람이 확인할 것:

1. 운영 postgres 재생성: 이 변경이 main에 들어간 첫 prod 배포는 배포 직전 덤프 단계에서 운영 postgres를 한 번 다시 만든다(볼륨 `POSTGRES_VOLUME`은 그대로, 정상 종료 60초까지 대기). 이때 옛 api가 돌고 있어 정상 종료 몇 초 + 기동과 healthcheck(10초 간격) 동안 요청이 실패하고, 같은 배포가 이어서 api를 새로 띄우므로 끊김은 api 재시작 시간에 십수 초가 더해지는 정도다. 2차 배포(V48 포함, 어차피 api가 다시 뜨는 배포)에 함께 넣고 사용이 적은 시각에 수동 승인한다. 덤프는 재생성 뒤에 뜨므로 복원 기준점은 그대로다.
2. develop 배포는 운영 postgres를 건드리지 않는다(에지에 `runkk_db-prod` 망만 만든다). 다만 병합 뒤 main 배포(핫픽스)가 먼저 돌면 grafana가 DB 망에서 떨어지고(대시보드만 영향), 병합 전에 갈라진 release/* 에 push하면 develop postgres가 다시 만들어진다([임시:GF-성-6]). 2차 배포 release 브랜치는 이 MR 병합 뒤에 자른다.
3. CI 변수 `GRAFANA_DB_READONLY_PASSWORD`(masked, protected, 영숫자 16자 이상)를 넣고, 운영 `INFRA_ENV_PROD`의 `DB_NAME`과 develop `INFRA_ENV`의 `DB_NAME`이 `runkk`인지 확인한다. 다르면 `INFRA_ENV_EDGE`에 `GRAFANA_DB_NAME_PROD`/`GRAFANA_DB_NAME_DEVELOP`를 넣는다.
4. 병합 뒤 develop 배포 로그에 `Grafana read-only DB role runkk_grafana_ro is in sync on develop.`, Grafana `runkk-db-develop` Save & test, 이벤트별 응모자 수와 관리 API 응모자 명단 전체 수 비교. 다음 날 develop 파이프라인에서 backup:restore-check를 한 번 돌려 [임시:GF-성-1] 수정을 확인한다.
5. Grafana 계정은 관리자 하나로 둔다(편집 권한자는 Explore로 계정 범위 SQL을 돌릴 수 있다).

### 8.1 설계서 갱신 항목

- 반영함(후속 커밋 「fix: Grafana 읽기 전용 계정 권한 정리와 리뷰 경미 결함 반영」): design-oo 05-29 3절 readonly-role.sql 명세(권한 회수 범위, 세션 로그 설정, [임시:GF-성-3]·[임시:GF-성-4]), 4.1 패널 2 이름([임시:GF-성-7]), 7절 4 release/* ([임시:GF-성-6]).
- 남음: 6절 남는 위험에 임시 표([임시:GF-성-5]), 8절 TC-성-20 문구(기본값일 때만), 2절 재생성 문장에 정상 종료 60초와 옛 api 요청 실패. 담당(이성현)이 다음 판에 반영한다.
