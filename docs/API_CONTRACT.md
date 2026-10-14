# 博客系统 API 接口契约（草案）

> **文件状态：草案（未冻结）**
> 本文件是前后端联调前的接口契约**草案**，**不是已实现接口的事实描述**。除已决策项外，接口统一标注状态「待审计确认」。
> 冻结条件：claude 完成阶段 0 审计（`docs/PROJECT_AUDIT.md`），并与真实后端代码逐一对齐后，本契约才能冻结为单一事实来源（看板里程碑 M2）。
>
> **决策记录（2026-10-14，负责人已确认）**
> - C1 认证：采用**无状态 JWT**（`Authorization: Bearer <token>`）。
> - C2 标签：**纳入 MVP**，开发顺序排在文章/分类之后（计划中-阶段3）；后端需补 `article_tag` 关联，表结构以 claude 审计后设计为准；前端保留入口。
> - C3 站点设置：**首版不支持**，不建 `site_settings` 表/接口；站点名称等走前端配置。
>
> 参考材料（均**不得当作事实**）：
> - `docs/API_SPEC.md` —— 仅提供接口方向参考，其内容与当前代码一致性待审计；
> - `docs/BLOG_DATABASE_SCHEMA.md` —— 数据库**参考方案**，实际表名/字段以真实代码与迁移为准；
> - 实际后端代码（`blogs-*` 模块）—— 已确认事实：Spring Boot 3.4 / Java 21 / MyBatis-Plus，现有实体 `Article`、`Category`，**当前无 Controller、无 Tag/AdminUser/SiteSettings 实体**。

---

## 1. 通用约定

| 项 | 约定 | 状态 |
| --- | --- | --- |
| Base URL | `/api/v1`（参考 `API_SPEC.md`） | 待审计确认 |
| 请求格式 | `Content-Type: application/json; charset=UTF-8` | 待审计确认 |
| 认证方式 | `Authorization: Bearer <JWT>`（无状态 JWT） | 已决策（2026-10-14） |
| 管理端鉴权 | 必须由**服务端**强制认证与授权，前端守卫/隐藏按钮不算安全措施 | 已决策（2026-10-14） |
| 时间格式 | ISO-8601 / `yyyy-MM-dd HH:mm:ss`（待审计确认） | 待审计确认 |

---

## 2. 统一响应包络 `Result<T>`

除文件流/SSE 外，所有接口返回统一 JSON：

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
| `code` | int | 业务/HTTP 状态码，`200` 成功；非 200 见第 4 节错误码 |
| `message` | string | 人类可读信息；失败时为可展示的错误原因 |
| `data` | object/array/null | 业务数据；失败时为 `null` |
| `timestamp` | long | 服务端毫秒时间戳 |

> **状态：待审计确认**。响应包络字段名（`code/message/data/timestamp`）参考 `API_SPEC.md`；若后端已有 `Result<T>` 实现，以其为准。

---

## 3. 分页规范

所有列表接口使用统一分页：

**请求参数（Query）**

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `pageNum` | int | 1 | 当前页码，从 1 开始 |
| `pageSize` | int | 10 | 每页条数，最大 100 |

**响应 `data` 结构**

```json
{
  "list": [],
  "total": 45,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 5
}
```

> **状态：待审计确认**。分页参数与响应字段参考 `API_SPEC.md`；若后端采用 MyBatis-Plus `IPage` 分页，字段映射待审计后统一。

---

## 4. 错误码

| code | 含义 | 典型场景 |
| --- | --- | --- |
| 200 | 成功 | — |
| 400 | 参数校验失败 / 业务校验失败 | 必填字段缺失、状态值非法、slug 重复 |
| 401 | 未认证 | 未携带 token、token 过期/无效 |
| 403 | 权限不足 | 非管理员访问 `/admin/**` |
| 404 | 资源不存在 | 文章/分类/标签 ID 不存在，或草稿被公开访问 |
| 429 | 请求过多 | 预留限流（当前 MVP 不依赖） |
| 500 | 服务端内部错误 | 未捕获异常 |

**错误响应示例：**

```json
{
  "code": 404,
  "message": "文章不存在或未发布",
  "data": null,
  "timestamp": 1721732646000
}
```

> **状态：待审计确认**。错误码表参考 `API_SPEC.md`；具体业务错误信息由后端统一维护，前端只展示 `message` 并做分类处理（401 跳登录、403 提示无权限、404 提示资源不存在、400 提示参数错误）。

---

## 5. 认证方式（已决策：无状态 JWT）

- **登录**：`POST /api/v1/auth/login` 成功后返回 JWT `token`。
- **携带方式**：`Authorization: Bearer <token>`（无状态 JWT，2026-10-14 已决策）。
- **管理端接口**：除登录外，所有 `/api/v1/admin/**` 均需有效 token；服务端校验角色为管理员，否则返回 `403`。
- **登出/刷新**：暂按不实现处理（MVP 单管理员场景）；token 有效期、存储与续期策略**待审计确认**。

> 实现说明：当前后端仅引入 `jjwt` 依赖，无登录/认证实现；登录接口路径与字段仍待阶段 0 审计后与真实实现对齐。

---

## 6. 接口清单

> 除特别说明外，所有接口状态均为「**待审计确认**」；标注「已决策」「计划中」「MVP 不支持」的条目状态以标注为准。

### 6.1 公开接口（无需认证）

#### 6.1.1 文章列表

| 项 | 值 |
| --- | --- |
| 方法/路径 | `GET /api/v1/articles` |
| 权限 | 公开 |
| 说明 | 分页获取**已发布**文章列表（`status=1`）；支持分类/标签/关键词筛选 |
| 状态 | 待审计确认 |

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNum` / `pageSize` | int | 否 | 分页参数，见第 3 节 |
| `categoryId` | long | 否 | 按分类筛选 |
| `tagId` | long | 否 | 按标签筛选（标签计划中-阶段3，见 6.1.4） |
| `keyword` | string | 否 | 标题/摘要模糊搜索 |

**响应 `data.list[]` 元素（列表项，不含正文）**

```json
{
  "id": 1,
  "title": "Spring Boot 3.4 与 Java 21 最佳实践",
  "summary": "本文将介绍如何基于 Spring Boot 3.4 快速搭建后台服务...",
  "categoryId": 2,
  "coverImage": "https://...",
  "viewCount": 128,
  "isTop": 0,
  "createdAt": "2026-10-01 10:00:00",
  "publishedAt": "2026-10-01 10:00:00"
}
```

> 列表项应**排除** `contentMd/contentHtml`；字段是否含 `tagIds` 待审计确认。

#### 6.1.2 文章详情

| 项 | 值 |
| --- | --- |
| 方法/路径 | `GET /api/v1/articles/{id}` |
| 权限 | 公开 |
| 说明 | 获取**已发布**文章详情；草稿/下架文章公开访问返回 `404`（不泄露存在性） |
| 状态 | 待审计确认 |

**响应 `data`**

```json
{
  "id": 1,
  "title": "Spring Boot 3.4 与 Java 21 最佳实践",
  "summary": "...",
  "contentMd": "# 标题\n正文...",
  "contentHtml": "<h1>标题</h1>...",
  "categoryId": 2,
  "coverImage": "https://...",
  "viewCount": 128,
  "isTop": 0,
  "tagIds": [1, 3],
  "createdAt": "2026-10-01 10:00:00",
  "publishedAt": "2026-10-01 10:00:00"
}
```

> 公开接口是否返回 `contentMd`（或仅 `contentHtml`）**待审计确认**；`tagIds` 依赖标签表实现（计划中-阶段3，见 6.1.4）。

#### 6.1.3 分类列表

| 项 | 值 |
| --- | --- |
| 方法/路径 | `GET /api/v1/categories` |
| 权限 | 公开 |
| 说明 | 获取启用分类列表；`Category.parentId=0` 为顶级，支持层级（扁平列表或树形**待审计确认**） |
| 状态 | 待审计确认 |

**响应 `data`**

```json
[
  {
    "id": 1,
    "name": "Java 后端",
    "parentId": 0,
    "description": "Java 生态与 Spring 体系知识分享",
    "sortOrder": 10
  }
]
```

#### 6.1.4 标签列表

| 项 | 值 |
| --- | --- |
| 方法/路径 | `GET /api/v1/tags` |
| 权限 | 公开 |
| 说明 | 获取全部标签列表 |
| 状态 | **计划中-阶段3**：纳入 MVP，开发顺序排在文章/分类之后；当前后端无 `Tag` 实体/表，后端需补 `article_tag` 关联（表结构以 claude 审计后设计为准）。契约冻结前前端保留入口、可使用标注的 mock |

**响应 `data`**

```json
[
  {
    "id": 1,
    "name": "Spring",
    "slug": "spring"
  }
]
```

#### 6.1.5 站点公开信息（MVP 不支持）

| 项 | 值 |
| --- | --- |
| 方法/路径 | `GET /api/v1/site/settings` |
| 权限 | 公开 |
| 说明 | 返回站点名称、简介、访问地址等**非敏感**公开配置（`site_name`、`site_description`、`site_url`） |
| 状态 | **MVP 不支持**（2026-10-14 决策）：首版不做站点设置，不建 `site_settings` 表/接口；站点名称、简介等走前端配置。本条目仅作记录，不进入实现 |

**响应 `data`（仅作记录，不实现）**

```json
{
  "siteName": "我的个人博客",
  "siteDescription": "记录学习过程，分享兴趣爱好",
  "siteUrl": "https://example.com"
}
```

---

### 6.2 管理端接口（需 JWT，服务端强制鉴权）

> 所有 `/api/v1/admin/**` 未认证返回 `401`，非管理员返回 `403`。未特别标注的条目状态均为「待审计确认」。

#### 6.2.1 管理员登录

| 项 | 值 |
| --- | --- |
| 方法/路径 | `POST /api/v1/auth/login` |
| 权限 | 公开（但仅限管理员账号；MVP 不做开放注册） |
| 说明 | 管理员登录，成功返回 JWT token 与管理员基本信息（认证方案已决策：无状态 JWT） |
| 状态 | 待审计确认（认证方案已决策，接口实现待审计） |

**请求体**

```json
{
  "username": "admin",
  "password": "raw_password"
}
```

**响应 `data`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userInfo": {
    "id": 1,
    "username": "admin",
    "nickname": "系统管理员",
    "avatar": "https://...",
    "role": "ADMIN"
  }
}
```

> 登录失败（用户名不存在/密码错误）统一返回 `400` 或 `401`（**待审计确认**，建议 `401` 且 message 不区分“用户不存在/密码错误”，避免账号枚举）；密码必须服务端哈希存储（bcrypt/Argon2），禁止明文。

#### 6.2.2 当前管理员信息

| 项 | 值 |
| --- | --- |
| 方法/路径 | `GET /api/v1/auth/me` |
| 权限 | 需登录 |
| 说明 | 获取当前登录管理员信息（用于刷新用户态、判断登录态） |
| 状态 | 待审计确认（可选接口，前端可仅依赖本地 token 判断） |

#### 6.2.3 文章管理 CRUD

| 方法/路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/v1/admin/articles` | 管理员 | 文章列表（**含草稿/下架**，按 `status` 筛选、分页） |
| `GET /api/v1/admin/articles/{id}` | 管理员 | 文章详情（含草稿/下架） |
| `POST /api/v1/admin/articles` | 管理员 | 创建文章（可草稿/发布） |
| `PUT /api/v1/admin/articles/{id}` | 管理员 | 更新文章（标题/正文/分类/标签/封面/状态等） |
| `DELETE /api/v1/admin/articles/{id}` | 管理员 | 删除文章（**逻辑删除**，`deleted=1`） |

状态：全部**待审计确认**。

**创建/更新请求体示例（POST/PUT）**

```json
{
  "title": "Spring Boot 3.4 与 Java 21 最佳实践",
  "summary": "摘要...",
  "contentMd": "# 标题\n正文...",
  "categoryId": 2,
  "coverImage": "https://...",
  "tagIds": [1, 3],
  "status": 1
}
```

> - `status` 取值范围 `0/1/2`（草稿/已发布/已下架），非法值返回 `400`；
> - `contentHtml` 是否由后端渲染生成（而非前端提交）**待审计确认**；
> - `tagIds` 依赖标签表实现（计划中-阶段3，见 6.1.4）；
> - 创建时后端应写入 `userId`（当前登录管理员）与 `createdAt`，前端不提交；
> - 更新已发布文章时不应无条件重置 `publishedAt`（参考方案约定，待审计确认）。

**列表响应 `data`**（分页结构见第 3 节）

```json
{
  "list": [
    {
      "id": 1,
      "title": "...",
      "categoryId": 2,
      "status": 1,
      "viewCount": 128,
      "isTop": 0,
      "createdAt": "2026-10-01 10:00:00",
      "updatedAt": "2026-10-02 12:00:00"
    }
  ],
  "total": 45,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 5
}
```

#### 6.2.4 分类管理 CRUD

| 方法/路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/v1/admin/categories` | 管理员 | 分类列表（含层级，含排序） |
| `POST /api/v1/admin/categories` | 管理员 | 创建分类 |
| `PUT /api/v1/admin/categories/{id}` | 管理员 | 修改分类 |
| `DELETE /api/v1/admin/categories/{id}` | 管理员 | 删除分类（**逻辑删除**；删除策略：文章 `categoryId` 置空还是禁止删除，**待审计确认**） |

状态：全部**待审计确认**。

**创建/更新请求体示例**

```json
{
  "name": "Java 后端",
  "parentId": 0,
  "description": "Java 生态与 Spring 体系知识分享",
  "sortOrder": 10
}
```

> 分类删除若影响已有文章（`category_id` 外键引用），行为需审计后明确（参考方案建议删除分类时文章 `category_id` 置空；实际以代码为准）。

#### 6.2.5 标签管理 CRUD

| 方法/路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/v1/admin/tags` | 管理员 | 标签列表 |
| `POST /api/v1/admin/tags` | 管理员 | 创建标签 |
| `PUT /api/v1/admin/tags/{id}` | 管理员 | 修改标签 |
| `DELETE /api/v1/admin/tags/{id}` | 管理员 | 删除标签 |

状态：**计划中-阶段3**——纳入 MVP，开发顺序排在文章/分类之后（见 6.1.4）；当前后端无标签表/实体，接口字段待 claude 审计后设计确认。

#### 6.2.6 站点设置（MVP 不支持）

| 方法/路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/v1/admin/site/settings` | 管理员 | 获取站点配置（全部，含仅后台可见项） |
| `PUT /api/v1/admin/site/settings` | 管理员 | 更新站点配置 |

状态：**MVP 不支持**（2026-10-14 决策）——首版不实现（见 6.1.5），本节不进入实现；站点名称等走前端配置。如后续纳入，站点配置中**不得存放数据库密码、JWT 密钥、第三方 Key 等机密**。

---

## 7. 数据模型（草案，供前端类型定义参考）

> 字段以真实实体为准；当前已从代码确认 `Article`、`Category` 实体字段，其余为草案。

### 7.1 Article（已确认实体字段 + 草案补充）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 主键 |
| `title` | string | 标题 |
| `summary` | string | 摘要 |
| `contentMd` | string | Markdown 正文 |
| `contentHtml` | string | 渲染后 HTML（是否由后端渲染待确认） |
| `categoryId` | long | 分类 ID |
| `userId` | long | 作者用户 ID（服务端写入） |
| `status` | int | 0=草稿，1=已发布，2=已下架 |
| `coverImage` | string | 封面 URL |
| `viewCount` | int | 浏览次数 |
| `isTop` | int | 0=普通，1=置顶 |
| `createdAt` / `updatedAt` | datetime | 创建/更新时间 |
| `tagIds` | long[] | 草案补充，依赖标签表（计划中-阶段3） |

### 7.2 Category（已确认实体字段）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 主键 |
| `name` | string | 分类名称 |
| `parentId` | long | 父分类 ID，0=顶级 |
| `description` | string | 描述 |
| `sortOrder` | int | 排序权重，越小越靠前 |
| `createdAt` / `updatedAt` | datetime | 创建/更新时间 |

### 7.3 Tag / AdminUser / SiteSettings

- **Tag**：`id`、`name`、`slug`、`createdAt`、`updatedAt`（草案；**计划中-阶段3**，表结构以 claude 审计后设计为准）
- **AdminUser**：`id`、`username`、`nickname`、`avatar`、`role`（登录返回用；当前无实体，认证已决策为无状态 JWT，字段待审计）
- **SiteSettings**：**MVP 不支持**（2026-10-14 决策），不进入实现

---

## 8. 文章状态枚举（已从代码确认）

| 值 | 含义 |
| --- | --- |
| 0 | 草稿（仅管理端可见） |
| 1 | 已发布（公开可见） |
| 2 | 已下架（仅管理端可见） |

**草稿/下架访问控制约定：**
- 公开列表/详情只暴露 `status=1`；
- 草稿/下架只能通过 `/api/v1/admin/**` 访问；
- 公开详情访问草稿/下架统一返回 `404`（避免泄露资源存在性）。

---

## 9. 待确认项汇总（供 claude 阶段 0 审计后核对）

> C1–C3 已于 **2026-10-14** 由负责人决策；C4–C9 仍待审计确认。

| 编号 | 问题 | 影响 |
| --- | --- | --- |
| C1 | ✅ 已决策：无状态 JWT；token 有效期/续期策略仍待审计 | 前端登录态处理方式（Bearer JWT） |
| C2 | ✅ 已决策：标签纳入 MVP（计划中-阶段3）；`article_tag` 表结构待 claude 设计 | 标签接口保留，开发排在文章/分类之后 |
| C3 | ✅ 已决策：站点设置 MVP 不支持 | 不建表/接口，站点名称走前端配置 |
| C4 | 后端渲染 `contentHtml` 还是前端渲染 Markdown | 详情接口字段与前端渲染方案 |
| C5 | 分类删除策略（置空文章分类 vs 禁止删除） | 管理端交互与错误码 |
| C6 | 统一响应包络/分页字段是否与后端 `Result<T>`、MyBatis-Plus `IPage` 一致 | 前端 axios 拦截器与类型定义 |
| C7 | `/api/v1` 前缀是否由后端统一配置（context-path 或 Controller 路径） | Vite proxy 与部署反向代理 |
| C8 | 管理员表结构、密码哈希算法、首个管理员初始化方式 | 登录与部署初始化 |
| C9 | 健康检查端点：`/actuator/health` 或自定义 `/api/v1/health` | 部署探活与联调自检 |

---

## 10. 冻结流程

1. claude 完成阶段 0 审计，产出 `docs/PROJECT_AUDIT.md`、`docs/KNOWN_ISSUES.md`；
2. 依据审计结果逐条核对本契约（C4–C9；C1–C3 已决策，仅需核对真实实现是否一致）；
3. 后端按冻结契约实现/修正接口；
4. 前端按冻结契约替换 mock；
5. 契约状态由「待审计确认」改为「已冻结」，进入 M2 里程碑。

---
文档版本：0.2（草案，含 2026-10-14 决策）｜维护人：编程专家（前后端联调）｜创建日期：2026-10-14
