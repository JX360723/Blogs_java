package com.xdl.blogsadmin.controller;

import com.xdl.blogscommon.jwt.JwtPrincipal;
import com.xdl.blogscommon.result.Result;
import com.xdl.blogscore.service.ArticleService;
import com.xdl.model.dto.AdminArticleQueryRequest;
import com.xdl.model.dto.ArticleSaveRequest;
import com.xdl.model.vo.AdminArticleDetailVO;
import com.xdl.model.vo.AdminArticleSummaryVO;
import com.xdl.model.vo.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端文章接口
 * <p>
 * 对应 docs/WORKBUDDY_PROMPT_PHASE3_ADMIN_API.md 2.1，路径与前端
 * {@code frontend/src/api/articles.js} 完全一致。
 * <p>
 * 权限：{@code SecurityConfig} 已对 {@code /api/v1/admin/**} 强制 {@code hasRole("ADMIN")}，
 * 未携带有效 token 返回 401、非管理员返回 403，Controller 上不再重复写权限注解。
 * <p>
 * 分层职责：Controller 只做参数接收、登录主体传递与响应包装，业务规则全部在 ArticleService。
 *
 * @author xdl
 */
@RestController
@RequestMapping("/api/v1/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    /**
     * 分页查询文章（含草稿与已下架）
     *
     * @param query 分页与筛选参数（pageNum/pageSize/status，status 为空表示全部）
     * @return 分页结构，列表项不含正文
     */
    @GetMapping
    public Result<PageResult<AdminArticleSummaryVO>> list(@Valid AdminArticleQueryRequest query) {
        return Result.success(articleService.pageForAdmin(query));
    }

    /**
     * 查询文章详情（含草稿与已下架），供编辑回填正文与状态
     * <p>
     * 附加接口：列表为控制响应体积不返回 {@code contentMd}，
     * 前端打开编辑弹窗时应先调本接口取回正文，否则提交时会把正文覆盖成空串。
     * <p>
     * 返回 {@link AdminArticleDetailVO} 而不是公开侧的 {@code ArticleDetailVO}：
     * 公开 VO 刻意不含 {@code status}，编辑表单拿不到真实状态会把「已发布」回填成「草稿」，
     * 用户改个错别字保存，文章就从公开列表消失。
     *
     * @param id 文章ID
     * @return 管理端文章详情，含 contentMd 与 status
     */
    @GetMapping("/{id}")
    public Result<AdminArticleDetailVO> detail(@PathVariable Long id) {
        return Result.success(articleService.getAdminDetail(id));
    }

    /**
     * 创建文章
     * <p>
     * 作者取当前登录管理员，前端不提交 userId；阅读量与时间由服务端生成。
     *
     * @param request   文章内容
     * @param principal 当前登录管理员
     * @return 新文章ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ArticleSaveRequest request,
                               @AuthenticationPrincipal JwtPrincipal principal) {
        return Result.success("创建成功", articleService.createArticle(request, principal.id()));
    }

    /**
     * 更新文章
     * <p>
     * 不接收作者信息：文章作者在创建时确定，编辑不允许转移作者。
     *
     * @param id      文章ID
     * @param request 文章内容
     * @return 空响应体
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ArticleSaveRequest request) {
        articleService.updateArticle(id, request);
        return Result.success("更新成功", null);
    }

    /**
     * 删除文章（逻辑删除，数据保留）
     *
     * @param id 文章ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success("删除成功", null);
    }
}
