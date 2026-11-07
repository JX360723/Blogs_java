package com.xdl.blogscore.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.blogscommon.result.ResultCode;
import com.xdl.blogscore.mapper.ArticleMapper;
import com.xdl.blogscore.mapper.CategoryMapper;
import com.xdl.blogscore.service.CategoryService;
import com.xdl.model.dto.CategorySaveRequest;
import com.xdl.model.entity.Article;
import com.xdl.model.entity.Category;
import com.xdl.model.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类 Service 实现类
 * <p>
 * 覆盖公开端扁平列表与管理端 CRUD，契约见 docs/API_CONTRACT.md 6.1.3 / 6.2.4。
 *
 * @author xdl
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    /**
     * 顶级分类的 parentId 约定值
     */
    private static final long ROOT_PARENT_ID = 0L;

    /**
     * 父级回溯的最大深度
     * <p>
     * 用来兜底历史脏数据里已经存在的环：没有这个上限，
     * 沿 parent_id 向上遍历遇到 A→B→A 会永远转下去，接口直接挂死。
     */
    private static final int MAX_PARENT_DEPTH = 10;

    /**
     * 删除分类时要把引用该分类的文章解绑，需要跨表操作
     */
    private final ArticleMapper articleMapper;

    @Override
    public List<CategoryVO> listAllOrdered() {
        // 逻辑删除由 @TableLogic 自动过滤，无需显式加 deleted 条件；
        // 排序按 sortOrder 升序（数值越小越靠前），同权重时按 id 升序保证顺序稳定。
        // 管理端 GET /admin/categories 复用本方法：两端都需要「全部未删除分类 + 稳定排序」，
        // 返回结构（id/name/parentId/description/sortOrder）已满足管理页使用。
        List<Category> categories = list(Wrappers.lambdaQuery(Category.class)
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
        return categories.stream().map(CategoryVO::from).toList();
    }

    @Override
    public Long createCategory(CategorySaveRequest request) {
        String name = request.getName().trim();
        Long parentId = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        validateParent(parentId, null);
        ensureNameAvailable(name, parentId, null);

        Category category = Category.builder()
                .name(name)
                .parentId(parentId)
                .description(trimToNull(request.getDescription()))
                // sortOrder 不传时按 0：与表默认值一致，避免 null 落库
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .build();
        save(category);
        return category.getId();
    }

    @Override
    public void updateCategory(Long id, CategorySaveRequest request) {
        Category exist = requireCategory(id);
        String name = request.getName().trim();
        // 前端 MVP 不提供父分类选择，不传 parentId 时保留原值，避免把已有的层级关系清掉
        Long parentId = request.getParentId() == null ? exist.getParentId() : request.getParentId();
        validateParent(parentId, id);
        ensureNameAvailable(name, parentId, id);

        Category update = new Category();
        update.setId(id);
        update.setName(name);
        update.setParentId(parentId);
        // 空串表示「用户主动清空」，不能归一成 null，否则 MyBatis-Plus 的 NOT_NULL 策略会忽略该字段
        update.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        update.setSortOrder(request.getSortOrder());
        updateById(update);
    }

    @Override
    public void deleteCategory(Long id) {
        requireCategory(id);

        long childCount = count(Wrappers.lambdaQuery(Category.class).eq(Category::getParentId, id));
        if (childCount > 0) {
            // 不做级联删除：父分类一删，子分类就成了查不到父级的孤儿数据，
            // 前端扁平展示时无法解释其归属，因此要求先处理子分类
            throw new BusinessException(ResultCode.BAD_REQUEST, "该分类下还有子分类，请先删除子分类");
        }

        // 有文章引用时拒绝删除（施工单明确要求）：
        // 若放任删除，这些文章的 category_id 会指向一条查不到的记录，
        // 公开列表 categoryName 变 null、前端显示「未分类」，数据实际已脏且无法回溯。
        // 逻辑删除由 @TableLogic 自动追加 deleted = 0，此处统计的只是未删除文章。
        long articleCount = articleMapper.selectCount(
                Wrappers.lambdaQuery(Article.class).eq(Article::getCategoryId, id));
        if (articleCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该分类下还有 " + articleCount + " 篇文章，请先移走或删除");
        }

        // @TableLogic 已全局配置，removeById 实际执行 UPDATE category SET deleted = 1
        removeById(id);
    }

    /**
     * 校验父分类（含跨层成环检测）
     * <p>
     * 只拦「父分类是自己」是不够的：A 的父级设为 B、B 的父级又是 A，就形成了环。
     * 一旦成环，任何按 parent_id 向上遍历的逻辑（面包屑、树形组装、这里的校验本身）
     * 都会死循环，接口挂住不返回。
     * <p>
     * 做法：从待选的父分类开始沿 parent_id 一路向上回溯，撞到自身就判定成环；
     * 同时把回溯深度限制在 {@link #MAX_PARENT_DEPTH} 层 —— 历史脏数据里可能已经存在环，
     * 光靠「撞到 selfId」判断是死循环，深度上限是最后一道保险。
     *
     * @param parentId 父分类ID，0 表示顶级
     * @param selfId   当前分类ID，创建时为 null（新建还没有 id，不可能成环）
     */
    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null || parentId <= ROOT_PARENT_ID) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "父分类不能是自己");
        }

        Long cursor = parentId;
        int depth = 0;
        while (cursor != null && cursor > ROOT_PARENT_ID) {
            // 回溯路径上出现自己 → 把这棵子树挂到自己下面，必然成环
            if (cursor.equals(selfId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "父分类不能是自己的子分类，会形成循环层级");
            }
            if (++depth > MAX_PARENT_DEPTH) {
                // 深度超限说明库里已有环（撞不到 selfId 也会一直转），直接拒绝写入
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "分类层级超过 " + MAX_PARENT_DEPTH + " 层或存在循环引用，请检查父分类");
            }
            Category parent = getById(cursor);
            if (parent == null) {
                // 第一层就查不到 = 用户选了个不存在的父分类；
                // 更深层断链 = 历史数据脏，两种都拒绝，提示文案区分开便于排查
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        depth == 1 ? "父分类不存在" : "父分类层级数据异常，请检查上级分类");
            }
            cursor = parent.getParentId();
        }
    }

    /**
     * 校验同级下分类名是否重复
     * <p>
     * 表上只有普通索引、没有唯一约束，不校验会出现多个同名分类，
     * 访客端分类筛选条与文章列表的「分类名」都将无法区分。
     * 该规则契约未明确，属实现层补充的合理性约束（如产品要求允许同名可去掉）。
     *
     * @param name      分类名
     * @param parentId  父分类ID
     * @param excludeId 更新时排除自身
     */
    private void ensureNameAvailable(String name, Long parentId, Long excludeId) {
        long sameNameCount = count(Wrappers.lambdaQuery(Category.class)
                .eq(Category::getName, name)
                .eq(Category::getParentId, parentId)
                .ne(excludeId != null, Category::getId, excludeId));
        if (sameNameCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同级下已存在同名分类");
        }
    }

    /**
     * 查询分类，不存在时抛 404
     *
     * @param id 分类ID
     * @return 分类实体
     */
    private Category requireCategory(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类ID不合法");
        }
        Category category = getById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    /**
     * 去首尾空白，空串按 null 处理
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
}
