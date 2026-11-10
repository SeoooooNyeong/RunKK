"""`templates`: 슬롯 템플릿 11장(5.3). 세 층 = (1) 맨몸 바탕 (2) 허용 영역(하늘색) (3) 부위 경계 선과 금지 영역(빨간 빗금).

디자이너는 이 PNG를 밑그림 레이어로 깔고 같은 크기·좌표로 옷만 그린다. 신발 슬롯은 맨몸이 없어 기본 신발을 옅게 깐다.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw

from outfit_pipeline import spec
from outfit_pipeline.masks import allowed, scene, stand_zones, uv_mask

ZONE_COLORS = {"TOP": (40, 110, 255), "BOTTOM": (20, 170, 80), "SHOES": (150, 70, 220), "HANDS": (240, 150, 0)}


def _base(outfits_dir: Path, raw_dir: Path, template: spec.Template) -> Image.Image:
    slot = spec.SLOTS[template.slot]
    path = outfits_dir / spec.BODY_FOLDER / f"{template.gender}_{template.slot}.png"
    if path.is_file():
        return Image.open(path).convert("RGBA")
    embedded = scene(raw_dir, template.riv).asset_bytes.get(slot.asset)
    if not embedded:
        return Image.new("RGBA", (slot.width, slot.height), (0, 0, 0, 0))
    import io

    guide = Image.open(io.BytesIO(embedded)).convert("RGBA")
    guide.putalpha(guide.getchannel("A").point(lambda v: v * 35 // 100))
    return guide


def _edges(mask: Image.Image) -> np.ndarray:
    a = np.array(mask) > 0
    edge = np.zeros_like(a)
    edge[1:, :] |= a[1:, :] != a[:-1, :]
    edge[:, 1:] |= a[:, 1:] != a[:, :-1]
    return edge


def render(outfits_dir: Path, raw_dir: Path, template: spec.Template) -> Image.Image:
    slot = spec.SLOTS[template.slot]
    canvas = Image.new("RGBA", (slot.width, slot.height), (255, 255, 255, 255))
    canvas.alpha_composite(_base(outfits_dir, raw_dir, template))
    uv = uv_mask(raw_dir, template)
    tint = Image.new("RGBA", canvas.size, (120, 200, 255, 0))
    tint.putalpha(uv.point(lambda v: 46 if v else 0))
    canvas.alpha_composite(tint)
    parts = spec.PARTS if template.slot == "stand" else (spec.RUN_SLOT_PART[template.slot],)
    any_allowed = Image.new("L", canvas.size, 0)
    for part in parts:
        any_allowed = ImageChops.lighter(any_allowed, allowed(raw_dir, template, part))
    # 금지 영역: 어느 부위도 그릴 수 없는 곳(UV 밖, 손 칸 등). 빨간 빗금.
    forbidden = np.array(any_allowed) == 0
    yy, xx = np.mgrid[0 : slot.height, 0 : slot.width]
    hatch = forbidden & (((xx + yy) % 12) < 2)
    pixels = np.array(canvas)
    pixels[hatch] = (230, 40, 40, 255)
    if template.slot == "stand":
        for zone, mask in stand_zones(raw_dir).items():
            pixels[_edges(ImageChops.multiply(mask, uv))] = (*ZONE_COLORS[zone], 255)
        for part in spec.PARTS:
            edge = _edges(allowed(raw_dir, template, part))
            dashed = edge & (((xx // 3) + (yy // 3)) % 2 == 0)
            pixels[dashed] = (*ZONE_COLORS[part], 255)
    out = Image.fromarray(pixels, "RGBA")
    draw = ImageDraw.Draw(out)
    draw.text((4, slot.height - 12), template.name, fill=(30, 30, 30, 255))
    return out


def write_templates(outfits_dir: Path, raw_dir: Path, out_dir: Path) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    for template in spec.TEMPLATES.values():
        path = out_dir / f"{template.name}.png"
        render(outfits_dir, raw_dir, template).save(path, optimize=True)
        written.append(path)
    return written
