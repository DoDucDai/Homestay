# Build from the Homestay repository root (the directory containing this file).
# NOTE TRIỂN KHAI: Stage build biên dịch Java, đóng gói frontend/AI và migration thành JAR; không đưa .env thật vào image.
FROM maven:3.9-eclipse-temurin-25-noble AS build
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY frontend frontend
COPY ai-rag/web ai-rag/web
COPY database/migrations database/migrations
RUN mvn -B -f backend/pom.xml -DskipTests package

# NOTE TRIỂN KHAI: Stage runtime chỉ chứa JRE/Python và bản build, chạy bằng user homestay có quyền hạn chế.
FROM eclipse-temurin:25-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 homestay \
    && useradd --uid 10001 --gid homestay --create-home homestay \
    && mkdir -p /app /var/lib/homestay \
    && chown homestay:homestay /app /var/lib/homestay
WORKDIR /app
COPY --from=build --chown=homestay:homestay /build/backend/target/homestay-backend-1.0.0.jar /app/homestay.jar
COPY --chown=homestay:homestay ai-rag/app /app/ai-rag/app
COPY --chown=homestay:homestay ai-rag/import-knowledge.py /app/ai-rag/import-knowledge.py
COPY --chown=homestay:homestay ai-rag/knowledge/published.json /app/ai-rag/knowledge/published.json
COPY --chown=homestay:homestay docker /app/docker
# NOTE TRIỂN KHAI: Các giá trị môi trường mặc định bật profile prod, cookie HTTPS, kiểm tra TLS và nơi lưu SQLite.
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    SPRING_PROFILES_ACTIVE=prod \
    COOKIE_SECURE=true \
    DB_ENCRYPT=true \
    DB_TRUST_SERVER_CERTIFICATE=false \
    SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    AI_RAG_DB_PATH=/var/lib/homestay/assistant.sqlite3
USER homestay
EXPOSE 8080
STOPSIGNAL SIGTERM
# NOTE TRIỂN KHAI: Docker kiểm tra cả hai dịch vụ qua healthcheck.py; ENTRYPOINT giao docker/start.py quản lý vòng đời.
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD ["python3", "/app/docker/healthcheck.py"]
ENTRYPOINT ["python3", "/app/docker/start.py"]
