"""배치 설정 회귀(5.4, M1): compose가 컨테이너마다 필요한 환경 변수만 넘기는지 본다.

ai 컨테이너는 Settings가 읽는 RUNKK_*를 빠짐없이 받고, 그 밖에는 SDK가 직접 읽는 주소 변수(_SDK_ENV)만 받는다.
api 컨테이너는 ai 키를 받지 않는다.
YAML 파서 의존성을 늘리지 않으려고 서비스 블록을 들여쓰기로 잘라 읽는다.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest

from runkk_ai.core import Settings

COMPOSE = Path(__file__).resolve().parents[2] / "infra" / "docker-compose.yml"
# Settings를 거치지 않고 SDK가 환경 변수로 직접 읽는 값. 키·토큰은 넣지 않는다
_SDK_ENV = {"ANTHROPIC_BASE_URL"}

pytestmark = pytest.mark.skipif(not COMPOSE.exists(), reason="저장소 밖(ai 단독 체크아웃)에서는 infra가 없다")


def _service_block(name: str) -> str:
    text = COMPOSE.read_text(encoding="utf-8")
    match = re.search(rf"^  {name}:\n(.*?)(?=^  \S|^\S|\Z)", text, flags=re.MULTILINE | re.DOTALL)
    assert match is not None, name
    return match.group(1)


def _environment_names(name: str) -> set[str]:
    return set(re.findall(r"^      ([A-Z][A-Z0-9_]*):", _service_block(name), flags=re.MULTILINE))


@pytest.mark.parametrize("service", ["api", "ai"])
def test_service_does_not_take_whole_env_file(service: str) -> None:
    assert "env_file" not in _service_block(service)


def test_ai_gets_exactly_its_settings() -> None:
    expected = {f"RUNKK_{field.upper()}" for field in Settings.model_fields} | _SDK_ENV

    assert _environment_names("ai") == expected


def test_api_gets_internal_token_but_no_ai_keys() -> None:
    names = _environment_names("api")

    ai_settings = {f"RUNKK_{field.upper()}" for field in Settings.model_fields}

    assert {"AI_SERVER_URL", "RUNKK_INTERNAL_TOKEN", "DB_PASSWORD"} <= names
    # api 자기 설정(RUNKK_QUEUE·RUNKK_LIVE 등)은 받아도 되지만, ai 설정 중에서는 공유 토큰 하나만 받는다
    assert names & ai_settings == {"RUNKK_INTERNAL_TOKEN"}


@pytest.mark.parametrize("service", ["api", "ai"])
def test_tmp_is_tmpfs(service: str) -> None:
    """셀카가 디스크에 남을 길을 막는다(5.4, D-59)."""
    assert re.search(r"tmpfs:\n\s+- /tmp:", _service_block(service))


def test_ai_container_is_hardened() -> None:
    block = _service_block("ai")

    for line in ("read_only: true", "cap_drop: [ALL]", "no-new-privileges:true", "mem_limit:"):
        assert line in block
    assert "ports:" not in block  # api만 내부 망으로 부른다(D-23)
