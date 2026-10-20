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
