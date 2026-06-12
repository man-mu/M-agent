# 和风天气 MCP Dockerfile
FROM docker.1ms.run/library/node:18-alpine AS builder

WORKDIR /app

# 先复制依赖文件利用缓存
COPY package.json package-lock.json ./
RUN npm ci

# 复制源码并编译
COPY . .
RUN npm run build

# 运行阶段
FROM docker.1ms.run/library/node:18-alpine

WORKDIR /app

# 复制编译结果和依赖
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./

# 暴露端口
EXPOSE 18090

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -q --spider http://localhost:18090/health || exit 1

# 启动命令
CMD ["node", "dist/index.js"]
