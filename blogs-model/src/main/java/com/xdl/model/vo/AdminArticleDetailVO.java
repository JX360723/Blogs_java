package com.xdl.model.vo;

import com.xdl.model.entity.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端文章详情视图对象
 * <p>
 * 与公开的 {@link ArticleDetailVO} 分开：管理端要 {@code status}（表单回填状态单选），
 * 公开端绝不能有。字段集合刻意与 {@code AdminArticleSummaryVO} 保持超集关系，
 * 前端可以把详情对象直接当列表行用。
 * <p>
 * <b>{@code contentMd} 是必给字段。</b> 管理端列表接口排除了正文大字段，
 * 编辑时若拿列表行回填，正文就是 {@code undefined} → 表单空白 →
 * 用户一点保存，原文被覆盖成空串。这个接口就是为回填而存在的。
 * <p>
 * 不返回 {@code contentHtml}：管理端编辑器只用 Markdown 原文，
 * HTML 由展示端按需渲染，回填用不到。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminArticleDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String summary;

    /**
     * Markdown 正文（编辑回填必需）
     */
    private String contentMd;

    /**
     * 所属分类ID
     */
    private Long categoryId;

    /**
     * 所属分类名称
     */
    private String categoryName;

    /**
     * 封面图URL
     */
    private String coverImage;

    /**
     * 浏览次数（管理端只读展示，不因管理端访问而自增）
     */
    private Integer viewCount;

    /**
     * 是否置顶（0-否，1-是）
     */
    private Integer isTop;

    /**
     * 文章状态（0-草稿，1-已发布，2-已下架）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 由实体转换为管理端详情视图
     *
     * @param article      文章实体（必须含 contentMd）
     * @param categoryName 分类名称，可为 null
     * @return 管理端详情视图
     */
    public static AdminArticleDetailVO from(Article article, String categoryName) {
        return AdminArticleDetailVO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .contentMd(article.getContentMd())
                .categoryId(article.getCategoryId())
                .categoryName(categoryName)
                .coverImage(article.getCoverImage())
                .viewCount(article.getViewCount() == null ? 0 : article.getViewCount())
                .isTop(article.getIsTop() == null ? Article.NOT_TOP : article.getIsTop())
                .status(article.getStatus())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
