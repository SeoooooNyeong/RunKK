"""환경 설정(13.3, 6.1.16). 값은 환경 변수(접두어 RUNKK_)로만 받는다.

이 Settings는 runkk_ai.main만 읽는다. comment·face는 Settings를 import하지 않고
main이 꺼내 준 값만 생성자로 받는다(D-60).
"""

from __future__ import annotations

from typing import Literal, Self

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# .env.example 자리표시 값에 들어 있는 표지. 이 값을 그대로 배포하면 기동을 멈춘다
_PLACEHOLDER_MARK = "change-me"
_CLAUDE_MODEL_PREFIX = "claude-"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="RUNKK_",
        extra="ignore",
        frozen=True,
        # compose environment 목록은 정하지 않은 변수를 ""로 넘긴다. 빈 값은 기본값으로 본다
        env_ignore_empty=True,
        # 설정 오류 문구는 컨테이너 로그로 나간다. 입력값(토큰·키)을 싣지 않는다
        hide_input_in_errors=True,
    )

    # api ↔ ai 내부 토큰. compose가 같은 RUNKK_INTERNAL_TOKEN을 두 컨테이너에 넘긴다(5.4)
    internal_token: SecretStr = Field(min_length=16)

    # LLM 코멘트(D-58). 제공자는 C-006 선정 결과를 환경 설정으로 고른다.
    # llm_timeout_s 상한(라우터 전체 제한보다 짧게)은 두 값을 모두 아는 main.create_app이 확인한다(H-22)
    llm_provider: Literal["claude", "openai"] = "claude"
    llm_model: str = "claude-haiku-5-5"
    llm_timeout_s: float = Field(default=2.5, gt=0)
    llm_max_tokens: int = Field(default=200, gt=0)
    llm_daily_hard_cap: int = Field(default=240, gt=0)  # 운영 한도에서 파생하지 않는 고정 상한(D-62)
    anthropic_api_key: SecretStr | None = None
    openai_api_key: SecretStr | None = None

    # 얼굴 변환(D-59). 구현은 C-011 선정 결과로 고른다
    face_impl: Literal["external", "local"] = "external"
    face_timeout_s: float = Field(default=12.0, gt=0, lt=15.0)  # api AiServerClient 읽기 제한 15초 안
    face_api_url: str | None = None
    face_api_key: SecretStr | None = None
    max_image_bytes: int = Field(default=1024 * 1024, gt=0)
    # 셀카 가로 × 세로 상한(머리 정보로 확인, L28). 기본 4096 × 4096
    max_image_pixels: int = Field(default=4096 * 4096, gt=0)

    # 운영에서는 /docs·/openapi.json을 열지 않는다
    expose_docs: bool = False

    @field_validator("internal_token")
    @classmethod
    def _reject_placeholder(cls, value: SecretStr) -> SecretStr:
        if _PLACEHOLDER_MARK in value.get_secret_value().lower():
            raise ValueError("RUNKK_INTERNAL_TOKEN이 .env.example 자리표시 값이다. 무작위 값으로 바꾼다")
        return value

    @model_validator(mode="after")
    def _model_matches_provider(self) -> Self:
        """제공자만 바꾸고 모델을 그대로 두면 모든 호출이 조용히 503이 된다. 기동 때 막는다."""
        is_claude_model = self.llm_model.startswith(_CLAUDE_MODEL_PREFIX)
        if is_claude_model != (self.llm_provider == "claude"):
            raise ValueError(f"llm_model이 llm_provider={self.llm_provider}의 모델이 아니다")
        return self
