package com.xdl.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 标签创建/更新请求体
 * <p>
 * 对应 {@code POST /api/v1/admin/tags} 与 {@code PUT /api/v1/admin/tags/{id}}。
 * 字段长度与表结构严格对齐，超长在参数边界就拦下，避免落库时被 MySQL 截断成脏数据。
 *
 * @author xdl
 */
@Data
public class TagSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签名称，必填，最长 30（与表字段 name VARCHAR(30) 对齐）
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 30, message = "标签名称长度不能超过30位")
    private String name;

    /**
     * 标签描述，可选，最长 255（与表字段 description VARCHAR(255) 对齐）
     */
    @Size(max = 255, message = "标签描述长度不能超过255位")
    private String description;
}
