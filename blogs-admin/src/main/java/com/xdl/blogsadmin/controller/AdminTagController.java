package com.xdl.blogsadmin.controller;

import com.xdl.blogscommon.result.Result;
import com.xdl.blogscore.service.TagService;
import com.xdl.model.dto.TagSaveRequest;
import com.xdl.model.vo.TagVO;
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
 * 管理端标签接口
 * <p>
 * 权限：{@code SecurityConfig} 已对 {@code /api/v1/admin/**} 强制 {@code hasRole("ADMIN")}，
 * 未携带有效 token 返回 401、非管理员返回 403，Controller 不重复写权限注解。
 * <p>
 * 分层职责：Controller 只做参数接收与响应包装，唯一性校验、级联清理等规则都在 TagService。
 *
 * @author xdl
 */
@RestController
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final TagService tagService;

    /**
     * 标签列表（与公开接口同一份数据，管理端需要看到全部标签）
     *
     * @return 标签列表
     */
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.success(tagService.listAllOrdered());
    }

    /**
     * 创建标签
     *
     * @param request 标签内容
     * @return 新标签ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TagSaveRequest request) {
        return Result.success("创建成功", tagService.createTag(request));
    }

    /**
     * 更新标签
     *
     * @param id      标签ID
     * @param request 标签内容
     * @return 空响应体
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TagSaveRequest request) {
        tagService.updateTag(id, request);
        return Result.success("更新成功", null);
    }

    /**
     * 删除标签（物理删除，同时清理 article_tag 中的绑定关系）
     *
     * @param id 标签ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.success("删除成功", null);
    }
}
