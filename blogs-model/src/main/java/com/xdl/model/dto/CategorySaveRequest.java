package com.xdl.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端分类创建/更新请求体
 * <p>
 * 对应 docs/API_CONTRACT.md 6.2.4 的分类请求体。
 * 前端 {@code CategoryManage.vue} 当前只提交 {@code name / description / sortOrder}
 * （MVP 不做多级分类的界面），因此 {@code parentId} 允许缺省，缺省即顶级分类。
 *
 * @author xdl
 */
@Data
// 前端提交整个 form 对象（含 id），未知字段一律忽略，避免多字段导致 400
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategorySaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分类名称，与表字段 name VARCHAR(50) 对齐
     */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50位")
    private String name;

    /**
     * 父分类ID：0 或 null 表示顶级分类
     */
    @Min(value = 0, message = "父分类ID不能为负数")
    private Long parentId;

    /**
     * 分类描述，与表字段 description VARCHAR(255) 对齐
     */
    @Size(max = 255, message = "分类描述长度不能超过255位")
    private String description;

    /**
     * 排序权重，数值越小越靠前；不传时按 0 处理
     */
    @Min(value = 0, message = "排序权重不能为负数")
    @Max(value = 999, message = "排序权重不能超过999")
    private Integer sortOrder;
}
