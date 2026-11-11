"""달리기 엉덩이 잇기 메시 시험(판정 round1 C)."""

from __future__ import annotations

import re
import struct

import numpy as np
import pytest
from PIL import Image

from outfit_pipeline import spec
from outfit_pipeline.cli import OUTFITS, RAW
from outfit_pipeline.hip import SOURCE, TARGET_TOP, V_SHIFT, paint_hip, retarget, retarget_rml, vertex_v_offsets

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


def test_paint_hip_copies_the_patch_and_outlines_the_back() -> None:
    layer = np.zeros((768, 256, 4), np.uint8)
    x0, y0, x1, y1 = SOURCE
    layer[y0:y1, x0:x1] = (200, 180, 140, 255)
    out = np.array(paint_hip(Image.fromarray(layer, "RGBA")))
    mid = TARGET_TOP + (y1 - y0) // 2
    assert out[mid, (x0 + x1) // 2, :3].tolist() == [200, 180, 140]
    assert out[mid, x0 + 1, :3].mean() < 80  # 등 쪽(왼쪽) 외곽선
    assert out[mid, x1 - 2, :3].tolist() == [200, 180, 140]  # 앞쪽은 선이 없다


@pytest.mark.parametrize("key", [*spec.LEGACY_KEYS["BOTTOM"], spec.BODY_FOLDER])
def test_committed_run_legs_carry_the_hip_patch(key: str) -> None:
    layer = Image.open(OUTFITS / key / f"female_run_leg{spec.EXTENSION}")
    assert np.array_equal(np.array(paint_hip(layer)), np.array(layer.convert("RGBA")))
