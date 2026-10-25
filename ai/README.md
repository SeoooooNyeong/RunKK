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
uv run lint-imports          # core·comment·face·runbody 상호 독립, 모듈 안 라우터 → 서비스 → 하위 한 방향
uv run mypy
uv run pytest --cov=runkk_ai # 라인 커버리지 80% 미만이면 실패
```

Windows 콘솔에서 `lint-imports` 한글이 깨지면 `PYTHONIOENCODING=utf-8`을 붙인다.

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

분석은 별도 스레드에서 돈다(동시 2개, 한 번에 수십 ms). 결과 저장소는 아직 메모리라 재시작하면 빈다
(analytics 스키마 저장은 S15P31A207-163). 같은 `sessionId`를 다시 보내면 다시 계산하지 않고 현재 상태를 돌려준다.

## 컨테이너

```bash
docker build -t runkk-ai ai
docker run --read-only --tmpfs /tmp -e RUNKK_INTERNAL_TOKEN=... runkk-ai
```

비루트 사용자(uid 10001)로 돈다. 셀카는 메모리에서만 다루므로 `/tmp`는 반드시 tmpfs로 마운트한다(D-59).
