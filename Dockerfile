# ==========================================
# Dockerfile - AI Chat Platform
# 支持: Render / Railway / Fly.io / 阿里云
# ==========================================

# ---- Build Stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建数据目录
RUN mkdir -p /app/data /app/uploads

# 从构建阶段复制 JAR
COPY --from=build /app/target/*.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:8080/ || exit 1

# 环境变量（可通过 -e 覆盖）
ENV SERVER_PORT=8080
ENV AI_API_KEY=""
ENV AI_API_URL="https://api.deepseek.com/chat/completions"
ENV AI_MODEL="deepseek-v4-flash"
ENV SPRING_DATASOURCE_URL="jdbc:h2:file:/app/data/chatdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
ENV APP_UPLOAD_DIR="/app/uploads"

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
