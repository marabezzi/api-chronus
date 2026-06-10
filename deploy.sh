#!/bin/bash
set -e

echo "=== Deploy Chronus PROD ==="

# 1. Compila
echo "[1/4] Compilando..."
./mvnw clean package -DskipTests -q

# 2. Copia JAR
echo "[2/4] Copiando JAR..."
docker cp target/api-chronus-0.0.1-SNAPSHOT.jar \
  chronus-api:/app/chronus.jar

# 3. Reinicia
echo "[3/4] Reiniciando..."
docker-compose -f docker-compose.yml \
  -f docker-compose.prod.yml \
  --env-file .env.prod \
  restart chronus

# 4. Aguarda e verifica
echo "[4/4] Verificando..."
sleep 20
curl -s http://localhost:8081/api/sync/logs | head -c 100 \
  && echo "" \
  && echo "=== Deploy concluído! ==="