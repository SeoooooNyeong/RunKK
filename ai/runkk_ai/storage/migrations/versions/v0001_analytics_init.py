"""analytics 초기 테이블: session_result·analysis_event·뷰 v_session_result (S15P31A207-173).

스키마와 runkk_ai 계정은 백엔드 Flyway가 만든다. 여기서는 그 안의 테이블만 만든다.

Revision ID: v0001
Revises:
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import JSONB

revision = "v0001"
down_revision = None
branch_labels = None
depends_on = None

SCHEMA = "analytics"

# 완료된 분석만 보인다. api·코치가 자주 쓰는 값은 jsonb에서 꺼내 컬럼으로 둔다(원본은 result)
_VIEW = """
CREATE VIEW analytics.v_session_result AS
SELECT
    r.session_id,
    r.user_id,
    r.started_at,
    r.updated_at AS analyzed_at,
    r.engine_version,
    (r.result -> 'summary' ->> 'distanceMeters')::int AS distance_meters,
    (r.result -> 'summary' ->> 'activeMs')::bigint AS active_ms,
    (r.result -> 'summary' ->> 'paceSecondsPerKm')::int AS pace_seconds_per_km,
    (r.result -> 'summary' ->> 'avgHeartRate')::int AS avg_heart_rate,
    (r.result -> 'summary' ->> 'maxHeartRate')::int AS max_heart_rate,
    (r.result -> 'summary' ->> 'cadenceMedian')::int AS cadence_median,
    (r.result -> 'summary' ->> 'recoveryHeartRate1m')::int AS recovery_heart_rate_1m,
    (r.result -> 'axes' -> 'intensity' ->> 'score')::int AS intensity_score,
    (r.result -> 'axes' -> 'endurance' ->> 'score')::int AS endurance_score,
    (r.result -> 'axes' -> 'stability' ->> 'score')::int AS stability_score,
    (r.result -> 'axes' -> 'form' ->> 'score')::int AS form_score,
    (r.result -> 'axes' -> 'fitness' ->> 'score')::int AS fitness_score,
    r.result -> 'coaching' -> 'flags' AS coaching_flags,
    r.result -> 'feedback' ->> 'headline' AS headline,
    r.result -> 'feedback' ->> 'ruleId' AS rule_id,
    r.result
FROM analytics.session_result r
WHERE r.status = 'done'
"""


def upgrade() -> None:
    op.create_table(
        "session_result",
        sa.Column("session_id", sa.Text(), primary_key=True),
        sa.Column("user_id", sa.Text(), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("status", sa.Text(), nullable=False),
        sa.Column("error_code", sa.Text()),
        sa.Column("engine_version", sa.Text()),
        sa.Column("result", JSONB()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint("status IN ('pending', 'done', 'failed')", name="ck_session_result_status"),
        sa.CheckConstraint("(status = 'done') = (result IS NOT NULL)", name="ck_session_result_done_has_result"),
        schema=SCHEMA,
    )
    op.create_index(
        "ix_session_result_user_done_started",
        "session_result",
        ["user_id", "started_at"],
        schema=SCHEMA,
        postgresql_where=sa.text("status = 'done'"),
    )
    op.create_table(
        "analysis_event",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column(
            "session_id",
            sa.Text(),
            sa.ForeignKey(f"{SCHEMA}.session_result.session_id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("status", sa.Text(), nullable=False),
        sa.Column("error_code", sa.Text()),
        sa.Column("at", sa.DateTime(timezone=True), nullable=False),
        schema=SCHEMA,
    )
    op.create_index("ix_analysis_event_session", "analysis_event", ["session_id"], schema=SCHEMA)
    op.execute(_VIEW)


def downgrade() -> None:
    op.execute("DROP VIEW IF EXISTS analytics.v_session_result")
    op.drop_table("analysis_event", schema=SCHEMA)
    op.drop_table("session_result", schema=SCHEMA)
