"""내부 토큰 검사. main이 CommentRouter·FaceRouter 등록 때 Depends로 건다(HealthRouter 제외)."""

from __future__ import annotations

import hmac
from typing import Annotated

from fastapi import Header, HTTPException, Request, status

from runkk_ai.core.config import Settings


async def verify_internal_token(
    request: Request,
    x_internal_token: Annotated[str | None, Header()] = None,
) -> None:
    """X-Internal-Token이 Settings.internal_token과 다르면 401."""
    settings: Settings = request.app.state.settings
    expected = settings.internal_token.get_secret_value().encode()
    given = (x_internal_token or "").encode()
    if not hmac.compare_digest(given, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="UNAUTHORIZED")
