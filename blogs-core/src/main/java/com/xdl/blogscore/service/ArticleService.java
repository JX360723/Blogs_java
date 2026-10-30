package com.xdl.blogscore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xdl.model.dto.ArticleQueryRequest;
import com.xdl.model.entity.Article;
import com.xdl.model.vo.ArticleDetailVO;
import com.xdl.model.vo.ArticleSummaryVO;
import com.xdl.model.vo.PageResult;

/**
 * 文章 Service 接口
 *
 * @author xdl
 */
public interface ArticleService extends IService<Article> {

    /**
     * 公开文章分页列表：只返回已发布（status=1），按置顶 + 创建时间倒序
     * <p>
     * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.5「GET /api/v1/articles」。
     * 草稿（0）与已下架（2）一律不出现在结果中。
     *
     * @param query 分页与筛选条件
     * @return 前端约定的分页结构，列表项不含正文
     */
    PageResult<ArticleSummaryVO> pagePublished(ArticleQueryRequest query);

    /**
     * 公开文章详情：只有已发布文章可访问，并把浏览次数 +1
     * <p>
     * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.5「GET /api/v1/articles/{id}」。
     * 草稿/下架/不存在一律抛 404「文章不存在或未发布」，不泄露资源是否存在。
     *
     * @param id 文章ID
     * @return 文章详情（含正文）
     */
    ArticleDetailVO getPublishedDetail(Long id);
}
