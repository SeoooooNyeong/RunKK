"""원본 → 정렬·잘라내기 → assets/outfits/<assetKey>/ + shop.webp + index.json (5.2, D-성-113, D-성-114).

원본 폴더 하나 = 묶음 하나: `<원본>/<assetKey>/{g}_{slot}{b}.png`(또는 .webp). 결과는 무손실 WebP. 크기가 슬롯의 정수배(예: 2배로 그린 그림)면 줄인다.
`extract`는 몸 전체로 그린 그림(맨몸 위에 옷을 입힌 아틀라스)에서 맨몸과 다른 픽셀만 의상 레이어로 뽑는다.
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageFilter

from outfit_pipeline import spec
from outfit_pipeline.body import (
    bare_hips,
    close_seams,
    close_side_gaps,
    cover_run_leg,
    drop_dust,
    drop_gap_fragments,
    drop_strays,
    elbow_tail_mask,
    extend_waist,
    fill_pinholes,
    meet_torso_outline,
    smooth_sleeve_line,
    square_waist_sides,
    trim_waist,
    wisp_layer,
    wisp_strokes,
)
from outfit_pipeline.check import Problem, check_bundle, hole_area, template_for
from outfit_pipeline.hip import paint_hip
from outfit_pipeline.holes import fill_garment_holes
from outfit_pipeline.masks import load_mask
from outfit_pipeline.shoes import drop_ghost, drop_pieces
from outfit_pipeline.skin import strip_skin

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


MIN_PIECE = 40  # 이보다 작은 옷 조각(떨어진 점·외곽선 부스러기)은 버린다


def _components(mask: np.ndarray) -> list[list[int]]:
    """4방향으로 이은 덩어리(평탄화한 픽셀 번호 목록)."""
    from collections import deque

    _, width = mask.shape
    flat = mask.reshape(-1)
    seen = np.zeros_like(flat)
    pieces = []
    for start in np.flatnonzero(flat):
        if seen[start]:
            continue
        piece = [int(start)]
        seen[start] = True
        queue = deque([int(start)])
        while queue:
            i = queue.popleft()
            x = i % width
            for n in (i - 1 if x > 0 else -1, i + 1 if x < width - 1 else -1, i - width, i + width):
                if 0 <= n < flat.size and flat[n] and not seen[n]:
                    seen[n] = True
                    piece.append(n)
                    queue.append(n)
        pieces.append(piece)
    return pieces


def drop_specks(mask: np.ndarray, minimum: int = MIN_PIECE) -> np.ndarray:
    """4방향으로 이은 덩어리 가운데 [minimum]픽셀 미만을 지운다."""
    from collections import deque

    height, width = mask.shape
    flat = mask.reshape(-1).copy()
    seen = np.zeros_like(flat)
    for start in np.flatnonzero(flat):
        if seen[start]:
            continue
        piece = [start]
        seen[start] = True
        queue = deque([start])
        while queue:
            i = queue.popleft()
            x = i % width
            for n in (i - 1 if x > 0 else -1, i + 1 if x < width - 1 else -1, i - width, i + width):
                if 0 <= n < flat.size and flat[n] and not seen[n]:
                    seen[n] = True
                    piece.append(n)
                    queue.append(n)
        if len(piece) < minimum:
            flat[piece] = False
    return flat.reshape(height, width)


SHIFT_TOLERANCE = (
    2  # 맨몸과 비교할 때 허용하는 어긋남(px). 그림 도구가 1~2px 옮겨 그린 맨몸 외곽선이 옷으로 딸려 오지 않게
)


def _shift_tolerant_diff(a: np.ndarray, b: np.ndarray, radius: int) -> np.ndarray:
    """픽셀마다 [b]를 ±[radius]px 옮긴 것 가운데 가장 가까운 색과의 차이(RGB 합)."""
    height, width = a.shape[:2]
    best = np.full((height, width), 10**6, dtype=np.int64)
    padded = np.pad(b[..., :3], ((radius, radius), (radius, radius), (0, 0)), mode="edge")
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            shifted = padded[radius + dy : radius + dy + height, radius + dx : radius + dx + width]
            best = np.minimum(best, np.abs(a[..., :3] - shifted).sum(axis=2))
    return best


def extract(
    dressed: Image.Image,
    body: Image.Image | None,
    allowed: Image.Image,
    threshold: int = 42,
    reference: Image.Image | None = None,
    own: Image.Image | None = None,
    skin_area: Image.Image | None = None,
    foreign: Image.Image | None = None,
    exact_body: bool = False,
    shift: int = SHIFT_TOLERANCE,
) -> Image.Image:
    """맨몸 위에 옷을 그린 그림([dressed])에서 옷 픽셀만 남긴다. 허용 영역 밖은 버린다.

    옷 = 불투명하고, 맨몸 피부색이 아니고, 맨몸([body])과 색이 다르거나(±2px 어긋남 허용) 맨몸이 비어 있거나 맨몸이 속옷(연회색)인 곳.
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
        diff = _shift_tolerant_diff(a, b, shift)
        if exact_body:
            # 기본 의상(승인 텍스처 그대로): 맨몸 바탕이 승인 피부를 그대로 옮겨 와 같은 자리 비교가 맞다
            exact = np.abs(a[..., :3] - b[..., :3]).sum(axis=2)
            # 맨몸 바탕에 승인 피부·팔 외곽선을 그대로 옮겨 두었으므로 같은 픽셀(외곽선 포함)은 옷이 아니다
            raw &= (exact > threshold) | (b[..., 3] == 0)
        else:
            near_body = (
                np.array(Image.fromarray(((b[..., 3] > 0) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5))) > 0
            )
            rgb = b[..., :3]
            underwear = (b[..., 3] > 0) & ((rgb.max(axis=2) - rgb.min(axis=2)) < 28) & (rgb.min(axis=2) > 150)
            # 옷 = 맨몸과 색이 다르거나(±2px 어긋남 허용), 맨몸이 없거나, 맨몸이 속옷(연회색)인 곳
            raw &= (diff > threshold) | ~near_body | underwear
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
    if exact_body:
        # 승인 기본 텍스처의 팔 외곽선(맨몸 바탕과 같은 그림)이 옷 레이어에 가는 선으로 딸려 오지 않게: 굵은 옷 덩어리에서 4px 밖의 가는 선을 뺀다
        solid = (
            Image.fromarray((keep * 255).astype(np.uint8))
            .filter(ImageFilter.MinFilter(9))
            .filter(ImageFilter.MaxFilter(9))
        )
        keep &= np.array(solid.filter(ImageFilter.MaxFilter(9))) > 0
    keep = drop_specks(keep)
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


def _same_pixels(a: Path, b: Path) -> bool:
    return a.is_file() and b.is_file() and np.array_equal(np.array(Image.open(a)), np.array(Image.open(b)))


def share_genders(folder: Path, names: list[str]) -> dict[str, str]:
    """남녀 같은 내용의 파일은 남자 쪽을 지우고 여자 파일을 함께 쓴다(이름 → 실제 파일 이름)."""
    shared = {}
    for name in names:
        if not name.startswith("male_"):
            continue
        female = "female_" + name.removeprefix("male_")
        male_path, female_path = folder / f"{name}{spec.EXTENSION}", folder / f"{female}{spec.EXTENSION}"
        if _same_pixels(male_path, female_path):
            male_path.unlink()
            shared[name] = female
    return shared


def build_bundle(
    source: Path,
    outfits_dir: Path,
    masks_dir: Path,
    asset_key: str,
    male_top_surface: int | None = None,
) -> tuple[dict, list[Problem]]:
    """원본 폴더 [source]의 레이어를 규격에 맞춰 [outfits_dir]/<assetKey>/에 무손실 WebP로 쓴다. index 항목과 검사 결과를 돌려준다."""
    part = spec.part_of_key(asset_key)
    surface = male_top_surface or spec.default_male_top_surface(asset_key)
    target = outfits_dir / asset_key
    # 새 결과는 임시 폴더에 다 쓴 뒤에 바꿔 넣는다(중간에 실패하면 이전 묶음이 그대로 남는다).
    staging = outfits_dir / f".{asset_key}.building"
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir(parents=True)
    try:
        entry = _write_bundle(source, staging, masks_dir, asset_key, part, surface)
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    _swap(staging, target)
    return entry, check_bundle(outfits_dir, entry, masks_dir)


def _swap(staging: Path, target: Path) -> None:
    """[staging]을 [target]으로 바꿔 넣는다. 그림이 아닌 이전 파일(메모 등)은 옮겨 남긴다."""
    if target.exists():
        for old in target.iterdir():
            if old.suffix not in (".png", spec.EXTENSION) and not (staging / old.name).exists():
                shutil.move(str(old), staging / old.name)
        shutil.rmtree(target)
    staging.rename(target)


def _write_bundle(source: Path, target: Path, masks_dir: Path, asset_key: str, part: str, surface: int) -> dict:
    overrides = []
    names = []
    for path in sorted(source.iterdir()):
        match = spec.SOURCE_PATTERN.match(path.name)
        if not match or match.group(2) not in spec.PART_SLOTS[part]:
            continue
        name = path.name.rsplit(".", 1)[0]
        template = template_for(path.name, part, surface)
        slot = spec.SLOTS[spec.TEMPLATES[template].slot]
        layer, _ = strip_skin(clip(to_slot(Image.open(path), slot), load_mask(masks_dir, template, part)))
        wisps = wisp_layer(asset_key, name)
        layer, _ = drop_strays(layer, garment=wisps, thin=True)
        stand_body = target.parent / spec.BODY_FOLDER / f"female_stand{spec.EXTENSION}"
        if part == "TOP" and match.group(2) == "stand" and stand_body.is_file():
            layer, _ = meet_torso_outline(layer, Image.open(stand_body))  # 판정 round1 A
        if part == "BOTTOM":
            layer = tidy_bottom(layer, target.parent / spec.BODY_FOLDER, wisps, match.group(2) == "stand")
        if part == "SHOES":
            layer, _ = drop_pieces(layer)
        if asset_key in spec.SHOE_GHOST_KEYS:
            # 생성 그림자 잔상(판정 round1 D). 되풀이하면 더 깎이므로 원본에서 만들 때 한 번만 지운다(clean-body에서는 하지 않는다)
            layer, _ = drop_ghost(layer)
            layer, _ = strip_skin(layer)  # 잔상 밖 테두리가 떨어지면 남은 연한 살구색 테두리가 맨살로 드러난다
            layer, _ = drop_strays(layer, thin=True)
            layer, _ = drop_pieces(layer)
        layer = fill_layer_holes(layer, masks_dir, f"{name}{spec.EXTENSION}", part, surface)
        spec.save_webp(layer, target / f"{name}{spec.EXTENSION}")
        names.append(name)
        if match.group(3):
            overrides.append(name)
    stand = target / f"female_stand{spec.EXTENSION}"
    if stand.is_file():
        spec.save_webp(make_shop(Image.open(stand).convert("RGBA")), target / spec.SHOP_FILE)
    entry: dict = {"assetKey": asset_key, "part": part}
    if part == "TOP":
        entry["maleTopSurface"] = surface
    entry["bodyOverrides"] = overrides
    shared = share_genders(target, names)
    if shared:
        entry["shared"] = shared
    return entry


def cut_area(raw_dir: Path, slot_name: str) -> Image.Image:
    """상의 자르기 칸([임시:D-성-156], 값 = 띠 종류): 서기는 상의 칸에서 손 칸과 목·가슴 위, 허리 밴드를 빼고 엉덩이 옆(손 칸 밖)을 더한 곳(팔 띠 둘은 따로 값), 달리기 몸통은 목
    조각을 뺀 전체, 달리기 팔은 전체(소매 끝 아래는 팔 띠)."""
    from outfit_pipeline.masks import stand_zones

    slot = spec.SLOTS[slot_name]
    if slot_name == "stand":
        zones = stand_zones(raw_dir)
        area = ImageChops.subtract(zones["TOP"], zones["HANDS"])
        area.paste(0, spec.NECK_CHEST)
        area.paste(0, spec.NECK_TOP)
        area.paste(0, spec.STAND_WAIST)
        # 엉덩이(하의 칸 위쪽): 하의 밖으로 비치는 맨몸 테두리도 지운다(하의는 옷으로 친다, [임시:D-성-185])
        hips = Image.new("L", area.size, 0)
        for box in spec.STAND_HIP_CUTS:
            hips.paste(spec.CUT_HIP, box)
        area = ImageChops.lighter(area, ImageChops.subtract(hips, zones["HANDS"]))
        array = np.array(area)
        for box, value in (
            (spec.STAND_LEFT_ARM, spec.CUT_LEFT_ARM),
            (spec.STAND_RIGHT_ARM, spec.CUT_RIGHT_ARM),
            (spec.STAND_LEFT_SHOULDER, spec.CUT_LEFT_SHOULDER),
            (spec.STAND_RIGHT_SHOULDER, spec.CUT_RIGHT_SHOULDER),
        ):
            x0, y0, x1, y1 = box
            region = array[y0:y1, x0:x1]
            region[region > 0] = value
        (l0, l1), (r0, r1) = spec.NECK_SIDES
        for (x0, x1), value in (((l0, l1), spec.CUT_LEFT_NECK_SIDE), ((r0, r1), spec.CUT_RIGHT_NECK_SIDE)):
            array[: spec.NECK_TOP[3], x0:x1] = (
                value  # 칼라 옆 칸(어깨를 덮는 상의만 상의 밖을 모두 지운다, 판정 round1~3 B)
            )
        return Image.fromarray(array, "L")
    area = Image.new("L", (slot.width, slot.height), spec.CUT_ALL if slot_name == "run_body" else spec.CUT_TORSO)
    if slot_name == "run_body":
        area.paste(0, spec.RUN_NECK)
    if slot_name == "run_arm":
        area.paste(spec.CUT_LEFT_ARM, spec.RUN_ARM_FOREARM)
    return area


def build_body(source: Path, outfits_dir: Path, raw_dir: Path) -> dict:
    """맨몸 바탕(원본 `<source>/{g}_{slot}.png|webp`)과 상의 자르기 칸을 assets/outfits/body/에 쓰고, 남녀 같은 파일은 함께 쓴다."""
    folder = outfits_dir / spec.BODY_FOLDER
    folder.mkdir(parents=True, exist_ok=True)
    for old in list(folder.glob("*.png")) + list(folder.glob("*.webp")):
        old.unlink()
    names = []
    for gender in spec.GENDERS:
        for slot_name in spec.BODY_SLOTS:
            src = next(
                p for p in (source / f"{gender}_{slot_name}.png", source / f"{gender}_{slot_name}.webp") if p.is_file()
            )
            image, band = clean_body(slot_name, to_slot(Image.open(src), spec.SLOTS[slot_name]))
            if band is not None and gender == "female":
                merge_waistband(outfits_dir, band)
            array = np.array(image)
            array[array[..., 3] == 0] = 0
            spec.save_webp(Image.fromarray(array, "RGBA"), folder / f"{gender}_{slot_name}{spec.EXTENSION}")
            names.append(f"{gender}_{slot_name}")
        for slot_name in spec.CUT_SLOTS:
            area = cut_area(raw_dir, slot_name)
            mask = Image.new("RGBA", area.size, (0, 0, 0, 0))
            mask.putalpha(area)
            spec.save_webp(mask, folder / f"{gender}_{slot_name}_cut{spec.EXTENSION}")
            names.append(f"{gender}_{slot_name}_cut")
    return share_genders(folder, names)


def update_index(outfits_dir: Path, entry: dict | None = None, body_shared: dict[str, str] | None = None) -> None:
    path = outfits_dir / "index.json"
    index = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {"version": 2, "outfits": []}
    index["version"] = 2
    if body_shared is not None:
        index["body"] = {"shared": body_shared}
    if entry is None:
        path.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return
    outfits = [e for e in index["outfits"] if e["assetKey"] != entry["assetKey"]] + [entry]
    order = {key: i for i, key in enumerate(k for part in spec.PARTS for k in spec.LEGACY_KEYS[part])}
    outfits.sort(key=lambda e: (order.get(e["assetKey"], len(order)), e["assetKey"]))
    index["outfits"] = outfits
    path.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def strip_bundles(outfits_dir: Path) -> dict[str, int]:
    """이미 만든 묶음의 옷 레이어에서 드러난 맨살을 지우고 상점 썸네일을 다시 만든다. 파일별 지운 픽셀 수."""
    removed = {}
    for folder in sorted(p for p in outfits_dir.iterdir() if p.is_dir() and p.name != spec.BODY_FOLDER):
        for path in sorted(folder.glob(f"*{spec.EXTENSION}")):
            if path.name == spec.SHOP_FILE:
                continue
            layer, count = strip_skin(Image.open(path))
            if count:
                spec.save_webp(layer, path)
                removed[f"{folder.name}/{path.name}"] = count
        stand = folder / f"female_stand{spec.EXTENSION}"
        if stand.is_file() and any(k.startswith(f"{folder.name}/") for k in removed):
            spec.save_webp(make_shop(Image.open(stand).convert("RGBA")), folder / spec.SHOP_FILE)
    return removed


def clean_body(slot_name: str, image: Image.Image) -> tuple[Image.Image, Image.Image | None]:
    """맨몸 바탕 정리([임시:D-성-184]·[임시:D-성-185], `outfit_pipeline.body`): 부스러기를 지우고, 서기는 소매 밑단 줄을 펴고 허리·엉덩이
    속옷을 맨몸으로 바꾸고 외곽선 밖 나란한 가는 획을 지운다. (맨몸, 서기면 기본 하의로 옮길 허리 밴드 레이어)."""
    # 맨몸 외곽선과 그 안티에일리어싱은 승인 원본 그대로 둔다(검수: 외곽선을 다시 그리거나 3x3로 깎으면 실기기에서 팔이 울퉁불퉁했다).
    # 고립 점만 지우고, 서기는 팔 안쪽 피부의 소매 줄, 팔꿈치 꼬리 조각, 허리·엉덩이 안쪽 속옷, 팔과 몸통 사이 떨어진 조각만 고친다
    if slot_name != "stand":
        # 달리기 슬롯(옆모습)은 3x3 굵은 실루엣 밖 1~2px 털도 지운다: 남기면 긴소매 소매 끝 옆 손목에서 상의 자르기가 빗살을 남겼다
        image, _ = drop_strays(image.convert("RGBA"), thin=True)
        if slot_name == "run_leg":
            image = paint_hip(image)  # 엉덩이 잇기 메시 칸(판정 round1 C)
        return image, None
    image, _ = drop_dust(image.convert("RGBA"))
    from outfit_pipeline.cli import RAW

    array = np.array(smooth_sleeve_line(image, Image.open(RAW / "avatar_legacy_standing_body.png")))
    array[elbow_tail_mask(array)] = 0
    # 외곽선 바깥에 나란히 뜬 가는 획(손 외곽 털, 판정 round1 E)만 지운다. 외곽선 자체는 그대로
    array[wisp_strokes(array)] = 0
    image, band = bare_hips(Image.fromarray(array, "RGBA"))
    image, _ = drop_dust(drop_gap_fragments(image))
    return fill_pinholes(image), band


LONG_LEG_ROW = 500  # 달리기 다리 하의가 이 행 아래까지 내려오면 긴 하의(레깅스·조거·와이드)


def tidy_bottom(layer: Image.Image, body_folder: Path, wisps: bool = False, stand: bool = True) -> Image.Image:
    """하의 레이어 정리([임시:D-성-185]): 세로 틈을 메우고 부스러기를 지우고, 서기면 허리 조각을 지우고 허리를 겹침 띠 위까지 늘린다(맨몸
    바탕이 있으면). 한 단계가 다른 단계의 결과를 바꿀 수 있어(메운 틈 옆 털, 늘린 허리 옆 조각) 더 바뀌지 않을 때까지(최대 6번) 되풀이한다."""
    path = body_folder / f"female_stand{spec.EXTENSION}"
    body = Image.open(path) if stand and path.is_file() else None
    for _ in range(6):
        before = np.array(layer.convert("RGBA"))
        layer, _ = close_seams(layer)
        layer, _ = drop_strays(layer, garment=wisps, thin=True)
        if stand:
            layer, _ = trim_waist(layer)
            if body is not None:
                layer, _ = extend_waist(layer, body)
                layer, _ = square_waist_sides(layer)
                layer, _ = close_side_gaps(layer, body)
        if np.array_equal(before, np.array(layer)):
            break
    # 마지막으로 늘린 허리·메운 틈 옆에 남은 점과 조각을 지운다
    layer, _ = drop_strays(layer, garment=wisps, thin=True)
    if stand:
        layer, _ = trim_waist(layer)
    else:
        # 달리기 엉덩이 잇기 메시가 쓰는 칸(판정 round1 C, outfit_pipeline/hip.py)
        layer = paint_hip(layer)
        leg = body_folder / f"female_run_leg{spec.EXTENSION}"
        # 천이 넓게(40px 이상) 있는 행만 센다(반바지 레이어에 딸려 온 다리 외곽선 가는 줄은 발목까지 내려간다)
        rows = np.nonzero(((np.array(layer.convert("RGBA"))[..., 3] >= 128).sum(axis=1)) >= 40)[0]
        if leg.is_file() and len(rows) and rows.max() >= LONG_LEG_ROW:
            # 긴 하의: 맨몸 다리 피부를 덮고 배경 쪽 가장자리에 외곽선(판정 round1 C)
            layer, _ = cover_run_leg(layer, Image.open(leg))
            layer, _ = close_seams(layer)
            layer, _ = drop_strays(layer, garment=wisps, thin=True)
    return layer


def merge_waistband(outfits_dir: Path, band: Image.Image, masks_dir: Path | None = None) -> None:
    """맨몸에서 뺀 허리 밴드를 기본 하의 서기 레이어 밑에 깐다(기본 의상 모습은 승인 텍스처 그대로). 하의 허용 영역(남녀 템플릿)으로 자른다."""
    path = outfits_dir / spec.LEGACY_KEYS["BOTTOM"][0] / f"female_stand{spec.EXTENSION}"
    if not path.is_file():
        return
    from outfit_pipeline.cli import MASKS

    masks = masks_dir or MASKS
    merged = band.copy()
    merged.alpha_composite(Image.open(path).convert("RGBA"))
    allowed = ImageChops.multiply(
        load_mask(masks, "female_stand", "BOTTOM"), load_mask(masks, spec.stand_template("male", 1), "BOTTOM")
    )
    layer, _ = drop_strays(clip(merged, allowed), thin=True)
    spec.save_webp(layer, path)


def clean_assets(outfits_dir: Path, raw_dir: Path, masks_dir: Path) -> dict[str, int]:
    """이미 만든 자산을 정리한다(`clean-body`): 맨몸 바탕(`clean_body`)과 자르기 칸을 다시 쓰고, 허리 밴드를 기본 하의로 옮기고, 옷
    레이어의 부스러기와 하의 허리 조각을 지운다(상점 그림도 다시). 파일별 바뀐 픽셀 수."""
    changed: dict[str, int] = {}
    folder = outfits_dir / spec.BODY_FOLDER
    for path in sorted(folder.glob(f"*{spec.EXTENSION}")):
        name = path.stem
        if name.endswith("_cut"):
            slot_name = name.split("_", 1)[1].removesuffix("_cut")
            area = cut_area(raw_dir, slot_name)
            mask = Image.new("RGBA", area.size, (0, 0, 0, 0))
            mask.putalpha(area)
            spec.save_webp(mask, path)
            continue
        slot_name = name.split("_", 1)[1]
        before = Image.open(path).convert("RGBA")
        image, band = clean_body(slot_name, before)
        if band is not None:
            merge_waistband(outfits_dir, band, masks_dir)
            changed[f"base_bottom/female_stand{spec.EXTENSION}"] = int(np.array(band)[..., 3].astype(bool).sum())
        changed[f"{folder.name}/{path.name}"] = int((np.array(image) != np.array(before)).any(axis=2).sum())
        spec.save_webp(image, path)
    for bundle in sorted(p for p in outfits_dir.iterdir() if p.is_dir() and p.name != spec.BODY_FOLDER):
        touched = False
        for path in sorted(bundle.glob(f"*{spec.EXTENSION}")):
            if path.name == spec.SHOP_FILE:
                continue
            wisps = wisp_layer(bundle.name, path.name)
            before = np.array(Image.open(path).convert("RGBA"))
            layer, _ = drop_strays(Image.open(path), garment=wisps, thin=True)
            part = spec.part_of_key(bundle.name)
            if part == "BOTTOM":
                layer = tidy_bottom(layer, folder, wisps, path.stem.split("_", 1)[1] == "stand")
            if part == "SHOES":
                layer, _ = drop_pieces(layer)
            stand_body = folder / f"female_stand{spec.EXTENSION}"
            if part == "TOP" and path.stem.split("_", 1)[1] == "stand" and stand_body.is_file():
                layer, _ = meet_torso_outline(layer, Image.open(stand_body))
            surface = spec.default_male_top_surface(bundle.name)
            layer = fill_layer_holes(layer, masks_dir, path.name, part, surface)
            count = int((np.array(layer) != before).any(axis=2).sum())
            if count:
                spec.save_webp(layer, path)
                changed[f"{bundle.name}/{path.name}"] = count
                touched = True
        stand = bundle / f"female_stand{spec.EXTENSION}"
        if stand.is_file() and (touched or bundle.name == spec.LEGACY_KEYS["BOTTOM"][0]):
            spec.save_webp(make_shop(Image.open(stand).convert("RGBA")), bundle / spec.SHOP_FILE)
    return changed


def fill_layer_holes(layer: Image.Image, masks_dir: Path, file_name: str, part: str, surface: int) -> Image.Image:
    """옷 레이어의 구멍을 메우고(`outfit_pipeline.holes`) 메운 픽셀은 16번 검사 영역(`hole_area`, 남녀 템플릿 허용 영역의 교집합)
    안에만 남긴다. 메운 색이 맨살 색(검사 9번)이면 둘레 7x7의 맨살이 아닌 천 색 가운데값으로 바꾼다(후드 안감처럼 살구색 천 옆).
    영역 밖을 되돌리면 새 트임이 생길 수 있어 영역 안 구멍이 없어질 때까지(최대 3번) 되풀이한다. 하의는 메운 뒤 세로 틈(검사 14번)을 닫는다."""
    from outfit_pipeline.holes import hole_mask
    from outfit_pipeline.skin import skin_mask

    area = np.array(hole_area(masks_dir, file_name, part, surface)) > 0
    original = np.array(layer.convert("RGBA"))
    current = layer
    for _ in range(3):
        filled, count = fill_garment_holes(current)
        if not count:
            break
        before = np.array(current.convert("RGBA"))
        array = np.array(filled)
        added = (before[..., 3] < 255) & (array[..., 3] == 255)
        outside = added & ~area
        array[outside] = before[outside]
        skin = skin_mask(array) & added & area
        if skin.any():
            cloth = (array[..., 3] >= 250) & ~skin_mask(array)
            for y, x in zip(*np.nonzero(skin), strict=True):
                y0, y1, x0, x1 = max(0, y - 3), y + 4, max(0, x - 3), x + 4
                near = cloth[y0:y1, x0:x1]
                if near.any():
                    array[y, x, :3] = np.median(array[y0:y1, x0:x1][near][:, :3], axis=0)
        current = Image.fromarray(array, "RGBA")
        if part == "BOTTOM":
            current, _ = close_seams(current)
        if not (hole_mask(np.array(current)) & area).any():
            break
    if np.array_equal(np.array(current.convert("RGBA")), original):
        return layer
    return current
