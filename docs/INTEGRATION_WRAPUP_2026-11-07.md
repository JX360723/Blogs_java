# 前后端联调收尾报告（2026-11-07）

> 范围：`frontend/` ↔ `blogs-admin` 全链路契约核对 + 缺陷修复 + 可验证性建设。
> 基线：`main` @ `c1a56a6`（后端管理端 9 接口、公开端 3 接口、认证 2 接口均已实现）。

## 1. 契约核对结果

| # | 前端调用点 | 实际请求 | 后端路由 | 结论 |
| --- | --- | --- | --- | --- |
| 1 | `api/auth.js#login` | `POST /api/v1/auth/login` | `AuthController#login` | ✅ |
| 2 | `api/auth.js#getMe` | `GET /api/v1/auth/me` | `AuthController#me` | ✅ |
| 3 | `api/articles.js#getPublicArticles` | `GET /api/v1/articles` | `PublicArticleController#list` | ✅ `list/total/pageNum/pageSize` 与 `PageResult` 一致 |
| 4 | `api/articles.js#getPublicArticle` | `GET /api/v1/articles/{id}` | `PublicArticleController#detail` | ✅ 草稿/下架统一 404 |
| 5 | `api/categories.js#getCategories` | `GET /api/v1/categories` | `PublicCategoryController#list` | ✅ 扁平数组 |
| 6 | `api/articles.js#getAdminArticles` | `GET /api/v1/admin/articles` | `AdminArticleController#list` | ✅ 列表不含正文，符合预期 |
| 7 | `api/articles.js#getAdminArticle` | `GET /api/v1/admin/articles/{id}` | `AdminArticleController#detail` | ⚠️ 原缺 `status` → 已修（见 2.1） |
| 8 | `api/articles.js#createArticle` | `POST /api/v1/admin/articles` | `AdminArticleController#create` | ✅ `Result<Long>` |
| 9 | `api/articles.js#updateArticle` | `PUT /api/v1/admin/articles/{id}` | `AdminArticleController#update` | ✅ 多传的 `id` 由 `@JsonIgnoreProperties` 忽略 |
| 10 | `api/articles.js#deleteArticle` | `DELETE /api/v1/admin/articles/{id}` | `AdminArticleController#delete` | ✅ 逻辑删除 |
| 11 | `api/categories.js#getAdminCategories` | `GET /api/v1/admin/categories` | `AdminCategoryController#list` | ✅ `sortOrder ASC` |
| 12 | `api/categories.js#createCategory/updateCategory/deleteCategory` | `POST/PUT/DELETE /api/v1/admin/categories…` | 同名方法 | ✅ 删除有引用时拒绝 |
| 13 | `api/tags.js` 全部 | `/api/v1/tags`、`/api/v1/admin/tags` | **不存在** | ⚠️ 阶段 3 未交付 → 前端已降级（见 2.3） |

字段级核对：

- `Result<T>` 包络：前端 `utils/request.js` 响应拦截器统一返回 `res.data`（已解包），页面直接用 `data.list` / `data.contentMd`，与后端 `Result.success(data)` 对齐。
- 时间：后端 `JacksonConfig` 输出 `yyyy-MM-dd HH:mm:ss`，前端 `utils/datetime.js` 按此解析，未出现 `Invalid Date`。
- 管理端列表 `AdminArticleSummaryVO`：`id/title/summary/categoryId/categoryName/coverImage/viewCount/isTop/status/createdAt/updatedAt`，与 `ArticleManage.vue` 表格列一一对应。
- 公开列表 `ArticleSummaryVO` 含 `categoryName`（前端 `a.categoryName || '未分类'` 可用）。

## 2. 本轮修复的 5 个问题

### 2.1 【严重】管理端详情不返回 `status`，编辑已发布文章会被静默降级为草稿

- 现象链路：`ArticleManage.vue#openEdit` 拉详情 → `detail.status ?? 0` → 详情无该字段，恒取 0 → 保存时 `PUT` 提交 `status: 0` → 已发布文章变草稿，公开端立即 404。
- 修复：`ArticleDetailVO` 新增 `status` 字段并在 `from()` 中由实体映射（`blogs-model`）。
- 兼容性：公开详情只返回 `status=1` 的文章，该字段对访客无信息量，属向后兼容新增字段。
- 文档同步：`docs/BACKEND_DESIGN_DB_AND_API.md` 4.5 详情 `data` 示例已补 `"status": 1` 与说明。

### 2.2 【严重】根目录 `mvn test` 编译失败：非 admin 模块缺测试依赖

- 现象：`blogs-common`/`blogs-model`/`blogs-core` 有 `*ApplicationTests` 等测试类，但 `spring-boot-starter-test` 只声明在 `blogs-admin/pom.xml`，根目录跑测试直接 `test-compile` 失败。
- 修复：父 `pom.xml` 统一声明 `spring-boot-starter-test`（`scope=test`），各子模块继承；对无测试代码的 `blogs-security` 无副作用。

### 2.3 【中】标签接口未交付，页面照常发请求导致 404 弹窗

- 现象：访客首页 `onMounted` 无条件调 `getTags()`；管理端 `TagManage.vue` 无条件调 `getAdminTags()`。后端无 `/tags`、`/admin/tags` → 每次进页面弹「资源不存在」，像是接口坏了。
- 修复（显式降级，不改契约）：
  - `views/public/ArticleList.vue`：`tagFilterEnabled=false` 时不再请求标签（原本只隐藏了筛选条）。
  - `views/admin/TagManage.vue`：新增 `tagsReady = USE_MOCK`，真实后端模式下不请求，页面顶部显示「阶段 3 未交付」说明并禁用新建。
  - 标签接口上线后把这两个开关置 `true` 即可，其余代码零改动。

### 2.4 【中】`ArticleServiceImplTest` 3 个用例必然失败（`baseMapper can not be null`）

- 原因：`ServiceImpl.getById()` 走 `CrudRepository.getBaseMapper()`，该字段由 Spring 注入，Mockito `@InjectMocks` 不会填 → 抛 `MybatisPlusException`，草稿隔离断言无法执行。此前因 2.2 测试根本没跑起来，问题被掩盖。
- 修复：测试内用 `ReflectionTestUtils.setField(articleService, "baseMapper", articleMapper)` 注入 mock，仍走真实 `getById`（未把 `getById` 一起 mock 掉，保留真实分支逻辑）。

## 3. 联调配置落地

- 新增 `frontend/.env.local`（**已被 `frontend/.gitignore` 忽略，不会入库**）：
  - `VITE_API_BASE_URL=/api/v1`、`VITE_USE_MOCK=false`
  - 作用：关闭 mock，请求经 vite proxy `/api → http://localhost:8080` 打到真实后端。
- 后端凭据仍走环境变量（`DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET` / `ADMIN_INIT_PASSWORD`），仓库内无明文口令。

### 2.5 【中】`/actuator/health` 恒为 503 DOWN：未使用 Redis 却注册了 Redis 健康检查

- 现象：`blogs-common` 引了 `spring-boot-starter-data-redis`、dev 配置写了 `spring.data.redis`，但代码里没有任何 `RedisTemplate`/`@Cacheable` 使用。本机未起 Redis → `RedisHealthIndicator` 判 DOWN → `/actuator/health` 返回 `{"status":"DOWN"}` + HTTP 503，K8s 探活/监控会把正常的服务判成不可用。
- 修复：`application.yaml` 增加 `management.health.redis.enabled: false`（显式保留 `db.enabled: true`，数据源仍是硬依赖）；注释说明真正引入缓存后再打开。
- 验证：重启后 `GET /actuator/health` → `{"status":"UP"}` HTTP 200。

## 4. 验证结果

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| 后端编译 | `mvn -B -Dmaven.compiler.release=17 -Dmaven.test.skip=true -pl blogs-admin -am clean package` | ✅ BUILD SUCCESS |
| 后端单测 | `mvn -B -Dmaven.compiler.release=17 test` | ✅ 28 项，0 失败，1 跳过 |
| 前端构建 | `cd frontend && npm install && npm run build` | ✅ built in 9.55s（仅 chunk >500kB 提示） |
| 健康检查 | `GET /actuator/health` | ✅ `{"status":"UP"}` 200（修复前 503） |
| 真实 DB 冒烟 | Node 脚本 33 条断言，独立库 `blogs_smoke`（真实 MySQL 8.0.42） | ✅ 33 PASS / 0 FAIL |
| 前端→后端直连 | `curl http://localhost:5173/api/v1/articles`（vite proxy） | ✅ 200，返回后端 `{"code":200,"message":"操作成功"…}`，证明 mock 已关 |

单测覆盖（修复后，全项目 28 项，1 项按条件跳过）：

- `blogs-common` 2 项、`blogs-core` 3 项、`blogs-admin` 23 项（`ArticleServiceImplTest` 5、`AuthServiceImplTest` 6、`PageResultTest` 4、`PublicApiContractTest` 5、`RestSecurityExceptionHandlerTest` 2、`BlogsAdminApplicationTests` 1 skipped）。
- 本轮新增 2 个回归断言：
  - `ArticleServiceImplTest#adminDetailKeepsStatusAndContent`：管理端详情必须回填 `status` 与 `contentMd`，且不累加阅读量。
  - `PublicApiContractTest#detailVoMustHaveStatusForAdminEdit`：反射锁 `ArticleDetailVO.status`，防止后续重构再次删字段。

## 5. 真实链路冒烟（2026-11-07 已实跑通过）

环境：MySQL 8.0.42（`root`）、JDK 17、独立库 `blogs_smoke`（跑完已 DROP，`blogs_db` 零写入）。

```bash
# 0) 独立库，避免污染 blogs_db
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS blogs_smoke CHARACTER SET utf8mb4;"

# 1) 起后端（dev profile，schema.sql 自动建表；首次启动自动建 admin 账号）
export JWT_SECRET="不少于32字节的随机串"
export DB_USERNAME=root
export DB_PASSWORD='你的本机密码'
export ADMIN_INIT_PASSWORD='初始管理员密码'
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/blogs_smoke?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false'
java -jar blogs-admin/target/blogs-admin-0.0.1-SNAPSHOT.jar --server.port=8080

# 2) 起前端（.env.local 已就绪，VITE_USE_MOCK=false）
cd frontend && npm run dev   # http://localhost:5173
```

> ⚠️ 排障记录：本次启动时 Tomcat 初始化成了 **51493** 而非 8080，报 `port already in use`。
> 原因是宿主环境注入了 `SERVER_PORT`，覆盖了 `application.yaml` 的 `server.port`。
> 显式追加 `--server.port=8080`（命令行参数优先级高于环境变量）即恢复正常。

33 条断言覆盖（全部 PASS）：

| 分组 | 断言要点 |
| --- | --- |
| 鉴权 | 无 token 访问 `/admin/**` → 401；登录返回 token 与 `userInfo.role=ADMIN`；`/auth/me` 可用 |
| 分类 | 创建返回 id；同级重名 → 400（非 500）；有文章引用时删除 → 400 且文案含篇数；无引用可删 |
| 管理端列表 | `total` 正确；每项含 `status`/`categoryName`，**不含 `contentMd`**；`status=0` 筛选不到已发布 |
| 管理端详情 | `contentMd` 完整、**`status=1`**、`contentHtml` 已渲染、`viewCount` 不自增 |
| 公开链路 | 只出已发布；详情阅读量 1→2 自增；关键词搜索命中；列表不含正文 |
| 编辑回填 | 用详情回填后原样 PUT：标题生效、**status 仍为 1（未被降级）**、正文未丢 |
| 删除 | 文章逻辑删除后公开详情 404、管理列表 total=0；分类随后可删 |

完成后已停止前后端进程、DROP `blogs_smoke`、清理 `.smoke/` 与 `frontend/dist`。

## 6. 遗留事项

1. **标签模块（阶段 3）**：`tag` / `article_tag` 表 + `/api/v1/tags`、`/api/v1/admin/tags` 未实现；前端已降级，接口上线后打开 `tagFilterEnabled` / `tagsReady`。
2. **Markdown 双渲染**：后端用 commonmark 生成 `contentHtml`，前端访客详情页仍用 `utils/markdown.js` 渲染 `contentMd`，两套渲染结果可能不一致；建议后续统一为「访客端直接用 `contentHtml`，编辑端预览保留前端轻量渲染」。
3. **浏览器端 UI 点击回归**：本轮只做到 HTTP 层（脚本 + vite proxy），弹窗、表格、分页等交互仍需人工点一遍（`frontend/INTEGRATION_CHECKLIST.md` 第 7 节）。
4. 前端 `index-*.js` 单 chunk 1.2MB（Element Plus 全量引入），生产前建议按需引入或 `manualChunks` 拆包。
5. `blogs-common` 里的 Redis starter 目前是空转依赖，确认短期不用可移除，减少启动与攻击面。
