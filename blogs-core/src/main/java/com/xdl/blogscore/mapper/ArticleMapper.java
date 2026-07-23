package com.xdl.blogscore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xdl.model.entity.Article;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章表 Mapper 接口
 *
 * @author xdl
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
}
