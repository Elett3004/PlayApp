from datetime import datetime
import numpy as np
import pytest
from fastapi.testclient import TestClient
from ml.api import app
from ml.common import WeatherInput, features, FEATURES, calendar_features, weather_group, ROOT

PAYLOAD={"observed_at":"2026-12-27T16:00:00Z","temperature_c":32,"feels_like_c":38,"humidity_pct":75,"wind_kmh":12,"clouds_pct":20,"visibility_m":10000,"weather_codes":[801],"daylight":True}


def test_calendar_timezone_and_missing_masks():
    x=features(WeatherInput(**PAYLOAD))
    assert len(x)==len(FEATURES) and np.isfinite(x).all()
    assert x[FEATURES.index("rain_missing")]==1
    assert x[FEATURES.index("high_season")]==1
    local,_=calendar_features(datetime.fromisoformat(PAYLOAD["observed_at"]))
    assert local.hour==11
    assert weather_group([800,211])=="storm"


def test_synthetic_dataset_temporal_split():
    data=np.load(ROOT/"data/training.npz")
    assert len(data["x"])==35064
    assert set(data["years"])=={2022,2023,2024,2025}
    assert np.isfinite(data["x"]).all()
    assert ((data["y"]>=0)&(data["y"]<=100)).all()
    assert data["y"][data["x"][:,FEATURES.index("daylight")]==0].max()<=1


def test_real_saved_tensorflow_model_api_and_validation():
    with TestClient(app) as client:
        assert client.get("/health").status_code==200
        prediction=client.post("/predict",json=PAYLOAD)
        assert prediction.status_code==200,prediction.text
        body=prediction.json()
        assert body["source"]=="neural_network" and body["training_data"]=="SIMULATED_ONLY"
        assert 0<=body["occupancy_pct"]<=100
        night=client.post("/predict",json=PAYLOAD|{"observed_at":"2026-12-28T03:00:00Z","daylight":False}).json()
        assert 0<=night["occupancy_pct"]<=1 and night["night_policy_applied"]
        assert client.post("/predict",json=PAYLOAD|{"weather_codes":[600]}).status_code==422
        assert client.post("/predict",json=PAYLOAD|{"weather_codes":[800,600]}).status_code==422
        assert client.post("/predict",json=PAYLOAD|{"observed_at":"2026-12-27T11:00:00"}).status_code==422
        assert client.post("/predict",json=PAYLOAD|{"humidity_pct":101}).status_code==422
        assert client.post("/predict",json=PAYLOAD|{"weather_codes":[721],"visibility_m":None}).status_code==422
        app.state.model=None
        assert client.get("/health").status_code==503
        assert client.post("/predict",json=PAYLOAD).status_code==503
