"""달리기 옷 레이어 마무리 시험(판정 round3 달리기 묶음)."""

from __future__ import annotations

import numpy as np
import pytest
from PIL import Image

from outfit_pipeline import spec
from outfit_pipeline.cli import OUTFITS
from outfit_pipeline.run import clean_cord, drop_loose_pieces, finish_run_layer, outline_back


def test_loose_pieces_apart_from_the_garment_are_dropped() -> None:
    layer = np.zeros((300, 256, 4), np.uint8)
    layer[50:250, 40:220] = (120, 120, 120, 255)  # 본체
    layer[10:40, 5:8] = (60, 40, 35, 255)  # 떨어진 획
    out, removed = drop_loose_pieces(Image.fromarray(layer, "RGBA"))
    assert removed == 90 and np.array(out)[20, 6, 3] == 0 and np.array(out)[100, 100, 3] == 255


def test_hoodie_back_edge_gets_a_dark_outline_and_others_stay() -> None:
    layer = np.zeros((300, 256, 4), np.uint8)
    layer[50:250, 40:220] = (150, 145, 140, 255)
    image = Image.fromarray(layer, "RGBA")
    out = np.array(outline_back(image, "top_winter_hoodie")[0])
    assert out[100, 41, :3].mean() < 90 and out[100, 60, :3].mean() > 130 and out[100, 218, :3].mean() > 130
    assert np.array_equal(np.array(outline_back(image, "base_top")[0]), layer)


def test_jogger_cord_tip_takes_the_fabric_colour() -> None:
    layer = np.zeros((768, 256, 4), np.uint8)
    layer[0:700, 0:220] = (85, 85, 92, 255)  # 회색 천
    layer[110:140, 6:16] = (120, 80, 50, 255)  # 갈색 끈 끝
    out = np.array(clean_cord(Image.fromarray(layer, "RGBA"), "bottom_jogger_pants"))
    assert np.abs(out[125, 10, :3].astype(int) - [85, 85, 92]).max() <= 2


@pytest.mark.parametrize("key", spec.LEGACY_KEYS["TOP"])
def test_committed_run_tops_are_finished(key: str) -> None:
    for slot in ("run_body", "run_arm"):
        layer = Image.open(OUTFITS / key / f"female_{slot}{spec.EXTENSION}")
        again = np.array(finish_run_layer(layer, key, "TOP", slot))
        before = np.array(layer.convert("RGBA"))
        visible = before[..., 3] > 0
        changed = (before[..., 3] != again[..., 3]) | (
            visible & (np.abs(before[..., :3].astype(int) - again[..., :3]).max(axis=-1) > 30)
        )
        # 다시 돌려도 거의 그대로다(구멍 메우기 뒤 가장자리 몇 픽셀만 다를 수 있다)
        assert int(changed.sum()) <= visible.sum() // 200, slot


def test_fix_masks_are_applied_to_committed_layers() -> None:
    from outfit_pipeline.run import FIXES

    masks = sorted(FIXES.glob("*__*.png"))
    assert masks, "손 마스크 파일이 없다"
    for path in masks:
        key, stem = path.stem.split("__")
        mask = np.array(Image.open(path).convert("L")) > 0
        layer = np.array(Image.open(OUTFITS / key / f"{stem}{spec.EXTENSION}").convert("RGBA"))
        assert mask.shape == layer.shape[:2] and int(layer[mask, 3].max(initial=0)) == 0, path.name
