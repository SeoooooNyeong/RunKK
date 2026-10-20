"""환경 설정(13.3, 6.1.16). 값은 환경 변수(접두어 RUNKK_)로만 받는다.

이 Settings는 runkk_ai.main과 verify_internal_token만 읽는다. comment·face는 Settings를 import하지 않고
main이 꺼내 준 값만 생성자로 받는다(D-60).
"""

from __future__ import annotations

from typing import Literal

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="RUNKK_", extra="ignore", frozen=True)

    # api ↔ ai 내부 토큰. compose 환경 변수 파일 한 곳(RUNKK_INTERNAL_TOKEN)에서 두 컨테이너가 함께 읽는다(5.4)
    internal_token: SecretStr = Field(min_length=16)

    # LLM 코멘트(D-58). 제공자는 C-006 선정 결과를 환경 설정으로 고른다
    llm_provider: Literal["claude", "openai"] = "claude"
    llm_model: str = "claude-haiku-5-5"
    llm_timeout_s: float = Field(default=2.5, gt=0, lt=2.7)
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

    # 운영에서는 /docs·/openapi.json을 열지 않는다
    expose_docs: bool = False
