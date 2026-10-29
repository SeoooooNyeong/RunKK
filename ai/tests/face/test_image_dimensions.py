"""셀카 머리 정보의 가로·세로와 픽셀 상한(코드 리뷰 2차 L28). 디코드하지 않고 머리만 읽는다."""

from __future__ import annotations

import httpx
import pytest

from runkk_ai.face import AvatarService
from runkk_ai.face.schemas import DEFAULT_MAX_IMAGE_PIXELS, JPEG_SIGNATURE, PNG_SIGNATURE, image_dimensions
from tests.conftest import JPEG_HEAD, PNG_1X1, FakeAvatarClient

OCTET = {"Content-Type": "application/octet-stream"}


def _png(width: int, height: int) -> bytes:
    """IHDR까지만 있는 PNG 머리(CRC는 보지 않는다)."""
    ihdr = width.to_bytes(4, "big") + height.to_bytes(4, "big") + b"\x08\x06\x00\x00\x00"
    return PNG_SIGNATURE + b"\x00\x00\x00\x0dIHDR" + ihdr + b"\x00\x00\x00\x00"


def _jpeg(width: int, height: int, *, sof: int = 0xC0, before: bytes = b"") -> bytes:
    frame = b"\x08" + height.to_bytes(2, "big") + width.to_bytes(2, "big") + b"\x01\x01\x11\x00"
    return b"\xff\xd8" + before + bytes([0xFF, sof]) + (len(frame) + 2).to_bytes(2, "big") + frame


def test_fixtures_are_1x1() -> None:
    assert image_dimensions(PNG_1X1) == (1, 1)
    assert image_dimensions(JPEG_HEAD) == (1, 1)


@pytest.mark.parametrize(
    ("image", "expected"),
    [
        (_png(640, 480), (640, 480)),
        (_jpeg(4032, 3024), (4032, 3024)),
        (_jpeg(800, 600, sof=0xC2), (800, 600)),  # 프로그레시브(SOF2)
        (_jpeg(800, 600, before=b"\xff\xdb\x00\x04\x00\x00"), (800, 600)),  # SOF 앞 DQT 구간을 건너뛴다
        (_jpeg(800, 600, before=b"\xff\xff"), (800, 600)),  # 채움 바이트
        (_jpeg(800, 600, before=b"\xff\xc4\x00\x04\x00\x00"), (800, 600)),  # DHT(C4)는 프레임이 아니다
    ],
    ids=["png", "jpeg-baseline", "jpeg-progressive", "jpeg-after-dqt", "jpeg-fill", "jpeg-after-dht"],
)
def test_reads_dimensions_from_header(image: bytes, expected: tuple[int, int]) -> None:
    assert image_dimensions(image) == expected


@pytest.mark.parametrize(
    "image",
    [
        JPEG_SIGNATURE + b"\xe0" + b"\x00" * 10,  # 머리 구간이 끝나기 전에 끊김
        b"\xff\xd8\xff\xda\x00\x02",  # 프레임 전에 SOS
        b"\xff\xd8\x00\x00\x00\x00",  # 표지 자리가 0xFF가 아님
        _jpeg(0, 600),
        PNG_SIGNATURE + b"\x00" * 8,  # IHDR 없음
        _png(0, 10),
        b"GIF89a" + b"\x00" * 20,
    ],
    ids=["jpeg-truncated", "jpeg-sos-first", "jpeg-garbage", "jpeg-zero-width", "png-no-ihdr", "png-zero", "gif"],
)
def test_broken_header_is_none(image: bytes) -> None:
    assert image_dimensions(image) is None


def test_default_limit_is_4096_squared() -> None:
    assert DEFAULT_MAX_IMAGE_PIXELS == 4096 * 4096
    service = AvatarService(client=FakeAvatarClient(), timeout_s=1.0, max_image_bytes=1024)
    assert service.max_image_pixels == DEFAULT_MAX_IMAGE_PIXELS


async def test_pixels_over_limit_are_422_too_large_without_converting(
    client: httpx.AsyncClient, fake_face: FakeAvatarClient
) -> None:
    """1MB 안이라도 가로 × 세로가 상한을 넘으면 아바타 서버로 넘기지 않는다."""
    response = await client.post("/users/42/faces/jobs", content=_png(4097, 4096), headers=OCTET)

    assert response.status_code == 422
    assert response.json() == {"reason": "TOO_LARGE"}
    assert fake_face.submitted == []


async def test_pixels_at_limit_are_converted(client: httpx.AsyncClient, fake_face: FakeAvatarClient) -> None:
    response = await client.post("/users/42/faces/jobs", content=_jpeg(4096, 4096), headers=OCTET)

    assert response.status_code == 202
    assert len(fake_face.submitted) == 1


async def test_broken_header_is_422_unsupported_image(client: httpx.AsyncClient, fake_face: FakeAvatarClient) -> None:
    response = await client.post("/users/42/faces/jobs", content=JPEG_SIGNATURE + b"\xe0" + b"\x00" * 10, headers=OCTET)

    assert response.status_code == 422
    assert response.json() == {"reason": "UNSUPPORTED_IMAGE"}
    assert fake_face.submitted == []
