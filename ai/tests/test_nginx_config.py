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


def _body(path: str) -> str:
    # "/"는 홈(= /)과 api 넘김(location /) 두 개라 정확 일치 쪽을 고른다
    bodies = [body for m, p, body in _locations() if p == path and (path != "/" or m == "=")]
    assert len(bodies) == 1, path
    return bodies[0]


@pytest.mark.parametrize("path", ["/", "/legal/", "/events/", "/invite/battle/", "/invite/crew/"])
def test_static_documents_are_gzipped(path: str) -> None:
    # S15P31A207-329: 정적 html·css·js·svg가 압축 없이 나가던 것(개인정보 처리방침만 주 3.8MB)을 줄인다
    body = _body(path)
    assert re.search(r"^\s*gzip on;", body, flags=re.MULTILINE)
    assert "gzip_types text/css application/javascript image/svg+xml;" in body


def test_versioned_event_images_are_cached_for_a_day() -> None:
    # S15P31A207-329: 내용 해시를 이름에 넣은 이벤트 그림만 하루 캐시(이벤트 html은 짧게 둔다)
    body = _body("/events/")
    assert 'add_header Cache-Control "public, max-age=60";' in body
    nested = re.search(r'location ~ "(\^/events/[^"]+)" \{(.*?)\n    \}', body, flags=re.DOTALL)
    assert nested is not None
    assert re.fullmatch(nested.group(1), "/events/beta-1km/banner.59e500fa.png")
    assert not re.fullmatch(nested.group(1), "/events/beta-1km/banner.png")
    assert 'Cache-Control "public, max-age=86400, immutable"' in nested.group(2)


def test_invite_card_jpeg_is_served() -> None:
    # S15P31A207-329: 카카오 공유 카드 PNG 538KB → JPEG
    assert "try_files /invite/card.jpg =404;" in _body("/invite/card.jpg")
    static = APP_LOCATIONS.parents[1] / "static" / "invite" / "card.jpg"
    assert static.exists()
    assert static.stat().st_size <= 150 * 1024
