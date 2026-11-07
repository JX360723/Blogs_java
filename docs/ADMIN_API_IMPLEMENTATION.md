# 管理端接口实现与验证记录

> 日期：2026-11-03  
> 基线：`origin/main` = `e1ca1e8`  
> 依据：`docs/WORKBUDDY_PROMPT_PHASE3_ADMIN_API.md`（施工单，字段与行为为准）、  
> `docs/API_CONTRACT.md` 6.2.3 / 6.2.4、`docs/BACKEND_DESIGN_DB_AND_API.md` 4.5  
> 范围：PHASE 3 管理端 8 个接口（文章 4 + 分类 4），另附 1 个编辑回填用详情接口

---

## 一、实现的接口

| # | 方法     | 路径                              | 响应                                  | 说明                          |
| - | ------ | ------------------------------- | ----------------------------------- | --------------------------- |
| 1 | GET    | `/api/v1/admin/articles`        | `PageResult<AdminArticleSummaryVO>` | 含草稿/已下架，`status` 为空表示全部     |
| 2 | POST   | `/api/v1/admin/articles`        | `Long`（新 ID）                        | 作者取 JWT 主体，`viewCount` 强制 0 |
| 3 | PUT    | `/api/v1/admin/articles/{id}`   | `Void`                              | 只改可编辑字段，不动作者/创建时间/阅读量       |
| 4 | DELETE | `/api/v1/admin/articles/{id}`   | `Void`                              | 逻辑删除（`deleted=1`）           |
| 5 | GET    | `/api/v1/admin/categories`      | `List<CategoryVO>`                  | 扁平、`sortOrder ASC, id ASC`  |
| 6 | POST   | `/api/v1/admin/categories`      | `Long`（新 ID）                        | 同级重名校验，`parentId` 兜底 0      |
| 7 | PUT    | `/api/v1/admin/categories/{id}` | `Void`                              | 判重时排除自身                     |
| 8 | DELETE | `/api/v1/admin/categories/{id}` | `Void`                              | **有文章引用则拒绝**并提示篇数           |
| 附 | GET    | `/api/v1/admin/articles/{id}`   | `AdminArticleDetailVO`              | 编辑回填正文与状态用（施工单未列，见 2.2 说明）  |

> **修订 2026-11-07：详情接口返回类型由 `ArticleDetailVO` 改为 `AdminArticleDetailVO`。**
> 公开侧 `ArticleDetailVO` 刻意不含 `status`（防止访客探测草稿），但它同时被管理端详情接口复用，
> 导致前端编辑表单拿不到真实状态：`detail.status ?? 0` 恒等于 0，一篇已发布文章打开编辑框时
> 状态单选显示为「草稿」，用户改个错别字点保存，`PUT` 提交 `status=0`，文章立刻从公开列表消失——
> 页面上没有任何报错，属于静默的数据/可见性事故。
> 现在管理端详情单独返回 `AdminArticleDetailVO`（含 `status`），公开 VO 保持不含 `status`，
> 两条契约各守一边，`PublicApiContractTest` 与 `AdminArticleServiceImplTest` 分别锁死。

路径与前端 `frontend/src/api/articles.js`、`categories.js` 逐一对应，未改名、未调整层级。  
鉴权沿用 `SecurityConfig` 的 `/api/v1/admin/**` → `hasRole("ADMIN")`，Controller 内未写任何鉴权代码。

---

## 二、关键设计决策

### 2.1 公开端与管理端参数、VO 彻底隔离

| 关注点   | 公开端                                                 | 管理端                                             |
| ----- | --------------------------------------------------- | ----------------------------------------------- |
| 查询参数  | `ArticleQueryRequest`（**无 `status`**，一行未改）          | `AdminArticleQueryRequest`（含 `status`，空表示全部）    |
| 列表 VO | `ArticleSummaryVO`（**无 `status`/`updatedAt`**，一行未改） | `AdminArticleSummaryVO`（有 `status`/`updatedAt`） |

若共用一个查询参数类，访客请求 `GET /api/v1/articles?status=0` 就能拉草稿。  
实测确认该参数在公开端被忽略：`?status=0` 返回的仍是已发布文章（`total` 与不带参数一致）。

### 2.2 管理端列表不返回正文（按施工单执行）与编辑回填闭环

列表按施工单要求显式 `select`，**排除 `content_md` / `content_html` / `ai_summary`**，只取  
`id/title/summary/category_id/cover_image/status/view_count/is_top/created_at/updated_at`。

这里有一个必须留意的联动：前端 `ArticleManage.vue` 的 `openEdit(row)` 目前是

|                                                                                |   |                                            |
| ------------------------------------------------------------------------------ | - | ------------------------------------------ |
| \`contentMd: row.contentMd                                                     |   | ''\`，**没有调用详情接口**。列表不再返回正文后，点「编辑」→ 正文框为空 → |
| 保存会把原文覆盖成空串。因此本轮保留了 `GET /api/v1/admin/articles/{id}`（返回 `contentMd` 与         |   |                                            |
| `contentHtml`），前端只需在 `openEdit` 里加一行 `await getAdminArticle(row.id)` 即可闭掉这个风险。 |   |                                            |
| （`frontend/src/api/articles.js` 目前没有该函数，需要前端补一个 `getAdminArticle`。）            |   |                                            |

### 2.3 更新用显式 `set`，而不是 `updateById`

`updateById` 走 MyBatis-Plus 的 `NOT_NULL` 策略，null 字段整条忽略。实测发现两个后果：

- 用户在编辑页把「分类」清空（`el-select` clearable → `null`）后，`category_id` 根本没写回，  
  文章仍挂在用户以为已移走的分类上，进而导致分类删除被「还有 N 篇文章」挡住；
- 前端清空摘要/封面提交的是 `""`（空串非 null），能正常清空 —— 所以两者行为不一致，更难排查。

改为 `update(entity, wrapper)`：实体承载普通字段（同时让 `MybatisMetaObjectHandler` 刷新 `updated_at`），  
`categoryId` 由 wrapper 显式 `set`，null 也能落库。实测清空分类后库中 `category_id` 为 `NULL`。

### 2.4 分类删除：拒绝而非置空

施工单要求「有文章引用则拒绝」，与 `BACKEND_DESIGN` 早期「建议置空」的方案相反，本轮按施工单执行：

- 仍有子分类 → `400 该分类下还有子分类，请先删除子分类`
- 仍被未删除文章引用 → `400 该分类下还有 N 篇文章，请先移走或删除`
- 均不级联删除文章（个人博客误删代价太高）

`BACKEND_DESIGN_DB_AND_API.md` 第六节待决策项 2 的结论已同步改为「拒绝」。

### 2.5 `contentHtml` 仍由后端渲染

`ArticleSaveRequest` 不接收 `contentHtml`，入库值一律由 `MarkdownRenderer`（commonmark 0.22.0，  
`escapeHtml(true)`）从 `contentMd` 渲染，避免前端传什么就存什么。  
**注意：这需要 `blogs-core/pom.xml` 与根 `pom.xml` 里的 commonmark 依赖**（上一轮已引入），  
若团队决定改由前端渲染，删掉 `MarkdownRenderer` 与这两处依赖即可，接口签名不受影响。

---

## 三、新增 / 修改文件

**新增（blogs-model）**

- `dto/AdminArticleQueryRequest.java` — `pageNum`/`pageSize`(≤100)/`status`(0-2)，另含可选 `categoryId`/`keyword`
- `dto/ArticleSaveRequest.java` — `@JsonIgnoreProperties(ignoreUnknown = true)`，收 `id` 不报 400
- `dto/CategorySaveRequest.java` — 同上；`sortOrder` 0-999
- `vo/AdminArticleSummaryVO.java` — 管理端列表项（11 个字段，无正文）

**新增（blogs-admin）**

- `controller/AdminArticleController.java`
- `controller/AdminCategoryController.java`

**修改（blogs-core）**

- `service/ArticleService` + `impl` — 追加 `pageForAdmin` / `getAdminDetail` / `createArticle` / `updateArticle` / `deleteArticle`
- `service/CategoryService` + `impl` — 追加 `createCategory` / `updateCategory` / `deleteCategory`
- `support/MarkdownRenderer.java` — 上一轮新增，本轮沿用

**未改动**：`ArticleQueryRequest`、`ArticleSummaryVO`、`SecurityConfig`、`schema.sql`、  
`Result` / `ResultCode` / `GlobalExceptionHandler` / `MybatisPlusConfig` / `JacksonConfig`。

---

## 四、本轮修掉的两个真实缺陷

### 4.1 `Map.of().get(null)` 抛 NPE —— 未分类文章会让列表 500（影响公开端）

`loadCategoryNames` 在「本页文章全部没有分类」时返回 `Map.of()`。  
`Map.of()` 生成的不可变 Map 不允许 null 键，`get(null)` 直接 `requireNonNull` 抛 NPE。  
实测场景：新建一篇 `categoryId=null` 的草稿后访问管理端列表 → `500 系统内部错误`。

改为 `Collections.emptyMap()`（其 `get` 返回 null 而不抛）。**该缺陷同样存在于公开列表**——  
只是此前的验证数据都带分类，一直没触发。

### 4.2 `updateById` 忽略 null，分类清空不生效

见 2.3，复现路径：文章挂分类 1 → 编辑清空分类 → 删除分类报「还有 1 篇文章」。  
改为 wrapper 显式 `set` 后消失。

---

## 五、验收记录（施工单 10 项，逐条真实执行）

环境：本机 MySQL 8.0.42，**独立测试库 `blogs_smoke`**（结束即 `DROP DATABASE`，`blogs_db` 全程零写入）；  
`java -jar blogs-admin-0.0.1-SNAPSHOT.jar --server.port=8080`；  
编译 `mvn -B -Dmaven.compiler.release=17 -Dmaven.test.skip=true -pl blogs-admin -am clean package` → **BUILD SUCCESS**。  
凭据通过临时文件传给 curl（`-H @.smoke/hdr`），命令与输出中无令牌明文。

| #  | 验证项                                                          | 实际结果                                                                           | 结论 |
| -- | ------------------------------------------------------------ | ------------------------------------------------------------------------------ | -- |
| 1  | 不带 token 访问 `/admin/articles`                                | `401 {"code":401,"message":"未登录或登录已过期"}`                                       | ✅  |
| 2  | 带 token 访问                                                   | `200`，`{list:[],total:0,pageNum:1,pageSize:10,pages:0}`                        | ✅  |
| 3  | 建草稿（body 带 `id`/`userId=999`/`viewCount=8888`/`contentHtml`） | `200` 返回 `data=1`；库中 `user_id=1`、`view_count=0`、`content_html` 为服务端渲染值         | ✅  |
| 4  | 管理端查得到草稿、公开端查不到                                              | 管理端 `total=1`；`GET /articles` `total=0`；`GET /articles/1` `404`                | ✅  |
| 5  | 改成已发布                                                        | `PUT 200`；公开列表 `total=1`；详情 `viewCount` 1 → 2；`created_at` 未变、`updated_at` 已刷新 | ✅  |
| 6  | 改成已下架                                                        | `PUT 200`；公开详情 `404`、公开列表 `total=0`；管理端仍 `total=1`                             | ✅  |
| 7  | 删除                                                           | `DELETE 200`；再查详情 `404`、重复删 `404`；库中 `deleted=1` 且物理行仍在（`COUNT(*)=1`）          | ✅  |
| 8  | 建同名分类                                                        | 第二次 `400 同级下已存在同名分类`；更新成同名亦 `400`                                              | ✅  |
| 9  | 删有文章引用的分类                                                    | `400 该分类下还有 1 篇文章，请先移走或删除`；把文章分类清空后删除 `200`，库中 `category_id=NULL`              | ✅  |
| 10 | `?status=0` / `?status=1` / `?status=2` / 不带                 | `2` / `1` / `1` / `4` 条，互不串；`?status=5` → `400 状态筛选值非法`；`?status=`（空串）等同全部     | ✅  |

补充验证：

- 排序「置顶优先 + 创建时间倒序」：`已发布P(isTop=1)` 排在 `已下架O`、`草稿D` 之前 ✅
- 列表项字段恰为 11 个，**不含** `contentMd` / `contentHtml` ✅
- `pageNum=1&pageSize=2` → `total=4, pages=2` ✅；`pageSize=500` → `400` ✅
- 公开端 `?status=0` 参数被忽略，`total` 与不带参数一致 ✅
- 运行期日志 `ERROR` 计数 0、无未捕获异常 ✅

---

## 六、遗留与下一步

1. **前端 `openEdit` 需要改成先取详情**（见 2.2）。不改的话，「编辑已发布文章并保存」会把正文清空——  
   这是当前唯一的阻塞级联调风险，属于前端一行改动。
2. **权限自动化测试缺失**：仍靠 curl 实测。建议引入 `spring-boot-starter-test` + `MockMvc`，固化  
   「公开不带 status」「管理端 401/403」「草稿隔离」三条回归线。
3. **标签（阶段 3）未开始**：依赖 `tag` / `article_tag` 表，需先定表结构；前端当前 `tagFilterEnabled=false` 降级。
4. **多级分类环检测**：只拦了「父分类是自己」「父分类不存在」，`A→B→A` 跨层成环未做递归校验；  
   MVP 前端不提供父子选择，开放多级编辑时需补。
5. **未提交**：本轮改动仍在工作区，按项目规则由扣子统一推送开 PR、负责人审核合并。
