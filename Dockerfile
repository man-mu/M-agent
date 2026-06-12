# 后端 Dockerfile - 多阶段构建
# 阶段 1: Maven 编译
FROM docker.1ms.run/library/maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 先复制 pom.xml 利用 Docker 缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并编译（跳过测试）
COPY src/main ./src/main
RUN mvn package -DskipTests -B

# 阶段 2: 运行时
FROM docker.1ms.run/library/eclipse-temurin:17-jre

WORKDIR /app

# 安装必要的工具
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 复制编译好的 jar
COPY --from=builder /app/target/*.jar app.jar

# 创建配置目录
RUN mkdir -p /app/config /app/.local

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:8080/api/app/capabilities || exit 1

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
