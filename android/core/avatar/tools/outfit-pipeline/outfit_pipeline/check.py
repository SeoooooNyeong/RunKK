"""규격 검사(5.5 항목, OUTFIT_SPEC.md 6절 열여섯 항목). 같은 검사를 앱 단위 시험 OutfitAssetSpecTest가 빌드 때 다시 한다."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops

from outfit_pipeline import spec
from outfit_pipeline.body import (
    count_strays,
    dust_mask,
    outline_breaks,
    seam_mask,
    side_gap_mask,
    underwear_mask,
    waist_gap,
    waist_tufts_mask,
    wisp_layer,
)
from outfit_pipeline.holes import hole_mask
from outfit_pipeline.masks import load_mask
from outfit_pipeline.skin import count_exposed


@dataclass
class Problem:
    rule: int  # 5.5 번호
    where: str
    message: str

    def __str__(self) -> str:
        return f"[{self.rule}] {self.where}: {self.message}"


def read_index(outfits_dir: Path) -> dict:
    return json.loads((outfits_dir / "index.json").read_text(encoding="utf-8"))


def required_names(part: str) -> list[str]:
    """부위별 필수 레이어 이름(확장자 없이)."""
    return [f"{g}_{slot}" for g in spec.GENDERS for slot in spec.PART_SLOTS[part]]


def resolve(folder: Path, name: str, shared: dict[str, str]) -> Path:
    """레이어 이름 → 실제 파일([shared]로 다른 이름의 파일을 함께 쓰면 그 파일)."""
    return folder / f"{shared.get(name, name)}{spec.EXTENSION}"


def template_for(file_name: str, part: str, male_top_surface: int) -> str:
    gender, rest = file_name.split("_", 1)
    slot = rest.removesuffix(".png").removesuffix(".webp").removesuffix("_b1").removesuffix("_b2")
    if slot == "stand":
        return spec.stand_template(gender, male_top_surface if part == "TOP" else 1)
    return f"{gender}_{slot}"


def hole_area(masks_dir: Path, file_name: str, part: str, male_top_surface: int) -> Image.Image:
    """16번 구멍을 따지는(메우는) 곳: 그 슬롯 남녀 템플릿 허용 영역의 교집합. 남녀가 한 파일을 함께 쓸 수 있어 메운 픽셀이 어느 쪽 허용
    영역도 벗어나지 않게 한다."""
    _, rest = file_name.split("_", 1)
    area = None
    for gender in spec.GENDERS:
        mask = load_mask(masks_dir, template_for(f"{gender}_{rest}", part, male_top_surface), part)
        area = mask if area is None else ImageChops.multiply(area, mask)
    assert area is not None
    if rest.startswith("run_leg"):
        from outfit_pipeline.hip import hip_box

        area = area.copy()
        area.paste(
            0, hip_box()
        )  # 엉덩이 잇기 칸은 등 아래를 일부러 깎는다(판정 round3 C, 다리 메시는 이 칸을 쓰지 않는다)
    return area


def check_layer(
    path: Path,
    part: str,
    template: str,
    masks_dir: Path,
    wisps: bool | None = None,
    holes_in: Image.Image | None = None,
) -> list[Problem]:
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
    # 9번: 옷 레이어에 드러난 맨살이 없다(피부는 맨몸 바탕에서만 와 러닝량 톤이 걸린다, [임시:D-성-174]).
    skin = count_exposed(image)
    if skin:
        problems.append(Problem(9, str(path), f"{skin} exposed skin pixel(s) in garment layer"))
    # 10번: 굵은 실루엣 밖 부스러기(실루엣에서 뻗은 털, 떨어진 점)가 없다. 메시가 늘면 선으로 보인다([임시:D-성-184]).
    garment = wisp_layer(path.parent.name, path.name) if wisps is None else wisps
    strays = count_strays(image, garment=garment, thin=True)
    if strays:
        problems.append(Problem(10, str(path), f"{strays} stray pixel(s) outside the solid silhouette"))
    # 14번: 하의 레이어에 위·아래가 천인 세로 4px 이하 틈이 없다(밑단 안감 사이로 맨몸이 살색 점선으로 보였다, [임시:D-성-185]).
    if part == "BOTTOM" and (seams := int(seam_mask(image).sum())):
        problems.append(Problem(14, str(path), f"{seams} pixel(s) in thin vertical gaps of the garment"))
    # 16번: 옷 실루엣 안에 구멍(둘러싸인 반투명·투명 픽셀, 굵은 천 사이 좁은 트임)이 없다(폴로 단추 자리로 맨몸이 갈색 점으로 비쳤다).
    holes = int((hole_mask(image) & (np.array(allowed if holes_in is None else holes_in) > 0)).sum())
    if holes:
        problems.append(Problem(16, str(path), f"{holes} hole pixel(s) inside the garment"))
    # 12번: 하의 서기 레이어의 허리 겹침 띠에 하의 본체와 떨어진 조각이 없다([임시:D-성-185]).
    if part == "BOTTOM" and spec.TEMPLATES[template].slot == "stand" and (loose := int(waist_tufts_mask(image).sum())):
        problems.append(Problem(12, str(path), f"{loose} loose pixel(s) above the waistband"))
    return problems


def check_shop(path: Path) -> list[Problem]:
    image = Image.open(path)
    if image.size != (spec.SHOP_SIZE, spec.SHOP_SIZE) or image.mode != "RGBA":
        return [
            Problem(5, str(path), f"shop must be {spec.SHOP_SIZE}x{spec.SHOP_SIZE} RGBA, got {image.size} {image.mode}")
        ]
    box = image.getchannel("A").getbbox()
    if box is None:
        return [Problem(5, str(path), "empty shop image")]
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
    shared: dict[str, str] = entry.get("shared", {})
    names = required_names(part) + list(entry.get("bodyOverrides", []))
    for name in names:
        path = resolve(folder, name, shared)
        if not path.is_file():
            rule = 1 if name in required_names(part) else 8
            problems.append(Problem(rule, key, f"missing {name} ({path.name})"))
            continue
        # 함께 쓰는 파일은 그 이름(성별) 템플릿으로도 검사한다(남자 서기는 남자 표면 마스크).
        file_name = f"{name}{spec.EXTENSION}"
        problems += check_layer(
            path,
            part,
            template_for(file_name, part, surface),
            masks_dir,
            holes_in=hole_area(masks_dir, file_name, part, surface),
        )
    for alias, target in shared.items():
        if not spec.FILE_PATTERN.match(f"{alias}{spec.EXTENSION}") or (folder / f"{alias}{spec.EXTENSION}").is_file():
            problems.append(Problem(6, key, f"shared entry {alias} -> {target} must name a missing file"))
    # 13번: 하의 서기 허리가 겹침 띠 위(y 296)까지 맨몸 실루엣을 덮는다(상의 밑단 밑으로 들어간다, [임시:D-성-158]·[임시:D-성-185]).
    stand, body = (
        resolve(folder, "female_stand", shared),
        outfits_dir / spec.BODY_FOLDER / f"female_stand{spec.EXTENSION}",
    )
    if (
        part == "BOTTOM"
        and stand.is_file()
        and body.is_file()
        and (gap := waist_gap(Image.open(stand), Image.open(body)))
    ):
        problems.append(Problem(13, str(stand), f"{gap} body pixel(s) uncovered between y 296 and the waistband"))
    # 15번: 하의 서기 엉덩이 옆 칸에 맨몸 실루엣 안 트임(7px로 닫으면 메워지는 빈 곳)이 없다(상의 자르기가 그 밑 맨몸을 지워 배경이 비쳤다).
    if part == "BOTTOM" and stand.is_file() and body.is_file():
        side = int(side_gap_mask(Image.open(stand), Image.open(body)).sum())
        if side:
            problems.append(Problem(15, str(stand), f"{side} body pixel(s) in side slits of the bottom"))
    if not (folder / spec.SHOP_FILE).is_file():
        problems.append(Problem(1, key, f"missing {spec.SHOP_FILE}"))
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
    problems += check_body(outfits_dir, index.get("body", {}).get("shared", {}))
    return problems


def check_body(outfits_dir: Path, shared: dict[str, str] | None = None) -> list[Problem]:
    """맨몸 바탕 8장과 상의 자르기 칸 6장(5.3, [임시:D-성-156]): 크기·RGBA."""
    shared = shared or {}
    problems = []
    folder = outfits_dir / spec.BODY_FOLDER
    for gender in spec.GENDERS:
        names = [(f"{gender}_{s}", s) for s in spec.BODY_SLOTS] + [(f"{gender}_{s}_cut", s) for s in spec.CUT_SLOTS]
        for name, slot_name in names:
            path = resolve(folder, name, shared)
            slot = spec.SLOTS[slot_name]
            if not path.is_file():
                problems.append(Problem(1, str(path), f"missing body file {name}"))
                continue
            image = Image.open(path)
            # 자르기 칸이 슬롯 전체면(달리기 팔) WebP에 알파가 없어 RGB로 읽힌다(전체 불투명 = 전체가 칸).
            modes = ("RGBA", "RGB") if name.endswith("_cut") else ("RGBA",)
            if image.size != (slot.width, slot.height) or image.mode not in modes:
                problems.append(Problem(2, str(path), f"body file must be {slot.width}x{slot.height} {modes}"))
                continue
            if name.endswith("_cut"):
                continue
            # 맨몸은 외곽선에 붙은 픽셀을 건드리지 않고 고립 점만 본다(검수: 외곽선을 깎으면 팔이 울퉁불퉁했다)
            dust = int(dust_mask(image).sum())
            if dust:
                problems.append(Problem(10, str(path), f"{dust} isolated dust pixel(s) in the body"))
            reference = spec.outline_reference_path(name)
            if reference.is_file():
                breaks = int(outline_breaks(image, np.array(Image.open(reference).convert("L")) > 0).sum())
                # 17번: 맨몸 외곽선이 승인 원본(정리 전)과 같이 이어진다
                if breaks:
                    problems.append(Problem(17, str(path), f"{breaks} body outline pixel(s) broken"))
            # 11번: 서기 맨몸의 허리·엉덩이 칸에 속옷(무채색) 픽셀이 없다. 하의 밖으로 비치고 피부톤이 걸려 짙은 띠가 됐다([임시:D-성-185]).
            if slot_name == "stand" and (under := int(underwear_mask(image).sum())):
                problems.append(Problem(11, str(path), f"{under} underwear pixel(s) in the waist and hips"))
    return problems
