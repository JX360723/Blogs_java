package com.xdl.blogscore.service;

import com.baomidou.mybatisplus.extension.service.IService;
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
}
