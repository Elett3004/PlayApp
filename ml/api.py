import os
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL","2")
import hashlib
import json
import logging
from contextlib import asynccontextmanager
import numpy as np
from fastapi import FastAPI, HTTPException
from .common import ROOT, CONFIG, FEATURES, WeatherInput, features


@asynccontextmanager
async def lifespan(app):
    app.state.model=None
    try:
        import tensorflow as tf
        meta=json.loads((ROOT/"artifacts/metadata.json").read_text(encoding="utf-8"))
        path=ROOT/"artifacts/occupancy.keras"
        if meta["features"]!=FEATURES or meta["config"]!=CONFIG or hashlib.sha256(path.read_bytes()).hexdigest()!=meta["model_sha256"]:
            raise ValueError("Model/preprocessing mismatch")
        app.state.model=tf.keras.models.load_model(path,compile=False)
        app.state.meta=meta
    except Exception:
        logging.getLogger(__name__).exception("Occupancy model failed to load")
        app.state.model=None
    yield


app=FastAPI(title="PlayApp - ocupacion simulada",lifespan=lifespan)


@app.get("/health")
def health():
    if app.state.model is None:raise HTTPException(503,"Model unavailable; run offline training")
    return {"ready":True,"version":app.state.meta["version"],"training_data":"SIMULATED_ONLY"}


@app.post("/predict")
def predict(row: WeatherInput):
    if app.state.model is None:raise HTTPException(503,"Model unavailable")
    try:x=features(row)
    except ValueError as ex:raise HTTPException(422,str(ex)) from ex
    raw=float(app.state.model(np.asarray([x]),training=False).numpy()[0,0])*100
    if not np.isfinite(raw):raise HTTPException(503,"Invalid model output")
    prediction=float(np.clip(raw,0,100 if row.daylight else 1))
    return {"occupancy_pct":round(prediction,1),"source":"neural_network","training_data":"SIMULATED_ONLY","model_version":app.state.meta["version"],"calendar": {"timezone":CONFIG["timezone"]},"night_policy_applied":not row.daylight}
