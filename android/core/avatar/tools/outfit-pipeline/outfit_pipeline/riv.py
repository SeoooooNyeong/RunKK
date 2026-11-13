"""riv(Rive 런타임 파일) 읽기. 의상 템플릿에 필요한 것만 꺼낸다: 이미지 자산, Image·Mesh·MeshVertex(u, v), 애니메이션의 불투명도 키.

파일은 읽기만 한다(승인본 SHA 고정, ApprovedAvatarAssetsTest). 형식: 「RIVE」, 판 번호 둘, 파일 ID, 속성 목차(ToC), 그 뒤 객체 목록.
객체 = 형식 키(varuint) + (속성 키, 값)… + 0. 아트보드 안 객체 번호는 Artboard가 0이고 parentId가 이 번호를 가리킨다.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass, field
from pathlib import Path

from outfit_pipeline.riv_schema import PROPERTY_FORMAT, TYPE_NAME

# 속성 키(rive schema)
NAME = 4
PARENT_ID = 5
OPACITY = 18
ANIMATION_NAME = 55
KEYED_OBJECT_ID = 51
KEYED_PROPERTY_KEY = 53
KEYFRAME_VALUE = 70
ASSET_NAME = 203
IMAGE_ASSET_ID = 206
ASSET_BYTES = 212
VERTEX_U = 215
VERTEX_V = 216
TRIANGLE_INDEX_BYTES = 223

_TOC_FORMAT = ("u", "s", "f", "c")


def _varuint(data: bytes, at: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        byte = data[at]
        at += 1
        value |= (byte & 0x7F) << shift
        shift += 7
        if byte < 0x80:
            return value, at


@dataclass
class RivObject:
    type_name: str
    props: dict[int, object]


@dataclass
class RivImage:
    """아트보드의 Image 하나. [triangles]가 None이면 메시 없이 텍스처 전체가 그려진다."""

    index: int
    name: str
    asset: str
    opacity: float
    uvs: list[tuple[float, float]] = field(default_factory=list)
    triangles: list[int] | None = None


@dataclass
class RivScene:
    assets: list[str]
    asset_bytes: dict[str, bytes]
    images: list[RivImage]
    # 애니메이션 이름 → (Image 번호 → 그 애니메이션이 넣는 마지막 불투명도)
    opacity_keys: dict[str, dict[int, float]]

    def visible_images(self, asset: str, animations: list[str]) -> list[RivImage]:
        """[animations]를 차례로 걸었을 때 불투명도가 0이 아닌, [asset]을 쓰는 Image."""
        shown = []
        for image in self.images:
            if image.asset != asset:
                continue
            opacity = image.opacity
            for animation in animations:
                opacity = self.opacity_keys.get(animation, {}).get(image.index, opacity)
            if opacity > 0:
                shown.append(image)
        return shown


def read_objects(data: bytes) -> list[RivObject]:
    if data[:4] != b"RIVE":
        raise ValueError("not a riv file")
    at = 4
    for _ in range(3):  # 큰 판, 작은 판, 파일 ID
        _, at = _varuint(data, at)
    toc: list[int] = []
    while True:
        key, at = _varuint(data, at)
        if key == 0:
            break
        toc.append(key)
    toc_format: dict[int, str] = {}
    bits = 8
    packed = 0
    for key in toc:
        if bits == 8:
            packed = struct.unpack_from("<I", data, at)[0]
            at += 4
            bits = 0
        toc_format[key] = _TOC_FORMAT[(packed >> bits) & 3]
        bits += 2
    objects: list[RivObject] = []
    while at < len(data):
        type_key, at = _varuint(data, at)
        props: dict[int, object] = {}
        while True:
            key, at = _varuint(data, at)
            if key == 0:
                break
            kind = toc_format.get(key) or PROPERTY_FORMAT.get(key)
            if kind is None:
                raise ValueError(f"unknown property key {key} (type {type_key}); regenerate riv_schema.py")
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
            props[key] = value
        objects.append(RivObject(TYPE_NAME.get(type_key, str(type_key)), props))
    return objects


def _text(value: object) -> str:
    return value.decode("utf-8") if isinstance(value, bytes) else ""


def read_scene(path: Path) -> RivScene:
    objects = read_objects(path.read_bytes())
    assets: list[str] = []
    asset_bytes: dict[str, bytes] = {}
    artboard: list[RivObject] = []
    animations: list[tuple[str, list[RivObject]]] = []
    in_artboard = False
    for obj in objects:
        kind = obj.type_name
        if kind in ("ImageAsset", "FontAsset", "AudioAsset"):
            assets.append(_text(obj.props.get(ASSET_NAME)))
        elif kind == "FileAssetContents" and assets:
            asset_bytes[assets[-1]] = bytes(obj.props.get(ASSET_BYTES, b""))  # type: ignore[arg-type]
        if kind == "Artboard":
            if artboard:
                break  # 첫 아트보드만 쓴다(네 riv 모두 하나)
            in_artboard = True
        if kind == "LinearAnimation":
            in_artboard = False
            animations.append((_text(obj.props.get(ANIMATION_NAME)), []))
            continue
        if kind == "StateMachine":
            in_artboard = False
        if in_artboard:
            artboard.append(obj)
        elif animations and kind in ("KeyedObject", "KeyedProperty", "KeyFrameDouble"):
            animations[-1][1].append(obj)

    images: dict[int, RivImage] = {}
    for index, obj in enumerate(artboard):
        if obj.type_name == "Image":
            asset_index = int(obj.props.get(IMAGE_ASSET_ID, 0))  # type: ignore[arg-type]
            images[index] = RivImage(
                index=index,
                name=_text(obj.props.get(NAME)),
                asset=assets[asset_index],
                opacity=float(obj.props.get(OPACITY, 1.0)),  # type: ignore[arg-type]
            )
    meshes: dict[int, RivImage] = {}
    for index, obj in enumerate(artboard):
        if obj.type_name == "Mesh" and obj.props.get(PARENT_ID) in images:
            image = images[int(obj.props[PARENT_ID])]  # type: ignore[arg-type]
            raw = bytes(obj.props.get(TRIANGLE_INDEX_BYTES, b""))  # type: ignore[arg-type]
            indices = []
            at = 0
            while at < len(raw):
                value, at = _varuint(raw, at)
                indices.append(value)
            image.triangles = indices
            meshes[index] = image
    for obj in artboard:
        if obj.type_name in ("MeshVertex", "ContourMeshVertex") and obj.props.get(PARENT_ID) in meshes:
            meshes[int(obj.props[PARENT_ID])].uvs.append(  # type: ignore[arg-type]
                (float(obj.props.get(VERTEX_U, 0.0)), float(obj.props.get(VERTEX_V, 0.0)))  # type: ignore[arg-type]
            )

    opacity_keys: dict[str, dict[int, float]] = {}
    for name, items in animations:
        target: int | None = None
        prop: int | None = None
        keyed: dict[int, float] = {}
        for obj in items:
            if obj.type_name == "KeyedObject":
                target = int(obj.props.get(KEYED_OBJECT_ID, -1))  # type: ignore[arg-type]
            elif obj.type_name == "KeyedProperty":
                prop = int(obj.props.get(KEYED_PROPERTY_KEY, -1))  # type: ignore[arg-type]
            elif obj.type_name == "KeyFrameDouble" and prop == OPACITY and target in images:
                keyed[target] = float(obj.props.get(KEYFRAME_VALUE, 0.0))  # type: ignore[arg-type]
        opacity_keys[name] = keyed
    return RivScene(assets, asset_bytes, list(images.values()), opacity_keys)
