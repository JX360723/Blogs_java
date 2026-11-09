# PHASE 5 施工单：Docker Compose 整栈可运行

> 交付对象：后端/运维开发（WorkBuddy）
> 基线：`main`（`b431f43` 之后，含 PHASE 4 标签模块）
> 交付时间：2026-11-07
> 目标：`docker compose up -d` 一条命令起全套，本机能登录、能发文、访客能看

## 一、你接手时的现状

`deploy/` 已经有 6 个文件的骨架，**结构是对的，但有 4 个问题会让 `docker compose up` 直接失败**：

| 文件 | 状态 |
| --- | --- |
| `deploy/docker-compose.yml` | 三服务齐全（mysql / backend / frontend），但见下方问题 |
| `deploy/backend/Dockerfile` | 多阶段构建，思路对；运行阶段缺 curl |
| `deploy/frontend/Dockerfile` | 多阶段构建；`COPY frontend/ .` 会拷进本地 node_modules |
| `deploy/frontend/nginx.conf` | SPA 回退 + `/api` 反代都写了，基本可用 |
| `deploy/.env.example` | 缺 `ADMIN_INIT_PASSWORD` |
| `deploy/README.md` | 待补完整操作步骤 |

**你的任务不是重写这些文件，是让它们真的跑起来。**

## 二、任务 A：让整栈能起来（P0，必须做）

### A1. 管理员账号没有初始化，登录不进去

后端 `blogs-core/.../init/AdminUserInitializer.java` 的逻辑是：启动时如果 `admin_user` 表为空，用环境变量 `ADMIN_INIT_PASSWORD` 的 bcrypt 哈希创建 `admin` 账号。

但 `docker-compose.yml` 的 backend 环境变量里**没有传这个变量**。容器起来后表是空的，初始化器读不到密码，跳过创建 —— 前端登录页永远「用户名或密码错误」。

修：

```yaml
# docker-compose.yml → backend.environment
ADMIN_INIT_PASSWORD: ${ADMIN_INIT_PASSWORD}
```

```bash
# deploy/.env.example
ADMIN_INIT_PASSWORD=change_me_admin_password_at_least_8_chars
```

注意 `AdminUserInitializer` 只在表为空时创建，**改了 `.env` 里的密码不会更新已存在的 admin**。要重置得先 `DELETE FROM admin_user` 或 `docker compose down -v`。

### A2. 数据库初始化策略要明确

现状是两套机制混着，需要理清：

- `application-dev.yml` 里有 `spring.sql.init.mode: always` + `schema-locations: classpath:db/schema.sql` → **dev profile 下后端启动会自动建表**
- `docker-compose.yml` 里 `./initdb:/docker-entrypoint-initdb.d` 被注释掉了

**采用方案**：容器跑 `prod` profile（见 A4），生产不自动建表；建表交给 MySQL 容器的初始化目录。

```yaml
# docker-compose.yml → mysql.volumes
- mysql_data:/var/lib/mysql
- ../blogs-admin/src/main/resources/db/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
```

`schema.sql` 里全是 `CREATE TABLE IF NOT EXISTS`，幂等，可以直接挂。

**坑**：`docker-entrypoint-initdb.d` 只在数据卷**为空**时执行一次。改了 schema 后要 `docker compose down -v` 清卷重来，否则新表不会创建。

### A3. healthcheck 用了镜像里不存在的 curl

```yaml
healthcheck:
  test: ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health || exit 1"]
```

`eclipse-temurin:21-jre` 镜像里**没有 curl**（也没有 wget、没有 netcat）。这个 healthcheck 永远返回失败，而 `frontend` 服务写了 `depends_on: backend`，会导致前端容器一直等待。

两种修法，选一个：

**推荐**：Dockerfile 运行阶段装 curl

```dockerfile
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
```

**备选**：去掉 backend 的 healthcheck，前端 `depends_on` 只写服务名不带 condition。但这样「后端没起来前端已经在跑」的问题就藏起来了，不推荐。

### A4. 容器跑 dev profile 不合适

`.env.example` 里 `SPRING_PROFILES_ACTIVE=dev`，容器里会：

- 打印**全量 SQL 和参数**到日志（性能损耗 + 把用户数据打进日志）
- 自动建表（与 A2 的方案冲突，两处同时建表容易出竞态）
- Redis 指向 `localhost:6379`（容器内没有 Redis，虽然健康检查已关，但配置是错的）

新增 `blogs-admin/src/main/resources/application-prod.yml`：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    # 全部走环境变量，镜像内不落任何真实值
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  # 生产不自动建表：建表由 MySQL 容器的 initdb 负责
  sql:
    init:
      mode: never
  data:
    redis:
      # MVP 不强制依赖 Redis；真要启用时用环境变量注入
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

# 生产不打印 SQL（application.yaml 里刻意没设 log-impl，这里再显式关一次）
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl

logging:
  level:
    root: info
    com.xdl: info
```

然后 `.env.example` 改 `SPRING_PROFILES_ACTIVE=prod`。

**验证点**：容器日志里不应该出现 `==>  Preparing:` 这类 SQL 语句。

## 三、任务 B：镜像瘦身（P1，很影响构建速度）

### B1. 缺 `.dockerignore`，构建上下文会把 target 和 node_modules 全传进去

`docker-compose.yml` 里 backend 和 frontend 的构建上下文都是 `context: ..`（仓库根目录）。没有 `.dockerignore` 时，Docker 会把：

- `**/target/`（Maven 编译产物，几十到几百 MB）
- `frontend/node_modules/`（几百 MB）
- `.git/`（完整历史）

全部打包发给 Docker daemon。**构建会慢到不可接受**，而且 `COPY blogs-common blogs-common` 会把本地 `target/` 带进镜像。

在仓库根新建 `.dockerignore`：

```
# 构建产物
**/target/
frontend/dist/
frontend/node_modules/

# 版本控制与 IDE
.git/
.gitignore
.idea/
.vscode/

# 部署与文档（构建不需要）
deploy/
docs/
worktrees/

# 本地配置与密钥
.env
.env.local
deploy/.env
**/*.log
```

### B2. `COPY frontend/ .` 会覆盖容器内装好的 node_modules

```dockerfile
COPY frontend/package*.json ./
RUN npm ci || npm install
COPY frontend/ .          # ← 这一行会把本地 node_modules 整个覆盖进来
RUN npm run build
```

有 `.dockerignore` 之后这条就安全了（`frontend/node_modules/` 被排除）。**两件事必须一起做**，只改一个不行。

另外 `RUN npm ci || npm install` 的 `||` 有问题：`npm ci` 失败时可能已经写了半个 `node_modules`，再跑 `npm install` 会拿到不一致的依赖树。`package-lock.json` 在仓库里，直接用 `npm ci`：

```dockerfile
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
```

## 四、任务 C：补齐运维文档

`deploy/README.md` 至少写清：

1. 前置要求（Docker 20.10+ / Compose V2）
2. 首次启动步骤：`cp .env.example .env` → 改 4 个密码 → `docker compose up -d --build`
3. 各服务端口与访问地址（前端 80、后端 8080、MySQL 3306）
4. 默认管理员账号 `admin` + `.env` 里的 `ADMIN_INIT_PASSWORD`
5. **改了 schema 之后怎么重建**：`docker compose down -v && docker compose up -d`
6. 数据备份：`docker exec blogs-mysql mysqldump -u root -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE} > backup.sql`
7. 日志排查：`docker compose logs -f backend`

## 五、坑点（逐条对照，都是这套骨架里已经存在的）

1. **`version: "3.9"` 已经废弃**，Compose V2 会打 warning，直接删掉这一行
2. **`docker-entrypoint-initdb.d` 只在空卷时执行**，改完 schema 必须 `down -v`
3. **`ADMIN_INIT_PASSWORD` 只在 `admin_user` 表为空时生效**，改密码不重建容器是没用的
4. **JRE 镜像没有 curl**，healthcheck 会永远失败
5. **没有 `.dockerignore` 时构建上下文巨大**，且会覆盖容器内 node_modules
6. **`SPRING_PROFILES_ACTIVE=dev` 会打印全量 SQL**，生产必须换 prod
7. **MySQL 容器启动慢**，`depends_on` 必须带 `condition: service_healthy`，healthcheck 的 `start_period` 不要小于 30s
8. **`MYSQL_USER` 不能是 `root`**，官方镜像会拒绝创建
9. **前端镜像构建时 `npm ci` 需要 `package-lock.json`**，仓库里已经有了，不要删
10. **`proxy_pass http://backend:8080`** 里的 `backend` 是 compose 服务名，改服务名时 nginx.conf 要同步改

## 六、可复制的完整提示词

```
你是这个个人知识博客项目的后端/运维开发。仓库：Blogs_java，当前在 main 分支。

【任务目标】
让 docker compose 一条命令起全套（MySQL + 后端 + 前端 Nginx），
本机能登录、能发文、访客能看文章。deploy/ 目录已有骨架，你的任务是修好它，
不是重写。

【项目技术栈】
Spring Boot 3.4.0 + Java 21 + MyBatis-Plus 3.5.9 + MySQL 8.0 + Spring Security。
Maven 五模块：blogs-model / blogs-common / blogs-security / blogs-core / blogs-admin。
前端 Vue3 + 纯 JavaScript + Vite + Element Plus，构建产物在 frontend/dist。

【已有骨架】
deploy/docker-compose.yml      三服务齐全（mysql / backend / frontend）
deploy/backend/Dockerfile      多阶段 Maven 构建
deploy/frontend/Dockerfile     多阶段 Node 构建 + Nginx
deploy/frontend/nginx.conf     SPA 回退 + /api 反代到 backend:8080
deploy/.env.example            环境变量模板
deploy/README.md               待补

【必须修的 6 件事】

1. 管理员账号初始化
   docker-compose.yml 的 backend.environment 里加 ADMIN_INIT_PASSWORD: ${ADMIN_INIT_PASSWORD}
   deploy/.env.example 里加 ADMIN_INIT_PASSWORD=change_me_admin_password_at_least_8_chars
   后端 blogs-core/.../init/AdminUserInitializer.java 只在 admin_user 表为空时创建 admin，
   没传这个变量就跳过创建，前端永远登录不进去。

2. 数据库初始化
   docker-compose.yml 的 mysql.volumes 里挂：
     - ../blogs-admin/src/main/resources/db/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
   schema.sql 全是 CREATE TABLE IF NOT EXISTS，幂等可直接挂。
   注意 docker-entrypoint-initdb.d 只在数据卷为空时执行。

3. 修 healthcheck
   eclipse-temurin:21-jre 镜像里没有 curl。在 backend/Dockerfile 运行阶段加：
     RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
   不要删掉 healthcheck，前端 depends_on 依赖它。

4. 新增 prod profile
   新建 blogs-admin/src/main/resources/application-prod.yml：
   - datasource 全走环境变量 SPRING_DATASOURCE_URL / USERNAME / PASSWORD
   - spring.sql.init.mode: never（生产不自动建表）
   - mybatis-plus.configuration.log-impl: NoLoggingImpl（生产不打 SQL）
   - redis host/port 走环境变量，默认 localhost
   deploy/.env.example 里 SPRING_PROFILES_ACTIVE 从 dev 改成 prod。

5. 新增仓库根 .dockerignore
   排除：**/target/、frontend/dist/、frontend/node_modules/、.git/、.idea/、
   .vscode/、deploy/、docs/、worktrees/、.env、.env.local、deploy/.env、**/*.log
   这是必须的：构建上下文是仓库根目录，不排除的话会把 target 和 node_modules 全传给 daemon，
   而且 frontend/Dockerfile 的 COPY frontend/ . 会把本地 node_modules 覆盖进镜像。

6. frontend/Dockerfile 的 npm 安装
   把 RUN npm ci || npm install 改成 RUN npm ci。
   package-lock.json 在仓库里，npm ci 失败说明 lock 和 package.json 不一致，应该修 lock 而不是兜底。

【顺带清理】
- docker-compose.yml 删掉 version: "3.9"（Compose V2 已废弃，会 warning）
- 补齐 deploy/README.md：前置要求、首次启动步骤、端口说明、默认管理员账号、
  改 schema 后如何 down -v 重建、mysqldump 备份命令、日志排查命令

【硬性约束】
- 不要在仓库里写任何真实密码、密钥，全部用 .env + 环境变量注入
- 不要删掉 mysql 的 healthcheck 和 backend 的 depends_on condition
- 不要改后端业务代码和前端业务代码，本轮只动 deploy/ 和 application-prod.yml、.dockerignore
- 前端是 Vue3 + 纯 JavaScript，禁用 TypeScript
- nginx.conf 里 proxy_pass 指向 backend:8080，改 compose 服务名时要同步改

【交付物】
1. 修改：deploy/docker-compose.yml、deploy/backend/Dockerfile、deploy/frontend/Dockerfile、deploy/.env.example
2. 新增：.dockerignore（仓库根）、blogs-admin/src/main/resources/application-prod.yml
3. 补齐：deploy/README.md
4. 一份 docs/PHASE5_DEPLOY_IMPLEMENTATION.md，说明改了什么、为什么、
   以及本机 docker compose up 的实测结果（哪些服务 healthy、登录是否成功）

【验收标准】
1. docker compose up -d --build 后三个容器都 Up，mysql 和 backend 是 healthy
2. docker compose logs backend 里没有 SQL 语句打印（说明 prod profile 生效）
3. 浏览器打开 http://localhost 能看到文章列表页
4. 用 admin + .env 里的 ADMIN_INIT_PASSWORD 能登录后台
5. 后台新建一篇文章并发布，访客端首页能看到
6. 打标签、按标签筛选、编辑文章标签不被清空
7. docker compose down -v 后再 up，数据清空但表结构自动重建，能再次登录
```

## 七、不要做的事

- 不要重写 `deploy/frontend/nginx.conf`，SPA 回退和 `/api` 反代都是对的
- 不要在 docker-compose.yml 里写死密码，全部走 `.env`
- 不要把 `SPRING_PROFILES_ACTIVE` 留在 `dev`
- 不要为了省事删掉 healthcheck
- 不要改后端业务代码（本轮只做部署）
- 不要提交 `deploy/.env`

## 八、验收之后

整栈跑起来后，由 PM 按 `frontend/INTEGRATION_CHECKLIST.md` 走一遍全链路联调，覆盖：

- 访客端：列表 / 分页 / 搜索 / 分类筛选 / 标签筛选 / 详情页阅读数 +1 / 草稿与下架 404
- 管理端：登录 / 文章增删改查 / 快捷发布下架 / 标签增删改 + 绑定 / 分类多级 + 环检测拒绝
- 边界：401 / 403 / 404 文案一致、`pageSize` 过大、非法 ID

这是项目从「代码写完」到「真的能用」的最后一步，也是 M3/M4 里程碑的验收条件。
