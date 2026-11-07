package com.xdl.blogscore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.blogscommon.result.ResultCode;
import com.xdl.blogscore.mapper.ArticleMapper;
import com.xdl.blogscore.mapper.ArticleTagMapper;
import com.xdl.blogscore.mapper.CategoryMapper;
import com.xdl.blogscore.mapper.TagMapper;
import com.xdl.blogscore.service.ArticleService;
import com.xdl.blogscore.support.MarkdownRenderer;
import com.xdl.model.dto.AdminArticleQueryRequest;
import com.xdl.model.dto.ArticleQueryRequest;
import com.xdl.model.dto.ArticleSaveRequest;
import com.xdl.model.entity.Article;
import com.xdl.model.entity.ArticleTag;
import com.xdl.model.entity.Category;
import com.xdl.model.entity.Tag;
import com.xdl.model.vo.AdminArticleDetailVO;
import com.xdl.model.vo.AdminArticleSummaryVO;
import com.xdl.model.vo.ArticleDetailVO;
import com.xdl.model.vo.ArticleSummaryVO;
import com.xdl.model.vo.PageResult;
import com.xdl.model.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文章 Service 实现类
 * <p>
 * 覆盖公开端读取（列表分页、详情与阅读量自增）与管理端 CRUD（含草稿/下架）。
 * 契约见 docs/API_CONTRACT.md 6.1.1 / 6.1.2 / 6.2.3。
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
     * 阅读量列名，用于 SQL 层原子自增
     */
    private static final String VIEW_COUNT_COLUMN = "view_count";

    /**
     * 单篇文章最多绑定的标签数
     * <p>
     * 不设上限的话，一次请求可以塞进几千个 tagId，同步时要生成同样多的 INSERT，
     * 既能被用来打满数据库，也让详情接口的 tags 数组变成不可控大小。
     */
    private static final int MAX_TAGS_PER_ARTICLE = 10;

    private final CategoryMapper categoryMapper;

    private final MarkdownRenderer markdownRenderer;

    /**
     * 文章与标签的关联表：文章侧保存时要按 article_id 全量重建绑定
     */
    private final ArticleTagMapper articleTagMapper;

    /**
     * 标签表：用于校验 tagId 是否真实存在、以及回填标签详情
     */
    private final TagMapper tagMapper;

    @Override
    public PageResult<ArticleSummaryVO> pagePublished(ArticleQueryRequest query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? DEFAULT_PAGE_NUM : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : query.getPageSize();
        Long categoryId = query.getCategoryId() == null || query.getCategoryId() <= 0
                ? null : query.getCategoryId();
        Long tagId = query.getTagId() == null || query.getTagId() <= 0 ? null : query.getTagId();
        // 标签筛选：先取该标签下的文章ID，再把 ids 交给主查询做 IN 过滤。
        // 这样能把「标签下没有文章」与「标签下文章都没发布」两种情况分得很清楚，
        // 排查时只看一条 SQL 日志也能定位问题出在关联表还是 article 表。
        List<Long> taggedArticleIds = tagId == null ? null : loadArticleIdsByTagId(tagId);
        if (taggedArticleIds != null && taggedArticleIds.isEmpty()) {
            // 关键：绝不能把空集合直接交给 .in(Article::getId, emptyList)，
            // MyBatis-Plus 会拼出 `IN ()`，MySQL 语法报错，接口直接 500。
            // 「标签下没有文章」本来就是合法的空结果，这里直接返回空页。
            return PageResult.of(Collections.emptyList(), 0, pageNum, pageSize);
        }
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
                // tagId 只是筛选条件，status=1 的强制条件不受影响：
                // 即便 tagId 命中的全是草稿，也只会得到空列表，不会把草稿带出来
                .in(taggedArticleIds != null, Article::getId, taggedArticleIds)
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
        return ArticleDetailVO.from(article, loadCategoryName(article.getCategoryId()),
                currentViewCount, loadTags(id));
    }

    // ==================== 管理端（需 JWT + ROLE_ADMIN，鉴权由 SecurityConfig 强制） ====================

    @Override
    public PageResult<AdminArticleSummaryVO> pageForAdmin(AdminArticleQueryRequest query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? DEFAULT_PAGE_NUM : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : query.getPageSize();
        Long categoryId = query.getCategoryId() == null || query.getCategoryId() <= 0
                ? null : query.getCategoryId();
        String keyword = normalizeKeyword(query.getKeyword());
        String likeKeyword = keyword == null ? null : escapeLikeKeyword(keyword);

        LambdaQueryWrapper<Article> wrapper = Wrappers.lambdaQuery(Article.class)
                // 显式指定列：排除 content_md / content_html / ai_summary 三个大字段。
                // 正文单篇可达几十 KB，列表一页 20 条就是 1MB+，而列表根本不展示正文。
                // 需要正文时走 GET /admin/articles/{id} 详情接口取单篇。
                .select(Article::getId, Article::getTitle, Article::getSummary,
                        Article::getCategoryId, Article::getCoverImage, Article::getStatus,
                        Article::getViewCount, Article::getIsTop,
                        Article::getCreatedAt, Article::getUpdatedAt)
                // 管理端不加 status 条件即为「全部状态」，草稿与已下架都必须在列表可见
                .eq(query.getStatus() != null, Article::getStatus, query.getStatus())
                .eq(categoryId != null, Article::getCategoryId, categoryId)
                .and(likeKeyword != null, sub -> sub
                        .like(Article::getTitle, likeKeyword)
                        .or()
                        .like(Article::getSummary, likeKeyword))
                // 与公开接口一致的排序：置顶优先，其次按创建时间倒序；
                // 末尾补 id 倒序做兜底，避免 created_at 相同的记录在翻页时错位重复
                .orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getCreatedAt)
                .orderByDesc(Article::getId);

        Page<Article> page = page(new Page<>(pageNum, pageSize), wrapper);
        // 批量取分类名，避免逐条查分类造成 N+1
        Map<Long, String> categoryNames = loadCategoryNames(
                page.getRecords().stream().map(Article::getCategoryId).toList());
        List<AdminArticleSummaryVO> list = page.getRecords().stream()
                .map(article -> AdminArticleSummaryVO.from(article, categoryNames.get(article.getCategoryId())))
                .toList();
        return PageResult.of(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AdminArticleDetailVO getAdminDetail(Long id) {
        Article article = requireArticle(id);
        // 详情不改阅读量：管理端预览不应污染访客侧统计。
        // 用管理端专用 VO：公开 ArticleDetailVO 不含 status，
        // 编辑表单拿不到真实状态会把「已发布」回填成「草稿」，保存即下架。
        // tagIds 必须给空数组而不是 null：前端 el-select 的 v-model 绑到 null 上会直接抛
        // Cannot read properties of null，编辑弹窗白屏。
        return AdminArticleDetailVO.from(article, loadCategoryName(article.getCategoryId()),
                loadTagIds(id), loadTags(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArticle(ArticleSaveRequest request, Long adminId) {
        Long categoryId = normalizeCategoryId(request.getCategoryId());
        validateCategoryExists(categoryId);
        String contentMd = trimToNull(request.getContentMd());

        Article article = Article.builder()
                .title(request.getTitle().trim())
                .summary(trimToNull(request.getSummary()))
                .contentMd(contentMd)
                // contentHtml 一律由服务端渲染，不接收前端提交值，保证正文与渲染结果永远同源
                .contentHtml(markdownRenderer.render(contentMd))
                .categoryId(categoryId)
                .coverImage(trimToNull(request.getCoverImage()))
                // 作者取当前登录管理员，不接受前端传值
                .userId(adminId)
                .status(request.getStatus())
                // 阅读量/置顶由服务端兜底，不信任前端传值（防止伪造阅读量）
                .viewCount(0)
                .isTop(request.getIsTop() == null ? Article.NOT_TOP : request.getIsTop())
                .build();
        // createdAt / updatedAt 由 MybatisMetaObjectHandler 自动填充，这里不手动 set
        save(article);
        // 文章与标签分两张表，写标签失败必须让文章一起回滚，否则会出现「文章存了、标签丢了」的半成品
        syncArticleTags(article.getId(), request.getTagIds());
        return article.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticle(Long id, ArticleSaveRequest request) {
        requireArticle(id);
        // 施工单明确「分类不存在时不要报错」：分类可能在编辑期间被删除，
        // 此时按「未分类」保存即可，不阻断编辑（个人博客宽松优先）。
        // 创建时仍校验（见 createArticle），避免一开始就写入脏引用。

        Article update = new Article();
        update.setId(id);
        update.setTitle(request.getTitle().trim());
        // 可清空字段用「空串即清空」的语义：前端把摘要/封面删空后提交的是 ""，
        // 若归一成 null 会被 MyBatis-Plus 的 NOT_NULL 策略忽略，用户会看到「清空保存后旧值还在」
        update.setSummary(normalizeForUpdate(request.getSummary()));
        update.setCoverImage(normalizeForUpdate(request.getCoverImage()));
        update.setStatus(request.getStatus());
        update.setIsTop(request.getIsTop());

        if (request.getContentMd() != null) {
            String contentMd = normalizeForUpdate(request.getContentMd());
            update.setContentMd(contentMd);
            // 正文变更时同步重渲染，避免 contentHtml 与 contentMd 长期不一致
            update.setContentHtml(markdownRenderer.render(contentMd));
        }

        // 刻意不 set userId / createdAt / viewCount：
        // 作者与创建时间不允许被更新接口改写，阅读量只能由公开详情接口自增
        //
        // 用 update(entity, wrapper) 而不是 updateById(entity)：
        // updateById 走 MyBatis-Plus 的 NOT_NULL 策略，null 字段会被整条忽略，
        // 于是「把分类清空」这种合法操作（前端 el-select clearable 传 null）写不进库，
        // 文章会继续挂在一个用户以为已经移走的分类上。这里用 wrapper 显式 set，
        // null 也能落库；同时保留实体参数，好让 MybatisMetaObjectHandler 刷新 updated_at。
        update(update, Wrappers.lambdaUpdate(Article.class)
                .eq(Article::getId, id)
                .set(Article::getCategoryId, normalizeCategoryId(request.getCategoryId())));

        // 标签同理由本方法收敛：tagIds 为 null 时不改动绑定，为 [] 时清空，为非空时全量覆盖
        syncArticleTags(id, request.getTagIds());
    }

    @Override
    public void deleteArticle(Long id) {
        requireArticle(id);
        // @TableLogic 已全局配置，removeById 实际执行 UPDATE article SET deleted = 1，数据保留可追溯
        removeById(id);
    }

    /**
     * 查询文章，不存在时抛 404
     *
     * @param id 文章ID
     * @return 文章实体
     */
    private Article requireArticle(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文章ID不合法");
        }
        Article article = getById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    /**
     * 分类ID归一化
     * <p>
     * 前端未选分类时可能提交 {@code null}、空串或 {@code 0}，
     * 统一收敛成 {@code null}（未分类），避免把 0 当成真实分类ID 去查库或落库。
     *
     * @param categoryId 原始分类ID
     * @return 合法分类ID，或 null 表示未分类
     */
    private Long normalizeCategoryId(Long categoryId) {
        return categoryId == null || categoryId <= 0 ? null : categoryId;
    }

    /**
     * 校验文章引用的分类是否存在
     * <p>
     * 分类可能已被逻辑删除。若放任写入，文章会挂在一个查不到的 categoryId 上，
     * 管理页与访客页都只能显示「未分类」，等于静默丢数据，因此直接拒绝。
     *
     * @param categoryId 分类ID，允许为 null（未分类）
     */
    private void validateCategoryExists(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (categoryId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类ID不合法");
        }
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选分类不存在");
        }
    }

    /**
     * 关键词归一化：去首尾空格，空串视为不过滤
     *
     * @param rawKeyword 原始关键词
     * @return 归一化后的关键词，或 null
     */
    private String normalizeKeyword(String rawKeyword) {
        return trimToNull(rawKeyword);
    }

    /**
     * 去除首尾空白，空串按 null 处理（写入前归一，避免库里出现空字符串）
     *
     * @param value 原始文本
     * @return 归一后的文本或 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 更新场景的文本归一：只去首尾空白，保留空串
     * <p>
     * 与 {@link #trimToNull} 的区别在于「空串不清成 null」。更新时 null 会被
     * MyBatis-Plus 的 NOT_NULL 策略忽略，若把用户主动清空的字段归一成 null，
     * 就无法把已有值清掉。
     *
     * @param value 原始文本
     * @return 去空白后的文本；入参为 null 时返回 null 表示「该字段不更新」
     */
    private String normalizeForUpdate(String value) {
        return value == null ? null : value.trim();
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
     * 查询某个标签下的全部文章ID
     * <p>
     * 只取 article_id 一列：调用方只需要 ID 做 IN 过滤，把整行取回来纯属浪费带宽。
     *
     * @param tagId 标签ID
     * @return 文章ID列表，标签下没有文章时返回空集合（调用方必须先判空再拼 IN）
     */
    private List<Long> loadArticleIdsByTagId(Long tagId) {
        List<ArticleTag> links = articleTagMapper.selectList(
                Wrappers.lambdaQuery(ArticleTag.class)
                        .select(ArticleTag::getArticleId)
                        .eq(ArticleTag::getTagId, tagId));
        return links.stream()
                .map(ArticleTag::getArticleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 查询文章已绑定的标签ID
     * <p>
     * 恒返回集合（无标签时空集合，绝不返回 null）：管理端详情直接把结果塞进 response，
     * 前端 el-select 的 v-model 一旦拿到 null 就会抛异常。
     *
     * @param articleId 文章ID
     * @return 标签ID列表，按绑定先后排序
     */
    private List<Long> loadTagIds(Long articleId) {
        List<ArticleTag> links = articleTagMapper.selectList(
                Wrappers.lambdaQuery(ArticleTag.class)
                        .select(ArticleTag::getTagId)
                        .eq(ArticleTag::getArticleId, articleId)
                        .orderByAsc(ArticleTag::getId));
        return links.stream()
                .map(ArticleTag::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 查询文章已绑定的标签详情
     * <p>
     * 先按 articleId 取 tagId，再一次性把标签查回来，避免逐个 tagId 查库造成 N+1。
     * 输出顺序跟随绑定顺序，而不是标签表的主键顺序，保证同一篇文章的标签展示稳定。
     *
     * @param articleId 文章ID
     * @return 标签视图列表，无标签时为空集合
     */
    private List<TagVO> loadTags(Long articleId) {
        List<Long> tagIds = loadTagIds(articleId);
        if (tagIds.isEmpty()) {
            return List.of();
        }
        List<Tag> tags = tagMapper.selectList(
                Wrappers.lambdaQuery(Tag.class).in(Tag::getId, tagIds));
        Map<Long, Tag> tagMap = tags.stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag, (first, second) -> first));
        List<TagVO> result = new ArrayList<>(tagIds.size());
        for (Long tagId : tagIds) {
            Tag tag = tagMap.get(tagId);
            // 关联表里可能存在指向已删除标签的历史脏行（例如早期手工改库），跳过而不是塞 null
            if (tag != null) {
                result.add(TagVO.from(tag));
            }
        }
        return result;
    }

    /**
     * 同步文章与标签的绑定关系（三态语义）
     * <p>
     * 三种入参语义必须严格区分，否则会出现「只想改标题、标签却被清空」这类静默丢数据：
     * <ul>
     *   <li>{@code null}：<b>不修改</b>，保留原绑定。前端编辑页没有动标签选择器时不会提交该字段</li>
     *   <li>{@code []}：<b>清空</b>全部绑定，代表用户主动把标签都删掉了</li>
     *   <li>非空：全量覆盖（先删后插），并做去重、上限校验与无效 tagId 过滤</li>
     * </ul>
     * 过滤「库里已不存在的 tagId」而不是整体报错：标签可能刚被别的管理员删掉，
     * 此时让用户整篇正文白填一遍，体验比静默跳过几个失效标签差得多。
     *
     * @param articleId 文章ID
     * @param tagIds    前端提交的标签ID列表，语义见上
     */
    private void syncArticleTags(Long articleId, List<Long> tagIds) {
        if (tagIds == null) {
            // 不传 = 不动标签，直接返回；这里绝不能当成「清空」处理
            return;
        }

        // 去重保序：重复的 tagId 会直接撞 uk_article_tag 唯一索引（Duplicate entry 500）
        LinkedHashSet<Long> uniqueIds = tagIds.stream()
                .filter(Objects::nonNull)
                .filter(tagId -> tagId > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (uniqueIds.size() > MAX_TAGS_PER_ARTICLE) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "单篇文章最多绑定 " + MAX_TAGS_PER_ARTICLE + " 个标签");
        }

        // 过滤无效 ID：只保留标签表里真实存在的
        List<Long> existingIds = uniqueIds.isEmpty() ? List.of()
                : tagMapper.selectList(Wrappers.lambdaQuery(Tag.class)
                        .select(Tag::getId)
                        .in(Tag::getId, uniqueIds))
                .stream()
                .map(Tag::getId)
                .toList();
        List<Long> validTagIds = uniqueIds.stream().filter(existingIds::contains).toList();

        // 先全删再插入：单篇文章的标签通常只有几个，全量重建比逐条 diff 更简单，
        // 也不会漏掉「用户取消勾选」这种解绑操作
        articleTagMapper.delete(Wrappers.lambdaQuery(ArticleTag.class)
                .eq(ArticleTag::getArticleId, articleId));
        for (Long tagId : validTagIds) {
            articleTagMapper.insert(ArticleTag.builder()
                    .articleId(articleId)
                    .tagId(tagId)
                    .build());
        }
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
            // 必须返回允许 null 键查询的空 Map。
            // 这里用 Map.of() 会在调用方 categoryNames.get(null) 时抛 NPE
            // （ImmutableCollections 的 get 对 null 键做 requireNonNull），
            // 一旦本页文章全部未分类，列表接口会直接 500。
            return Collections.emptyMap();
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
