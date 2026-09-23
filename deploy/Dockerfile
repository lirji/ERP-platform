# Node 仅用于生成静态资源；运行镜像仍只有既有 JVM。
FROM node:24.12.0-alpine AS frontend
WORKDIR /web
COPY erp-web/package.json erp-web/package-lock.json ./
RUN npm ci --no-audit
COPY erp-web/ ./
RUN npm run build

# Java 21 / Maven 与既有技术选型一致；构建阶段不进入最终运行镜像。
FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
COPY --from=frontend /web/dist /workspace/erp-web/dist
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests -DskipFrontend=true package

FROM eclipse-temurin:21.0.12_8-jre-ubi9-minimal
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/erp-app/target/erp-app-0.1.0-SNAPSHOT.jar /app/erp-app.jar
USER 10001:10001
EXPOSE 8500
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "/app/erp-app.jar"]
