# PHASE 6 前后端联调报告

- **联调时间**：2026-11-13 18:49 – 18:52（GMT+8）
- **目标环境**：Docker Compose 全套（`blogs-frontend` Nginx :80 → `blogs-backend` Spring Boot :8080 → `blogs-mysql` 8.0）
- **前端**：Vue3 + JavaScript，镜像内 `VITE_USE_MOCK=false`（构建期注入）
- **账号**：`admin`（密码取本机 `deploy/.env` 的 `ADMIN_INIT_PASSWORD`）
- **执行方式**：`python scripts/integration_test.py` 自动断言 67 条 + 补充手工/脚本外部验证
- **结论**：**67 / 67 自动断言全部 PASS**；42 条用例中 30 条接口层完全验证通过，12 条 UI 渲染类完成代码级核对并标注需人工点视确认；**未发现后端功能缺陷**（唯一失败项经查为联调脚本自身字段名笔误，已修正）

---

## 一、42 条用例逐条结论

> 验证方式说明：**API** = 真实 HTTP 请求断言；**代码核对** = 检查实现逻辑 + 构建产物；**人工** = 需要浏览器肉眼确认渲染效果。

### 3.1 访客端（不登录）

| # | 用例 | 验证方式 | 结论 | 证据 |
| --- | --- | --- | --- | --- |
| 1 | 打开首页 | API + 代码核对 | ✅ PASS | `GET /api/v1/articles` 返回 `{list,total,pageNum,pageSize,pages}`；`ArticleList.vue:69-73` 有 `el-pagination :total`，`layout` 含 `total` |
| 2 | 点分类筛选 | API + 代码核对 | ✅ PASS | `?categoryId=` 结果全部属于该分类；`ArticleList.vue:98-111` 分类值与请求参数绑定 |
| 3 | 点标签筛选 | API + 代码核对 | ✅ PASS | `?tagId=` 命中该文章；`ArticleList.vue:42,111` |
| 4 | 分类 + 标签同时筛选 | API | ✅ PASS | 组合筛选返回交集 |
| 5 | 搜索关键词 | API + 代码核对 | ✅ PASS | 中文关键词命中；`keyword=zzz不存在zzz` → `total:0, list:[]`；`ArticleList.vue:66` 有 `el-empty description="暂无文章"` |
| 6 | 翻到第 2 页 | API | ✅ PASS | 5 篇数据 `pageSize=2`：page1 `[11,14]`、page2 `[12,13]`，**无重复无遗漏** |
| 7 | 点进详情 | API + 代码核对 | ✅ PASS | 详情返回非空 `contentHtml`（服务端 Markdown 渲染）；`ArticleDetail.vue:35` `v-html="renderedMd"` |
| 8 | 详情页刷新阅读数 +1 | API | ✅ PASS | 连续两次 GET：`1 → 2`，**每个请求恰好 +1 次** |
| 9 | 访问草稿文章 ID | API | ✅ PASS | HTTP 404 |
| 10 | 访问已下架文章 ID | API | ✅ PASS | HTTP 404，文案「文章不存在或未发布」 |
| 11 | 访问不存在的 ID | API | ✅ PASS | HTTP 404，文案与 9/10 **完全一致** |
| 12 | 详情页标签展示 | API + 代码核对 | ✅ PASS | 详情 `tags` 数组含 2 个标签；`ArticleDetail.vue:22-24` 按 `article.tags` 渲染 |

### 3.2 管理端（需登录）

| # | 用例 | 验证方式 | 结论 | 证据 |
| --- | --- | --- | --- | --- |
| 13 | 错误密码登录 | API | ✅ PASS | 401「用户名或密码错误」（不区分用户是否存在） |
| 14 | 正确密码登录 | API | ✅ PASS | 返回 `token/tokenType/expiresIn`，`/auth/me` 可取管理员信息 |
| 15 | 未登录访问 `/admin/articles` | 代码核对 | ✅ PASS（需人工点视） | `router/index.js:68-73` 守卫：`meta.requiresAuth && !token` → 跳 `/login?redirect=` |
| 16 | 无 token 调管理接口 | API | ✅ PASS | 401 + `Result` 包络（`code/message/data/timestamp`） |
| 17 | 非 ADMIN 角色调管理接口 | 单测 | ✅ PASS | `AdminArticleSecurityTest:87-93` `status().isForbidden()`；系统仅 admin 一个账号，无法用真实账号验证（单测已锁） |
| 18 | 文章列表 status 列 | API + 代码核对 | ✅ PASS | 列表项含 `status`；`ArticleManage.vue:352-354` 映射 `{0:草稿,1:已发布,2:已下架}`，兜底「未知」不会误触发 |
| 19 | 新建文章存草稿 | API | ✅ PASS | 草稿创建成功，公开列表与详情均不可见（404） |
| 20 | 编辑文章正文框有内容 | API + 代码核对 | ✅ PASS | **【P0 回归】** 管理详情返回非空 `contentMd`；`ArticleManage.vue:398-410` 编辑必先拉详情再回填（注释明确写了列表接口不返回 contentMd 的原因） |
| 21 | 编辑已发布文章不变草稿 | API | ✅ PASS | **【P0 回归】** 编辑后 `status` 仍为 1 |
| 22 | 快捷「发布」 | API | ✅ PASS | 状态 → 1，公开详情立即可访问 |
| 23 | 快捷「下架」 | API | ✅ PASS | 状态 → 2，公开详情立即 404 |
| 24 | 给文章打标签 | API | ✅ PASS | 保存后详情 `tags` 与公开列表均可见 |
| 25 | 编辑不碰标签直接保存 | API | ✅ PASS | **【三态回归】** 不传 `tagIds` → 标签保留（仍 2 个） |
| 26 | 手动清空所有标签 | API | ✅ PASS | 传 `tagIds: []` → 标签清空 |
| 27 | 单篇打 11 个标签 | API | ✅ PASS | 400「单篇文章最多绑定 10 个标签」；绑定 10 个成功（边界值） |
| 28 | 新建分类 | API | ✅ PASS | 出现在管理列表与公开分类列表 |
| 29 | 新建子分类（选上级） | API + 代码核对 | ✅ PASS | `parentId` 生效、公开接口返回 `parentId`；`CategoryManage.vue:110-129` 层级缩进与上级名称展示 |
| 30 | 父分类设为自身子分类 | API | ✅ PASS | 400「父分类不能是自己的子分类，会形成循环层级」 |
| 31 | 删除有文章的分类 | API | ✅ PASS | 400，提示存在引用 |
| 32 | 删除有子分类的分类 | API | ✅ PASS | 400「该分类下还有子分类，请先删除子分类」，与 `ADMIN_API_IMPLEMENTATION.md:79`、`BACKEND_DESIGN_DB_AND_API.md:436` 一致（决策为拒绝，不级联） |
| 33 | 新建标签 | API | ✅ PASS | 出现在管理列表与公开标签列表 |
| 34 | 删除已绑定文章的标签 | API | ✅ PASS | 删除成功，且文章上的该标签被级联解绑（`article_tag` 无脏数据） |
| 35 | 标签重名 | API | ✅ PASS | 400「标签名称已存在」 |

### 3.3 边界与异常

| # | 用例 | 验证方式 | 结论 | 证据 |
| --- | --- | --- | --- | --- |
| 36 | `pageSize=1000` | API | ✅ PASS | 400「每页条数不能超过100」（`@Max(100)`） |
| 37 | `pageNum=0` / 负数 | API | ✅ PASS | 400「页码必须大于0」（`@Min(1)`） |
| 38 | 文章 ID 传字符串 | API | ✅ PASS | 400 + `Result` 包络（`MethodArgumentTypeMismatchException`），不是 500 |
| 39 | 标题超长 | API | ✅ PASS | 400「标题长度不能超过120位」。**注**：实现上限为 120 字（`ArticleSaveRequest:44` `@Size(max=120)`），比施工单举的「>200 字」更严，150 字同样会被拒——属更严格约束，非缺陷 |
| 40 | `status=99` | API | ✅ PASS | 400「文章状态值非法，只能为0/1/2」 |
| 41 | token 过期后操作 | API | ✅ PASS | 用真实 `JWT_SECRET` 构造 `exp` 已过期 1 小时的 HS256 token → **401**「未登录或登录已过期」+ `Result` 包络 |
| 42 | 并发两次阅读同一文章 | API | ✅ PASS | 20 线程并发 GET：基线 1 → 最终 22（= 1 + 20 + 1），**零丢失零报错**。实现为 `UPDATE article SET view_count = view_count + 1`（`ArticleServiceImpl:161`），非读改写 |

---

## 二、暴露的缺陷

### 缺陷总览

| 编号 | 级别 | 现象 | 归属 | 状态 |
| --- | --- | --- | --- | --- |
| T-1 | P2（工具） | 联调脚本「阅读数递增」恒失败，打印 `None → None` | 脚本（非后端） | ✅ 已修 |

**本轮后端代码零缺陷**：42 条用例涉及的业务逻辑、鉴权、校验、并发、三态语义全部符合契约。

### T-1 联调脚本读了不存在的字段名

- **现象**：脚本第 348 行断言失败，输出 `None → None`，即两次读取都没拿到阅读数。
- **排查**：脚本读 `data.views`，但接口返回的字段是 `viewCount`。
- **判定依据**（为什么是脚本的问题而不是后端的问题）：
  1. `docs/API_CONTRACT.md:458` 明确定义响应字段为 `viewCount`（`| viewCount | int | 浏览次数 |`）；
  2. 前端 4 处消费方全部使用 `viewCount`：`ArticleDetail.vue:19`、`ArticleList.vue:60`、`ArticleManage.vue:39`、mock 数据 `data.js:32`；
  3. 施工单第 42 条写的 `UPDATE ... SET views = views + 1` 是**数据库列语义的示意**，实际列名为 `view_count`。
- **修复**：`scripts/integration_test.py:344,347` 改为 `viewCount`。修复后该用例 PASS，并额外验证了并发递增。
- **说明**：本次修改属于**修正测试自身的字段名错误**，不是「为了让用例通过而改断言」——原断言 `v2 == v1 + 1` 的逻辑一字未动。

---

## 三、验收标准对照

| # | 标准 | 结果 |
| --- | --- | --- |
| 1 | 42 条用例全部 PASS，或 FAIL 有说明且不属 P0/P1 | ✅ 42/42 PASS（12 条 UI 项为代码核对 + 需人工点视，均无 FAIL） |
| 2 | `VITE_USE_MOCK=false` 下可见真实请求 | ✅ 前端镜像产物 18 个 JS chunk 中 mock 标记命中 **0**；`admin/files` 等真实接口路径命中 1，说明走真实 axios |
| 3 | 三条历史 P0 回归点 | ✅ 全部确认：编辑正文不空（#20）、编辑已发布不变草稿（#21）、不碰标签不丢标签（#25） |
| 4 | 「登录 → 发文 → 访客看到 → 下架 → 404」闭环 | ✅ 同一环境同一实例内完成（#14 → #19/22 → #3 → #23） |
| 5 | 后端日志无 500、无未捕获异常栈 | ✅ 近 30 分钟日志：`ERROR` **0** 条、异常栈 **0** 条；32 条 `WARN` 全部是负向用例的预期业务提示（401/404/400）。同时 `==> Preparing` / `==> Parameters` 均为 **0**（prod profile 生效） |

---

## 四、需人工点视确认的 12 项（渲染层）

接口层已全部验证通过，下列 12 项仅涉及浏览器渲染效果，代码核对结论如下表。建议打开 `http://localhost` 与 `http://localhost/login`（admin / `deploy/.env` 里的密码）各点一遍：

| 清单项 | 代码核对结论 |
| --- | --- |
| 1 首页列表 + 分页总数 | `el-pagination` 绑定 `total`，`layout` 含 `total` → 会显示 |
| 2 分类/标签筛选后列表变化 | 筛选值变化触发重新请求，参数已绑定 |
| 3 搜索无结果空态 | `el-empty description="暂无文章"`，且后端返回 `total:0` 而非报错 |
| 4 Markdown 渲染（标题/加粗/代码块/表格/图片） | `utils/markdown.js` 自实现，与编辑端预览同源；服务端 `contentHtml` 亦非空（双保险） |
| 5 详情页底部标签 | `v-if="article.tags && article.tags.length"`，无标签整块不渲染（不会出现空盒子） |
| 6 未登录访问 `/admin/articles` 重定向 | 路由守卫 `requiresAuth` + `redirect` 回跳 |
| 7 status 列显示中文而非「未知」 | `statusText()` 覆盖 0/1/2 |
| 8 编辑后正文框有内容 | `openEdit` 必先 `getAdminArticle` 再回填 `contentMd`，注释已说明为何不用列表行 |
| 9 编辑弹窗标签多选回填 | `tagIds: Array.isArray(detail.tagIds) ? [...detail.tagIds] : undefined` |
| 10 发布/下架确认弹窗 + 列表刷新 | `onChangeStatus` 带 `ElMessageBox` 确认与行级 loading |
| 11 分类页层级缩进 + L1/L2 + 上级下拉 | `parentOf` 映射算层级、`nameOf(parentId)` 显示上级名、编辑时排除自身与后代 |
| 12 标签页创建时间不是 `-` | `formatDate(row.createdAt)`，`TagVO` 含 `createdAt`（无 `updatedAt`，页面也不需要） |
| 13 DevTools 可见真实 `/api/v1/` 请求 | 构建产物 mock 命中 0（见验收标准 2） |

---

## 五、遗留项与未修复说明

| 项 | 级别 | 说明 |
| --- | --- | --- |
| 阅读数无防刷/去重 | P3 | 每次详情 GET 都 +1，刷新即可刷量。MVP 未设计 IP/会话去重，属预期行为；公网部署前可加 Redis 去重窗口 |
| URI 含未编码的非 ASCII 字节返回 Tomcat HTML 400 | P3 | 浏览器与 axios 都会自动百分号编码，真实链路触发不到；仅手写 curl 时会看到非 `Result` 包络 |
| 非 ADMIN 角色 403 无真实账号验证 | P3 | 系统只有 admin 一个账号，无注册/多角色入口；由 `AdminArticleSecurityTest`（`@WebMvcTest`）锁定 |
| 标题上限 120 与施工单「>200」表述不一致 | 说明 | 实现更严格，150 字也会 400；如需放宽到 200 需同时改 DTO 与前端提示 |
| 前端无浏览器自动化测试 | P3 | 本轮 12 项渲染靠代码核对；后续可引入 Playwright 做冒烟 |

## 六、复现方式

```bash
# 1) 起全套
cd deploy && docker compose up -d --build

# 2) 跑联调（67 条自动断言）
cd .. && python scripts/integration_test.py \
  --password "$(grep '^ADMIN_INIT_PASSWORD=' deploy/.env | cut -d= -f2)" \
  --report /tmp/phase6-report.md

# 3) 日志核对（应全为 0）
docker compose -f deploy/docker-compose.yml logs backend | grep -c '==>  Preparing'
docker compose -f deploy/docker-compose.yml logs backend | grep -c ' ERROR '
```

脚本会自建 `__IT_` 前缀数据并在结束时全部清理（文章/分类/标签逐条删除并校验返回 200），不会污染业务数据。
