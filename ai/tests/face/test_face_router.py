"""FaceRouter: octet-stream 본문, 413·415, reason별 실패(14장 단위(ai 서버), D-59)."""

from __future__ import annotations

import base64
from collections.abc import AsyncIterator

import httpx
import pytest

from runkk_ai.face import FacePart, FaceTransformError
from tests.conftest import JPEG_HEAD, PNG_1X1, FakeFaceTransformer

OCTET = {"Content-Type": "application/octet-stream"}


async def test_jpeg_returns_all_parts_as_base64_png(client: httpx.AsyncClient, fake_face: FakeFaceTransformer) -> None:
    response = await client.post("/faces", content=JPEG_HEAD, headers=OCTET)

    assert response.status_code == 200
    parts = response.json()["parts"]
    assert set(parts) == {p.value for p in FacePart} == {"EYES", "NOSE", "MOUTH", "EARS", "HAIR"}
    assert all(base64.b64decode(v) == PNG_1X1 for v in parts.values())
    assert fake_face.received == [len(JPEG_HEAD)]


async def test_png_is_accepted(client: httpx.AsyncClient) -> None:
    response = await client.post("/faces", content=PNG_1X1, headers=OCTET)

    assert response.status_code == 200


async def test_declared_length_over_limit_is_413_without_converting(
    client: httpx.AsyncClient, fake_face: FakeFaceTransformer
) -> None:
    body = JPEG_HEAD + b"\x00" * (1024 * 1024)

    response = await client.post("/faces", content=body, headers=OCTET)

    assert response.status_code == 413
    assert response.json() == {"reason": "TOO_LARGE"}
    assert fake_face.received == []


async def test_exactly_limit_is_accepted(client: httpx.AsyncClient) -> None:
    body = JPEG_HEAD + b"\x00" * (1024 * 1024 - len(JPEG_HEAD))

    response = await client.post("/faces", content=body, headers=OCTET)

    assert response.status_code == 200


async def test_streamed_body_over_limit_is_413(client: httpx.AsyncClient, fake_face: FakeFaceTransformer) -> None:
    """Content-Length 없이(chunked) 보내도 상한에서 끊는다."""

    async def chunks() -> AsyncIterator[bytes]:
        yield JPEG_HEAD
        for _ in range(20):
            yield b"\x00" * 65536

    response = await client.post("/faces", content=chunks(), headers=OCTET)

    assert response.status_code == 413
    assert fake_face.received == []


@pytest.mark.parametrize(
    ("content_type", "body"),
    [
        ("multipart/form-data; boundary=x", JPEG_HEAD),
        ("image/jpeg", JPEG_HEAD),
        ("application/json", b"{}"),
        ("application/octet-stream", b"GIF89a" + b"\x00" * 10),
        ("application/octet-stream", b""),
        ("application/octet-stream", b"\xff\xd8"),
    ],
    ids=["multipart", "image-jpeg-header", "json", "gif", "empty", "truncated-jpeg"],
)
async def test_non_jpeg_png_is_415(
    client: httpx.AsyncClient, fake_face: FakeFaceTransformer, content_type: str, body: bytes
) -> None:
    response = await client.post("/faces", content=body, headers={"Content-Type": content_type})

    assert response.status_code == 415
    assert response.json() == {"reason": "UNSUPPORTED_IMAGE"}
    assert fake_face.received == []


async def test_missing_content_type_is_415(client: httpx.AsyncClient) -> None:
    response = await client.post("/faces", content=JPEG_HEAD)  # 원시 bytes는 Content-Type을 붙이지 않는다

    assert response.status_code == 415


@pytest.mark.parametrize(
    ("reason", "status"),
    [
        ("NO_FACE", 422),
        ("MULTIPLE_FACES", 422),
        ("UNSUPPORTED_IMAGE", 422),
        ("TOO_LARGE", 422),
        ("UPSTREAM_FAILED", 503),
        ("TIMEOUT", 504),
    ],
)
async def test_transformer_reason_maps_to_status(
    client: httpx.AsyncClient, fake_face: FakeFaceTransformer, reason: str, status: int
) -> None:
    fake_face.error = FaceTransformError(reason)  # type: ignore[arg-type]

    response = await client.post("/faces", content=JPEG_HEAD, headers=OCTET)

    assert response.status_code == status
    assert response.json() == {"reason": reason}


async def test_unexpected_error_is_upstream_failed(client: httpx.AsyncClient, fake_face: FakeFaceTransformer) -> None:
    fake_face.error = RuntimeError("model crashed")

    response = await client.post("/faces", content=JPEG_HEAD, headers=OCTET)

    assert response.status_code == 503
    assert response.json() == {"reason": "UPSTREAM_FAILED"}


async def test_slow_transformer_is_timeout(client: httpx.AsyncClient, fake_face: FakeFaceTransformer) -> None:
    fake_face.delay_s = 5.0  # 픽스처의 face_service 제한은 1초

    response = await client.post("/faces", content=JPEG_HEAD, headers=OCTET)

    assert response.status_code == 504
    assert response.json() == {"reason": "TIMEOUT"}
