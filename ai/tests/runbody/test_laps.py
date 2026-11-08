"""랩 계산(metrics.laps): 마지막 부분 랩도 평균 심박을 셈한다(MR !91 e2e 결함 7)."""

from __future__ import annotations

import pandas as pd

from runkk_ai.runbody import metrics


def _grid(seconds: int, mps: float, hr: float | None) -> pd.DataFrame:
    ts = list(range(1, seconds + 1))
    return pd.DataFrame(
        {
            "t": ts,
            "dist_cum": [t * mps for t in ts],
            "hr": [hr] * seconds,
        }
    )


def test_partial_last_lap_has_average_heart_rate() -> None:
    # 1,500m를 5m/s로 300초: 1km 랩 하나 + 500m 부분 랩
    laps = metrics.laps(_grid(300, 5.0, 150.0))

    assert [lap.get("partial", False) for lap in laps] == [False, True]
    assert laps[0]["avg_hr"] == 150
    assert laps[1]["km"] == 0.5
    assert laps[1]["avg_hr"] == 150


def test_partial_last_lap_without_heart_rate_is_none() -> None:
    laps = metrics.laps(_grid(300, 5.0, None))

    assert laps[1]["partial"] is True
    assert laps[1]["avg_hr"] is None


def test_partial_lap_averages_only_its_own_seconds() -> None:
    grid = _grid(300, 5.0, 140.0)
    grid.loc[grid["t"] > 200, "hr"] = 170.0  # 1km(200초) 뒤 부분 랩만 170

    laps = metrics.laps(grid)

    assert laps[0]["avg_hr"] == 140
    assert laps[1]["avg_hr"] == 170
