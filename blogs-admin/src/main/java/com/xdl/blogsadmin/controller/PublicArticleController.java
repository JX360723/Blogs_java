package com.xdl.blogsadmin.controller;

import com.xdl.blogscommon.result.Result;
import com.xdl.blogscore.service.ArticleService;
import com.xdl.model.dto.ArticleQueryRequest;
import com.xdl.model.vo.ArticleDetailVO;
import com.xdl.model.vo.ArticleSummaryVO;
import com.xdl.model.vo.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开文章接口（主页面）
 * <p>
 * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.5「公开接口（3 个，主页核心）」：
 * GET /api/v1/articles 与 GET /api/v1/articles/{id}。
 * 路径必须与前端 frontend/src/api/articles.js 完全一致，不允许调整层级。
 * <p>
 * 权限：SecurityConfig 已对 GET /api/v1/articles/** 放行，匿名可访问；
 * Controller 只做参数接收与响应包装，业务规则全部在 ArticleService。
 *
 * @author xdl
 */
@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class PublicArticleController {

    private final ArticleService articleService;

    /**
     * 分页获取已发布文章列表
     *
     * @param query 分页与筛选参数（pageNum/pageSize/categoryId/keyword）
     * @return 分页结构 { list, total, pageNum, pageSize, pages }
     */
    @GetMapping
    public Result<PageResult<ArticleSummaryVO>> list(@Valid ArticleQueryRequest query) {
        return Result.success(articleService.pagePublished(query));
    }

    /**
     * 获取已发布文章详情，同时把浏览次数 +1
     * <p>
     * 草稿/下架/不存在的文章统一返回 404，不泄露资源是否存在。
     *
     * @param id 文章ID
     * @return 文章详情（含 contentMd / contentHtml）
     */
    @GetMapping("/{id}")
    public Result<ArticleDetailVO> detail(@PathVariable Long id) {
        return Result.success(articleService.getPublishedDetail(id));
    }
}
