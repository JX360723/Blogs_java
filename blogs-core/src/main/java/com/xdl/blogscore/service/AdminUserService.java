package com.xdl.blogscore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xdl.model.entity.AdminUser;

/**
 * 后台管理员 Service 接口
 *
 * @author xdl
 */
public interface AdminUserService extends IService<AdminUser> {

    /**
     * 按用户名查询管理员（自动过滤逻辑删除数据）
     *
     * @param username 登录用户名
     * @return 管理员实体，不存在时返回 null
     */
    AdminUser getByUsername(String username);
}
