package com.xdl.blogscore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xdl.blogscore.mapper.AdminUserMapper;
import com.xdl.blogscore.service.AdminUserService;
import com.xdl.model.entity.AdminUser;
import org.springframework.stereotype.Service;

/**
 * 后台管理员 Service 实现类
 *
 * @author xdl
 */
@Service
public class AdminUserServiceImpl extends ServiceImpl<AdminUserMapper, AdminUser> implements AdminUserService {

    @Override
    public AdminUser getByUsername(String username) {
        return lambdaQuery()
                .eq(AdminUser::getUsername, username)
                .one();
    }
}
