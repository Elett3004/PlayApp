# Red neuronal de ocupación de Bocagrande

Prototipo académico: **entrenado exclusivamente con datos simulados**. Las métricas
evalúan acuerdo con el simulador; no miden precisión real en la playa.

## Archivos

- `generate_dataset.py`: genera 35.064 observaciones sintéticas horarias (2022–2025),
  CSV para revisar y NPZ para entrenar. No descarga historia de OpenWeatherMap.
- `config.json`: semilla y supuestos de temporadas y demanda. Diciembre/enero,
  vacaciones junio/julio y Semana Santa son supuestos de este proyecto, no un
  calendario turístico oficial. Festivos: `python-holidays`, Colombia.
- `common.py`: validación, calendario colombiano, zona America/Bogota y preparación
  compartida por entrenamiento e inferencia. Valores ausentes usan sustituciones
  explícitas más indicadores de ausencia; no se presentan como mediciones de cero.
- `train.py`: NumPy + TensorFlow/Keras, red 64/32 neuronas, normalización aprendida
  solo del entrenamiento, early stopping. Train: 2022–2023, validación: 2024, test: 2025.
- `artifacts/occupancy.keras`: modelo entrenado; `metadata.json`: versión, hash y
  configuración; `evaluation.json`: errores en puntos porcentuales y referencia media.
- `api.py`: FastAPI; carga el modelo al iniciar, no entrena por petición. Rechaza
  artefactos con preparación o configuración incompatibles y responde 503 si faltan.
- `data/field_observations_template.csv`: plantilla de observaciones del grupo.

## Ejecutar en Windows

Python 3.13 CPU, dependencias fijadas en `requirements.txt`. Entorno aislado,
sin cambios al Python global. Modelo y dataset ya están incluidos.

```powershell
python -m venv .venv-occupancy
.\.venv-occupancy\Scripts\python.exe -m pip install -r ml/requirements.txt
powershell -ExecutionPolicy Bypass -File scripts/start-occupancy.ps1
```

El inicio local habitual de PlayApp también inicia FastAPI en segundo plano si
encuentra el entorno y el modelo. URL local: `http://127.0.0.1:8001/docs`;
salud: `/health`. Logs: `target/occupancy-api.*.log`. El servicio escucha solo
en localhost. Para detenerlo, terminar su proceso de uvicorn; no cerrar todos los Python.

Para regenerar y entrenar desde cero:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-occupancy.ps1
```

Tras reentrenar, reiniciar FastAPI para cargar la nueva versión. No entrenar sobre
los mismos archivos que un servicio está cargando. Para promoción de versiones,
parar el servicio, entrenar, probar y volver a iniciarlo.

## Docker

```powershell
docker compose up -d --build occupancy-model
docker compose --profile app up -d --build
```

Docker publica el servicio en `http://127.0.0.1:8002/docs` para que pueda coexistir
con el FastAPI local del puerto 8001. El puerto interno del contenedor sigue siendo 8001.
El contenedor sirve el modelo empaquetado; no lo entrena al iniciar. App Java usa
`http://occupancy-model:8001`; local usa `http://localhost:8001`. Se configura con
`OCCUPANCY_MODEL_URL`. La clave OpenWeatherMap permanece exclusivamente en Java.

## Contrato y límites

`POST /predict` recibe JSON con `observed_at` ISO 8601 con zona, `temperature_c`,
`feels_like_c`, `wind_kmh`, `weather_codes` (lista), `daylight` (booleano). Opcionales:
`humidity_pct`, `gust_kmh`, `clouds_pct`, `visibility_m`, `rain_1h_mm`, `pressure_hpa`.
Responde `occupancy_pct`, `model_version`, `training_data=SIMULATED_ONLY` y
`source=neural_network`. Sin porcentajes de confianza inventados ni intervalos arbitrarios.
Noche: ajuste posterior de política 0–1 %, identificado como `night_policy_applied`.
El rango no significa certeza de ausencia de personas. El simulador supone sol 06–18;
en producción Java usa amanecer/atardecer de la API y envía su hora de observación.

Clima fuera de dominio (nieve, humo, polvo, etc.), visibilidad ausente para niebla,
inputs inválidos, servicio caído o respuesta incorrecta: Java conserva reglas de
respaldo y muestra esa procedencia. Los datos del clima conservan su caché de 10 min.
La explicación neural es descriptiva de las entradas; no es una atribución causal.

## Pruebas y validación real

```powershell
.\.venv-occupancy\Scripts\python.exe -m pytest ml/tests -q
```

El CSV de campo registra predicción, porcentaje observado, fecha, versión y
observador. Completar con datos reales del mismo punto y momento; no copiar las
etiquetas simuladas como observaciones. Definir un criterio consistente de 0–100 %.
Reservar observaciones de evaluación que no se utilicen para reentrenar. Las reglas
actuales y su Excel siguen siendo la base del simulador y el respaldo de Java.

Referencias técnicas: https://www.tensorflow.org/tutorials/keras/regression,
https://fastapi.tiangolo.com/advanced/events/, https://numpy.org/doc/stable/reference/random/generator.html.
