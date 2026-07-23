package com.xdl.blogscore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xdl.model.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类表 Mapper 接口
 *
 * @author xdl
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
