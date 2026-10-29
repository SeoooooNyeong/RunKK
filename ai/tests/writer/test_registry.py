"""InMemoryModelRegistry: 등록·활성화·롤백. PostgreSQL 구현(tests/storage/test_model_store.py)과 같은 규약을 쓴다."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest

from runkk_ai.writer.registry import InMemoryModelRegistry, ModelNotFound, NoPreviousModel

T0 = datetime(2026, 10, 28, 12, 0, tzinfo=UTC)


def _clock() -> datetime:
    _clock.at += timedelta(minutes=1)  # type: ignore[attr-defined]
    return _clock.at  # type: ignore[attr-defined,no-any-return]


@pytest.fixture
def registry() -> InMemoryModelRegistry:
    _clock.at = T0  # type: ignore[attr-defined]
    return InMemoryModelRegistry(clock=_clock)


async def test_no_active_model_before_anything_is_registered(registry: InMemoryModelRegistry) -> None:
    assert await registry.active() is None
    assert await registry.models() == []


async def test_first_registered_model_becomes_active(registry: InMemoryModelRegistry) -> None:
    """첫 모델은 바로 켠다. 등록만 하고 아무것도 안 켜져 있으면 writer가 통째로 쉬게 된다."""
    registered = await registry.register("v1-qwen3-4b", "http://writer:8080", note="통과율 1.0")

    active = await registry.active()

    assert registered.active is True
    assert active is not None
    assert (active.model_id, active.base_url, active.note) == ("v1-qwen3-4b", "http://writer:8080", "통과율 1.0")
    assert active.activated_at is not None


async def test_second_model_waits_until_activated(registry: InMemoryModelRegistry) -> None:
    await registry.register("v1", "http://writer:8080")
    await registry.register("v2", "http://writer-v2:8080")

    active = await registry.active()

    assert active is not None
    assert active.model_id == "v1"
    assert [m.model_id for m in await registry.models()] == ["v1", "v2"]


async def test_activate_switches_and_leaves_exactly_one_active(registry: InMemoryModelRegistry) -> None:
    await registry.register("v1", "http://writer:8080")
    await registry.register("v2", "http://writer-v2:8080")

    activated = await registry.activate("v2")

    assert activated.model_id == "v2"
    assert [m.model_id for m in await registry.models() if m.active] == ["v2"]


async def test_rollback_returns_to_the_model_used_before(registry: InMemoryModelRegistry) -> None:
    await registry.register("v1", "http://writer:8080")
    await registry.register("v2", "http://writer-v2:8080")
    await registry.activate("v2")

    rolled_back = await registry.rollback()

    assert rolled_back.model_id == "v1"
    assert (await registry.active()).model_id == "v1"  # type: ignore[union-attr]


async def test_rollback_twice_goes_forward_again(registry: InMemoryModelRegistry) -> None:
    """롤백은 '직전에 켰던 모델로'다. 되돌린 뒤 다시 롤백하면 방금 내린 모델로 돌아간다."""
    await registry.register("v1", "http://writer:8080")
    await registry.register("v2", "http://writer-v2:8080")
    await registry.activate("v2")
    await registry.rollback()

    assert (await registry.rollback()).model_id == "v2"


async def test_rollback_without_a_previous_model_is_refused(registry: InMemoryModelRegistry) -> None:
    await registry.register("v1", "http://writer:8080")

    with pytest.raises(NoPreviousModel):
        await registry.rollback()


async def test_activating_an_unknown_model_is_refused(registry: InMemoryModelRegistry) -> None:
    with pytest.raises(ModelNotFound):
        await registry.activate("none")


async def test_registering_the_same_id_updates_it_without_switching(registry: InMemoryModelRegistry) -> None:
    """주소를 고쳐 다시 등록해도 지금 켜진 모델이 바뀌지는 않는다."""
    await registry.register("v1", "http://writer:8080")
    await registry.register("v2", "http://writer-v2:8080")
    await registry.activate("v2")

    again = await registry.register("v1", "http://writer-moved:8080", note="주소 변경")

    assert (again.base_url, again.active) == ("http://writer-moved:8080", False)
    assert (await registry.active()).model_id == "v2"  # type: ignore[union-attr]
