package com.xdl.blogsadmin.unit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.blogscore.mapper.ArticleMapper;
import com.xdl.blogscore.mapper.CategoryMapper;
import com.xdl.blogscore.service.impl.ArticleServiceImpl;
import com.xdl.blogscore.support.MarkdownRenderer;
import com.xdl.model.dto.AdminArticleQueryRequest;
import com.xdl.model.dto.ArticleSaveRequest;
import com.xdl.model.entity.Article;
import com.xdl.model.vo.AdminArticleDetailVO;
import com.xdl.model.vo.AdminArticleSummaryVO;
import com.xdl.model.vo.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端文章接口测试
 * <p>
 * 锁死两条「静默丢数据」回归线：
 * <ol>
 *     <li><b>状态回填</b>：详情接口必须带 {@code status}。公开 VO 不含该字段，
 *     若管理端详情复用它，编辑表单会把「已发布」回填成「草稿」，用户改个错别字
 *     保存后文章就从公开列表消失——页面上没有任何报错。</li>
 *     <li><b>正文回填</b>：详情接口必须带 {@code contentMd}。列表接口为控制体积
 *     排除了正文，若编辑拿列表行回填，正文框是空的，保存即清空原文。</li>
 * </ol>
 * 另外守住：阅读量不被管理端访问或编辑污染、删除走逻辑删除、作者与创建时间不可改。
 *
 * @author xdl
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("管理端文章-状态与正文回填")
class AdminArticleServiceImplTest extends ServiceTestSupport {

    private static final Long ARTICLE_ID = 100L;
    private static final Long ADMIN_ID = 7L;
    private static final Long CATEGORY_ID = 3L;
    private static final String CONTENT_MD = "# 标题\n\n正文内容";
    private static final String RENDERED_HTML = "<h1>标题</h1><p>正文内容</p>";

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private MarkdownRenderer markdownRenderer;

    @InjectMocks
    private ArticleServiceImpl articleService;

    /**
     * 把 Mock 的 Mapper 补进 ServiceImpl 的父类字段，否则继承方法一调就抛 baseMapper can not be null
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
                .contentMd(CONTENT_MD)
                .contentHtml(RENDERED_HTML)
                .categoryId(CATEGORY_ID)
                .userId(ADMIN_ID)
                .status(status)
                .viewCount(42)
                .isTop(Article.NOT_TOP)
                .build();
    }

    private static ArticleSaveRequest saveRequest(Integer status) {
        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle("测试文章");
        request.setSummary("摘要");
        request.setContentMd(CONTENT_MD);
        request.setCategoryId(CATEGORY_ID);
        request.setStatus(status);
        request.setIsTop(Article.NOT_TOP);
        return request;
    }

    // ---------------------------------------------------------------- 详情回填

    @Test
    @DisplayName("详情必须带 status：已发布文章回填后仍是「已发布」，不会被改回草稿")
    void adminDetailMustCarryStatus() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_PUBLISHED));

        AdminArticleDetailVO vo = articleService.getAdminDetail(ARTICLE_ID);

        assertEquals(Article.STATUS_PUBLISHED, vo.getStatus(),
                "管理端详情没有 status，编辑表单会回填成草稿，保存即把已发布文章下架");
    }

    @Test
    @DisplayName("详情必须带 contentMd：编辑回填靠它，缺了会把正文清空")
    void adminDetailMustCarryContentMd() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_DRAFT));

        AdminArticleDetailVO vo = articleService.getAdminDetail(ARTICLE_ID);

        assertEquals(CONTENT_MD, vo.getContentMd());
        assertEquals("测试文章", vo.getTitle());
    }

    @Test
    @DisplayName("管理端查看详情不自增阅读量：预览不能污染访客侧统计")
    void adminDetailMustNotIncreaseViewCount() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_PUBLISHED));

        AdminArticleDetailVO vo = articleService.getAdminDetail(ARTICLE_ID);

        assertEquals(42, vo.getViewCount());
        verify(articleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("详情查不到：404")
    void adminDetailNotFound() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.getAdminDetail(ARTICLE_ID));
        assertEquals(404, ex.getCode());
    }

    // ---------------------------------------------------------------- 修改与发布

    @Test
    @DisplayName("修改并发布：status 从草稿改为已发布")
    void updateCanPublishDraft() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_DRAFT));
        when(articleMapper.update(any(Article.class), any())).thenReturn(1);
        when(markdownRenderer.render(anyString())).thenReturn(RENDERED_HTML);

        articleService.updateArticle(ARTICLE_ID, saveRequest(Article.STATUS_PUBLISHED));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).update(captor.capture(), any());
        assertEquals(Article.STATUS_PUBLISHED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("修改不得覆盖阅读量、作者与创建时间")
    void updateMustNotTouchViewCountAuthorOrCreatedAt() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_PUBLISHED));
        when(articleMapper.update(any(Article.class), any())).thenReturn(1);
        when(markdownRenderer.render(anyString())).thenReturn(RENDERED_HTML);

        articleService.updateArticle(ARTICLE_ID, saveRequest(Article.STATUS_PUBLISHED));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).update(captor.capture(), any());
        Article sent = captor.getValue();
        assertNull(sent.getViewCount(), "update 实体不应携带 viewCount，否则编辑一次文章阅读量就被重置");
        assertNull(sent.getUserId(), "update 实体不应携带 userId，否则文章作者会被改成最后编辑人");
        assertNull(sent.getCreatedAt(), "update 实体不应携带 createdAt，否则创建时间被刷新");
    }

    @Test
    @DisplayName("正文变更时同步重渲染 contentHtml，两者不能长期不一致")
    void updateRerendersContentHtml() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_PUBLISHED));
        when(articleMapper.update(any(Article.class), any())).thenReturn(1);
        when(markdownRenderer.render(CONTENT_MD)).thenReturn(RENDERED_HTML);

        articleService.updateArticle(ARTICLE_ID, saveRequest(Article.STATUS_PUBLISHED));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).update(captor.capture(), any());
        assertEquals(CONTENT_MD, captor.getValue().getContentMd());
        assertEquals(RENDERED_HTML, captor.getValue().getContentHtml());
    }

    @Test
    @DisplayName("修改不存在的文章：404，且不执行更新")
    void updateNotFound() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.updateArticle(ARTICLE_ID, saveRequest(Article.STATUS_PUBLISHED)));
        assertEquals(404, ex.getCode());
        verify(articleMapper, never()).update(any(), any());
    }

    // ---------------------------------------------------------------- 删除

    @Test
    @DisplayName("删除走逻辑删除，数据保留可追溯")
    void deleteUsesLogicalDelete() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article(Article.STATUS_PUBLISHED));
        when(articleMapper.deleteById((Serializable) ARTICLE_ID)).thenReturn(1);

        articleService.deleteArticle(ARTICLE_ID);

        verify(articleMapper).deleteById((Serializable) ARTICLE_ID);
    }

    @Test
    @DisplayName("删除不存在的文章：404")
    void deleteNotFound() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.deleteArticle(ARTICLE_ID));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("非法 ID：400，且不查库")
    void invalidIdRejectedWithoutQuery() {
        assertThrows(BusinessException.class, () -> articleService.getAdminDetail(0L));
        assertThrows(BusinessException.class, () -> articleService.deleteArticle(null));
        verify(articleMapper, never()).selectById(any());
    }

    // ---------------------------------------------------------------- 新建

    @Test
    @DisplayName("新建：阅读量强制归零、作者取登录人，不信任前端传值")
    void createForcesViewCountZeroAndUsesLoginAdmin() {
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(new com.xdl.model.entity.Category());
        when(markdownRenderer.render(anyString())).thenReturn(RENDERED_HTML);
        when(articleMapper.insert(any(Article.class))).thenAnswer(invocation -> {
            Article toInsert = invocation.getArgument(0);
            toInsert.setId(ARTICLE_ID);
            return 1;
        });

        Long createdId = articleService.createArticle(saveRequest(Article.STATUS_DRAFT), ADMIN_ID);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).insert(captor.capture());
        Article saved = captor.getValue();
        assertEquals(0, saved.getViewCount(), "新建必须把阅读量写死为 0，防止前端伪造");
        assertEquals(ADMIN_ID, saved.getUserId(), "作者必须取当前登录管理员");
        assertEquals(ARTICLE_ID, createdId);
    }

    // ---------------------------------------------------------------- 列表

    @Test
    @DisplayName("列表返回前端约定的分页结构，且带 status")
    void pageForAdminReturnsFrontendShape() {
        // loadCategoryNames 会批量查分类，不 stub 会返回 null，.stream() 直接 NPE
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        when(articleMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Article> page = invocation.getArgument(0);
            page.setRecords(List.of(article(Article.STATUS_DRAFT)));
            page.setTotal(1);
            return page;
        });

        AdminArticleQueryRequest query = new AdminArticleQueryRequest();
        query.setPageNum(1L);
        query.setPageSize(10L);
        query.setStatus(Article.STATUS_DRAFT);

        PageResult<AdminArticleSummaryVO> result = articleService.pageForAdmin(query);

        assertNotNull(result.getList());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
        assertEquals(1, result.getPages());
        assertEquals(Article.STATUS_DRAFT, result.getList().get(0).getStatus(),
                "管理端列表必须带 status，否则无法区分草稿与已发布");
    }
}
