"""상의 자르기 일치 시험 픽스처(앱 TopBodyCut ↔ 파이프라인 cut.py). 다시 만들 때: uv run python tests/make_topcut_fixture.py"""

from pathlib import Path

from PIL import Image

from outfit_pipeline import spec
from outfit_pipeline.cut import apply

OUT = Path(__file__).resolve().parents[3] / "src/test/resources/topcut"
SKIN = (250, 220, 200, 255)


def inputs() -> tuple[Image.Image, Image.Image, Image.Image]:
    body = Image.new("RGBA", (48, 64), (0, 0, 0, 0))
    body.paste(SKIN, (4, 4, 44, 60))  # 몸통·팔·달리기 몸통 칸을 덮는 맨몸
    top = Image.new("RGBA", (48, 64), (0, 0, 0, 0))
    top.paste((40, 90, 200, 255), (8, 8, 40, 30))  # 맨몸보다 좌우·위로 4px 좁은 상의(팔 띠 일부만 덮음)
    area = Image.new("RGBA", (48, 64), (0, 0, 0, 0))
    labels = Image.new("L", (48, 64), 0)
    labels.paste(spec.CUT_TORSO, (0, 0, 48, 24))  # 몸통
    labels.paste(spec.CUT_LEFT_ARM, (0, 24, 48, 40))  # 팔 띠: 상의가 덮는 몫이 60% 이하라 자르지 않는다
    labels.paste(spec.CUT_ALL, (0, 40, 48, 64))  # 모두 지우는 칸
    area.putalpha(labels)
    return body, top, area


def expected() -> Image.Image:
    body, top, area = inputs()
    return apply(body, top, area)


def neck_inputs() -> tuple[Image.Image, Image.Image, Image.Image]:
    """칼라 옆 칸(221·181): 짝 어깨 띠(220·180)를 상의가 60% 넘게 덮는 왼쪽은 상의 밖을 모두 지우고, 덮지 않는 오른쪽은 띠 판정만 한다."""
    body = Image.new("RGBA", (48, 32), (0, 0, 0, 0))
    body.paste(SKIN, (2, 2, 46, 30))
    top = Image.new("RGBA", (48, 32), (0, 0, 0, 0))
    top.paste((40, 90, 200, 255), (2, 12, 22, 30))  # 왼쪽 어깨 띠(y 12~29)를 모두 덮는다
    top.paste((40, 90, 200, 255), (30, 26, 46, 30))  # 오른쪽 어깨 띠는 조금만
    labels = Image.new("L", (48, 32), 0)
    labels.paste(spec.CUT_LEFT_NECK_SIDE, (0, 0, 24, 12))
    labels.paste(spec.CUT_RIGHT_NECK_SIDE, (24, 0, 48, 12))
    labels.paste(spec.CUT_LEFT_SHOULDER, (0, 12, 24, 32))
    labels.paste(spec.CUT_RIGHT_SHOULDER, (24, 12, 48, 32))
    area = Image.new("RGBA", (48, 32), (0, 0, 0, 0))
    area.putalpha(labels)
    return body, top, area


REAL_TOPS = ("top_dawn_windbreaker", "top_crew_tee")  # 승인 묶음의 실제 서기 조합(칼라 옆 칸을 쓰는 긴소매, 반팔)


def real_inputs(key: str) -> tuple[Image.Image, Image.Image, Image.Image]:
    from outfit_pipeline.cli import OUTFITS

    body = Image.open(OUTFITS / spec.BODY_FOLDER / f"female_stand{spec.EXTENSION}").convert("RGBA")
    top = Image.open(OUTFITS / key / f"female_stand{spec.EXTENSION}").convert("RGBA")
    area = Image.open(OUTFITS / spec.BODY_FOLDER / f"female_stand_cut{spec.EXTENSION}").convert("RGBA")
    return body, top, area


def sets() -> dict[str, tuple[Image.Image, Image.Image, Image.Image]]:
    """픽스처 이름 앞붙임 → (맨몸, 상의, 칸). 앞붙임 ""은 처음 픽스처(body.png 등)."""
    out = {"": inputs(), "neck_": neck_inputs()}
    out |= {f"real_{key}_": real_inputs(key) for key in REAL_TOPS}
    return out


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for prefix, images in sets().items():
        for name, image in zip(("body", "top", "area"), images, strict=True):
            image.save(OUT / f"{prefix}{name}.png")
        apply(*images).save(OUT / f"{prefix}expected.png")
    print(OUT)
