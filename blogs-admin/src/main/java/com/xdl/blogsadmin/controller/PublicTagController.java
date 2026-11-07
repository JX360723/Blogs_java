package com.xdl.blogsadmin.controller;

import com.xdl.blogscommon.result.Result;
import com.xdl.blogscore.service.TagService;
import com.xdl.model.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开标签接口（访客端标签筛选条）
 * <p>
 * 对应 {@code GET /api/v1/tags}：返回扁平数组（非分页），按创建时间倒序。
 * 路径与前端 {@code frontend/src/api/tags.js} 的 {@code getTags()} 一致。
 * <p>
 * {@code SecurityConfig} 已把 {@code GET /api/v1/tags/**} 放行，无需额外注解。
 *
 * @author xdl
 */
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class PublicTagController {

    private final TagService tagService;

    /**
     * 获取全部标签
     *
     * @return 标签列表（扁平数组）
     */
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.success(tagService.listAllOrdered());
    }
}
