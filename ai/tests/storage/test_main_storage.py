"""main 조립: RUNKK_DB_URL이 있으면 기동 때 마이그레이션 후 PostgreSQL 저장소를 쓰고, 없으면 메모리를 쓴다."""

from __future__ import annotations

import httpx
import pytest
from sqlalchemy import text

from runkk_ai.main import create_app
from runkk_ai.runbody import InMemoryResultRepository
from runkk_ai.storage import PgResultRepository
from tests.conftest import AUTH, make_settings
from tests.runbody.conftest import load_body
from tests.storage.conftest import fresh_engine


async def test_without_db_url_uses_memory() -> None:
    app = create_app(make_settings())

    async with app.router.lifespan_context(app):
        assert isinstance(app.state.runbody_service._repository, InMemoryResultRepository)


async def test_with_db_url_migrates_and_stores_in_postgres() -> None:
    engine = await fresh_engine()  # 빈 analytics 스키마. 테이블은 기동이 만든다
    async with engine.begin() as conn:
        await conn.execute(text("DROP TABLE analytics.alembic_version, analytics.analysis_event CASCADE"))
        await conn.execute(text("DROP TABLE analytics.session_result CASCADE"))
    url = str(engine.url.render_as_string(hide_password=False))
    app = create_app(make_settings(db_url=url))

    async with app.router.lifespan_context(app):
        service = app.state.runbody_service
        assert isinstance(service._repository, PgResultRepository)
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://ai", headers=AUTH) as client:
            accepted = await client.post("/sessions/run-20261019/analyze", json=load_body("second_run"))
            await service.wait("run-20261019")
            fetched = await client.get("/sessions/run-20261019")

    async with engine.connect() as conn:
        count = (await conn.execute(text("SELECT count(*) FROM analytics.v_session_result"))).scalar_one()
    await engine.dispose()
    assert accepted.status_code == 202
    assert fetched.status_code == 200
    assert count == 1


async def test_unreachable_db_stops_startup() -> None:
    app = create_app(make_settings(db_url="postgresql://nobody:wrong@127.0.0.1:1/none"))

    with pytest.raises(ConnectionRefusedError):
        async with app.router.lifespan_context(app):
            pass  # pragma: no cover
