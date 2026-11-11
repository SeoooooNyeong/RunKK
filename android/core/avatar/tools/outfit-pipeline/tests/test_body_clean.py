"""맨몸 바탕·옷 레이어 정리 시험([임시:D-성-184] 민소매 팔 선, [임시:D-성-185] 허리 띠·반바지 가장자리)."""

from __future__ import annotations

import itertools
import shutil
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from outfit_pipeline import spec
from outfit_pipeline.body import (
    bare_hips,
    close_seams,
    close_side_gaps,
    count_strays,
    drop_dust,
    drop_gap_fragments,
    drop_strays,
    dust_mask,
    elbow_tail_mask,
    extend_waist,
    fill_pinholes,
    outline_breaks,
    seam_mask,
    side_gap_mask,
    smooth_sleeve_line,
    trim_waist,
    underwear_mask,
    waist_gap,
    waist_tufts_mask,
)
from outfit_pipeline.build import cut_area
from outfit_pipeline.check import check_body, check_layer, template_for
from outfit_pipeline.cli import MASKS, OUTFITS, RAW

SKIN = (246, 205, 190, 255)
GREY = (200, 200, 206, 255)
DARK = (60, 56, 61, 255)


def _blob() -> np.ndarray:
    array = np.zeros((60, 60, 4), np.uint8)
    array[10:50, 10:50] = SKIN
    return array


def test_drop_strays_removes_isolated_dots_and_dust_but_keeps_attached_pixels() -> None:
    array = _blob()
    array[30, 50:58] = (70, 40, 30, 220)  # 몸에서 뻗은 1px 털(소매 모서리 외곽선 부스러기)
    array[5, 5] = (60, 30, 20, 90)  # 떨어진 점
    array[2, 30] = (60, 30, 20, 3)  # 거의 투명한 먼지
    array[9, 10:50] = (70, 40, 30, 100)  # 가장자리 안티에일리어싱 1px(남긴다)
    image = Image.fromarray(array, "RGBA")
    assert (
        count_strays(image) == 1 + 1
    )  # 떨어진 점과 먼지만. 몸에 붙은 털·안티에일리어싱은 남긴다(외곽선을 깎지 않는다)
    cleaned, removed = drop_strays(image)
    out = np.array(cleaned)
    assert removed == 2 and count_strays(cleaned) == 0
    assert out[30, 51:58, 3].min() == 220 and out[5, 5, 3] == 0 and out[2, 30, 3] == 0
    assert (out[10:50, 10:50] == array[10:50, 10:50]).all()
    assert (out[9, 10:50, 3] == 100).all()


def test_bare_hips_paints_underwear_as_skin_and_returns_the_waistband() -> None:
    array = np.zeros((spec.SLOTS["stand"].height, spec.SLOTS["stand"].width, 4), np.uint8)
    x0, y0, x1, y1 = spec.STAND_HIPS
    array[y0 - 40 : y1 + 40, x0 + 10 : x1 - 10] = SKIN  # 몸(위 몸통, 아래 다리는 피부)
    array[y0:y1, x0 + 10 : x1 - 10] = GREY  # 속바지
    _, wy0, _, wy1 = spec.STAND_WAIST
    array[wy0 + 6 : wy1, x0 + 10 : x1 - 10] = DARK  # 승인 텍스처의 짙은 허리 밴드
    array[y0:y1, x0 + 10] = (37, 25, 23, 255)  # 실루엣 외곽선(무채색)
    body, band = bare_hips(Image.fromarray(array, "RGBA"))
    out = np.array(body)
    assert underwear_mask(out).sum() == 0
    assert out[310, 256, 3] == 255 and out[310, 256, 0] > 200  # 허리는 피부
    assert out[380, 256, 0] > 200 and out[380, 256, 0] - out[380, 256, 2] > 30
    edge = out[350, x0 + 10]
    assert edge[3] == 255 and edge[0] < 80 and edge[0] - edge[2] >= 10  # 외곽선은 갈색 선으로 남는다
    b = np.array(band)
    assert tuple(b[310, 256]) == DARK  # 허리 밴드는 기본 하의 레이어로 옮긴다
    assert b[380, 256, 3] == 0  # 속바지(밴드 아래)는 옮기지 않는다
    assert b[:wy0, :, 3].max() == 0 and b[wy1:, :, 3].max() == 0


def test_smooth_sleeve_line_removes_the_hem_line_across_the_bare_arm() -> None:
    array = np.zeros((spec.SLOTS["stand"].height, spec.SLOTS["stand"].width, 4), np.uint8)
    array[120:260, 60:130] = (250, 216, 204, 255)  # 왼팔
    array[171, 64:126] = (209, 151, 131, 255)  # 소매 밑단 선
    array[172:184, 64:126] = (236, 171, 149, 255)  # 소매 그림자 띠
    legacy = np.zeros_like(array)
    legacy[100:171, 50:140] = (250, 250, 250, 255)  # 승인 흰 반팔(밑단 행 170)
    legacy[171:260, 50:140] = (250, 216, 204, 255)  # 그 아래 팔 피부
    out = np.array(smooth_sleeve_line(Image.fromarray(array, "RGBA"), Image.fromarray(legacy, "RGBA"))).astype(int)
    column = out[140:220, 95, :3].sum(axis=1)
    assert np.abs(np.diff(column)).max() <= 6
    assert (out[120:260, 60:130, 3] == 255).all()


def test_cut_area_cuts_rims_around_the_hips_but_not_the_waistband() -> None:
    area = cut_area(RAW, "stand")
    assert area.getpixel((110, 360)) == spec.CUT_HIP  # 엉덩이 옆(하의 밖 옛 반바지 테두리)
    assert area.getpixel((400, 360)) == spec.CUT_HIP
    assert area.getpixel((256, 380)) == 0  # 가랑이·허벅지는 자르지 않는다(점점이 비었다)
    assert area.getpixel((256, 310)) == 0  # 허리 밴드는 자르지 않는다(밑단과 허리 사이 구멍)
    assert area.getpixel((40, 350)) == 0  # 손 칸
    assert area.getpixel((256, 500)) == 0  # 다리


def test_check_reports_strays_and_underwear(tmp_path: Path) -> None:
    layer = np.array(Image.open(OUTFITS / "top_crew_tee/female_stand.webp").convert("RGBA"))
    ys, xs = np.nonzero(layer[..., 3] > 200)
    y, x = ys[len(ys) // 2], xs.min()
    layer[y - 40, x - 12] = (60, 40, 30, 255)  # 옷에서 떨어진 점
    path = tmp_path / "female_stand.webp"
    spec.save_webp(Image.fromarray(layer, "RGBA"), path)
    assert [p.rule for p in check_layer(path, "TOP", template_for(path.name, "TOP", 1), MASKS)] == [10]

    outfits = tmp_path / "outfits"
    folder = outfits / spec.BODY_FOLDER
    folder.mkdir(parents=True)
    for source in (OUTFITS / spec.BODY_FOLDER).glob("*.webp"):
        (folder / source.name).write_bytes(source.read_bytes())
    stand = np.array(Image.open(folder / "female_stand.webp").convert("RGBA"))
    stand[330:340, 200:240] = GREY  # 엉덩이 칸 안쪽 속옷
    spec.save_webp(Image.fromarray(stand, "RGBA"), folder / "female_stand.webp")
    shared = {f"male_{n}": f"female_{n}" for n in (*spec.BODY_SLOTS, *(f"{s}_cut" for s in spec.CUT_SLOTS))}
    assert 11 in {p.rule for p in check_body(outfits, shared)}


def test_committed_stand_body_has_no_underwear_around_the_hips_and_no_sleeve_line() -> None:
    body = np.array(Image.open(OUTFITS / spec.BODY_FOLDER / "female_stand.webp").convert("RGBA"))
    assert underwear_mask(body).sum() == 0
    legacy = Image.open(RAW / "avatar_legacy_standing_body.png")
    smoothed = np.array(smooth_sleeve_line(Image.fromarray(body, "RGBA"), legacy))
    assert np.abs(smoothed.astype(int) - body.astype(int)).max() <= 2


def test_base_bottom_carries_the_approved_waistband() -> None:
    """맨몸에서 뺀 짙은 허리 밴드는 기본 하의가 그린다: 기본 의상 모습은 승인 텍스처 그대로다."""
    approved = np.array(Image.open(RAW / "avatar_legacy_standing_body.png").convert("RGBA")).astype(int)
    base = np.array(Image.open(OUTFITS / "base_bottom/female_stand.webp").convert("RGBA")).astype(int)
    assert base[315, 256, 3] >= 250
    assert np.abs(base[315, 256, :3] - approved[315, 256, :3]).sum() <= 12


def test_trim_waist_drops_pieces_above_the_waistband_that_float_off_the_bottom() -> None:
    array = np.zeros((spec.SLOTS["stand"].height, spec.SLOTS["stand"].width, 4), np.uint8)
    array[316:500, 120:390] = (60, 56, 61, 255)  # 하의 본체(허리부터 아래)
    array[306:316, 200:230] = (60, 56, 61, 255)  # 본체에 붙은 높은 허리 고리(남긴다)
    array[300:306, 140:150] = (60, 56, 61, 255)  # 떨어진 조각
    image = Image.fromarray(array, "RGBA")
    assert int(waist_tufts_mask(image).sum()) == 60
    trimmed, removed = trim_waist(image)
    out = np.array(trimmed)
    assert removed == 60 and out[300:306, 140:150, 3].max() == 0
    assert (out[306:316, 200:230, 3] == 255).all() and (out[316:500, 120:390, 3] == 255).all()


@pytest.mark.parametrize("key", spec.LEGACY_KEYS["BOTTOM"])
def test_committed_bottoms_have_no_loose_waist_pieces(key: str) -> None:
    assert int(waist_tufts_mask(Image.open(OUTFITS / key / "female_stand.webp")).sum()) == 0


def test_extend_waist_fills_the_body_between_y296_and_the_waistband() -> None:
    size = (spec.SLOTS["stand"].height, spec.SLOTS["stand"].width, 4)
    body = np.zeros(size, np.uint8)
    body[200:600, 130:380] = SKIN
    layer = np.zeros(size, np.uint8)
    layer[318:500, 120:390] = (60, 56, 61, 255)  # 허리가 y 318에서 시작하는 하의(맨몸보다 넓다)
    layer[318, 120:390] = (20, 20, 20, 255)  # 허리 위 외곽선
    assert waist_gap(Image.fromarray(layer, "RGBA"), Image.fromarray(body, "RGBA")) == 22 * 250
    out, added = extend_waist(Image.fromarray(layer, "RGBA"), Image.fromarray(body, "RGBA"))
    array = np.array(out)
    assert added == 22 * 250 and waist_gap(out, Image.fromarray(body, "RGBA")) == 0
    assert tuple(array[300, 200]) == (60, 56, 61, 255)  # 외곽선이 아니라 허리 천 색
    assert array[300, 125, 3] == 0 and array[290, 200, 3] == 0  # 맨몸 밖·띠 위는 그대로


def test_fill_pinholes_closes_small_holes_inside_the_body_only() -> None:
    array = _blob()
    array[30, 30] = 0  # 몸 안 1px 구멍
    array[20:32, 20:22] = 0  # 큰 틈(24px)은 그대로
    out = np.array(fill_pinholes(Image.fromarray(array, "RGBA")))
    assert tuple(out[30, 30]) == SKIN and out[25, 20, 3] == 0 and out[5, 5, 3] == 0


def test_square_waist_sides_continues_the_hip_edge_up_to_the_waist() -> None:
    """허리를 맨몸 실루엣까지만 늘리면 하의 원래 허리(y 약 320)와 3~8px 계단(홈)이 생겨 밑단 옆에 짙은 획·홈으로 보였다(판정 round1 A):
    계단 아래 행의 옆 가장자리(외곽선 포함)를 위로 이어 붙인다."""
    from outfit_pipeline.body import square_waist_sides

    array = np.zeros((768, 512, 4), np.uint8)
    array[296:320, 116:395] = (80, 80, 90, 255)  # 맨몸 실루엣까지 늘린 허리
    array[320:400, 110:403] = (80, 80, 90, 255)  # 하의 본체(더 넓다)
    array[320:400, 110:113] = (40, 30, 30, 255)  # 왼쪽 외곽선
    array[320:400, 400:403] = (40, 30, 30, 255)  # 오른쪽 외곽선
    out = np.array(square_waist_sides(Image.fromarray(array, "RGBA"))[0])
    assert (out[296:320, 110:113, :3] == (40, 30, 30)).all() and (out[296:320, 400:403, 3] == 255).all()
    assert out[296:320, 113:116, 3].min() == 255 and out[296:400, 105:110, 3].max() == 0


@pytest.mark.parametrize("key", [k for k in spec.LEGACY_KEYS["BOTTOM"]])
def test_committed_bottom_waist_sides_have_no_steps(key: str) -> None:
    alpha = np.array(Image.open(OUTFITS / key / f"female_stand{spec.EXTENSION}"))[..., 3] >= 128
    left = [int(np.nonzero(alpha[y])[0].min()) for y in range(296, 340)]
    right = [int(np.nonzero(alpha[y])[0].max()) for y in range(296, 340)]
    steps = [abs(a - b) for side in (left, right) for a, b in itertools.pairwise(side)]
    assert max(steps) <= 2, (left, right)


def test_parallel_wisps_beside_the_outline_are_dropped_but_the_outline_stays() -> None:
    """손 외곽선 바깥 1~3px 틈 너머 나란한 가는 획(털처럼 삐친 선, 판정 round1 E)은 지우고 외곽선·안티앨리어싱은 그대로 둔다."""
    from outfit_pipeline.body import parallel_wisps

    array = np.zeros((60, 60, 4), np.uint8)
    array[10:50, 20:40] = (230, 200, 190, 255)  # 손
    array[10:50, 20:23] = (80, 50, 40, 255)  # 외곽선
    array[10:50, 19] = (80, 50, 40, 120)  # 안티앨리어싱
    array[15:45, 16] = (70, 45, 40, 230)  # 나란한 가는 획(틈 x 17~18)
    mask = parallel_wisps(array)
    assert mask[15:45, 16].all() and not mask[:, 19:].any()


def test_committed_stand_body_has_no_parallel_wisps() -> None:
    from outfit_pipeline.body import parallel_wisps

    body = np.array(Image.open(OUTFITS / spec.BODY_FOLDER / f"female_stand{spec.EXTENSION}").convert("RGBA"))
    # 승인 원본에서 지킨 외곽선(17번 기준)은 남는다: 원본 순서로 지우면 바로 앞 획에 가려 털로 보이지 않던 픽셀
    reference = np.array(Image.open(spec.outline_reference_path("female_stand"))) > 0
    assert int((parallel_wisps(body) & ~reference).sum()) == 0


def test_check_reports_a_bottom_that_stops_below_the_waist(tmp_path: Path) -> None:
    from outfit_pipeline.check import check_bundle, read_index

    outfits = tmp_path / "outfits"
    for name in ("bottom_running_shorts", spec.BODY_FOLDER):
        shutil.copytree(OUTFITS / name, outfits / name)
    entry = next(e for e in read_index(OUTFITS)["outfits"] if e["assetKey"] == "bottom_running_shorts")
    assert check_bundle(outfits, entry, MASKS) == []
    layer = np.array(Image.open(outfits / "bottom_running_shorts/female_stand.webp").convert("RGBA"))
    layer[296:312, 150:360] = 0  # 허리를 띠 아래로 내린다
    spec.save_webp(Image.fromarray(layer, "RGBA"), outfits / "bottom_running_shorts/female_stand.webp")
    assert 13 in {p.rule for p in check_bundle(outfits, entry, MASKS)}


def test_garment_strays_include_thin_brown_wisps_but_not_on_the_body() -> None:
    array = np.zeros((60, 60, 4), np.uint8)
    array[10:50, 10:50] = (150, 210, 190, 255)  # 민트 천
    array[50:58, 28:31] = (110, 70, 50, 255)  # 밑단에서 늘어진 3px 갈색 실오라기(맨몸 외곽선 조각)
    image = Image.fromarray(array, "RGBA")
    assert count_strays(image) == 0  # 3x3 실루엣으로는 굵어서 남는다(맨몸 외곽선은 이 규칙만 본다)
    assert count_strays(image, garment=True) > 0
    cleaned, _ = drop_strays(image, garment=True)
    assert np.array(cleaned)[52:58, 28:31, 3].max() == 0 and np.array(cleaned)[30, 30, 3] == 255


def test_cut_clears_one_pixel_lines_left_between_cut_rows() -> None:
    from outfit_pipeline.cut import close_slits

    before = np.zeros((7, 7, 4), np.uint8)
    before[1:6, 1:6] = SKIN
    after = before.copy()
    after[2, 1:6] = 0  # 한 줄 걸러 지운 빗살
    after[4, 1:6] = 0
    out = close_slits(before, after)
    assert out[3, 1:6, 3].max() == 0  # 지운 줄 사이 1px 남은 줄도 지운다
    assert (out[1, 1:6] == SKIN).all() and (out[5, 1:6] == SKIN).all()  # 한쪽만 지운 줄은 그대로
    slit = before.copy()
    slit[3, 1:6] = 0  # 맨몸 안의 1px 줄 틈(위·아래가 두꺼운 맨몸)은 되살린다
    assert (close_slits(before, slit)[3, 1:6] == SKIN).all()


def test_close_seams_fills_thin_vertical_gaps_between_cloth_only() -> None:
    array = np.zeros((40, 20, 4), np.uint8)
    array[5:20, 2:18] = (60, 56, 61, 255)  # 바지
    array[23:28, 2:18] = (20, 20, 30, 255)  # 밑단 안감(3px 틈 아래)
    array[5:12, 8:12] = 0  # 위가 트인 홈(위에 천이 없다)은 그대로
    image = Image.fromarray(array, "RGBA")
    assert int(seam_mask(image).sum()) == 3 * 16
    out, filled = close_seams(image)
    result = np.array(out)
    assert filled == 48 and (result[20:23, 2:18, 3] == 255).all()
    assert result[21, 5, 0] in range(20, 61) and result[8, 9, 3] == 0


@pytest.mark.parametrize("key", spec.LEGACY_KEYS["BOTTOM"])
def test_committed_bottoms_have_no_thin_gaps(key: str) -> None:
    for name in ("female_stand", "female_run_leg"):
        assert int(seam_mask(Image.open(OUTFITS / key / f"{name}.webp")).sum()) == 0


def test_drop_gap_fragments_removes_dark_slivers_between_arm_and_torso() -> None:
    size = (spec.SLOTS["stand"].height, spec.SLOTS["stand"].width, 4)
    array = np.zeros(size, np.uint8)
    array[150:300, 60:141] = SKIN  # 왼팔
    array[150:300, 141:145] = (60, 30, 18, 255)  # 팔 안쪽 외곽선(남긴다)
    array[170:186, 150:153] = (60, 30, 18, 255)  # 팔과 몸통 사이 틈의 짙은 조각(외곽선과 떨어진 덩어리)
    array[150:300, 145:147] = (60, 30, 18, 255)  # 두꺼운 외곽선 끝(본체와 이어져 남는다)
    out = np.array(drop_gap_fragments(Image.fromarray(array, "RGBA")))
    assert out[170:186, 150:153, 3].max() == 0
    assert (out[150:300, 141:147, 3] == 255).all() and (out[200, 100] == SKIN).all()


def test_body_cleaning_keeps_the_outline_and_its_antialiasing() -> None:
    """맨몸 외곽선과 거기 붙은 안티에일리어싱은 그대로, 고립 점과 팔꿈치 꼬리만 지운다(검수: 외곽선을 깎거나 다시 그리면 팔이 울퉁불퉁했다)."""
    size = (spec.SLOTS["stand"].height, spec.SLOTS["stand"].width, 4)
    array = np.zeros(size, np.uint8)
    array[120:260, 64:130] = SKIN  # 왼팔
    array[120:260, 60:64] = (60, 30, 18, 255)  # 바깥 외곽선
    array[120:260, 59] = (60, 30, 18, 90)  # 외곽선 안티에일리어싱(1px, 반투명)
    array[150, 59] = (60, 30, 18, 200)  # 외곽선에서 1px 튀어나온 들쭉날쭉(붙어 있다)
    array[155, 50:59] = (60, 30, 18, 230)  # 팔꿈치 높이에서 뻗은 꼬리
    array[300, 20] = (60, 30, 18, 200)  # 떨어진 점
    array[310:313, 20:60] = (60, 30, 18, 3)  # 거의 투명한 유령 외곽선(큰 덩어리)
    image = Image.fromarray(array, "RGBA")
    assert int(dust_mask(image).sum()) == 1 + 3 * 40
    assert int(elbow_tail_mask(image).sum()) >= 8
    cleaned, _ = drop_dust(image)
    out = np.array(cleaned)
    assert (out[120:260, 59, 3] > 0).all() and out[150, 59, 3] == 200
    assert out[300, 20, 3] == 0 and out[311, 30, 3] == 0


def test_close_side_gaps_fills_the_slit_between_shorts_and_liner() -> None:
    size = (spec.SLOTS["stand"].height, spec.SLOTS["stand"].width, 4)
    body = np.zeros(size, np.uint8)
    body[300:430, 100:412] = SKIN
    layer = np.zeros(size, np.uint8)
    layer[320:395, 104:400] = (60, 56, 61, 255)  # 바지
    layer[399:404, 104:140] = (40, 36, 41, 255)  # 떨어진 안감 밑단(4px 트임)
    layer[395:399, 104:110] = (60, 56, 61, 255)  # 트임 바깥 끝은 닫혀 있다
    image, base = Image.fromarray(layer, "RGBA"), Image.fromarray(body, "RGBA")
    assert int(side_gap_mask(image, base).sum()) > 0
    out, filled = close_side_gaps(image, base)
    assert filled > 0 and side_gap_mask(out, base).sum() == 0
    assert np.array(out)[397, 120, 3] == 255 and np.array(out)[410, 120, 3] == 0


@pytest.mark.parametrize("key", spec.LEGACY_KEYS["BOTTOM"])
def test_committed_bottoms_have_no_side_gaps(key: str) -> None:
    body = Image.open(OUTFITS / spec.BODY_FOLDER / "female_stand.webp")
    assert int(side_gap_mask(Image.open(OUTFITS / key / "female_stand.webp"), body).sum()) == 0


def test_committed_stand_body_has_no_gap_fragments_and_keeps_the_outline() -> None:
    body = Image.open(OUTFITS / spec.BODY_FOLDER / "female_stand.webp").convert("RGBA")
    assert np.array_equal(np.array(drop_gap_fragments(body)), np.array(body))
    for slot in spec.BODY_SLOTS:
        image = Image.open(OUTFITS / spec.BODY_FOLDER / f"female_{slot}.webp")
        reference = np.array(Image.open(spec.outline_reference_path(f"female_{slot}")).convert("L")) > 0
        assert reference.sum() > 500
        assert int(outline_breaks(image, reference).sum()) == 0, slot
        assert int(dust_mask(image).sum()) == 0, slot


def test_hip_cut_removes_only_body_outside_the_clothes_row_span() -> None:
    """엉덩이 옆 칸(230): 옷이 그 행에 걸친 가로 범위 밖만 지운다. 범위 안(밑단 옆 트임 아래 다리)과 옷이 없는 행은 남긴다(앱 TopBodyCut과 같다)."""
    from outfit_pipeline.cut import apply

    body = Image.new("RGBA", (40, 30), (0, 0, 0, 0))
    body.paste(SKIN, (2, 0, 38, 30))
    top = Image.new("RGBA", (40, 30), (0, 0, 0, 0))
    top.paste((60, 56, 61, 255), (8, 0, 32, 20))  # 하의(맨몸보다 좁다)
    top.paste((0, 0, 0, 0), (12, 16, 18, 20))  # 밑단 옆 트임(아래로 열림)
    area = Image.new("RGBA", (40, 30), (0, 0, 0, 0))
    labels = Image.new("L", (40, 30), spec.CUT_HIP)
    area.putalpha(labels)
    out = np.array(apply(body, top, area))
    assert out[10, 3, 3] == 0 and out[10, 35, 3] == 0  # 하의 옆(가로 범위 밖) 맨몸은 지운다
    assert out[18, 14, 3] == 255  # 트임 아래 맨몸(가로 범위 안)은 남긴다
    assert out[25, 3, 3] == 255  # 하의가 없는 행(다리)은 남긴다


def test_hip_cut_keeps_the_top_of_the_leg_below_the_zone() -> None:
    """엉덩이 옆 칸 바로 아래 행 다리의 가로 범위 안은 옷 범위 밖이어도 남긴다(밑단 모서리 아래 다리 윗부분)."""
    from outfit_pipeline.cut import apply

    body = Image.new("RGBA", (40, 40), (0, 0, 0, 0))
    body.paste(SKIN, (2, 0, 38, 20))  # 옛 반바지 실루엣(넓다)
    body.paste(SKIN, (10, 20, 30, 40))  # 다리
    top = Image.new("RGBA", (40, 40), (0, 0, 0, 0))
    top.paste((60, 56, 61, 255), (14, 0, 26, 18))  # 하의(밑단 쪽이 좁다)
    area = Image.new("RGBA", (40, 40), (0, 0, 0, 0))
    labels = Image.new("L", (40, 40), 0)
    labels.paste(spec.CUT_HIP, (0, 0, 40, 20))
    area.putalpha(labels)
    out = np.array(apply(body, top, area))
    assert out[10, 4, 3] == 0  # 다리 범위 밖 옛 실루엣은 지운다
    assert out[10, 11, 3] == 255  # 다리 범위 안(다리 윗부분)은 남긴다


def test_cover_run_leg_hides_the_body_leg_and_outlines_the_garment() -> None:
    """긴 하의 달리기 다리: 맨몸 다리가 하의보다 넓은 곳(무릎 겹침, 뒤 허벅지)의 피부를 하의 색으로 덮고, 배경과 맞닿은 하의 가장자리는
    짙은 외곽선으로 칠한다(판정 round1 C: 레깅스 무릎 피부 쐐기, wide·jogger 외곽선 없음)."""
    from outfit_pipeline.body import cover_run_leg

    body = np.zeros((700, 256, 4), np.uint8)
    body[0:690, 40:200] = (240, 200, 185, 255)
    body[0:690, 40:44] = (70, 45, 40, 255)  # 맨몸 외곽선
    body[0:690, 196:200] = (70, 45, 40, 255)
    garment = np.zeros_like(body)
    garment[0:600, 60:230] = (200, 180, 140, 255)  # 앞은 다리보다 넓고 뒤는 좁은 바지
    out = np.array(cover_run_leg(Image.fromarray(garment, "RGBA"), Image.fromarray(body, "RGBA"))[0])
    assert (out[10:590, 44:60, 3] == 255).all()  # 뒤 허벅지 피부를 덮었다
    assert out[300, 228, :3].mean() < 110 and out[300, 120, :3].mean() > 150  # 배경 쪽 가장자리만 짙다
    assert out[650, 100, 3] == 0  # 바지 밑단 아래 다리는 그대로
