"""파이프라인 시험: riv 읽기, UV 마스크, 규격 검사, 묶음 빌드(OUTFIT_SPEC.md 5·6절)."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

import pytest
from PIL import Image

from outfit_pipeline import spec
from outfit_pipeline.build import build_bundle, clip, extract, make_shop, update_index
from outfit_pipeline.check import check_all, check_bundle, check_shop
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


def test_check_rejects_forbidden_pixels_bad_names_and_sizes(tmp_path: Path) -> None:
    outfits = _bundle(tmp_path, "base_bottom")
    entry = {"assetKey": "base_bottom", "part": "BOTTOM", "bodyOverrides": []}
    assert check_bundle(outfits, entry, MASKS) == []
    layer = Image.open(outfits / "base_bottom/female_stand.png").convert("RGBA")
    layer.putpixel((256, 100), (255, 0, 0, 255))  # 상의 칸
    layer.save(outfits / "base_bottom/female_stand.png")
    Image.new("RGBA", (100, 100)).save(outfits / "base_bottom/male_run_leg.png")
    Image.new("RGBA", (256, 768)).save(outfits / "base_bottom/female_run_arm.png")
    rules = sorted({p.rule for p in check_bundle(outfits, entry, MASKS)})
    assert rules == [2, 3, 6]


def test_check_requires_male_top_surface_and_files(tmp_path: Path) -> None:
    outfits = _bundle(tmp_path, "base_top")
    (outfits / "base_top/male_run_arm.png").unlink()
    entry = {"assetKey": "base_top", "part": "TOP", "bodyOverrides": ["female_stand_b2"]}
    rules = sorted({p.rule for p in check_bundle(outfits, entry, MASKS)})
    assert rules == [1, 8]


def test_shop_image_is_cropped_and_centered() -> None:
    stand = Image.open(OUTFITS / "top_crew_tee/female_stand.png")
    shop = make_shop(stand)
    assert shop.size == (512, 512)
    box = shop.getchannel("A").getbbox()
    assert box is not None and max(box[2] - box[0], box[3] - box[1]) == spec.SHOP_LONG_SIDE
    assert check_shop_ok(shop)


def check_shop_ok(image: Image.Image, tmp: Path | None = None) -> bool:
    path = Path(__file__).parent / "_shop.png"
    image.save(path)
    try:
        return check_shop(path) == []
    finally:
        path.unlink()


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


def test_build_bundle_writes_files_shop_and_index(tmp_path: Path) -> None:
    source = tmp_path / "src/base_shoes"
    source.mkdir(parents=True)
    for name in ("female_stand", "male_stand", "female_run_shoe", "male_run_shoe"):
        image = Image.open(OUTFITS / f"base_shoes/{name}.png")
        image.resize((image.width * 2, image.height * 2)).save(source / f"{name}.png")  # 2배로 그린 원본
    (source / "notes.png").write_bytes(b"")  # 규칙 밖 이름은 무시
    outfits = tmp_path / "outfits"
    (outfits / spec.BODY_FOLDER).mkdir(parents=True)
    entry, problems = build_bundle(source, outfits, MASKS, "base_shoes")
    assert problems == []
    assert entry == {"assetKey": "base_shoes", "part": "SHOES", "bodyOverrides": []}
    assert Image.open(outfits / "base_shoes/female_stand.png").size == (512, 768)
    assert (outfits / "base_shoes/shop.png").is_file()
    update_index(outfits, entry)
    update_index(outfits, {"assetKey": "base_top", "part": "TOP", "maleTopSurface": 1, "bodyOverrides": []})
    keys = [e["assetKey"] for e in json.loads((outfits / "index.json").read_text(encoding="utf-8"))["outfits"]]
    assert keys == ["base_top", "base_shoes"]


@pytest.mark.parametrize("key", ["top_cherry_vest", "top_lime_singlet", "base_top"])
def test_default_male_top_surface(key: str) -> None:
    assert spec.default_male_top_surface(key) == (2 if key == "top_cherry_vest" else 1)
