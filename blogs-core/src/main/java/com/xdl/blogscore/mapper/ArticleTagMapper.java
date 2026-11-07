package com.xdl.blogscore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xdl.model.entity.ArticleTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章标签关联表 Mapper 接口
 * <p>
 * 文章侧与标签侧都要用：文章保存时按 article_id 全量重建绑定，
 * 标签删除时按 tag_id 清理关联行。
 *
 * @author xdl
 */
@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {
}
