# PHASE 4 前端配套实现说明

> 对应后端提交：`6d82a84`（标签模块、分类环检测、管理端鉴权测试）
> 前端分支：`feature/admin-pages-frontend`
> 完成时间：2026-11-07

## 一、这一轮做了什么

后端 PHASE 4 把标签模块做完了，前端原来所有「阶段 3 降级」的开关都可以拆掉，同时把管理页几个一直缺的功能补齐。

| 页面 | 改动 |
| --- | --- |
| `public/ArticleList.vue` | 移除 `tagFilterEnabled` 开关与降级占位，标签筛选条常态可用；删掉死代码 `onCategoryChange` |
| `public/ArticleDetail.vue` | 标题下方渲染 `article.tags`（`List<TagVO>`），无标签时整块不渲染 |
| `admin/TagManage.vue` | 移除 `tagsReady` 降级；「更新时间」改「创建时间」读 `createdAt`；提交时剥离 `id`；删除提示补充「会解绑所有关联文章」 |
| `admin/ArticleManage.vue` | 编辑弹窗新增标签多选；列表操作列新增「发布 / 下架」快捷按钮；`tagIds` 三态防护 |
| `admin/CategoryManage.vue` | 新增「上级分类」选择（`el-tree-select`）、名称列层级缩进与 L 标签、上级分类列；编辑时剔除自身与子孙 |
| `mock/index.js` | 详情补 `tags` 数组；删除标签级联清理文章 `tagIds` |

## 二、三个必须解释的实现决策

### 1. `tagIds` 的三态，前端不能只当数组用

后端 `ArticleSaveRequest.tagIds` 有三种语义：

- 字段不传 / `null` → **不修改**已绑定标签
- `[]` → **清空**全部标签
- 非空数组 → 全量覆盖

如果前端把「拿不到 tagIds」也归一成 `[]`，那么详情接口一旦出问题（字段缺失、请求失败），用户点一次保存就会把文章标签全清掉，而且页面不会报错。

所以 `form.tagIds` 的初始值与回填逻辑是：

```js
// 新建：确实没有绑定，[] 是准确语义
tagIds: []

// 编辑：详情带 tagIds 才用，没带就留 undefined
tagIds: Array.isArray(detail.tagIds) ? [...detail.tagIds] : undefined
```

保存时：

```js
const payload = { ...form }
delete payload.id
// undefined 会被 JSON.stringify 丢掉，等价于「不传该字段」→ 后端按不修改处理
if (payload.tagIds === undefined) delete payload.tagIds
```

对应地，标签选择器在 `tagIds === undefined` 时禁用，placeholder 直接告诉用户「标签未加载，本次保存不会改动标签」。

### 2. 快捷发布 / 下架不能只提交 `{ status }`

看起来很自然，但后端会拦：

- `ArticleSaveRequest.title` 是 `@NotBlank`，只传 `status` 直接 400「标题不能为空」
- 即使绕过校验，`updateArticle` 内部有 `update.setTitle(request.getTitle().trim())`，`title` 为 `null` 直接 NPE

所以 `onChangeStatus` 的实现是**先拉详情，改 status 后整份提交**：

```js
const detail = await getAdminArticle(row.id)
const payload = {
  title: detail.title,
  summary: detail.summary,
  contentMd: detail.contentMd,
  categoryId: detail.categoryId,
  coverImage: detail.coverImage,
  isTop: detail.isTop,
  status
}
if (Array.isArray(detail.tagIds)) payload.tagIds = detail.tagIds
await updateArticle(row.id, payload)
```

多一次请求换「点一下发布，标签被清空」这种事故不发生，值得。

### 3. 分类的「上级分类」要剔除自身和所有子孙

后端 `CategoryServiceImpl.validateParent` 已经做了多级环检测（沿 `parent_id` 回溯，深度上限 10 层），但让用户先选出一个非法值再收到 400，体验很差。

前端在编辑时先把自身与所有后代从候选树里剔除：

```js
const banned = new Set([form.id])
let changed = true
while (changed) {              // 反复扫描直到没有新增，等价于求传递闭包
  changed = false
  list.value.forEach((c) => {
    if (c.parentId && banned.has(c.parentId) && !banned.has(c.id)) {
      banned.add(c.id)
      changed = true
    }
  })
}
```

层级深度计算也加了 10 层上限兜底——万一库里已有脏数据成环，页面不能跟着无限递归卡死。

## 三、TagManage 修掉的两个字段错位

| 问题 | 原因 | 修法 |
| --- | --- | --- |
| 「更新时间」列永远是 `-` | 后端 `TagVO` 只有 `createdAt`，**没有** `updatedAt`（后端刻意省掉，页面无此需求） | 列名改「创建时间」，读 `row.createdAt` |
| 新建标签请求体里带 `id: null` | `createTag({ ...form })` 把表单整个发过去 | 只提交 `{ name, description }` |

第一个问题在 mock 阶段看不出来——mock 的标签数据里 `updatedAt` 是有的，只有切真实后端才会显示 `-`。

## 四、验证

### 静态校验

```bash
cd frontend && npx vite build
```

构建通过即说明五个 `.vue` 文件模板语法、`script setup` 变量引用、mock 模块导入均正常。

### 联调验证项（需本机跑真实后端）

| 项 | 预期 |
| --- | --- |
| 首页标签筛选条 | 有标签时显示，点选后列表按 `tagId` 过滤，再点取消 |
| 详情页标签 | 标题下方显示标签；文章无标签时整块不显示 |
| 编辑文章只改标题 | 保存后标签原样保留（验证 `tagIds` 三态） |
| 编辑文章加/减标签 | 保存后详情页标签同步变化 |
| 列表点「发布」 | 草稿变已发布，公开列表可见，标签不丢 |
| 列表点「下架」 | 已发布变已下架，公开列表消失 |
| 标签管理删除标签 | 提示会解绑；删除后文章详情不再显示该标签 |
| 分类编辑选上级 | 候选中看不到自己与子孙；选父级保存后表格层级缩进变化 |
| 标签页时间列 | 显示创建时间，不是 `-` |

## 五、仍未做的事

- 管理端没有独立的「文章预览」入口，预览只能靠编辑弹窗右侧的 Markdown 实时渲染
- 标签筛选不支持多选（后端 `ArticleQueryRequest.tagId` 是单值，要支持多选得改后端契约）
- 分类树在表格里是扁平 + 缩进，不是可折叠的 `el-table` 树形结构（分类数量少，够用）
