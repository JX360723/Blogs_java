# WorkBuddy 施工单 · PHASE 4：标签模块 + 三处遗留修复

> **基线**：`main` 分支 2026-11-07（`c1a56a6` 之后）
> **用法**：整段复制第六节发给 WorkBuddy；前五节是给人和 AI 看的依据，不要复制。
> **预计改动**：新增 12 个文件、修改 8 个文件，约 1200 行。

---

## 一、你接手时的代码基线（已存在，**不要重复实现**）

### 1.1 已经做完的

| 位置 | 状态 |
| --- | --- |
| `blogs-admin/controller/AdminArticleController` | 5 个接口：列表 / 新建 / 详情 / 修改 / 删除 |
| `blogs-admin/controller/AdminCategoryController` | 4 个接口：列表 / 新建 / 修改 / 删除 |
| `blogs-admin/controller/PublicArticleController`、`PublicCategoryController` | 公开侧列表 / 详情 |
| `blogs-core/support/MarkdownRenderer` | Markdown → HTML，服务端渲染 |
| `blogs-model/vo/AdminArticleSummaryVO` | 管理端列表 VO，含 `status` / `updatedAt` |
| `blogs-model/vo/AdminArticleDetailVO` | 管理端详情 VO，含 `contentMd` / `status`（2026-11-07 新增） |
| `blogs-model/vo/ArticleDetailVO` | 公开详情 VO，含 `contentMd` / `contentHtml`，**刻意不含 `status`** |
| `blogs-model/dto/ArticleSaveRequest` | `title` / `summary` / `contentMd` / `categoryId` / `coverImage` / `isTop` / `status` |
| `blogs-model/dto/ArticleQueryRequest` | `pageNum` / `pageSize` / `categoryId` / `keyword` |
| `blogs-admin/resources/db/schema.sql` | 三张表：`admin_user` / `article` / `category` |
| `blogs-admin/src/test/.../PublicApiContractTest` | 反射锁公开接口契约，公开 VO 出现 `status` 会直接失败 |

### 1.2 完全没有的

`tag` / `article_tag` 表、`Tag` / `ArticleTag` 实体、`TagMapper` / `ArticleTagMapper`、`TagService`、`TagController`。
全仓库搜 `Tag` 只有前端文件和 `docs/PHASE3_TAG_DESIGN.md`，后端一行都没有。

### 1.3 前端现状

| 文件 | 现状 |
| --- | --- |
| `frontend/src/api/tags.js` | 5 个函数已写好，路径与本文契约一致，**不用改** |
| `frontend/src/views/admin/TagManage.vue` | 页面完整，现在走 mock |
| `frontend/src/views/public/ArticleList.vue` | `tagFilterEnabled = false`（第 114 行），筛选条隐藏并显示「阶段3开放」 |
| `frontend/src/views/public/ArticleDetail.vue` | **没有标签展示**，需要新增 |
| `frontend/src/views/admin/ArticleManage.vue` | 编辑弹窗**没有标签选择器**，需要新增 |
| `frontend/src/mock/index.js` | 标签 5 接口 + `tagId` 过滤已实现，可作行为参照 |

---

## 二、任务 A：标签模块（主体）

### A1 建表

追加到 `blogs-admin/src/main/resources/db/schema.sql` 末尾，风格与现有三张表一致（`CREATE TABLE IF NOT EXISTS`、`utf8mb4_general_ci`、`created_at` / `updated_at`）。

```sql
CREATE TABLE IF NOT EXISTS `tag`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name`        VARCHAR(30) NOT NULL COMMENT '标签名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '标签描述',
    `created_at`  DATETIME    DEFAULT NULL COMMENT '创建时间',
    `updated_at`  DATETIME    DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='标签表';

CREATE TABLE IF NOT EXISTS `article_tag`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `article_id` BIGINT   NOT NULL COMMENT '文章ID',
    `tag_id`     BIGINT   NOT NULL COMMENT '标签ID',
    `created_at` DATETIME DEFAULT NULL COMMENT '绑定时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_tag` (`article_id`, `tag_id`),
    KEY `idx_article_tag_tag` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='文章标签关联表';
```

> **两张表都不要 `deleted` 字段。** 原因见坑点 1，这是本单最容易写错的地方。

### A2 实体与 Mapper

`blogs-model/src/main/java/com/xdl/model/entity/`

```java
// Tag.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tag")
public class Tag implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ArticleTag.java —— 关联表，无业务字段
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_tag")
public class ArticleTag implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long tagId;
    private LocalDateTime createdAt;
}
```

`blogs-core/src/main/java/com/xdl/blogscore/mapper/`：`TagMapper extends BaseMapper<Tag>`、`ArticleTagMapper extends BaseMapper<ArticleTag>`，都加 `@Mapper`。

### A3 DTO / VO

`blogs-model/src/main/java/com/xdl/model/dto/TagSaveRequest.java`

```java
@Data
public class TagSaveRequest implements Serializable {
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 30, message = "标签名称最多 30 个字符")
    private String name;

    @Size(max = 255, message = "标签描述最多 255 个字符")
    private String description;
}
```

`blogs-model/src/main/java/com/xdl/model/vo/TagVO.java`：`id` / `name` / `description` / `createdAt`，提供 `from(Tag)` 静态工厂。

**给已有类加字段：**

| 类 | 新增字段 | 说明 |
| --- | --- | --- |
| `ArticleSaveRequest` | `private List<Long> tagIds;` | 不加 `@NotNull`，null 表示「不修改」 |
| `AdminArticleDetailVO` | `private List<Long> tagIds;` | 编辑回填用，不能是 null |
| `AdminArticleDetailVO` | `private List<TagVO> tags;` | 管理端展示标签名 |
| `ArticleDetailVO` | `private List<TagVO> tags;` | 访客详情页展示标签 |
| `ArticleQueryRequest` | `private Long tagId;` | 公开列表按标签筛选 |

> `ArticleSummaryVO` **不加** `tags`。列表页卡片不展示标签，加了只会引出 N+1 查询。

### A4 TagService

`blogs-core/.../service/TagService.java` + `impl/TagServiceImpl.java`，风格照抄 `CategoryServiceImpl`。

```java
List<TagVO> listAllOrdered();          // ORDER BY created_at DESC, id DESC
Long createTag(TagSaveRequest request); // 名称唯一，重复抛 BAD_REQUEST
void updateTag(Long id, TagSaveRequest request);
void deleteTag(Long id);                // 物理删除 + 级联删 article_tag
```

**`deleteTag` 必须在一个事务里做两件事**：

```java
@Transactional(rollbackFor = Exception.class)
public void deleteTag(Long id) {
    requireTag(id);
    // 先解绑再删标签：article_tag 没有外键约束，
    // 只删 tag 会留下指向不存在标签的孤儿关联行，
    // 文章详情的标签列表会查不到名字而静默丢标签
    articleTagMapper.delete(Wrappers.lambdaQuery(ArticleTag.class)
            .eq(ArticleTag::getTagId, id));
    tagMapper.deleteById(id);
}
```

**名称判重**：`selectCount(name = ? and id != ?) > 0` → 抛 `BusinessException(ResultCode.BAD_REQUEST, "标签名称已存在")`。`updateTag` 判重时排除自身。

### A5 Controller

```java
// PublicTagController —— 公开，无需鉴权
@RestController
@RequestMapping("/api/v1/tags")
public class PublicTagController {
    @GetMapping
    public Result<List<TagVO>> list() { ... }
}

// AdminTagController —— /api/v1/admin/** 已由 SecurityConfig 拦 hasRole("ADMIN")
@RestController
@RequestMapping("/api/v1/admin/tags")
public class AdminTagController {
    @GetMapping
    public Result<List<TagVO>> list() { ... }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody TagSaveRequest request) { ... }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TagSaveRequest request) { ... }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { ... }
}
```

### A6 文章侧接入（**本单最容易出错的部分**）

**① 公开列表按标签筛选** —— `ArticleServiceImpl.pagePublished`：

```java
Long tagId = query.getTagId();
if (tagId != null && tagId > 0) {
    List<Long> articleIds = articleTagMapper.selectList(
                    Wrappers.lambdaQuery(ArticleTag.class).eq(ArticleTag::getTagId, tagId))
            .stream().map(ArticleTag::getArticleId).toList();
    if (articleIds.isEmpty()) {
        // 必须在这里短路返回空页。
        // 若直接 .in(Article::getId, 空集合)，MyBatis-Plus 会生成 IN ()，
        // MySQL 直接语法报错，接口 500 而不是返回空列表
        return PageResult.of(Collections.emptyList(), 0, pageNum, pageSize);
    }
    wrapper.in(Article::getId, articleIds);
}
```

`status = 1` 的强制条件保持不变 —— `tagId` 只是筛选条件，不是可见性开关。

**② 公开详情返回 tags** —— `getPublishedDetail` 里补：

```java
List<TagVO> tags = loadTags(article.getId());
return ArticleDetailVO.from(article, categoryName, currentViewCount, tags);
```

**③ 管理端详情返回 tagIds** —— `getAdminDetail` 里补：

```java
List<Long> tagIds = articleTagMapper.selectList(
                Wrappers.lambdaQuery(ArticleTag.class).eq(ArticleTag::getArticleId, id))
        .stream().map(ArticleTag::getTagId).toList();
return AdminArticleDetailVO.from(article, categoryName, tagIds);
```

> `tagIds` 必须是 `[]` 而不是 `null`。前端 `el-select` 绑 `null` 会报 `Cannot read properties of null`。

**④ 新建 / 修改文章时同步绑定** —— 新增私有方法：

```java
/**
 * 同步文章标签绑定
 * <p>
 * tagIds 为 null 表示「不修改」——前端只改标题、没动标签选择器时传 null，
 * 此时必须原样保留已有绑定，否则改个错别字就把标签全清掉。
 * tagIds 为 [] 表示「清空」——用户主动把标签都删了。
 * 两者语义必须区分，不能用同一个分支处理。
 */
private void syncArticleTags(Long articleId, List<Long> tagIds) {
    if (tagIds == null) {
        return;
    }
    // 先删后插：不做差集计算，简单且不会漏
    articleTagMapper.delete(Wrappers.lambdaQuery(ArticleTag.class)
            .eq(ArticleTag::getArticleId, articleId));
    if (tagIds.isEmpty()) {
        return;
    }
    List<Long> distinct = tagIds.stream().filter(Objects::nonNull).distinct().toList();
    if (distinct.isEmpty()) {
        return;
    }
    if (distinct.size() > 10) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "单篇文章最多绑定 10 个标签");
    }
    // 过滤掉已被别人删掉的标签，而不是整体报错：
    // 前端选择器里的列表可能是几分钟前拉的，用户点保存时标签可能已经不存在了
    List<Long> existing = tagMapper.selectList(
                    Wrappers.lambdaQuery(Tag.class).in(Tag::getId, distinct))
            .stream().map(Tag::getId).toList();
    for (Long tagId : existing) {
        articleTagMapper.insert(ArticleTag.builder()
                .articleId(articleId)
                .tagId(tagId)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
```

在 `createArticle` 的 `save(article)` 之后、`updateArticle` 的 `update(...)` 之后各调一次 `syncArticleTags(article.getId(), request.getTagIds())`。两个方法都要加 `@Transactional(rollbackFor = Exception.class)`。

---

## 三、任务 B：多级分类环检测（约 20 行）

**问题**：`CategoryServiceImpl.validateParent` 只拦了「父分类是自己」和「父分类不存在」，A→B→A 这种跨层成环没拦。用户把 A 的父级设成 B、再把 B 的父级设成 A，两个分类就互相指向，前端递归渲染分类树时死循环。

**改法**：`validateParent` 里沿 `parent_id` 向上回溯，撞到 `selfId` 就拒绝。

```java
private void validateParent(Long parentId, Long selfId) {
    if (parentId == null || parentId <= 0) {
        return;
    }
    if (Objects.equals(parentId, selfId)) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "父分类不能是自己");
    }
    Long current = parentId;
    // 深度上限兜底：万一库里已经存在环（历史数据），
    // 这里不能跟着无限循环，10 层足够个人博客的分类层级
    for (int depth = 0; depth < 10; depth++) {
        Category parent = categoryMapper.selectById(current);
        if (parent == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "父分类不存在");
        }
        if (Objects.equals(parent.getId(), selfId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "不能把分类移动到自己的子分类下，会形成循环引用");
        }
        Long next = parent.getParentId();
        if (next == null || next <= 0) {
            return; // 已到根，无环
        }
        current = next;
    }
    throw new BusinessException(ResultCode.BAD_REQUEST, "分类层级过深或已存在循环引用");
}
```

注意 `updateCategory` 调用时要传 `selfId = id`，`createCategory` 传 `selfId = null`（新建时还不可能有子分类）。

---

## 四、任务 C：管理端 401/403 的 MockMvc 测试（约 60 行）

**背景**：`SecurityConfig` 把 `/api/v1/admin/**` 配成 `hasRole("ADMIN")`，但至今没有任何自动化测试证明「不带 token 真的返回 401」「带普通用户 token 真的返回 403」。只能靠 curl 手工试，改动 SecurityConfig 时没人拦得住回归。

新建 `blogs-admin/src/test/java/com/xdl/blogsadmin/web/AdminArticleSecurityTest.java`：

```java
@WebMvcTest(controllers = AdminArticleController.class)
@AutoConfigureMockMvc
class AdminArticleSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArticleService articleService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;   // 按实际类名调整

    @Test
    @DisplayName("不带 token 访问管理端接口：401，且不进入业务层")
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/articles"))
                .andExpect(status().isUnauthorized());
        verify(articleService, never()).pageForAdmin(any());
    }

    @Test
    @DisplayName("带非管理员 token：403")
    void nonAdminTokenReturns403() throws Exception { ... }

    @Test
    @DisplayName("带管理员 token：200")
    void adminTokenReturns200() throws Exception { ... }
}
```

**实现提示**：
- `@WebMvcTest` 不会加载 `DataSource`，`ArticleService` 用 `@MockBean` 顶掉
- `SecurityConfig` 会被加载，JWT 过滤器依赖的 `JwtTokenProvider` 也要 `@MockBean`
- 如果 `@WebMvcTest` 因为 `BlogsApplication` 的扫描范围报错，改用 `@SpringBootTest(classes = {BlogsApplication.class, SecurityConfig.class})` + `@AutoConfigureMockMvc`，并排除数据源自动配置
- 断言 401/403 时不要断言响应体文案，只断言状态码，避免文案改动导致测试假失败

---

## 五、坑点（每条都是踩过的，逐条对照）

1. **`tag` 和 `article_tag` 都不能有 `deleted` 字段。**
   `application.yaml` 里配了全局 `logic-delete-field: deleted`，实体上再加 `@TableLogic` 就变成逻辑删除。而 `tag.name` 上有唯一索引：删掉标签「Java」后行还在（`deleted=1`），再新建一个「Java」会撞唯一索引，接口 500，用户看到「标签名称已存在」但列表里根本找不到它。
   `article_tag` 同理：解绑后再绑同一标签会撞 `uk_article_tag`。
   **所以两张表都物理删除**，删除时用事务保证级联清理。

2. **`tagIds` 的 `null` 和 `[]` 必须分开处理。**
   `null` = 前端没动标签（只改标题），保留原绑定；`[]` = 用户主动清空。
   写成 `if (tagIds != null && !tagIds.isEmpty())` 一个分支，会导致「编辑文章时不动标签 → 标签全被清掉」。

3. **管理端详情接口必须返回 `status`，不要退回 `ArticleDetailVO`。**
   公开 VO 刻意不含 `status`，一旦复用，前端 `detail.status ?? 0` 恒为 0，编辑已发布文章保存后变成草稿，文章从公开列表消失，页面上没有任何报错。2026-11-07 已专门修过这个问题，`AdminArticleServiceImplTest` 有断言守着，改回去测试会红。

4. **管理端列表不能返回 `contentMd`。**
   正文是 `LONGTEXT`，一页 20 条会把几 MB 塞进响应。编辑必须先调详情接口。

5. **公开 VO 绝不能出现 `status` 字段。**
   `PublicApiContractTest` 用反射断言守着，加了就是测试失败。公开接口一旦支持 `status` 过滤，访客能用 `?status=0` 直接拉草稿。

6. **`in()` 传空集合会生成 `IN ()`，MySQL 语法报错。**
   按标签筛选时如果该标签下没有文章，必须先判空短路返回空页，不能直接 `.in(Article::getId, emptyList)`。

7. **`Collections.emptyMap()` 不能换成 `Map.of()`。**
   `loadCategoryNames` 里 `categoryNames.get(null)` 会调用 `Map.of()` 的 `get`，对 null 键做 `requireNonNull` 直接 NPE。本页文章全部未分类时列表接口会 500。

8. **`blogs-common` 和 `blogs-core` 的 `spring-boot-starter-test` 已于 2026-11-07 补上。**
   这两个模块有测试类但缺依赖，`mvn test` 会直接编译失败。如果你在别的分支上发现又没了，先补依赖再谈别的。

9. **`ArticleSaveRequest.status` 是 `@NotNull`。**
   前端编辑弹窗必须回填真实状态，不能靠默认值。

10. **单篇标签上限 10 个，超了抛 400。**
    校验放在 `syncArticleTags` 里，不要放到 DTO 的 `@Size` 上——`@Size(max = 10)` 报的文案是「个数必须在 0 和 10 之间」，用户看不懂。

---

## 六、可复制的完整提示词

```
你是这个个人知识博客项目的后端开发。仓库：Blogs_java，当前在 main 分支。

【项目技术栈，不要改】
Spring Boot 3.4.0 + Java 21 + MyBatis-Plus 3.5.9 + MySQL 8.0 + Spring Security + jjwt 0.12.6。
Maven 五模块：blogs-model（实体/DTO/VO）、blogs-common（Result/异常/JWT）、blogs-security（Spring Security 配置）、blogs-core（Service/Mapper）、blogs-admin（Controller/配置/schema.sql）。
统一响应包络 Result<T> = {code, message, data, timestamp}，字段名是 message 不是 msg。
分页请求 pageNum/pageSize，响应 PageResult {list, total, pageNum, pageSize, pages}。

【已经做完的，不要重写】
AdminArticleController 5 个接口（列表/新建/详情/修改/删除）、AdminCategoryController 4 个接口、MarkdownRenderer、AdminArticleSummaryVO、AdminArticleDetailVO（含 contentMd 和 status）、ArticleDetailVO（公开侧，刻意不含 status）。
后端标签相关代码一行都没有，本轮从零开始。

【本轮任务】

一、标签模块

1. 在 blogs-admin/src/main/resources/db/schema.sql 末尾追加两张表：

CREATE TABLE IF NOT EXISTS `tag`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name`        VARCHAR(30) NOT NULL COMMENT '标签名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '标签描述',
    `created_at`  DATETIME    DEFAULT NULL COMMENT '创建时间',
    `updated_at`  DATETIME    DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='标签表';

CREATE TABLE IF NOT EXISTS `article_tag`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `article_id` BIGINT   NOT NULL COMMENT '文章ID',
    `tag_id`     BIGINT   NOT NULL COMMENT '标签ID',
    `created_at` DATETIME DEFAULT NULL COMMENT '绑定时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_tag` (`article_id`, `tag_id`),
    KEY `idx_article_tag_tag` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='文章标签关联表';

两张表都不要 deleted 字段，也不要 @TableLogic。项目 application.yaml 配了全局 logic-delete-field: deleted，
加了这个字段就变成逻辑删除，删除后再建同名标签会撞唯一索引报 500。

2. blogs-model 新增：entity/Tag、entity/ArticleTag、dto/TagSaveRequest、vo/TagVO。
   TagSaveRequest 字段：name（@NotBlank @Size(max=30)）、description（@Size(max=255)）。
   TagVO 字段：id、name、description、createdAt，带 from(Tag) 静态工厂。

3. blogs-core 新增：mapper/TagMapper、mapper/ArticleTagMapper（都加 @Mapper），
   service/TagService + impl/TagServiceImpl，风格照抄 CategoryServiceImpl。
   方法：listAllOrdered（created_at DESC, id DESC）、createTag、updateTag、deleteTag。
   deleteTag 必须 @Transactional，先 delete article_tag where tag_id=?，再 delete tag，否则留下孤儿关联行。
   标签名称唯一，重复抛 BusinessException(ResultCode.BAD_REQUEST, "标签名称已存在")，updateTag 判重排除自身。

4. blogs-admin 新增：controller/PublicTagController（GET /api/v1/tags）、
   controller/AdminTagController（GET/POST /api/v1/admin/tags、PUT/DELETE /api/v1/admin/tags/{id}）。
   鉴权不用写代码，SecurityConfig 已经拦 /api/v1/admin/** 要求 hasRole("ADMIN")。

5. 给已有类加字段：
   - ArticleSaveRequest 加 private List<Long> tagIds;（不加 @NotNull）
   - AdminArticleDetailVO 加 private List<Long> tagIds; 和 private List<TagVO> tags;
   - ArticleDetailVO 加 private List<TagVO> tags;
   - ArticleQueryRequest 加 private Long tagId;
   - ArticleSummaryVO 不要加 tags，列表页不展示标签，加了只会引出 N+1。

6. 文章侧接入：
   a) 公开列表 pagePublished 支持 tagId 筛选：先查 article_tag 拿到 articleId 列表，
      如果为空必须直接返回空页 PageResult.of(Collections.emptyList(), 0, pageNum, pageSize)，
      不能直接 .in(Article::getId, 空集合)，那会生成 IN () 让 MySQL 语法报错、接口 500。
      status=1 的强制条件保持不变，tagId 只是筛选条件不是可见性开关。
   b) getPublishedDetail 补 tags。
   c) getAdminDetail 补 tagIds 和 tags。tagIds 必须是 [] 不能是 null，
      前端 el-select 绑 null 会报 Cannot read properties of null。
   d) 新增私有方法 syncArticleTags(Long articleId, List<Long> tagIds)：
      tagIds 为 null 直接 return（表示不修改，保留原绑定）；
      tagIds 为 [] 表示清空；
      否则先 delete 再批量 insert，过滤掉库里已不存在的 tagId 而不是整体报错，
      去重后超过 10 个抛 BusinessException(ResultCode.BAD_REQUEST, "单篇文章最多绑定 10 个标签")。
      createArticle 和 updateArticle 都要加 @Transactional(rollbackFor = Exception.class)，
      并在写库之后调用 syncArticleTags。

二、多级分类环检测
CategoryServiceImpl.validateParent 目前只拦了「父分类是自己」和「父分类不存在」，
A→B→A 这种跨层成环没拦。改成沿 parent_id 向上回溯，撞到 selfId 就抛 400，
回溯深度上限 10 层兜底（防止历史脏数据里已有环导致死循环）。
updateCategory 传 selfId=id，createCategory 传 selfId=null。

三、管理端 401/403 的 MockMvc 测试
新建 blogs-admin/src/test/java/com/xdl/blogsadmin/web/AdminArticleSecurityTest.java，
用 @WebMvcTest(controllers = AdminArticleController.class) + @AutoConfigureMockMvc，
ArticleService 和 JwtTokenProvider 用 @MockBean 顶掉（@WebMvcTest 不加载 DataSource）。
三个用例：不带 token 访问 GET /api/v1/admin/articles 期望 401 且不进入业务层；
带非管理员 token 期望 403；带管理员 token 期望 200。
只断言状态码，不要断言响应体文案。

【硬性约束】
- 前端是 Vue3 + 纯 JavaScript，禁用 TypeScript。
- 不要改公开 VO 的 status 契约。公开接口出现 status 会让 PublicApiContractTest 失败，
  而且访客能用 ?status=0 拉草稿，属于安全事故。
- 管理端详情接口必须返回 AdminArticleDetailVO（含 status），不要退回 ArticleDetailVO。
  公开 VO 没有 status，编辑表单会把它回填成草稿，保存后已发布文章从列表消失，页面上没有任何报错。
- 管理端列表不要返回 contentMd，正文是 LONGTEXT，一页 20 条会塞几 MB。
- 不要用 Map.of() 做空 Map 返回，loadCategoryNames 里会 get(null) 触发 NPE，改用 Collections.emptyMap()。
- 新增的 Service 方法都要写 Javadoc 说明「为什么这么做」，特别是 syncArticleTags 的 null 与 [] 语义、
  deleteTag 的级联清理、环检测的深度上限。
- 所有新增类和方法注释用中文。

【交付物】
1. 两张表的 DDL 追加到 schema.sql
2. 12 个新增文件：Tag、ArticleTag、TagSaveRequest、TagVO、TagMapper、ArticleTagMapper、
   TagService、TagServiceImpl、PublicTagController、AdminTagController、AdminArticleSecurityTest
3. 修改的文件：ArticleSaveRequest、AdminArticleDetailVO、ArticleDetailVO、ArticleQueryRequest、
   ArticleServiceImpl、CategoryServiceImpl、schema.sql
4. 一份 docs/PHASE4_TAG_IMPLEMENTATION.md，说明：实现了哪些接口、tagIds 的 null/[] 语义怎么处理、
   标签删除为什么用物理删除、环检测的回溯逻辑、本地 mvn test 结果
5. 不要提交，不要 push，改完留在工作区由 PM 统一提交开 PR

【验收标准】
1. mvn -pl blogs-admin -am test 全绿
2. 给文章打 2 个标签，公开列表 ?tagId=x 能筛选命中；解绑后筛选不到
3. 用不存在的 tagId 筛选返回空列表而不是 500
4. 编辑文章只改标题、不动标签选择器（tagIds 传 null），保存后标签还在
5. 删除标签后，文章详情不再显示该标签，article_tag 里没有孤儿行
6. 把分类 A 的父级设成自己的子分类 B，接口返回 400 而不是死循环
7. 不带 token 请求 GET /api/v1/admin/articles 返回 401
```

---

## 七、不要做的事

- 不要重写已完成的 `AdminArticleController` / `AdminCategoryController` / `MarkdownRenderer`
- 不要给 `article_tag` 加 `deleted` 或外键约束（外键会让 `article` 逻辑删除后无法清理关联）
- 不要给 `ArticleSummaryVO` 加 `tags`
- 不要做标签合并、标签云、标签颜色、标签热度排序（PHASE 5 之后再说）
- 不要改前端 `api/tags.js` 的路径，它已经和后端契约对齐了
- 不要动 `sql/` 目录和 `application.txt`（待 PM 决策）

---

## 八、前端配套（后端交付后单独一轮，约 100 行）

| 文件 | 改动 |
| --- | --- |
| `frontend/src/views/public/ArticleList.vue` | 第 114 行 `tagFilterEnabled` 改 `true`，删掉「阶段3开放」占位分支 |
| `frontend/src/views/public/ArticleDetail.vue` | 正文下方渲染 `article.tags`，用 `el-tag` |
| `frontend/src/views/admin/ArticleManage.vue` | 编辑弹窗加 `el-select` 多选，数据源 `getAdminTags()`，绑 `form.tagIds`；`openEdit` 里 `form.tagIds = detail.tagIds \|\| []` |
| `frontend/src/views/admin/ArticleManage.vue` | 列表操作列加「发布 / 下架」快捷按钮，直接 `updateArticle(id, { ...row, status: 1 \| 2 })`，省得为了改状态打开编辑弹窗 |

> 最后一条是体验优化：目前改状态必须打开编辑弹窗、翻到状态单选、点保存，三步操作。列表页一键发布更顺手。
