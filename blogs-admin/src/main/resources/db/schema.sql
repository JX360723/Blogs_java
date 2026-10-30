-- 后台管理员表：字段定义见 docs/API_CONTRACT.md 6.2.1「后端落地要求」
-- 脚本幂等，可重复执行；初始管理员由 AdminUserInitializer 依据环境变量 ADMIN_INIT_PASSWORD 创建
CREATE TABLE IF NOT EXISTS `admin_user`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
    `username`   VARCHAR(50)  NOT NULL COMMENT '登录用户名',
    `password`   VARCHAR(100) NOT NULL COMMENT '密码（bcrypt 哈希）',
    `nickname`   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`     VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    `role`       VARCHAR(20)  NOT NULL DEFAULT 'ADMIN' COMMENT '角色标识',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `created_at` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_user_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='后台管理员表';

-- 文章表：字段与 blogs-model 的 Article 实体逐字段对应，索引见 docs/BACKEND_DESIGN_DB_AND_API.md 3.3
-- 状态：0-草稿，1-已发布，2-已下架；公开接口只暴露 status=1
CREATE TABLE IF NOT EXISTS `article`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文章ID',
    `title`        VARCHAR(120) NOT NULL COMMENT '文章标题',
    `summary`      VARCHAR(500)  DEFAULT NULL COMMENT '摘要/简介',
    `content_md`   LONGTEXT      DEFAULT NULL COMMENT 'Markdown 正文',
    `content_html` LONGTEXT      DEFAULT NULL COMMENT '渲染后的 HTML 正文',
    `ai_summary`   VARCHAR(1000) DEFAULT NULL COMMENT 'AI 自动摘要（预留，MVP 不生成）',
    `category_id`  BIGINT        DEFAULT NULL COMMENT '所属分类ID',
    `user_id`      BIGINT        DEFAULT NULL COMMENT '作者（管理员）ID',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-草稿，1-已发布，2-已下架',
    `cover_image`  VARCHAR(255)  DEFAULT NULL COMMENT '封面图URL',
    `view_count`   INT          NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `is_top`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
    `created_at`   DATETIME      DEFAULT NULL COMMENT '创建时间',
    `updated_at`   DATETIME      DEFAULT NULL COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_article_status` (`status`),
    KEY `idx_article_category` (`category_id`),
    KEY `idx_article_created` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='文章表';

-- 分类表：自关联 parent_id 支持多级，parent_id=0 为顶级；sort_order 越小越靠前
CREATE TABLE IF NOT EXISTS `category`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name`        VARCHAR(50) NOT NULL COMMENT '分类名称',
    `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类ID，0 表示顶级',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '排序权重，数值越小越靠前',
    `created_at`  DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_category_parent` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='知识版块分类表';
