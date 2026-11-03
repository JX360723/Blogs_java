package com.xdl.blogsmodel;

import com.xdl.model.entity.Article;
import com.xdl.model.vo.ArticleSummaryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 实体常量与视图兜底测试
 * <p>
 * 原为空壳 contextLoads，模型模块没有可启动的上下文。
 * 两件事必须锁死：
 * 1. 文章状态取值——这几个数字同时出现在数据库注释、前端下拉选项和后端查询条件里，
 *    任何一处改动都可能让草稿被公开；
 * 2. 列表 VO 的 null 兜底——实体里 viewCount / isTop 允许为 null（由数据库 DEFAULT 0 兜底），
 *    若 VO 不做兜底，前端表格会显示 null。
 *
 * @author xdl
 */
@DisplayName("实体常量与视图兜底")
class BlogsModelApplicationTests {

    @Test
    @DisplayName("文章状态：草稿 0 / 已发布 1 / 已下架 2，三者互不相等")
    void articleStatusConstants() {
        assertEquals(0, Article.STATUS_DRAFT);
        assertEquals(1, Article.STATUS_PUBLISHED);
        assertEquals(2, Article.STATUS_OFFLINE);
        assertNotEquals(Article.STATUS_DRAFT, Article.STATUS_PUBLISHED);
        assertNotEquals(Article.STATUS_PUBLISHED, Article.STATUS_OFFLINE);
    }

    @Test
    @DisplayName("置顶常量：NOT_TOP = 0，与「未置顶」的数据库默认值一致")
    void articleTopConstant() {
        assertEquals(0, Article.NOT_TOP);
    }

    @Test
    @DisplayName("列表 VO 兜底：实体 viewCount / isTop 为 null 时输出 0，前端表格不出现 null")
    void summaryVoFallback() {
        Article article = Article.builder().id(1L).title("标题").build();

        ArticleSummaryVO vo = ArticleSummaryVO.from(article, null);

        assertEquals(0, vo.getViewCount(), "viewCount 为 null 时必须兜底为 0");
        assertEquals(Article.NOT_TOP, vo.getIsTop(), "isTop 为 null 时必须兜底为 0（未置顶）");
        assertNull(vo.getCategoryName(), "无分类时 categoryName 保持 null，由前端兜底显示「未分类」");
    }
}
