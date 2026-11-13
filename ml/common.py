import json
from datetime import datetime, timedelta
from functools import lru_cache
from pathlib import Path
from zoneinfo import ZoneInfo

import holidays
import numpy as np
from pydantic import BaseModel, ConfigDict, Field, field_validator

ROOT = Path(__file__).resolve().parent
CONFIG = json.loads((ROOT / "config.json").read_text(encoding="utf-8"))
ZONE = ZoneInfo(CONFIG["timezone"])
GROUPS = ["clear", "partial", "cloudy", "mist", "light", "moderate", "heavy", "storm"]
FEATURES = ["temperature", "feels_like", "humidity", "wind", "gust", "clouds", "visibility", "rain", "pressure",
            "humidity_missing", "gust_missing", "clouds_missing", "visibility_missing", "rain_missing", "pressure_missing",
            "hour_sin", "hour_cos", "weekday_sin", "weekday_cos", "year_sin", "year_cos", "holiday", "bridge", "vacation", "high_season", "daylight"] + GROUPS


class WeatherInput(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    observed_at: datetime
    temperature_c: float = Field(ge=18, le=45)
    feels_like_c: float = Field(ge=18, le=60)
    humidity_pct: float | None = Field(default=None, ge=0, le=100)
    wind_kmh: float = Field(ge=0, le=100)
    gust_kmh: float | None = Field(default=None, ge=0, le=150)
    clouds_pct: float | None = Field(default=None, ge=0, le=100)
    visibility_m: float | None = Field(default=None, ge=0, le=10000)
    rain_1h_mm: float | None = Field(default=None, ge=0, le=100)
    pressure_hpa: float | None = Field(default=None, ge=900, le=1100)
    weather_codes: list[int] = Field(min_length=1, max_length=10)
    daylight: bool

    @field_validator("observed_at")
    @classmethod
    def aware_timestamp(cls, value):
        if value.tzinfo is None:
            raise ValueError("observed_at must include UTC offset")
        return value


@lru_cache(maxsize=32)
def calendar(year):
    return holidays.country_holidays("CO", years=[year - 1, year, year + 1])


def calendar_features(moment):
    local = moment.astimezone(ZONE)
    day = local.date()
    cal = calendar(day.year)
    holy_thursdays = [d for d, name in cal.items() if d.year == day.year and ("Maundy" in name or "Jueves Santo" in name)]
    holy_week = any(d - timedelta(days=3) <= day <= d + timedelta(days=3) for d in holy_thursdays)
    high = local.month in CONFIG["high_months"] or holy_week
    vacation = local.month in CONFIG["vacation_months"] or holy_week
    bridge = (local.weekday() == 5 and day + timedelta(days=2) in cal) or (local.weekday() == 6 and day + timedelta(days=1) in cal)
    return local, {"holiday": day in cal, "bridge": bridge, "vacation": vacation, "high_season": high}


def weather_group(codes):
    supported = {200,201,202,210,211,212,221,230,231,232,300,301,302,310,311,312,313,314,321,500,501,502,503,504,520,521,522,531,701,721,741,800,801,802,803,804}
    if not codes or any(c not in supported for c in codes):
        raise ValueError("Weather condition outside the simulated training domain")
    if any(200 <= c < 300 for c in codes): return "storm"
    if any(c in (302, 312, 314, 502, 503, 504, 522) for c in codes): return "heavy"
    if any(c in (501, 521, 531) for c in codes): return "moderate"
    if any(300 <= c < 400 or c in (500, 520) for c in codes): return "light"
    if any(c in (701, 721, 741) for c in codes): return "mist"
    if any(c in (803, 804) for c in codes): return "cloudy"
    if any(c in (801, 802) for c in codes): return "partial"
    if codes == [800]: return "clear"
    raise ValueError("Weather condition outside the simulated training domain")


def features(row: WeatherInput):
    group = weather_group(row.weather_codes)
    if group == "mist" and row.visibility_m is None:
        raise ValueError("Visibility required for mist or fog")
    local, flags = calendar_features(row.observed_at)
    optional = [row.humidity_pct, row.gust_kmh, row.clouds_pct, row.visibility_m, row.rain_1h_mm, row.pressure_hpa]
    defaults = [75, row.wind_kmh, 50, 10000, 0, 1010]
    values = [v if v is not None else default for v, default in zip(optional, defaults)]
    humidity, gust, clouds, visibility, rain, pressure = values
    hour = local.hour + local.minute / 60
    phase = (local.timetuple().tm_yday - 1) / (366 if calendar_is_leap(local.year) else 365)
    x = [row.temperature_c, row.feels_like_c, humidity, row.wind_kmh, gust, clouds, visibility, rain, pressure]
    x += [float(v is None) for v in optional]
    for angle in [2*np.pi*hour/24, 2*np.pi*local.weekday()/7, 2*np.pi*phase]:
        x.extend([np.sin(angle), np.cos(angle)])
    x += [float(flags[k]) for k in ["holiday", "bridge", "vacation", "high_season"]] + [float(row.daylight)]
    x += [float(group == g) for g in GROUPS]
    return np.asarray(x, dtype=np.float32)


def calendar_is_leap(year):
    return year % 4 == 0 and (year % 100 != 0 or year % 400 == 0)
