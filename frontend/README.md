# 博客前端（frontend/）

个人知识博客网站 · 前端工程（MVP 冲刺版，2026-10-30 续）

- 技术栈：Vue 3 + 纯 JavaScript（严禁 TypeScript）+ Vite + Element Plus + Vue Router + Pinia + Axios
- 基址：`/api/v1`（本地 dev 经 Vite proxy `/api → http://localhost:8080`，与后端 `API_CONTRACT.md` 一致）
- 文档依据：`docs/API_CONTRACT.md`（接口契约草案，登录 6.2.1 已冻结 2026-10-20）

## 快速开始

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # 产物 dist/
```

## mock 开关（对接真实接口）

**默认开启 mock**。mock 通过 axios 自定义 adapter 实现：`src/mock/index.js` 中的 `USE_MOCK` 决定是否拦截请求，
数据集中在 `src/mock/data.js`，接口处理逻辑在 `src/mock/index.js`。

切真实接口**只改一个开关**：

```bash
# frontend/.env.local
VITE_USE_MOCK=false
```

之后所有请求走 Vite proxy → `http://localhost:8080`，API 层（`src/api/*`）零改动。
也可以直接修改 `src/mock/index.js` 中 `USE_MOCK` 的默认值。

**说明**：mock 使用 axios adapter 机制（`service.defaults.adapter = createMockAdapter()`），因此响应拦截器（Result<T> 解包、
401 处理）在 mock 与真实后端下行为一致；若需彻底移除 mock，删除 `src/utils/request.js` 中以下三行即可：

```js
if (USE_MOCK) {
  service.defaults.adapter = createMockAdapter()
}
```

## 目录结构

```
frontend/
├── src/
│   ├── api/            # 接口模块（auth/articles/categories/tags）
│   ├── mock/           # mock 适配器 + 数据（集中在此，切真实接口只关开关）
│   ├── stores/         # Pinia（auth 登录态）
│   ├── router/         # 路由 + 守卫（/admin/** 需 token）
│   ├── utils/          # axios 实例（baseURL=/api/v1，解包 Result<T>，401 跳登录）
│   │                    # + markdown.js（轻量 Markdown 渲染器，自实现）
│   ├── layouts/        # AdminLayout（侧边菜单 + 顶栏）
│   └── views/
│       ├── public/     # 访客端：文章列表（分页+搜索+标签筛选）、文章详情（Markdown）、404
│       ├── auth/       # 登录页
│       └── admin/      # 文章管理（含封面/置顶/Markdown 分栏预览）、分类管理、标签管理
├── vite.config.js      # /api 代理 → http://localhost:8080
└── package.json
```

## MVP 已覆盖主链路

访客看文章（列表：分页/分类/关键词搜索/标签筛选；详情：Markdown 渲染）→ 管理员登录（admin / admin123，mock 专用；
禁用账号 disabled / admin123 演示 403）→ 发文（新建/编辑/删除，支持封面 URL、置顶开关、Markdown 分栏实时预览）→
分类管理（增删改查）→ 标签管理（增删改查，mock 先行）。

## 与契约/文档的差异与假设（待后端确认）

1. **纯 JS vs FRONTEND.md 的 TS**：仓库 `Blogs_java/docs/FRONTEND.md` 原写 TypeScript，但任务硬性要求纯 JavaScript，已按任务指令以 JS 实现。
2. **登录接口**：契约 `POST /api/v1/auth/login` 冻结版返回 `{ token, tokenType:'Bearer', expiresIn:86400, userInfo }`（mock 已对齐）；
   `GET /api/v1/auth/me` 获取当前用户信息。若后端返回结构不同，需调整 `src/api/auth.js` 与 `src/stores/auth.js`。
3. **Markdown 渲染**：已自实现轻量渲染器 `src/utils/markdown.js`（标题/列表/引用/代码块/加粗/斜体/行内代码/链接），
   编辑端分栏实时预览 + 详情页渲染共用；正式版若需完整语法建议接入 markdown-it + 代码高亮。
4. **管理端文章列表**：使用 `GET /api/v1/admin/articles`（含草稿/下架 + status 过滤），公开列表用 `GET /api/v1/articles`
   （仅已发布 + categoryId + keyword + tagId 过滤）。后端若未单独提供 admin 列表接口，需补充。
5. **标签**：MVP 已纳入（排在分类之后）。前端接口已定型（`GET /tags`、`GET/POST/PUT/DELETE /admin/tags`），
   **后端 tag 表为阶段3未建，当前 mock 先行**；对接后需后端实现标签接口并同步 `keyword/tagId` 过滤。
6. **站点设置**：契约标记「MVP 不支持」，未实现。
7. **mock 管理端接口**：`POST/PUT/DELETE /admin/articles`、`/admin/categories`、`/admin/tags` 为前端演示用，最终以审计后的真实接口为准。

## 后端必须提供（对接最小清单）

- `GET  /api/v1/articles`（分页、仅已发布、categoryId/keyword/tagId 过滤）
- `GET  /api/v1/articles/{id}`（仅已发布，未发布 404）
- `GET  /api/v1/categories`
- `GET  /api/v1/tags`
- `POST /api/v1/auth/login`（返回 token + tokenType + expiresIn + userInfo；无状态 JWT）
- `GET  /api/v1/auth/me`
- `GET  /api/v1/admin/articles`（含草稿/下架，status 过滤）
- `POST /api/v1/admin/articles`（新建，返回文章实体）
- `PUT  /api/v1/admin/articles/{id}`（更新，含 coverImage/isTop/tagIds）
- `DELETE /api/v1/admin/articles/{id}`（逻辑删除）
- `GET/POST/PUT/DELETE /api/v1/admin/categories`（分类管理）
- `GET/POST/PUT/DELETE /api/v1/admin/tags`（标签管理，阶段3后端落地）
- 统一响应包络 `Result<T>`（code=200 成功）、错误码 400/401/403/404/500、分页 `pageNum/pageSize → { list,total,pageNum,pageSize,pages }`
