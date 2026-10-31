# 本地联调 Checklist（真实后端）

> 适用：开发者在**自己电脑**上跑通「前端 → 真实后端」全链路。
> 基线：后端 main 已含公开侧 5 接口 + 管理端 8 接口（契约见 `docs/BACKEND_HANDOFF_WORKBUDDY_ADMIN_API.md`）。
> 注意：Agent 云端环境访问不到你本机 `localhost:8080`，本清单由你本机执行。

## 0. 准备

```bash
# 后端构建（聚合工程根目录，跳过测试）
cd Blogs_java
mvn clean package -Dmaven.test.skip=true

# 前端依赖
cd frontend
npm install
```

## 1. 起后端

```bash
cd Blogs_java
export JWT_SECRET="至少32字节的随机字符串，例如 0123456789abcdef0123456789abcdef"
# 首次建库/建管理员还需 ADMIN_INIT_PASSWORD（之后可省略，AdminUserInitializer 仅首次创建）
export ADMIN_INIT_PASSWORD="你的初始管理员密码"
java -jar blogs-admin/target/blogs-admin-*.jar --spring.profiles.active=dev
```

- 数据库：MySQL 8.0，库名 `blogs_db`（dev profile，见 `application-dev.yml`，默认 root/123456@localhost:3306，按本机实际改）
- dev 启动自动执行 `db/schema.sql` 建表（admin_user / article / category）
- 启动成功标志：控制台出现 Tomcat started on port **8080**，且日志显示管理员初始化成功

## 2. 起前端（关闭 mock，连真实后端）

```bash
cd frontend
# 复制联调配置（关键：VITE_USE_MOCK=false）
cp .env.local.example .env.local
# 确认 .env.local 内容：VITE_API_BASE_URL=/api/v1、VITE_USE_MOCK=false
npm run dev
```

- 前端 dev：`http://localhost:5173`，vite proxy `/api → http://localhost:8080`（vite.config.js 已配）
- 不建 `.env.local` 时默认走 mock（VITE_USE_MOCK=true），页面能开但数据是假的

## 3. 登录链路

| 步骤 | 操作 | 预期 |
| --- | --- | --- |
| 3.1 | 浏览器打开 `http://localhost:5173/login`，用初始管理员登录（username 见初始管理员创建逻辑；密码 = ADMIN_INIT_PASSWORD） | 登录成功跳管理后台 |
| 3.2 | 打开 DevTools → Network，看登录请求 `POST /api/v1/auth/login` | 响应 data 含 `{ token, tokenType:'Bearer', expiresIn:86400, userInfo }` |
| 3.3 | 刷新管理后台页面 | 不跳回登录页，正常恢复登录态（前端调 `GET /api/v1/auth/me`） |
| 3.4 | Network 里看 `GET /api/v1/auth/me` | 200，返回当前管理员 userInfo |
| 3.5 | 登录页输错误密码 | 401「用户名或密码错误」；空/超长参数 → 400 |

**拿 token 备用**（后续 curl 验证）：Network → login 响应复制 `data.token`，记作 `$TOKEN`。

```bash
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
```

## 4. 公开链路（访客端，无需登录）

| 步骤 | 操作 | 预期 |
| --- | --- | --- |
| 4.1 | 首页列表 | 仅显示已发布文章，分页正常（pageNum/pageSize） |
| 4.2 | 关键词搜索 | 输入标题/摘要/正文关键词，列表按 keyword 过滤 |
| 4.3 | 分类筛选 | 点分类，列表按 categoryId 过滤 |
| 4.4 | 文章详情 | 打开一篇文章，**阅读数 +1**（刷新后再看） |
| 4.5 | 草稿/下架文章 | 直接访问 `/article/6`（草稿 id）→ 404「文章不存在或未发布」 |
| 4.6 | 标签筛选条 | 显示「阶段3开放」占位（后端暂无 tagId，前端已降级隐藏） |

curl 示例：

```bash
curl -s "http://localhost:8080/api/v1/articles?pageNum=1&pageSize=10&keyword=Vue&categoryId=1"
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/articles/6   # 期望 404
```

## 5. 管理端 8 个接口（逐个验证）

全部需要 `Authorization: Bearer $TOKEN`；不带 token → 401，非管理员 → 403。

### 5.1 文章 CRUD

| # | 接口 | 验证点 |
| --- | --- | --- |
| ① | `GET /api/v1/admin/articles?pageNum=1&pageSize=10&status=0` | 返回分页结构；**list 每项含 status**（0/1/2）与 updatedAt、categoryName；status=0 时只出草稿；不传 status 出全部 |
| ② | `POST /api/v1/admin/articles` | body `{title, summary, contentMd, categoryId, status, coverImage, isTop}`；返回 data 为 id（Long） |
| ③ | `PUT /api/v1/admin/articles/{id}` | body 同上；200；改 status=1 后公开列表能搜到 |
| ④ | `DELETE /api/v1/admin/articles/{id}` | 200（逻辑删除）；删除后管理列表消失、公开详情 404 |

### 5.2 分类 CRUD

| # | 接口 | 验证点 |
| --- | --- | --- |
| ⑤ | `GET /api/v1/admin/categories` | 返回扁平数组，字段 id/name/parentId/description/sortOrder，按 sortOrder 升序 |
| ⑥ | `POST /api/v1/admin/categories` | body `{name, description, sortOrder}`；200 |
| ⑦ | `PUT /api/v1/admin/categories/{id}` | body 同上；200 |
| ⑧ | `DELETE /api/v1/admin/categories/{id}` | 该分类下**还有文章**时返回业务错误（如「该分类下还有 N 篇文章」），**不是 500**；无文章时 200 |

curl 示例：

```bash
curl -s "http://localhost:8080/api/v1/admin/articles?pageNum=1&pageSize=10" -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://localhost:8080/api/v1/admin/articles \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"联调测试","summary":"s","contentMd":"# 正文","categoryId":1,"status":0,"coverImage":"","isTop":0}'
```

## 6. 常见报错排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 前端列表加载失败 / 401 | token 过期或未带 | 重新登录；检查拦截器是否把 Bearer 头带上；`GET /auth/me` 确认 token 有效 |
| 403 | 当前账号非 ADMIN 角色 | 用管理员账号登录（SecurityConfig 仅 ROLE_ADMIN 可访问 /admin/**） |
| CORS 报错 | 后端 cors 配置不含前端源 | dev 下是 vite proxy 转发，同源无 CORS；若直连 8080，检查 `application.yaml` 的 `blogs.cors.allowed-origins`（应含 http://localhost:5173） |
| 404 | 路径写错 / 文章未发布 / 分类不存在 | 核对 URL（前缀 /api/v1）；草稿与下架对公开接口就是 404，属正常 |
| 管理端列表 status 列全「未知」 | 后端 VO 未返回 status | 确认后端用 `AdminArticleSummaryVO`（含 status + updatedAt），公开 VO 不要加 |
| 时间显示 NaN-NaN | 后端时间格式不是 `yyyy-MM-dd HH:mm:ss` | 后端已配 JacksonConfig 统一格式；如仍异常检查是否有 @JsonFormat 覆盖 |
| 分类删除 500 | 删除前未校验引用 | 后端必须按契约先 count 引用文章再删，有引用抛业务异常 |

## 7. 联调完成标准

- [ ] 登录成功拿 token，刷新页面登录态保持（/auth/me 生效）
- [ ] 公开列表：分页 / 关键词 / 分类筛选正常，草稿 404，详情阅读数 +1
- [ ] 管理端文章 4 接口全通（含 status 筛选、新建后公开可见、逻辑删除生效）
- [ ] 管理端分类 4 接口全通（含删除引用校验）
- [ ] 前端切 mock（删掉 .env.local 或 VITE_USE_MOCK=true）后回退正常
