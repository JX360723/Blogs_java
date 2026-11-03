package com.xdl.blogsadmin.controller;

import com.xdl.blogscommon.result.Result;
import com.xdl.blogscore.service.CategoryService;
import com.xdl.model.dto.CategorySaveRequest;
import com.xdl.model.vo.CategoryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端分类接口
 * <p>
 * 对应 docs/API_CONTRACT.md 6.2.4 的 4 个接口，路径与前端 {@code frontend/src/api/categories.js} 一致。
 * <p>
 * 权限：{@code SecurityConfig} 已对 {@code /api/v1/admin/**} 强制 {@code hasRole("ADMIN")}。
 * <p>
 * 分类列表与公开端共用 {@link CategoryService#listAllOrdered()}：
 * 两端都需要「全部未删除分类 + sortOrder 升序」，返回字段（id/name/parentId/description/sortOrder）
 * 已覆盖管理页展示与编辑回填，无需为管理端另建一份结构相同的 VO。
 *
 * @author xdl
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    /**
     * 分类列表（扁平数组，按 sortOrder 升序）
     *
     * @return 分类列表
     */
    @GetMapping
    public Result<List<CategoryVO>> list() {
        return Result.success(categoryService.listAllOrdered());
    }

    /**
     * 创建分类
     *
     * @param request 分类内容
     * @return 新分类ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CategorySaveRequest request) {
        return Result.success("创建成功", categoryService.createCategory(request));
    }

    /**
     * 更新分类
     *
     * @param id      分类ID
     * @param request 分类内容
     * @return 空响应体
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest request) {
        categoryService.updateCategory(id, request);
        return Result.success("更新成功", null);
    }

    /**
     * 删除分类（逻辑删除；仍有文章引用时拒绝删除并提示文章数量）
     *
     * @param id 分类ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success("删除成功", null);
    }
}
