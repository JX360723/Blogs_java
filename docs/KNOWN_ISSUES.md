# 已知问题清单（KNOWN_ISSUES）

> 阶段：PHASE 0 交付物 2/3
> 更新时间：2026-11-03
> 基线：`origin/main` = `e1ca1e8`
> 分级：P0 阻断/安全 → P1 核心功能 → P2 体验/维护 → P3 未来扩展

## P0（阻断 / 安全）

### P0-1 开发环境数据库密码明文入库

- **位置**：`blogs-admin/src/main/resources/application-dev.yml` 第 6 行 `password: 123456`
- **影响**：仓库公开则库密码泄露；多人协作时环境不一致
- **修复**：改为 `${DB_PASSWORD:}` 占位，本地用环境变量或 `application-dev-local.yml`（gitignore）
- **状态**：🔧 本轮修复

### P0-2 库模块残留冗余 `@SpringBootApplication`，测试必然起不来

- **位置**：
  - `blogs-common/src/main/java/com/xdl/blogscommon/BlogsCommonApplication.java`
  - `blogs-security/src/main/java/com/xdl/bologssecurity/BologsSecurityApplication.java`
- **影响**：
  - `BlogsApplication` 用 `scanBasePackages = "com.xdl"` 全包扫描，会扫到这两个类，造成**自动配置与组件扫描重复执行**（Bean 重复定义风险）
  - `@SpringBootTest` 会搜索 `@SpringBootConfiguration`，找到 **3 个**（BlogsApplication + 上面两个）→ 抛 `IllegalStateException: Found multiple @SpringBootConfiguration annotated classes`，**所有测试直接失败**
  - 这是 Spring Initializr 给每个模块生成启动类的典型脚手架残留，库模块本不需要启动类
- **修复**：
  - 首选：删除这两个类（以及 `blogs-model` 那个没有主类的空测试类）
  - 本轮采用：`BlogsApplication` 加显式 `@ComponentScan` + `REGEX` 排除 `*Application`，**零删除**规避；删除动作待负责人授权
- **状态**：🔧 本轮规避；删除待授权

### P0-3 管理端 9 个接口零实现

- **位置**：`blogs-admin/controller/`（无 AdminArticleController / AdminCategoryController）、`ArticleService` / `CategoryService`（无写方法）
- **影响**：前端管理端文章 / 分类页面全部 404，只能跑 mock；PHASE 3 无法收口
- **修复**：交 WorkBuddy，按 `docs/WORKBUDDY_PROMPT_PHASE3_ADMIN_API.md` 施工
- **状态**：📋 已出施工单，待 WorkBuddy 执行

### P0-4 GitHub PAT 明文出现在群聊

- **位置**：群聊历史（2026-10-20 与后续多次重配）
- **影响**：任何能看到群聊记录的人都能读写仓库
- **修复**：项目收尾后立即在 GitHub Settings 吊销，换 fine-grained token（仅限本仓库、仅 Contents 读写）
- **状态**：⏳ 待负责人操作

## P1（核心功能）

### P1-1 `SecurityConfig` 放行了不存在的端点

- **位置**：`SecurityConfig` `INFRA_ENDPOINTS = {"/actuator/health", "/error"}`，但 `blogs-admin/pom.xml` 无 `spring-boot-starter-actuator`
- **影响**：健康检查 404；容器编排 / 负载均衡无法探活；部署阶段会卡
- **修复**：加 `spring-boot-starter-actuator`，`application.yaml` 只暴露 `health`
- **状态**：🔧 本轮修复

### P1-2 全模块缺 `spring-boot-starter-test`

- **位置**：`pom.xml` 无该依赖，5 个模块 pom 也没有
- **影响**：5 个 `*ApplicationTests.java` 里的 `@Test` / `@SpringBootTest` 无法编译，`mvn test` 直接报错
- **修复**：父 pom `dependencyManagement` 引入（版本由 Spring Boot parent 管理），`blogs-admin` 加 `test` scope 依赖
- **状态**：🔧 本轮修复

### P1-3 无任何认证鉴权自动化测试

- **位置**：`blogs-admin/src/test/` 只有空 `contextLoads`
- **影响**：登录、401、403、参数校验全靠手测；改 SecurityConfig 无回归保护
- **修复**：本轮补 `AuthSecurityTest`（MockMvc，不依赖真实数据库：用 `@WebMvcTest` + MockBean 隔离，或 `@SpringBootTest` + H2 —— 本轮采用**不启动上下文的分层测试**，见 P1-3 实现说明）
- **状态**：🔧 本轮修复

### P1-4 管理端列表 VO 缺 status / updatedAt

- **位置**：`ArticleSummaryVO` 字段 = id/title/summary/categoryId/categoryName/coverImage/viewCount/isTop/createdAt
- **影响**：管理页要显示「草稿 / 已发布 / 已下架」和「更新时间」，公开 VO 提供不了
- **修复**：新建 `AdminArticleSummaryVO`（加 status + updatedAt），**不得给公开 `ArticleSummaryVO` 加 status**，否则管理端字段污染公开接口
- **状态**：📋 已写入施工单

### P1-5 多级分类存在跨层成环风险（后端 2026-11-04 反馈）

- **位置**：`CategoryService` 更新分类时的父分类校验
- **现状**：已拦截「父分类是自己」和「父分类不存在」两种情况，但 **A→B→A 这类跨层成环未做递归校验**
- **影响**：MVP 前端不提供父子分类选择（分类表单只有 name/description/sortOrder，不传 parentId），所以当前**触发不到**；一旦开放多级编辑，成环会让分类树渲染无限递归或直接栈溢出
- **修复方案**：更新分类前从目标 `parentId` 沿 `parent_id` 向上回溯，若回溯路径中出现当前分类 ID 则判定成环，抛 `BusinessException(ResultCode.BAD_REQUEST, "不能把分类移动到自己的子分类下")`；同时加回溯深度上限（如 10 层）防脏数据导致死循环
- **状态**：📅 开放多级分类编辑前必须补，当前不阻塞

### P1-6 管理端 401/403 缺少 MockMvc 链路级测试（后端 2026-11-04 反馈）

- **位置**：`blogs-admin/src/test/java/com/xdl/blogsadmin/unit/`
- **现状**：已有 `RestSecurityExceptionHandlerTest` 覆盖 401/403 的**响应体格式**，但那是直接调用异常处理器，**没有走真实的 Security filter chain**
- **为什么现在补不了**：`@WebMvcTest(controllers = AdminArticleController.class)` 依赖 Controller 类存在，而管理端 Controller 尚在后端本地开发中、未进仓库
- **已做的替代**：新增 `PublicApiContractTest`（反射断言公开 DTO 无 status、公开 VO 无 status/updatedAt、详情 VO 带 contentMd），把最容易出安全事故的三条约定锁死
- **待补**：管理端 Controller 合并后，补 `@WebMvcTest` + `@Import(SecurityConfig.class)` 的 401/403 断言，三条回归线为「公开不带 status」「管理端 401/403」「草稿隔离」
- **状态**：🔒 依赖 P0-3（管理端接口）合并

## P2（体验 / 维护）

### P2-1 `application.txt` 垃圾文件

- **位置**：`blogs-admin/src/main/resources/application.txt`，内容 `spring.application.name=blogs-admin`
- **影响**：无功能影响，但会让人误以为有第二个配置文件；打包进 jar 无意义
- **修复**：删除（待授权）或加 `.gitignore`
- **状态**：⏳ 待授权

### P2-2 `PHASE_4_FRONTEND.md` 技术栈写成 TypeScript

- **位置**：`docs/PHASE_4_FRONTEND.md`
- **影响**：与已锁定的「Vue3 + 纯 JavaScript」冲突，新人照文档会写错
- **修复**：全文把 TS 表述改为纯 JS
- **状态**：🔧 本轮修复

### P2-3 生产环境 SQL 日志全开

- **位置**：`application.yaml` `mybatis-plus.configuration.log-impl: StdOutImpl`
- **影响**：主配置里开 SQL 打印，生产会打全量 SQL（含参数），有性能与信息泄露风险
- **修复**：把 `log-impl` 移到 `application-dev.yml`，主配置不设
- **状态**：🔧 本轮修复

## P3（未来扩展）

### P3-1 仓库根 `sql/` 与 `db/schema.sql` 重复

- **位置**：`sql/schema.sql`（未跟踪）、`blogs-admin/src/main/resources/db/schema.sql`（已入库）
- **影响**：两份三表脚本并存，后续改表容易只改一份
- **修复**：以 `db/schema.sql` 为唯一来源，`sql/` 删除或改为文档说明（待负责人决定）
- **状态**：⏳ 待决策

### P3-2 `article.content_html` 永远为 null

- **位置**：`schema.sql` 有 `content_html`，后端无渲染逻辑，前端用 `contentMd` 自行渲染
- **影响**：字段冗余；若将来加服务端渲染需回填历史数据
- **修复**：保留字段（预留），或明确不做服务端渲染后移除
- **状态**：📝 记录，暂不处理

### P3-3 标签模块整体未做

- **位置**：`tag` 表不存在；`ArticleQueryRequest` 无 `tagId`；`SecurityConfig` 已放行 `/tags/**` 但无对应 Controller
- **影响**：前端 `ArticleList.vue` 已把标签筛选置 `tagFilterEnabled = false` 降级隐藏
- **修复**：表结构与接口契约已定稿，见 `docs/PHASE3_TAG_DESIGN.md`（tag / article_tag 两张表、公开 2 + 管理端 7 个接口、绑定规则）
- **状态**：📐 设计已定稿，等管理端主链路交付后排期落地

## 附：本轮修复清单（2026-11-03）

| 编号 | 问题 | 处理方式 |
| --- | --- | --- |
| P0-1 | dev 库密码明文 | 改环境变量占位 |
| P0-2 | 冗余启动类 | 显式 `@ComponentScan` 排除，零删除 |
| P1-1 | 无 actuator | 加依赖 + 只暴露 health |
| P1-2 | 无测试依赖 | 父 pom + admin 模块引入 |
| P1-3 | 无认证测试 | 新增 9 个测试类（4 纯单元 + 5 改造），另补 `PublicApiContractTest` 锁公开契约 |
| P2-2 | PHASE_4 写 TS | 文档修订 |
| P2-3 | 生产开 SQL 日志 | 移到 dev profile |

**2026-11-04 增量（响应后端反馈）**：

| 编号 | 事项 | 处理 |
| --- | --- | --- |
| 前端 P0 | `openEdit` 用列表行回填导致正文被清空 | ✅ 已修（前端 e9a3446）：新增 `GET /admin/articles/{id}` 详情接口 + 先拉详情再回填 |
| P1-5 | 多级分类跨层成环未校验 | 📅 已记录，开放多级编辑前必须补 |
| P1-6 | 管理端 401/403 缺 MockMvc 链路测试 | 🔒 阻塞于管理端 Controller 未进仓库，先以反射契约测试替代 |
| P3-3 | 标签表结构未定 | 📐 已定稿 `docs/PHASE3_TAG_DESIGN.md` |

**未处理（需授权 / 待决策）**：P0-4 PAT 吊销、P0-3 管理端接口（已出施工单，后端本地开发中）、P2-1 删 `application.txt`、P3-1 `sql/` 目录去向、P0-2 删除动作。
