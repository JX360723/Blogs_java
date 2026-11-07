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
 * 管理端文章详情视图对象
 * <p>
 * 与公开侧 {@link ArticleDetailVO} 拆开的原因：
 * <ul>
 *   <li>管理端必须返回 {@code status}：编辑表单会把整个表单（含 status）原样 PUT 回去，
 *       详情不回填 status 就会取默认值 0，编辑一篇已发布文章会把它静默降级成草稿，
 *       发布状态消失且页面上没有任何报错</li>
 *   <li>管理端需要 {@code tagIds} 回填标签选择器，公开侧不需要也不该暴露</li>
 *   <li>公开接口出现 {@code status} 等于告诉访客「这篇是草稿」，
 *       且容易被猜到 {@code ?status=0} 这类越权用法，故公开 VO 严格不带</li>
 * </ul>
 * 两个 VO 字段同名同义，前端互不影响。
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
     * Markdown 正文
     */
    private String contentMd;

    /**
     * 渲染后的 HTML 正文
     */
    private String contentHtml;

    /**
     * 所属分类ID，null 表示未分类
     */
    private Long categoryId;

    /**
     * 所属分类名称，可能为 null
     */
    private String categoryName;

    /**
     * 封面图URL
     */
    private String coverImage;

    /**
     * 浏览次数（管理端详情不累加，仅展示当前值）
     */
    private Integer viewCount;

    /**
     * 是否置顶：0-否，1-是
     */
    private Integer isTop;

    /**
     * 文章状态：0-草稿，1-已发布，2-已下架
     * <p>
     * 编辑回填的关键字段，详见类注释。
     */
    private Integer status;

    /**
     * 已绑定的标签ID列表
     * <p>
     * <b>永远不为 null</b>：没有标签时返回空数组。
     * 前端 {@code el-select} 的 {@code v-model} 绑到 null 上会抛
     * {@code Cannot read properties of null (reading 'length')}，导致编辑弹窗直接白屏。
     */
    private List<Long> tagIds;

    /**
     * 已绑定的标签详情（id / name / description / createdAt）
     * <p>
     * 供编辑页直接展示标签名，省掉一次「拿 tagIds 再查标签表」的请求；无标签时为空数组。
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
     * 由实体转换为管理端详情视图
     *
     * @param article      文章实体
     * @param categoryName 分类名称，可为 null
     * @param tagIds       已绑定标签ID，调用方保证非 null
     * @param tags         已绑定标签详情，调用方保证非 null
     * @return 管理端详情视图
     */
    public static AdminArticleDetailVO from(Article article, String categoryName,
                                            List<Long> tagIds, List<TagVO> tags) {
        return AdminArticleDetailVO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .contentMd(article.getContentMd())
                .contentHtml(article.getContentHtml())
                .categoryId(article.getCategoryId())
                .categoryName(categoryName)
                .coverImage(article.getCoverImage())
                .viewCount(article.getViewCount() == null ? 0 : article.getViewCount())
                .isTop(article.getIsTop() == null ? Article.NOT_TOP : article.getIsTop())
                .status(article.getStatus() == null ? Article.STATUS_DRAFT : article.getStatus())
                .tagIds(tagIds == null ? List.of() : tagIds)
                .tags(tags == null ? List.of() : tags)
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
