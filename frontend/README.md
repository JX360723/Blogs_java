# 博客前端（frontend/）

个人知识博客网站 · 前端工程（MVP 两小时冲刺版）

- 技术栈：Vue 3 + 纯 JavaScript（严禁 TypeScript）+ Vite + Element Plus + Vue Router + Pinia + Axios
- 基址：`/api/v1`（本地 dev 经 Vite proxy `/api → http://localhost:8080`，与后端 `API_CONTRACT.md` 一致）
- 文档依据：`docs/API_CONTRACT.md`（接口契约草案 0.2，全接口待审计确认）

## 快速开始

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # 产物 dist/
```

## mock 开关（15:00 对接真实接口）

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
│   ├── api/            # 接口模块（auth/articles/categories）
│   ├── mock/           # mock 适配器 + 数据（集中在此，切真实接口只关开关）
│   ├── stores/         # Pinia（auth 登录态）
│   ├── router/         # 路由 + 守卫（/admin/** 需 token）
│   ├── utils/          # axios 实例（baseURL=/api/v1，解包 Result<T>，401 跳登录）
│   ├── layouts/        # AdminLayout（侧边菜单 + 顶栏）
│   └── views/
│       ├── public/     # 访客端：文章列表（分页）、文章详情、404
│       ├── auth/       # 登录页
│       └── admin/      # 文章管理（列表 + 新建/编辑弹窗）、分类管理
├── vite.config.js      # /api 代理 → http://localhost:8080
└── package.json
```

## MVP 已覆盖主链路

访客看文章（列表/详情）→ 管理员登录（admin / admin123，mock 专用；禁用账号 disabled / admin123 演示 403）→ 发文（新建/编辑/删除）→ 分类管理（增删改查）。

## 与契约/文档的差异与假设（待后端确认）

1. **纯 JS vs FRONTEND.md 的 TS**：仓库 `Blogs_java/docs/FRONTEND.md` 原写 TypeScript，但本次任务硬性要求纯 JavaScript，已按任务指令以 JS 实现。
2. **登录接口**：契约 `POST /api/v1/auth/login` 返回 `{ token, userInfo }`（mock 对齐该结构）；`GET /api/v1/auth/me` 获取当前用户信息。若后端返回结构不同（如 data 直接为 token 字符串），需调整 `src/api/auth.js` 与 `src/stores/auth.js`。
3. **文章详情 Markdown**：MVP 以纯文本（pre-wrap）展示 `contentMd`，未引入 markdown 渲染库；正式版建议接入 markdown-it + 代码高亮。
4. **管理端文章列表**：使用 `GET /api/v1/admin/articles`（含草稿/下架 + status 过滤），公开列表用 `GET /api/v1/articles`（仅已发布）。后端若未单独提供 admin 列表接口，需补充。
5. **分类**：公开 `GET /api/v1/categories` 与管理端 `GET /api/v1/admin/categories`（管理页复用）；分类管理页 MVP 未做（有余力再加）。
6. **标签（阶段3）**：接口与入口均未实现，侧边菜单仅文章管理。
7. **站点设置**：契约标记「MVP 不支持」，未实现。
8. **mock 管理端接口**：`POST/PUT/DELETE /admin/articles`、`/admin/categories` 为前端演示用，最终以 claude 审计后的真实接口为准。

## 后端必须提供（对接最小清单）

- `GET  /api/v1/articles`（分页、仅已发布、categoryId 过滤）
- `GET  /api/v1/articles/{id}`（仅已发布，未发布 404）
- `GET  /api/v1/categories`
- `POST /api/v1/auth/login`（返回 token + userInfo；无状态 JWT）
- `GET  /api/v1/auth/me`
- `GET  /api/v1/admin/articles`（含草稿/下架，status 过滤）
- `POST /api/v1/admin/articles`（新建，返回文章实体）
- `PUT  /api/v1/admin/articles/{id}`（更新）
- `DELETE /api/v1/admin/articles/{id}`（逻辑删除）
- 统一响应包络 `Result<T>`（code=200 成功）、错误码 400/401/403/404/500、分页 `pageNum/pageSize → { list,total,pageNum,pageSize,pages }`
