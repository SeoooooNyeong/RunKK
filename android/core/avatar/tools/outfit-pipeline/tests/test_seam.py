"""남자 서기 긴소매 메시 팔·몸통 이음매 시험(판정 round3 소매 이음새 틈)."""

from __future__ import annotations

import re
import struct

from outfit_pipeline.cli import OUTFITS, RAW
from outfit_pipeline.seam import close_seam, close_seam_rml, copy_pairs, seam_pairs

KOTLIN = OUTFITS.parents[1] / "java/com/a207/runkk/app/core/avatar/RivSleeveSeam.kt"


def _table(source: str, name: str) -> list[int]:
    body = re.search(r"(?<![A-Z_])" + name + r" = intArrayOf\(([^)]*)\)", source)
    assert body is not None
    return [int(x) for x in re.findall(r"\d+", body.group(1))]


def test_app_table_matches_the_riv() -> None:
    pairs = copy_pairs((RAW / "avatar_male_idle.riv").read_bytes())
    # 이음매 짝 20개(양쪽 겨드랑이 아래 10행) × 바탕 x·y와 체형 셋의 x 키
    assert len(pairs) == 100
    source = KOTLIN.read_text(encoding="utf-8")
    assert _table(source, "SOURCES") == [s for s, _ in pairs]
    assert _table(source, "TARGETS") == [t for _, t in pairs]


def test_close_seam_copies_only_the_torso_side_values() -> None:
    data = (RAW / "avatar_male_idle.riv").read_bytes()
    out = close_seam(data)
    pairs = copy_pairs(data)
    changed = {i for i in range(len(data)) if data[i] != out[i]}
    assert changed <= {t + k for _, t in pairs for k in range(4)}
    for s, t in pairs:
        assert out[t : t + 4] == data[s : s + 4]
        gap = abs(struct.unpack_from("<f", data, s)[0] - struct.unpack_from("<f", data, t)[0])
        assert 0 < gap <= 1.5
    assert copy_pairs(out) == []  # 닫은 뒤에는 더 옮길 값이 없다


def test_seam_pairs_pick_unconnected_twins_with_the_same_weight() -> None:
    # 두 띠: 팔(0, 1)과 몸통(2, 3). 1·2가 같은 (u, v)·무게인 이음매 짝, 3·4는 무게가 달라 짝이 아니다
    vertices = [
        {"u": 0.0, "v": 0.5, "weight": "a"},
        {"u": 0.26, "v": 0.5, "weight": "a"},
        {"u": 0.26, "v": 0.5, "weight": "a"},
        {"u": 0.39, "v": 0.5, "weight": "b"},
        {"u": 0.39, "v": 0.5, "weight": "c"},
    ]
    triangles = [0, 1, 0, 2, 3, 2]
    assert seam_pairs(vertices, triangles) == [(1, 2)]


def test_close_seam_rml_moves_the_torso_twin_and_its_shape_keys() -> None:
    import base64

    tri = base64.b64encode(bytes([0, 1, 0, 2, 3, 2])).decode()

    def vertex(i: int, x: float, u: float) -> str:
        return f'<MeshVertex x="{x}" y="-30.0" u="{u}" v="0.3" id="0:{i}">\n<Weight values="255" indices="4" />\n</MeshVertex>'

    def keys(name: str, x: float) -> str:
        return (
            f'<LinearAnimation name="{name}" id="1">'
            f'<KeyedObject objectId="0:1"><KeyedProperty property="x"><KeyFrameDouble frame="0" value="-28.5" /></KeyedProperty></KeyedObject>'
            f'<KeyedObject objectId="0:2"><KeyedProperty property="x"><KeyFrameDouble frame="0" value="{x}" /></KeyedProperty></KeyedObject>'
            "</LinearAnimation>"
        )

    text = (
        f'<Image name="Long sleeve wardrobe top"><Mesh triangleIndexBytes="{tri}">'
        + vertex(0, -54.0, 0.0)
        + vertex(1, -28.5, 0.26)
        + vertex(2, -27.7, 0.26)
        + '<MeshVertex x="-13.0" y="-30.0" u="0.39" v="0.3" id="0:3">\n<Weight values="200" indices="3" />\n</MeshVertex>'
        + "</Mesh></Image>"
        + keys("Body shape base", -27.7)
        + keys("Body shape medium", -27.0)
        + keys("Body shape fuller", -26.0)
    )
    out = close_seam_rml(text)
    assert 'x="-28.5" y="-30.0" u="0.26" v="0.3" id="0:2"' in out
    assert out.count('value="-28.5"') == 6
    assert 'x="-13.0"' in out
