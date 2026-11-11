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


# 원본 그림의 뒤꿈치·발목 모양으로 원래 있던 옆 가장자리 계단(판정 round3 원본에서 잰 수). 나머지 신발은 계단이 없어야 한다
SHOE_EDGE_STEPS = {"shoes_high_top": 1, "shoes_lime_runner": 2}


def _edge_steps(array: np.ndarray) -> int:
    """두 발 칸 바깥 옆 가장자리(행마다 처음 만나는 불투명 픽셀)가 이웃 행과 4px 넘게 어긋나는 곳 수(y 640~759)."""
    from outfit_pipeline.feet import FEET

    solid = array[..., 3] >= 128
    count = 0
    for x0, _, x1, _ in FEET:
        for xs in (list(range(x0, x1)), list(range(x1 - 1, x0 - 1, -1))):
            previous = None
            for y in range(640, 760):
                edge = next((i for i, x in enumerate(xs) if solid[y, x]), None)
                if edge is not None and previous is not None and abs(edge - previous) >= 4:
                    count += 1
                previous = edge
    return count


@pytest.mark.parametrize("key", spec.LEGACY_KEYS["SHOES"])
def test_committed_shoes_have_no_side_tabs(key: str) -> None:
    """판정 round4: 회색 띠를 아래쪽만 지워 뒤꿈치 옆에 귀 모양 탭이 남았다(basic_runner). 옆 가장자리에 새 계단이 없다."""
    array = np.array(Image.open(OUTFITS / key / f"female_stand{spec.EXTENSION}").convert("RGBA"))
    assert _edge_steps(array) <= SHOE_EDGE_STEPS.get(key, 0)


def test_cover_feet_never_paints_outside_the_bare_foot() -> None:
    """맨발 덮기는 맨발 실루엣(알파 128 이상) 안만 칠한다: 신발 실루엣을 맨발보다 넓히지 않는다."""
    body, shoe = _foot_case()
    out = np.array(cover_feet(Image.fromarray(shoe, "RGBA"), Image.fromarray(body, "RGBA"))[0])
    added = (out[..., 3] > 0) & (shoe[..., 3] == 0)
    assert added.any() and (body[added][:, 3] >= 128).all()


@pytest.mark.parametrize("key", spec.LEGACY_KEYS["SHOES"])
def test_committed_shoes_need_no_cover_outside_the_bare_foot(key: str) -> None:
    body = np.array(Image.open(OUTFITS / spec.BODY_FOLDER / f"female_stand{spec.EXTENSION}").convert("RGBA"))
    layer = Image.open(OUTFITS / key / f"female_stand{spec.EXTENSION}")
    region = exposed_feet(layer, body)
    assert (body[region][:, 3] >= 128).all()


def test_close_side_skin_covers_bare_strips_beside_and_below_a_sleeveless_top() -> None:
    from outfit_pipeline.stand_fix import close_side_skin

    body = np.zeros((768, 512, 4), np.uint8)
    body[150:330, 110:402] = (240, 200, 180, 255)  # 맨몸 몸통
    body[150:330, 110:114] = (60, 30, 16, 255)  # 왼쪽 몸통 옆 외곽선
    top = np.zeros((768, 512, 4), np.uint8)
    top[150:291, 117:395] = (120, 200, 170, 255)  # 맨몸보다 좁은 민소매, 밑단 y 290
    out, count = close_side_skin(Image.fromarray(top, "RGBA"), Image.fromarray(body, "RGBA"))
    array = np.array(out)
    assert count > 0
    assert array[250, 115, 3] == 255  # 옆 맨살 띠는 천으로
    assert array[250, 112, 3] == 0  # 몸통 옆 외곽선은 맨몸 그대로(상의 옆 외곽선)
    assert array[293, 150, 3] == 255 and array[293, 150, :3].tolist() == [120, 200, 170]  # 밑단 아래 하의 허리 위 맨살
    assert array[300, 150, 3] == 0  # 하의 허리(y 296) 아래는 그대로


def test_drop_side_fragments_removes_detached_pieces_below_the_hem_beside_the_hips() -> None:
    from outfit_pipeline.stand_fix import drop_side_fragments

    layer = np.zeros((768, 512, 4), np.uint8)
    layer[100:296, 150:362] = (250, 250, 250, 255)  # 상의 본체
    layer[304:320, 112:120] = (40, 30, 25, 255)  # 밑단 아래 옆 조각(기본 반바지 옆 외곽선)
    layer[300:320, 200:260] = (250, 250, 250, 255)  # 밑단 아래 가운데(본체와 떨어졌지만 옆 칸 밖)
    out, count = drop_side_fragments(Image.fromarray(layer, "RGBA"))
    array = np.array(out)
    assert count == 16 * 8
    assert array[310, 115, 3] == 0 and array[310, 230, 3] == 255 and array[200, 200, 3] == 255


def test_close_hem_slit_joins_the_hanging_flap_to_the_hem() -> None:
    from outfit_pipeline.stand_fix import close_hem_slit

    layer = np.zeros((768, 512, 4), np.uint8)
    layer[300:404, 116:300] = (50, 50, 55, 255)  # 반바지
    layer[392:404, 100:110] = (50, 50, 55, 255)  # 처진 앞판 끝
    layer[392:404, 110:116] = 0  # 그 사이 트임
    out, count = close_hem_slit(Image.fromarray(layer, "RGBA"))
    array = np.array(out)
    assert count > 0 and (array[396, 110:116, 3] == 255).all()


def test_solid_hem_corner_paints_light_specks_inside_the_hem_outline() -> None:
    from outfit_pipeline.stand_fix import solid_hem_corner

    layer = np.zeros((768, 512, 4), np.uint8)
    layer[200:296, 150:400] = (250, 250, 250, 255)
    layer[288:296, 380:392] = (50, 40, 35, 255)  # 밑단 모서리 외곽선 띠
    layer[292, 385] = (250, 250, 250, 255)  # 띠 안 흰 점
    out, count = solid_hem_corner(Image.fromarray(layer, "RGBA"))
    array = np.array(out)
    assert count == 1 and array[292, 385, :3].mean() < 100
    assert array[250, 300, :3].tolist() == [250, 250, 250]
