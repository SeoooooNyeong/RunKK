"""nginx 설정 회귀(S15P31A207-300): api 주소를 가로채는 location이 앱 주소를 바꾸지 않는지 본다.

nginx는 이름이 "/"로 끝나는 접두 location 안에서 proxy_pass를 쓰면, "/"만 뺀 같은 주소 요청에 직접 301을 돌려준다.
v0.13.3에서 `location ^~ /api/v1/avatar/`가 `GET /api/v1/avatar`(내 아바타)를 `/api/v1/avatar/`로 보내 api가 404를 냈다.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest

APP_LOCATIONS = Path(__file__).resolve().parents[2] / "infra" / "nginx" / "common" / "app-locations.conf"

pytestmark = pytest.mark.skipif(not APP_LOCATIONS.exists(), reason="저장소 밖(ai 단독 체크아웃)에서는 infra가 없다")

_LOCATION = re.compile(r"^location\s+(=|\^~|~\*?)?\s*(\S+)\s*\{(.*?)^\}", flags=re.MULTILINE | re.DOTALL)


def _locations() -> list[tuple[str, str, str]]:
    text = APP_LOCATIONS.read_text(encoding="utf-8")
    return [(m.group(1) or "", m.group(2), m.group(3)) for m in _LOCATION.finditer(text)]


def test_locations_are_parsed() -> None:
    prefixes = {path for _, path, _ in _locations()}
    assert {"/ws", "/", "/api/v1/runs"} <= prefixes


def test_api_prefix_proxy_locations_do_not_redirect_slashless_path() -> None:
    exact = {path for modifier, path, _ in _locations() if modifier == "="}
    offending = [
        path
        for modifier, path, body in _locations()
        if modifier in ("", "^~")
        and path.startswith("/api/")
        and path.endswith("/")
        and "proxy_pass" in body
        and path.rstrip("/") not in exact
    ]
    assert offending == [], f"앱이 '/' 없이 부르는 주소가 301로 바뀐다: {offending}"


def test_avatar_location_keeps_large_buffers() -> None:
    avatar = [body for _, path, body in _locations() if path.rstrip("/") == "/api/v1/avatar"]
    assert len(avatar) == 1
    assert "proxy_buffers 64 128k" in avatar[0]
