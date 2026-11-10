"""규격 검사(5.5 여덟 항목). 같은 검사를 앱 단위 시험 OutfitAssetSpecTest가 빌드 때 다시 한다."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageChops

from outfit_pipeline import spec
from outfit_pipeline.masks import load_mask


@dataclass
class Problem:
    rule: int  # 5.5 번호
    where: str
    message: str

    def __str__(self) -> str:
        return f"[{self.rule}] {self.where}: {self.message}"


def read_index(outfits_dir: Path) -> dict:
    return json.loads((outfits_dir / "index.json").read_text(encoding="utf-8"))


def required_files(part: str) -> list[str]:
    return [f"{g}_{slot}.png" for g in spec.GENDERS for slot in spec.PART_SLOTS[part]] + [spec.SHOP_FILE]


def template_for(file_name: str, part: str, male_top_surface: int) -> str:
    gender, rest = file_name.split("_", 1)
    slot = rest.removesuffix(".png").removesuffix("_b1").removesuffix("_b2")
    if slot == "stand":
        return spec.stand_template(gender, male_top_surface if part == "TOP" else 1)
    return f"{gender}_{slot}"


def check_layer(path: Path, part: str, template: str, masks_dir: Path) -> list[Problem]:
    problems: list[Problem] = []
    slot = spec.SLOTS[spec.TEMPLATES[template].slot]
    image = Image.open(path)
    if image.size != (slot.width, slot.height):
        problems.append(Problem(2, str(path), f"size {image.size} != {(slot.width, slot.height)}"))
        return problems
    if image.mode != "RGBA":
        problems.append(Problem(2, str(path), f"mode {image.mode} != RGBA"))
        return problems
    alpha = image.getchannel("A").point(lambda v: 255 if v else 0)
    allowed = load_mask(masks_dir, template, part)
    uv = load_mask(masks_dir, template)
    # 3번: 그 부위가 아닌 아틀라스 칸(금지 영역)에서 알파 0.
    forbidden = ImageChops.multiply(ImageChops.invert(allowed), uv)
    bad = ImageChops.multiply(alpha, forbidden).getbbox()
    if bad:
        problems.append(Problem(3, str(path), f"alpha in forbidden area at {bad}"))
    # 4번: 알파가 슬롯 UV 허용 영역 안에만, 1% 이상.
    outside = ImageChops.multiply(alpha, ImageChops.invert(uv)).getbbox()
    if outside:
        problems.append(Problem(4, str(path), f"alpha outside UV area at {outside}"))
    coverage = sum(alpha.histogram()[1:]) / (slot.width * slot.height)
    if coverage < spec.MIN_COVERAGE:
        problems.append(Problem(4, str(path), f"coverage {coverage:.4f} < {spec.MIN_COVERAGE}"))
    return problems


def check_shop(path: Path) -> list[Problem]:
    image = Image.open(path)
    if image.size != (spec.SHOP_SIZE, spec.SHOP_SIZE) or image.mode != "RGBA":
        return [
            Problem(
                5, str(path), f"shop.png must be {spec.SHOP_SIZE}x{spec.SHOP_SIZE} RGBA, got {image.size} {image.mode}"
            )
        ]
    box = image.getchannel("A").getbbox()
    if box is None:
        return [Problem(5, str(path), "empty shop.png")]
    width, height = box[2] - box[0], box[3] - box[1]
    problems = []
    low, high = spec.SHOP_LONG_SIDE_RANGE
    if not low <= max(width, height) <= high:
        problems.append(Problem(5, str(path), f"long side {max(width, height)} not in {low}..{high}"))
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    if (
        abs(cx - spec.SHOP_SIZE / 2) > spec.SHOP_CENTER_TOLERANCE
        or abs(cy - spec.SHOP_SIZE / 2) > spec.SHOP_CENTER_TOLERANCE
    ):
        problems.append(Problem(5, str(path), f"center ({cx}, {cy}) off by more than {spec.SHOP_CENTER_TOLERANCE}px"))
    return problems


def check_bundle(outfits_dir: Path, entry: dict, masks_dir: Path) -> list[Problem]:
    key = entry["assetKey"]
    folder = outfits_dir / key
    part = entry["part"]
    problems: list[Problem] = []
    if part not in spec.PARTS:
        return [Problem(1, key, f"unknown part {part}")]
    surface = int(entry.get("maleTopSurface", 1))
    if part == "TOP" and entry.get("maleTopSurface") not in (1, 2):
        problems.append(Problem(8, key, "TOP needs maleTopSurface 1 or 2"))
    for name in required_files(part):
        if not (folder / name).is_file():
            problems.append(Problem(1, key, f"missing {name}"))
    for override in entry.get("bodyOverrides", []):
        if not (folder / f"{override}.png").is_file():
            problems.append(Problem(8, key, f"bodyOverrides file missing: {override}.png"))
    total = 0
    allowed_slots = set(spec.PART_SLOTS[part])
    for path in sorted(folder.iterdir()):
        total += path.stat().st_size
        if path.name == spec.SHOP_FILE:
            problems += check_shop(path)
            continue
        match = spec.FILE_PATTERN.match(path.name)
        if not match or match.group(2) not in allowed_slots:
            problems.append(Problem(6, str(path), "file name outside the rule"))
            continue
        problems += check_layer(path, part, template_for(path.name, part, surface), masks_dir)
    if total > spec.MAX_BUNDLE_BYTES:
        problems.append(Problem(7, key, f"bundle {total} bytes > {spec.MAX_BUNDLE_BYTES}"))
    return problems


def check_all(outfits_dir: Path, masks_dir: Path) -> list[Problem]:
    index = read_index(outfits_dir)
    problems: list[Problem] = []
    keys = [e["assetKey"] for e in index["outfits"]]
    if len(set(keys)) != len(keys):
        problems.append(Problem(1, "index.json", "duplicate assetKey"))
    folders = {p.name for p in outfits_dir.iterdir() if p.is_dir() and p.name != spec.BODY_FOLDER}
    for extra in sorted(folders - set(keys)):
        problems.append(Problem(1, extra, "folder not listed in index.json"))
    for entry in index["outfits"]:
        if not (outfits_dir / entry["assetKey"]).is_dir():
            problems.append(Problem(1, entry["assetKey"], "listed folder missing"))
            continue
        problems += check_bundle(outfits_dir, entry, masks_dir)
    problems += check_body(outfits_dir)
    return problems


def check_body(outfits_dir: Path) -> list[Problem]:
    """맨몸 바탕 8장(5.3): 크기·RGBA."""
    problems = []
    for gender in spec.GENDERS:
        for slot_name in spec.BODY_SLOTS:
            path = outfits_dir / spec.BODY_FOLDER / f"{gender}_{slot_name}.png"
            slot = spec.SLOTS[slot_name]
            if not path.is_file():
                problems.append(Problem(1, str(path), "missing body base"))
                continue
            image = Image.open(path)
            if image.size != (slot.width, slot.height) or image.mode != "RGBA":
                problems.append(Problem(2, str(path), f"body base must be {slot.width}x{slot.height} RGBA"))
    return problems
