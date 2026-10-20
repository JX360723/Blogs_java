package com.xdl.model.vo;

import com.xdl.model.entity.AdminUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员信息视图对象
 * <p>
 * 用于登录响应与 GET /api/v1/auth/me，不包含密码等敏感字段。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 管理员ID
     */
    private Long id;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 角色标识
     */
    private String role;

    /**
     * 由管理员实体转换为对外视图，剔除密码字段
     *
     * @param adminUser 管理员实体
     * @return 对外管理员信息
     */
    public static AdminUserInfo from(AdminUser adminUser) {
        return AdminUserInfo.builder()
                .id(adminUser.getId())
                .username(adminUser.getUsername())
                .nickname(adminUser.getNickname())
                .avatar(adminUser.getAvatar())
                .role(adminUser.getRole())
                .build();
    }
}
