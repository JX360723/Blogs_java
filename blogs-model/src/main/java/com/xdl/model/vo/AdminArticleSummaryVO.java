package com.xdl.model.vo;

import com.xdl.model.entity.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端文章列表项视图
 * <p>
 * 与公开端 {@link ArticleSummaryVO} 刻意分成两个类：
 * <ul>
 *   <li><b>必须带 {@code status}</b>：管理页要区分草稿/已发布/已下架。
 *       若给公开 VO 加该字段，等于告诉访客「这篇文章其实没发布」，故公开 VO 保持无 status</li>
 *   <li><b>额外的 {@code updatedAt}</b>：管理页「更新时间」列直接读该字段</li>
 * </ul>
 * 列表<b>不包含</b> {@code contentMd} / {@code contentHtml} / {@code aiSummary}：
 * 正文单篇可达几十 KB，一页 20 条就能拖出 1MB 以上，列表用不到。
 * 需要正文回填时走 {@code GET /api/v1/admin/articles/{id}} 详情接口。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminArticleSummaryVO implements Serializable {

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
     * 所属分类名称，由 Service 批量查询填充（避免 N+1）；分类已删除时为 null
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
     * 是否置顶：0-否，1-是
     */
    private Integer isTop;

    /**
     * 状态：0-草稿，1-已发布，2-已下架
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
     * 由实体转换为管理端列表项
     *
     * @param article      文章实体（列表查询只取了 VO 需要的列）
     * @param categoryName 分类名称，可为 null
     * @return 管理端列表项视图
     */
    public static AdminArticleSummaryVO from(Article article, String categoryName) {
        return AdminArticleSummaryVO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .categoryId(article.getCategoryId())
                .categoryName(categoryName)
                .coverImage(article.getCoverImage())
                // 历史数据可能为 null，统一兜底成 0，避免前端渲染出空单元格
                .viewCount(article.getViewCount() == null ? 0 : article.getViewCount())
                .isTop(article.getIsTop() == null ? Article.NOT_TOP : article.getIsTop())
                .status(article.getStatus())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
