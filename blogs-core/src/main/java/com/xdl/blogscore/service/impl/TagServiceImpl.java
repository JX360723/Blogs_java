package com.xdl.blogscore.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.blogscommon.result.ResultCode;
import com.xdl.blogscore.mapper.ArticleTagMapper;
import com.xdl.blogscore.mapper.TagMapper;
import com.xdl.blogscore.service.TagService;
import com.xdl.model.dto.TagSaveRequest;
import com.xdl.model.entity.ArticleTag;
import com.xdl.model.entity.Tag;
import com.xdl.model.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 标签 Service 实现类
 * <p>
 * 覆盖公开端标签列表与管理端 CRUD，风格与 {@link CategoryServiceImpl} 保持一致。
 *
 * @author xdl
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    /**
     * 删除标签时要顺手清理关联表，因此需要 ArticleTagMapper
     */
    private final ArticleTagMapper articleTagMapper;

    @Override
    public List<TagVO> listAllOrdered() {
        // 按创建时间倒序 + id 倒序兜底：新建标签排最前，同秒创建时顺序稳定不跳动。
        // 标签没有 @TableLogic 字段，不会被自动追加 deleted 条件，这里就是纯物理全表读。
        List<Tag> tags = list(Wrappers.lambdaQuery(Tag.class)
                .orderByDesc(Tag::getCreatedAt)
                .orderByDesc(Tag::getId));
        return tags.stream().map(TagVO::from).toList();
    }

    @Override
    public Long createTag(TagSaveRequest request) {
        String name = request.getName().trim();
        ensureNameAvailable(name, null);

        Tag tag = Tag.builder()
                .name(name)
                .description(trimToNull(request.getDescription()))
                .build();
        // createdAt / updatedAt 由 MybatisMetaObjectHandler 自动填充
        save(tag);
        return tag.getId();
    }

    @Override
    public void updateTag(Long id, TagSaveRequest request) {
        requireTag(id);
        String name = request.getName().trim();
        // 判重必须排除自身：否则「只改描述」也会被自己的名字判成重复
        ensureNameAvailable(name, id);

        Tag update = new Tag();
        update.setId(id);
        update.setName(name);
        // 空串表示用户主动清空描述，不能归一成 null：
        // MyBatis-Plus 的 NOT_NULL 更新策略会忽略 null 字段，旧描述就清不掉了
        update.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        requireTag(id);
        // 必须先删关联行再删标签：两者在同一事务里，任一步失败整体回滚。
        // 若先删标签，一旦清理关联失败，库里就留下一批指向不存在标签的孤儿行，
        // 文章详情查标签时拿到 null，前端可能直接渲染异常。
        articleTagMapper.delete(Wrappers.lambdaQuery(ArticleTag.class)
                .eq(ArticleTag::getTagId, id));
        // 标签实体没有 deleted 字段，removeById 走真实 DELETE（物理删除）
        removeById(id);
    }

    /**
     * 校验标签名是否已被占用
     * <p>
     * 表上有 uk_tag_name 唯一索引做最后一道防线，但靠数据库报错会变成 500；
     * 这里先查一次给出可读的 400，唯一索引只作为并发写入时的兜底。
     *
     * @param name      标签名（已 trim）
     * @param excludeId 更新时排除自身，创建时传 null
     */
    private void ensureNameAvailable(String name, Long excludeId) {
        long sameNameCount = count(Wrappers.lambdaQuery(Tag.class)
                .eq(Tag::getName, name)
                .ne(excludeId != null, Tag::getId, excludeId));
        if (sameNameCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "标签名称已存在");
        }
    }

    /**
     * 查询标签，不存在时抛 404
     *
     * @param id 标签ID
     * @return 标签实体
     */
    private Tag requireTag(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "标签ID不合法");
        }
        Tag tag = getById(id);
        if (tag == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    /**
     * 去首尾空白，空串按 null 处理
     *
     * @param value 原始文本
     * @return 归一后的文本或 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
