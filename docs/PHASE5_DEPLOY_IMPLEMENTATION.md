# PHASE 5 · Docker Compose 整栈部署实施记录

> 目标：`docker compose up -d --build` 一条命令起 MySQL + 后端 + 前端，本机能登录、能发文、访客能看文章。
> 范围：只动 `deploy/`、`application-prod.yml`、根 `.dockerignore`；后端与前端业务代码零改动。

## 0. 先说结论与边界

| 项 | 状态 |
| --- | --- |
| 6 项必修 + 顺带清理 | 全部完成 |
| `application-prod.yml` 真实启动验证 | ✅ 已实测（profiles=prod、零 SQL 日志、health UP） |
| prod profile 下业务链路（登录/发文/打标签/筛选/编辑不丢标签） | ✅ 12 条断言全 PASS |
| 前端 `npm ci` + 生产构建（`VITE_USE_MOCK=false`） | ✅ 已实测，产物内无 mock 代码 |
| 构建产物经「静态托管 + SPA 回退 + /api 反代」跑通真实后端 | ✅ 已实测（Node 复刻 nginx 三条职责） |
| `docker compose up` 三容器 healthy | ⚠️ **未实测**：本机未安装 Docker（`docker` 不在 PATH，无 Docker Desktop、无 `com.docker.service`） |
| `down -v` 后重建 | ⚠️ 同上，未实测；逻辑与命令见第 5 节 |

Docker 层面的三条验收（容器 healthy、日志无 SQL、down -v 重建）需要装好 Docker 后按第 5 节逐条跑。
为了让这三条尽量「装上就能过」，第 3 节列了三个静态风险点及其处理。

## 1. 六项必修

### 1.1 管理员账号初始化

- `docker-compose.yml` 的 `backend.environment` 增加 `ADMIN_INIT_PASSWORD: ${ADMIN_INIT_PASSWORD}`；
- `deploy/.env.example` 增加 `ADMIN_INIT_PASSWORD=change_me_admin_password_at_least_8_chars`。

**为什么必须显式传**：`AdminUserInitializer` 的逻辑是「`admin_user` 表为空 && 环境变量有值」才创建 admin。
不传变量时它只打一条 WARN 就返回，容器照样 healthy、页面照样能开，但**没有任何账号能登录后台**，
排查起来毫无线索（日志里那条 WARN 还很容易被后面的 INFO 冲掉）。密码长度要求 6-64，不合法时同样是「静默跳过 + 一条 ERROR」。

### 1.2 数据库初始化

```yaml
volumes:
  - ../blogs-admin/src/main/resources/db/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
```

- `schema.sql` 里 5 张表（admin_user / article / category / tag / article_tag）全是 `CREATE TABLE IF NOT EXISTS`，幂等；
- MySQL 官方镜像的 `docker-entrypoint.sh` 会用 `--database="$MYSQL_DATABASE"` 执行 initdb 里的 `.sql`，
  所以脚本里的 `CREATE TABLE` 会自动落到 `MYSQL_DATABASE` 指定的库，不需要在 SQL 里写 `USE`；
- `docker-entrypoint-initdb.d` **只在数据卷为空时执行**，改了表结构必须 `down -v`（见 README 第 5 节）。

### 1.3 修 healthcheck

`eclipse-temurin:21-jre` 里没有 curl，而 compose 的 backend healthcheck 用的就是 curl。
不装的表现是：容器 `Up` 但永远 `unhealthy`，而 frontend 又 `depends_on` 后端的 healthy → 前端根本不启动。

```dockerfile
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
```

healthcheck 本身没有删，前端对它的依赖也保留了（并顺手把 `depends_on` 升级成 `condition: service_healthy`）。

### 1.4 新增 prod profile

`blogs-admin/src/main/resources/application-prod.yml`：

| 配置 | 值 | 理由 |
| --- | --- | --- |
| datasource url/username/password | `${SPRING_DATASOURCE_*}`（无默认值） | 缺失即启动失败，而不是带着空密码连库、等第一次查询才报认证错 |
| `spring.sql.init.mode` | `never` | 表由 MySQL 容器首启建好，应用不再建表 |
| `mybatis-plus.configuration.log-impl` | `NoLoggingImpl` | dev 的 `StdOutImpl` 会把每条 SQL + 参数同步写 stdout，既拖慢响应又把文章正文写进日志 |
| `spring.data.redis.host/port` | `${SPRING_DATA_REDIS_HOST:localhost}` 等 | 预留；健康检查已在 `application.yaml` 关闭，未装 Redis 不影响探活 |
| `logging.level.root` | `info` | 生产不打 debug |

`deploy/.env.example` 的 `SPRING_PROFILES_ACTIVE` 已从 `dev` 改为 `prod`。

### 1.5 根目录 `.dockerignore`

排除：`**/target/`、`frontend/dist/`、`frontend/node_modules/`、`.git/`、`.idea/`、`.vscode/`、
`docs/`、`worktrees/`、`.workbuddy/`、`.env`、`**/.env`、`.env.local`、`**/.env.local`、`deploy/.env`、`**/*.log`。

三条关键理由：

1. context 是仓库根，不排除 `target/` + `node_modules/` 每次 build 要传几个 G 给 daemon；
2. `frontend/Dockerfile` 有 `COPY frontend/ .`，本地 `node_modules` 是 **Windows 原生二进制**（esbuild 等），
   覆盖进 linux 镜像后 `npm run build` 直接崩；
3. `frontend/.env.local` 里有 `VITE_USE_MOCK=false` 这类本地配置，不该进镜像（镜像里由 Dockerfile 显式注入）。

> **与施工单的偏差（有意）**：没有整目录排除 `deploy/`。
> 因为 `frontend/Dockerfile` 需要 `COPY deploy/frontend/nginx.conf`，而 Docker 打包 context 时会
> 对被排除的目录直接 `SkipDir`，`!deploy/frontend/nginx.conf` 这类反向规则救不回来，构建会以
> `COPY failed: file not found` 失败。这里只排除 `deploy/.env`，其余 `deploy/*` 合计不到 20 KB，代价可以忽略。

### 1.6 npm ci

`RUN npm ci || npm install` → `RUN npm ci`。
`package-lock.json` 已在仓库里（PHASE 4 一并提交），lock 与 `package.json` 不一致时应当修 lock，
兜底到 `npm install` 只会把「锁文件失效」这个真问题悄悄吞掉，最后镜像里的依赖树和提交的对不上。

本机验证：`npm ci --dry-run` 通过（lock 与 package.json 一致，无 EUSAGE 报错）。

### 1.7 顺带清理

- 删掉 `version: "3.9"`（Compose V2 已废弃该字段，会打 warning）；
- `deploy/README.md` 补齐：前置要求、首次启动、端口、默认管理员、down -v 重建、mysqldump 备份、日志排查（含 9 条常见现象对照表）。

## 2. 施工单之外，另外改的 4 处

这 4 处不改的话，验收会以「看起来能跑但就是不对」的方式失败，所以一并处理。

### 2.1 前端构建期注入 `VITE_USE_MOCK=false`（最重要）

`frontend/src/mock/index.js` 的判断是 `(import.meta.env.VITE_USE_MOCK) !== 'false'` —— **默认开启 mock**。
镜像里没有 `.env.local`（被 .dockerignore 排除），所以照原样构建出来的生产包会走本地 mock：
页面能开、列表有假数据、登录"成功"、发文"成功"，但**一条请求都没到后端**，刷新即消失。

```dockerfile
ARG VITE_USE_MOCK=false
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_USE_MOCK=$VITE_USE_MOCK
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
```

Vite 会把 `process.env` 里 `VITE_` 前缀的变量并入 `import.meta.env`，效果等同 `.env.production`。
实测：`VITE_USE_MOCK=false npm run build` 后，产物里 `grep -c mock` = **0**，整个 mock 模块被 tree-shake 掉了。

### 2.2 backend Dockerfile 去掉 `dependency:go-offline`

原骨架第一步是 `mvn -B dependency:go-offline`。本项目 `blogs-admin` 依赖**同版本号的兄弟模块**
（`com.xdl:blogs-core:0.0.1-SNAPSHOT` 等），go-offline 阶段兄弟模块既没 install 也没有 reactor 产物，
Maven 会去远程仓库找 SNAPSHOT 并失败（maven-dependency-plugin 的已知问题 MDEP-516），构建直接卡在第一步。

改成「各模块 pom → 各模块 src → 一次 `mvn package`」。代价是丢掉依赖层缓存，换来构建能过。
文件末尾留了 BuildKit cache mount 的可选写法（`--mount=type=cache,target=/root/.m2`），需要时再开。

### 2.3 mysql healthcheck 改走 TCP（`-h 127.0.0.1`）

MySQL 初始化阶段 mysqld 以 `--skip-networking` 启动，只有 unix socket。
原来的 `mysqladmin ping -h localhost` 走 socket，会在建表脚本还没跑完时就返回成功 →
backend 抢先启动 → 表还不存在 → `/actuator/health` 长期 DOWN → frontend 因为 `service_healthy` 永远不启动。
改成 `127.0.0.1` 强制走 TCP，TCP 端口在初始化阶段是关闭的，等真正 mysqld 起来才通。
同时 `start_period` 放宽到 60s（首次初始化要建库 + 建 5 张表）。

### 2.4 后端镜像非 root 运行

`groupadd/useradd app` + `--chown=app:app` + `USER app`。容器被攻破时少一分横向移动的余地，代价为零。

## 3. 静态风险清单（Docker 装上后第一批要看的地方）

| 风险 | 处理 | 万一失败的现象 |
| --- | --- | --- |
| `deploy/` 若被整目录排除 → nginx.conf 拷不进去 | 未排除，只排除 `deploy/.env` | `COPY failed: ... nginx.conf: not found` |
| 依赖兄弟模块 SNAPSHOT | 去掉 go-offline | 构建第一步 `Could not resolve dependencies` |
| 镜像无 curl | 运行阶段 apt 装 curl | 容器 Up 但 `(unhealthy)`，前端不启动 |
| 生产包走 mock | 构建期 `VITE_USE_MOCK=false` | 页面有假数据，后端日志一条请求都没有 |
| 建表竞态 | mysql 探活走 TCP + start_period 60s | backend 启动报 `Table 'blogs_db.admin_user' doesn't exist` |
| `.env` 缺项 | compose 里全是 `${VAR}` 无默认值，缺了就报 `variable is not set` | `up` 直接报错退出（这是期望行为） |

## 4. 本机实测（无 Docker 环境下的替代验证）

Docker 不可用，因此把「容器内会发生什么」拆成能在本机复现的三段，逐段真跑：

### 4.1 prod profile 真实启动

模拟 MySQL 容器的 initdb（建空库 → 用 `--database` 执行 schema.sql → 业务账号授权），然后用
`SPRING_PROFILES_ACTIVE=prod` + 环境变量注入数据源启动 jar：

```bash
mvn -Dmaven.compiler.release=17 -Dmaven.test.skip=true -pl blogs-admin -am clean package
# 确认 application-prod.yml 已进包
unzip -l blogs-admin/target/blogs-admin-0.0.1-SNAPSHOT.jar | grep application-prod.yml   # ✅ BOOT-INF/classes/application-prod.yml
```

启动结果：

| 检查 | 结果 |
| --- | --- |
| 激活 profile | `The following 1 profile is active: "prod"` |
| `/actuator/health` | `{"status":"UP"}` HTTP 200 |
| `==>  Preparing` / `==> Parameters` 出现次数 | **0 / 0**（对应验收 2 的核心判据） |
| 建表脚本执行 | 空库 → 5 张表（admin_user / article / category / tag / article_tag） |

### 4.2 prod profile 下的业务链路（12 条断言，全 PASS）

| # | 断言 | 结果 |
| --- | --- | --- |
| 1 | admin 登录（`ADMIN_INIT_PASSWORD` 初始化出来的账号） | ✅ 200 |
| 2 | 返回 role=ADMIN | ✅ |
| 3 | 无 token 访问 `/admin/articles` | ✅ 401 |
| 4 | 新建标签 | ✅ |
| 5 | 新建并发布文章（带 1 个标签） | ✅ |
| 6 | 访客列表能看到该文章 | ✅ |
| 7 | 访客详情 tags=1 | ✅ |
| 8 | 公开详情不含 status | ✅ |
| 9 | 按 tagId 筛选命中 1 篇 | ✅ |
| 10 | 编辑文章不传 tagIds | ✅ 200 |
| 11 | 编辑后标签保留 `tagIds=[1]` | ✅ |
| 12 | 编辑后状态仍为已发布 | ✅ |

### 4.3 前端构建产物 + 反代链路

```bash
VITE_USE_MOCK=false VITE_API_BASE_URL=/api/v1 npm run build   # ✅ built in 7.99s
grep -c mock dist/assets/index-*.js                            # ✅ 0（mock 已被 tree-shake）
```

再用 Node 起一个「静态托管 + SPA 回退 + /api 反代到 8080」的小服务，等价于 nginx.conf 的三条职责：

| 请求 | 结果 |
| --- | --- |
| `GET /` | 200，返回 index.html |
| `GET /article/1`（深链接） | 200，回退到 index.html |
| `GET /api/v1/articles` | 200，返回上一步创建的文章（`total=1`），证明反代路径与后端契约对齐 |

### 4.4 现场清理

`blogs_deploy` 库、临时账号 `blogs@%`、`.smoke/`、`frontend/dist` 全部删除；8080/8081 已释放；
`blogs_db` 零写入。

## 5. 装好 Docker 后的验收清单

```bash
cd deploy
cp .env.example .env          # 改掉 change_me_*（至少：MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD / JWT_SECRET / ADMIN_INIT_PASSWORD）
docker compose up -d --build
docker compose ps             # 期望：mysql healthy、backend healthy、frontend Up
```

| 验收项 | 命令 / 操作 | 期望 |
| --- | --- | --- |
| ① 三容器 Up，mysql+backend healthy | `docker compose ps` | 状态列含 `healthy` |
| ② 日志无 SQL | `docker compose logs backend \| grep -c "==>  Preparing"` | `0` |
| ③ 首页可访问 | 浏览器打开 `http://localhost` | 文章列表页 |
| ④ 能登录后台 | `admin` + `.env` 的 `ADMIN_INIT_PASSWORD` | 进入管理端 |
| ⑤ 发文后访客可见 | 后台新建文章、状态选「已发布」→ 打开首页 | 列表出现该文章 |
| ⑥ 标签链路 | 打 2 个标签 → 首页按标签筛选 → 编辑文章只改标题保存 | 筛选命中；保存后标签仍在 |
| ⑦ down -v 重建 | `docker compose down -v && docker compose up -d --build` | 数据清空、表自动重建、能再次登录 |

### 5.1 实测结果（2026-11-10，Windows 10 + Docker Desktop 引擎 29.9.2，Compose v5.6.1）

环境备注：本机 Docker Hub 直连不通（`auth.docker.io` 超时），通过 `docker.m.daocloud.io`
预拉 5 个基础镜像并 `docker tag` 回原名解决，未改动 daemon.json 与任何 Dockerfile。
首次 `docker compose up -d --build` 实测约 2 分钟完成三镜像构建并全部启动。

| 验收项 | 实测结果 |
| --- | --- |
| ① 三容器 Up，mysql+backend healthy | ✅ `blogs-mysql/blogs-backend (healthy)`、`blogs-frontend Up`；依赖链 mysql→backend→frontend 按 `service_healthy` 顺序启动 |
| ② 日志无 SQL | ✅ `==>  Preparing` 计数 0、`==> Parameters:` 计数 0；启动日志确认 `The following 1 profile is active: "prod"` |
| ③ 首页可访问 | ✅ `http://localhost` 200，SPA 回退 `/login` 200，`/api/v1/articles` 经 Nginx 反代返回正常 JSON |
| ④ 能登录后台 | ✅ `admin` + `.env` 的 `ADMIN_INIT_PASSWORD` 登录返回 `code:200` 与 token；日志同时可见「已创建首个管理员账号: admin」 |
| ⑤ 发文后访客可见 | ✅ 管理端发文（status=1）→ 公开列表命中该标题 |
| ⑥ 标签链路 | ✅ 10 项断言全 PASS：建标签 / 发文带标签 / 公开详情含标签 / `?tagId=` 筛选命中 / 编辑不传 tagIds 标签保留（三态语义）/ 传 `[]` 清空 / 重新绑定生效 / 公开标签列表可见 |
| ⑦ down -v 重建 | ✅ 数据卷销毁后重建：article/tag 计数归 0（admin_user 重新初始化为 1），5 张表（admin_user/article/article_tag/category/tag）自动重建，admin 可再次登录 |

结论：7 条验收全部通过。

排障顺序（出问题先看这里）：

```bash
docker compose logs --tail=100 mysql     # 建表是否报错
docker compose logs --tail=100 backend   # 是否 Access denied / 表不存在
docker compose exec backend curl -s localhost:8080/actuator/health
```

## 6. 遗留与建议

1. **端口暴露**：目前 `3306`/`8080` 都映射到宿主，生产环境应只保留 80 并前置 HTTPS（Caddy 或 Nginx + 证书）；
2. **JWT 密钥轮换**：`.env` 里换 `JWT_SECRET` 会让所有已签发 token 立即失效，属于预期行为，操作前通知用户；
3. **备份**：README 第 6 节给了 `mysqldump` 命令，但没有定时任务，生产建议加 cron + 异地留存；
4. **镜像体积**：后端构建阶段用 `maven:3.9-eclipse-temurin-21`（约 600 MB），可用 BuildKit cache mount 提升重建速度；
5. ~~一次性的验收缺口~~：已于 2026-11-10 在 Docker Desktop（引擎 29.9.2）上补跑全部 7 条验收并回填至 5.1 节，全部通过。
