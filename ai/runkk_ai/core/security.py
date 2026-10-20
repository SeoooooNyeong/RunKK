"""내부 토큰 검사(5.4). main이 앱 전체에 미들웨어로 건다. 공개 경로(/health 등)만 검사하지 않는다.

FastAPI 의존성(Depends)은 요청 본문을 읽고 JSON을 파싱한 뒤에 돌아, 토큰 없는 깨진 JSON이 401이 아니라 422를 받고
본문이 크기 상한 없이 메모리에 올라간다. 순수 ASGI 미들웨어로 본문을 읽기 전에 끊는다(L15).
새 라우터는 따로 걸지 않아도 검사를 받는다(허용 목록 방식).
"""

from __future__ import annotations

import hmac
from collections.abc import Collection

from pydantic import SecretStr
from starlette.responses import JSONResponse
from starlette.status import HTTP_401_UNAUTHORIZED
from starlette.types import ASGIApp, Receive, Scope, Send

_TOKEN_HEADER = b"x-internal-token"  # ASGI 헤더 이름은 소문자 bytes
UNAUTHORIZED_REASON = "UNAUTHORIZED"


class InternalTokenMiddleware:
    """X-Internal-Token이 Settings.internal_token과 다르면 401 {"reason": "UNAUTHORIZED"}. 상수 시간 비교."""

    def __init__(self, app: ASGIApp, token: SecretStr, public_paths: Collection[str]) -> None:
        self._app = app
        self._expected = token.get_secret_value().encode()
        self._public_paths = frozenset(public_paths)

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http" and scope["path"] not in self._public_paths and not self._authorized(scope):
            response = JSONResponse(status_code=HTTP_401_UNAUTHORIZED, content={"reason": UNAUTHORIZED_REASON})
            await response(scope, receive, send)
            return
        await self._app(scope, receive, send)

    def _authorized(self, scope: Scope) -> bool:
        given = next((value for name, value in scope["headers"] if name == _TOKEN_HEADER), b"")
        return hmac.compare_digest(given, self._expected)
