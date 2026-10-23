# Imagem do servico MONAI do GHBAM (modo stub por padrao).
# Para o modo bundle, partir de uma base com CUDA/torch e instalar monai[all].
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

# Sem modelo por padrao: modo stub. Defina MONAI_BUNDLE_DIR para ligar um bundle.
ENV MONAI_MODELO=stub \
    MONAI_VERSAO=0.1.0

EXPOSE 8000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
