package com.xdl.blogscore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.blogscommon.result.ResultCode;
import com.xdl.blogscore.mapper.ArticleMapper;
import com.xdl.blogscore.mapper.CategoryMapper;
import com.xdl.blogscore.service.ArticleService;
import com.xdl.model.dto.ArticleQueryRequest;
import com.xdl.model.entity.Article;
import com.xdl.model.entity.Category;
import com.xdl.model.vo.ArticleDetailVO;
import com.xdl.model.vo.ArticleSummaryVO;
import com.xdl.model.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文章 Service 实现类
 * <p>
 * 本轮实现公开端读取能力（主页面）：列表分页、详情与阅读量自增。
 * 管理端 CRUD 在后续迭代补齐，契约见 docs/API_CONTRACT.md 6.2.3。
 *
 * @author xdl
 */
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    /**
     * 默认页码
     */
    private static final long DEFAULT_PAGE_NUM = 1L;

    /**
     * 默认每页条数，与契约第 3 节一致
     */
    private static final long DEFAULT_PAGE_SIZE = 10L;

    /**
     * 列表项查询列：刻意排除 content_md / content_html / ai_summary，
     * 避免列表接口把整篇正文查出来（单篇几十 KB，10 条就是一页几百 KB）
     */
    private static final String VIEW_COUNT_COLUMN = "view_count";

    private final CategoryMapper categoryMapper;

    @Override
    public PageResult<ArticleSummaryVO> pagePublished(ArticleQueryRequest query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? DEFAULT_PAGE_NUM : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : query.getPageSize();
        Long categoryId = query.getCategoryId() == null || query.getCategoryId() <= 0
                ? null : query.getCategoryId();
        String keyword = normalizeKeyword(query.getKeyword());
        // 关键词里的 % 与 _ 是 LIKE 通配符，不转义的话用户搜「%」会命中全部已发布文章；
        // 这里先转义再交给 MyBatis-Plus 拼 %value%
        String likeKeyword = keyword == null ? null : escapeLikeKeyword(keyword);

        LambdaQueryWrapper<Article> wrapper = Wrappers.lambdaQuery(Article.class)
                .select(Article::getId, Article::getTitle, Article::getSummary, Article::getCategoryId,
                        Article::getCoverImage, Article::getViewCount, Article::getIsTop, Article::getCreatedAt)
                // 公开接口只暴露已发布文章，草稿与下架由管理端接口负责
                .eq(Article::getStatus, Article.STATUS_PUBLISHED)
                .eq(categoryId != null, Article::getCategoryId, categoryId)
                // 关键词对标题或摘要做模糊匹配，两个条件必须整体加括号，
                // 否则 OR 会与 status 条件平级，导致草稿文章被搜出来
                .and(likeKeyword != null, sub -> sub
                        .like(Article::getTitle, likeKeyword)
                        .or()
                        .like(Article::getSummary, likeKeyword))
                // 置顶优先，其次按创建时间倒序（与 BACKEND_DESIGN 4.5 一致）
                .orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getCreatedAt);

        Page<Article> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<Article> records = page.getRecords();

        // 批量取分类名，避免逐条查分类造成 N+1
        Map<Long, String> categoryNames = loadCategoryNames(
                records.stream().map(Article::getCategoryId).toList());
        List<ArticleSummaryVO> list = records.stream()
                .map(article -> ArticleSummaryVO.from(article, categoryNames.get(article.getCategoryId())))
                .toList();
        return PageResult.of(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ArticleDetailVO getPublishedDetail(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文章ID不合法");
        }
        Article article = getById(id);
        // 不存在、草稿、已下架统一返回 404：避免通过状态码差异探测未公开文章是否存在
        if (article == null || !Article.STATUS_PUBLISHED.equals(article.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在或未发布");
        }

        // 阅读量用 SQL 自增而不是「读出来 +1 再写回」：并发访问时后者会丢计数
        lambdaUpdate()
                .eq(Article::getId, id)
                .setSql(VIEW_COUNT_COLUMN + " = " + VIEW_COUNT_COLUMN + " + 1")
                .update();

        int currentViewCount = (article.getViewCount() == null ? 0 : article.getViewCount()) + 1;
        return ArticleDetailVO.from(article, loadCategoryName(article.getCategoryId()), currentViewCount);
    }

    /**
     * 关键词归一化：去首尾空格，空串视为不过滤
     *
     * @param rawKeyword 原始关键词
     * @return 归一化后的关键词，或 null
     */
    private String normalizeKeyword(String rawKeyword) {
        if (!StringUtils.hasText(rawKeyword)) {
            return null;
        }
        String keyword = rawKeyword.trim();
        return keyword.isEmpty() ? null : keyword;
    }

    /**
     * 转义 LIKE 通配符
     * <p>
     * MySQL 的 LIKE 默认以反斜杠作为转义符，因此把 {@code \}、{@code %}、{@code _}
     * 各自加一层反斜杠后，模糊匹配就退化为「按字面包含」。
     * 注意顺序：必须最先处理反斜杠，否则会把后面新加的反斜杠再转义一遍。
     *
     * @param keyword 已 trim 的关键词
     * @return 可安全拼接进 LIKE 的关键词
     */
    private String escapeLikeKeyword(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * 批量查询分类名称
     *
     * @param categoryIds 分类ID集合，允许含 null
     * @return 分类ID -> 分类名称
     */
    private Map<Long, String> loadCategoryNames(Collection<Long> categoryIds) {
        List<Long> ids = categoryIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Category> categories = categoryMapper.selectList(
                Wrappers.lambdaQuery(Category.class).in(Category::getId, ids));
        return categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (first, second) -> first));
    }

    /**
     * 查询单个分类名称
     *
     * @param categoryId 分类ID，可为 null
     * @return 分类名称，分类不存在或未分类时返回 null（前端兜底显示「未分类」）
     */
    private String loadCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }
}
