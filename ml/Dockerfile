FROM python:3.13-slim
WORKDIR /app
COPY ml/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY ml /app/ml
EXPOSE 8001
CMD ["python", "-m", "uvicorn", "ml.api:app", "--host", "0.0.0.0", "--port", "8001"]
