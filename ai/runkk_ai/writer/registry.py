"""writer 모델 등록부(S15P31A207-197). 어느 모델을 쓸지 고르고, 문제가 생기면 직전 모델로 되돌린다.

llama.cpp 서버는 기동할 때 모델 파일 하나로 고정된다. 그래서 '활성화'는 파일을 바꿔 끼우는 게 아니라
ai가 어느 writer 주소를 부를지 바꾸는 것이다. 새 모델을 올릴 때 writer 컨테이너를 하나 더 띄워 두면
재배포 없이 전환·롤백이 된다(컨테이너가 하나뿐이면 주소가 같은 기록 하나만 두고 쓴다).

- 켜진 모델은 항상 하나 이하다. 없으면 writer를 쓰지 않고 템플릿 문장을 그대로 둔다.
- 롤백은 '직전에 켰던 모델로' 돌아간다. 되돌린 뒤 다시 롤백하면 방금 내린 모델로 간다.
- 메모리 구현은 재시작하면 빈다. PostgreSQL 구현은 runkk_ai.storage.model_store(analytics.llm_model).
"""

from __future__ import annotations

import asyncio
from collections.abc import Callable
from dataclasses import dataclass, replace
from datetime import UTC, datetime
from typing import Protocol

MODEL_ID_MAX = 64
BASE_URL_MAX = 200
NOTE_MAX = 500


class ModelNotFound(Exception):
    """등록하지 않은 모델을 켜려고 했다."""


class NoPreviousModel(Exception):
    """되돌아갈 모델이 없다(지금 모델이 처음 켠 모델이다)."""


@dataclass(frozen=True, slots=True)
class WriterModel:
    model_id: str
    base_url: str
    note: str | None
    registered_at: datetime
    activated_at: datetime | None  # 한 번도 켠 적이 없으면 None
    active: bool


class ModelRegistry(Protocol):
    async def models(self) -> list[WriterModel]:
        """등록한 순서대로."""
        ...

    async def active(self) -> WriterModel | None:
        """지금 켜진 모델. 없으면 None(writer를 쓰지 않는다)."""
        ...

    async def register(self, model_id: str, base_url: str, note: str | None = None) -> WriterModel:
        """새 모델을 등록한다. 첫 모델은 바로 켜고, 이미 켜진 모델이 있으면 꺼진 채로 둔다.

        같은 model_id를 다시 등록하면 주소·메모만 고치고 켜진 모델은 바꾸지 않는다.
        """
        ...

    async def activate(self, model_id: str) -> WriterModel:
        """켠다. 모르는 model_id면 ModelNotFound."""
        ...

    async def rollback(self) -> WriterModel:
        """직전에 켰던 모델로 되돌린다. 없으면 NoPreviousModel."""
        ...


def _now() -> datetime:
    return datetime.now(UTC)


def previous_id(models: list[WriterModel]) -> str | None:
    """직전에 켰던 모델. 지금 꺼져 있으면서 켠 적이 있는 것 중 가장 나중에 켠 것."""
    candidates = [m for m in models if not m.active and m.activated_at is not None]
    if not candidates:
        return None
    return max(candidates, key=lambda m: m.activated_at or datetime.min.replace(tzinfo=UTC)).model_id


class InMemoryModelRegistry:
    def __init__(self, *, clock: Callable[[], datetime] = _now) -> None:
        self._models: dict[str, WriterModel] = {}
        self._clock = clock
        self._lock = asyncio.Lock()

    async def models(self) -> list[WriterModel]:
        return list(self._models.values())

    async def active(self) -> WriterModel | None:
        return next((m for m in self._models.values() if m.active), None)

    async def register(self, model_id: str, base_url: str, note: str | None = None) -> WriterModel:
        async with self._lock:
            existing = self._models.get(model_id)
            if existing is not None:
                updated = replace(existing, base_url=base_url, note=note)
            else:
                first = not self._models
                now = self._clock()
                updated = WriterModel(
                    model_id=model_id,
                    base_url=base_url,
                    note=note,
                    registered_at=now,
                    activated_at=now if first else None,
                    active=first,
                )
            self._models[model_id] = updated
            return updated

    async def activate(self, model_id: str) -> WriterModel:
        async with self._lock:
            if model_id not in self._models:
                raise ModelNotFound(model_id)
            now = self._clock()
            for key, model in self._models.items():
                if model.active and key != model_id:
                    self._models[key] = replace(model, active=False)
            activated = replace(self._models[model_id], active=True, activated_at=now)
            self._models[model_id] = activated
            return activated

    async def rollback(self) -> WriterModel:
        target = previous_id(list(self._models.values()))
        if target is None:
            raise NoPreviousModel
        return await self.activate(target)
