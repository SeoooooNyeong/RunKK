"""남자 서기 긴소매 메시의 팔·몸통 이음매 닫기(판정 round3 소매 이음새 하늘색 틈).

승인 남자 서기 riv(avatar_male_idle)의 Image 「Long sleeve wardrobe top」(긴소매 표면 2)은 아틀라스를 세로 띠로 나눈 메시다. 팔 띠(u 0~약
0.26, 0.74~1)와 몸통 띠는 이음매 열에 같은 (u, v)·같은 무게의 꼭짓점을 따로 둔다(띠마다 하나). 위쪽 행(v 0.25까지)은 두 꼭짓점이 같은
자리라 이어져 있지만, 겨드랑이 아래(v 0.26~0.35, 아틀라스 y 200~272)는 몸통 쪽 꼭짓점이 0.1~0.8 단위(1배 화면 약 2px) 안쪽에 있어 두 띠
사이가 벌어졌다. 체형 애니메이션(「Body shape base·medium·fuller」)도 꼭짓점 x를 따로 키로 넣어 체형 100에서는 다른 행이 벌어졌다. 그 틈으로
배경이 소매와 몸통 사이 가는 세로 줄로 비쳤다(긴소매 상의 4벌, 남자 서기만).

그래서 몸통 쪽 꼭짓점의 x·y(바탕 자세)와 세 체형 애니메이션의 x 키 값을 짝인 팔 쪽 꼭짓점 값으로 맞춘다. 무게가 같아 뼈가 움직여도 함께
움직이므로 어느 프레임·체형에서도 틈이 없다. 무게가 다른 짝(소매 끝 아래, 팔과 몸통이 실제로 떨어진 곳)은 그대로 둔다. riv 파일은
승인본(SHA 시험) 그대로 두고, 앱은 읽은 바이트에서 그 float32만 옮겨 쓴다(RivSleeveSeam, [copy_pairs]로 만든 표).
"""

from __future__ import annotations

import base64
import re
import struct

from outfit_pipeline.riv import (
    ANIMATION_NAME,
    KEYED_OBJECT_ID,
    KEYED_PROPERTY_KEY,
    KEYFRAME_VALUE,
    NAME,
    PARENT_ID,
    TRIANGLE_INDEX_BYTES,
    VERTEX_U,
    VERTEX_V,
    _varuint,
)
from outfit_pipeline.riv_schema import PROPERTY_FORMAT, TYPE_NAME

IMAGE_NAME = "Long sleeve wardrobe top"
SHAPE_ANIMATIONS = ("Body shape base", "Body shape medium", "Body shape fuller")
VERTEX_X = 24
VERTEX_Y = 25
SAME_UV = 0.002  # 이음매 짝으로 보는 u 차 상한(같은 v)
_TOC_FORMAT = ("u", "s", "f", "c")


def _objects(data: bytes) -> list[tuple[str, dict[int, tuple[object, int]]]]:
    """riv 객체 목록: (형식 이름, 속성 키 → (값, 값의 바이트 위치))."""
    if data[:4] != b"RIVE":
        raise ValueError("not a riv file")
    at = 4
    for _ in range(3):
        _, at = _varuint(data, at)
    toc: list[int] = []
    while True:
        key, at = _varuint(data, at)
        if key == 0:
            break
        toc.append(key)
    toc_format: dict[int, str] = {}
    bits, packed = 8, 0
    for key in toc:
        if bits == 8:
            packed = struct.unpack_from("<I", data, at)[0]
            at += 4
            bits = 0
        toc_format[key] = _TOC_FORMAT[(packed >> bits) & 3]
        bits += 2
    objects: list[tuple[str, dict[int, tuple[object, int]]]] = []
    while at < len(data):
        type_key, at = _varuint(data, at)
        props: dict[int, tuple[object, int]] = {}
        while True:
            key, at = _varuint(data, at)
            if key == 0:
                break
            kind = toc_format.get(key) or PROPERTY_FORMAT.get(key)
            start = at
            value: object
            if kind == "u":
                value, at = _varuint(data, at)
            elif kind == "s":
                length, at = _varuint(data, at)
                value = data[at : at + length]
                at += length
            elif kind == "f":
                value = struct.unpack_from("<f", data, at)[0]
                at += 4
            else:
                value = struct.unpack_from("<I", data, at)[0]
                at += 4
            props[key] = (value, start)
        objects.append((TYPE_NAME.get(type_key, str(type_key)), props))
    return objects


def _indices(raw: bytes) -> list[int]:
    out, at = [], 0
    while at < len(raw):
        value, at = _varuint(raw, at)
        out.append(value)
    return out


def seam_pairs(vertices: list[dict], triangles: list[int]) -> list[tuple[int, int]]:
    """이음매 짝(팔 쪽 번호, 몸통 쪽 번호). [vertices]는 메시 꼭짓점 순서대로 {u, v, weight}. 잇달아 놓인 두 꼭짓점이 같은 (u, v)·같은
    무게인데 같은 삼각형에 없으면 짝이다. 팔 쪽 = 이웃이 텍스처 바깥(u 0·1) 쪽으로 더 나간 꼭짓점."""
    neighbours: dict[int, set[int]] = {i: set() for i in range(len(vertices))}
    for t in range(0, len(triangles) - 2, 3):
        tri = triangles[t : t + 3]
        for a in tri:
            neighbours[a] |= set(tri)

    def outer(k: int) -> bool:
        return any(abs(vertices[n]["u"] - 0.5) > abs(vertices[k]["u"] - 0.5) + 0.05 for n in neighbours[k] if n != k)

    pairs = []
    for i in range(len(vertices) - 1):
        a, b = vertices[i], vertices[i + 1]
        if (
            abs(a["u"] - b["u"]) < SAME_UV
            and a["v"] == b["v"]
            and a["weight"] == b["weight"]
            and (i + 1) not in neighbours[i]
            and outer(i) != outer(i + 1)
        ):
            pairs.append((i, i + 1) if outer(i) else (i + 1, i))
    return pairs


def copy_pairs(data: bytes) -> list[tuple[int, int]]:
    """[data](avatar_male_idle.riv)에서 옮겨 쓸 float32 위치 짝(팔 쪽 값의 위치, 몸통 쪽 값의 위치). 값이 이미 같은 짝은 뺀다."""
    objects = _objects(data)
    names = [kind for kind, _ in objects]
    start = names.index("Artboard")
    end = next((i for i in range(start + 1, len(names)) if names[i] in ("LinearAnimation", "Artboard")), len(names))
    board = objects[start:end]
    image = next(
        i
        for i, (kind, props) in enumerate(board)
        if kind == "Image" and props.get(NAME, (b"", 0))[0] == IMAGE_NAME.encode()
    )
    mesh = next(
        i for i, (kind, props) in enumerate(board) if kind == "Mesh" and props.get(PARENT_ID, (None, 0))[0] == image
    )
    triangles = _indices(bytes(board[mesh][1][TRIANGLE_INDEX_BYTES][0]))  # type: ignore[arg-type]
    order = [i for i, (kind, props) in enumerate(board) if kind == "MeshVertex" and props[PARENT_ID][0] == mesh]
    weights: dict[int, tuple] = {}
    for kind, props in board:
        if kind == "Weight":
            weights[int(props[PARENT_ID][0])] = tuple(  # type: ignore[arg-type]
                sorted((k, v) for k, (v, _) in props.items() if k != PARENT_ID)
            )
    vertices = [
        {
            "u": board[i][1].get(VERTEX_U, (0.0, -1))[0],
            "v": board[i][1].get(VERTEX_V, (0.0, -1))[0],
            "weight": weights.get(i),
        }
        for i in order
    ]
    # 애니메이션: 이름 → (아트보드 객체 번호 → 그 객체 x 키프레임 값 위치들)
    keys: dict[str, dict[int, list[int]]] = {}
    current: dict[int, list[int]] | None = None
    target, prop = -1, -1
    for kind, props in objects[end:]:
        if kind == "Artboard":
            break
        if kind == "LinearAnimation":
            name = bytes(props.get(ANIMATION_NAME, (b"", 0))[0]).decode("utf-8")  # type: ignore[arg-type]
            current = keys.setdefault(name, {}) if name in SHAPE_ANIMATIONS else None
        elif kind == "StateMachine":
            current = None
        elif current is not None and kind == "KeyedObject":
            target = int(props[KEYED_OBJECT_ID][0])  # type: ignore[arg-type]
        elif current is not None and kind == "KeyedProperty":
            prop = int(props[KEYED_PROPERTY_KEY][0])  # type: ignore[arg-type]
        elif current is not None and kind == "KeyFrameDouble" and prop == VERTEX_X:
            current.setdefault(target, []).append(props.get(KEYFRAME_VALUE, (0.0, -1))[1])
    if set(keys) != set(SHAPE_ANIMATIONS):
        raise ValueError(f"body shape animations missing: {sorted(set(SHAPE_ANIMATIONS) - set(keys))}")
    out: list[tuple[int, int]] = []
    for arm, torso in seam_pairs(vertices, triangles):
        a, t = order[arm], order[torso]
        candidates = []
        for key in (VERTEX_X, VERTEX_Y):
            if key not in board[a][1] or key not in board[t][1]:
                raise ValueError(f"vertex {a} or {t} has no stored property {key} (cannot patch in place)")
            candidates.append((board[a][1][key][1], board[t][1][key][1]))
        for name in SHAPE_ANIMATIONS:
            ka, kt = keys[name].get(a, []), keys[name].get(t, [])
            if len(ka) != len(kt):
                raise ValueError(f"{name}: keyframe count differs for vertices {a}, {t}")
            candidates += list(zip(ka, kt, strict=True))
        for src, dst in candidates:
            if src < 0 or dst < 0:
                raise ValueError(f"vertices {a}, {t}: a keyframe value is not stored (0), cannot patch in place")
            if data[src : src + 4] != data[dst : dst + 4]:
                out.append((src, dst))
    return out


def close_seam(data: bytes) -> bytes:
    """[copy_pairs]대로 몸통 쪽 꼭짓점 값을 팔 쪽 값으로 바꾼 riv 사본."""
    out = bytearray(data)
    for src, dst in copy_pairs(data):
        out[dst : dst + 4] = data[src : src + 4]
    return bytes(out)


def close_seam_rml(text: str) -> str:
    """Rive 원본(scene.rml, 남자 서기)에서 같은 이음매를 닫는다(판정 렌더가 앱과 같은 메시를 쓰게)."""
    start = text.index(f'<Image name="{IMAGE_NAME}"')
    end = text.index("</Mesh>", start)
    block = text[start:end]
    raw = base64.b64decode(re.search(r'triangleIndexBytes="([^"]+)"', block).group(1))  # type: ignore[union-attr]
    found = list(
        re.finditer(
            r'<MeshVertex x="([^"]+)" y="([^"]+)" u="([^"]+)" v="([^"]+)" id="([^"]+)">\s*'
            r'<Weight values="(\d+)" indices="(\d+)"',
            block,
        )
    )
    vertices = [{"u": float(m.group(3)), "v": float(m.group(4)), "weight": (m.group(6), m.group(7))} for m in found]
    edits: list[tuple[int, int, str]] = []
    spans = []
    for name in SHAPE_ANIMATIONS:
        s = text.index(f'<LinearAnimation name="{name}"')
        spans.append((s, text.index("</LinearAnimation>", s)))
    for arm, torso in seam_pairs(vertices, _indices(raw)):
        a, t = found[arm], found[torso]
        edits.append((start + t.start(1), start + t.end(1), a.group(1)))
        edits.append((start + t.start(2), start + t.end(2), a.group(2)))
        for s, e in spans:
            pattern = r'<KeyedObject objectId="{}">\s*<KeyedProperty property="x">\s*<KeyFrameDouble frame="0" value="([^"]+)"'
            ka = re.search(pattern.format(re.escape(a.group(5))), text[s:e])
            kt = re.search(pattern.format(re.escape(t.group(5))), text[s:e])
            if ka and kt:
                edits.append((s + kt.start(1), s + kt.end(1), ka.group(1)))
    for s, e, new in sorted(edits, reverse=True):
        text = text[:s] + new + text[e:]
    return text
