# PHASE 3 标签模块设计（tag / article_tag）

> 状态：**表结构草案，待负责人确认后由后端落地**
> 背景：后端反馈「标签（阶段 3）未开始，依赖 tag / article_tag 表，需先定表结构」
> 前端现状：`tagFilterEnabled = false` 已降级（标签筛选隐藏并提示「阶段3开放」），接口层 `frontend/src/api/tags.js` 5 个函数已写好，mock 数据已就绪
> 已确认决策：标签纳入 MVP，开发顺序排在文章/分类之后（2026-10-14 负责人确认）

## 一、表结构

追加到 `blogs-admin/src/main/resources/db/schema.sql` 末尾，风格与现有三表保持一致（幂等 `CREATE TABLE IF NOT EXISTS`、`utf8mb4`、`deleted` 逻辑删除、`created_at`/`updated_at`）。

```sql
-- 标签表：与分类是两个维度——分类是树（一篇文章属于一个分类），标签是平面集合（一篇文章可有多个标签）
CREATE TABLE IF NOT EXISTS `tag`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name`        VARCHAR(30) NOT NULL COMMENT '标签名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '标签描述',
    `created_at`  DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='标签表';

-- 文章-标签关联表：多对多中间表
-- 注意：中间表不加 deleted 字段，也不加 @TableLogic。解绑就是物理删除关联行，
-- 否则逻辑删除会让「重新绑定同一标签」撞上唯一索引。
CREATE TABLE IF NOT EXISTS `article_tag`
(
    `id`         BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `tag_id`     BIGINT NOT NULL COMMENT '标签ID',
    `created_at` DATETIME DEFAULT NULL COMMENT '绑定时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_tag` (`article_id`, `tag_id`),
    KEY `idx_article_tag_tag` (`tag_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='文章标签关联表';
```

### 设计要点

| 决策 | 理由 |
| --- | --- |
| `tag.name` 全局唯一（`UNIQUE KEY`） | 标签是平面结构，不像分类有 `parent_id` 分组，「Java」全局只能有一个 |
| `tag.name` 长度 30（分类是 50） | 标签是短词，限制短一点能防「把一句话当标签」 |
| 中间表**不做逻辑删除** | 逻辑删除 + 唯一索引会互斥：解绑后再绑同一标签，唯一索引会撞上 `deleted=1` 的旧行 |
| 中间表带 `created_at` 不带 `updated_at` | 关联关系没有「更新」语义，只有「绑定/解绑」 |
| `idx_article_tag_tag` 索引 | 标签筛选是 `WHERE tag_id = ?` 反查文章，必须有 |
| 不做标签层级、不做标签颜色 | MVP 不需要；真要加颜色，加 `color VARCHAR(7)` 即可，不影响现有结构 |

## 二、接口契约（9 个，与前端 `frontend/src/api/tags.js` 逐字段对齐）

### 2.1 公开侧（2 个）

| # | 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/api/v1/tags` | — | `Result<List<TagVO>>` |
| 2 | GET | `/api/v1/articles?tagId={id}` | 复用现有文章列表，**新增 `tagId` 查询参数** | `Result<PageResult<ArticleSummaryVO>>` |

`TagVO` 字段：`id` / `name` / `description` / `createdAt`

> **第 2 条是本次唯一的公开接口改动**：给 `ArticleQueryRequest` 加 `tagId` 字段。
> 注意与坑点 1 区分——`tagId` 是**筛选条件**，不影响可见性（底层仍然强制 `status=1`）；
> `status` 是**可见性开关**，公开接口绝不能有。两者性质完全不同。

### 2.2 管理端（7 个）

| # | 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- | --- |
| 3 | GET | `/api/v1/admin/tags` | — | `Result<List<TagVO>>` |
| 4 | POST | `/api/v1/admin/tags` | body: `TagSaveRequest` | `Result<Long>` |
| 5 | PUT | `/api/v1/admin/tags/{id}` | body: `TagSaveRequest` | `Result<Void>` |
| 6 | DELETE | `/api/v1/admin/tags/{id}` | — | `Result<Void>` |
| 7 | GET | `/api/v1/admin/articles/{id}` | — | `Result<AdminArticleDetailVO>`（**新增 `tagIds` 字段**） |
| 8 | POST | `/api/v1/admin/articles` | body 增加 `tagIds: Long[]` | `Result<Long>` |
| 9 | PUT | `/api/v1/admin/articles/{id}` | body 增加 `tagIds: Long[]` | `Result<Void>` |

`TagSaveRequest` 字段：

```
name        String  @NotBlank @Size(max=30)
description String  @Size(max=255)  可空
```

### 2.3 文章与标签的绑定规则

- 保存文章时，若 `tagIds` 非空：**先删后插**（`DELETE FROM article_tag WHERE article_id = ?` 再批量 `INSERT`），这是最简单且不会漏的实现
- `tagIds` 传 `null` 表示**不修改**已有绑定；传 `[]` 表示**清空**绑定。两者语义必须区分开，否则前端「只改标题不动标签」会把标签全清掉
- `tagIds` 里的 tag 若不存在，直接忽略而不是报错（避免前端传了已被别人删掉的标签就整体保存失败）
- 单个文章标签数量上限 **10 个**，超出抛 `BusinessException(ResultCode.BAD_REQUEST, "单篇文章最多绑定 10 个标签")`

## 三、前端对接点（已就绪，后端交付后只需切开关）

| 位置 | 现状 | 后端交付后 |
| --- | --- | --- |
| `frontend/src/api/tags.js` | 5 个函数已写好 | 无需改动 |
| `frontend/src/views/public/ArticleList.vue` | `tagFilterEnabled = false`，标签筛选隐藏并提示「阶段3开放」 | 改成 `true` |
| `frontend/src/mock/index.js` | mock 标签 5 接口 + `tagId` 过滤已实现 | 无需改动 |
| `frontend/src/views/admin/TagManage.vue` | 页面完整，走 mock | 无需改动 |
| 文章编辑表单 | 目前无标签选择器 | **需新增**：`el-select` 多选，数据源 `getAdminTags()`，绑定 `form.tagIds` |

## 四、落地顺序建议

1. 后端加两张表 + `Tag` 实体 + `ArticleTag` 实体 + Mapper
2. 后端补 `TagService` / `TagController`（公开 1 + 管理 4）
3. 后端给 `ArticleQueryRequest` 加 `tagId`、给 `ArticleSaveRequest` / `AdminArticleDetailVO` 加 `tagIds`
4. 前端把 `tagFilterEnabled` 置 `true`，文章编辑表单加标签多选
5. 联调验收：给文章打 2 个标签 → 公开列表按标签筛选命中 → 解绑后筛选不到

## 五、不在本期范围

- 标签合并、标签重命名批量迁移
- 标签热度排序、标签云
- 标签颜色 / 图标
