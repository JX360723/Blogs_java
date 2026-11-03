# 项目审计报告（PROJECT_AUDIT）

> 阶段：PHASE 0 交付物 1/3
> 审计时间：2026-11-03
> 审计基线：`origin/main` = `e1ca1e8`（Merge PR #6）
> 审计方式：逐文件读取 + 依赖树核对 + 配置比对；**云端环境无 Maven / JDK 21，编译与启动验证需在本地执行**（见第 8 节）

## 1. 审计结论摘要

| 结论 | 说明 |
| --- | --- |
| 工程骨架 | ✅ 五模块 Maven 分层清晰，依赖方向正确（admin → core → security/common → model） |
| 认证主线 | ✅ 登录 / me / JWT / 强制鉴权 / 管理员初始化全部落地，契约对齐 |
| 公开侧读接口 | ✅ 3 个接口落地，分页、关键词转义、阅读量自增、草稿隔离均有正确处理 |
| 管理端写接口 | ❌ **8 个接口零实现**，Service 层无任何写方法，前端管理页完全依赖 mock |
| 启动隐患 | 🔴 **存在 2 个冗余 `@SpringBootApplication`，加测试依赖后 `mvn test` 必然失败**（见 P0-2） |
| 测试体系 | ❌ 5 个测试类全是脚手架空壳，且**全模块缺 `spring-boot-starter-test` 依赖**，无法编译 |
| 健康检查 | ❌ `SecurityConfig` 已放行 `/actuator/health`，但 **pom 未引入 actuator**，端点不存在 |
| 安全配置 | ⚠️ `application-dev.yml` 明文写库密码 `123456` |
| 真实联调 | ❌ 从未跑通，前端 `USE_MOCK` 默认 true |

**一句话**：认证和公开读链路是可信的，写链路和测试体系是空的，且工程里埋着一个会让测试直接起不来的脚手架垃圾。

## 2. 仓库与构建

| 项 | 实测值 |
| --- | --- |
| 仓库根 | `/Coze/Drive/博客网站/Blogs_java` |
| 远端 | `github.com/JX360723/Blogs_java` |
| 当前 main | `e1ca1e8` |
| 分支策略 | 特性分支 → PR → 审核合并，不直推 main |
| 模块 | blogs-model / blogs-common / blogs-security / blogs-core / blogs-admin（5 个） |
| Java | 21（`<java.version>21</java.version>`） |
| Spring Boot | 3.4.0（parent） |
| MyBatis-Plus | 3.5.9 + `mybatis-plus-jsqlparser`（分页插件必需） |
| JWT | jjwt 0.12.6 |
| 打包 | `spring-boot-maven-plugin` repackage，产物在 blogs-admin |

**依赖方向核对**：`blogs-admin → blogs-core → {blogs-security, blogs-common} → blogs-model`，无循环依赖。✅

**缺失依赖**：
- `spring-boot-starter-actuator`（配置已引用端点，依赖未加）
- `spring-boot-starter-test`（所有模块均无，测试类无法编译）

## 3. 五模块真实状态

### 3.1 blogs-model（DTO / Entity / VO）

| 文件 | 状态 |
| --- | --- |
| `entity/Article` | ✅ 含 `STATUS_DRAFT=0 / STATUS_PUBLISHED=1 / STATUS_OFFLINE=2 / NOT_TOP=0` 常量，字段与 `schema.sql` 逐字段对应 |
| `entity/Category` | ✅ 自关联 `parentId`，`parentId=0` 为顶级 |
| `entity/AdminUser` | ✅ 含 `role` / `status` / `deleted` |
| `dto/ArticleQueryRequest` | ✅ pageNum / pageSize / categoryId / keyword，**刻意不含 status**（安全设计正确） |
| `dto/LoginRequest` | ✅ |
| `vo/ArticleSummaryVO` | ✅ 9 字段，**不含 status / updatedAt**（管理端需要，见 P0-3） |
| `vo/ArticleDetailVO` | ✅ 含 contentMd / contentHtml |
| `vo/CategoryVO` | ✅ |
| `vo/PageResult` | ✅ `list/total/pageNum/pageSize/pages`，含 `of()` 工厂方法 |
| `vo/LoginResponse` / `vo/AdminUserInfo` | ✅ |
| **缺失** | ❌ `AdminArticleQueryRequest`、`ArticleSaveRequest`、`CategorySaveRequest`、`AdminArticleSummaryVO` |

### 3.2 blogs-common（公共能力）

| 能力 | 状态 |
| --- | --- |
| `Result<T>` | ✅ `code/message/data/timestamp`，字段名 `message`（不是 msg） |
| `ResultCode` | ✅ 200/400/401/403/404/429/500 |
| `BusinessException` + `GlobalExceptionHandler` | ✅ 覆盖 10 类异常：业务异常、参数校验、约束校验、缺参、类型不匹配、请求体不可读、404、方法不支持、兜底 500 |
| `JacksonConfig` | ✅ LocalDateTime → `yyyy-MM-dd HH:mm:ss`，LocalDate → `yyyy-MM-dd`，序列化 + 反序列化双向配置 |
| `JwtTokenProvider` / `JwtProperties` / `JwtPrincipal` | ✅ HS256，密钥由 `JWT_SECRET` 环境变量注入，默认空值（不硬编码） |
| **冗余** | 🔴 `BlogsCommonApplication`（`@SpringBootApplication`）— 库模块不需要启动类，见 P0-2 |

### 3.3 blogs-security（安全）

| 能力 | 状态 |
| --- | --- |
| `SecurityConfig` | ✅ 无状态 JWT；`OPTIONS /**` 放行；`POST /auth/login` 放行；`/actuator/health`、`/error` 放行；`GET /articles/** /categories/** /tags/**` 放行；`/admin/**` 需 `ROLE_ADMIN`；其余需认证 |
| `JwtAuthenticationFilter` | ✅ 解析 token 写入 SecurityContext |
| `RestSecurityExceptionHandler` | ✅ 401/403 统一走 `Result` 包络 |
| `SecurityCorsProperties` | ✅ 允许 `http://localhost:5173`，方法 GET/POST/PUT/DELETE/OPTIONS |
| **冗余** | 🔴 `BologsSecurityApplication`（`@SpringBootApplication`）— 同上，见 P0-2 |

### 3.4 blogs-core（业务）

| 能力 | 状态 |
| --- | --- |
| `MybatisPlusConfig` | ✅ 分页插件 `PaginationInnerInterceptor(MYSQL)`，单页上限 100 |
| `MybatisMetaObjectHandler` | ✅ `createdAt` / `updatedAt` 自动填充 |
| `AdminUserInitializer` | ✅ 表空时用 `ADMIN_INIT_PASSWORD` 建 admin，长度校验 8–64 位，不落明文 |
| `ArticleMapper` / `CategoryMapper` / `AdminUserMapper` | ✅ |
| `ArticleService` | ⚠️ 仅 `pagePublished` / `getPublishedDetail` 两个方法，**无写方法** |
| `CategoryService` | ⚠️ 仅 `listAllOrdered`，**无写方法** |
| `AdminUserService` | ✅ `getByUsername` |
| `AuthService` | ✅ `login` / `getLoginAdminInfo` |

**`ArticleServiceImpl` 质量抽查（163 行）——实现质量高于预期**：
- ✅ 列表 `select` 刻意排除 `content_md` / `content_html` / `ai_summary`，避免列表接口拖出正文
- ✅ 关键词 `%` `_` `\` 转义，防止用户搜 `%` 命中全表
- ✅ OR 条件整体加括号，避免与 `status=1` 平级导致草稿泄露
- ✅ 分类名批量查询，避免 N+1
- ✅ 阅读量用 `setSql("view_count = view_count + 1")` 自增，而非读改写，避免并发丢计数
- ✅ 详情不存在 / 草稿 / 下架统一 404，不泄露资源存在性

### 3.5 blogs-admin（入口 + 控制器）

| 文件 | 状态 |
| --- | --- |
| `BlogsApplication` | ⚠️ `scanBasePackages = "com.xdl"`，排除 `UserDetailsServiceAutoConfiguration`；**正是这个全包扫描把两个冗余启动类扫了进来** |
| `AuthController` | ✅ `POST /api/v1/auth/login`、`GET /api/v1/auth/me` |
| `PublicArticleController` | ✅ `GET /api/v1/articles`、`GET /api/v1/articles/{id}` |
| `PublicCategoryController` | ✅ `GET /api/v1/categories` |
| **缺失** | ❌ `AdminArticleController`、`AdminCategoryController`（8 个接口） |

## 4. 接口清单

### 4.1 已实现（5 个）

| 方法 | 路径 | 权限 | 实现 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | 匿名 | AuthController |
| GET | `/api/v1/auth/me` | 需 token | AuthController |
| GET | `/api/v1/articles` | 匿名 | PublicArticleController |
| GET | `/api/v1/articles/{id}` | 匿名 | PublicArticleController |
| GET | `/api/v1/categories` | 匿名 | PublicCategoryController |

### 4.2 未实现（8 个，前端 api 层已写好，全部 404）

| 方法 | 路径 | 前端调用点 |
| --- | --- | --- |
| GET | `/api/v1/admin/articles?pageNum&pageSize&status` | `getAdminArticles` |
| POST | `/api/v1/admin/articles` | `createArticle` |
| PUT | `/api/v1/admin/articles/{id}` | `updateArticle` |
| DELETE | `/api/v1/admin/articles/{id}` | `deleteArticle` |
| GET | `/api/v1/admin/categories` | `getAdminCategories` |
| POST | `/api/v1/admin/categories` | `createCategory` |
| PUT | `/api/v1/admin/categories/{id}` | `updateCategory` |
| DELETE | `/api/v1/admin/categories/{id}` | `deleteCategory` |

## 5. 数据库

`blogs-admin/src/main/resources/db/schema.sql` 已含三表，`CREATE TABLE IF NOT EXISTS` 幂等，dev profile 启动时自动执行。

| 表 | 索引 | 备注 |
| --- | --- | --- |
| `admin_user` | `uk_admin_user_username` | 逻辑删除 `deleted` |
| `article` | `idx_article_status`、`idx_article_category`、`idx_article_created` | `content_html` 字段预留但当前无服务端渲染，前端自行渲染 Markdown |
| `category` | `idx_category_parent` | `sort_order` 越小越靠前 |

**发现**：
- `article.content_html` 永远为 null（后端不渲染 Markdown），前端 `ArticleDetail.vue` 实际用 `contentMd` 自行渲染 → 字段冗余但无害
- 仓库根 `sql/` 目录（编程专家早期交付的三表脚本）**仍未跟踪**，与 `db/schema.sql` 内容重复，建议清理（见 P3-1）

## 6. 认证与安全

| 检查项 | 结果 |
| --- | --- |
| JWT 密钥来源 | ✅ 环境变量 `JWT_SECRET`，代码默认空串，无硬编码 |
| 密钥长度校验 | ✅ `AdminUserInitializer` 校验 8–64 位 |
| 密码存储 | ✅ bcrypt（`BCryptPasswordEncoder`） |
| 会话 | ✅ STATELESS，无 session |
| CSRF | ✅ disable（无状态 JWT 场景合理） |
| 管理端鉴权 | ✅ `/api/v1/admin/**` 强制 `hasRole("ADMIN")` |
| 401/403 响应格式 | ✅ 统一 `Result` 包络 |
| **库密码** | 🔴 `application-dev.yml` 明文 `password: 123456`（见 P0-1） |
| **PAT** | 🔴 GitHub PAT 曾在群聊明文出现，仍在多处使用（见 P0-4） |

## 7. 代码与文档差异

| 文档 | 声明 | 实际 | 处理 |
| --- | --- | --- | --- |
| `BACKEND_DESIGN_DB_AND_API.md` 第五节 | 时间格式已落地 | ✅ 一致 | — |
| `BACKEND_HANDOFF_WORKBUDDY.md` 坑点 1/2/9 | 已解决 | ✅ 一致 | — |
| `BACKEND_HANDOFF_WORKBUDDY_ADMIN_API.md` | 管理端 8 接口施工单 | ❌ **文件不在 main**，内容只存在于 commit `348c4ca`（未合并） | 本轮重新产出提示词，见 `WORKBUDDY_PROMPT_PHASE3_ADMIN_API.md` |
| `PHASE_4_FRONTEND.md` | 技术栈写 TypeScript | ❌ 实际锁定纯 JavaScript | 待修订（P2-2） |
| `frontend/INTEGRATION_CHECKLIST.md` | 曾称"后端已含管理端 8 接口" | ❌ 零实现 | 已修正（`1a7dfae`，PR #7 待合） |
| `API_SPEC.md` / `API_CONTRACT.md` | 接口定义 | ✅ 与已实现 5 接口一致 | — |

## 8. 运行验证结果

| 验证项 | 云端 | 本地 | 说明 |
| --- | --- | --- | --- |
| JDK 版本 | ❌ 仅 JDK 11 | 需 JDK 21 | 项目要求 21 |
| Maven | ❌ 未安装 | 需 3.9+ | — |
| `mvn compile` | ❌ 无法执行 | 待执行 | 本轮改动为语法级 + 依赖级，需本地验证 |
| `mvn test` | ❌ 无法执行 | 待执行 | 见 P0-2，**修完才能跑通** |
| 应用启动 | ❌ 无法执行 | 待执行 | 需本地 MySQL 8 + `JWT_SECRET` + `ADMIN_INIT_PASSWORD` |

**结论**：本轮所有后端改动的编译/测试/启动验证必须在本地执行，云端只能做静态核对。

## 9. 分级问题清单

详见 `docs/KNOWN_ISSUES.md`。摘要：

- **P0（阻断/安全）4 项**：明文库密码、冗余启动类、管理端 8 接口全缺、PAT 明文
- **P1（核心功能）4 项**：无 actuator、无测试依赖、无认证测试、管理端 VO 缺 status
- **P2（体验/维护）3 项**：PHASE_4 文档 TS 表述、application.txt 垃圾文件、日志 SQL 全开
- **P3（未来扩展）3 项**：sql/ 目录重复、content_html 冗余、标签模块未做
