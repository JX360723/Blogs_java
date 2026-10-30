package com.xdl.blogscore.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xdl.blogscore.mapper.CategoryMapper;
import com.xdl.blogscore.service.CategoryService;
import com.xdl.model.entity.Category;
import com.xdl.model.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类 Service 实现类
 *
 * @author xdl
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<CategoryVO> listAllOrdered() {
        // 逻辑删除由 @TableLogic 自动过滤，无需显式加 deleted 条件；
        // 排序按 sortOrder 升序（数值越小越靠前），同权重时按 id 升序保证顺序稳定
        List<Category> categories = list(Wrappers.lambdaQuery(Category.class)
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
        return categories.stream().map(CategoryVO::from).toList();
    }
}
