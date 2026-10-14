# 前后端集成联调方案（草案）

> **状态：草案**。依赖 `docs/API_CONTRACT.md`（接口契约草案）。契约冻结前，本方案用于指导本地联调顺序与验收；契约冻结后按冻结版执行。
> 适用阶段：阶段 0 审计完成后、前端 mock 替换为真实接口的联调窗口。

---

## 1. 目标与范围

在本地打通「管理员登录 → 发文 → 公开阅读」核心链路，验证前后端接口对齐、错误处理与部署骨架可用。

**范围内**
- 后端本地启动（Maven 多模块 Spring Boot）
- 前端本地开发（Vite dev server）通过 proxy 调用后端
- 契约草案核对（`API_CONTRACT.md` ↔ 后端真实路由）
- 主链路验收 + 异常路径验收

**范围外（本阶段不做）**
- 修改 `frontend/`、`blogs-*` 业务代码（由 codex / claude 负责）
- 生产环境部署
- AI 问答（后续阶段）

---

## 2. 本地架构与端口约定

```
浏览器
  │  http://localhost:5173        （Vite dev server，前端）
  ▼
frontend (Vite dev)
  │  /api/*  →  proxy → http://localhost:8080
  ▼
backend (Spring Boot, :8080)
  │  JDBC
  ▼
MySQL 8.0 (:3306, 库 blogs_db)
```

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| 前端 Vite dev | `5173` | `npm run dev`；通过 proxy 访问后端 |
| 后端 Spring Boot | `8080` | 已确认 `application.yaml` 端口 8080 |
| MySQL | `3306` | 本地或 Docker；dev 配置库名 `blogs_db` |
| Redis | `6379` | **可选**：dev 配置已写 Redis，但 MVP 不强制依赖；若后端启动报 Redis 连接错误，按 4.1 节处理 |
| 生产（Docker Compose） | 前端 `80` / 后端 `8080` / MySQL `3306` | 见 `deploy/` |

---

## 3. 联调前置条件

- [ ] JDK 21 + Maven（`mvnw` 已包含）
- [ ] Node.js（Vite 前端，codex 脚手架就位）
- [ ] MySQL 8.0 可用，`blogs_db` 可创建
- [ ] 阶段 0 审计已产出（`docs/PROJECT_AUDIT.md`、`docs/KNOWN_ISSUES.md`），或至少后端可正常启动
- [ ] 数据库 schema / 迁移与实体一致（表名 `article`、`category`；`deleted` 逻辑删除字段）

---

## 4. 本地联调顺序（4 步）

### 步骤 1：后端启动

**方式 A：本地 Maven 启动（推荐用于联调）**

```bash
# 1. 创建数据库（若不存在）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS blogs_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 初始化表结构与首个管理员（依赖 claude 提供 SQL/迁移；当前仓库无迁移脚本 → 阻塞项 B1）

# 3. 启动
cd Blogs_java
./mvnw -pl blogs-admin -am spring-boot:run
```

**方式 B：Docker Compose 启动（见 `deploy/README.md`）**

启动后自检：
- 访问健康检查端点（见第 6 节）返回正常；
- 控制台无异常堆栈、无 SQL 错误。

> 注意：`application-dev.yml` 当前配置 `localhost:3306` + `root/123456`，且已配置 Redis。若本地无 Redis，后端启动可能因 Redis 连接失败而退出——这是**已知待确认项**（见第 9 节 K1），联调前由 claude 确认是否移除/放宽 Redis 依赖。

### 步骤 2：契约核对（claude 审计后执行）

以 `docs/API_CONTRACT.md` 为基准，对照后端真实路由：

- [ ] 逐条核对 6.1/6.2 接口路径、方法、请求/响应字段是否一致；
- [ ] 核对统一响应包络与分页字段；
- [ ] 核对错误码（401/403/404/400/500）；
- [ ] 核对认证方案（JWT vs Session，C1）；
- [ ] 核对标签/站点设置是否实现（C2/C3）；
- [ ] 差异登记到 `docs/KNOWN_ISSUES.md`，契约状态由「待审计确认」改为「已冻结」。

### 步骤 3：前端替换 mock（codex 执行）

- 前端当前 mock 层**明确标注**，替换为 axios 真实调用；
- 统一在 axios 拦截器处理 `Result<T>` 包络与错误码（401 跳登录、403 提示、404 提示、400 展示 message）；
- 先替换「公开文章列表/详情」「分类列表」等只读链路，验证 proxy 通；
- 再替换「管理员登录」「文章 CRUD」链路，验证鉴权；
- 未冻结/阻塞接口（标签、站点设置）前端隐藏入口或保留 mock 并标注。

### 步骤 4：主链路验证

按第 7 节验收清单逐项执行，全部通过后向扣子汇报，进入 M3 里程碑判定。

---

## 5. `/api` 前缀与 Vite proxy / CORS 约定

### 5.1 API 前缀

- 后端统一以 `/api/v1` 为接口前缀（草案约定，参考 `API_SPEC.md`；前缀由后端 context-path 还是 Controller 路径实现，**待审计确认** C7）。
- 前端所有请求使用相对路径 `/api/v1/...`，**不写死** `localhost:8080`，保证 dev/prod 一致。

### 5.2 Vite dev proxy（前端 `vite.config.js` 骨架约定）

```js
// frontend/vite.config.js（由 codex 维护，此处为约定）
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

> 通过 proxy 转发时同源，**不需要后端开启 CORS**。

### 5.3 CORS（仅当直连后端时需要）

若联调中前端**绕过 proxy** 直连 `http://localhost:8080`，后端需允许前端 origin（占位约定，待审计确认）：

```
允许来源: http://localhost:5173
允许方法: GET, POST, PUT, DELETE, OPTIONS
允许头: Content-Type, Authorization
允许凭证: false（JWT 场景）
```

> 推荐优先使用 proxy，避免在生产/本地维护两套 CORS 配置。

---

## 6. 健康检查

| 端点 | 说明 | 状态 |
| --- | --- | --- |
| `GET /actuator/health` | Spring Boot Actuator（需引入 `spring-boot-starter-actuator`） | 待审计确认 C9 |
| `GET /api/v1/health` | 自定义健康接口（若后端实现） | 待审计确认 C9 |

**联调自检命令：**

```bash
curl -s http://localhost:8080/actuator/health   # 期望 {"status":"UP"}
```

> 若后端未引入 actuator 也无自定义健康接口，联调时以「后端端口可响应 + 日志正常」作为降级自检，并登记到待确认项。

---

## 7. 验收清单

### 7.1 主链路（必须通过）

| # | 场景 | 操作 | 期望结果 |
| --- | --- | --- | --- |
| A1 | 管理员登录成功 | `POST /api/v1/auth/login`（正确账号密码） | 返回 200 + token + 管理员信息 |
| A2 | 创建文章（草稿） | 登录后 `POST /api/v1/admin/articles`，`status=0` | 返回 200 + 文章 id；公开列表**不可见** |
| A3 | 发布文章 | `PUT /api/v1/admin/articles/{id}`，`status=1` | 返回 200；公开列表可见、详情可读 |
| A4 | 公开阅读列表 | `GET /api/v1/articles` | 仅返回 `status=1` 文章，分页字段正确 |
| A5 | 公开阅读详情 | `GET /api/v1/articles/{id}`（已发布） | 返回 200 + 正文（md 或 html 按契约） |
| A6 | 文章管理列表 | `GET /api/v1/admin/articles` | 含草稿/发布/下架，可按 status 筛选 |
| A7 | 分类创建/列表 | `POST + GET /api/v1/admin/categories`、`GET /api/v1/categories` | 管理端创建后公开端可见 |
| A8 | 更新/删除文章 | `PUT / DELETE /api/v1/admin/articles/{id}` | 更新生效；删除为逻辑删除，公开端消失 |

### 7.2 异常与安全路径（必须通过）

| # | 场景 | 操作 | 期望结果 |
| --- | --- | --- | --- |
| B1 | 草稿访问控制 | 公开 `GET /api/v1/articles/{id}` 访问草稿/下架文章 | 返回 **404**（不泄露存在性） |
| B2 | 未认证访问管理端 | 不带 token 访问 `/api/v1/admin/**` | 返回 **401** |
| B3 | 无效/过期 token | 伪造或过期 token 访问管理端 | 返回 **401**，前端跳登录 |
| B4 | 权限不足 | 非管理员 token 访问管理端 | 返回 **403**（MVP 单管理员，理论上无此场景，仍须校验） |
| B5 | 资源不存在 | 访问不存在的文章/分类 id | 返回 **404** |
| B6 | 参数错误 | 缺必填字段 / `status` 传 3 / `pageSize` 传 200 | 返回 **400** 且 message 可读 |
| B7 | 登录失败 | 错误密码 / 不存在用户 | 返回 401（建议），message 不泄露“用户不存在/密码错误”差异 |
| B8 | 重复 slug/名称 | 创建重复分类名或文章 slug | 返回 **400** 业务错误 |
| B9 | 逻辑删除 | 删除后管理端列表默认不含已删记录 | `deleted=1` 记录不出现（MyBatis-Plus `@TableLogic`） |

### 7.3 前端错误处理核对（codex 侧）

- [ ] axios 统一解包 `Result<T>`；
- [ ] 401 → 清理登录态并跳转登录页；
- [ ] 403 → 提示无权限；
- [ ] 404 → 提示资源不存在/文章未发布；
- [ ] 400 → 展示后端 `message`；
- [ ] 500 → 通用错误提示，不展示堆栈/内部细节；
- [ ] 网络超时/断连 → 友好提示。

---

## 8. 失败排查速查

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| 前端请求 404 | proxy 未配置 / 后端无该路由 | 检查 Vite proxy；对照契约核对路径 |
| 前端请求 502 | 后端未启动 / 端口不一致 | 检查 8080 端口与日志 |
| CORS 报错 | 前端直连后端 | 改用 proxy；或后端配置 CORS（5.3） |
| 后端启动失败（Redis） | dev 配置强制连接 Redis | claude 确认移除/放宽 Redis 依赖（K1） |
| 登录成功但接口 403 | 角色校验未过 | 检查管理员角色字段与授权配置（待审计） |
| 数据查不到 | 表名/字段与实体不一致 | 以真实迁移为准，登记差异 |

---

## 9. 风险与待确认项

| 编号 | 事项 | 负责方 | 说明 |
| --- | --- | --- | --- |
| K1 | Redis 依赖 | claude | `application-dev.yml` 已配 Redis；MVP 不强制依赖，审计后确认是否注释/移除 |
| K2 | 数据库迁移/初始化脚本缺失 | claude | 仓库当前无 SQL/迁移；首启需提供建表与首个管理员初始化方式 |
| K3 | 后端无 Controller | claude | 阶段 1 实现接口前，契约冻结依赖审计结论 |
| K4 | 前端工程就位 | codex | Vite 脚手架完成后按 5.2 proxy 约定配置 |
| K5 | 契约未冻结 | 全体 | 冻结前前端保留 mock；联调只在已冻结接口上替换 |

---

## 10. 与部署（`deploy/`）的衔接

- 本地联调通过后，用 `deploy/docker-compose.yml` 整栈启动（MySQL + backend + frontend/Nginx）；
- 生产形态与本地 dev 的差异：前端从 Vite dev 变为 Nginx 静态托管 + `/api` 反代到后端容器；
- 端口/环境变量通过 `deploy/.env`（由 `.env.example` 复制）配置，真实密钥不入库。

---
文档版本：0.1（草案）｜维护人：编程专家（前后端联调）｜创建日期：2026-10-14
