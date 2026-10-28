"""AI 코치 대화 기록 coach_message (S15P31A207-193).

Revision ID: v0002
Revises: v0001
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "v0002"
down_revision = "v0001"
branch_labels = None
depends_on = None

SCHEMA = "analytics"


def upgrade() -> None:
    op.create_table(
        "coach_message",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("conversation_id", sa.Text(), nullable=False),
        sa.Column("user_id", sa.Text(), nullable=False),
        sa.Column("role", sa.Text(), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("model", sa.Text()),
        sa.Column("input_tokens", sa.Integer()),
        sa.Column("output_tokens", sa.Integer()),
        sa.Column("tools", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint("role IN ('user', 'assistant')", name="ck_coach_message_role"),
        schema=SCHEMA,
    )
    op.create_index("ix_coach_message_conversation", "coach_message", ["conversation_id", "id"], schema=SCHEMA)
    op.create_index("ix_coach_message_user_created", "coach_message", ["user_id", "created_at"], schema=SCHEMA)


def downgrade() -> None:
    op.drop_table("coach_message", schema=SCHEMA)
