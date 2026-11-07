package com.xdl.blogscore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xdl.model.dto.TagSaveRequest;
import com.xdl.model.entity.Tag;
import com.xdl.model.vo.TagVO;

import java.util.List;

/**
 * 标签 Service 接口
 *
 * @author xdl
 */
public interface TagService extends IService<Tag> {

    /**
     * 标签列表：扁平数组（非分页），按创建时间倒序
     * <p>
     * 公开接口 {@code GET /api/v1/tags} 与管理端 {@code GET /api/v1/admin/tags} 共用。
     * 用「创建时间倒序 + id 倒序」而不是按名称升序：新建的标签排在最前面，
     * 管理端建完标签立刻能在列表顶部看到；id 倒序用于兜底 created_at 相同（同秒批量建）时的顺序稳定。
     *
     * @return 标签列表
     */
    List<TagVO> listAllOrdered();

    /**
     * 创建标签
     * <p>
     * 名称全局唯一（表上有 uk_tag_name），重复时抛 400「标签名称已存在」，
     * 而不是把 DuplicateKeyException 抛到全局处理器变成 500 —— 这是用户输入问题，不是系统故障。
     *
     * @param request 标签内容
     * @return 新标签ID
     */
    Long createTag(TagSaveRequest request);

    /**
     * 更新标签
     * <p>
     * 判重时排除自身，否则「只改描述、名称不动」也会被自己的名字判定为重复。
     *
     * @param id      标签ID
     * @param request 标签内容
     */
    void updateTag(Long id, TagSaveRequest request);

    /**
     * 删除标签（物理删除 + 先清理关联行）
     * <p>
     * 为什么是物理删除：表上没有 {@code deleted} 字段，也不打算加 —— 唯一索引 uk_tag_name
     * 会挡住「删掉后重建同名标签」。为什么必须先删 article_tag：
     * 关联表没有外键约束，删标签不清理会留下指向不存在标签的孤儿行，
     * 文章详情按这些 tagId 查标签只会查到 null，前端渲染时可能直接报错。
     *
     * @param id 标签ID
     */
    void deleteTag(Long id);
}
