"""Offline training: python -m ml.train. Never invoked by HTTP requests."""
import os
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL","2")
os.environ.setdefault("TF_NUM_INTRAOP_THREADS","2")
os.environ.setdefault("TF_NUM_INTEROP_THREADS","2")
import hashlib
import json
import numpy as np
import tensorflow as tf
from .common import ROOT, CONFIG, FEATURES


def train():
    tf.keras.utils.set_random_seed(CONFIG["seed"])
    data=np.load(ROOT/"data/training.npz")
    x,y,years=data["x"],data["y"],data["years"]
    train=years<2024;valid=years==2024;test=years==2025
    norm=tf.keras.layers.Normalization();norm.adapt(x[train])
    model=tf.keras.Sequential([tf.keras.Input(shape=(len(FEATURES),)),norm,tf.keras.layers.Dense(64,activation="relu"),tf.keras.layers.Dense(32,activation="relu"),tf.keras.layers.Dense(1,activation="sigmoid")])
    model.compile(optimizer=tf.keras.optimizers.Adam(.001),loss="mse")
    def ds(mask,shuffle=False):
        result=tf.data.Dataset.from_tensor_slices((x[mask],y[mask]/100))
        if shuffle:result=result.shuffle(20000,seed=CONFIG["seed"])
        options=tf.data.Options();options.threading.private_threadpool_size=2
        return result.batch(128).with_options(options)
    history=model.fit(ds(train,True),validation_data=ds(valid),epochs=100,callbacks=[tf.keras.callbacks.EarlyStopping(monitor="val_loss",patience=10,restore_best_weights=True)],verbose=2)
    pred=model(x[test],training=False).numpy().ravel()*100
    actual=y[test]
    report={"training_data":"SIMULATED_ONLY","split":"train 2022-2023, validation 2024, test 2025","epochs":len(history.history["loss"]),"rows_train":int(train.sum()),"rows_validation":int(valid.sum()),"rows_test":int(test.sum()),"mae_percentage_points":float(np.mean(np.abs(actual-pred))),"rmse_percentage_points":float(np.sqrt(np.mean((actual-pred)**2))),"mean_baseline_mae":float(np.mean(np.abs(actual-y[train].mean()))),"notice":"Measures agreement with simulator, NOT real-world accuracy.","night_policy":"Night output is clamped to 0-1 percent after inference."}
    for name,mask in [("day",x[test,FEATURES.index("daylight")]==1),("night",x[test,FEATURES.index("daylight")]==0)]:
        report[name+"_mae_percentage_points"]=float(np.mean(np.abs(actual[mask]-pred[mask])))
    out=ROOT/"artifacts";out.mkdir(exist_ok=True)
    model.save(out/"occupancy.keras")
    digest=hashlib.sha256((out/"occupancy.keras").read_bytes()).hexdigest()
    metadata={"version":"sim-v1-"+digest[:12],"features":FEATURES,"config":CONFIG,"model_sha256":digest,"tensorflow":tf.__version__,"training_data":"SIMULATED_ONLY"}
    (out/"metadata.json").write_text(json.dumps(metadata,indent=2),encoding="utf-8")
    (out/"evaluation.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print(json.dumps(report,indent=2),flush=True)

if __name__=="__main__": train()
