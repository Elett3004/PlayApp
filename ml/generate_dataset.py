"""Run from project root: python -m ml.generate_dataset."""
import csv
import json
from datetime import datetime, timedelta
from .common import ROOT, CONFIG, ZONE, WeatherInput, calendar_features, features, weather_group
import numpy as np

RULE_PATH = ROOT.parent / "src/main/resources/occupancy/rules.json"


def target(row, rng, rules):
    local, flags = calendar_features(row.observed_at)
    if not row.daylight:
        return float(rng.uniform(rules["night"][0], rules["night"][1]))
    g = weather_group(row.weather_codes)
    ids = []
    ids.append({"clear": "clear_warm" if row.feels_like_c >= 32 else "clear_mild", "partial": "partly_warm", "cloudy": "cloudy",
                "mist": "fog" if row.visibility_m < 5000 else "mist", "light": "light_rain", "moderate": "moderate_rain", "heavy": "heavy_rain", "storm": "heavy_rain"}[g])
    if row.feels_like_c > 40: ids.append("extreme_heat")
    if row.feels_like_c < 26: ids.append("cool")
    if row.wind_kmh > 30: ids.append("wind")
    low, high = min((rules[k] for k in ids), key=lambda r: (r[0], r[1]))
    base = (low + high) / 2
    # Explicit project assumptions, not empirical tourist-demand coefficients.
    uplift = CONFIG["season_uplift_points"]*flags["high_season"] + CONFIG["vacation_uplift_points"]*flags["vacation"]
    uplift += CONFIG["weekend_uplift_points"]*(local.weekday() >= 5) + CONFIG["holiday_uplift_points"]*flags["holiday"]
    demand = .45 + .55*np.exp(-((local.hour-12)/4)**2)
    # Precipitation, mist and strong wind dampen calendar effects.
    damp = .2 if g in ("moderate", "heavy", "storm") else .5 if g in ("light", "mist") or row.wind_kmh > 30 else 1
    return float(np.clip((base + uplift*damp)*demand + rng.normal(0, CONFIG["label_noise_std_points"]), 0, 100))


def generate():
    rng = np.random.default_rng(CONFIG["seed"])
    rules = {r["id"]: (r["minimum"], r["maximum"]) for r in json.loads(RULE_PATH.read_text(encoding="utf-8"))}
    moment = datetime.fromisoformat(CONFIG["dataset_start"]).replace(tzinfo=ZONE)
    end = datetime.fromisoformat(CONFIG["dataset_end_exclusive"]).replace(tzinfo=ZONE)
    rows, xs, ys = [], [], []
    while moment < end:
        # Synthetic weather sampled as correlated scenarios, not API history.
        wet = moment.month in (4, 5, 9, 10, 11)
        code = int(rng.choice([800,801,804,721,741,500,501,502,211], p=[.17,.2,.2,.08,.02,.13,.1,.06,.04] if wet else [.4,.25,.12,.1,.01,.06,.03,.02,.01]))
        group = weather_group([code])
        temp = float(np.clip(28 + 3*np.sin((moment.hour-8)*np.pi/12) + rng.normal(0,1.4) - (2 if group in ("heavy","storm") else 0), 21, 38))
        humidity = float(np.clip(rng.normal(85 if group in ("light","moderate","heavy","storm","mist") else 72,7),40,100))
        feels = float(np.clip(temp + max(0,temp-25)*humidity/100*1.5,21,52))
        wind = float(np.clip(rng.gamma(2,6),0,55))
        rain = float(rng.uniform(*{"light":(.05,2),"moderate":(2,7),"heavy":(7,30),"storm":(2,25)}[group])) if group in ("light","moderate","heavy","storm") else None
        row = WeatherInput(observed_at=moment,temperature_c=round(temp,2),feels_like_c=round(feels,2),humidity_pct=round(humidity,2),wind_kmh=round(wind,2),gust_kmh=round(min(wind+rng.uniform(0,15),100),2),clouds_pct=float(rng.uniform(0,15) if code==800 else rng.uniform(20,50) if code==801 else rng.uniform(60,100)),visibility_m=float(rng.uniform(500,4500) if code==741 else rng.uniform(5000,9000) if code==721 else rng.uniform(5000,10000)),rain_1h_mm=rain,pressure_hpa=round(float(rng.normal(1010,3)),2),weather_codes=[code],daylight=6<=moment.hour<18)
        label = round(target(row,rng,rules),2)
        # Missingness is represented by masks; never treated as a measured zero.
        missing = {k: None for k in ["gust_kmh","humidity_pct","pressure_hpa","clouds_pct"] if rng.random()<.05}
        row = row.model_copy(update=missing)
        local, flags = calendar_features(moment)
        item = row.model_dump(mode="json") | flags | {"hour":local.hour,"weekday":local.weekday(),"month":local.month,"ocupacion_pct":label,"tipo_registro":"simulado","fuente_ocupacion":"simulador-v1"}
        rows.append(item);xs.append(features(row));ys.append(label)
        moment += timedelta(hours=1)
    out=ROOT/"data";out.mkdir(exist_ok=True)
    with (out/"occupancy_simulated.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
    np.savez_compressed(out/"training.npz", x=np.asarray(xs), y=np.asarray(ys,dtype=np.float32), years=np.asarray([int(r["observed_at"][:4]) for r in rows]))
    (out/"metadata.json").write_text(json.dumps({"rows":len(rows),"config":CONFIG,"type":"SIMULATED_ONLY"},indent=2),encoding="utf-8")
    print(f"Generated {len(rows)} simulated rows",flush=True)

if __name__=="__main__": generate()
