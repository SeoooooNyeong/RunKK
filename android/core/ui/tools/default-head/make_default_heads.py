"""기본 머리 그림(core/ui drawable avatar_default_head_{male,female}.webp, 512×512)을 만든다([임시:D-성-189]).

core/avatar 내장 기본 머리 정면 층(assets/avatars/{male,female}/face·eyes_open·hair.png, 1024 공통 캔버스)을 겹쳐
프로필 원 목표 구도(앱 PortraitHeadFrame.TARGET과 같다)에 놓는다.

- 정수리(헤어 알파 128 이상 위 끝)는 상자 위 끝에서 -5%(살짝 잘림), 턱은 86%, 목 가운데는 50%. 머리 너비가 상자의 약 90%다.
- 목은 턱과 목 끝 사이 가운데 줄을 상자 바닥까지 목 폭 그대로 늘린다. 그 아래 얼굴 층(목 끝 외곽선)은 그리지 않는다.

실행: python make_default_heads.py (저장소 android/ 기준 경로를 쓴다, Pillow 필요)
"""
from pathlib import Path

from PIL import Image

ANDROID = Path(__file__).resolve().parents[4]
ASSETS = ANDROID / "core/avatar/src/main/assets/avatars"
OUT = ANDROID / "core/ui/src/main/res/drawable-nodpi"

SIZE = 512
CROWN_Y, CHIN_Y, CENTER_X = -0.05, 0.86, 0.5
# 내장 머리의 턱·목 가운데 x(core/avatar HeadTarget 정면 값, 내장 머리가 그 자리에 맞춰져 있다).
CHIN = {"male": (520, 854), "female": (534, 787)}


def crown_of(hair: Image.Image) -> int:
    alpha = hair.getchannel("A").point(lambda a: 255 if a >= 128 else 0)
    return alpha.getbbox()[1]


def neck_bottom(face: Image.Image) -> int:
    return face.getchannel("A").point(lambda a: 255 if a >= 200 else 0).getbbox()[3]


def make(gender: str) -> Image.Image:
    face, eyes, hair = (Image.open(ASSETS / gender / f"{n}.png").convert("RGBA") for n in ("face", "eyes_open", "hair"))
    center_x, chin = CHIN[gender]
    crown = crown_of(hair)
    scale = SIZE * (CHIN_Y - CROWN_Y) / (chin - crown)
    dx = SIZE * CENTER_X - center_x * scale
    dy = SIZE * CHIN_Y - chin * scale
    bottom = neck_bottom(face)
    row = round(chin + (bottom - chin) / 2)
    # 상자 바닥까지 닿게 캔버스를 아래로 늘린 원본 좌표 높이.
    tall = max(face.height, int((SIZE - dy) / scale) + 2)
    canvas = Image.new("RGBA", (face.width, tall), (0, 0, 0, 0))
    canvas.alpha_composite(face.crop((0, 0, face.width, row)), (0, 0))
    strip = face.crop((0, row, face.width, row + 1)).resize((face.width, tall - row), Image.NEAREST)
    canvas.alpha_composite(strip, (0, row))
    canvas.alpha_composite(eyes, (0, 0))
    canvas.alpha_composite(hair, (0, 0))
    # 출력 (u, v) ← 원본 ((u - dx) / scale, (v - dy) / scale)
    big = canvas.transform(
        (SIZE * 2, SIZE * 2),
        Image.AFFINE,
        (1 / scale / 2, 0, -dx / scale, 0, 1 / scale / 2, -dy / scale),
        resample=Image.BICUBIC,
    )
    return big.resize((SIZE, SIZE), Image.LANCZOS)


if __name__ == "__main__":
    for g in ("male", "female"):
        make(g).save(OUT / f"avatar_default_head_{g}.webp", "WEBP", quality=92, method=6)
        print(g, (OUT / f"avatar_default_head_{g}.webp").stat().st_size)
