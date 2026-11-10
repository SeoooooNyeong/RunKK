# 병합 대기: feature/S15P31A207-275-grafana-event-dashboard

> 작성: 2026-11-10 | 작성자: 이성현 | 스킬: sw-srs, sw-design-oo(Grafana 이벤트 현황 대시보드, 구현 포함), sw-code-review(MR !133 독립 리뷰)

2026-11-10 사용자 결정(관리 웹 대신 기존 Grafana에 운영 DB 읽기 전용 연결을 더해 이벤트 현황을 본다)을 요구, 설계, 구현 순서로 넣었다. 서버 코드는 바꾸지 않았다. 바꾼 것은 infra(compose 두 파일, deploy.sh, Grafana provisioning, 대시보드 JSON, 읽기 전용 계정 SQL, README 6절), .gitlab-ci.yml(CI 변수 한 줄)이다. 운영 서버와 운영 DB에는 아무것도 하지 않았다. develop DB 읽기 전용 조회는 작업 환경에서 서버 접속이 거부되어 로컬 마이그레이션 DB 두 벌과 로컬 Grafana로 대신 확인했다(design-oo/05-29 8절).

## 고친 조각
- srs/07-25-변경-요청-CR-이벤트현황-성.md: 새 조각(7.13.28). 변경 요청, 도출 기록, [임시:FR-성-1] 운영자 이벤트 현황 조회, 보안 비기능 2건, 시나리오 S-EV-DASH-1~5, 영향 분석, 확인 기준
- design-oo/05-29-설계-이벤트현황-성.md: 새 조각. 결정 8건(연결 방식, 계정 생성 시점은 배포 스크립트, 권한, 비밀번호 전달, provisioning, 집계 SQL, 시각 보정), 배치와 망, 구성 요소 명세, 패널과 공통 SQL, 시각 처리, 보안 영향, 운영 적용 절차, 로컬 확인 결과
- code-review/Grafana이벤트현황-성.md: 새 조각. MR !133 독립 리뷰(872fc650), 합격(0 / 2 / 6, 제안 1). 주요 [임시:GF-성-1](복구 시험이 runkk_grafana_ro GRANT에서 멈춤)과 [임시:GF-성-2](postgres 재생성 때 정상 종료 대기 10초)는 ca4e996b에서 고침(infra/restore-check.sh, infra/docker-compose.yml, infra/README.md). 경미 6과 제안 1은 남김

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:CR-성-2] | CR | 운영자가 Grafana에서 이벤트 현황을 본다(운영과 develop DB 읽기 전용, 망 최소 노출, 비밀번호는 CI 변수) | srs/07-25 |
| [임시:FR-성-1] | FR | 운영자 이벤트 현황 조회(응모자 수, 코치별 대결과 승리, 1km 응모자, 포인트 지급, 일별 추이, 응모자 닉네임 명단) | srs/07-25 |
| [임시:NFR-성-2] | NFR | 보안(최소 권한, 개인정보): 대시보드 표와 열만 SELECT, 쓰기 불가, 회원은 닉네임까지, 비밀번호 평문 없음, 값 없으면 연결만 빠짐 | srs/07-25 |
| [임시:NFR-성-3] | NFR | 보안(망 노출): 환경마다 postgres만 붙은 internal 전용 망, 호스트 포트 없음, 다른 컨테이너와 다른 환경 DB에 닿지 않음 | srs/07-25 |
| [임시:D-성-7]~[임시:D-성-14] | 설계 결정 | Grafana 데이터소스와 대시보드, DB 전용 망, 배포 스크립트 계정 생성, 표·열 권한, 비밀번호 전달, provisioning on/off, 판정 SQL, 시각 보정 변수 | design-oo/05-29 |
| [임시:TC-성-19]~[임시:TC-성-27] | 시험 케이스 | 계정 멱등, 권한 거부, 권한 재맞춤, 쿼리 기대값(경계), V48 전후 같은 결과, Grafana provisioning과 매크로, 데이터소스 on/off, 비밀번호 확인 함수, compose 설정 | design-oo/05-29 8절 |
| [임시:GF-성-1]~[임시:GF-성-9] | 코드 리뷰 결함 | 주요 2(복구 시험 계정 목록, postgres 정상 종료 대기), 경미 6(권한 재맞춤 범위, 비밀번호 실패 로그, 임시 표, release/* 배포 순서 문서, 패널 2 제목, 명단 2000 제한), 제안 1(postgresVersion) | code-review/Grafana이벤트현황-성 |

정식 번호: CR은 [임시:CR-성-1] 정식 번호 다음, FR은 FR-321 다음, NFR은 [임시:NFR-성-1] 정식 번호 다음, D는 [임시:D-성-6] 정식 번호 다음, TC는 이벤트 대역(TC-2601~TC-2999)의 빈 번호에 매긴다. 서버 시간대 브랜치(fix-S15P31A207-275-server-timezone.md)의 임시 번호를 먼저 매긴 뒤 이 파일을 매긴다.

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| srs.md | 머리 헤더 입력란과 목차 표에 07-25 행(7.13.28 Grafana 이벤트 현황 변경 요청) |
| srs/03-07-운영-관리.md | 3.7.2 이벤트 관리 끝(FR-319 다음)에 [임시:FR-성-1] 표(07-25 7.13.28.2의 표 그대로) |
| srs/04-비기능-요구사항.md | 4.3 보안·개인정보에 [임시:NFR-성-2], [임시:NFR-성-3] 행 |
| srs/06-02b-DFD-Level2-프로세스-6-7.md | 7.9 이벤트 관리 설명에 「운영자의 이벤트현황요청은 모니터링 화면이 ║D6║ 대결레코드, ║D2║ 러닝기록, ║D1║ 회원레코드(닉네임, 상태), 이벤트 파일, 포인트 지급 이력을 읽기 전용 계정으로 직접 읽어 이벤트현황으로 돌려준다(서버를 거치지 않음, [임시:FR-성-1])」 |
| srs/06-04b-자료사전-아-이후.md | 이벤트현황 = 응모자수 + 코치별현황 + 지급건수 + 지급합계 + 일별추이 + 응모자명단(닉네임까지) |
| srs/07-01b-추적표-FR-130-이후.md | [임시:CR-성-2] → [임시:FR-성-1], [임시:NFR-성-2], [임시:NFR-성-3] → D-성-7~14 → TC-성-19~27 |
| design-oo.md | 목차 표에 05-29 행, 머리 헤더 입력란 |
| design-oo/12-의존도.md 또는 배치도 | 에지 grafana → runkk_db-<환경> → postgres-<환경> 연결(05-29 2절) |
| plan/08-09-품질-위험.md | 새 위험 후보 2건: Grafana 침해 시 두 환경 DB 일부 읽기 노출(완화: 열 권한, 관리자 하나, 비밀번호 교체 절차, 05-29 6절), 대시보드 SQL과 서버 응모 판정 어긋남(완화: 판정 규칙 CR은 대시보드 SQL도 함께 고침) |
| test.md | 머리 헤더 입력란에 Grafana 이벤트 현황 로컬 확인(TC-성-19~27, design-oo/05-29 8절), develop 배포 뒤 확인 남음 |
| code-review.md | 목차 표에 Grafana이벤트현황-성 행(MR !133 872fc650, 수정 ca4e996b, 0 / 2 / 6 제안 1, 합격), 머리 헤더 입력란, 최신 판정 문단. 정식 결함 번호는 다음 PL 계열로 |

## 다른 담당에게 요청
- 박재영: 대결 판정(battle.judged_at, winner_member_id, opponent_record_id)이나 셀럽 대결 종류 값을 바꾸면 `infra/monitoring/grafana/dashboards/runkk-event.json`의 공통 SQL과 `infra/monitoring/grafana/runkk-db/readonly-role.sql`의 battle 열 목록도 함께 고쳐 달라(design-oo/05-29 9절). 러닝 기록 유효 판정(run_record.valid, distance_meters)도 같다.
- 이성현(design-oo/05-29, 다음 판): 코드 리뷰 8.1절 갱신 항목(6절 남는 위험에 임시 표와 손 권한 잔존, 7절 4에 release/*, 8절 TC-성-20 문구, 2절 재생성 문장)

## 사용자 할 일
1. GitLab CI 변수 `GRAFANA_DB_READONLY_PASSWORD`(Variable, masked, protected, 영숫자 16자 이상, 예: `openssl rand -hex 24`)를 넣는다. 병합 전에 넣으면 병합 뒤 develop 배포에서 바로 켜진다.
2. 병합 뒤 develop 배포가 끝나면 Grafana `runkk-db-develop` 연결 확인과 숫자 비교(관리 API 응모자 명단 전체 수).
3. 운영은 이 변경이 main에 들어간 prod 배포(2차 배포 예정)에서 켜진다(design-oo/05-29 7절).
4. 이 MR 병합 뒤에 2차 배포 release 브랜치를 자른다. 병합 전에 갈라진 release/* 에 push하면 develop postgres가 다시 만들어지고 grafana가 DB 망에서 떨어진다(code-review/Grafana이벤트현황-성 [임시:GF-성-6]).
5. 운영 `INFRA_ENV_PROD`와 develop `INFRA_ENV`의 `DB_NAME`이 `runkk`인지 확인한다. 다르면 `INFRA_ENV_EDGE`에 `GRAFANA_DB_NAME_PROD`, `GRAFANA_DB_NAME_DEVELOP`를 넣는다.
6. 운영 첫 적용(prod 배포)은 운영 postgres를 한 번 다시 만든다(볼륨 그대로, 옛 api 요청이 십수 초 실패). 2차 배포와 함께 사용이 적은 시각에 수동 승인한다. develop 첫 배포 다음 날 develop 파이프라인의 backup:restore-check를 한 번 돌려 복구 시험 수정을 확인한다.
