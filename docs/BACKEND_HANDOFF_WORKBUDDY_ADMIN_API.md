# 后端管理端接口交接单（给 WorkBuddy）

> 生成时间：2026-10-31
> 基线：main `4ba0a48`
> 适用对象：接手管理端 8 个接口的后端开发者

---

## 一、先看清现状，别重做已完成的部分

### 1.1 已完成（已合并 main，直接复用）

**基础设施**
- Maven 五模块：blogs-model / blogs-common / blogs-security / blogs-core / blogs-admin
- Spring Boot 3.4.0 + Java 21 + MyBatis-Plus + jjwt 0.12.6 + MySQL 8.0
- 统一响应包络 `Result<T>`（字段是 `code` / `message` / `data` / `timestamp`，**不是 msg**）
- `ResultCode` 枚举、`BusinessException`、`GlobalExceptionHandler`（HTTP 状态码与业务 code 一致）
- JWT HS256 签发校验、`JwtAuthenticationFilter`、`JwtPrincipal(id, username, role)`
- `SecurityConfig`：`/api/v1/auth/login` 放行，`/api/v1/admin/**` 需 ROLE_ADMIN，公开 GET 放行 `/api/v1/articles/**`、`/api/v1/categories/**`、`/api/v1/tags/**`
- `MybatisPlusConfig`：已注册 `PaginationInnerInterceptor`（MYSQL 方言，单页上限 100），pom 已补 `mybatis-plus-jsqlparser`
- `JacksonConfig`：已统一 `LocalDateTime` / `LocalDate` 序列化与反序列化为 `yyyy-MM-dd HH:mm:ss` / `yyyy-MM-dd`
- `MybatisMetaObjectHandler`：createdAt / updatedAt 自动填充
- `AdminUserInitializer`：首次启动创建管理员

**已实现接口（5 个，不要改动）**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | 管理员登录 |
| GET | `/api/v1/auth/me` | 当前管理员信息 |
| GET | `/api/v1/articles` | 公开文章分页（仅已发布，支持 categoryId + keyword） |
| GET | `/api/v1/articles/{id}` | 公开文章详情（阅读量 +1，草稿/下架返回 404） |
| GET | `/api/v1/categories` | 公开分类列表（扁平数组，按 sortOrder 升序） |

**已就绪的类**
- 实体：`AdminUser` / `Article` / `Category`（均含 `@TableLogic deleted`）
- Mapper：`AdminUserMapper` / `ArticleMapper` / `CategoryMapper`（空接口 extends BaseMapper）
- Service 实现：`AuthServiceImpl` / `ArticleServiceImpl`（仅公开查询）/ `CategoryServiceImpl`（仅公开查询）
- VO：`ArticleSummaryVO` / `ArticleDetailVO` / `CategoryVO` / `PageResult<T>` / `AdminUserInfo` / `LoginResponse`
- DTO：`ArticleQueryRequest`（pageNum / pageSize / categoryId / keyword）
- 建表：`blogs-admin/src/main/resources/db/schema.sql` 已含三张表，dev 启动自动建表

### 1.2 本次要做的（8 个接口，全部缺失）

管理端文章 CRUD 4 个 + 分类 CRUD 4 个。Service 层目前**没有任何写操作**，Controller 层**没有任何 admin Controller**。

---

## 二、接口契约（按前端已定型的调用反推，必须严格对齐）

前端 api 层已经写好并在 mock 下跑通，URL 和字段都不可改。以下契约以 `frontend/src/api/articles.js`、`frontend/src/api/categories.js` 为准。

### 2.1 文章管理端

**① `GET /api/v1/admin/articles`** — 文章列表（含草稿/下架）

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | Long | 否 | 默认 1 |
| pageSize | Long | 否 | 默认 10，上限 100 |
| status | Integer | 否 | 0 草稿 / 1 已发布 / 2 已下架；不传=全部 |

响应 `data`：分页结构

```json
{
  "list": [
    {
      "id": 1,
      "title": "标题",
      "summary": "摘要",
      "categoryId": 2,
      "categoryName": "后端",
      "coverImage": "https://...",
      "viewCount": 12,
      "isTop": 0,
      "status": 0,
      "createdAt": "2026-10-31 13:00:00",
      "updatedAt": "2026-10-31 13:00:00"
    }
  ],
  "total": 1,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 1
}
```

> ⚠️ **注意 `status` 字段**：现有 `ArticleSummaryVO` **没有 status**，管理页要靠它显示「草稿/已发布/已下架」标签。必须新建 `AdminArticleSummaryVO`（含 status + updatedAt）或给 SummaryVO 加字段，**但不要给公开 VO 加 status**——公开接口不该暴露文章状态。

**② `POST /api/v1/admin/articles`** — 新建

请求体（前端表单实际提交的字段）：

```json
{
  "title": "标题",
  "summary": "摘要",
  "contentMd": "# Markdown 正文",
  "categoryId": 2,
  "status": 0,
  "coverImage": "",
  "isTop": 0
}
```

响应 `data`：新建后的文章 id（Long）或完整详情，前端只用 `id` 判断成功。

**③ `PUT /api/v1/admin/articles/{id}`** — 更新

请求体同新建。响应 `data` 可为 null。

**④ `DELETE /api/v1/admin/articles/{id}`** — 删除

逻辑删除（`removeById` 即可，`@TableLogic` 自动生效）。响应 `data` 为 null。

### 2.2 分类管理端

**⑤ `GET /api/v1/admin/categories`** — 分类列表（不分页）

响应 `data`：`List<CategoryVO>`，字段 `id` / `name` / `parentId` / `description` / `sortOrder`，按 `sortOrder` 升序。

**⑥ `POST /api/v1/admin/categories`** — 新建

```json
{ "name": "后端", "description": "后端技术", "sortOrder": 0 }
```

**⑦ `PUT /api/v1/admin/categories/{id}`** — 更新

请求体同新建。

**⑧ `DELETE /api/v1/admin/categories/{id}`** — 删除

逻辑删除，但**删除前必须校验该分类下是否还有未删除的文章**，有则抛业务异常（如「该分类下还有 N 篇文章，无法删除」），否则会产生孤儿文章。

---

## 三、核心代码骨架

### 3.1 新增 DTO：`AdminArticleQueryRequest`

```java
package com.xdl.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端文章列表查询条件
 * 与公开侧 ArticleQueryRequest 分开，避免 status 参数被公开接口误用
 */
@Data
public class AdminArticleQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Min(1)
    private Long pageNum = 1L;

    @Min(1)
    @Max(100)
    private Long pageSize = 10L;

    /** 0 草稿 / 1 已发布 / 2 已下架；null 表示全部 */
    private Integer status;
}
```

### 3.2 新增 DTO：`ArticleSaveRequest`（新建/更新共用）

```java
package com.xdl.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String summary;

    @NotBlank(message = "正文不能为空")
    private String contentMd;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    /** 0 草稿 / 1 已发布 / 2 已下架 */
    private Integer status = 0;

    private String coverImage;

    /** 0 普通 / 1 置顶 */
    private Integer isTop = 0;
}
```

### 3.3 新增 DTO：`CategorySaveRequest`

```java
package com.xdl.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class CategorySaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private Long parentId;

    private String description;

    private Integer sortOrder = 0;
}
```

### 3.4 Service 接口扩展

`ArticleService` 增加：

```java
PageResult<AdminArticleSummaryVO> pageForAdmin(AdminArticleQueryRequest query);

Long createArticle(ArticleSaveRequest request, Long operatorId);

void updateArticle(Long id, ArticleSaveRequest request);

void deleteArticle(Long id);
```

`CategoryService` 增加：

```java
List<CategoryVO> listForAdmin();

Long createCategory(CategorySaveRequest request);

void updateCategory(Long id, CategorySaveRequest request);

void deleteCategory(Long id);
```

### 3.5 Controller

```java
package com.xdl.blogsadmin.controller;

import com.xdl.blogscommon.jwt.JwtPrincipal;
import com.xdl.blogscommon.result.Result;
import com.xdl.blogscore.service.ArticleService;
import com.xdl.model.dto.AdminArticleQueryRequest;
import com.xdl.model.dto.ArticleSaveRequest;
import com.xdl.model.vo.AdminArticleSummaryVO;
import com.xdl.model.vo.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

`AdminCategoryController` 同理，`@RequestMapping("/api/v1/admin/categories")`。

> 路径带 `/api/v1/admin/`，`SecurityConfig` 里 `hasRole("ADMIN")` 已覆盖，**不要在 Controller 上重复加安全注解**。

### 3.6 关键实现要点

- **`operatorId`**：`Article` 实体有 `userId` 字段，新建时从 `JwtPrincipal.id()` 取当前管理员 id 写入
- **`viewCount`**：新建时初始化为 0，更新时不要覆盖（`null` 字段 MyBatis-Plus 默认忽略，注意别用全量 update）
- **`isTop` / `status`**：前端传的是数字 0/1，实体是 `Integer`，直接映射即可
- **`contentHtml`**：见坑点 5
- **`categoryName`**：列表要返回分类名，参考现有 `ArticleServiceImpl.loadCategoryNames()` 的批量查询写法，不要 N+1
- **排序**：管理端列表建议 `isTop DESC, created_at DESC`，与公开侧一致

---

## 四、坑点（踩了会浪费时间）

**坑点 1：公开接口不能被 status 参数污染 ⚠️ 安全相关**

`ArticleQueryRequest` 是公开接口 `GET /api/v1/articles` 的入参。如果你图省事给它加 `status` 字段，攻击者传 `?status=0` 就能从公开接口拉出草稿。

**正确做法**：管理端单独用 `AdminArticleQueryRequest`（3.1），公开侧 `pagePublished` 内部强制 `status = 1`，忽略任何外部 status 参数。

**坑点 2：管理端列表 VO 必须带 status，但不要动公开 VO**

`ArticleSummaryVO` 没有 `status`，管理页需要。新建 `AdminArticleSummaryVO`（id / title / summary / categoryId / categoryName / coverImage / viewCount / isTop / **status** / createdAt / **updatedAt**），不要往公开 VO 里塞 status。

**坑点 3：分页插件已配好，别重复注册**

`MybatisPlusConfig` 已有 `PaginationInnerInterceptor`，直接用 `page()` 即可。重复注册会导致分页参数被多次拦截。

**坑点 4：逻辑删除自动生效，别手写 deleted 条件**

`Article` / `Category` 都有 `@TableLogic deleted`，`removeById()` 自动 UPDATE `deleted=1`。查询时也不要手写 `where deleted = 0`，插件已自动拼接。

**坑点 5：`contentHtml` 渲染职责仍未定**

前端 `ArticleDetail.vue` 目前用 `contentMd` 在前端渲染（`utils/markdown.js`），**没有用 contentHtml**。所以本次可以：
- 方案 A（推荐）：后端收到 `contentMd` 后原样透传给 `contentHtml`，或直接存 null，不引依赖
- 方案 B：引 `commonmark-java` 在后端渲染

**选 A，不要引新依赖**，并在代码注释标注「待前端切换到 contentHtml 时再定」。

**坑点 6：分类删除必须校验引用**

直接 `removeById` 会产生孤儿文章（文章 categoryId 指向已删分类，列表里分类名显示「未分类」）。删除前先 `count` 该分类下 `deleted=0` 的文章，大于 0 抛 `BusinessException`。

**坑点 7：时间格式已配好，不要再配**

`JacksonConfig` 已统一 `yyyy-MM-dd HH:mm:ss`。不要加 `@JsonFormat`，不要改 `spring.jackson.date-format`（对 `java.time` 无效）。

**坑点 8：打包必须跳测试**

测试类缺 `spring-boot-starter-test` 依赖（只有 blogs-security 有）。打包命令：

```bash
mvn clean package -Dmaven.test.skip=true
```

**坑点 9：启动方式与环境变量**

聚合工程根目录 `spring-boot:run` 跑不起来，必须：

```bash
export JWT_SECRET="至少32字节的随机字符串"
java -jar blogs-admin/target/blogs-admin-*.jar --spring.profiles.active=dev
```

首次建库还需 `ADMIN_INIT_PASSWORD`。详见 `docs/AUTH_SETUP.md`。

---

## 五、给 WorkBuddy 的提示词（直接复制）

```
你是这个 Spring Boot 博客后端项目的开发者。任务：补齐管理端 8 个接口。

## 第一步：先通读，别重做
读这些文件，确认已有实现：
- blogs-admin/src/main/java/com/xdl/blogsadmin/controller/（已有 AuthController、PublicArticleController、PublicCategoryController）
- blogs-core/src/main/java/com/xdl/blogscore/service/impl/ArticleServiceImpl.java、CategoryServiceImpl.java
- blogs-core/src/main/java/com/xdl/blogscore/config/MybatisPlusConfig.java（分页插件已配）
- blogs-common/src/main/java/com/xdl/blogscommon/config/JacksonConfig.java（时间格式已配）
- blogs-security/src/main/java/com/xdl/bologssecurity/config/SecurityConfig.java（/api/v1/admin/** 已要求 ROLE_ADMIN）
- docs/BACKEND_HANDOFF_WORKBUDDY_ADMIN_API.md（本任务的完整契约，务必先读）

以下内容已完成，绝对不要重写：登录、/auth/me、公开文章列表、公开文章详情、公开分类列表、
分页插件、Jackson 时间格式、建表脚本、JWT、统一响应包络、全局异常处理。

## 第二步：实现 8 个接口
严格按 docs/BACKEND_HANDOFF_WORKBUDDY_ADMIN_API.md 第二、三节的契约和骨架实现：
- GET    /api/v1/admin/articles          文章列表（含草稿/下架，支持 status 筛选）
- POST   /api/v1/admin/articles          新建文章
- PUT    /api/v1/admin/articles/{id}     更新文章
- DELETE /api/v1/admin/articles/{id}     删除文章（逻辑删除）
- GET    /api/v1/admin/categories        分类列表
- POST   /api/v1/admin/categories        新建分类
- PUT    /api/v1/admin/categories/{id}   更新分类
- DELETE /api/v1/admin/categories/{id}   删除分类（逻辑删除，删前校验引用）

## 硬约束（违反会返工）
1. 管理端查询用新建的 AdminArticleQueryRequest，绝对不要给公开的 ArticleQueryRequest 加 status 字段——
   否则公开接口能被构造 status=0 拉出草稿，是安全事故。
2. 管理端列表要返回 status 字段（管理页显示草稿/已发布/已下架），新建 AdminArticleSummaryVO，
   但不要往公开的 ArticleSummaryVO 里加 status。
3. 分页插件已注册，直接用 page()，不要重复注册拦截器。
4. 逻辑删除已由 @TableLogic 处理，不要手写 deleted 条件。
5. 分类删除前必须统计该分类下未删除文章数，大于 0 抛 BusinessException。
6. contentHtml 本次原样透传或存 null，不要引入 commonmark-java 等新依赖。
7. 时间字段不要加 @JsonFormat，JacksonConfig 已统一格式。
8. Controller 路径写 /api/v1/admin/articles 和 /api/v1/admin/categories，不要在方法上加 @PreAuthorize，
   SecurityConfig 已覆盖。
9. 新建文章时 userId 从 JwtPrincipal.id() 取当前管理员 id。
10. 不要修改公开侧的 5 个已实现接口，前端已联调对齐字段。

## 第三步：自测（必须全部通过再交付）
1. mvn clean package -Dmaven.test.skip=true 构建通过
2. 启动后带管理员 token：
   - GET /api/v1/admin/articles 返回含 status 的分页列表，草稿可见
   - POST 新建草稿 → GET 列表能看到 → PUT 改状态为已发布 → 公开 GET /api/v1/articles 能看到
   - DELETE 删除 → 管理列表消失，公开接口 404
   - 分类新建/更新/删除正常；删除有文章引用的分类返回业务错误而不是 500
3. 不带 token 访问 /api/v1/admin/** 返回 401
4. 用普通用户 token（如果有）访问返回 403
5. 公开接口 GET /api/v1/articles?status=0 不会返回草稿（验证坑点 1 没踩）

## 交付
列出改动的文件、新增的类、每个接口的实测结果。不要动 frontend/ 目录。
```

---

## 六、验收清单（扣子会逐项核）

| # | 检查项 | 判定标准 |
| --- | --- | --- |
| 1 | 8 个接口全部可达 | 路径、方法、参数与第二节完全一致 |
| 2 | 管理端列表含 status | 响应 JSON 里有 status 字段 |
| 3 | 公开接口未被污染 | `GET /articles?status=0` 不返回草稿 |
| 4 | 公开 VO 未加 status | `ArticleSummaryVO` 字段未变 |
| 5 | 分类删除校验 | 有引用时返回业务错误，非 500 |
| 6 | 逻辑删除生效 | 删除后数据库 deleted=1，非物理删除 |
| 7 | 时间格式 | 响应时间为 `yyyy-MM-dd HH:mm:ss`，无 T |
| 8 | 无新增依赖 | pom 未引入 commonmark 等 |
| 9 | 构建通过 | `mvn clean package -Dmaven.test.skip=true` 成功 |
| 10 | 公开侧未改动 | 5 个已实现接口 diff 为空 |
