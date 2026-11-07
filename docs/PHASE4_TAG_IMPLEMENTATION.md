# 阶段 4 · 标签模块实现说明（2026-11-07）

> 范围：标签（tag / article_tag）从零落地 + 文章侧标签接入 + 多级分类环检测 + 管理端鉴权测试。
> 技术栈未变：Spring Boot 3.4.0 / Java 21（本机 JDK 17 以 `-Dmaven.compiler.release=17` 编译）/ MyBatis-Plus 3.5.9 / MySQL 8.0。
> 本轮所有改动**留在工作区未提交**，由 PM 统一提交开 PR。

## 1. 交付清单

### 1.1 新增文件（12 个）

| 模块 | 文件 | 职责 |
| --- | --- | --- |
| blogs-model | `entity/Tag.java` | 标签实体（无 `deleted`，物理删除） |
| blogs-model | `entity/ArticleTag.java` | 文章-标签关联实体 |
| blogs-model | `dto/TagSaveRequest.java` | `name` `@NotBlank @Size(max=30)`、`description` `@Size(max=255)` |
| blogs-model | `vo/TagVO.java` | id / name / description / createdAt + `from(Tag)` |
| blogs-model | `vo/AdminArticleDetailVO.java` | 管理端详情（含 status / tagIds / tags） |
| blogs-core | `mapper/TagMapper.java` | `@Mapper`，BaseMapper |
| blogs-core | `mapper/ArticleTagMapper.java` | `@Mapper`，BaseMapper |
| blogs-core | `service/TagService.java` | `listAllOrdered` / `createTag` / `updateTag` / `deleteTag` |
| blogs-core | `service/impl/TagServiceImpl.java` | 唯一名校验、级联清理、三态语义支撑 |
| blogs-admin | `controller/PublicTagController.java` | `GET /api/v1/tags` |
| blogs-admin | `controller/AdminTagController.java` | `GET/POST /api/v1/admin/tags`、`PUT/DELETE /api/v1/admin/tags/{id}` |
| blogs-admin(test) | `web/AdminArticleSecurityTest.java` | 401 / 403 / 200 三个用例（MockMvc） |

### 1.2 修改文件

`schema.sql`（追加 `tag` / `article_tag`）、`ArticleSaveRequest`（+`tagIds`）、`ArticleQueryRequest`（+`tagId`）、
`ArticleDetailVO`（+`tags`，**移除 status**）、`AdminArticleDetailVO`（新建，管理端专用）、
`ArticleService` / `ArticleServiceImpl`（标签接入、`syncArticleTags`、`tagId` 筛选）、
`CategoryServiceImpl`（父级回溯环检测）、`AdminArticleController`（详情返回 `AdminArticleDetailVO`）、
`PublicApiContractTest` / `ArticleServiceImplTest`（契约与依赖同步）。

## 2. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/tags` | 公开标签列表，扁平数组，`created_at DESC, id DESC` |
| GET | `/api/v1/admin/tags` | 管理端列表（同一份数据） |
| POST | `/api/v1/admin/tags` | 创建，返回新 ID；重名 → 400「标签名称已存在」 |
| PUT | `/api/v1/admin/tags/{id}` | 更新，判重排除自身 |
| DELETE | `/api/v1/admin/tags/{id}` | 物理删除 + 先清 `article_tag` |
| GET | `/api/v1/articles?tagId=x` | 公开列表新增标签筛选（`status=1` 仍强制） |
| GET | `/api/v1/articles/{id}` | 公开详情新增 `tags`（不含 status） |
| GET | `/api/v1/admin/articles/{id}` | 管理端详情新增 `tagIds` / `tags`，改返回 `AdminArticleDetailVO` |

鉴权未写代码：`SecurityConfig` 已对 `/api/v1/admin/**` 要求 `hasRole("ADMIN")`，且已放行 `GET /api/v1/tags/**`。

## 3. `tagIds` 的三态语义（`ArticleServiceImpl#syncArticleTags`）

| 入参 | 行为 | 场景 |
| --- | --- | --- |
| 字段缺失 / `null` | **不修改**，保留原绑定 | 编辑页只改标题，前端没提交标签字段 |
| `[]` | **清空**全部绑定 | 用户把标签全部取消勾选 |
| 非空数组 | 全量覆盖（先删后插） | 常规保存 |

处理细节：

1. **去重保序**：`LinkedHashSet` 去重，重复 tagId 会直接撞 `uk_article_tag` 唯一索引（`Duplicate entry` → 500）。
2. **上限**：去重后超过 10 个 → 400「单篇文章最多绑定 10 个标签」。不设上限的话，一次请求能塞几千个 tagId 生成同样多的 INSERT。
3. **无效 ID 过滤**：只保留 `tag` 表里真实存在的；标签可能刚被别的管理员删掉，整体报错会让用户整篇正文白填。
4. **先删后插**：单篇标签通常只有几个，全量重建比逐条 diff 简单，也不会漏掉「取消勾选」这种解绑。
5. `createArticle` / `updateArticle` 均加 `@Transactional(rollbackFor = Exception.class)`：文章与标签分两张表写，任何一步失败必须整体回滚，否则会出现「文章存了、标签丢了」的半成品。

## 4. 标签为什么用物理删除

- 全局配置了 `logic-delete-field: deleted`。实体若带 `deleted` 字段，`removeById` 会被改写成 `UPDATE tag SET deleted = 1`，
  而 `uk_tag_name` 是唯一索引 —— **删掉再建同名标签会撞唯一键报 500**。
- 所以 `Tag` / `ArticleTag` 都不带 `deleted`、不加 `@TableLogic`，`removeById` 走真实 `DELETE`。
- 关联表同理：软删行会一直占着 `uk_article_tag`，导致「解绑后再绑回来」插入失败。
- `deleteTag` 顺序固定：先 `delete article_tag where tag_id = ?`，再删标签，且在同一事务内。
  反过来的话，一旦清理失败就留下指向不存在标签的孤儿行，文章详情查标签拿到 null，前端渲染异常。

## 5. 多级分类环检测（`CategoryServiceImpl#validateParent`）

原实现只拦「父分类是自己」和「父分类不存在」，`A→B→A` 这种跨层环拦不住。

现改为：从待选父分类开始沿 `parent_id` 向上回溯：

```
cursor = parentId
while cursor > 0:
    若 cursor == selfId          → 400「父分类不能是自己的子分类，会形成循环层级」
    若 回溯层数 > 10             → 400「分类层级超过 10 层或存在循环引用」（兜底，防历史脏数据已成环时死循环）
    parent = getById(cursor)
    若 parent == null            → 400（第一层：父分类不存在；更深层：父分类层级数据异常）
    cursor = parent.parentId
```

- `createCategory` 传 `selfId = null`（新建还没有 id，不可能成环）；
- `updateCategory` 传 `selfId = id`。
- 深度上限 10 是最后一道保险：**撞不到 selfId 的环（库里已存在的脏环）只能靠它退出**，否则接口会挂死。

## 6. VO 拆分：公开侧与管理端

| 字段 | `ArticleDetailVO`（公开） | `AdminArticleDetailVO`（管理端） |
| --- | --- | --- |
| `status` | ❌ 刻意不含 | ✅ 必须含 |
| `tagIds` | ❌ | ✅ 恒为非 null 数组 |
| `tags` | ✅ | ✅ |
| `contentMd` / `contentHtml` | ✅ | ✅ |

原因：

- 管理端编辑表单会把整个 form（含 `status`）原样 PUT 回去，详情不回填 `status` 就取默认 0 → **编辑已发布文章被静默降级为草稿**；
- 公开接口暴露 `status` 既无信息量，又诱导他人尝试 `?status=0` 拉草稿；
- `tagIds` 若返回 `null`，前端 `el-select` 的 `v-model` 绑 null 会抛 `Cannot read properties of null`，编辑弹窗白屏。

`PublicApiContractTest` 已同步为两条断言：管理端详情 **必须**有 `status`/`tagIds`；公开详情 **不得**有 `status`。

## 7. 公开列表 `tagId` 筛选的两个坑

1. **空集合必须先短路**：`article_tag` 里该标签没文章时，若直接 `.in(Article::getId, emptyList)`，MyBatis-Plus 会拼出 `IN ()`，
   MySQL 语法报错 → 接口 500。现在先查 ID 集合，为空就 `PageResult.of(Collections.emptyList(), 0, pageNum, pageSize)` 直接返回。
2. **`tagId` 只是筛选条件，不是可见性开关**：`status = 1` 的强制条件保持不动，
   即便 tagId 命中的全是草稿，也只会得到空列表，不会把草稿带出来。

## 8. 测试与验收

### 8.1 `mvn -Dmaven.compiler.release=17 test`

```
blogs-common   2 项  ✔
blogs-model    3 项  ✔
blogs-security 2 项  ✔
blogs-core     3 项  ✔
blogs-admin   27 项  ✔（含 ArticleServiceImplTest 5、AuthServiceImplTest 6、PageResultTest 4、
                        PublicApiContractTest 6、RestSecurityExceptionHandlerTest 2、
                        AdminArticleSecurityTest 3、BlogsAdminApplicationTests 1 skipped）
合计 37 项，0 失败，1 跳过（BlogsAdminApplicationTests 按条件跳过）
BUILD SUCCESS
```

新增测试注意点：`ArticleServiceImplTest` 是纯 Mockito 单测，没有 MyBatis 启动流程，
`Wrappers.lambdaQuery(ArticleTag.class)` 需要先 `TableInfoHelper.initTableInfo(...)` 注册实体元信息，
否则抛 `can not find lambda cache for this entity`；`ServiceImpl.getById` 还需要
`ReflectionTestUtils.setField(articleService, "baseMapper", articleMapper)` 注入。

`AdminArticleSecurityTest` 用 `@WebMvcTest` + `@ContextConfiguration` 只装 4 个 Bean：
`BlogsApplication` 上有 `@ComponentScan("com.xdl")`，不显式限定上下文会把全部 Service/Mapper 扫进来，
Mapper 没有 `sqlSessionFactory` 直接起不来。测试不依赖 DataSource。

### 8.2 真实 MySQL 冒烟（独立库 `blogs_smoke`，跑完已 DROP）

37 条断言全 PASS，覆盖验收 2~7：

| 验收项 | 结果 |
| --- | --- |
| ② 打 2 个标签，`?tagId=x` 命中；解绑后筛不到 | ✅ |
| ③ 不存在的 tagId 返回空列表而非 500 | ✅（HTTP 200, total=0） |
| ④ 编辑只改标题、不传 tagIds，标签仍在 | ✅ |
| ⑤ 删标签后详情不再显示，`article_tag` 无孤儿行 | ✅（DB 直查：该 tag 关联行 0、标签行 0） |
| ⑥ A 的父级设为子分类 B → 400 | ✅（文案含「循环」） |
| ⑦ 不带 token 请求管理端 → 401 | ✅ |
| 附加：同名标签 400、空白名 400、`[]` 清空、无效 tagId 静默过滤、>10 个 400、物理删除后可重建同名 | ✅ |

## 9. 前端衔接（本轮未改前端代码）

- 后端已交付 `/api/v1/tags` 与 `/api/v1/admin/tags`，但 `ArticleList.vue` 的 `tagFilterEnabled`
  与 `TagManage.vue` 的 `tagsReady` 目前仍为 `false`（阶段 3 占位时做的降级），需要前端把两个开关打开。
- 文章编辑页的标签选择器（`el-select multiple` 绑 `tagIds`）尚未接入，接入后注意：
  - 提交时要区分「没改标签」与「清空标签」——没动选择器就不传 `tagIds`，全清才传 `[]`；
  - 详情返回的 `tagIds` 一定是数组，不会为 null。
- 列表 VO 刻意不含 `tags`（避免 N+1），列表页不要试图展示标签。

## 10. 已知遗留

1. 前端两个开关与标签选择器未接（见第 9 节）。
2. `ArticleQueryRequest.tagId` 未做存在性校验：传不存在的 ID 返回空列表（按需求如此，不报错）。
3. 分类环检测只覆盖「写入时拦截」，库里若已存在历史环不会自动修复，需要一次性数据清洗脚本（当前库无此数据）。
