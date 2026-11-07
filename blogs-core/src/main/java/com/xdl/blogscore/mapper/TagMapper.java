package com.xdl.blogscore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xdl.model.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签表 Mapper 接口
 * <p>
 * 只用 BaseMapper 提供的 CRUD，没有自定义 SQL：
 * 标签的读写都是单表简单查询，写自定义 XML 反而增加维护成本。
 *
 * @author xdl
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
