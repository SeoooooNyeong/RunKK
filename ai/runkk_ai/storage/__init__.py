"""분석 결과 저장소의 PostgreSQL 구현(analytics 스키마, S15P31A207-163).

runbody는 이 모듈을 모른다. runbody.repository.ResultRepository 규약을 여기서 구현하고 main이 끼운다.
테이블은 Alembic(migrations/)이 관리한다. 스키마와 runkk_ai 계정은 백엔드 Flyway가 만든다.
"""

from runkk_ai.storage.engine import create_engine
from runkk_ai.storage.migrate import upgrade
from runkk_ai.storage.pg_repository import PgResultRepository

__all__ = ["PgResultRepository", "create_engine", "upgrade"]
