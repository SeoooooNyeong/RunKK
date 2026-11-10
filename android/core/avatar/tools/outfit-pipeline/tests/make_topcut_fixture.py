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


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, image in zip(("body", "top", "area"), inputs(), strict=True):
        image.save(OUT / f"{name}.png")
    expected().save(OUT / "expected.png")
    print(OUT)
