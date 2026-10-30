---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 3137366841633914_0-drive/235678159419209852/Blogs_java/docs/BACKEND_HANDOFF_WORKBUDDY.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 3137366841633914#1793327545989
    ReservedCode2: ""
---
# 后端交接说明与 WorkBuddy 提示词

> 生成时间：2026-10-30
> 基线提交：`ed54db5`（main）
> 用途：把现有后端现状、接口契约、代码骨架交接给 WorkBuddy，用于继续开发文章/分类等剩余接口。
> 事实来源：全部结论来自对当前仓库代码的实际核验，非文档推测。

---

## 一、现状盘点

### 1.1 已完成（可直接复用，不要重写）

| 能力 | 位置 | 状态 |
| --- | --- | --- |
| 管理员登录 | `blogs-admin/.../controller/AuthController.java` → `POST /api/v1/auth/login` | ✅ 已实现并合并 |
| 当前管理员信息 | 同上 → `GET /api/v1/auth/me` | ✅ 已实现并合并 |
| 统一响应包络 | `blogs-common/.../result/Result.java`、`ResultCode.java` | ✅ 完整 |
| 全局异常处理 | `blogs-common/.../exception/GlobalExceptionHandler.java`、`BusinessException.java` | ✅ 完整，HTTP 状态码与业务 code 一致 |
| JWT 签发/校验 | `blogs-common/.../jwt/JwtTokenProvider.java`、`JwtProperties.java`、`JwtPrincipal.java` | ✅ HS256 / 86400s |
| 安全过滤链 | `blogs-security/.../filter/JwtAuthenticationFilter.java`、`config/SecurityConfig.java`、`handler/RestSecurityExceptionHandler.java` | ✅ 完整，`/api/v1/admin/**` 需 `ROLE_ADMIN` |
| 管理员初始化 | `blogs-core/.../init/AdminUserInitializer.java` | ✅ 表空且设了 `ADMIN_INIT_PASSWORD` 时建 `admin` |
| 自动填充 | `blogs-core/.../config/MybatisMetaObjectHandler.java` | ✅ `createdAt` / `updatedAt` |
| 实体层 | `blogs-model/.../entity/`：`AdminUser`、`Article`、`Category` | ✅ 字段完整 |
| 数据层 | `blogs-core/.../mapper/`：`AdminUserMapper`、`ArticleMapper`、`CategoryMapper` | ✅ 均为空接口 `extends BaseMapper<T>` |

### 1.2 骨架（有类无实现，需填充）

| 位置 | 现状 |
| --- | --- |
| `ArticleService` / `ArticleServiceImpl` | 仅 `extends IService<Article>` / `ServiceImpl<ArticleMapper, Article>`，**无任何自定义方法** |
| `CategoryService` / `CategoryServiceImpl` | 同上，**无任何自定义方法** |
| `AdminUserService` / `AdminUserServiceImpl` | 同上 |

### 1.3 缺失（本次要做的）

- **文章 Controller 完全不存在**（无 `ArticleController`，公开端与管理端都没有）
- **分类 Controller 完全不存在**
- 文章/分类的 DTO（请求体）与 VO（响应体）一个都没有，只有 `LoginRequest` / `LoginResponse` / `AdminUserInfo`
- **分页插件未配置**（见 3.3 坑点 1，必补）
- **建表脚本不全**：`blogs-admin/src/main/resources/db/schema.sql` 只有 `admin_user`，没有 `article` / `category`
- 标签（Tag）全链路未开始（阶段 3）

---

## 二、现有后端接口契约

### 2.1 通用约定（已落地，直接沿用）

**Base URL**：`/api/v1`（各 Controller 在 `@RequestMapping` 里显式带上，项目**未**设置 `server.servlet.context-path`）

**统一响应包络**（`Result<T>`，注意字段名是 `message` 不是 `msg`）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1721732646000
}
```

**错误码**（`ResultCode` 枚举已定义）：`200` 成功、`400` 参数/业务校验失败、`401` 未认证、`403` 权限不足、`404` 资源不存在、`429` 限流（预留）、`500` 内部错误。

**HTTP 状态码与业务 `code` 保持一致**——`GlobalExceptionHandler` 的 `resolveStatus()` 已实现该映射，抛 `BusinessException` 即可，不要手动 `ResponseEntity`。

**认证**：`Authorization: Bearer <JWT>`；claims 中 `sub`=管理员 id、`username`、`role`。

### 2.2 已实现接口

#### `POST /api/v1/auth/login`（已冻结 2026-10-20）

请求体：

```json
{ "username": "admin", "password": "raw_password" }
```

校验：`username` 3–50 字符、`password` 6–64 字符。

成功 `data`：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "userInfo": { "id": 1, "username": "admin", "nickname": "系统管理员", "avatar": null, "role": "ADMIN" }
}
```

失败：`401 用户名或密码错误`（用户不存在与密码错误统一文案，防账号枚举）、`403 账号已被禁用`、`400` 字段校验信息。

#### `GET /api/v1/auth/me`

需有效 JWT。成功 `data` 为 `userInfo`（同登录响应内结构，不含密码）。失败 `401`。

### 2.3 待实现接口清单（与前端 api 层已定型的 URL 严格对应）

前端 `frontend/src/api/*.js` 已经写好并定型，**后端路径必须完全一致，不允许改**。

#### 公开接口（无需认证）

| # | 方法/路径 | 说明 | 关键参数/字段 |
| --- | --- | --- | --- |
| 1 | `GET /api/v1/articles` | 分页，**仅 `status=1`** | Query：`pageNum`、`pageSize`、`categoryId`、`keyword`（标题/摘要模糊） |
| 2 | `GET /api/v1/articles/{id}` | 已发布文章详情；草稿/下架返回 `404`（不泄露存在性） | 需 `viewCount` +1 |
| 3 | `GET /api/v1/categories` | 分类列表（扁平数组，非分页） | `id`、`name`、`parentId`、`description`、`sortOrder` |

#### 管理端接口（需 JWT + `ROLE_ADMIN`）

| # | 方法/路径 | 说明 |
| --- | --- | --- |
| 4 | `GET /api/v1/admin/articles` | 分页，**含草稿/下架**，支持 `status` 筛选 |
| 5 | `GET /api/v1/admin/articles/{id}` | 单篇详情（含草稿/下架），编辑回填用 |
| 6 | `POST /api/v1/admin/articles` | 创建（可草稿/发布） |
| 7 | `PUT /api/v1/admin/articles/{id}` | 更新 |
| 8 | `DELETE /api/v1/admin/articles/{id}` | **逻辑删除**（`@TableLogic` 已全局配置，直接 `removeById` 即可） |
| 9 | `GET /api/v1/admin/categories` | 分类列表（管理端） |
| 10 | `POST /api/v1/admin/categories` | 创建分类 |
| 11 | `PUT /api/v1/admin/categories/{id}` | 更新分类 |
| 12 | `DELETE /api/v1/admin/categories/{id}` | 逻辑删除 |

> 标签相关（`/api/v1/tags`、`/api/v1/admin/tags`）属**阶段 3**，本轮不实现。

#### 分页响应结构（统一）

```json
{
  "list": [],
  "total": 45,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 5
}
```

> MyBatis-Plus 的 `IPage<T>` 字段名是 `records` / `size` / `current`，**必须转换成上面的 `list` / `pageSize` / `pageNum`**，否则前端分页拿不到数据。

#### 文章创建/更新请求体

```json
{
  "title": "标题",
  "summary": "摘要",
  "contentMd": "# 标题\n正文...",
  "categoryId": 2,
  "coverImage": "https://...",
  "status": 1
}
```

约束：
- `status` 只能 `0`（草稿）/ `1`（已发布）/ `2`（已下架），非法值 `400`
- `userId` 由后端从 `JwtPrincipal.id()` 取，**前端不提交**
- `createdAt` / `updatedAt` 由 `MybatisMetaObjectHandler` 自动填充，**不要手动 set**
- `viewCount` 默认 0、`isTop` 默认 0，由后端兜底
- 更新已发布文章时**不要重置**发布时间

#### 分类创建/更新请求体

```json
{ "name": "Java 后端", "parentId": 0, "description": "描述", "sortOrder": 10 }
```

---

## 三、核心代码骨架

### 3.1 模块结构（Maven 多模块，根为 `pom` 聚合）

```
blogs (pom, Spring Boot 3.4.0, Java 21)
├── blogs-model      实体 / DTO / VO
├── blogs-common     Result、ResultCode、BusinessException、GlobalExceptionHandler、JWT
├── blogs-security   SecurityConfig、JwtAuthenticationFilter、RestSecurityExceptionHandler
├── blogs-core       Mapper、Service、Config、Init
└── blogs-admin      Web 入口（BlogsApplication + Controller）
```

依赖方向：`blogs-admin` → `blogs-security` / `blogs-core` → `blogs-common` / `blogs-model`。**不要反向依赖。**

### 3.2 关键骨架用法

**Controller 取当前登录管理员**（`JwtPrincipal` 是 record）：

```java
@GetMapping("/me")
public Result<AdminUserInfo> currentAdmin(@AuthenticationPrincipal JwtPrincipal principal) {
    return Result.success(authService.getLoginAdminInfo(principal.id()));
}
```

**抛业务异常**（自动映射 HTTP 状态码）：

```java
throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
throw new BusinessException(ResultCode.BAD_REQUEST, "文章状态值非法");
```

**成功返回**：`Result.success(data)`；带自定义消息：`Result.success("创建成功", data)`。

**Service 继承**：`ServiceImpl<ArticleMapper, Article>` 已提供 `page()`、`getById()`、`save()`、`updateById()`、`removeById()`（逻辑删除）。**除非有跨表逻辑，否则不需要写 Mapper XML。**

**权限**：`SecurityConfig` 已配置好 `/api/v1/admin/**` 需 `hasRole("ADMIN")`，**Controller 上不需要再加注解**。

### 3.3 已知坑点（必读，踩了会浪费大量时间）

**坑点 1：分页插件没配 ⚠️ 最高优先级**

`blogs-core/src/main/java/com/xdl/blogscore/config/MybatisPlusConfig.java` 当前只有：

```java
@Configuration
@MapperScan("com.xdl.blogscore.mapper")
public class MybatisPlusConfig {
}
```

**必须补 `PaginationInnerInterceptor`，否则 `page()` 不生效**——会返回全表数据、`total` 为 0：

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
}
```

**坑点 2：建表脚本不全**

`blogs-admin/src/main/resources/db/schema.sql` 只有 `admin_user`。`article` / `category` 的 DDL 在仓库根目录 `sql/schema.sql`（已与实体逐字段核对一致，可直接引用）。dev 启动要自动建全表，需把这两段并入 `db/schema.sql`。

**坑点 3：`publishedAt` 字段不存在**

契约文档 6.1.1 / 6.1.2 的响应示例里有 `publishedAt`，但 `Article` 实体**没有这个字段**。两个选择：
- （推荐）用 `createdAt` 代替，VO 里不返回 `publishedAt`
- 或在实体与表里补 `published_at` 字段

**先按推荐方案做，不要自己加字段。**

**坑点 4：`tagIds` 字段不存在**

`Article` 实体无标签关联，阶段 3 才有。VO 里**不要返回 `tagIds`**。

**坑点 5：测试类缺依赖，打包必须跳测试**

各模块的 `*ApplicationTests` 缺 `spring-boot-starter-test`，用 `-DskipTests` 仍会编译测试代码并失败。必须：

```bash
mvn clean package -Dmaven.test.skip=true
```

**坑点 6：启动方式**

根工程是 `pom` 聚合模块，`mvn -pl blogs-admin -am spring-boot:run` 会报「Unable to find a suitable main class」。用：

```bash
java -jar blogs-admin/target/blogs-admin-0.0.1-SNAPSHOT.jar
```

**坑点 7：环境变量**

启动必须设 `JWT_SECRET`（≥32 字节，缺失直接启动失败）。首次建库还需 `ADMIN_INIT_PASSWORD`。详见 `docs/AUTH_SETUP.md`。

**坑点 8：`contentHtml` 渲染职责未定**

契约里 `contentHtml` 字段存在，但「前端提交还是后端渲染」未决策。**建议后端渲染**：接收 `contentMd` 后转 HTML 存入 `contentHtml`（可用 `commonmark-java`，轻量无额外负担）。若不想引依赖，可先原样透传，但要在代码注释里标注待定。

---

## 四、给 WorkBuddy 的提示词（直接复制使用）

```
你是这个 Spring Boot 博客后端项目的开发者。请先通读仓库，再动手。

## 项目现状（已核验，不要重复实现）
- Maven 多模块：blogs-model / blogs-common / blogs-security / blogs-core / blogs-admin
- Spring Boot 3.4.0 + Java 21 + MyBatis-Plus + jjwt 0.12.6
- 已完成：管理员登录（POST /api/v1/auth/login）、当前管理员信息（GET /api/v1/auth/me）、
  统一响应包络 Result<T>（code/message/data/timestamp）、ResultCode 枚举、BusinessException、
  GlobalExceptionHandler（HTTP 状态码与 code 一致）、JWT HS256 签发校验、JwtAuthenticationFilter、
  SecurityConfig（/api/v1/admin/** 需 ROLE_ADMIN）、AdminUserInitializer、MybatisMetaObjectHandler
- 实体已就绪：AdminUser / Article / Category（字段完整，含 @TableLogic deleted）
- Mapper 已就绪：AdminUserMapper / ArticleMapper / CategoryMapper（空接口 extends BaseMapper）
- Service 骨架：ArticleService / CategoryService 只 extends IService，**无任何自定义方法**

## 你要做的事
按 docs/API_CONTRACT.md 实现文章与分类的完整 CRUD，共 12 个接口：

公开（无需认证）：
1. GET  /api/v1/articles          分页，仅 status=1，支持 categoryId、keyword（标题/摘要模糊）
2. GET  /api/v1/articles/{id}     已发布详情；草稿/下架返回 404；viewCount +1
3. GET  /api/v1/categories        分类列表（扁平数组，非分页）

管理端（需 JWT + ROLE_ADMIN）：
4. GET    /api/v1/admin/articles
5. GET    /api/v1/admin/articles/{id}
6. POST   /api/v1/admin/articles
7. PUT    /api/v1/admin/articles/{id}
8. DELETE /api/v1/admin/articles/{id}    逻辑删除
9. GET    /api/v1/admin/categories
10. POST  /api/v1/admin/categories
11. PUT   /api/v1/admin/categories/{id}
12. DELETE /api/v1/admin/categories/{id} 逻辑删除

## 硬性约束（违反即返工）
1. **接口路径必须与前端 frontend/src/api/*.js 完全一致**，不允许改名或调整层级
2. 所有接口返回 Result<T>，用 Result.success(data) / throw new BusinessException(ResultCode.XXX, "msg")
3. **分页响应必须转成 { list, total, pageNum, pageSize, pages }**，MyBatis-Plus 的 records/size/current 不直接返回
4. **必须先补分页插件**：MybatisPlusConfig 里加 PaginationInnerInterceptor(DbType.MYSQL)，
   否则分页完全失效（当前确实没配，这是已知缺陷）
5. **不要返回 publishedAt**（实体无此字段，用 createdAt 代替）
6. **不要返回 tagIds**（标签是阶段3）
7. userId 从 @AuthenticationPrincipal JwtPrincipal 取，createdAt/updatedAt 靠自动填充，都不要手动 set
8. 建表脚本 blogs-admin/src/main/resources/db/schema.sql 只有 admin_user，
   需把仓库根目录 sql/schema.sql 里的 article / category 段并入（DDL 已核对过，直接用）
9. contentMd 转 contentHtml 建议用 commonmark-java 渲染；不想引依赖就原样透传并在注释标注 TODO
10. 打包用 mvn clean package -Dmaven.test.skip=true（-DskipTests 会失败，测试类缺依赖）
11. 启动用 java -jar blogs-admin/target/blogs-admin-0.0.1-SNAPSHOT.jar（不要用 spring-boot:run）
12. 新增 DTO/VO 放 blogs-model（dto / vo 包），Controller 放 blogs-admin，业务逻辑放 blogs-core 的 Service
13. 不要反向依赖：blogs-core 不能依赖 blogs-admin

## 交付要求
- 按「实体/DTO/VO → Service 接口 → Service 实现 → Controller」的顺序推进
- 每完成一组（文章 / 分类）就本地验证：启动服务，用 curl 跑通该组全部接口
- 提供一份简短的自测记录：每个接口的 curl 命令 + 实际返回
- 代码注释用中文，关键业务判断（状态流转、逻辑删除、viewCount 自增）写清原因
- 遇到契约未明确的点（如分类删除时文章 categoryId 如何处理），**先按「置空」实现并在注释标注待确认**，
  不要自己发明新接口或新字段

## 参考文档
- docs/API_CONTRACT.md（接口契约，6.1 公开 / 6.2 管理端）
- docs/AUTH_SETUP.md（环境变量、启动、凭证维护）
- docs/BACKEND_HANDOFF_WORKBUDDY.md（本文件，含全部已知坑点）
- sql/schema.sql（article / category 建表 DDL）
```

---

## 五、联调自测清单（交付前逐项确认）

| # | 检查项 | 预期 |
| --- | --- | --- |
| 1 | 分页插件已配 | `GET /api/v1/articles?pageNum=1&pageSize=2` 的 `data.total` 不为 0 |
| 2 | 建表完整 | 启动后 `SHOW TABLES` 有 `admin_user`、`article`、`category` |
| 3 | 公开列表只返回已发布 | 草稿文章不出现在 `GET /api/v1/articles` |
| 4 | 草稿公开访问 404 | `GET /api/v1/articles/{草稿id}` → 404 |
| 5 | viewCount 自增 | 连续两次详情请求，`viewCount` 递增 |
| 6 | 管理端未登录 401 | 不带 token 访问 `/api/v1/admin/articles` → 401 |
| 7 | 逻辑删除生效 | DELETE 后 `deleted=1`，列表查不到，但数据库记录仍在 |
| 8 | 包络一致 | 所有响应含 `code`/`message`/`data`/`timestamp` |
| 9 | 前端可直连 | 前端 `.env.local` 设 `VITE_USE_MOCK=false` 后，登录→列表→发文全流程可用 |

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
