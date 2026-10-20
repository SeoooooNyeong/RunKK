# 런크크 ai 서버 (FastAPI). api 컨테이너만 부르는 내부 서비스라 compose에서 ports 없이 expose만 한다(5.4).
# 컨테이너 /tmp는 tmpfs로 마운트한다(셀카가 디스크에 남을 길을 막는다, D-59). 이미지에는 소스만 두고 쓰기 경로를 만들지 않는다.

FROM python:3.12-slim AS build
RUN pip install --no-cache-dir uv==0.12.5
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    UV_PROJECT_ENVIRONMENT=/app/.venv
WORKDIR /src
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY runkk_ai ./runkk_ai
RUN uv sync --frozen --no-dev --no-editable

FROM python:3.12-slim
RUN groupadd --system --gid 10001 runkk \
    && useradd --system --uid 10001 --gid runkk --no-create-home --shell /usr/sbin/nologin runkk
COPY --from=build /app/.venv /app/.venv
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /app
USER runkk
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2)"]
CMD ["uvicorn", "--factory", "runkk_ai.main:create_app", "--host", "0.0.0.0", "--port", "8000", "--no-server-header"]
