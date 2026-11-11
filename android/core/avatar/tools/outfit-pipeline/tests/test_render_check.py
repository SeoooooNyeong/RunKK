"""체형 100 서기 렌더 검사 판정 함수 시험(판정 round3: 체형 100 메시 변형 뒤 외곽선 끊김·사다리 획)."""

from __future__ import annotations

import numpy as np

from outfit_pipeline.render_check import BODY_TOP, KEY, defects


def _render() -> np.ndarray:
    rgb = np.zeros((850, 540, 3), np.uint8)
    rgb[:] = KEY
    rgb[100:800, 150:390] = (230, 190, 170)  # 몸
    return rgb


def test_clean_render_passes() -> None:
    assert defects(_render()) == []


def test_ladder_strokes_beside_the_body_fail() -> None:
    rgb = _render()
    for y in range(520, 550, 3):  # 몸 옆에 떨어진 짧은 가로 획(사다리)
        rgb[y, 140:146] = (60, 30, 16)
    found = defects(rgb)
    assert found and all(d.startswith("floating") for d in found)


def test_see_through_slit_inside_the_body_fails() -> None:
    rgb = _render()
    rgb[530:545, 200:202] = KEY  # 소매와 몸통 사이 가는 틈
    assert any(d.startswith("see-through") for d in defects(rgb))


def test_holes_above_the_chin_and_wide_gaps_are_ignored() -> None:
    rgb = _render()
    rgb[BODY_TOP - 40 : BODY_TOP - 30, 200:202] = KEY  # 머리카락 사이 틈(턱 위)
    rgb[500:520, 152:154] = KEY  # 가로 범위 밖(긴 머리카락 옆)은 보지 않는다
    rgb[100:800, 150:152] = KEY
    rgb[600:700, 250:300] = KEY  # 팔과 몸 사이처럼 넓게 갇힌 배경
    assert defects(rgb) == []
