package com.xdl.blogscore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xdl.model.dto.CategorySaveRequest;
import com.xdl.model.entity.Category;
import com.xdl.model.vo.CategoryVO;

import java.util.List;

/**
 * 分类 Service 接口
 *
 * @author xdl
 */
public interface CategoryService extends IService<Category> {

    /**
     * 公开分类列表：扁平数组（非分页），按 sortOrder 升序
     * <p>
     * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.5「GET /api/v1/categories」，
     * 供主页面分类筛选条使用。MVP 前端按扁平列表消费，树形由前端自行组装。
     *
     * @return 分类列表，按排序权重升序
     */
    List<CategoryVO> listAllOrdered();

    /**
     * 创建分类
     * <p>
     * 对应 docs/API_CONTRACT.md 6.2.4「POST /api/v1/admin/categories」。
     * 校验：父分类必须存在、同级下不允许重名。
     *
     * @param request 分类内容
     * @return 新分类ID
     */
    Long createCategory(CategorySaveRequest request);

    /**
     * 更新分类
     *
     * @param id      分类ID
     * @param request 分类内容
     */
    void updateCategory(Long id, CategorySaveRequest request);

    /**
     * 删除分类（逻辑删除）
     * <p>
     * 对应的施工单要求「有文章引用则拒绝」：
     * <ul>
     *   <li>仍存在子分类 → 拒绝，避免孤儿分类</li>
     *   <li>仍被未删除的文章引用 → 拒绝，并提示文章数量。
     *       不做级联删除（个人博客误删代价太高），也不静默把文章置为未分类
     *       （会让访客端分类筛选里凭空多出一批「未分类」文章，数据实际已脏）</li>
     * </ul>
     *
     * @param id 分类ID
     */
    void deleteCategory(Long id);
}
