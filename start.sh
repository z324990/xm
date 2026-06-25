#!/bin/bash
# Railway / Render 启动脚本
set -e

echo "=== AI Chat Platform Start ==="
echo "Port: ${PORT:-8080}"
echo "Java: $(java -version 2>&1 | head -1)"

# 创建必要目录
mkdir -p /app/data /app/uploads

exec java -jar app.jar \
  --server.port=${PORT:-8080} \
  --spring.datasource.url=jdbc:h2:file:/app/data/chatdb\;DB_CLOSE_DELAY=-1\;DB_CLOSE_ON_EXIT=FALSE \
  --app.upload.dir=/app/uploads
