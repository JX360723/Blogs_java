# 博客系统 · Docker Compose 部署

一条命令起全套：**MySQL 8.0 + 后端（Spring Boot 3.4 / Java 21）+ 前端（Vite 构建 → Nginx）**。
目标是本机能登录后台、能发文、访客能看文章。

```
浏览器 ──▶ frontend(Nginx:80) ──/api──▶ backend(Spring Boot:8080) ──▶ mysql(8.0:3306)
```

## 1. 前置要求

| 项 | 要求 | 检查命令 |
| --- | --- | --- |
| Docker Engine / Docker Desktop | 23.0+（含 Compose V2） | `docker version`、`docker compose version` |
| 可用内存 | ≥ 4 GB | Docker Desktop → Settings → Resources |
| 可用磁盘 | ≥ 5 GB（镜像 + Maven 依赖 + 数据卷） | `docker system df` |
| 宿主端口 | `80` / `8080` / `3306` 未被占用 | `netstat -ano \| findstr :80`（Windows） |

> Windows 上 Docker Desktop 需要开启 WSL2 后端；首次 `up` 会拉 mysql / maven / node / nginx 镜像，耗时取决于网速。

## 2. 首次启动

```bash
cd blogs/deploy

# 1) 生成配置：逐项改掉 change_me_*（密码、JWT、初始管理员密码）
cp .env.example .env

# 2) 构建并启动（首次会比较久：Maven 拉依赖 + npm ci + 前端构建）
docker compose up -d --build

# 3) 看状态，等 mysql 与 backend 都变成 healthy（约 1-3 分钟）
docker compose ps

# 4) 冒烟
curl -s http://localhost/                    # 前端首页 HTML
curl -s http://localhost/api/v1/articles     # 经 Nginx 反代到后端
curl -s http://localhost:8080/actuator/health # {"status":"UP"}
```

浏览器打开 <http://localhost> → 右上角「登录」→ 用 `admin` + `.env` 里的 `ADMIN_INIT_PASSWORD` 进后台。

## 3. 端口说明

| 服务 | 宿主端口 | 变量 | 说明 |
| --- | --- | --- | --- |
| frontend | 80 | `FRONTEND_PORT` | Nginx：SPA 静态资源 + `/api` 反代到 `backend:8080` |
| backend | 8080 | `BACKEND_PORT` | 仅排查用；正常访问走 80 端口的 `/api` |
| mysql | 3306 | `MYSQL_PORT` | 仅排查/备份用；**生产建议不要暴露到公网** |

改端口只改 `.env`，`nginx.conf` 里的 `proxy_pass http://backend:8080` 指向的是容器内端口，不用动。

## 4. 默认管理员账号

| 用户名 | 密码 | 来源 |
| --- | --- | --- |
| `admin` | `.env` 里的 `ADMIN_INIT_PASSWORD` | 后端 `AdminUserInitializer` 在 `admin_user` 表为空时创建 |

要点：

- 密码长度必须 **6-64 位**。不合法时后端只打一条 `ERROR` 日志并跳过创建，表现为「登录永远失败」；
  查看：`docker compose logs backend | grep -i 管理员`。
- 变量存在但表里已经有管理员时**不会覆盖**；改 `ADMIN_INIT_PASSWORD` 对老库无效，
  要么 `docker compose down -v` 重建，要么进库改密码哈希。
- 初始化只在**表为空**时发生，所以第一次 `up` 必须带上这个变量。

## 5. 表结构与数据

- 表结构由 `blogs-admin/src/main/resources/db/schema.sql` 提供，挂到 MySQL 容器的
  `docker-entrypoint-initdb.d/01-schema.sql`，**仅在数据卷为空时执行一次**。
- `schema.sql` 全部是 `CREATE TABLE IF NOT EXISTS`，重复执行安全；但 `docker-entrypoint-initdb.d`
  不会对已存在的卷二次执行，所以**改了表结构必须重建卷**：

```bash
docker compose down -v      # -v 会删除 mysql_data 卷，数据全丢，谨慎
docker compose up -d --build
```

- 业务数据在命名卷 `mysql_data`。`docker compose down`（不带 `-v`）**不会**删数据。

## 6. 备份与恢复

```bash
# 备份（导出到当前目录 blogs_YYYYmmdd.sql）
docker compose exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
  > blogs_$(date +%Y%m%d).sql

# 恢复（覆盖现有数据，先停后端避免写入）
docker compose stop backend frontend
docker compose exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < blogs_20261107.sql
docker compose start backend frontend
```

> 备份文件里有全部文章内容，不要提交进仓库。

## 7. 日志与排查

```bash
docker compose ps                          # 谁 healthy、谁重启中
docker compose logs -f backend             # 后端实时日志
docker compose logs --tail=200 mysql       # MySQL 启动/建表日志
docker compose logs backend | grep -i "error\|exception"
docker stats --no-stream                   # 内存/CPU 占用
```

常见现象与原因：

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `mysql` 一直 `starting` / `unhealthy` | `.env` 缺 `MYSQL_*`，或端口 3306 被本机 MySQL 占用 | 补 `.env`；改 `MYSQL_PORT=3307` |
| `backend` 反复重启 | 数据源连不上、`JWT_SECRET` 未设 | `docker compose logs backend` 看第一条异常 |
| `backend` Up 但 `unhealthy` | 镜像缺 curl（Dockerfile 已装）或 `/actuator/health` DOWN | 先 `curl localhost:8080/actuator/health` 看明细 |
| `frontend` 一直不启动 | 它 `depends_on` 后端 `healthy`，后端没起来它就不起 | 先修 backend |
| 页面能开但列表空、登录失败 | 前端打到了 mock（`VITE_USE_MOCK` 未设成 false） | 确认 Dockerfile 里 `ARG VITE_USE_MOCK=false` 生效后重建前端 |
| 登录报账号密码错 | `ADMIN_INIT_PASSWORD` 未设/长度不合法，admin 没被创建 | 进库 `select * from admin_user;` 确认 |
| 改了 `schema.sql` 但新字段不生效 | initdb 只在空卷执行 | `down -v` 重建 |
| 时间显示差 8 小时 | 容器 TZ | compose 已设 `TZ=Asia/Shanghai`，检查镜像是否带 tzdata |

## 8. 安全约定

- 真实密码/密钥只存在 `deploy/.env`（已被 `deploy/.gitignore` 忽略），仓库里只有 `change_me_*` 占位；
- 镜像内不写任何凭据，全部运行时注入；
- 生产环境请把 `3306`/`8080` 从 `ports` 里去掉，只暴露 80，并前置 HTTPS（Caddy / Nginx + 证书）；
- 后端容器以非 root 用户 `app` 运行。

## 9. 目录结构

```
deploy/
├── docker-compose.yml       # 三服务编排（Redis 为可选注释块）
├── .env.example             # 环境变量占位模板
├── dockerignore 说明见仓库根 .dockerignore（构建上下文 = 仓库根）
├── backend/Dockerfile       # Maven 多模块构建 → JRE 运行（含 curl 供 healthcheck）
└── frontend/
    ├── Dockerfile           # npm ci → vite build → Nginx
    └── nginx.conf           # SPA 回退 + /api 反代到 backend:8080
```
