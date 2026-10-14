# 博客系统部署骨架（deploy/）

> **状态：骨架草案**。用于本地 Docker Compose 整栈运行（MySQL + 后端 + 前端/Nginx），并作为后续服务器部署基线。
> 尚未经过完整构建验证：后端/前端 Dockerfile 需在对应代码就位后由 claude/codex 验证。

## 目录结构

```
deploy/
├── docker-compose.yml      # 整栈编排（MySQL 8.0 + backend + frontend/Nginx；Redis 注释可选）
├── .env.example            # 环境变量占位（严禁真实密钥；复制为 .env 使用）
├── README.md
├── backend/
│   └── Dockerfile          # 后端镜像骨架（Maven 多模块 → JRE 运行）
└── frontend/
    ├── Dockerfile          # 前端镜像骨架（Node 构建 → Nginx）
    └── nginx.conf          # Nginx：托管静态 + /api 反代到 backend
```

## 快速开始

```bash
cd Blogs_java/deploy

# 1. 准备环境变量（只放占位值；生产必须替换为强随机值）
cp .env.example .env

# 2. 构建并启动整栈
docker compose up -d --build

# 3. 验证
curl -s http://localhost:80            # 前端页面
curl -s http://localhost:8080/actuator/health   # 后端健康检查（端点待审计确认）

# 4. 停止 / 清理（数据卷默认保留）
docker compose down
# 如需同时删除数据卷（谨慎，会清空 MySQL 数据）：
# docker compose down -v
```

## 端口与访问

| 服务 | 宿主端口 | 说明 |
| --- | --- | --- |
| frontend | 80（`FRONTEND_PORT`） | 博客前台/管理端（Nginx） |
| backend | 8080（`BACKEND_PORT`） | Spring Boot API |
| mysql | 3306（`MYSQL_PORT`） | MySQL 8.0，数据卷 `mysql_data` 持久化 |

## 环境变量说明（.env）

- `MYSQL_*`：MySQL root 密码、库名、业务账号密码（占位，必须修改）
- `BACKEND_PORT` / `FRONTEND_PORT` / `MYSQL_PORT`：宿主端口映射
- `JWT_SECRET`：JWT 签名密钥占位（生产必须替换为足够长的随机值，严禁与示例相同）

> 后端容器通过环境变量（`SPRING_DATASOURCE_URL` 等）覆盖本地 `application-dev.yml` 中的 `localhost` 连接，指向 compose 内的 `mysql` 服务。

## 数据持久化

- MySQL 数据保存在命名卷 `mysql_data`，`docker compose down` 不删除；
- 首启初始化：可挂载 `./initdb`（docker-entrypoint-initdb.d）执行 SQL/迁移脚本，待 claude 提供迁移脚本后启用。

## Redis（可选）

当前 MVP 不强制依赖 Redis。`docker-compose.yml` 中 Redis 服务默认**注释**。
若后端审计确认需要 Redis，取消注释并解除 `backend.environment` 中 Redis 相关行的注释。

## 安全约定

- 真实密钥只放 `.env`（已被 `.gitignore` 忽略），**严禁**提交 `.env` 或把真实密钥写入镜像/代码；
- 数据库、JWT 密钥、第三方 Key 一律通过环境变量注入；
- 错误日志不得输出密码、token、内部堆栈细节；
- 管理端接口必须服务端认证授权（见 `docs/API_CONTRACT.md` 第 5 节）。

## 待验证/待确认

- [ ] backend Dockerfile 多模块构建在真实代码上验证
- [ ] frontend Dockerfile 依赖 `frontend/` 工程就位（codex）
- [ ] 健康检查端点：`/actuator/health` 是否引入（待审计）
- [ ] 数据库初始化 SQL / 迁移脚本（claude）
- [ ] Redis 是否启用（待审计）
