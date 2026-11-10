"""원본 → 정렬·잘라내기 → assets/outfits/<assetKey>/ + shop.png + index.json (5.2, D-성-113, D-성-114).

원본 폴더 하나 = 묶음 하나: `<원본>/<assetKey>/{g}_{slot}{b}.png`. 크기가 슬롯의 정수배(예: 2배로 그린 그림)면 줄인다.
`extract`는 몸 전체로 그린 그림(맨몸 위에 옷을 입힌 아틀라스)에서 맨몸과 다른 픽셀만 의상 레이어로 뽑는다.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageFilter

from outfit_pipeline import spec
from outfit_pipeline.check import Problem, check_bundle, template_for
from outfit_pipeline.masks import load_mask

ALPHA_FLOOR = 8  # 이보다 옅은 알파는 잡티로 보고 0


def to_slot(image: Image.Image, slot: spec.Slot) -> Image.Image:
    """RGBA로 바꾸고 슬롯 크기로 줄인다. 비율이 다르면 실패."""
    image = image.convert("RGBA")
    if image.size == (slot.width, slot.height):
        return image
    if image.width * slot.height != image.height * slot.width:
        raise ValueError(f"aspect {image.size} differs from slot {slot.width}x{slot.height}")
    return image.resize((slot.width, slot.height), Image.Resampling.LANCZOS)


def clip(image: Image.Image, allowed: Image.Image) -> Image.Image:
    """허용 영역 밖 알파를 0으로, 옅은 잡티 알파도 0으로. 색은 그대로."""
    alpha = image.getchannel("A").point(lambda v: 0 if v < ALPHA_FLOOR else v)
    alpha = ImageChops.multiply(alpha, allowed)
    out = image.copy()
    out.putalpha(alpha)
    # 알파 0 픽셀의 색을 비워 PNG 크기를 줄이고 보간 때 번짐을 막는다.
    array = np.array(out)
    array[array[..., 3] == 0] = 0
    return Image.fromarray(array, "RGBA")


def outline_cut(image: Image.Image, allowed: Image.Image, strength: float = 0.55) -> Image.Image:
    """허용 영역 경계에서 잘린 옷 가장자리에 1px 윤곽(옷 색의 어두운 톤)을 다시 그린다(5.3, D-성-116 외곽선)."""
    array = np.array(image).astype(np.float32)
    alpha = array[..., 3] > 0
    edge = np.array(allowed.filter(ImageFilter.MinFilter(3))) == 0  # 허용 영역 가장자리 1px
    cut = alpha & edge & (np.array(allowed) > 0)
    array[cut, :3] *= 1 - strength
    return Image.fromarray(array.clip(0, 255).astype(np.uint8), "RGBA")


def align(source: Image.Image, reference: Image.Image, search: int = 24, scales: tuple[float, ...] = ()) -> Image.Image:
    """[source] 실루엣을 [reference] 실루엣에 맞춘다(배율·이동만, IoU 최대). 그림 도구 출력이 몇 px 어긋날 때 쓴다."""
    scales = scales or tuple(round(0.94 + 0.01 * i, 2) for i in range(13))
    ref = np.array(reference.getchannel("A").reduce(4)) > 32
    best = (-1.0, 1.0, 0, 0)
    width, height = source.size
    for scale in scales:
        scaled = source.resize((round(width * scale), round(height * scale)), Image.Resampling.BILINEAR)
        for dy in range(-search, search + 1, 4):
            for dx in range(-search, search + 1, 4):
                canvas = Image.new("RGBA", reference.size, (0, 0, 0, 0))
                ox = round((width - scaled.width) / 2) + dx
                oy = round((height - scaled.height) / 2) + dy
                canvas.paste(scaled, (ox, oy))
                src = np.array(canvas.getchannel("A").reduce(4)) > 32
                union = np.logical_or(src, ref).sum()
                iou = np.logical_and(src, ref).sum() / union if union else 0.0
                if iou > best[0]:
                    best = (iou, scale, ox, oy)
    _, scale, ox, oy = best
    scaled = source.resize((round(width * scale), round(height * scale)), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", reference.size, (0, 0, 0, 0))
    canvas.paste(scaled, (ox, oy))
    return canvas


def align_box(source: Image.Image, reference: Image.Image) -> Image.Image:
    """[source] 불투명 영역 상자를 [reference] 불투명 영역 상자에 가로·세로 따로 맞춘다(팔·다리처럼 그림 도구가 비율을 바꾼 출력)."""
    src_box = source.getchannel("A").point(lambda v: 255 if v > 32 else 0).getbbox()
    ref_box = reference.getchannel("A").point(lambda v: 255 if v > 32 else 0).getbbox()
    if src_box is None or ref_box is None:
        raise ValueError("empty image")
    item = source.crop(src_box).resize((ref_box[2] - ref_box[0], ref_box[3] - ref_box[1]), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", reference.size, (0, 0, 0, 0))
    canvas.paste(item, (ref_box[0], ref_box[1]))
    return canvas


def _skin_like(rgb: np.ndarray) -> np.ndarray:
    """맨몸 피부색(복숭아빛: 색상 5~32도, 채도 0.10~0.48, 밝기 0.62 이상)."""
    r, g, b = (rgb[..., i].astype(np.float32) / 255 for i in range(3))
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    hue = np.where(mx == r, (60 * (g - b) / np.maximum(mx - mn, 1e-6)) % 360, 0)
    return (mx == r) & (hue >= 5) & (hue <= 32) & (sat >= 0.10) & (sat <= 0.48) & (mx >= 0.62)


def _fill_holes(mask: Image.Image) -> Image.Image:
    """바깥과 이어지지 않은 빈 곳(옷 안의 밝은 무늬 등)을 채운다."""
    from PIL import ImageDraw

    padded = Image.new("L", (mask.width + 2, mask.height + 2), 0)
    padded.paste(mask, (1, 1))
    ImageDraw.floodfill(padded, (0, 0), 128)
    outside = np.array(padded)[1:-1, 1:-1] == 128
    return Image.fromarray(np.where(outside, 0, 255).astype(np.uint8), "L")


def extract(
    dressed: Image.Image,
    body: Image.Image | None,
    allowed: Image.Image,
    threshold: int = 42,
    reference: Image.Image | None = None,
    own: Image.Image | None = None,
    skin_area: Image.Image | None = None,
    foreign: Image.Image | None = None,
) -> Image.Image:
    """맨몸 위에 옷을 그린 그림([dressed])에서 옷 픽셀만 남긴다. 허용 영역 밖은 버린다.

    옷 = 불투명하고, 맨몸 피부색이 아니고, 맨몸([body])과 색이 다르거나 맨몸이 비어 있거나 맨몸이 속옷(피부색 아님)인 곳.
    [reference](그림 도구에 준 원래 아틀라스)와 [own](이 부위 칸)을 주면, 겹침 띠(허용 영역 중 이 부위 칸 밖)에서는 원래 그림과 달라진
    픽셀만 옷으로 본다(다른 부위 옷이 겹침 띠로 딸려 오지 않게). [skin_area]를 주면 피부 판정은 그 안(맨몸이 보일 수 있는 곳, 예: 긴소매의
    목·손)에서만 하고 밖의 불투명 픽셀은 모두 옷이다(크림·회색처럼 피부와 비슷한 옷 색). [foreign]은 원래 그림에서 다른 부위 옷이
    있던 픽셀이다: 원래 그림과 같으면(그림 도구가 그대로 둔 다른 부위 옷) 버린다. 그 덩어리를 열기(5px)로 다듬고 빈 곳을
    채운 뒤, 덩어리에서 4px 안의 원래 옷 픽셀(외곽선·끈)을 되살린다. 그래서 맨몸 쪽 외곽선 잔선이 옷 레이어에 남지 않는다.
    """
    a = np.array(dressed.convert("RGBA")).astype(np.int32)
    inside = (a[..., 3] > ALPHA_FLOOR) & (np.array(allowed) > 0)
    raw = inside & ~_skin_like(a[..., :3])
    if body is not None:
        b = np.array(body.convert("RGBA")).astype(np.int32)
        diff = np.abs(a[..., :3] - b[..., :3]).sum(axis=2)
        raw &= (diff > threshold) | (b[..., 3] == 0) | ~_skin_like(b[..., :3])
    if skin_area is not None:
        raw |= inside & ~(np.array(skin_area) > 0)
    if reference is not None and foreign is not None:
        r = np.array(reference.convert("RGBA")).astype(np.int32)
        unchanged = np.abs(a[..., :3] - r[..., :3]).sum(axis=2) <= threshold
        raw &= ~((np.array(foreign) > 0) & unchanged)
    if reference is not None and own is not None:
        r = np.array(reference.convert("RGBA")).astype(np.int32)
        changed = np.abs(a[..., :3] - r[..., :3]).sum(axis=2) > threshold
        raw &= (np.array(own) > 0) | changed
    raw_img = Image.fromarray((raw * 255).astype(np.uint8), "L")
    core = raw_img.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))
    core = _fill_holes(core)
    near = np.array(core.filter(ImageFilter.MaxFilter(9))) > 0
    keep = (np.array(core) > 0) | (raw & near)
    keep &= inside
    out = dressed.convert("RGBA").copy()
    alpha = np.where(keep, np.array(out.getchannel("A")), 0).astype(np.uint8)
    out.putalpha(Image.fromarray(alpha, "L"))
    return clip(out, allowed)


def make_shop(stand: Image.Image) -> Image.Image:
    """그 의상의 여자 서기 레이어 불투명 영역을 잘라 512x512 가운데에 긴 변 440px로(D-성-113)."""
    box = stand.getchannel("A").getbbox()
    if box is None:
        raise ValueError("empty stand layer")
    item = stand.crop(box)
    scale = spec.SHOP_LONG_SIDE / max(item.size)
    item = item.resize(
        (max(1, round(item.width * scale)), max(1, round(item.height * scale))), Image.Resampling.LANCZOS
    )
    canvas = Image.new("RGBA", (spec.SHOP_SIZE, spec.SHOP_SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(item, ((spec.SHOP_SIZE - item.width) // 2, (spec.SHOP_SIZE - item.height) // 2))
    return canvas


def build_bundle(
    source: Path,
    outfits_dir: Path,
    masks_dir: Path,
    asset_key: str,
    male_top_surface: int | None = None,
) -> tuple[dict, list[Problem]]:
    """원본 폴더 [source]의 레이어를 규격에 맞춰 [outfits_dir]/<assetKey>/에 쓴다. index 항목과 검사 결과를 돌려준다."""
    part = spec.part_of_key(asset_key)
    surface = male_top_surface or spec.default_male_top_surface(asset_key)
    target = outfits_dir / asset_key
    target.mkdir(parents=True, exist_ok=True)
    for old in target.glob("*.png"):
        old.unlink()
    overrides = []
    for path in sorted(source.glob("*.png")):
        match = spec.FILE_PATTERN.match(path.name)
        if not match or match.group(2) not in spec.PART_SLOTS[part]:
            continue
        template = template_for(path.name, part, surface)
        slot = spec.SLOTS[spec.TEMPLATES[template].slot]
        layer = to_slot(Image.open(path), slot)
        allowed = load_mask(masks_dir, template, part)
        layer = clip(layer, allowed)
        layer.save(target / path.name, optimize=True)
        if match.group(3):
            overrides.append(path.name.removesuffix(".png"))
    stand = target / "female_stand.png"
    if stand.is_file():
        make_shop(Image.open(stand)).save(target / spec.SHOP_FILE, optimize=True)
    entry: dict = {"assetKey": asset_key, "part": part}
    if part == "TOP":
        entry["maleTopSurface"] = surface
    entry["bodyOverrides"] = overrides
    return entry, check_bundle(outfits_dir, entry, masks_dir)


def update_index(outfits_dir: Path, entry: dict) -> None:
    path = outfits_dir / "index.json"
    index = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {"version": 1, "outfits": []}
    outfits = [e for e in index["outfits"] if e["assetKey"] != entry["assetKey"]] + [entry]
    order = {key: i for i, key in enumerate(k for part in spec.PARTS for k in spec.LEGACY_KEYS[part])}
    outfits.sort(key=lambda e: (order.get(e["assetKey"], len(order)), e["assetKey"]))
    index["outfits"] = outfits
    path.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
