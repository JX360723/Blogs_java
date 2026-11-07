package com.xdl.blogsadmin.unit;

import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.blogscore.mapper.ArticleMapper;
import com.xdl.blogscore.mapper.CategoryMapper;
import com.xdl.blogscore.service.impl.ArticleServiceImpl;
import com.xdl.model.entity.Article;
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

    @InjectMocks
    private ArticleServiceImpl articleService;

    /**
     * 把 Mock 的 Mapper 补进 ServiceImpl 的父类字段，否则 getById 等继承方法抛 baseMapper can not be null
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
}
