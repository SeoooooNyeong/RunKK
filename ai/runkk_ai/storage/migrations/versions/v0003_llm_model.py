"""writer 모델 등록부 llm_model (S15P31A207-197).

Revision ID: v0003
Revises: v0002
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "v0003"
down_revision = "v0002"
branch_labels = None
depends_on = None

SCHEMA = "analytics"


def upgrade() -> None:
    op.create_table(
        "llm_model",
        sa.Column("model_id", sa.Text(), primary_key=True),
        sa.Column("base_url", sa.Text(), nullable=False),
        sa.Column("note", sa.Text()),
        sa.Column("registered_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("activated_at", sa.DateTime(timezone=True)),
        sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.CheckConstraint("(active = false) OR (activated_at IS NOT NULL)", name="ck_llm_model_active_has_time"),
        schema=SCHEMA,
    )
    # 켜진 모델은 하나 이하
    op.create_index(
        "ux_llm_model_active", "llm_model", ["active"], unique=True, postgresql_where=sa.text("active"), schema=SCHEMA
    )


def downgrade() -> None:
    op.drop_table("llm_model", schema=SCHEMA)
