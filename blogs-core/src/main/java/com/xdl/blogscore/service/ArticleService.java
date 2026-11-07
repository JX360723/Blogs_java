package com.xdl.blogscore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xdl.model.dto.AdminArticleQueryRequest;
import com.xdl.model.dto.ArticleQueryRequest;
import com.xdl.model.dto.ArticleSaveRequest;
import com.xdl.model.entity.Article;
import com.xdl.model.vo.AdminArticleDetailVO;
import com.xdl.model.vo.AdminArticleSummaryVO;
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

    /**
     * 管理端文章分页列表：包含草稿与已下架，可按状态/分类/关键词筛选
     * <p>
     * 对应 docs/WORKBUDDY_PROMPT_PHASE3_ADMIN_API.md 2.1「GET /api/v1/admin/articles」。
     * 与公开列表的关键差异：不限制 status、返回 status 与 updatedAt；
     * 但同样<b>不返回正文</b>（列表只取展示所需列）。
     *
     * @param query 分页与筛选条件，status 为 null 表示不过滤
     * @return 前端约定的分页结构
     */
    PageResult<AdminArticleSummaryVO> pageForAdmin(AdminArticleQueryRequest query);

    /**
     * 管理端文章详情：草稿与已下架同样可查看，供编辑回填正文、状态与标签
     * <p>
     * 附带接口：施工单的 8 个接口未包含详情，但管理页「编辑」需要正文。
     * 列表为控制响应体积不返回 contentMd，编辑前应调用本接口取回正文，
     * 否则提交时会用空串覆盖原文。
     * <p>
     * 必须返回 {@link AdminArticleDetailVO}（含 {@code status}）而不能退回公开侧
     * {@code ArticleDetailVO}：编辑表单会把整个表单（含 status）原样 PUT 回去，
     * 少一个 status 就会把已发布文章静默降级成草稿，用户改个错别字保存，
     * 文章就从公开列表消失。
     * {@code tagIds} 恒为非 null 数组，避免前端 el-select 绑 null 报错。
     *
     * @param id 文章ID
     * @return 管理端文章详情（含 contentMd / contentHtml / status / tagIds / tags）
     */
    AdminArticleDetailVO getAdminDetail(Long id);

    /**
     * 创建文章
     * <p>
     * 服务端职责：userId 取当前登录管理员、contentHtml 由 contentMd 渲染、
     * viewCount 强制 0、isTop 缺省 0、createdAt/updatedAt 自动填充。
     *
     * @param request 文章内容
     * @param adminId 当前登录管理员ID
     * @return 新文章ID
     */
    Long createArticle(ArticleSaveRequest request, Long adminId);

    /**
     * 更新文章
     * <p>
     * 只更新可编辑字段，不允许改动作者、创建时间与浏览次数；
     * 正文有变更时同步重新渲染 contentHtml。
     *
     * @param id      文章ID
     * @param request 文章内容
     */
    void updateArticle(Long id, ArticleSaveRequest request);

    /**
     * 逻辑删除文章（deleted=1，数据保留）
     *
     * @param id 文章ID
     */
    void deleteArticle(Long id);
}
