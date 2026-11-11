"""옷 레이어 구멍 메우기 시험(검수 3차: 폴로 단추 자리·밑단 트임으로 맨몸·배경이 비침)."""

from __future__ import annotations

import numpy as np
import pytest
from PIL import Image, ImageDraw

from outfit_pipeline import spec
from outfit_pipeline.check import hole_area
from outfit_pipeline.cli import MASKS, OUTFITS
from outfit_pipeline.holes import fill_garment_holes, hole_mask

BLUE = (70, 110, 190, 255)
WHITE = (240, 240, 236, 255)
DARK = (50, 46, 52, 255)


def _canvas(size: tuple[int, int] = (120, 120)) -> Image.Image:
    return Image.new("RGBA", size, (0, 0, 0, 0))


def test_button_holes_on_a_light_placket_become_drawn_buttons() -> None:
    image = _canvas()
    draw = ImageDraw.Draw(image)
    draw.rectangle((10, 10, 110, 110), fill=BLUE)
    draw.rectangle((50, 20, 70, 100), fill=WHITE)  # 플라켓
    draw.ellipse((55, 40, 65, 50), fill=(0, 0, 0, 0))  # 투명한 단추 자리
    draw.ellipse((55, 70, 65, 80), fill=(230, 200, 180, 90))  # 반투명 단추 자리
    assert int(hole_mask(image).sum()) > 100
    out, filled = fill_garment_holes(image)
    array = np.array(out).astype(int)
    assert filled > 100 and int(hole_mask(out).sum()) == 0
    assert (array[38:53, 53:68, 3] == 255).all() and (array[68:83, 53:68, 3] == 255).all()
    center, rim = array[45, 60, :3].mean(), array[40, 60, :3].mean()
    assert rim < center < 240  # 흰 플라켓 위에 테두리가 짙은 단추 모양


def test_loop_interiors_take_the_fabric_colour_not_the_cord() -> None:
    image = _canvas()
    draw = ImageDraw.Draw(image)
    draw.rectangle((10, 10, 110, 110), fill=DARK)
    draw.ellipse((40, 40, 80, 70), fill=WHITE)  # 끈 고리
    draw.ellipse((44, 44, 76, 66), fill=(0, 0, 0, 0))  # 고리 안(구멍)
    out, _ = fill_garment_holes(image)
    pixel = np.array(out)[55, 60]
    assert pixel[3] == 255 and pixel[:3].mean() < 90


def test_narrow_slits_between_thick_cloth_close_but_cord_gaps_and_openings_stay() -> None:
    image = _canvas((140, 120))
    draw = ImageDraw.Draw(image)
    draw.rectangle((10, 10, 130, 80), fill=BLUE)  # 몸판
    draw.polygon([(30, 81), (33, 66), (36, 81)], fill=(0, 0, 0, 0))  # 밑단 옆 V 트임(아래로 열림)
    draw.rectangle((90, 81, 92, 100), fill=DARK)  # 밑단 아래로 늘어진 끈 둘
    draw.rectangle((97, 81, 99, 100), fill=DARK)
    draw.rectangle((55, 10, 75, 30), fill=(0, 0, 0, 0))  # 목 트임(위로 열린 넓은 곳)
    mask = hole_mask(image)
    assert mask[75, 33] and not mask[90, 94] and not mask[20, 65]
    out = np.array(fill_garment_holes(image)[0])
    assert out[75, 33, 3] == 255 and out[90, 94, 3] == 0 and out[20, 65, 3] == 0


def test_long_light_gaps_are_filled_plain_without_a_button() -> None:
    image = _canvas((160, 120))
    draw = ImageDraw.Draw(image)
    draw.rectangle((10, 10, 150, 110), fill=WHITE)  # 흰 티셔츠 몸판
    draw.ellipse((40, 80, 100, 92), fill=(0, 0, 0, 0))  # 밑단 위 길쭉한 구멍(단추 자리가 아니다)
    out, filled = fill_garment_holes(image)
    array = np.array(out).astype(int)
    assert filled > 100 and (array[82:91, 45:96, 3] == 255).all()
    assert array[80:93, 40:101, :3].min() > 200  # 짙은 단추 테두리가 없다


def test_outline_antialiasing_is_not_a_hole() -> None:
    image = _canvas()
    draw = ImageDraw.Draw(image)
    draw.rectangle((6, 6, 114, 114), fill=(60, 50, 50, 150))  # 부드러운(150) 외곽선 그림자 띠
    draw.rectangle((10, 10, 110, 110), fill=(60, 50, 50, 230))  # 반투명(230) 외곽선 띠
    draw.rectangle((14, 14, 106, 106), fill=BLUE)
    draw.rectangle((40, 40, 60, 60), fill=(60, 50, 50, 220))  # 안쪽 반투명 무늬
    assert int(hole_mask(image).sum()) == 0
    out, filled = fill_garment_holes(image)
    assert filled == 0 and np.array_equal(np.array(out), np.array(image))


@pytest.mark.parametrize("key", [k for part in spec.PARTS for k in spec.LEGACY_KEYS[part]])
def test_committed_garments_have_no_holes(key: str) -> None:
    part = spec.part_of_key(key)
    surface = spec.default_male_top_surface(key)
    for path in sorted((OUTFITS / key).glob("*.webp")):
        if path.name != spec.SHOP_FILE:
            area = np.array(hole_area(MASKS, path.name, part, surface)) > 0
            assert int((hole_mask(Image.open(path)) & area).sum()) == 0, path.name


def test_cloth_cut_by_the_texture_edge_is_open_there() -> None:
    image = _canvas()
    draw = ImageDraw.Draw(image)
    draw.rectangle((20, 0, 100, 80), fill=WHITE)  # 텍스처 위 끝에서 잘린 양말 목
    draw.rectangle((40, 0, 44, 2), fill=(240, 240, 236, 60))  # 위 끝 반투명 가장자리
    draw.rectangle((60, 0, 62, 1), fill=(0, 0, 0, 0))
    assert int(hole_mask(image).sum()) == 0


def test_soft_rims_around_a_real_hole_are_filled_with_it() -> None:
    image = _canvas()
    draw = ImageDraw.Draw(image)
    draw.rectangle((10, 10, 110, 110), fill=BLUE)
    draw.ellipse((48, 48, 72, 72), fill=(70, 110, 190, 160))  # 구멍 둘레 2px 반투명(160)
    draw.ellipse((50, 50, 70, 70), fill=(0, 0, 0, 0))  # 구멍
    out = np.array(fill_garment_holes(image)[0])
    assert (out[45:76, 45:76, 3] == 255).all()
