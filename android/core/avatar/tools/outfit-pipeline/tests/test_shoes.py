"""신발 잔상 지우기 시험(판정 round1 D: high_top 검은 잔상, lime_runner 적갈색 테두리)."""

from __future__ import annotations

import numpy as np
import pytest
from PIL import Image, ImageDraw

from outfit_pipeline import spec
from outfit_pipeline.cli import OUTFITS
from outfit_pipeline.shoes import drop_ghost, drop_pieces, ghost_mask


def _shoe(ghost: bool, radius: int = 24) -> Image.Image:
    image = Image.new("RGBA", (200, 160), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if ghost:
        draw.rounded_rectangle((20, 20, 180, 150), 30, fill=(20, 20, 40, 252))  # 생성 그림자 잔상(부드러운 짙은 색)
    draw.rounded_rectangle((32, 30, 168, 140), radius, fill=(70, 50, 50, 255))  # 신발 외곽선
    draw.rounded_rectangle((36, 34, 164, 136), max(0, radius - 2), fill=(60, 80, 160, 255))  # 갑피
    return image


def test_ghost_band_outside_the_shoe_outline_is_dropped() -> None:
    out, removed = drop_ghost(_shoe(ghost=True))
    array = np.array(out)
    assert removed > 1000
    assert array[85, 22, 3] == 0 and array[85, 178, 3] == 0  # 옆 잔상
    assert array[85, 33, 3] == 255 and array[85, 100, 3] == 255  # 외곽선·갑피는 남는다


def test_shoe_whose_outline_is_the_silhouette_edge_keeps_everything() -> None:
    assert int(ghost_mask(_shoe(ghost=False, radius=4)).sum()) == 0


@pytest.mark.parametrize("key", spec.SHOE_GHOST_KEYS)
def test_ghost_keys_are_shoe_bundles(key: str) -> None:
    assert spec.part_of_key(key) == "SHOES" and (OUTFITS / key / f"female_stand{spec.EXTENSION}").is_file()


def test_small_pieces_apart_from_the_shoes_are_dropped() -> None:
    image = _shoe(ghost=False, radius=4)
    ImageDraw.Draw(image).line((100, 2, 106, 12), fill=(90, 60, 40, 255), width=2)  # 발목 위 짧은 획
    out, removed = drop_pieces(image)
    assert 0 < removed < 200 and np.array(out)[7, 103, 3] == 0 and np.array(out)[85, 100, 3] == 255


@pytest.mark.parametrize("key", [k for k in spec.LEGACY_KEYS["SHOES"]])
def test_committed_shoes_have_no_loose_pieces(key: str) -> None:
    for slot in ("female_stand", "female_run_shoe"):
        assert drop_pieces(Image.open(OUTFITS / key / f"{slot}{spec.EXTENSION}"))[1] == 0, slot
