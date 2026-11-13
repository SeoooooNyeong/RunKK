# runkk-ai

런크크 ai 서버(Python 3.12, FastAPI). api 컨테이너만 부르는 내부 서비스다. 설계: design-oo 6.1.16, 13.3, D-58~D-62.

| 엔드포인트 | 내용 |
|---|---|
| `GET /health` | 상태 확인(토큰 검사 없음) |
| `POST /comments` | 체력 요약 집계값 → AI 코멘트. 200 `{"text"}` / 503·504·422 `{"reason"}` |
| `POST /users/{userId}/faces/jobs[?attempt=n]` | 셀카(`application/octet-stream`) → 아바타 머리 생성 작업 접수. `attempt`(1~20, 기본 1)는 같은 사진의 명시적 재시도 번호로 Node 멱등 키를 바꾼다(1이면 예전 키, DV-성-9). 202 `{"jobId","status","headId","error"}` / 413·415·422·503·504 `{"reason"}` |
| `GET /users/{userId}/faces/jobs/{jobId}` | 작업 상태 `QUEUED`·`RUNNING`·`SUCCEEDED`·`FAILED`(실패도 200 + `error.code`). 404 `JOB_NOT_FOUND` |
| `GET /users/{userId}/faces/jobs/{jobId}/result` | 완성된 머리: `faceImage`·`eyesImage`·`hairImage`(1024×1024 PNG Data URL)와 `traits`·`alignment`·`mobile`. 404 / 409 `JOB_NOT_READY` |
| `GET /users/{userId}/faces/latest` | 사용자의 가장 최근 머리. 404 `HEAD_NOT_FOUND` |
| `GET /avatar/bodies/{bodyId}` | 기본 몸 프리셋(`pack.mobile`로 앱이 머리와 합성). 404 `BODY_NOT_FOUND` |
| `POST /sessions/{sessionId}/analyze` | 러닝 1회 원본(1초 센서·GPS·회복 심박) → RUN바디 분석 접수. 202 `{"sessionId","status","engineVersion"}` / 409·413·422 `{"reason"}` |
| `GET /sessions/{sessionId}/status` | 분석 상태 `pending`·`done`·`failed`. 200 / 404 `{"reason"}` |
| `GET /sessions/{sessionId}` | RUN바디 1회 결과(5각 점수·판정 라벨·코칭 플래그·km 랩·피드백 문장). 200 / 404·409 `{"reason"}` |
| `GET /users/{userId}/months/{yearMonth}` | 월간 RUN바디(월 요약·5각 평균·최고 기록·주간 거리·최근 부하·개인 기준값·아바타 입력값). 200 / 422 `{"reason"}` |
| `DELETE /users/{userId}` | 탈퇴 회원 파기(-219·-257). 분석 결과·코치 대화·아바타 서버 폴더를 지우고 `{"sessionResults": n, "coachMessages": m, "avatarImages": 0\|1}`(아바타는 켠 환경에서만). 멱등(없어도 200에 0) / 503 `STORAGE_UNAVAILABLE` · 504 `STORAGE_TIMEOUT` |
| `POST /coach/chat` | AI 코치 대화(F5). `{"userId","conversationId"?,"message"}` → 200 `text/event-stream`(meta·tool·delta·done·error) / 404·413·422·429·503 `{"reason"}` |

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

제공자 키(`RUNKK_ANTHROPIC_API_KEY` 등)·아바타 서버 주소가 없어도 뜬다. 그때 `/comments`는 503 `LLM_UNAVAILABLE`,
`/users/{userId}/faces/*`는 503 `UPSTREAM_FAILED`를 돌려주고 api가 규칙 문구·기본 캐릭터로 대체한다. 환경 변수 목록은 `.env.example`.

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

## AI 코치 도구 (S15P31A207-192)

AI 코치(F5)가 질문에 답할 때 API 모델이 고르는 조회 도구다. 코드는 `runkk_ai/coach/tools.py`(`CoachTools`, `TOOL_SPECS`).
대화 API는 아래 "AI 코치 대화" 절.

| 도구 | 입력 | 돌려주는 것 |
|---|---|---|
| `getSessionResult` | `sessionId`(비우면 최근) | 러닝 1회: 거리·페이스·심박·5각 판정·코칭 신호·km 랩·피드백·지난 러닝 비교 |
| `getMonthlyResult` | `yearMonth`(비우면 이번 달) | 월 요약: 러닝 수·총거리·평균 페이스·5각 평균·최고 기록·주간 거리·부하·기준값 |
| `getBaseline` | 없음 | 개인 기준값(최근 10회 평균)·최근 5회 목록·최근 부하(ACWR) |

- 사용자 ID는 모델 입력이 아니라 서버가 넣는다(api가 인증한 사용자). 다른 사람의 세션은 `SESSION_NOT_FOUND`로 답한다.
- 응답은 2,000자 안으로 줄인다(페이스 `6'05"`, 날짜는 한국 시간). 실패는 `{"error": 코드}`(`NO_RUNS`, `INVALID_YEAR_MONTH`, `UNKNOWN_TOOL`).
- 저장소 규약 위에서 읽으므로 메모리 저장소와 analytics 스키마에서 똑같이 동작한다.

## AI 코치 대화 (S15P31A207-193)

`POST /coach/chat`은 질문을 받아 API 모델(기본 `claude-haiku-5-5`)이 위 도구를 골라 기록을 확인한 뒤 답을 글자 단위로 흘려보낸다(SSE).

| 이벤트 | data | 뜻 |
|---|---|---|
| `meta` | `{"conversationId"}` | 맨 처음. 새 대화면 새 ID |
| `tool` | `{"name"}` | 도구를 부를 때마다(앱은 "기록 확인 중"을 띄운다) |
| `delta` | `{"text"}` | 답 조각 |
| `done` | `{"model","inputTokens","outputTokens","tools"}` | 정상 끝 |
| `error` | `{"reason"}` | 중간 실패(`LLM_TIMEOUT`, `LLM_ERROR`). 받은 delta는 버린다 |

- 시작 전 거절: 키 없음 503 `LLM_UNAVAILABLE`, 남의 대화 404 `CONVERSATION_NOT_FOUND`, 하루 상한 429 `QUOTA_EXCEEDED`.
- 크레딧 보호: 하루 상한(전체 `RUNKK_COACH_DAILY_HARD_CAP`=300, 사용자별 `RUNKK_COACH_USER_DAILY_CAP`=30, 한국 날짜 기준). 도구 turn은 3번까지, 이전 대화는 최근 10개 메시지만 다시 보낸다.
- 대화 기록: 질문과 최종 답만 `analytics.coach_message`(DB 없으면 메모리)에 남긴다. 토큰 수·모델·부른 도구도 같이 남겨 단가를 확인한다.
- 키는 `RUNKK_ANTHROPIC_API_KEY`, GMS 주소는 `ANTHROPIC_BASE_URL`을 `/comments`와 같이 쓴다.

로컬에서 해 보기(분석 2회를 먼저 보낸 뒤, Windows는 `curl.exe`):

```bash
curl -N localhost:8000/coach/chat -H "X-Internal-Token: $RUNKK_INTERNAL_TOKEN" -H 'Content-Type: application/json' \
  -d '{"userId":"u_demo","message":"어제 러닝 어땠어?"}'
```

## writer 학습 데이터 (S15P31A207-191)

자체 모델 runkk-writer가 배울 (분석 요약 → 코칭 문장) 쌍을 만든다. 코드는 `runkk_ai/writer/`.

| 단계 | 명령 | 결과(`data/writer/`, git에 올리지 않음) |
|---|---|---|
| 1. 상황 | `uv run python -m runkk_ai.writer.dataset situations --total 1000` | `situations.jsonl`: 실측 2회를 변형해 엔진으로 분석한 상황. 고칠 점 규칙 8개가 고르게 섞인다 |
| 2. 초안 | `uv run python -m runkk_ai.writer.dataset draft --model claude-opus-5-5` | `drafts.jsonl`: API 모델(GMS)이 쓴 문장. 끊겨도 다시 돌리면 남은 것만 묻는다 |
| 3. 검사 | `uv run python -m runkk_ai.writer.dataset check --eval 100` | `train.jsonl`(학습, 대화 형식), `eval.jsonl`(홀드아웃), `report.json`(탈락 이유·규칙별 개수) |

- 초안 단계는 `RUNKK_ANTHROPIC_API_KEY`(GMS 키)와 `ANTHROPIC_BASE_URL`(GMS 주소)이 필요하다. 먼저 `draft --limit 5`로 문장을 보고 지시문(`writer/prompt.py`)을 다듬은 뒤 전부 돌린다.
- writer는 판단하지 않는다. 엔진 규칙이 고른 고칠 점·좋은 점과 템플릿 문장(`brief.template`)을 받아 자연스럽게 다시 쓴다. 숫자는 요약에 있는 것만 쓴다(`writer/checks.py`가 지어낸 숫자를 걸러 낸다).
- 지시문이나 요약 형식을 바꾸면 `PROMPT_VERSION`·`BRIEF_VERSION`을 올리고 데이터를 다시 만든다.

## writer 모델 서버 (S15P31A207-196)

학습한 runkk-writer(Qwen3-4B QLoRA → GGUF Q4_K_M, 약 2.5GB, `notebooks/writer_qlora.ipynb`)를 llama.cpp 서버로 CPU에서 돌려
분석 피드백 네 문장(headline·good·fix·next)을 자연스럽게 다시 쓴다. 코드는 `writer/client.py`·`writer/service.py`.

- 흐름: 계산 → writer(검사 → 실패 시 1번 더 → 그래도 실패면 템플릿) → `done` 저장. 앱은 `done`이 된 결과를 한 번 읽으면 된다.
  writer가 바꾼 결과는 `feedback.source = "writer"`, `feedback.modelVersion`에 모델 버전이 들어간다. 판단(`ruleId` 등)은 바꾸지 않는다.
- 한 번에 한 건만 보낸다. 앞 요청 대기까지 합쳐 `RUNKK_WRITER_TIMEOUT_S`(기본 60초)를 넘기면 템플릿으로 끝낸다.
- `RUNKK_WRITER_URL`이 비었거나 writer가 꺼져 있으면 템플릿 그대로다(분석은 실패하지 않는다). 로그 `writer_polish result=…`로 비율을 본다.
- 프롬프트는 학습 때와 같이 assistant 차례를 빈 think 블록까지 열어 두고, 출력은 `json_schema`로 키 4개·길이 상한을 강제한다.

로컬에서 띄워 보기(Docker, 모델 파일은 `data/writer/`에):

```bash
docker run --rm -p 8081:8080 -v "$PWD/data/writer:/models" ghcr.io/ggml-org/llama.cpp:server \
  -m /models/runkk-writer-v1-qwen3-4b-q4_k_m.gguf -c 2048 -np 1 -t 4 --host 0.0.0.0
export RUNKK_WRITER_URL=http://localhost:8081   # 그다음 ai 서버를 띄우고 분석을 보낸다
```

측정(PC, CPU 4스레드, eval 10건): 1건 평균 25.8초(최대 31.4초), 9.4 tok/s, 검사 10/10 통과, 출력은 모두 `{`로 시작.

서버 배치는 compose `writer` 서비스(프로필 `writer`)다. `infra/.env`에 `COMPOSE_PROFILES=writer`, `RUNKK_WRITER_URL=http://writer:8080`을 넣고
모델 파일을 `WRITER_MODEL_DIR`(기본 `/srv/runkk/models`)에 올린 환경에서만 뜬다. 메모리 상한 3.5GB.

## writer 모델 관리 (S15P31A207-197)

어느 모델을 쓸지는 등록부(`analytics.llm_model`)가 정한다. 운영자가 직접 부른다(앱·api가 아니라 사람이 쓰는 API다).

llama.cpp 서버는 기동할 때 모델 파일 하나로 고정된다. 그래서 **활성화는 파일을 바꿔 끼우는 게 아니라 ai가 어느 writer 주소를 부를지 바꾸는 것**이다.
새 모델을 올릴 때 writer 컨테이너를 하나 더 띄워 두면(compose `writer-v2` 등) 재배포 없이 전환·롤백이 된다.

| 연산 | 뜻 |
|---|---|
| `GET /models` | 등록한 모델과 지금 켜진 모델 |
| `POST /models` | 등록. `{"modelId", "baseUrl", "note"}`. 첫 모델은 바로 켜지고, 같은 id를 다시 등록하면 주소·메모만 고친다 |
| `POST /models/{modelId}/activate` | 켠다. 모르는 id면 404 `MODEL_NOT_FOUND` |
| `POST /models/rollback` | 직전에 켰던 모델로 되돌린다. 없으면 409 `NO_PREVIOUS_MODEL` |

```bash
T=$RUNKK_INTERNAL_TOKEN
curl -X POST localhost:8000/models -H "X-Internal-Token: $T" -H 'Content-Type: application/json' \
  -d '{"modelId":"v2-qwen3-1.7b","baseUrl":"http://writer-v2:8080","note":"통과율 0.97, 1건 30초"}'
curl -X POST localhost:8000/models/v2-qwen3-1.7b/activate -H "X-Internal-Token: $T"   # 새 모델로
curl -X POST localhost:8000/models/rollback -H "X-Internal-Token: $T"                 # 문제 있으면 되돌리기
curl localhost:8000/models -H "X-Internal-Token: $T"
```

- 켜진 모델은 **하나 이하**다(부분 유일 인덱스가 DB에서도 지킨다). 없으면 writer를 부르지 않고 템플릿 문장을 그대로 둔다.
- `RUNKK_WRITER_URL`이 **writer 전체 스위치**다. 비우면 등록부에 뭐가 있든 writer를 쓰지 않는다. 값이 있으면 기동할 때
  `RUNKK_WRITER_MODEL_VERSION` 이름으로 등록한다(처음이면 바로 켜지고, 이미 켠 모델이 있으면 주소만 고친다).
- 그래서 운영자가 바꿔 둔 모델은 **재시작해도 그대로 남는다**(`RUNKK_DB_URL`이 있을 때. 메모리 저장소면 재시작마다 환경 변수 모델로 돌아간다).
- 폴백은 그대로다: 켜진 모델 → (검사 실패 시 1번 더) → 템플릿 문장.

## 아바타 중계 (S15P31A207-164)

얼굴은 ai가 만들지 않는다. 안선영 님의 Node 아바타 서버(명세 `RunKK_Node_API_Spec_v1.2`, 기준 경로 `/api/v1`)가
셀카 → 특징 분석(gpt-4.1-mini) → 머리 1장 생성(gpt-image-2.5) → 코드 마스크로 **face(코·입·귀·피부)·eyes·hair(눈썹 포함)** 3레이어로
나눠 저장하고, ai는 그 앞에서 입력 검사와 오류 형식 통일만 한다. 한 건에 약 54초가 걸려 **비동기**다(분석과 같은 폴링 패턴).

```
api  POST /users/{userId}/faces/jobs (셀카)      → ai → Node POST /head-jobs (multipart userId·clientRequestId·selfie) → 202 jobId
api  GET  /users/{userId}/faces/jobs/{jobId}     → ai → Node GET /head-jobs/{id}?userId=    (SUCCEEDED가 될 때까지 폴링)
api  GET  /users/{userId}/faces/jobs/{jobId}/result → ai → Node GET /head-jobs/{id}/result?userId=
api  GET  /users/{userId}/faces/latest           → ai → Node GET /heads/latest?userId=
api  GET  /avatar/bodies/{bodyId}                → ai → Node GET /bodies/{id}
```

- `clientRequestId`는 ai가 `uuid5(userId:sha256(셀카))`로 만든다. 같은 사용자가 같은 셀카를 다시 보내면 Node가 같은 작업을 돌려줘 이미지 생성 과금이 겹치지 않는다.
- Node 오류 `code`는 `JOB_NOT_FOUND`·`HEAD_NOT_FOUND`·`BODY_NOT_FOUND`(404)·`JOB_NOT_READY`(409)·`UPSTREAM_TIMEOUT`(504 `TIMEOUT`)만 그대로 옮기고 나머지는 503 `UPSTREAM_FAILED`다.
  작업 자체의 실패(`SELFIE_QUALITY_REJECTED`, `NO_FACE_DETECTED` 등)는 HTTP 오류가 아니라 상태 조회 200의 `error.code`로 온다.
- 셀카는 메모리에서만 다루고(UploadFile·임시 파일 없음) 로그에는 사용자 식별자·이미지를 남기지 않는다. `metrics`(과금 지표)는 응답에서 뺀다.
- 설정은 `RUNKK_FACE_API_URL` 하나다(compose `avatar` 프로필을 켠 환경에서 `http://avatar:3000/api/v1`). 컨테이너 구성은 `infra/docker-compose.yml`의 `avatar` 서비스.

## 탈퇴 회원 파기 (S15P31A207-219)

분석 결과(`analytics.session_result`·`analysis_event`)와 코치 대화(`coach_message`)는 회원별 개인정보라 처리방침의 파기 대상이다.
테이블은 ai가 Alembic으로 관리하므로 **api가 직접 지우지 않고** `DELETE /users/{userId}`를 부른다.

- api는 `MemberWithdrawn` 리스너와 파기 배치(잔여 7일 뒤)에서 부른다. **멱등**이라 두 번 불러도 안전하고, 503이면 다시 부르면 된다
  (한 저장소라도 실패하면 503이고, 이미 지운 저장소는 다음 호출에 0을 돌려준다).
- 응답의 숫자는 지운 행 수다. 로그에는 결과와 행 수만 남고 회원 식별자는 남지 않는다.
- 백업 덤프(infra `backup`, 7일 보존)에는 파기 전 행이 남을 수 있다. 처리방침의 "백업본 7일 보존" 문구가 이걸 가리킨다.
- **아바타 서버(-257)**: `RUNKK_FACE_API_URL`을 켠 환경이면 Node `DELETE /api/v1/users/{userId}`도 부른다. Node는 사용자 폴더
  (`data/users/{sha256(userId)}`: 셀카 작업·머리 레이어·우측 얼굴·latest)를 통째로 지운다. 그 사용자의 생성이 진행 중이면 409 `USER_BUSY`라
  ai는 503을 돌려주고, api가 파기 배치에서 다시 부르면 지워진다. 대기 중인 작업은 큐에서 빠져 생성되지 않는다.
- 새 회원별 테이블을 만들면 `main.py`의 `WithdrawalService` 등록에 저장소를 더한다(`purge_user(user_id)`를 구현하면 된다).

## 코치 대화 보관 기한 (S15P31A207-258)

처리방침(D-436)대로 코치 대화 본문(`analytics.coach_message`)은 **90일**이 지나면 지운다. ai가 기동할 때 한 번, 그 뒤 6시간마다
`created_at < 지금 - 90일`인 메시지를 지운다(`coach/retention.py`, 로그 `coach_retention result=ok deleted=n`).
기한은 처리방침에 묶인 값이라 환경 변수로 열지 않는다. 지우다 실패하면 경고 로그만 남기고 다음 주기에 다시 지운다.
대화의 모든 메시지가 지워지면 그 `conversationId`는 없는 대화(404)가 된다.

## 컨테이너

```bash
docker build -t runkk-ai ai
docker run --read-only --tmpfs /tmp -e RUNKK_INTERNAL_TOKEN=... runkk-ai
```

비루트 사용자(uid 10001)로 돈다. 셀카는 메모리에서만 다루므로 `/tmp`는 반드시 tmpfs로 마운트한다(D-59).
