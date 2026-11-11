"""서기 마무리 시험(판정 round3): 신발이 맨발 덮기, 긴소매 칼라 안감, 상의 자르기 칸."""

from __future__ import annotations

import numpy as np
import pytest
from PIL import Image

from outfit_pipeline import spec
from outfit_pipeline.cli import OUTFITS, RAW
from outfit_pipeline.feet import cover_feet, exposed_feet
from outfit_pipeline.holes import line_collar
from outfit_pipeline.stand_fix import COLLAR_KEYS


def _foot_case() -> tuple[np.ndarray, np.ndarray]:
    """두 발 칸 왼쪽: 맨발(다리 x 140~200, 발 아래쪽이 x 120~220로 퍼지는 실루엣, 가장자리 2px 외곽선)과 그보다 좁은 신발(x 135~205)."""
    body = np.zeros((768, 512, 4), np.uint8)
    body[600:768, 140:200] = (240, 200, 180, 255)
    body[690:768, 120:220] = (240, 200, 180, 255)
    body[690:768, 120:122] = (60, 30, 16, 255)
    body[690:768, 218:220] = (60, 30, 16, 255)
    shoe = np.zeros((768, 512, 4), np.uint8)
    shoe[650:768, 135:205] = (220, 90, 70, 255)
    return body, shoe


def test_cover_feet_hides_bare_feet_beside_a_narrow_shoe() -> None:
    body, shoe = _foot_case()
    out, count = cover_feet(Image.fromarray(shoe, "RGBA"), Image.fromarray(body, "RGBA"))
    array = np.array(out)
    assert count > 0
    assert array[720, 125, 3] == 255 and array[720, 125, 0] > 150  # 신발 옆 맨발은 신발 천으로
    assert array[720, 120, :3].tolist() == [60, 30, 16]  # 바깥 외곽선은 맨발 그대로
    assert array[620, 145, 3] == 0  # 신발 위 다리(발목)는 덮지 않는다
    assert not exposed_feet(array, body).any()  # 다시 걸면 더 덮을 곳이 없다


@pytest.mark.parametrize("key", spec.LEGACY_KEYS["SHOES"])
def test_committed_shoes_cover_the_bare_feet(key: str) -> None:
    body = Image.open(OUTFITS / spec.BODY_FOLDER / f"female_stand{spec.EXTENSION}")
    layer = Image.open(OUTFITS / key / f"female_stand{spec.EXTENSION}")
    assert int(exposed_feet(layer, body).sum()) == 0


@pytest.mark.parametrize("key", COLLAR_KEYS)
def test_committed_collars_are_lined(key: str) -> None:
    body = Image.open(OUTFITS / spec.BODY_FOLDER / f"female_stand{spec.EXTENSION}")
    layer = Image.open(OUTFITS / key / f"female_stand{spec.EXTENSION}")
    assert line_collar(layer, body)[1] == 0  # 칼라·후드 안에 메울 빈 곳이 없다


def test_committed_stand_cut_area_matches_the_rule() -> None:
    from outfit_pipeline.build import cut_area

    committed = np.array(Image.open(OUTFITS / spec.BODY_FOLDER / f"female_stand_cut{spec.EXTENSION}").convert("RGBA"))
    assert np.array_equal(committed[..., 3], np.array(cut_area(RAW, "stand")))


def test_solid_side_outline_paints_light_specks_outside_the_side_outline() -> None:
    from outfit_pipeline.stand_fix import solid_side_outline

    layer = np.zeros((768, 512, 4), np.uint8)
    layer[185:300, 150:362] = (250, 250, 250, 255)  # 흰 몸판
    layer[185:300, 152:154] = (60, 40, 30, 255)  # 몸통 옆 외곽선(가장자리에서 2px 안쪽)
    layer[200, 150:152] = (250, 250, 250, 255)  # 외곽선 밖 흰 점(가장자리)
    out, count = solid_side_outline(Image.fromarray(layer, "RGBA"))
    array = np.array(out)
    assert count > 0
    assert array[200, 150, :3].tolist() == [60, 40, 30]  # 외곽선 밖 흰 점은 외곽선 색
    assert array[200, 160, :3].tolist() == [250, 250, 250]  # 외곽선 안 천은 그대로
    assert solid_side_outline(out)[1] == 0


@pytest.mark.parametrize("key", [k for k in spec.LEGACY_KEYS["TOP"] if spec.default_male_top_surface(k) == 1])
def test_committed_short_tops_have_solid_side_outlines(key: str) -> None:
    from outfit_pipeline.stand_fix import solid_side_outline

    assert solid_side_outline(Image.open(OUTFITS / key / f"female_stand{spec.EXTENSION}"))[1] == 0


def test_drop_halo_removes_the_grey_band_outside_the_shoe_outline() -> None:
    from outfit_pipeline.feet import drop_halo

    shoe = np.zeros((768, 512, 4), np.uint8)
    shoe[650:768, 120:200] = (230, 225, 215, 255)  # 흰 신발
    shoe[650:768, 120:123] = (60, 50, 45, 255)  # 바깥 외곽선
    shoe[650:768, 116:120] = (150, 150, 150, 120)  # 외곽선 밖 회색 띠(생성 그림자)
    out, count = drop_halo(Image.fromarray(shoe, "RGBA"))
    array = np.array(out)
    assert count > 0
    assert (array[700, 116:120, 3] == 0).all()
    assert array[700, 120, :3].tolist() == [60, 50, 45] and array[700, 150, 3] == 255
    assert drop_halo(out)[1] == 0


def test_stand_fix_is_stable_on_the_committed_assets(tmp_path) -> None:
    """이미 만든 묶음에 `stand-fix`를 다시 걸어도 바뀌는 파일이 없다(회색 띠를 지운 자리가 검사 16번 메우기로 되돌아오는 신발 가장자리
    몇 픽셀도 그대로 둔다)."""
    import shutil

    from outfit_pipeline.cli import MASKS
    from outfit_pipeline.stand_fix import fix_stand

    shutil.copytree(OUTFITS, tmp_path / "outfits")
    assert fix_stand(tmp_path / "outfits", RAW, MASKS) == {}
