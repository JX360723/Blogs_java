---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 3137366841633914_0-drive/235678159419209852/Blogs_java/docs/WORKBUDDY_PROMPT_PHASE3_ADMIN_API.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 3137366841633914#1793708891856
    ReservedCode2: ""
---
# WorkBuddy 施工提示词：PHASE 3 管理端 8 个接口

> 用途：整段复制给 WorkBuddy 执行
> 基线：`origin/main` = `e1ca1e8`（2026-11-03 核验）
> 前端契约来源：`frontend/src/api/articles.js`、`frontend/src/api/categories.js`、`frontend/src/views/admin/*.vue`（逐字段核对）

## 一、当前真实状态（不要重做已完成的部分）

已经落地、**不要改动**：

| 类别 | 已有内容 |
| --- | --- |
| 公开接口 | `POST /api/v1/auth/login`、`GET /api/v1/auth/me`、`GET /api/v1/articles`、`GET /api/v1/articles/{id}`、`GET /api/v1/categories` |
| 基础设施 | `Result<T>` 包络、`ResultCode`、`BusinessException`、`GlobalExceptionHandler`、`JacksonConfig`、`MybatisPlusConfig`（分页插件已注册）、`MybatisMetaObjectHandler`（时间自动填充）、`AdminUserInitializer` |
| 安全 | `SecurityConfig` 已把 `/api/v1/admin/**` 全部拦成 `hasRole("ADMIN")`，**Controller 里不要重复写鉴权** |
| 数据库 | `blogs-admin/src/main/resources/db/schema.sql` 三表齐全（admin_user / article / category），启动自动建表 |
| 实体 | `Article`（含 `STATUS_DRAFT=0` / `STATUS_PUBLISHED=1` / `STATUS_OFFLINE=2` / `NOT_TOP=0`）、`Category`、`AdminUser` |
| 已有 VO | `ArticleSummaryVO`（公开用）、`ArticleDetailVO`、`CategoryVO`、`PageResult<T>`、`AdminUserInfo`、`LoginResponse` |

**完全没有的**（本次要做的全部）：

- `AdminArticleController`、`AdminCategoryController`
- `AdminArticleQueryRequest`、`ArticleSaveRequest`、`CategorySaveRequest`、`AdminArticleSummaryVO`
- `ArticleService` / `CategoryService` 里的写方法

## 二、8 个接口契约（按前端实际调用反推，字段名不可改）

### 2.1 文章

| # | 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/api/v1/admin/articles` | query: `pageNum`(默认1)、`pageSize`(默认10，最大100)、`status`(可选 0/1/2) | `Result<PageResult<AdminArticleSummaryVO>>` |
| 2 | POST | `/api/v1/admin/articles` | body: `ArticleSaveRequest` | `Result<Long>`（新文章 ID） |
| 3 | PUT | `/api/v1/admin/articles/{id}` | body: `ArticleSaveRequest` | `Result<Void>` |
| 4 | DELETE | `/api/v1/admin/articles/{id}` | — | `Result<Void>` |

**`AdminArticleSummaryVO` 字段（顺序不重要，字段名必须一致）**

```
id          Long
title       String
summary     String
categoryId  Long
categoryName String      // Service 层批量查分类填充，避免 N+1
coverImage  String
viewCount   Integer
isTop       Integer
status      Integer      // 关键：管理页要显示草稿/已发布/已下架
createdAt   LocalDateTime
updatedAt   LocalDateTime // 关键：管理页「更新时间」列用
```

**`ArticleSaveRequest` 字段**

```
title       String   @NotBlank @Size(max=120)
summary     String   @Size(max=500)          可空
contentMd   String                           可空
categoryId  Long                             可空（未分类）
status      Integer  @NotNull @Min(0) @Max(2) 必填
coverImage  String   @Size(max=255)          可空
isTop       Integer  @Min(0) @Max(1)         可空，默认 0
```

> 前端提交的是整个 `form` 对象，**会带一个 `id` 字段**（新建时为 `null`）。DTO 上加 `@JsonIgnoreProperties(ignoreUnknown = true)`，或显式加一个 `id` 字段并在 Service 里忽略它，两种都行，但**必须保证不会因为多出 `id` 而报 400**。

**列表行为要求**

- 必须返回**全部状态**（0 草稿 / 1 已发布 / 2 已下架），不能像公开接口那样只查 `status=1`
- `status` 参数为空表示不过滤
- 排序：`is_top DESC, created_at DESC`（与公开接口一致）
- 查询列**必须排除** `content_md` / `content_html` / `ai_summary`（大字段，列表用不到，10 条就能拖出几百 KB）
- 逻辑删除由 `@TableLogic` 自动处理，不要手写 `deleted = 0`

**新建行为要求**

- `status` 由请求体决定，允许直接发草稿（`0`）
- `userId` 取当前登录管理员：Controller 方法加 `@AuthenticationPrincipal JwtPrincipal principal`，把 `principal.id()` 传给 Service
- `viewCount` 强制为 `0`，**不接受前端传入**（防止伪造阅读量）
- `createdAt` / `updatedAt` 由 `MybatisMetaObjectHandler` 自动填充，Service 不要手写
- 标题重复**不做唯一约束**（个人博客允许同名文章，加唯一键反而会误伤）

**更新行为要求**

- 先查存在性，不存在或已删除 → `BusinessException(ResultCode.NOT_FOUND, "文章不存在")`
- **只更新可编辑字段**，不要整体 `updateById` 把 `viewCount` / `createdAt` / `userId` 覆盖成 null
- `updatedAt` 由 MetaObjectHandler 自动更新
- 分类不存在时**不要报错**，允许 `categoryId` 指向空（个人博客场景宽松优先）

**删除行为要求**

- 用 `removeById(id)` 触发 `@TableLogic` 逻辑删除，**不要物理删除**
- 不存在 → 404

### 2.2 分类

| # | 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- | --- |
| 5 | GET | `/api/v1/admin/categories` | — | `Result<List<CategoryVO>>` |
| 6 | POST | `/api/v1/admin/categories` | body: `CategorySaveRequest` | `Result<Long>` |
| 7 | PUT | `/api/v1/admin/categories/{id}` | body: `CategorySaveRequest` | `Result<Void>` |
| 8 | DELETE | `/api/v1/admin/categories/{id}` | — | `Result<Void>` |

**`CategorySaveRequest` 字段**

```
name        String   @NotBlank @Size(max=50)
description String   @Size(max=255)   可空
sortOrder   Integer  @Min(0) @Max(999) 可空，默认 0
parentId    Long                       可空
```

> **前端分类表单只有 `name` / `description` / `sortOrder` 三个字段，不传 `parentId`。**
> `category.parent_id` 是 `NOT NULL DEFAULT 0`，MyBatis-Plus 默认忽略 null 字段，所以插入时走数据库默认值 0 即可。
> 但为了语义明确，Service 里显式做一次兜底：`parentId == null || parentId < 0` → 设 `0`。

**管理端分类列表要求**

- 不分页，返回全部分类（前端 `CategoryManage.vue` 用表格直接渲染）
- 排序 `sort_order ASC, id ASC`
- 字段沿用已有 `CategoryVO`（`id` / `name` / `parentId` / `description` / `sortOrder` / `createdAt`）
- **注意**：前端分类表格直接读 `row.createdAt` 吗？不读，只读 `name` / `description` / `sortOrder` / 操作列。所以 `CategoryVO` 保持现状即可

**新建 / 更新要求**

- 同一 `parentId` 下 `name` 不能重复 → 冲突抛 `BusinessException(ResultCode.BAD_REQUEST, "同级下已存在同名分类")`
- 更新时排除自身 ID 再判重
- 不允许把 `parentId` 设成自己的 id（自引用）

**删除要求（重点）**

- 删除前统计该分类下**未删除**的文章数量
- 有引用 → `BusinessException(ResultCode.BAD_REQUEST, "该分类下还有 N 篇文章，请先移走或删除")`
- 无引用 → 逻辑删除
- **不要**级联删除文章（个人博客误删代价太高）

## 三、代码骨架（可直接用，按项目现有风格补全）

### 3.1 DTO / VO

```java
// blogs-model/src/main/java/com/xdl/model/dto/AdminArticleQueryRequest.java
package com.xdl.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.io.Serializable;

/**
 * 管理端文章列表查询参数
 * <p>
 * 与公开侧 ArticleQueryRequest 刻意分开：公开接口绝不能出现 status 字段，
 * 否则 GET /articles?status=0 就能把草稿拉出来。
 */
@Data
public class AdminArticleQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Min(value = 1, message = "页码必须大于0")
    private Long pageNum = 1L;

    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    private Long pageSize = 10L;

    /** 文章状态：0-草稿，1-已发布，2-已下架；null 表示全部 */
    @Min(value = 0, message = "状态取值只能是0/1/2")
    @Max(value = 2, message = "状态取值只能是0/1/2")
    private Integer status;
}
```

```java
// blogs-model/src/main/java/com/xdl/model/dto/ArticleSaveRequest.java
package com.xdl.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // 前端会带 id 字段，直接忽略
public class ArticleSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "文章标题不能为空")
    @Size(max = 120, message = "标题长度不能超过120位")
    private String title;

    @Size(max = 500, message = "摘要长度不能超过500位")
    private String summary;

    private String contentMd;

    private Long categoryId;

    @NotNull(message = "文章状态不能为空")
    @Min(value = 0, message = "状态取值只能是0/1/2")
    @Max(value = 2, message = "状态取值只能是0/1/2")
    private Integer status;

    @Size(max = 255, message = "封面地址长度不能超过255位")
    private String coverImage;

    @Min(value = 0, message = "置顶取值只能是0/1")
    @Max(value = 1, message = "置顶取值只能是0/1")
    private Integer isTop;
}
```

```java
// blogs-model/src/main/java/com/xdl/model/dto/CategorySaveRequest.java
package com.xdl.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategorySaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50位")
    private String name;

    @Size(max = 255, message = "分类描述长度不能超过255位")
    private String description;

    @Min(value = 0, message = "排序值不能小于0")
    @Max(value = 999, message = "排序值不能大于999")
    private Integer sortOrder;

    /** 父分类ID，前端不传时兜底为 0（顶级） */
    private Long parentId;
}
```

```java
// blogs-model/src/main/java/com/xdl/model/vo/AdminArticleSummaryVO.java
package com.xdl.model.vo;

import com.xdl.model.entity.Article;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端文章列表项
 * <p>
 * 与公开的 ArticleSummaryVO 分开：管理端需要 status 与 updatedAt，
 * 但不能给公开 VO 加这两个字段，否则管理端字段会污染公开接口。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminArticleSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String summary;
    private Long categoryId;
    private String categoryName;
    private String coverImage;
    private Integer viewCount;
    private Integer isTop;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminArticleSummaryVO from(Article article, String categoryName) {
        return AdminArticleSummaryVO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .categoryId(article.getCategoryId())
                .categoryName(categoryName)
                .coverImage(article.getCoverImage())
                .viewCount(article.getViewCount() == null ? 0 : article.getViewCount())
                .isTop(article.getIsTop() == null ? Article.NOT_TOP : article.getIsTop())
                .status(article.getStatus())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
```

### 3.2 Service 方法签名（加到现有接口里，不要新建接口）

```java
// ArticleService 追加
PageResult<AdminArticleSummaryVO> pageForAdmin(AdminArticleQueryRequest query);
Long createArticle(ArticleSaveRequest request, Long adminId);
void updateArticle(Long id, ArticleSaveRequest request);
void deleteArticle(Long id);

// CategoryService 追加
Long createCategory(CategorySaveRequest request);
void updateCategory(Long id, CategorySaveRequest request);
void deleteCategory(Long id);
```

### 3.3 Controller

```java
@RestController
@RequestMapping("/api/v1/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    @GetMapping
    public Result<PageResult<AdminArticleSummaryVO>> list(@Valid AdminArticleQueryRequest query) {
        return Result.success(articleService.pageForAdmin(query));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody ArticleSaveRequest request,
                               @AuthenticationPrincipal JwtPrincipal principal) {
        return Result.success(articleService.createArticle(request, principal.id()));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ArticleSaveRequest request) {
        articleService.updateArticle(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success();
    }
}
```

`AdminCategoryController` 同构，路径 `/api/v1/admin/categories`。

## 四、9 个坑（每个都踩过一次或差点踩到）

1. **公开 `ArticleQueryRequest` 绝对不能加 `status` 字段**
   `GET /api/v1/articles?status=0` 一旦生效，访客就能直接拉草稿。管理端必须用独立的 `AdminArticleQueryRequest`，两者不共用。

2. **不要给公开 `ArticleSummaryVO` 加 `status` / `updatedAt`**
   管理页需要这两个字段，但公开 VO 加了等于把「这篇文章是草稿」告诉访客。必须新建 `AdminArticleSummaryVO`。

3. **前端提交会带 `id` 字段**
   `ArticleManage.vue` 里是 `createArticle({ ...form })`，`form` 含 `id`。DTO 不加 `@JsonIgnoreProperties(ignoreUnknown = true)` 会 400。

4. **分类删除必须校验引用**
   否则删掉「Java」分类后，那些文章的 `category_id` 指向不存在的记录，公开列表 `categoryName` 变 null，前端显示「未分类」，数据实际上已经脏了。

5. **分页必须用 `PageResult.of()` 转换**
   MyBatis-Plus 返回 `records / current / size`，前端要 `list / pageNum / pageSize / pages`。直接返回 `IPage` 前端拿不到数据。

6. **列表查询必须排除大字段**
   `content_md` 单篇几十 KB，20 条列表就是 1MB+。用 `.select(...)` 显式指定列，跟 `ArticleServiceImpl.pagePublished` 一样的写法。

7. **更新不要整体 `updateById`**
   前端只传可编辑字段，`updateById` 会把没传的 `viewCount` / `createdAt` / `userId` 覆盖成 null。要么用 `LambdaUpdateWrapper` 显式 set，要么先把原记录查出来合并。

8. **`updatedAt` 不要手写**
   `MybatisMetaObjectHandler` 已经在 `INSERT_UPDATE` 时自动填充，手写反而可能被覆盖成不一致的值。

9. **Controller 不要写鉴权代码**
   `SecurityConfig` 已经对 `/api/v1/admin/**` 做了 `hasRole("ADMIN")`，Controller 再写一遍是冗余；写错了（比如漏了）反而掩盖问题。

## 五、验收清单（10 项，逐条打勾）

前置：本地 MySQL 已建 `blogs_db`，`export DB_PASSWORD=xxx`、`export JWT_SECRET=至少32字节`、`export ADMIN_INIT_PASSWORD=xxx`，`mvn -pl blogs-admin -am spring-boot:run`。

| # | 验证项 | 期望 |
| --- | --- | --- |
| 1 | 不带 token 访问 `GET /api/v1/admin/articles` | 401，`{"code":401,"message":"未登录或登录已过期"}` |
| 2 | 登录拿 token 后访问同一接口 | 200，`data.list` 为空数组，`data.total=0` |
| 3 | `POST /admin/articles` 建草稿（status=0） | 200，返回新 ID |
| 4 | `GET /admin/articles` | 能查到刚才的草稿（公开接口 `GET /articles` 查不到 —— 这条最关键） |
| 5 | `PUT /admin/articles/{id}` 改成 status=1 | 200；`GET /articles` 能查到；`GET /articles/{id}` 阅读数 +1 |
| 6 | `PUT` 把 status 改成 2（下架） | `GET /articles/{id}` 返回 404 |
| 7 | `DELETE /admin/articles/{id}` | 200；再查该 ID 返回 404；数据库 `deleted=1` 而非物理删除 |
| 8 | `POST /admin/categories` 建分类，再建同名 | 第二次 400「同级下已存在同名分类」 |
| 9 | 给分类挂一篇文章后 `DELETE /admin/categories/{id}` | 400，提示还有 N 篇文章 |
| 10 | `GET /admin/articles?status=0` 与 `?status=1` | 分别只返回对应状态；不带 status 返回全部 |

## 六、可直接复制的提示词

````text
你负责 Blogs_java 后端项目的「管理端 8 个接口」开发。这是 Spring Boot 3.4.0 + Java 21 + MyBatis-Plus 3.5.9 + MySQL 8 的五模块 Maven 工程（blogs-model / blogs-common / blogs-security / blogs-core / blogs-admin）。

【背景】
项目已完成登录认证和公开侧 3 个接口（文章列表、文章详情、分类列表），前端管理端页面也已写好但全部走 mock，现在需要你把管理端 8 个接口补齐，让前端能切真实后端联调。

【本次要做的 8 个接口】
文章：
1. GET    /api/v1/admin/articles?pageNum=&pageSize=&status=   管理端文章分页（含草稿/已发布/已下架）
2. POST   /api/v1/admin/articles                              新建文章
3. PUT    /api/v1/admin/articles/{id}                         更新文章
4. DELETE /api/v1/admin/articles/{id}                         删除文章（逻辑删除）
分类：
5. GET    /api/v1/admin/categories                            分类列表（不分页）
6. POST   /api/v1/admin/categories                            新建分类
7. PUT    /api/v1/admin/categories/{id}                       更新分类
8. DELETE /api/v1/admin/categories/{id}                       删除分类（有文章引用则拒绝）

【必须新增的文件】
- blogs-model: AdminArticleQueryRequest、ArticleSaveRequest、CategorySaveRequest、AdminArticleSummaryVO
- blogs-admin: AdminArticleController、AdminCategoryController
- 在已有的 ArticleService / CategoryService 接口和实现里追加写方法（不要新建接口）

【不要动的东西】
- 不要改 ArticleQueryRequest（公开侧查询参数），绝对不能给它加 status 字段，否则 GET /api/v1/articles?status=0 能把草稿拉出来，是安全事故
- 不要给公开的 ArticleSummaryVO 加 status / updatedAt，管理端用独立的 AdminArticleSummaryVO
- 不要改 SecurityConfig，/api/v1/admin/** 已经被统一拦成 hasRole("ADMIN")，Controller 里不要写鉴权
- 不要改 schema.sql，三张表字段已经齐了
- 不要改 Result / ResultCode / GlobalExceptionHandler / MybatisPlusConfig / JacksonConfig

【字段契约（前端已按此写好，字段名不可改）】
管理端文章列表项 AdminArticleSummaryVO：
id, title, summary, categoryId, categoryName, coverImage, viewCount, isTop, status, createdAt, updatedAt

文章保存 ArticleSaveRequest：
title(必填,≤120), summary(≤500), contentMd, categoryId, status(必填,0/1/2), coverImage(≤255), isTop(0/1)

分类保存 CategorySaveRequest：
name(必填,≤50), description(≤255), sortOrder(0-999), parentId(可空)

【关键实现要求】
1. 管理端文章列表必须返回全部状态，不能只查 status=1；status 参数为空表示不过滤
2. 列表查询必须显式 select 列，排除 content_md / content_html / ai_summary 大字段
3. 排序 is_top DESC, created_at DESC
4. 分页响应必须用 PageResult.of() 转成 list/total/pageNum/pageSize/pages，不能直接返回 IPage
5. 新建文章时 userId 取当前登录管理员（@AuthenticationPrincipal JwtPrincipal principal → principal.id()），viewCount 强制为 0
6. 前端提交的 form 对象里带 id 字段，DTO 必须加 @JsonIgnoreProperties(ignoreUnknown = true) 或显式处理，不能因为多出 id 就 400
7. 更新时不要整体 updateById，避免把 viewCount / createdAt / userId 覆盖成 null
8. createdAt / updatedAt 由 MybatisMetaObjectHandler 自动填充，Service 不要手写
9. 删除用 removeById 触发 @TableLogic 逻辑删除，不要物理删除
10. 分类删除前必须统计该分类下未删除的文章数，有引用抛 BusinessException(ResultCode.BAD_REQUEST, "该分类下还有 N 篇文章，请先移走或删除")
11. 同一 parentId 下分类名不能重复，更新时排除自身 ID 再判重
12. 分类 parentId 前端不传，Service 里兜底为 0（顶级）
13. categoryName 用批量查询填充，不要逐条查（避免 N+1），参考 ArticleServiceImpl.loadCategoryNames 的写法
14. 所有异常用 BusinessException + ResultCode，不要自己拼 Result 或抛 RuntimeException

【代码风格】
严格跟随现有代码：中文 Javadoc 注释、@RequiredArgsConstructor 构造注入、常量提取成 private static final、Service 实现类里不要出现魔法值。参考 ArticleServiceImpl 的写法（它已经处理了关键词转义、N+1、阅读量自增，质量可以作为模板）。

【交付要求】
- 只改上述列出的文件，不碰前端、不碰 pom、不改 main 分支
- 本地 mvn -pl blogs-admin -am clean package 必须通过
- 交付时说明：改了哪些文件、每个接口的实现要点、以及按验收清单自测的结果

【验收清单（自测后逐条回复）】
1. 不带 token 访问 /admin/articles → 401
2. 带 token → 200，空列表
3. 建草稿 → 200 返回 ID
4. 管理端能查到草稿，公开接口 GET /articles 查不到（这条最关键）
5. 改成已发布 → 公开列表能查到，详情阅读数 +1
6. 改成已下架 → 公开详情 404
7. 删除 → 再查 404，数据库 deleted=1
8. 建同名分类 → 400
9. 删有文章引用的分类 → 400 并提示文章数
10. ?status=0 和 ?status=1 分别只返回对应状态
````

## 七、前端对应关系（WorkBuddy 自查用）

| 前端调用 | 文件 | 传参 |
| --- | --- | --- |
| `getAdminArticles({pageNum,pageSize,status})` | `api/articles.js` | `status` 为字符串 `'0'/'1'/'2'`，空串时不传 |
| `createArticle({...form})` | `views/admin/ArticleManage.vue` | form = `{id,title,summary,contentMd,categoryId,status,coverImage,isTop}` |
| `updateArticle(id, {...form})` | 同上 | 同上 |
| `deleteArticle(id)` | 同上 | — |
| `getAdminCategories()` | `api/categories.js` | — |
| `createCategory({...form})` | `views/admin/CategoryManage.vue` | form = `{id,name,description,sortOrder}` |
| `updateCategory(id, {...form})` | 同上 | 同上 |
| `deleteCategory(id)` | 同上 | — |

> 注意 `status` 是字符串传过来的（`el-radio-group` 的值是 `'0'/'1'/'2'`），Spring 会自动转 Integer，不用特殊处理。

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
