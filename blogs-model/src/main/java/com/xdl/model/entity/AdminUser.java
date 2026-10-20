package com.xdl.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台管理员实体类
 * <p>
 * 对应表 admin_user，字段定义见 docs/API_CONTRACT.md 6.2.1「后端落地要求」。
 * 密码以 bcrypt 哈希存储，禁止明文与可逆加密，且不参与 toString 输出。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("admin_user")
public class AdminUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色：管理员（MVP 仅此一种角色）
     */
    public static final String ROLE_ADMIN = "ADMIN";

    /**
     * 状态：启用
     */
    public static final Integer STATUS_ENABLED = 1;

    /**
     * 状态：禁用
     */
    public static final Integer STATUS_DISABLED = 0;

    /**
     * 管理员主键ID，写入 JWT 的 sub claim
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名（唯一）
     */
    private String username;

    /**
     * bcrypt 密码哈希，禁止对外返回与写入日志
     */
    @ToString.Exclude
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 角色标识，默认 ADMIN
     */
    private String role;

    /**
     * 账号状态（1-启用，0-禁用）
     */
    private Integer status;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer deleted;
}
