# runkk-ai

런크크 ai 서버(Python 3.12, FastAPI). api 컨테이너만 부르는 내부 서비스다. 설계: design-oo 6.1.16, 13.3, D-58~D-62.

| 엔드포인트 | 내용 |
|---|---|
| `GET /health` | 상태 확인(토큰 검사 없음) |
| `POST /comments` | 체력 요약 집계값 → AI 코멘트. 200 `{"text"}` / 503·504·422 `{"reason"}` |
| `POST /faces` | 셀카(`application/octet-stream`) → 부위별 base64 PNG. 200 `{"parts"}` / 413·415·422·503·504 `{"reason"}` |
| `POST /sessions/{sessionId}/analyze` | 러닝 1회 원본(1초 센서·GPS·회복 심박) → RUN바디 분석 접수. 202 `{"sessionId","status","engineVersion"}` / 409·413·422 `{"reason"}` |
| `GET /sessions/{sessionId}/status` | 분석 상태 `pending`·`done`·`failed`. 200 / 404 `{"reason"}` |
| `GET /sessions/{sessionId}` | RUN바디 1회 결과(5각 점수·판정 라벨·코칭 플래그·km 랩·피드백 문장). 200 / 404·409 `{"reason"}` |
| `GET /users/{userId}/months/{yearMonth}` | 월간 RUN바디(월 요약·5각 평균·최고 기록·주간 거리·최근 부하·개인 기준값·아바타 입력값). 200 / 422 `{"reason"}` |

`/health` 말고는 모두 `X-Internal-Token` 헤더가 필요하다. 미들웨어가 본문을 읽기 전에 검사해 틀리면 401 `{"reason": "UNAUTHORIZED"}`
(404·405도 `{"reason": "NOT_FOUND"}` 형식). 응답 표본은 `tests/contract/*.json`(서버 Gateway 계약 시험이 같이 쓴다).

## 준비

```bash
cd ai
uv python install 3.12
uv sync                      # .venv 생성, 개발 도구 포함
```

## 검증 (커밋 전)

```bash
uv run ruff format --check .
uv run ruff check .
uv run lint-imports          # core·comment·face·runbody 상호 독립, storage는 main만 끼움, 모듈 안 한 방향
uv run mypy
uv run pytest --cov=runkk_ai # 라인 커버리지 80% 미만이면 실패
```

Windows 콘솔에서 `lint-imports` 한글이 깨지면 `PYTHONIOENCODING=utf-8`을 붙인다.
`tests/storage`의 PostgreSQL 시험은 `RUNKK_TEST_DB_URL`이 있을 때만 돈다(CI는 항상 돈다. 아래 "PostgreSQL 저장소").

## 실행

```bash
export RUNKK_INTERNAL_TOKEN=local-dev-token-0123456789 RUNKK_EXPOSE_DOCS=true
uv run uvicorn --factory runkk_ai.main:create_app --port 8000
```

제공자 키(`RUNKK_ANTHROPIC_API_KEY` 등)·얼굴 API 주소가 없어도 뜬다. 그때 `/comments`는 503 `LLM_UNAVAILABLE`,
`/faces`는 503 `UPSTREAM_FAILED`를 돌려주고 api가 규칙 문구·기본 캐릭터로 대체한다. 환경 변수 목록은 `.env.example`.

### RUN바디 분석 해 보기

`tests/fixtures/`의 실측 러닝 2회(10/18, 10/19)를 순서대로 보낸다. 두 번째 결과에 첫 번째와의 비교(`prev`)가 들어간다.

```bash
H="X-Internal-Token: $RUNKK_INTERNAL_TOKEN"
curl -X POST localhost:8000/sessions/run-20261018/analyze -H "$H" -H 'Content-Type: application/json' \
  --data-binary @tests/fixtures/first_run.json
curl -X POST localhost:8000/sessions/run-20261019/analyze -H "$H" -H 'Content-Type: application/json' \
  --data-binary @tests/fixtures/second_run.json
curl localhost:8000/sessions/run-20261019/status -H "$H"   # "done"이 되면
curl localhost:8000/sessions/run-20261019 -H "$H"          # 헤드라인 "출발이 너무 빨랐어요"
```

월간 결과는 같은 저장소의 1회 결과로 요청 때마다 계산한다(러닝이 없는 달도 200, `sessionCount` 0).

```bash
curl localhost:8000/users/u_demo/months/2026-10 -H "$H"   # 2회 5,035 m, 5각 평균, 10/19 최장 3,008 m
```

분석은 별도 스레드에서 돈다(동시 2개, 한 번에 수십 ms). 결과 저장소는 아직 메모리라 재시작하면 빈다
(analytics 스키마 저장은 S15P31A207-163). 같은 `sessionId`를 다시 보내면 다시 계산하지 않고 현재 상태를 돌려준다.

### RUN바디 판단 기준

| 구분 | 기준 | 코드 |
|---|---|---|
| 5각 점수·라벨 | 축별 지표 → 0~100 점수와 `label`·`level`(앱은 그대로 표시) | `runbody/scoring.py` |
| 코칭 플래그 5개 | warmup_missing · positive_split · overstride · recovery_slower · acwr_high | `runbody/coaching.py` |
| 고칠 점 규칙 8개 | overpace_start > no_warmup > positive_split > acwr_high > recovery_slower > too_hard > overstride > all_good | `feedback.FIX_RULES` |
| 좋은 점 규칙 7개 | recovery_improved > negative_split > steady_pace > recovery_good > efficiency_kept > distance_up > finished | `feedback.GOOD_RULES` |
| 최근 부하(ACWR) | 7일 거리 ÷ (28일 거리 ÷ 4). 첫 러닝 28일 뒤부터, 0.8 미만 low · 1.5 초과 high | `runbody/load.py` |
| 개인 기준값 | 최근 10회 평균, 3회부터 | `runbody/load.py` |

체형 단계(아바타)는 백엔드가 정한다. 월간 결과의 `avatarInputs`(최근 14일 거리·러닝 수)는 그 입력값이다.

## PostgreSQL 저장소

`RUNKK_DB_URL`이 없으면 분석 결과를 메모리에만 둔다(재시작하면 사라진다). 있으면 같은 PostgreSQL의 `analytics` 스키마에 저장한다.

| 대상 | 내용 |
|---|---|
| `analytics.session_result` | 세션 1행. 상태(`pending`·`done`·`failed`)·사용자·시작 시각, 결과는 응답 JSON 그대로 `result`(jsonb) |
| `analytics.analysis_event` | 상태가 바뀔 때마다 1행(접수·완료·실패, 오류 코드) |
| `analytics.v_session_result` | 완료된 결과만. 거리·페이스·심박·5각 점수·헤드라인을 컬럼으로 꺼낸 뷰(api·코치 조회용) |

- 테이블은 Alembic(`runkk_ai/storage/migrations`)이 관리하고, ai가 **기동할 때 `upgrade head`를 스스로 돈다**. DB에 못 붙거나 마이그레이션이 실패하면 기동을 멈춘다.
- 스키마와 `runkk_ai` 계정은 ai가 만들지 않는다. EC2에서는 백엔드 Flyway가, 로컬에서는 `scripts/analytics-dev-init.sql`이 만든다. `runkk_ai`는 `analytics`를 소유하고 `public`(백엔드 테이블)은 읽기만 한다.
- 스키마를 바꿀 때는 `runkk_ai/storage/tables.py`와 새 리비전(`migrations/versions/`)을 함께 고친다. 둘이 어긋나면 `test_migration_matches_table_definitions`가 실패한다.

로컬에서 띄우기(백엔드 로컬 compose의 PostgreSQL 17을 같이 쓴다. 저장소 루트에서):

```bash
docker compose -f backend/compose.yaml up -d postgres
docker compose -f backend/compose.yaml exec -T postgres psql -U runkk -d runkk < ai/scripts/analytics-dev-init.sql

cd ai
export RUNKK_DB_URL=postgresql://runkk_ai:runkk_ai@localhost:5432/runkk   # 로컬 전용 비밀번호
uv run uvicorn --factory runkk_ai.main:create_app --port 8000             # 기동 때 테이블 생성
# 분석 2회를 보낸 뒤
docker compose -f backend/compose.yaml exec postgres psql -U runkk -d runkk \
  -c "select session_id, distance_meters, headline from analytics.v_session_result"
```

Windows에서 기동 때 `connection was closed in the middle of operation`이 나면 PC에 설치된 PostgreSQL 서비스가 5432를 함께 잡고 있는 경우다
(`netstat -ano | findstr :5432`에 PID가 둘). 관리자 PowerShell에서 `Stop-Service postgresql-x64-17`로 끄거나(`Get-Service *postgres*`로 이름 확인),
`docker run -p 55432:5432 …`처럼 다른 포트로 띄우고 주소의 포트를 바꾼다.

PostgreSQL 시험(시험마다 `analytics` 스키마를 지우고 다시 만든다. 개발 DB와 다른 DB를 쓴다):

```bash
docker compose -f ../backend/compose.yaml exec postgres createdb -U runkk runkk_test
RUNKK_TEST_DB_URL=postgresql://runkk:runkk@localhost:5432/runkk_test uv run pytest tests/storage
```

## 컨테이너

```bash
docker build -t runkk-ai ai
docker run --read-only --tmpfs /tmp -e RUNKK_INTERNAL_TOKEN=... runkk-ai
```

비루트 사용자(uid 10001)로 돈다. 셀카는 메모리에서만 다루므로 `/tmp`는 반드시 tmpfs로 마운트한다(D-59).
