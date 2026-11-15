"""기본 머리 그림(core/ui drawable avatar_default_head_{male,female}.webp, 512×512)을 만든다(D-821).

core/avatar 내장 기본 머리 정면 층(assets/avatars/{male,female}/face·eyes_open·hair.png, 1024 공통 캔버스)을 겹쳐
프로필 원 목표 구도(앱 PortraitHeadFrame.TARGET과 같다)에 놓는다.

- 머리 크기: 정수리(헤어 알파 128 이상 위 끝)~턱이 상자의 91%, 목 가운데는 50%. 머리 너비가 상자의 약 90%다.
- 원 안(avatar_default_head_*): 목은 늘리지 않고 제 길이 그대로 두고 머리를 내려, 목이 둥글게 끝나기 시작하는 줄(목 폭이 최대 폭의
  97% 이상인 마지막 줄 다음)이 상자 바닥에 오게 한다(사용자 요청 2026-11-12). 정수리는 약 2~3%이고 목 끝 외곽선은 상자 밖이다.
- 원 밖(avatar_default_head_*_free, D-825): 정수리 -5%, 턱 86% 자리에 목은 제 길이로 둥글게 끝난다(늘리지 않는다).
- 피부 층(avatar_default_head_*_skin, S15P31A207-300): 같은 구도의 얼굴(피부) 층에서 눈·헤어가 덮는 만큼 알파를 뺀 그림이다. 앱은
  기본 머리를 그린 뒤 그을림이 있으면 이 층만 거르개로 덧그려 피부만 어둡게 한다(셀카 머리가 얼굴 층에만 거르개를 씌우는 것과 같다).

실행: python make_default_heads.py (저장소 android/ 기준 경로를 쓴다, Pillow 필요)
"""
from pathlib import Path

from PIL import Image, ImageChops

ANDROID = Path(__file__).resolve().parents[4]
ASSETS = ANDROID / "core/avatar/src/main/assets/avatars"
OUT = ANDROID / "core/ui/src/main/res/drawable-nodpi"

SIZE = 512
CROWN_Y, CHIN_Y, CENTER_X = -0.05, 0.86, 0.5
# 원 안에서 머리를 내릴 때 정수리 범위(앱 PortraitHeadFrame.CROWN_MIN·CROWN_MAX와 같다).
CROWN_MIN, CROWN_MAX = -0.05, 0.08
NECK_END_RATIO = 0.97
# 내장 머리의 턱·목 가운데 x(core/avatar HeadTarget 정면 값, 내장 머리가 그 자리에 맞춰져 있다).
CHIN = {"male": (520, 854), "female": (534, 787)}


def crown_of(hair: Image.Image) -> int:
    alpha = hair.getchannel("A").point(lambda a: 255 if a >= 128 else 0)
    return alpha.getbbox()[1]


def neck_bottom(face: Image.Image) -> int:
    return face.getchannel("A").point(lambda a: 255 if a >= 200 else 0).getbbox()[3]


def neck_width(face: Image.Image, y: int) -> int:
    """y줄의 불투명(알파 200 이상) 가장 넓은 연속 구간 폭."""
    alpha = face.getchannel("A")
    best = run = 0
    for x in range(face.width):
        run = run + 1 if alpha.getpixel((x, y)) >= 200 else 0
        best = max(best, run)
    return best


def neck_end(face: Image.Image, chin: int, bottom: int) -> int:
    """턱~목 끝 사이에서 목 폭이 최대 폭의 97% 이상인 마지막 줄 다음(목이 둥글게 끝나기 시작하는 줄). 앱 PortraitHeadFrame과 같다."""
    widths = {y: neck_width(face, y) for y in range(chin, bottom)}
    widest = max(widths.values())
    return max(y for y, w in widths.items() if w >= widest * NECK_END_RATIO) + 1


def make(gender: str, in_circle: bool = True, skin_only: bool = False) -> Image.Image:
    face, eyes, hair = (Image.open(ASSETS / gender / f"{n}.png").convert("RGBA") for n in ("face", "eyes_open", "hair"))
    center_x, chin = CHIN[gender]
    crown = crown_of(hair)
    scale = SIZE * (CHIN_Y - CROWN_Y) / (chin - crown)
    dx = SIZE * CENTER_X - center_x * scale
    bottom = neck_bottom(face)
    if in_circle:
        # 목 끝이 상자 바닥에 오게 머리를 내린다(정수리 범위 안에서). 목은 늘리지 않는다.
        end = neck_end(face, chin, bottom)
        dy = SIZE - end * scale
        dy = min(max(dy, SIZE * CROWN_MIN - crown * scale), SIZE * CROWN_MAX - crown * scale)
        print(gender, "neck end", end, "crown %.3f" % ((crown * scale + dy) / SIZE), "chin %.3f" % ((chin * scale + dy) / SIZE))
    else:
        dy = SIZE * CHIN_Y - chin * scale
    canvas = Image.new("RGBA", face.size, (0, 0, 0, 0))
    if skin_only:
        # 보이는 피부만: 얼굴 층 알파 × (1 - 눈·헤어 알파)
        over = Image.new("RGBA", face.size, (0, 0, 0, 0))
        over.alpha_composite(eyes, (0, 0))
        over.alpha_composite(hair, (0, 0))
        uncovered = over.getchannel("A").point(lambda a: 255 - a)
        skin = face.copy()
        skin.putalpha(ImageChops.multiply(face.getchannel("A"), uncovered))
        canvas.alpha_composite(skin, (0, 0))
    else:
        canvas.alpha_composite(face, (0, 0))
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
        for in_circle, suffix in ((True, ""), (False, "_free")):
            out = OUT / f"avatar_default_head_{g}{suffix}.webp"
            make(g, in_circle).save(out, "WEBP", quality=92, method=6)
            print(out.name, out.stat().st_size)
            skin = OUT / f"avatar_default_head_{g}{suffix}_skin.webp"
            make(g, in_circle, skin_only=True).save(skin, "WEBP", quality=92, method=6)
            print(skin.name, skin.stat().st_size)
