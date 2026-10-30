package com.xdl.blogsadmin.controller;

import com.xdl.blogscommon.result.Result;
import com.xdl.blogscore.service.CategoryService;
import com.xdl.model.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开分类接口（主页面分类筛选条）
 * <p>
 * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.5「GET /api/v1/categories」：
 * 返回扁平数组（非分页），按 sortOrder 升序。
 * 路径与前端 frontend/src/api/categories.js 的 getCategories() 一致。
 *
 * @author xdl
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class PublicCategoryController {

    private final CategoryService categoryService;

    /**
     * 获取全部分类
     *
     * @return 分类列表（扁平数组）
     */
    @GetMapping
    public Result<List<CategoryVO>> list() {
        return Result.success(categoryService.listAllOrdered());
    }
}
