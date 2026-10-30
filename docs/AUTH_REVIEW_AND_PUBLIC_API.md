# 登录功能二次核准 + 主页面接口交付记录

> 日期：2026-10-30
> 基线：`ed54db5`（main）
> 依据：`docs/API_CONTRACT.md`（6.2.1 已冻结）、`docs/BACKEND_DESIGN_DB_AND_API.md`、`docs/BACKEND_HANDOFF_WORKBUDDY.md`
> 范围：① 登录链路二次核准与缺陷修复；② 主页面（公开端）三个接口的后端实现

---

## 一、登录功能二次核准结论

对照契约 6.2.1「已冻结」逐条核验，原实现**主干正确**，发现 4 处缺陷并已修复。

### 1.1 逐条核验

| # | 契约要求 | 原实现 | 结论 |
| --- | --- | --- | --- |
| 1 | `POST /api/v1/auth/login`，`application/json` | 同 | ✅ 一致 |
| 2 | username 3–50，trim 后非空 | `@NotBlank` + `@Size(3,50)` | ⚠️ 缺陷 2（见 1.2） |
| 3 | password 6–64，不去空格 | `@NotBlank` + `@Size(6,64)`，未 trim | ✅ 一致 |
| 4 | 校验失败返回 400 + 具体字段信息 | `MethodArgumentNotValidException` → 400 首条字段错误 | ✅ 一致 |
| 5 | 用户不存在/密码错误统一 `401 用户名或密码错误` | 统一文案 | ✅ 一致（补充时序防护） |
| 6 | 禁用账号 `403 账号已被禁用` | 密码校验通过后判断 status | ✅ 一致，且「密码错+禁用」仍回 401，不泄露状态 |
| 7 | HS256、密钥 ≥32 字节来自 `JWT_SECRET`，缺失启动失败 | `JwtTokenProvider` `@PostConstruct` 校验 | ✅ 一致 |
| 8 | `expiresIn=86400`，claims 含 `sub/username/role/iat/exp` | 同上，`iat/exp/issuer` 全部写入 | ✅ 一致 |
| 9 | 响应 `token/tokenType=Bearer/expiresIn/userInfo{id,username,nickname,avatar,role}` | `LoginResponse` 字段完全对应 | ✅ 一致 |
| 10 | 密码 bcrypt 存储，不返回、不写日志 | `@ToString.Exclude` + VO 不含 password | ✅ 一致 |
| 11 | `GET /api/v1/auth/me` 需 JWT，返回 userInfo，失效 401 | 已实现，禁用账号返回 403 | ✅ 一致 |
| 12 | `/api/v1/admin/**` 服务端强制 `ROLE_ADMIN` | `SecurityConfig.hasRole("ADMIN")` + 过滤器写入 `ROLE_+role` | ✅ 一致 |
| 13 | 首个管理员由 `ADMIN_INIT_PASSWORD` 初始化，不硬编码 | `AdminUserInitializer` 表空时创建 | ⚠️ 缺陷 3 |

### 1.2 已修复的缺陷

| 编号 | 问题 | 影响 | 修复 |
| --- | --- | --- | --- |
| 缺陷 1 | 用户不存在时**不执行** bcrypt 比对，直接返回 401；密码错误时执行（约几十毫秒） | 攻击者可用响应时间差枚举账号是否存在，与契约「防账号枚举」目标相悖 | 启动时生成随机占位哈希，用户不存在也做一次 `matches`，两条失败路径耗时基本一致（`AuthServiceImpl#initDummyPasswordHash`） |
| 缺陷 2 | `@Size` 校验原始字符串，`"  a  "`（5 字符）能通过校验，trim 后仅 1 字符却仍去查库 | 与契约「去除首尾空格后非空」语义不符，产生无意义的查询 | `normalizeUsername` 在 trim 后按 3–50 复核，不合法直接 400 |
| 缺陷 3 | `ADMIN_INIT_PASSWORD` 无长度校验，若设为 5 位，建出来的 admin 因登录参数校验（≥6）**永远登不进去** | 部署踩坑：账号存在但无法登录，且表非空后初始化器不再重建 | 初始化前校验 6–64，不合规时 `log.error` 并跳过初始化，表保持为空以便修正环境变量后重试 |
| 缺陷 4 | `role` 为空时（手工插库遗漏）签发空角色 claim，且 `userInfo.role=null` | 该账号访问 `/api/v1/admin/**` 永久 403，登录态与权限不一致 | `AuthServiceImpl#resolveRole` 与 `AdminUserInfo.from` 统一兜底为 `ADMIN` |

### 1.3 仍存在的已知风险（未在本次处理）

| 风险 | 说明 | 建议 |
| --- | --- | --- |
| 无限流/锁定 | 契约预留 429，登录接口可被暴力破解 | 上线前按 IP + 账号维度加滑动窗口限流（Redis 已有依赖） |
| 前端令牌存储 | `frontend/src/utils/request.js` 把 token 放 `localStorage` | MVP 契约允许「前端丢弃即登出」，但存在 XSS 窃取风险；长期建议 httpOnly Cookie + CSRF 或内存态 + 刷新令牌 |
| 登出 | 无状态 JWT，服务端无法吊销 | 24h 有效期内被盗 token 一直可用；后续可加 token 版本号（`token_version`）实现吊销 |

---

## 二、主页面（公开端）接口实现

### 2.1 接口清单

| 方法 | 路径 | 说明 | 实现位置 |
| --- | --- | --- | --- |
| GET | `/api/v1/articles` | 分页，仅 `status=1`，置顶优先 + 创建时间倒序，支持 `categoryId`、`keyword` | `PublicArticleController#list` |
| GET | `/api/v1/articles/{id}` | 已发布详情，`viewCount` 原子 +1；草稿/下架 → 404 | `PublicArticleController#detail` |
| GET | `/api/v1/categories` | 扁平数组，`sortOrder` 升序 | `PublicCategoryController#list` |

路径与前端 `frontend/src/api/articles.js`、`categories.js` 完全一致，未做任何改名。

### 2.2 新增/修改文件

**新增（blogs-model）**

- `vo/PageResult.java` — 统一分页结构 `{list,total,pageNum,pageSize,pages}`，替代 MP 的 `records/current/size`
- `vo/ArticleSummaryVO.java` — 列表项，**不含** `contentMd/contentHtml`，含前端需要的 `categoryName`
- `vo/ArticleDetailVO.java` — 详情，含 `contentMd/contentHtml`
- `vo/CategoryVO.java` — 分类项
- `dto/ArticleQueryRequest.java` — 查询参数 + `@Min/@Max/@Size` 边界校验

**新增（blogs-admin）**

- `controller/PublicArticleController.java`
- `controller/PublicCategoryController.java`

**新增（blogs-common）**

- `config/JacksonConfig.java` — `LocalDateTime` 统一 `yyyy-MM-dd HH:mm:ss`

**修改**

- `blogs-core/service/ArticleService` + `impl` — `pagePublished` / `getPublishedDetail`
- `blogs-core/service/CategoryService` + `impl` — `listAllOrdered`
- `blogs-core/config/MybatisPlusConfig` — 补 `PaginationInnerInterceptor(DbType.MYSQL)`，`setMaxLimit(100)`
- `blogs-core/pom.xml` + 根 `pom.xml` — 引入 `mybatis-plus-jsqlparser`（3.5.9 起分页插件必需，否则运行期报类缺失）
- `blogs-admin/src/main/resources/db/schema.sql` — 并入 `article` / `category` 建表（含 `status`、`category_id`、`created_at` 三个索引）
- `blogs-common/.../GlobalExceptionHandler` — 补 `MethodArgumentTypeMismatchException` → 400（原会落到兜底 500）
- `blogs-model/entity/Article` — 增加 `STATUS_*` / `TOP` 常量

### 2.3 关键实现说明

1. **草稿不可泄露**：`status=1` 与关键词的 `OR` 条件用 `and(sub -> ...)` 显式加括号。若写成 `like(title).or().like(summary)` 与 `eq(status)` 平级，SQL 会变成 `status=1 AND title like ... OR summary like ...`，草稿会被搜出来（已用真实 SQL 验证过该风险点）。
2. **阅读量并发安全**：`UPDATE article SET view_count = view_count + 1 WHERE id = ? AND deleted = 0`，而不是「读出来 +1 再写回」，避免并发丢计数。返回给前端的 `viewCount` 为自增后的值。
3. **避免 N+1**：列表页先查文章，再用 `in (categoryIds)` 一次性取分类名；详情单条查询分类。
4. **列表不带正文**：`select` 显式列出列表所需列，`content_md/content_html` 不参与查询（单篇正文可能几十 KB）。
5. **不返回** `publishedAt`、`tagIds`：实体无 `publishedAt`（用 `createdAt` 代替），标签属阶段 3。

### 2.4 复核补充修正

| 问题 | 影响 | 修复 |
| --- | --- | --- |
| 关键词未转义 LIKE 通配符 | 用户搜索 `%` 或 `_` 时，`LIKE '%%%'` 会命中**全部已发布文章**，搜索语义失效（不泄露草稿，但结果错误） | `ArticleServiceImpl#escapeLikeKeyword`：按 `\` → `%` → `_` 顺序加反斜杠转义后再拼接；实测搜 `%` 返回 `total=0` |

其余复核项（分页插件、时间格式、建表完整性、状态过滤、阅读量自增）均通过，未发现新缺陷。

---

## 三、自测记录（真实执行）

### 3.1 第一轮：登录 + 主页面（`blogs_db`）

环境：本机 MySQL 8.0.42（`blogs_db`）+ `java -jar blogs-admin-0.0.1-SNAPSHOT.jar --server.port=8080`
编译：`mvn -B -Dmaven.compiler.release=17 -Dmaven.test.skip=true clean package` → **BUILD SUCCESS**（6 模块全绿）

> 说明：项目 `pom` 声明 Java 21，本机仅装 JDK 17，故本次以 `-Dmaven.compiler.release=17` 做类型检查与打包。代码未使用 Java 21 专属语法，正式出包请在 JDK 21 下执行 `mvn clean package -Dmaven.test.skip=true`。

| # | 用例 | 实际结果 | 结论 |
| --- | --- | --- | --- |
| 1 | 启动自动建表 | `SHOW TABLES` → `admin_user`/`article`/`category` | ✅ |
| 2 | 首个管理员初始化 | 日志创建 `admin`，`role=ADMIN,status=1` | ✅ |
| 3 | 登录成功 | 200，`data={token,tokenType:"Bearer",expiresIn:86400,userInfo{...role:"ADMIN"}}` | ✅ |
| 4 | 密码错误 | `401 {"code":401,"message":"用户名或密码错误"}` | ✅ |
| 5 | 用户名过短 | `400 用户名长度必须在3-50位之间` | ✅ |
| 6 | 公开列表 | 只返回 2 篇已发布（草稿未出现），`categoryName` 已填充，时间 `2026-10-01 10:00:00`（无 `T`） | ✅ |
| 7 | 分页字段 | `{"list":[...],"total":2,"pageNum":1,"pageSize":10,"pages":1}` | ✅ |
| 8 | 分页真实生效 | `pageNum=2&pageSize=1` → `total=2,pageNum=2,pageSize=1,pages=2`，第二条数据正确 | ✅ |
| 9 | 草稿详情 | `404 文章不存在或未发布` | ✅ |
| 10 | 阅读量自增 | 连续两次详情 `viewCount` 10→11→12 | ✅ |
| 11 | 关键词过滤 | `keyword=JWT`（仅草稿摘要含该词）→ `total=0` | ✅ |
| 12 | 分类筛选 | `categoryId=2` → `total=1` | ✅ |
| 13 | 分类列表 | 扁平数组，`sortOrder` 5 在 10 之前 | ✅ |
| 14 | 未登录访问管理端 | `GET /api/v1/admin/articles` → `401` | ✅ |
| 15 | 非法路径参数 | `GET /api/v1/articles/abc` → `400 参数类型不正确: id`（修复前为 500） | ✅ |
| 16 | pageSize 超限 | `pageSize=500` → `400 每页条数不能超过100` | ✅ |
| 17 | `/auth/me` 带 token / 伪造 token | 该条命令涉及令牌回显，被会话侧拒绝执行，**未实测** | ⚠️ 未验证 |

冒烟结束后已清理：清空本次插入的样例数据与测试管理员（三张表结构保留、数据为空），删除临时目录。

### 3.2 第二轮复验：主页面三个接口（独立库 `blogs_smoke`）

为避免污染业务库，本轮新建独立库 `blogs_smoke` 验证，结束即 `DROP DATABASE`，临时目录同步删除；`blogs_db` 全程未写入。

数据集：已发布 3 篇（1 篇置顶、创建时间跨度 9/1–10/2）+ 草稿 1 篇 + 已下架 1 篇；分类 2 个（`sortOrder` 5 / 10）。

| # | 用例 | 实际结果 | 结论 |
| --- | --- | --- | --- |
| 1 | 启动自动建表 | `SHOW TABLES` → `admin_user`/`article`/`category` | ✅ |
| 2 | 列表默认 | `total=3`，顺序 `置顶篇 → 10-02 → 09-01`（`isTop` 降序 + `createdAt` 降序） | ✅ |
| 3 | 列表不含正文 | 响应中 `contentMd` 命中 0 次 | ✅ |
| 4 | 时间格式 | `"createdAt":"2026-10-01 10:00:00"`，无 `T` | ✅ |
| 5 | 列表排除草稿/下架 | 草稿篇、已下架篇均未出现 | ✅ |
| 6 | 阅读量自增（持久化） | 连续访问 10→11→12，库中 `view_count=13` | ✅ |
| 7 | 自增不污染更新时间 | `updated_at` 仍为 `2026-10-01 10:00:00`（`update(entity=null)` 不触发自动填充） | ✅ |
| 8 | 草稿详情 | `HTTP 404 文章不存在或未发布` | ✅ |
| 9 | 下架详情 | `HTTP 404` | ✅ |
| 10 | 不存在 ID | `HTTP 404` | ✅ |
| 11 | 关键词仅命中草稿 | `keyword=JWT` → `total=0` | ✅ |
| 12 | 关键词通配符转义 | `keyword=%` → `total=0`（修复前会返回 3） | ✅ |
| 13 | 关键词正常命中 | `keyword=普通` → `total=1` | ✅ |
| 14 | 分类筛选 | `categoryId=1` → `total=2` | ✅ |
| 15 | 分页真实生效 | `pageNum=2&pageSize=2` → `total=3,pageNum=2,pageSize=2,pages=2`，本页 1 条 | ✅ |
| 16 | 空结果结构 | `"list":[]`（空数组，非 null）、`total=0` | ✅ |
| 17 | 详情字段完整 | `id/title/categoryId/categoryName/viewCount/isTop/createdAt/updatedAt` 齐全 | ✅ |
| 18 | 分类列表排序 | `前端(5)` 在 `Java 后端(10)` 之前，扁平数组 | ✅ |
| 19 | 包络一致 | `{"code":200,"message":"操作成功","data":...,"timestamp":...}` | ✅ |
| 20 | pageSize 超限 | `pageSize=500` → `400 每页条数不能超过100` | ✅ |
| 21 | 非法路径参数 | `/articles/abc` → `400 参数类型不正确: id` | ✅ |
| 22 | 未登录访问管理端 | `GET /api/v1/admin/articles` → `401` | ✅ |
| 23 | 登录成功 | `200`，`tokenType=Bearer`、`expiresIn=86400`、`role=ADMIN` | ✅ |
| 24 | 密码错误 | `401 用户名或密码错误` | ✅ |

编译：`mvn -B -Dmaven.compiler.release=17 -Dmaven.test.skip=true clean package` → **BUILD SUCCESS**（6 模块全绿）。

**前端联调注意（非后端缺陷）**：`ArticleList.vue` 用 `new Date(a.createdAt)` 解析时间，`yyyy-MM-dd HH:mm:ss` 在 Chrome 可用，但 Safari 会得到 `Invalid Date`。建议前端改为 `new Date(String(ts).replace(' ', 'T'))`，或后端改为返回 ISO 8601 + 前端格式化（当前按契约第五节保持空格格式）。

---

## 四、下一步建议

1. **管理端 CRUD（9 个接口）**：按 `docs/BACKEND_DESIGN_DB_AND_API.md` 4.5 实现，`userId` 从 `JwtPrincipal` 取、`createdAt/updatedAt` 走自动填充。
2. **`contentHtml` 渲染**：建议统一由后端在保存时用 commonmark-java 渲染，前端只传 `contentMd`（当前详情接口对 `contentHtml` 为原样透传，可能为 null）。
3. **分类删除策略**：建议「置空文章 `categoryId`」，并在删除时同事务更新，避免分类删除后文章仍指向已删分类。
4. **修 JDK**：本机装 JDK 21 后按 `pom` 声明版本出包，避免 `release` 覆盖带来的隐性差异。
5. **登录加固**：上线前补登录限流（429）与失败计数，必要时引入 token 版本号以支持吊销。
