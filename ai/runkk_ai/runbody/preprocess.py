"""세션 원본(samples·gps) → 1초 그리드 DataFrame.

컬럼: t, hr, speed, cadence, dist_delta, dist_cum, lat, lon, alt, acc, moving, warmup.
센서마다 기록 시각이 달라도 1초 칸으로 모은다(심박·속도·케이던스는 평균, 거리 증가량은 합).
"""

from __future__ import annotations

from datetime import date

import numpy as np
import pandas as pd

from runkk_ai.runbody.schemas import SessionIn

WARMUP_S = 60  # 시작 후 이 구간은 안정성·코스 지표에서 뺀다
MOVING_MPS = 0.5  # 이 속도(m/s) 미만은 멈춘 것으로 본다
_GPS_COLUMNS = ("lat", "lon", "alt", "acc")


def to_grid(session: SessionIn) -> pd.DataFrame:
    n = session.activeMs // 1000 + 1
    grid = pd.DataFrame({"t": np.arange(n)})
    samples = pd.DataFrame([s.model_dump() for s in session.samples])
    samples["t"] = samples["tMs"] // 1000
    samples = samples[(samples["t"] >= 0) & (samples["t"] < n)]

    def column(name: str, how: str) -> pd.Series[float]:
        values = samples.dropna(subset=[name]).groupby("t")[name].agg(how)
        return grid["t"].map(values).astype(float)

    grid["hr"] = column("heartRate", "mean").ffill().bfill()
    grid["speed"] = column("speed", "mean").ffill().fillna(0.0)
    grid["cadence"] = column("cadence", "mean").ffill().fillna(0.0)
    grid["dist_delta"] = column("distance", "sum").fillna(0.0)
    grid["dist_cum"] = grid["dist_delta"].cumsum()

    if session.gps:
        gps = pd.DataFrame([p.model_dump() for p in session.gps])
        gps["t"] = gps["tMs"] // 1000
        gps = gps[(gps["t"] >= 0) & (gps["t"] < n)].groupby("t").last()
        for name in _GPS_COLUMNS:
            grid[name] = grid["t"].map(gps[name]).astype(float)
    else:
        for name in _GPS_COLUMNS:
            grid[name] = np.nan

    grid["moving"] = grid["speed"] >= MOVING_MPS
    grid["warmup"] = grid["t"] < WARMUP_S
    return grid


def hr_max_for(birth_year: int, on: date, observed: int | None = None) -> int:
    """개인 최고 심박: api가 넘긴 관측값이 있으면 그것, 없으면 220 - 나이(러닝 날짜 기준)."""
    if observed is not None:
        return observed
    return 220 - (on.year - birth_year)
