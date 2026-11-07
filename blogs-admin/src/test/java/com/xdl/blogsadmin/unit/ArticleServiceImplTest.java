package com.xdl.blogsadmin.unit;

import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.blogscore.mapper.ArticleMapper;
import com.xdl.blogscore.mapper.ArticleTagMapper;
import com.xdl.blogscore.mapper.CategoryMapper;
import com.xdl.blogscore.mapper.TagMapper;
import com.xdl.blogscore.service.impl.ArticleServiceImpl;
import com.xdl.model.entity.Article;
import com.xdl.model.vo.AdminArticleDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 公开文章接口的草稿隔离测试
 * <p>
 * 核心安全断言：草稿（status=0）、已下架（status=2）、不存在三种情况
 * 必须返回**完全一致**的 404，不能通过状态码或文案差异探测未公开文章是否存在。
 *
 * @author xdl
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("公开文章-草稿隔离")
class ArticleServiceImplTest extends ServiceTestSupport {

    private static final Long ARTICLE_ID = 100L;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private CategoryMapper categoryMapper;

    /**
     * 标签相关的两张表：文章详情会查绑定关系，构造器注入缺了它们会拿到 null
     */
    @Mock
    private ArticleTagMapper articleTagMapper;

    @Mock
    private TagMapper tagMapper;

    @InjectMocks
    private ArticleServiceImpl articleService;

    /**
    /**
     * 把 Mock 的 Mapper 补进 ServiceImpl 的父类字段，否则 getById 等继承方法抛 baseMapper can not be null
     * <p>
     * 实体元数据（TableInfo）由 {@link ServiceTestSupport#initTableInfoCache()} 在 {@code @BeforeAll}
     * 统一注册，本类涉及的 Article / Category / Tag / ArticleTag 都已覆盖。
     */
    @BeforeEach
    void setUpBaseMapper() {
        injectBaseMapper(articleService, articleMapper);
    }

    private static Article article(Integer status) {
        return Article.builder()
                .id(ARTICLE_ID)
                .title("测试文章")
                .summary("摘要")
                .contentMd("# 正文")
                .status(status)
                .viewCount(0)
                .isTop(Article.NOT_TOP)
                .build();
    }

    @Test
    @DisplayName("文章不存在：404")
    void detailNotFound() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> articleService.getPublishedDetail(ARTICLE_ID));

        assertEquals(404, exception.getCode());
        assertEquals("文章不存在或未发布", exception.getMessage());
    }

    @Test
    @DisplayName("草稿文章：404，文案与「不存在」完全一致")
    void detailDraftAsNotFound() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_DRAFT));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> articleService.getPublishedDetail(ARTICLE_ID));

        assertEquals(404, exception.getCode());
        assertEquals("文章不存在或未发布", exception.getMessage());
    }

    @Test
    @DisplayName("已下架文章：404，文案与「不存在」完全一致")
    void detailOfflineAsNotFound() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_OFFLINE));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> articleService.getPublishedDetail(ARTICLE_ID));

        assertEquals(404, exception.getCode());
        assertEquals("文章不存在或未发布", exception.getMessage());
    }

    @Test
    @DisplayName("非法 ID：400，且不查库")
    void detailInvalidId() {
        BusinessException nullId = assertThrows(BusinessException.class,
                () -> articleService.getPublishedDetail(null));
        assertEquals(400, nullId.getCode());

        BusinessException zeroId = assertThrows(BusinessException.class,
                () -> articleService.getPublishedDetail(0L));
        assertEquals(400, zeroId.getCode());
    }

    @Test
    @DisplayName("管理端详情：必须回填 status，否则编辑已发布文章会被降级成草稿")
    void adminDetailKeepsStatusAndContent() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_PUBLISHED));

        AdminArticleDetailVO detail = articleService.getAdminDetail(ARTICLE_ID);
        assertEquals(Article.STATUS_PUBLISHED, detail.getStatus());
        assertEquals("# 正文", detail.getContentMd());
        // 管理端详情不累加阅读量，避免预览污染访客统计
        assertEquals(0, detail.getViewCount());
    }
}
