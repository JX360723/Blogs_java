# 项目指导规范 - Knowledge Blog Backend (blogs)

本文档专为 Claude Code / AI 辅助开发助手编写，包含项目架构、技术栈规范、数据库 Schema 及常用指令。

---

## 1. 项目概览与技术栈

- **项目名称**: blogs
- **架构模式**: MVC 分层架构 (Controller -> Service -> Mapper/DAO)
- **核心框架**: Spring Boot 3.4.0 + Java 21 (LTS)
- **持久层框架**: MyBatis-Plus 3.5.9
- **数据库**: MySQL 8.0 (数据库名: `blogs_db`)
- **安全认证**: Spring Security + JWT (JJWT 0.12.6)
- **缓存与限流**: Spring Data Redis
- **构建工具**: Maven

---

## 2. 核心代码规范与约定

### 2.1 命名规范
- **包路径**: `com.xdl.xdl`
- **数据库风格**: 统一采用下划线命名（例如 `category_id`, `created_at`）。
- **Java 实体类风格**: 统一采用驼峰命名（例如 `categoryId`, `createdAt`），依赖 MyBatis-Plus 自动下划线转驼峰属性。
- **接口设计**: 遵循 RESTful 规范，统一返回 `Result<T>` 响应包装类。

### 2.2 实体与分层结构
- **Entity**: 位于 `com.xdl.xdl.entity`，必须开启 Lombok 注解 (`@Data`, `@Builder`)，并配置 `@TableName` 和 `@TableId`。
- **Mapper**: 位于 `com.xdl.xdl.mapper`，继承 `BaseMapper<T>`。
- **Service**: 接口位于 `com.xdl.xdl.service`，实现类位于 `com.xdl.xdl.service.impl`（继承 `ServiceImpl<Mapper, Entity>`）。
- **Controller**: 位于 `com.xdl.xdl.controller`，使用 `@RestController` 与 `@RequestMapping`。

---

## 3. 数据库 Schema 约定 (`blogs_db`)

### 核心数据表
1. **`sys_user`** (用户表): 管理员与普通用户账号、角色 (`role`) 及 JWT 认证数据。
2. **`category`** (知识版块分类表): 支持 `parent_id` 树状层级分类，`sort_order` 控制权重。
3. **`article`** (文章表): 存储 Markdown (`content_md`) 与 HTML (`content_html`)，预留 `ai_summary` 字段。
4. **`tag` & `article_tag`** (标签体系): 文章与标签多对多关联。
5. **`ai_chat_session` & `ai_chat_message`** (AI 问答历史表): 预留会话与明细上下文记录。

---

## 4. 常用开发与构建命令

- **项目构建**: `mvn clean package`
- **跳过测试构建**: `mvn clean package -DskipTests`
- **本地启动**: `mvn spring-boot:run`

---

## 5. Claude Code 交互提示规则

在为此项目生成代码或重构时，请严格遵守以下规则：
1. **不要修改无需变动的代码**，保持类结构干净优雅。
2. 生成 MyBatis-Plus 实体类时，使用 `@TableLogic` 标注 `deleted` 逻辑删除字段，自动注入 `createdAt` 和 `updatedAt`。
3. Controller 层的任何返回结果必须统一封装为 `Result.success(data)` 或 `Result.error(code, message)`。
4. 在涉及权限或 AI 交互的代码中，需预留限流和安全性逻辑。