"""`uv-mask`: riv 메시 UV 삼각형을 텍스처 좌표에 칠해 허용 영역(화면에 보이는 픽셀)과 부위 칸 마스크를 만든다(5.3, DR-성-3)."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

from outfit_pipeline import spec
from outfit_pipeline.riv import RivImage, RivScene, read_scene

_scenes: dict[Path, RivScene] = {}


def scene(raw_dir: Path, riv: str) -> RivScene:
    path = raw_dir / riv
    if path not in _scenes:
        _scenes[path] = read_scene(path)
    return _scenes[path]


def rasterize(images: list[RivImage], width: int, height: int) -> Image.Image:
    """Image들의 UV 덮임(255 = 보임). 메시가 없는 Image는 텍스처 전체가 보인다."""
    mask = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(mask)
    for image in images:
        if image.triangles is None:
            draw.rectangle((0, 0, width, height), fill=255)
            continue
        uvs = image.uvs
        tri = image.triangles
        for k in range(0, len(tri) - 2, 3):
            points = [(uvs[tri[k + j]][0] * width, uvs[tri[k + j]][1] * height) for j in range(3)]
            draw.polygon(points, fill=255, outline=255)
    return mask


def uv_mask(raw_dir: Path, template: spec.Template) -> Image.Image:
    slot = spec.SLOTS[template.slot]
    riv = scene(raw_dir, template.riv)
    shown: dict[int, RivImage] = {}
    for state in template.states:
        for image in riv.visible_images(slot.asset, [a for a in state if a]):
            shown[image.index] = image
    if not shown:
        raise ValueError(f"no visible mesh for {template.name}")
    return rasterize(list(shown.values()), slot.width, slot.height)


def stand_zones(raw_dir: Path) -> dict[str, Image.Image]:
    """서기 아틀라스 부위 칸(TOP·BOTTOM·SHOES·HANDS). 겹치는 픽셀은 앞 칸이 갖는다."""
    riv = scene(raw_dir, spec.STAND_ZONE_RIV)
    slot = spec.SLOTS["stand"]
    zones: dict[str, Image.Image] = {}
    taken = Image.new("L", (slot.width, slot.height), 0)
    for zone in ("HANDS", "TOP", "BOTTOM", "SHOES"):
        images = [i for i in riv.images if i.name in spec.STAND_ZONE_IMAGES[zone]]
        mask = ImageChops.subtract(rasterize(images, slot.width, slot.height), taken)
        zones[zone] = mask
        taken = ImageChops.lighter(taken, mask)
    return zones


def _shift(mask: Image.Image, dy: int) -> Image.Image:
    shifted = Image.new("L", mask.size, 0)
    shifted.paste(mask, (0, dy))
    return shifted


def _grow_into(own: Image.Image, other: Image.Image, dy_sign: int) -> Image.Image:
    """[own]을 세로로 OVERLAP px 늘린 띠 가운데 [other] 칸에 들어가는 부분."""
    grown = own
    for step in range(1, spec.OVERLAP + 1):
        grown = ImageChops.lighter(grown, _shift(own, step * dy_sign))
    return ImageChops.multiply(grown, other)


def allowed(raw_dir: Path, template: spec.Template, part: str) -> Image.Image:
    """[template] 슬롯에서 [part] 파일이 그려도 되는 픽셀(255) = 부위 칸 + 겹침 띠, 그리고 UV 허용 영역 안."""
    uv = uv_mask(raw_dir, template)
    if template.slot != "stand":
        if spec.RUN_SLOT_PART[template.slot] != part:
            return Image.new("L", uv.size, 0)
        return uv
    zones = stand_zones(raw_dir)
    area = zones[part]
    for other in spec.OVERLAP_DOWN[part]:
        area = ImageChops.lighter(area, _grow_into(zones[part], zones[other], 1))
    for other in spec.OVERLAP_UP[part]:
        area = ImageChops.lighter(area, _grow_into(zones[part], zones[other], -1))
    return ImageChops.multiply(area, uv)


def write_masks(raw_dir: Path, out_dir: Path) -> list[Path]:
    """템플릿마다 `<이름>.png`(UV 허용 영역)와 `<이름>_<부위>.png`(그 부위 허용)를 1비트 PNG로 쓴다."""
    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    for template in spec.TEMPLATES.values():
        path = out_dir / f"{template.name}.png"
        uv_mask(raw_dir, template).convert("1").save(path, optimize=True)
        written.append(path)
        parts = spec.PARTS if template.slot == "stand" else (spec.RUN_SLOT_PART[template.slot],)
        for part in parts:
            path = out_dir / f"{template.name}_{part}.png"
            allowed(raw_dir, template, part).convert("1").save(path, optimize=True)
            written.append(path)
    return written


def load_mask(masks_dir: Path, template: str, part: str | None = None) -> Image.Image:
    name = f"{template}_{part}.png" if part else f"{template}.png"
    return Image.open(masks_dir / name).convert("L")
