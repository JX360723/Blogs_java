package com.xdl.blogsadmin.controller;

import com.xdl.blogscommon.result.Result;
import com.xdl.blogscore.storage.LocalFileStorage;
import com.xdl.model.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端图片上传接口
 * <p>
 * 契约：{@code POST /api/v1/admin/files}，multipart 字段 {@code file}（必填）+
 * {@code scope}（cover/image，可缺省），返回 {@code Result<FileVO>}。
 * <p>
 * 权限：{@code SecurityConfig} 对 {@code /api/v1/admin/**} 强制 {@code hasRole("ADMIN")}，
 * 本类不重复写权限注解。校验与落盘规则全部在 {@link LocalFileStorage}，
 * Controller 只做参数接收与响应包装。
 *
 * @author xdl
 */
@RestController
@RequestMapping("/api/v1/admin/files")
@RequiredArgsConstructor
public class AdminFileController {

    private final LocalFileStorage localFileStorage;

    /**
     * 上传图片（封面 scope=cover / 正文配图 scope=image）
     *
     * @param file  图片文件，必填
     * @param scope 用途标记，可缺省，缺省按 image 处理
     * @return 含相对 url 的 FileVO
     */
    @PostMapping
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "scope", required = false) String scope) {
        return Result.success(localFileStorage.store(file, scope));
    }
}
