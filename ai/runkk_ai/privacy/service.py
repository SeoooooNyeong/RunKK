"""파기 조정. 등록된 저장소마다 purge_user를 부르고 지운 수를 모은다.

하나라도 실패하면 전체를 실패로 알린다(api가 재시도하거나 파기 배치에서 다시 부른다).
이미 지운 저장소는 다시 불러도 0이라 재시도가 안전하다. 로그에는 결과와 지운 수만 남기고 회원 식별자는 남기지 않는다.
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Mapping
from typing import Literal, Protocol

logger = logging.getLogger(__name__)

PurgeFailure = Literal["STORAGE_UNAVAILABLE", "STORAGE_TIMEOUT"]


class UserDataPurger(Protocol):
    async def purge_user(self, user_id: str) -> int:
        """user_id의 행을 모두 지우고 지운 수를 돌려준다. 없으면 0."""
        ...


class PurgeError(Exception):
    def __init__(self, reason: PurgeFailure) -> None:
        super().__init__(reason)
        self.reason: PurgeFailure = reason


class WithdrawalService:
    def __init__(self, purgers: Mapping[str, UserDataPurger], timeout_s: float) -> None:
        self._purgers = dict(purgers)
        self._timeout_s = timeout_s

    async def purge(self, user_id: str) -> dict[str, int]:
        started = time.perf_counter()
        deleted: dict[str, int] = {}
        detail = ""
        error: PurgeError | None = None
        try:
            async with asyncio.timeout(self._timeout_s):
                for name, purger in self._purgers.items():
                    deleted[name] = await purger.purge_user(user_id)
        except TimeoutError:
            error = PurgeError("STORAGE_TIMEOUT")
        except Exception as exc:
            detail = type(exc).__name__  # 예외 문구에는 DB 주소·식별자가 섞일 수 있어 남기지 않는다
            error = PurgeError("STORAGE_UNAVAILABLE")
        logger.info(
            "user_purge result=%s detail=%s %s latency_ms=%d",
            error.reason if error else "ok",
            detail,
            " ".join(f"{name}={count}" for name, count in deleted.items()),
            int((time.perf_counter() - started) * 1000),
        )
        if error is not None:
            raise error
        return deleted
