"""analytics 스키마 테이블(SQLAlchemy Core). 저장소 코드가 쿼리를 만들 때 쓴다.

스키마 변경은 이 파일과 migrations/versions의 새 리비전을 함께 고친다(리비전은 이 파일을 import하지 않는다).
결과 본문은 응답 JSON 그대로 jsonb 한 칸에 둔다. 응답 형식이 곧 저장 형식이라 엔진이 바뀌어도 테이블을 고치지 않는다.
api·코치가 자주 읽는 값은 뷰 v_session_result가 jsonb에서 꺼내 준다.
"""

from __future__ import annotations

from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    Column,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    MetaData,
    Table,
    Text,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB

SCHEMA = "analytics"

metadata = MetaData(schema=SCHEMA)

session_result = Table(
    "session_result",
    metadata,
    Column("session_id", Text, primary_key=True),
    Column("user_id", Text, nullable=False),
    Column("started_at", DateTime(timezone=True), nullable=False),
    Column("status", Text, nullable=False),
    Column("error_code", Text),
    Column("engine_version", Text),
    Column("result", JSONB(none_as_null=True)),  # None은 JSON null이 아니라 SQL NULL
    Column("created_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("updated_at", DateTime(timezone=True), nullable=False),
    CheckConstraint("status IN ('pending', 'done', 'failed')", name="ck_session_result_status"),
    CheckConstraint("(status = 'done') = (result IS NOT NULL)", name="ck_session_result_done_has_result"),
    # 이전 기록·월간·부하 조회는 모두 '이 사용자의 완료된 러닝을 시작 시각 순으로'다
    Index(
        "ix_session_result_user_done_started",
        "user_id",
        "started_at",
        postgresql_where=text("status = 'done'"),
    ),
)

analysis_event = Table(
    "analysis_event",
    metadata,
    Column("id", BigInteger, primary_key=True, autoincrement=True),
    Column(
        "session_id",
        Text,
        ForeignKey(f"{SCHEMA}.session_result.session_id", ondelete="CASCADE"),
        nullable=False,
    ),
    Column("status", Text, nullable=False),
    Column("error_code", Text),
    Column("at", DateTime(timezone=True), nullable=False),
    Index("ix_analysis_event_session", "session_id"),
)

# AI 코치 대화(S15P31A207-193). 사용자 질문과 코치의 최종 답만 남긴다
coach_message = Table(
    "coach_message",
    metadata,
    Column("id", BigInteger, primary_key=True, autoincrement=True),
    Column("conversation_id", Text, nullable=False),
    Column("user_id", Text, nullable=False),
    Column("role", Text, nullable=False),
    Column("content", Text, nullable=False),
    Column("model", Text),
    Column("input_tokens", Integer),
    Column("output_tokens", Integer),
    Column("tools", Text),
    Column("created_at", DateTime(timezone=True), nullable=False),
    CheckConstraint("role IN ('user', 'assistant')", name="ck_coach_message_role"),
    Index("ix_coach_message_conversation", "conversation_id", "id"),
    Index("ix_coach_message_user_created", "user_id", "created_at"),
)

# writer 모델 등록부(S15P31A207-197). 켜진 모델은 하나 이하(부분 유일 인덱스가 DB에서 지킨다).
# 모델 파일 자체는 writer 컨테이너가 들고 있고 여기에는 '어느 주소를 부를지'만 둔다
llm_model = Table(
    "llm_model",
    metadata,
    Column("model_id", Text, primary_key=True),
    Column("base_url", Text, nullable=False),
    Column("note", Text),
    Column("registered_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("activated_at", DateTime(timezone=True)),  # 한 번도 켠 적이 없으면 NULL
    Column("active", Boolean, nullable=False, server_default=text("false")),
    CheckConstraint("(active = false) OR (activated_at IS NOT NULL)", name="ck_llm_model_active_has_time"),
    Index("ux_llm_model_active", "active", unique=True, postgresql_where=text("active")),
)
