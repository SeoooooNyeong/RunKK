"""파이프라인 시험: riv 읽기, UV 마스크, 규격 검사, 묶음 빌드(OUTFIT_SPEC.md 5·6절)."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from outfit_pipeline import spec
from outfit_pipeline.build import build_bundle, clip, cut_area, extract, make_shop, update_index
from outfit_pipeline.check import check_all, check_bundle, check_shop, read_index
from outfit_pipeline.cli import MASKS, OUTFITS, RAW
from outfit_pipeline.masks import load_mask, stand_zones, uv_mask
from outfit_pipeline.riv import read_scene


def test_riv_scene_reads_artboard_images_and_mesh_uvs() -> None:
    scene = read_scene(RAW / "avatar_male_idle.riv")
    assert "standing_body" in scene.assets
    assert scene.asset_bytes["standing_body"][:4] == b"\x89PNG"
    names = {i.name for i in scene.images if i.asset == "standing_body"}
    assert {"Existing wardrobe top", "Long sleeve wardrobe top", "Preserved approved hands"} <= names
    top = next(i for i in scene.images if i.name == "Existing wardrobe top")
    assert top.triangles and len(top.triangles) % 3 == 0
    assert all(0.0 <= u <= 1.0 and 0.0 <= v <= 1.0 for u, v in top.uvs)
    # 표면 애니메이션이 불투명도를 바꾼다(남자 selected_top, DR-성-1).
    assert scene.opacity_keys["top surface 2"][top.index] == 0.0


def test_visible_meshes_follow_state_animations() -> None:
    female = read_scene(RAW / "avatar_idle_layered.riv")
    shown = {i.name for i in female.visible_images("standing_body", ["Body shape medium"])}
    assert shown == {"Smooth standing body"}
    run = read_scene(RAW / "avatar_run_approved.riv")
    assert [i.name for i in run.visible_images("side_body_coral", [])] == ["Side outfit 0"]
    shoes = run.visible_images("side_shoe", [])
    assert shoes and all(i.triangles is None for i in shoes)  # 메시 없이 전체가 보인다


def test_masks_cover_the_expected_areas() -> None:
    male = uv_mask(RAW, spec.TEMPLATES["male_stand_s1"])
    assert male.size == (512, 768)
    zones = stand_zones(RAW)
    assert zones["HANDS"].getpixel((40, 350)) == 255
    assert zones["TOP"].getpixel((256, 100)) == 255
    assert zones["BOTTOM"].getpixel((256, 450)) == 255
    assert zones["SHOES"].getpixel((180, 700)) == 255
    arm = uv_mask(RAW, spec.TEMPLATES["female_run_arm"])
    assert arm.getpixel((128, 20)) == 0  # 팔 메시 위 7%는 보이지 않는다
    assert arm.getpixel((128, 600)) == 255
    top = load_mask(MASKS, "female_stand", "TOP")
    assert top.getpixel((256, 324)) == 255  # 겹침 띠: 상의 밑단이 하의 칸으로 8px
    assert top.getpixel((256, 340)) == 0
    assert top.getpixel((40, 350)) == 0  # 손 칸은 상의 금지


def test_committed_assets_pass_all_checks() -> None:
    assert check_all(OUTFITS, MASKS) == []


def _bundle(tmp: Path, key: str) -> Path:
    outfits = tmp / "outfits"
    shutil.copytree(OUTFITS / key, outfits / key)
    return outfits


def _entry(key: str) -> dict:
    return next(e for e in read_index(OUTFITS)["outfits"] if e["assetKey"] == key)


def test_check_rejects_forbidden_pixels_bad_names_and_sizes(tmp_path: Path) -> None:
    outfits = _bundle(tmp_path, "base_bottom")
    entry = _entry("base_bottom")
    assert check_bundle(outfits, entry, MASKS) == []
    layer = Image.open(outfits / "base_bottom/female_stand.webp").convert("RGBA")
    layer.paste((255, 0, 0, 255), (250, 100, 258, 108))  # 상의 칸(고립 점 규칙에 걸리지 않게 8x8)
    spec.save_webp(layer, outfits / "base_bottom/female_stand.webp")
    spec.save_webp(Image.new("RGBA", (100, 100)), outfits / "base_bottom/female_run_leg.webp")
    spec.save_webp(Image.new("RGBA", (256, 768)), outfits / "base_bottom/female_run_arm.webp")
    rules = sorted({p.rule for p in check_bundle(outfits, entry, MASKS)})
    assert rules == [2, 3, 6]


def test_check_requires_male_top_surface_files_and_valid_shared(tmp_path: Path) -> None:
    outfits = _bundle(tmp_path, "base_top")
    (outfits / "base_top/female_run_arm.webp").unlink()  # 남자도 이 파일을 함께 쓴다
    entry = {**_entry("base_top"), "bodyOverrides": ["female_stand_b2"]}
    entry.pop("maleTopSurface")
    rules = sorted({p.rule for p in check_bundle(outfits, entry, MASKS)})
    assert rules == [1, 8]
    bad = {**_entry("base_top"), "shared": {"female_stand": "male_stand"}}  # 있는 파일을 공용 이름으로
    assert 6 in {p.rule for p in check_bundle(_bundle(tmp_path / "b", "base_top"), bad, MASKS)}


def test_shop_image_is_cropped_and_centered(tmp_path: Path) -> None:
    stand = Image.open(OUTFITS / "top_crew_tee/female_stand.webp").convert("RGBA")
    shop = make_shop(stand)
    assert shop.size == (512, 512)
    box = shop.getchannel("A").getbbox()
    assert box is not None and max(box[2] - box[0], box[3] - box[1]) == spec.SHOP_LONG_SIDE
    path = tmp_path / spec.SHOP_FILE
    spec.save_webp(shop, path)
    assert check_shop(path) == []


def test_clip_and_extract_keep_only_garment_inside_allowed_area() -> None:
    allowed = Image.new("L", (40, 40), 0)
    allowed.paste(255, (0, 0, 20, 40))
    body = Image.new("RGBA", (40, 40), (250, 222, 205, 255))  # 피부
    dressed = body.copy()
    dressed.paste((40, 90, 200, 255), (5, 5, 35, 30))  # 파란 옷이 허용 영역 밖까지
    out = extract(dressed, body, allowed)
    assert out.getpixel((10, 10))[3] == 255  # 옷
    assert out.getpixel((25, 10))[3] == 0  # 허용 영역 밖
    assert out.getpixel((10, 35))[3] == 0  # 피부
    faint = Image.new("RGBA", (4, 4), (10, 10, 10, 5))
    assert clip(faint, Image.new("L", (4, 4), 255)).getchannel("A").getbbox() is None


def test_build_bundle_writes_lossless_webp_shop_index_and_shares_same_genders(tmp_path: Path) -> None:
    source = tmp_path / "src/base_shoes"
    source.mkdir(parents=True)
    entry = _entry("base_shoes")
    for name in ("female_stand", "male_stand", "female_run_shoe", "male_run_shoe"):
        image = Image.open(OUTFITS / "base_shoes" / f"{entry.get('shared', {}).get(name, name)}.webp").convert("RGBA")
        image.resize((image.width * 2, image.height * 2), Image.Resampling.NEAREST).save(source / f"{name}.png")
    (source / "notes.png").write_bytes(b"")  # 규칙 밖 이름은 무시
    outfits = tmp_path / "outfits"
    (outfits / spec.BODY_FOLDER).mkdir(parents=True)
    built, problems = build_bundle(source, outfits, MASKS, "base_shoes")
    assert problems == []
    assert built["shared"] == {"male_run_shoe": "female_run_shoe", "male_stand": "female_stand"}
    assert not (outfits / "base_shoes/male_stand.webp").exists()
    assert Image.open(outfits / "base_shoes/female_stand.webp").size == (512, 768)
    assert (outfits / "base_shoes" / spec.SHOP_FILE).is_file()
    update_index(outfits, built)
    update_index(outfits, {"assetKey": "base_top", "part": "TOP", "maleTopSurface": 1, "bodyOverrides": []})
    update_index(outfits, body_shared={"male_stand": "female_stand"})
    index = json.loads((outfits / "index.json").read_text(encoding="utf-8"))
    assert [e["assetKey"] for e in index["outfits"]] == ["base_top", "base_shoes"]
    assert index["version"] == 2 and index["body"] == {"shared": {"male_stand": "female_stand"}}


def test_webp_is_lossless(tmp_path: Path) -> None:
    image = Image.open(OUTFITS / "top_crew_tee/female_stand.webp").convert("RGBA")
    spec.save_webp(image, tmp_path / "x.webp")
    import numpy as np

    assert np.array_equal(np.array(Image.open(tmp_path / "x.webp").convert("RGBA")), np.array(image))


def test_cut_area_keeps_hands_and_neck() -> None:
    area = cut_area(RAW, "stand")
    assert area.getpixel((256, 150)) == spec.CUT_TORSO  # 몸통
    assert area.getpixel((120, 60)) == spec.CUT_LEFT_SHOULDER  # 어깨(덮인 몫으로 자르는 띠, 민소매는 맨어깨)
    assert area.getpixel((400, 60)) == spec.CUT_RIGHT_SHOULDER
    assert area.getpixel((256, 40)) == 0  # 목·가슴 위
    assert area.getpixel((40, 350)) == 0  # 손 칸
    assert area.getpixel((256, 500)) == 0  # 하의 칸
    run_body = cut_area(RAW, "run_body")
    assert run_body.getpixel((150, 20)) == 0 and run_body.getpixel((128, 200)) == spec.CUT_ALL
    assert area.getpixel((60, 300)) == spec.CUT_LEFT_ARM and area.getpixel((450, 300)) == spec.CUT_RIGHT_ARM


@pytest.mark.parametrize("key", ["top_cherry_vest", "top_lime_singlet", "base_top"])
def test_default_male_top_surface(key: str) -> None:
    assert spec.default_male_top_surface(key) == (2 if key == "top_cherry_vest" else 1)


def test_cut_matches_app_fixture() -> None:
    """앱 TopBodyCutParityTest와 같은 픽스처: 파이프라인 cut.py 결과가 expected.png와 같다(Kotlin도 같은 파일과 비교한다)."""
    import numpy as np

    from outfit_pipeline.cut import apply
    from tests.make_topcut_fixture import OUT, expected, sets

    assert np.array_equal(np.array(expected()), np.array(Image.open(OUT / "expected.png").convert("RGBA")))
    # 칼라 옆 칸(221·181) 픽스처와 승인 묶음의 실제 조합도 같은 파일과 비교한다
    for prefix, images in sets().items():
        saved = [np.array(Image.open(OUT / f"{prefix}{n}.png").convert("RGBA")) for n in ("body", "top", "area")]
        assert all(np.array_equal(np.array(i), s) for i, s in zip(images, saved, strict=True)), prefix
        want = np.array(Image.open(OUT / f"{prefix}expected.png").convert("RGBA"))[..., 3] > 0
        assert np.array_equal(np.array(apply(*images))[..., 3] > 0, want), prefix


def test_skin_rule_counts_only_skin_that_touches_empty_space() -> None:
    """맨살 규칙(DV3-8): 빈 곳에 닿은 피부 색조 덩어리만 센다. 천 안에 갇힌 피부색 무늬, 크림·코랄 천은 세지 않는다."""
    import numpy as np

    from outfit_pipeline.skin import count_exposed, strip_skin

    skin, cream, coral = (246, 209, 197, 255), (246, 223, 203, 255), (240, 120, 90, 255)
    array = np.zeros((40, 40, 4), np.uint8)
    array[:, 10:40] = coral  # 천
    array[5:15, 10:20] = skin  # 빈 곳(x < 10)에 닿은 맨살 100픽셀
    array[25:30, 25:30] = skin  # 천에 갇힌 피부색 무늬
    array[30:38, 12:20] = cream
    image = Image.fromarray(array, "RGBA")
    assert count_exposed(image) == 100
    stripped, removed = strip_skin(image)
    assert removed == 100 and count_exposed(stripped) == 0
    assert np.array(stripped)[27, 27, 3] == 255


def test_garment_layers_have_no_exposed_skin() -> None:
    """옷 레이어에 승인 텍스처의 맨살이 남지 않는다(피부는 맨몸 바탕에서만 와 톤이 걸린다)."""
    from outfit_pipeline.skin import count_exposed

    found = {
        f"{p.parent.name}/{p.name}": n
        for p in sorted(OUTFITS.glob("*/*.webp"))
        if p.parent.name != spec.BODY_FOLDER and p.name != spec.SHOP_FILE and (n := count_exposed(Image.open(p))) > 0
    }
    assert found == {}


def test_check_reports_exposed_skin(tmp_path: Path) -> None:
    import numpy as np

    from outfit_pipeline.check import check_layer, template_for

    layer = np.array(Image.open(OUTFITS / "top_crew_tee/female_stand.webp").convert("RGBA"))
    ys, xs = np.nonzero(layer[..., 3] > 200)
    y, x = ys[len(ys) // 2], xs.min()
    layer[y - 4 : y + 3, x : x + 7] = (
        246,
        209,
        197,
        255,
    )  # 왼쪽 빈 곳에 닿은 맨살(7x7: 고립 점 규칙 40px 미만에 걸리지 않게)
    path = tmp_path / "female_stand.webp"
    spec.save_webp(Image.fromarray(layer, "RGBA"), path)
    problems = check_layer(path, "TOP", template_for(path.name, "TOP", 1), MASKS)
    # 붙인 사각형이 옷 가장자리에 좁은 틈을 만들면 16번(구멍)도 함께 나올 수 있다
    rules = {p.rule for p in problems}
    assert 9 in rules and rules <= {9, 16}


def test_neck_side_cut_removes_skin_outside_sleeved_tops_only() -> None:
    """칼라 옆 칸(221·181)은 짝 어깨 띠(220·180)를 상의가 덮을 때만 상의 밖 맨몸을 모두 지운다(판정 round1~3 B: windbreaker 칼라 옆 획).
    앱 TopBodyCutParityTest의 같은 이름 시험과 같은 입력·기대값."""
    import numpy as np

    from outfit_pipeline.cut import apply

    width, height = 40, 20
    labels = np.zeros((height, width), np.uint8)
    labels[:, 0:20] = 220
    labels[:, 20:30] = 221
    area = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    area.putalpha(Image.fromarray(labels))

    def cut(shoulder: tuple[int, int]) -> np.ndarray:
        body = np.zeros((height, width, 4), np.uint8)
        body[:, 2:38] = (246, 205, 190, 255)
        top = np.zeros((height, width, 4), np.uint8)
        top[:, shoulder[0] : shoulder[1]] = (60, 56, 61, 255)
        top[:, 26:30] = (60, 56, 61, 255)
        return np.array(apply(Image.fromarray(body, "RGBA"), Image.fromarray(top, "RGBA"), area))[..., 3]

    sleeved = cut((2, 20))
    assert (sleeved[5, 20:26] == 0).all() and (sleeved[5, 30:38] == 255).all()
    assert (cut((2, 6))[5, 20:26] == 255).all()


def test_stand_cut_area_marks_neck_sides() -> None:
    """서기 자르기 칸: 맨 위 목 둘레(NECK_TOP 행) 가운데 목 기둥 밖 양옆이 칼라 옆 칸 값이다."""
    import numpy as np

    from outfit_pipeline.build import cut_area
    from outfit_pipeline.cli import RAW

    area = np.array(cut_area(RAW, "stand"))
    (l0, l1), (r0, r1) = spec.NECK_SIDES
    assert (area[: spec.NECK_TOP[3], l0:l1] == spec.CUT_LEFT_NECK_SIDE).all()
    assert (area[: spec.NECK_TOP[3], r0:r1] == spec.CUT_RIGHT_NECK_SIDE).all()
    assert (area[: spec.NECK_TOP[3], l1:r0] == 0).all()  # 목 기둥은 자르지 않는다
    assert area[spec.NECK_TOP[3] + 2, l0] == spec.CUT_LEFT_SHOULDER


def test_restore_enclosed_keeps_holes_away_from_the_background() -> None:
    """앱 TopBodyCutParityTest의 같은 시험과 같은 입력·기대값(판정 round4 crew 소매 끝 아래 틈)."""
    import numpy as np

    from outfit_pipeline.cut import restore_enclosed

    before = np.zeros((12, 12, 4), np.uint8)
    before[1:11, 1:11] = (246, 205, 190, 255)
    top = np.zeros((12, 12), bool)
    top[1:3, 1:11] = True
    after = before.copy()
    after[5, 5] = 0
    after[6, 5] = 0
    after[8, 1] = 0
    out = restore_enclosed(before, after, top)
    assert out[5, 5, 3] == 255 and out[6, 5, 3] == 255
    assert out[8, 1, 3] == 0


def test_full_cut_cell_is_not_restored() -> None:
    """모두 지우는 칸(240) 안의 맨몸은 배경에 닿지 않아도 되살리지 않는다(앱 TopBodyCutParityTest와 같은 입력·기댓값). 칸은 상의에서
    RIM_PX보다 멀어 부스러기 지우기에 걸리지 않는다."""
    from outfit_pipeline.cut import apply

    width, height = 50, 8
    body = np.zeros((height, width, 4), np.uint8)
    body[1:7, 1:49] = (246, 205, 190, 255)
    top = np.zeros_like(body)
    top[1:7, 44:47] = (60, 80, 120, 255)
    area = np.zeros((height, width, 4), np.uint8)
    area[2:6, 3:5, 3] = 240
    out = np.array(apply(Image.fromarray(body, "RGBA"), Image.fromarray(top, "RGBA"), Image.fromarray(area, "RGBA")))
    removed = [(y, x) for y in range(1, 7) for x in range(1, 49) if out[y, x, 3] == 0]
    assert removed == [(y, x) for y in range(2, 6) for x in (3, 4)]


def test_leg_span_does_not_wrap_when_the_hip_cell_starts_near_the_top() -> None:
    """엉덩이 옆 칸 맨 아래 행이 LEG_GUARD보다 위에 있어도 시작 행이 음수로 돌아 감기지 않는다(맨 위 행부터 지킨다)."""
    from outfit_pipeline.cut import _leg_span

    skin = np.zeros((10, 6), bool)
    skin[:, 1:5] = True
    hip = np.zeros_like(skin)
    hip[0:6] = True  # 칸 맨 아래 행 5, 다리 행 6
    out = _leg_span(skin, hip)
    assert out[0:6, 1:5].all() and not out[6:].any() and not out[:, 0].any()


def test_drop_faint_clears_nearly_transparent_pixels() -> None:
    """합성 텍스처의 알파 1~15 픽셀은 비운다(RGB 0). 앱 OutfitLayerComposer.dropFaint와 같다."""
    from outfit_pipeline.cut import FAINT_ALPHA, drop_faint

    a = np.zeros((1, 4, 4), np.uint8)
    a[0, 0] = (255, 255, 255, 1)
    a[0, 1] = (200, 100, 50, FAINT_ALPHA - 1)
    a[0, 2] = (200, 100, 50, FAINT_ALPHA)
    a[0, 3] = (10, 20, 30, 255)
    out = np.array(drop_faint(Image.fromarray(a, "RGBA")))
    assert out[0, 0].tolist() == [0, 0, 0, 0] and out[0, 1].tolist() == [0, 0, 0, 0]
    assert out[0, 2].tolist() == [200, 100, 50, FAINT_ALPHA] and out[0, 3].tolist() == [10, 20, 30, 255]
