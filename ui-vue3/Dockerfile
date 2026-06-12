# 前端 Dockerfile - 多阶段构建
# 阶段 1: Node.js 编译
FROM node:18-alpine AS builder

WORKDIR /app

# 先复制依赖文件利用缓存
COPY package.json package-lock.json ./
RUN npm ci

# 复制源码并编译
COPY . .
RUN npm run build

# 阶段 2: Nginx 运行
FROM nginx:alpine

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 复制编译好的静态文件
COPY --from=builder /app/ui /usr/share/nginx/html

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -q --spider http://localhost/ || exit 1
