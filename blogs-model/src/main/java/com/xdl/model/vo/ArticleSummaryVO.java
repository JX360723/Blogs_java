package com.xdl.model.vo;

import com.xdl.model.entity.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章列表项视图对象
 * <p>
 * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.5「列表项 data.list[] 元素」。
 * 刻意<b>不包含</b> {@code contentMd} / {@code contentHtml}（正文动辄几十 KB，列表页用不到），
 * 也不返回 {@code publishedAt} 与 {@code tagIds}（实体无该字段，标签属阶段 3）。
 * <p>
 * {@code categoryName} 为前端列表展示所需（ArticleList.vue 用 a.categoryName || '未分类'），
 * 由 Service 层批量查分类后填充，避免逐条查询造成 N+1。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSummaryVO implements Serializable {

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
     * 所属分类ID
     */
    private Long categoryId;

    /**
     * 所属分类名称，分类不存在时为 null，由前端兜底显示「未分类」
     */
    private String categoryName;

    /**
     * 封面图URL
     */
    private String coverImage;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 是否置顶（0-否，1-是）
     */
    private Integer isTop;

    /**
     * 创建时间，序列化为 yyyy-MM-dd HH:mm:ss
     */
    private LocalDateTime createdAt;

    /**
     * 由实体转换为列表项视图
     *
     * @param article      文章实体，需已排除正文大字段（可只 select 列表所需列）
     * @param categoryName 分类名称，可为 null
     * @return 列表项视图
     */
    public static ArticleSummaryVO from(Article article, String categoryName) {
        return ArticleSummaryVO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .categoryId(article.getCategoryId())
                .categoryName(categoryName)
                .coverImage(article.getCoverImage())
                .viewCount(article.getViewCount() == null ? 0 : article.getViewCount())
                .isTop(article.getIsTop() == null ? Article.NOT_TOP : article.getIsTop())
                .createdAt(article.getCreatedAt())
                .build();
    }
}
