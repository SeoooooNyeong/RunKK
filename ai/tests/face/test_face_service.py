"""FaceTransformService: 시간 제한, 결과 검증, 셀카를 로그·디스크에 남기지 않음(FR-156, D-59, T-17)."""

from __future__ import annotations

import base64
import builtins
import logging
import tempfile
from typing import Any

import httpx
import pytest

from runkk_ai.face import FacePart, FaceTransformError, FaceTransformService
from tests.conftest import JPEG_HEAD, PNG_1X1, FakeFaceTransformer, all_parts

SELFIE = JPEG_HEAD + b"SELFIE-SECRET-PIXELS" * 10


async def test_success_returns_images() -> None:
    service = FaceTransformService(transformer=FakeFaceTransformer(), timeout_s=1.0, max_image_bytes=1024)

    result = await service.transform(SELFIE)

    assert result.reason is None
    assert result.images is not None
    assert set(result.images.images) == set(FacePart)


def test_exposes_max_image_bytes() -> None:
    service = FaceTransformService(transformer=FakeFaceTransformer(), timeout_s=1.0, max_image_bytes=4321)

    assert service.max_image_bytes == 4321


async def test_timeout_is_timeout_reason() -> None:
    service = FaceTransformService(transformer=FakeFaceTransformer(delay_s=1.0), timeout_s=0.05, max_image_bytes=1024)

    result = await service.transform(SELFIE)

    assert (result.images, result.reason) == (None, "TIMEOUT")


async def test_transformer_timeout_error_is_timeout_reason() -> None:
    service = FaceTransformService(
        transformer=FakeFaceTransformer(error=TimeoutError()), timeout_s=1.0, max_image_bytes=1024
    )

    assert (await service.transform(SELFIE)).reason == "TIMEOUT"


async def test_missing_part_is_upstream_failed() -> None:
    parts = all_parts()
    del parts[FacePart.HAIR]
    service = FaceTransformService(transformer=FakeFaceTransformer(parts=parts), timeout_s=1.0, max_image_bytes=1024)

    assert (await service.transform(SELFIE)).reason == "UPSTREAM_FAILED"


async def test_non_png_part_is_upstream_failed() -> None:
    parts = all_parts()
    parts[FacePart.EYES] = b"\xff\xd8\xff not png"
    service = FaceTransformService(transformer=FakeFaceTransformer(parts=parts), timeout_s=1.0, max_image_bytes=1024)

    assert (await service.transform(SELFIE)).reason == "UPSTREAM_FAILED"


async def test_known_reason_is_passed_through() -> None:
    service = FaceTransformService(
        transformer=FakeFaceTransformer(error=FaceTransformError("MULTIPLE_FACES")), timeout_s=1.0, max_image_bytes=1024
    )

    assert (await service.transform(SELFIE)).reason == "MULTIPLE_FACES"


def test_unknown_reason_is_rejected() -> None:
    with pytest.raises(ValueError, match="reason"):
        FaceTransformError("SOMETHING_ELSE")  # type: ignore[arg-type]


async def test_log_has_result_but_no_image(caplog: pytest.LogCaptureFixture) -> None:
    caplog.set_level(logging.DEBUG)
    service = FaceTransformService(transformer=FakeFaceTransformer(), timeout_s=1.0, max_image_bytes=1024)

    await service.transform(SELFIE)

    assert "result=ok" in caplog.text
    assert "SELFIE-SECRET" not in caplog.text
    assert base64.b64encode(SELFIE).decode()[:16] not in caplog.text
    assert base64.b64encode(PNG_1X1).decode()[:16] not in caplog.text


async def test_failure_log_names_only_exception_type(caplog: pytest.LogCaptureFixture) -> None:
    caplog.set_level(logging.DEBUG)
    service = FaceTransformService(
        transformer=FakeFaceTransformer(error=RuntimeError("SELFIE-SECRET in message")),
        timeout_s=1.0,
        max_image_bytes=1024,
    )

    await service.transform(SELFIE)

    assert "result=UPSTREAM_FAILED" in caplog.text
    assert "RuntimeError" in caplog.text
    assert "SELFIE-SECRET" not in caplog.text


async def test_selfie_never_touches_disk(
    client: httpx.AsyncClient, monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    """T-17의 ai 쪽 단위 시험: 요청 처리 중 파일을 쓰거나 임시 파일을 만들지 않는다."""
    caplog.set_level(logging.DEBUG)
    writes: list[Any] = []
    real_open = builtins.open

    def guarded_open(file: Any, mode: str = "r", *args: Any, **kwargs: Any) -> Any:
        if any(flag in mode for flag in "wax+"):
            writes.append(file)
        return real_open(file, mode, *args, **kwargs)

    def forbidden(*args: Any, **kwargs: Any) -> Any:
        writes.append("tempfile")
        raise AssertionError("임시 파일을 만들면 안 된다")

    monkeypatch.setattr(builtins, "open", guarded_open)
    for name in ("TemporaryFile", "NamedTemporaryFile", "SpooledTemporaryFile", "mkstemp"):
        monkeypatch.setattr(tempfile, name, forbidden)

    response = await client.post("/faces", content=SELFIE, headers={"Content-Type": "application/octet-stream"})

    assert response.status_code == 200
    assert writes == []
    assert "SELFIE-SECRET" not in caplog.text
