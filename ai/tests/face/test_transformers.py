"""ExternalFaceApi(httpx MockTransport)·LocalSdModel(스레드 + Semaphore(1)). 실제 외부 API는 부르지 않는다."""

from __future__ import annotations

import asyncio
import base64
import threading
import time
from collections.abc import Mapping

import httpx
import pytest

from runkk_ai.face import ExternalFaceApi, FacePart, FaceTransformer, FaceTransformError, LocalSdModel
from tests.conftest import JPEG_HEAD, PNG_1X1


def _parts_json() -> dict[str, dict[str, str]]:
    return {"parts": {p.value: base64.b64encode(PNG_1X1).decode() for p in FacePart}}


def _api(handler: httpx.MockTransport | None, url: str | None = "https://face.example/convert") -> ExternalFaceApi:
    return ExternalFaceApi(base_url=url, api_key="face-key", timeout_s=1.0, transport=handler)


def test_transformers_implement_one_abc() -> None:
    assert ExternalFaceApi.__bases__ == (FaceTransformer,)
    assert LocalSdModel.__bases__ == (FaceTransformer,)


async def test_external_api_success_sends_octet_stream_and_decodes_parts() -> None:
    seen: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return httpx.Response(200, json=_parts_json())

    api = _api(httpx.MockTransport(handler))

    images = await api.convert(JPEG_HEAD)

    assert set(images.images) == set(FacePart)
    assert all(v == PNG_1X1 for v in images.images.values())
    assert seen[0].headers["Content-Type"] == "application/octet-stream"
    assert seen[0].headers["Authorization"] == "Bearer face-key"
    assert seen[0].content == JPEG_HEAD
    await api.aclose()


@pytest.mark.parametrize("reason", ["NO_FACE", "MULTIPLE_FACES", "UNSUPPORTED_IMAGE", "TOO_LARGE"])
async def test_external_api_client_error_reason_is_kept(reason: str) -> None:
    api = _api(httpx.MockTransport(lambda r: httpx.Response(422, json={"reason": reason})))

    with pytest.raises(FaceTransformError) as caught:
        await api.convert(JPEG_HEAD)

    assert caught.value.reason == reason


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(500, json={"reason": "NO_FACE"}),
        httpx.Response(422, json={"reason": "TIMEOUT"}),
        httpx.Response(400, text="bad"),
        httpx.Response(200, text="not json"),
        httpx.Response(200, json={"parts": {"EYES": "@@not-base64@@"}}),
        httpx.Response(200, json={"unexpected": True}),
    ],
    ids=["5xx", "reason-not-allowed", "non-json-error", "non-json-ok", "bad-base64", "no-parts"],
)
async def test_external_api_bad_answers_are_upstream_failed(response: httpx.Response) -> None:
    api = _api(httpx.MockTransport(lambda r: response))

    with pytest.raises(FaceTransformError) as caught:
        await api.convert(JPEG_HEAD)

    assert caught.value.reason == "UPSTREAM_FAILED"


async def test_external_api_timeout_becomes_timeout_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("slow", request=request)

    api = _api(httpx.MockTransport(handler))

    with pytest.raises(TimeoutError):
        await api.convert(JPEG_HEAD)


async def test_external_api_connection_error_is_upstream_failed() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("down", request=request)

    api = _api(httpx.MockTransport(handler))

    with pytest.raises(FaceTransformError) as caught:
        await api.convert(JPEG_HEAD)

    assert caught.value.reason == "UPSTREAM_FAILED"


async def test_external_api_without_url_is_upstream_failed() -> None:
    api = _api(None, url=None)

    with pytest.raises(FaceTransformError) as caught:
        await api.convert(JPEG_HEAD)

    assert caught.value.reason == "UPSTREAM_FAILED"
    await api.aclose()


async def test_local_model_runs_in_thread_one_at_a_time() -> None:
    lock = threading.Lock()
    state = {"active": 0, "max": 0, "threads": set()}

    def pipeline(image: bytes) -> Mapping[FacePart, bytes]:
        with lock:
            state["active"] += 1  # type: ignore[operator]
            state["max"] = max(state["max"], state["active"])  # type: ignore[call-overload]
            state["threads"].add(threading.get_ident())  # type: ignore[attr-defined]
        time.sleep(0.02)
        with lock:
            state["active"] -= 1  # type: ignore[operator]
        return dict.fromkeys(FacePart, PNG_1X1)

    model = LocalSdModel(pipeline=pipeline)

    results = await asyncio.gather(*(model.convert(JPEG_HEAD) for _ in range(4)))

    assert state["max"] == 1
    assert threading.get_ident() not in state["threads"]  # type: ignore[operator]
    assert all(set(r.images) == set(FacePart) for r in results)
    await model.aclose()


async def test_local_model_does_not_block_event_loop() -> None:
    def pipeline(image: bytes) -> Mapping[FacePart, bytes]:
        time.sleep(0.2)
        return dict.fromkeys(FacePart, PNG_1X1)

    model = LocalSdModel(pipeline=pipeline)
    ticks = 0

    async def ticker() -> None:
        nonlocal ticks
        for _ in range(10):
            await asyncio.sleep(0.01)
            ticks += 1

    await asyncio.gather(model.convert(JPEG_HEAD), ticker())

    assert ticks == 10
