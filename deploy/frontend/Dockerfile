# 博客前端镜像：Node 构建 → Nginx 托管静态资源并反代 /api
# 构建上下文 = 仓库根目录（compose 里 context: ..，另见根目录 .dockerignore）

# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /app

# 只拷贝锁文件与 package.json，依赖没变就一直命中缓存
COPY frontend/package.json frontend/package-lock.json ./

# 用 npm ci 而不是 `npm ci || npm install`：
#   lock 与 package.json 不一致时应当修 lock，兜底到 npm install 会把「锁文件失效」
#   这个真问题悄悄吞掉，装出一套和提交的依赖树不同的包。
RUN npm ci

# 构建期注入：Vite 只把 VITE_ 前缀的变量暴露给 import.meta.env。
# 不设 VITE_USE_MOCK=false 会踩一个大坑：mock/index.js 的默认值是「非 'false' 即开启」，
# 镜像里没有 .env.local（.dockerignore 排除），于是生产包会走本地 mock 数据，
# 前端永远不请求后端 —— 页面看着正常，但登录、发文、标签全都不落库。
ARG VITE_USE_MOCK=false
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_USE_MOCK=$VITE_USE_MOCK
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

COPY frontend/ ./
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
