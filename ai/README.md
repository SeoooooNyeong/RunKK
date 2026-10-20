# runkk-ai

런크크 ai 서버(Python 3.12, FastAPI). api 컨테이너만 부르는 내부 서비스다. 설계: design-oo 6.1.16, 13.3, D-58~D-62.

| 엔드포인트 | 내용 |
|---|---|
| `GET /health` | 상태 확인(토큰 검사 없음) |
| `POST /comments` | 체력 요약 집계값 → AI 코멘트. 200 `{"text"}` / 503·504·422 `{"reason"}` |
| `POST /faces` | 셀카(`application/octet-stream`) → 부위별 base64 PNG. 200 `{"parts"}` / 413·415·422·503·504 `{"reason"}` |

`/comments`·`/faces`는 `X-Internal-Token` 헤더가 필요하다. 응답 표본은 `tests/contract/*.json`(서버 Gateway 계약 시험이 같이 쓴다).

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
uv run lint-imports          # core·comment·face 상호 독립, 모듈 안 라우터 → 서비스 → 클라이언트
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

## 컨테이너

```bash
docker build -t runkk-ai ai
docker run --read-only --tmpfs /tmp -e RUNKK_INTERNAL_TOKEN=... runkk-ai
```

비루트 사용자(uid 10001)로 돈다. 셀카는 메모리에서만 다루므로 `/tmp`는 반드시 tmpfs로 마운트한다(D-59).
