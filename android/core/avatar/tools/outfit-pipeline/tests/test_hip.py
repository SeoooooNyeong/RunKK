"""달리기 엉덩이 잇기 메시 시험(판정 round1 C)."""

from __future__ import annotations

import itertools
import re
import struct

import numpy as np
import pytest
from PIL import Image

from outfit_pipeline import spec
from outfit_pipeline.cli import OUTFITS, RAW
from outfit_pipeline.hip import (
    CORNER_ROWS,
    SOURCE,
    TARGET_TOP,
    V_SHIFT,
    carve_hip,
    finish_run_leg,
    outline_leg_edge,
    paint_hip,
    retarget,
    retarget_rml,
    round_hip_corner,
    vertex_v_offsets,
)

KOTLIN = OUTFITS.parents[1] / "java/com/a207/runkk/app/core/avatar/RivHipConnection.kt"


@pytest.mark.parametrize(
    ("riv", "table"), [("avatar_run_approved.riv", "FEMALE_OFFSETS"), ("avatar_male_run.riv", "MALE_OFFSETS")]
)
def test_app_table_matches_the_riv(riv: str, table: str) -> None:
    offsets = vertex_v_offsets((RAW / riv).read_bytes())
    assert len(offsets) == 17 and all(0.2 <= v <= 0.28 for _, v in offsets)
    source = KOTLIN.read_text(encoding="utf-8")
    body = re.search(r"(?<![A-Z_])" + table + r" = intArrayOf\(([^)]*)\)", source)
    assert body is not None
    assert [int(x) for x in re.findall(r"\d+", body.group(1))] == [o for o, _ in offsets]
    assert f"V_SHIFT = {V_SHIFT!r}f" in source


def test_retarget_moves_only_the_hip_vertex_v() -> None:
    data = (RAW / "avatar_run_approved.riv").read_bytes()
    out = retarget(data)
    offsets = vertex_v_offsets(data)
    changed = {i for i in range(len(data)) if data[i] != out[i]}
    assert changed <= {o + k for o, _ in offsets for k in range(4)}
    for offset, value in offsets:
        assert struct.unpack_from("<f", out, offset)[0] == pytest.approx(value - V_SHIFT, abs=1e-6)


def test_retarget_rml_shifts_only_the_hip_mesh() -> None:
    text = (
        '<Image name="Other"><Mesh><ContourMeshVertex v="0.5" /></Mesh></Image>'
        '<Image name="Cloth textured hip connection"><Mesh><ContourMeshVertex u="0.5" v="0.24" /></Mesh></Image>'
    )
    out = retarget_rml(text)
    assert 'v="0.5"' in out and f'v="{0.24 - V_SHIFT!r}"' in out


def test_paint_hip_copies_the_patch_without_an_inner_arc() -> None:
    layer = np.zeros((768, 256, 4), np.uint8)
    x0, y0, x1, y1 = SOURCE
    layer[y0:y1, x0:x1] = (200, 180, 140, 255)
    out = np.array(paint_hip(Image.fromarray(layer, "RGBA")))
    assert (out[TARGET_TOP : TARGET_TOP + (y1 - y0), x0:x1] == layer[y0:y1, x0:x1]).all()


def test_round_hip_corner_rounds_the_back_and_outlines_the_new_edge() -> None:
    """엉덩이 뒷면이 곧게 내려와 허벅지에서 10px 꺾이던 모서리(판정 round3 C)를 둥근 곡선으로 깎고 그 가장자리에 외곽선을 그린다."""
    layer = np.zeros((768, 256, 4), np.uint8)
    layer[96:264, 2:200] = (240, 200, 185, 255)  # 곧은 뒷면
    layer[264:400, 11:200] = (240, 200, 185, 255)  # 허벅지(10px 안쪽)
    out = np.array(round_hip_corner(Image.fromarray(layer, "RGBA")))
    top, bottom = CORNER_ROWS
    edges = [int(np.nonzero(out[y, :, 3] >= 128)[0].min()) for y in range(top, bottom)]
    assert all(b - a <= 3 for a, b in itertools.pairwise(edges))  # 계단 없이 이어진다
    y = (top + bottom) // 2 + 20
    x = int(np.nonzero(out[y, :, 3] >= 128)[0].min())
    assert out[y, x + 1, :3].mean() < 80 and out[y, x + 20, :3].mean() > 150


@pytest.mark.parametrize("key", [*spec.LEGACY_KEYS["BOTTOM"], spec.BODY_FOLDER])
def test_committed_run_legs_are_finished(key: str) -> None:
    layer = Image.open(OUTFITS / key / f"female_run_leg{spec.EXTENSION}")
    if key == spec.BODY_FOLDER:
        again = round_hip_corner(carve_hip(paint_hip(layer)))
    else:
        again = finish_run_leg(layer, Image.open(OUTFITS / spec.BODY_FOLDER / f"female_run_leg{spec.EXTENSION}"))
    before, after = np.array(layer.convert("RGBA")), np.array(again)
    visible = (before[..., 3] > 0) | (after[..., 3] > 0)
    assert np.array_equal(before[..., 3], after[..., 3])
    assert np.array_equal(before[visible, :3], after[visible, :3])


def test_carve_hip_opens_the_back_bottom_corner() -> None:
    layer = np.zeros((768, 256, 4), np.uint8)
    x0, y0, x1, y1 = SOURCE
    layer[TARGET_TOP : TARGET_TOP + (y1 - y0), x0:x1] = (200, 180, 140, 255)
    out = np.array(carve_hip(Image.fromarray(layer, "RGBA")))
    assert out[TARGET_TOP + (y1 - y0) - 2, x0 + 2, 3] == 0  # 등 아래 모서리는 깎였다
    assert out[TARGET_TOP + 2, x1 - 3, 3] == 255  # 앞 위는 그대로


def test_outline_leg_edge_paints_the_body_outline_on_long_bottoms() -> None:
    body = np.zeros((768, 256, 4), np.uint8)
    body[100:700, 30:200] = (240, 200, 185, 255)
    body[100:700, 30:34] = (70, 45, 40, 255)
    garment = np.zeros_like(body)
    garment[100:690, 10:230] = (200, 180, 140, 255)
    out = np.array(outline_leg_edge(Image.fromarray(garment, "RGBA"), Image.fromarray(body, "RGBA")))
    assert out[300, 31, :3].tolist() == [70, 45, 40] and out[300, 100, :3].tolist() == [200, 180, 140]
