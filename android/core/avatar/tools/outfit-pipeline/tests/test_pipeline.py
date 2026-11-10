"""파이프라인 시험: riv 읽기, UV 마스크, 규격 검사, 묶음 빌드(OUTFIT_SPEC.md 5·6절)."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

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
    layer.putpixel((256, 100), (255, 0, 0, 255))  # 상의 칸
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
    assert area.getpixel((120, 60)) == spec.CUT_TORSO  # 어깨
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

    from tests.make_topcut_fixture import OUT, expected

    assert np.array_equal(np.array(expected()), np.array(Image.open(OUT / "expected.png").convert("RGBA")))


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
    layer[y - 3 : y + 3, x : x + 6] = (246, 209, 197, 255)  # 왼쪽 빈 곳에 닿은 맨살
    path = tmp_path / "female_stand.webp"
    spec.save_webp(Image.fromarray(layer, "RGBA"), path)
    problems = check_layer(path, "TOP", template_for(path.name, "TOP", 1), MASKS)
    assert [p.rule for p in problems] == [9]
