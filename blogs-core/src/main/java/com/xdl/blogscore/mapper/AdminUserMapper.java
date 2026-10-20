package com.xdl.blogscore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xdl.model.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台管理员表 Mapper 接口
 *
 * @author xdl
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
