package com.xdl.model.vo;

import com.xdl.model.entity.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开文章详情视图对象
 * <p>
 * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.5「详情 data」，含 Markdown 原文与渲染后 HTML。
 * 不返回 {@code publishedAt}（实体无此字段，用 {@code createdAt} 代替）。
 * <p>
 * <b>刻意不含 {@code status}</b>：公开接口只返回已发布文章，
 * 暴露状态字段既无信息量，又会让人误以为 {@code ?status=0} 能拉草稿（安全事故）。
 * 管理端编辑回填需要 status，走独立的 {@link AdminArticleDetailVO}。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDetailVO implements Serializable {

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
     * Markdown 正文
     */
    private String contentMd;

    /**
     * 渲染后的 HTML 正文
     * <p>
     * 当前 MVP 由管理端写入时决定是否填充（内容渲染职责见 BACKEND_DESIGN 第六节待决策项 1），
     * 为空时前端回退渲染 {@code contentMd}，不影响主页面展示。
     */
    private String contentHtml;

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
     * 浏览次数（已包含本次访问的自增）
     */
    private Integer viewCount;

    /**
     * 是否置顶（0-否，1-是）
     */
    private Integer isTop;

    /**
     * 文章绑定的标签（id / name / description / createdAt），无标签时为空数组
     * <p>
     * 访客端详情页展示标签用。列表 VO 刻意不带 tags：列表每页 10~20 条，
     * 逐条查标签就是典型 N+1，而列表页也不展示标签。
     */
    private List<TagVO> tags;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 由实体转换为详情视图
     *
     * @param article      文章实体
     * @param categoryName 分类名称，可为 null
     * @param viewCount    对外展示的浏览次数（通常为自增后的值）
     * @param tags         已绑定标签，调用方保证非 null（无标签传空集合）
     * @return 详情视图
     */
    public static ArticleDetailVO from(Article article, String categoryName,
                                       Integer viewCount, List<TagVO> tags) {
        return ArticleDetailVO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .contentMd(article.getContentMd())
                .contentHtml(article.getContentHtml())
                .categoryId(article.getCategoryId())
                .categoryName(categoryName)
                .coverImage(article.getCoverImage())
                .viewCount(viewCount == null ? 0 : viewCount)
                .isTop(article.getIsTop() == null ? Article.NOT_TOP : article.getIsTop())
                .tags(tags == null ? List.of() : tags)
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
