# 后端设计：数据库表结构 + 端口与 API

> 版本：v1.0（2026-10-30）
> 基线提交：`ed54db5`（main）
> 事实来源：全部字段、端口、配置均来自对当前仓库代码的实际核验
> 配套文档：`docs/API_CONTRACT.md`（接口契约全文）、`docs/AUTH_SETUP.md`（环境与启动）、`docs/BACKEND_HANDOFF_WORKBUDDY.md`（交接与提示词）

---

## 一、系统总览

### 1.1 技术栈（已锁定）

| 项 | 选型 |
| --- | --- |
| 语言 | Java 21 |
| 框架 | Spring Boot 3.4.0 |
| 持久层 | MyBatis-Plus（`mybatis-plus-spring-boot3-starter`） |
| 安全 | Spring Security + JWT（jjwt 0.12.6，HS256） |
| 数据库 | MySQL 8.0，InnoDB，utf8mb4 |
| 缓存 | Redis（可选，MVP 不强制依赖） |
| 构建 | Maven 多模块（根为 pom 聚合） |

### 1.2 模块划分与依赖方向

```
blogs (pom)
├── blogs-model      实体 / DTO / VO
├── blogs-common     Result、ResultCode、BusinessException、GlobalExceptionHandler、JWT
├── blogs-security   SecurityConfig、JwtAuthenticationFilter、RestSecurityExceptionHandler
├── blogs-core       Mapper、Service、Config、Init
└── blogs-admin      Web 入口（BlogsApplication + Controller）
```

依赖方向：`blogs-admin` → `blogs-security` / `blogs-core` → `blogs-common` / `blogs-model`，**不可反向**。

---

## 二、端口与网络拓扑

### 2.1 端口分配

| 服务 | 端口 | 配置位置 | 说明 |
| --- | --- | --- | --- |
| 后端 API | **8080** | `application.yaml` → `server.port` | 无 `context-path`，路径前缀写在 Controller 的 `@RequestMapping` |
| MySQL | 3306 | `application-dev.yml` → `spring.datasource.url` | 库名 `blogs_db` |
| Redis | 6379 | `application-dev.yml` → `spring.data.redis` | 可选，MVP 不依赖 |
| 前端 dev | 5173 | `frontend/vite.config.js` | Vite 默认端口 |

### 2.2 请求链路

```
浏览器 (localhost:5173)
   │  axios baseURL = /api/v1
   ▼
Vite Dev Server (5173)  ── proxy /api ──▶  后端 (localhost:8080)
                                              │
                                              ├─▶ MySQL (3306) blogs_db
                                              └─▶ Redis (6379) 可选
```

- 前端 `axios` 的 `baseURL` 固定为 `/api/v1`
- Vite 把 `/api` 前缀代理到 `http://localhost:8080`，**开发期不触发跨域**
- 后端仍配置了 CORS 放行 `http://localhost:5173`（`blogs.cors.allowed-origins`），供直连场景使用

### 2.3 路径规范

- 后端**未设置** `server.servlet.context-path`
- 所有 Controller 在 `@RequestMapping` 中显式写 `/api/v1/...`
- 例：`@RequestMapping("/api/v1/auth")` + `@PostMapping("/login")` → `POST /api/v1/auth/login`

---

## 三、数据库表结构设计

### 3.1 库级约定

| 项 | 约定 |
| --- | --- |
| 库名 | `blogs_db` |
| 字符集 | `utf8mb4` / `utf8mb4_general_ci` |
| 引擎 | InnoDB |
| 表名 | **单数**（`admin_user` / `article` / `category`），与实体 `@TableName` 严格一致 |
| 主键 | `BIGINT AUTO_INCREMENT`，实体侧 `@TableId(type = IdType.AUTO)` |
| 逻辑删除 | 统一字段 `deleted TINYINT DEFAULT 0`（0 未删 / 1 已删），实体侧 `@TableLogic`，全局配置在 `application.yaml` |
| 时间字段 | `created_at` / `updated_at` 由 `MybatisMetaObjectHandler` 自动填充，**业务代码不手动 set** |
| 物理外键 | **不建**。仅建索引，避免逻辑删除下的外键约束冲突 |
| 命名 | 数据库下划线，Java 驼峰（`map-underscore-to-camel-case: true`） |

> 关于表名：参考文档 `BLOG_DATABASE_SCHEMA.md` 用的是复数（`admin_users` / `articles`），**与实际代码不符**，一律以实际实体为准（单数）。

### 3.2 `admin_user` 后台管理员表

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 管理员 ID |
| `username` | VARCHAR(50) | NOT NULL, UNIQUE | 登录用户名 |
| `password` | VARCHAR(100) | NOT NULL | **bcrypt 哈希**，禁止明文/可逆加密 |
| `nickname` | VARCHAR(50) | NULL | 昵称 |
| `avatar` | VARCHAR(255) | NULL | 头像地址 |
| `role` | VARCHAR(20) | NOT NULL, DEFAULT 'ADMIN' | 角色标识（MVP 仅 `ADMIN`） |
| `status` | TINYINT | NOT NULL, DEFAULT 1 | 1 启用 / 0 禁用 |
| `created_at` | DATETIME | NULL | 创建时间（自动填充） |
| `updated_at` | DATETIME | NULL | 更新时间（自动填充） |
| `deleted` | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引**：`PRIMARY KEY(id)`、`UNIQUE KEY uk_admin_user_username(username)`

**初始化**：`AdminUserInitializer` 在启动时检查该表；表为空且设置了环境变量 `ADMIN_INIT_PASSWORD` 时创建 `admin`（角色 ADMIN、状态启用）。未设置变量则只告警跳过，**不写入任何默认密码**。

### 3.3 `article` 文章表

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 文章 ID |
| `title` | VARCHAR(120) | NOT NULL | 标题 |
| `summary` | VARCHAR(500) | NULL | 摘要/简介 |
| `content_md` | LONGTEXT | NULL | Markdown 正文 |
| `content_html` | LONGTEXT | NULL | 渲染后的 HTML 正文 |
| `ai_summary` | VARCHAR(1000) | NULL | AI 自动摘要（预留，MVP 不生成） |
| `category_id` | BIGINT | NULL | 所属分类 ID |
| `user_id` | BIGINT | NULL | 作者（管理员）ID |
| `status` | TINYINT | NOT NULL, DEFAULT 0 | **0 草稿 / 1 已发布 / 2 已下架** |
| `cover_image` | VARCHAR(255) | NULL | 封面图 URL |
| `view_count` | INT | NOT NULL, DEFAULT 0 | 浏览次数 |
| `is_top` | TINYINT | NOT NULL, DEFAULT 0 | 0 否 / 1 置顶 |
| `created_at` | DATETIME | NULL | 创建时间（自动填充） |
| `updated_at` | DATETIME | NULL | 更新时间（自动填充） |
| `deleted` | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引**：
- `PRIMARY KEY(id)`
- `KEY idx_article_status(status)` — 公开列表只查 `status=1`，命中率高
- `KEY idx_article_category(category_id)` — 按分类筛选
- `KEY idx_article_created(created_at)` — 按时间倒序分页

**状态流转**：

```
草稿(0) ──发布──▶ 已发布(1) ──下架──▶ 已下架(2)
   ▲                  │                  │
   └──────重新编辑─────┴──────重新上架─────┘
```

公开接口只暴露 `status=1`；草稿/下架被公开访问一律返回 `404`（不泄露存在性）。

### 3.4 `category` 分类表

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 分类 ID |
| `name` | VARCHAR(50) | NOT NULL | 分类名称 |
| `parent_id` | BIGINT | NOT NULL, DEFAULT 0 | 父分类 ID，**0 表示顶级** |
| `description` | VARCHAR(255) | NULL | 分类描述 |
| `sort_order` | INT | NOT NULL, DEFAULT 0 | 排序权重，**数值越小越靠前** |
| `created_at` | DATETIME | NULL | 创建时间（自动填充） |
| `updated_at` | DATETIME | NULL | 更新时间（自动填充） |
| `deleted` | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引**：`PRIMARY KEY(id)`、`KEY idx_category_parent(parent_id)`

**层级设计**：自关联 `parent_id`，支持多级；当前 MVP 前端按**扁平列表**消费，树形展示可选。

### 3.5 暂不建的表

| 表 | 原因 |
| --- | --- |
| `tag` / `article_tag` | 标签纳入 MVP 但属**阶段 3**，开发顺序排在文章/分类之后；表结构待审计后设计 |
| `site_settings` | 站点设置 MVP 不支持（2026-10-14 决策），站点信息走前端配置 |

### 3.6 建表脚本现状 ⚠️

| 位置 | 内容 |
| --- | --- |
| `blogs-admin/src/main/resources/db/schema.sql` | **只有 `admin_user`**（dev 启动自动执行的就是这个） |
| `sql/schema.sql`（仓库根，未跟踪） | 三张表齐全，已与实体逐字段核对一致 |

**结论**：dev 启动自动建表只能建出 `admin_user`。要么把 `sql/schema.sql` 的 `article` / `category` 段并入 `db/schema.sql`，要么手动执行一次 `sql/schema.sql`。**不处理会导致文章/分类接口报表不存在。**

---

## 四、API 设计

### 4.1 统一响应包络 `Result<T>`

所有接口（文件流/SSE 除外）返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1721732646000
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务码，与 HTTP 状态码保持一致 |
| `message` | string | 人类可读信息（注意字段名是 **message**，不是 msg） |
| `data` | object/array/null | 业务数据，失败为 `null` |
| `timestamp` | long | 服务端毫秒时间戳 |

### 4.2 错误码

| code | 含义 | 典型场景 |
| --- | --- | --- |
| 200 | 成功 | — |
| 400 | 参数/业务校验失败 | 必填缺失、状态值非法、字段超长 |
| 401 | 未认证 | 无 token、token 过期/无效、用户名或密码错误 |
| 403 | 权限不足 | 非管理员访问 `/admin/**`、账号被禁用 |
| 404 | 资源不存在 | ID 不存在、草稿被公开访问 |
| 429 | 请求过多 | 预留限流，MVP 不依赖 |
| 500 | 服务端内部错误 | 未捕获异常 |

实现方式：Service 层抛 `BusinessException(ResultCode.XXX, "具体信息")`，`GlobalExceptionHandler` 自动把 `code` 映射为 HTTP 状态码。**不要手动构造 `ResponseEntity`。**

### 4.3 分页规范

**请求 Query**：`pageNum`（默认 1，从 1 开始）、`pageSize`（默认 10，最大 100）

**响应 `data`**：

```json
{ "list": [], "total": 45, "pageNum": 1, "pageSize": 10, "pages": 5 }
```

> MyBatis-Plus 的 `IPage` 字段是 `records` / `current` / `size`，**必须转换成上述字段名**，否则前端分页拿不到数据。

### 4.4 认证设计

| 项 | 设计 |
| --- | --- |
| 方式 | 无状态 JWT，`Authorization: Bearer <token>` |
| 算法 | HS256，密钥来自环境变量 `JWT_SECRET`（≥32 字节，缺失则启动失败） |
| 有效期 | 86400 秒（24 小时），MVP 不做刷新 token、不做服务端登出 |
| claims | `sub`=管理员 id、`username`、`role` |
| 携带后 | `JwtAuthenticationFilter` 解析后写入 `SecurityContext`，Controller 用 `@AuthenticationPrincipal JwtPrincipal` 取 |
| 权限 | `/api/v1/auth/login` 公开；`/api/v1/admin/**` 需 `ROLE_ADMIN`；其余需有效 token |
| 公开 GET | `/api/v1/articles/**`、`/api/v1/categories`、`/api/v1/tags` |

### 4.5 接口清单

> 状态说明：✅ 已实现并合并 / ⬜ 待实现 / 🔒 阶段 3
> 路径必须与前端 `frontend/src/api/*.js` 完全一致。

#### 已实现（2 个）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| ✅ POST | `/api/v1/auth/login` | 公开 | 管理员登录，返回 token + userInfo |
| ✅ GET | `/api/v1/auth/me` | 需 JWT | 当前管理员信息，刷新恢复登录态 |

**登录成功 `data`**：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "userInfo": { "id": 1, "username": "admin", "nickname": "系统管理员", "avatar": null, "role": "ADMIN" }
}
```

**登录失败**：`401 用户名或密码错误`（用户不存在与密码错误统一文案，防账号枚举）/ `403 账号已被禁用` / `400` 字段校验信息。

#### 公开接口（3 个，主页核心）

| 方法 | 路径 | 说明 | 关键参数 |
| --- | --- | --- | --- |
| ⬜ GET | `/api/v1/articles` | 分页，**仅 `status=1`**，按 `isTop` 降序 + `created_at` 降序 | `pageNum`、`pageSize`、`categoryId`、`keyword`（标题/摘要模糊） |
| ⬜ GET | `/api/v1/articles/{id}` | 已发布文章详情，**`viewCount` +1**；草稿/下架返回 404 | 路径参数 `id` |
| ⬜ GET | `/api/v1/categories` | 分类列表（扁平数组，非分页），按 `sort_order` 升序 | 无 |

**列表项 `data.list[]` 元素**（**不含** `contentMd` / `contentHtml`，减小传输量）：

```json
{
  "id": 1,
  "title": "Spring Boot 3.4 与 Java 21 最佳实践",
  "summary": "本文将介绍...",
  "categoryId": 2,
  "coverImage": "https://...",
  "viewCount": 128,
  "isTop": 0,
  "createdAt": "2026-10-01 10:00:00"
}
```

**详情 `data`**（含正文）：

```json
{
  "id": 1, "title": "...", "summary": "...",
  "contentMd": "# 标题\n正文...",
  "contentHtml": "<h1>标题</h1>...",
  "categoryId": 2, "coverImage": "https://...",
  "viewCount": 129, "isTop": 0,
  "createdAt": "2026-10-01 10:00:00"
}
```

> ⚠️ **不返回 `publishedAt`**：`Article` 实体无此字段，用 `createdAt` 代替。
> ⚠️ **不返回 `tagIds`**：标签属阶段 3。

#### 管理端接口（9 个，需 JWT + ROLE_ADMIN）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| ⬜ GET | `/api/v1/admin/articles` | 分页，**含草稿/下架**，支持 `status` 筛选 |
| ⬜ GET | `/api/v1/admin/articles/{id}` | 单篇详情（含草稿/下架），编辑回填用 |
| ⬜ POST | `/api/v1/admin/articles` | 创建（可草稿/发布） |
| ⬜ PUT | `/api/v1/admin/articles/{id}` | 更新 |
| ⬜ DELETE | `/api/v1/admin/articles/{id}` | 逻辑删除 |
| ⬜ GET | `/api/v1/admin/categories` | 分类列表（管理端） |
| ⬜ POST | `/api/v1/admin/categories` | 创建分类 |
| ⬜ PUT | `/api/v1/admin/categories/{id}` | 更新分类 |
| ⬜ DELETE | `/api/v1/admin/categories/{id}` | 逻辑删除 |

**文章创建/更新请求体**：

```json
{
  "title": "标题",
  "summary": "摘要",
  "contentMd": "# 标题\n正文...",
  "categoryId": 2,
  "coverImage": "https://...",
  "status": 1
}
```

服务端职责：
- `userId` 从 `JwtPrincipal.id()` 取，**前端不提交**
- `createdAt` / `updatedAt` 自动填充，**不手动 set**
- `viewCount` 默认 0、`isTop` 默认 0，服务端兜底
- `status` 只允许 0/1/2，非法值返回 `400`
- 更新已发布文章时**不重置**创建时间

**分类创建/更新请求体**：

```json
{ "name": "Java 后端", "parentId": 0, "description": "描述", "sortOrder": 10 }
```

#### 阶段 3 接口（5 个，预留不实现）

| 方法 | 路径 |
| --- | --- |
| 🔒 GET | `/api/v1/tags` |
| 🔒 GET | `/api/v1/admin/tags` |
| 🔒 POST | `/api/v1/admin/tags` |
| 🔒 PUT | `/api/v1/admin/tags/{id}` |
| 🔒 DELETE | `/api/v1/admin/tags/{id}` |

#### MVP 不支持

- `GET/PUT /api/v1/site/settings`、`GET/PUT /api/v1/admin/site/settings` — 站点设置不做，站点信息写死前端

---

## 五、时间格式规范 ✅ 已定稿（`yyyy-MM-dd HH:mm:ss`，不带时区）

### 5.1 问题

- 契约文档示例写的是 `2026-10-01 10:00:00`（空格分隔）
- 但**当前 Jackson 未做任何日期格式配置**
- Spring Boot 3 + `JavaTimeModule` 默认把 `LocalDateTime` 序列化为 **ISO-8601 带 T**：`2026-10-01T10:00:00`

如果前端按空格格式解析，会拿不到正确时间。

### 5.2 建议方案

统一为 `yyyy-MM-dd HH:mm:ss`，在 `blogs-common` 或 `blogs-admin` 加全局 Jackson 配置：

```java
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class,
                    new LocalDateTimeSerializer(FORMATTER));
            builder.deserializerByType(LocalDateTime.class,
                    new LocalDateTimeDeserializer(FORMATTER));
        };
    }
}
```

> 注意：`spring.jackson.date-format` 只作用于 `java.util.Date`，**对 `LocalDateTime` 无效**，必须自定义 serializer。

### 5.3 前端侧已落地（2026-10-30 更新）

前端不再依赖浏览器原生 `new Date(string)` 解析，统一走 `frontend/src/utils/datetime.js`：

- 用正则拆解 `yyyy-MM-dd[ T]HH:mm[:ss]` 后手动构造本地 `Date`，**空格格式与 ISO 格式都能正确解析**
- 兼容毫秒时间戳、`Date` 实例、空值（解析失败统一显示 `-`）
- 背景：`new Date("2026-10-01 10:00:00")` 在 Chrome 可用，但 Safari / iOS WebView 按 ECMAScript 规范只保证解析 ISO 8601（带 T），会得到 `Invalid Date`，页面显示 `NaN-NaN-NaN`

**结论：后端保持 `yyyy-MM-dd HH:mm:ss` 输出、不带时区偏移即可，前端已完全兼容，两个方向都不需要再改。**

---

## 六、待决策项

| # | 事项 | 建议 | 影响 |
| --- | --- | --- | --- |
| 1 | `contentHtml` 由谁生成 | **后端渲染**（`commonmark-java`），前端只提交 `contentMd` | 决定是否引入渲染依赖 |
| 2 | 分类删除时文章 `categoryId` 如何处理 | **置空**（`categoryId = null`），不阻止删除 | 影响删除逻辑与前端提示 |
| 3 | 分类是否需要树形接口 | MVP 先扁平，前端自行组装 | 影响 `GET /categories` 响应结构 |
| 4 | `publishedAt` 是否补字段 | 暂不补，用 `createdAt` | 影响详情/列表 VO |
| 5 | 时间格式统一方案 | 按第五节加 Jackson 配置 | 影响全部含时间的响应 |
| 6 | 全表建表脚本落地方式 | 并入 `db/schema.sql` 让 dev 自动建全表 | 影响本地启动能否一次跑通 |

---

## 七、落地检查清单

| # | 检查项 | 通过标准 |
| --- | --- | --- |
| 1 | 分页插件已配置 | `MybatisPlusConfig` 含 `PaginationInnerInterceptor(DbType.MYSQL)` |
| 2 | 三张表已建 | `SHOW TABLES` 含 `admin_user`、`article`、`category` |
| 3 | 时间格式统一 | 响应中时间为 `yyyy-MM-dd HH:mm:ss`，无 `T` |
| 4 | 公开列表过滤草稿 | 草稿不出现在 `GET /api/v1/articles` |
| 5 | 草稿公开访问 404 | `GET /api/v1/articles/{草稿id}` → 404 |
| 6 | 阅读量自增 | 连续两次详情请求 `viewCount` 递增 |
| 7 | 管理端鉴权生效 | 无 token 访问 `/api/v1/admin/**` → 401；非 ADMIN → 403 |
| 8 | 逻辑删除生效 | DELETE 后 `deleted=1`，列表不可见，数据仍在库 |
| 9 | 包络一致 | 所有响应含 `code`/`message`/`data`/`timestamp` |
| 10 | 前端可直连 | `.env.local` 设 `VITE_USE_MOCK=false` 后全流程可用 |
