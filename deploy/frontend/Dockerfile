# 博客前端 Dockerfile（骨架草案）
# 构建 context 为仓库根目录（docker-compose 中 context: ..）
# 前置：frontend/ 为 Vue3+Vite 工程（codex 脚手架），构建产物输出到 frontend/dist
# 构建阶段
FROM node:20-alpine AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci || npm install
COPY frontend/ .
RUN npm run build

# 运行阶段：Nginx 托管静态文件 + /api 反代到后端
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
