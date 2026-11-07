package com.xdl.blogsadmin.unit;

import com.xdl.model.dto.ArticleQueryRequest;
import com.xdl.model.vo.AdminArticleDetailVO;
import com.xdl.model.vo.ArticleDetailVO;
import com.xdl.model.vo.ArticleSummaryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 公开接口契约回归线（反射断言，零 Spring 上下文）
 * <p>
 * 这三条断言保护的是「一旦被改坏就是安全事故」的约定，不是普通功能：
 * <ul>
 *   <li>公开查询参数一旦出现 status，{@code GET /articles?status=0} 就能把草稿拉出来</li>
 *   <li>公开列表 VO 一旦出现 status，等于主动告诉访客「这篇文章是草稿」</li>
 *   <li>详情 VO 一旦丢了 contentMd，前端编辑回填正文就是空，保存即清空原文</li>
 * </ul>
 * 用反射锁字段名，是因为这类问题不会让任何一行代码编译失败，靠 code review 很容易漏。
 */
@DisplayName("公开接口契约回归线")
class PublicApiContractTest {

    /** 取类自身声明的实例字段名（排除 serialVersionUID 这类静态字段） */
    private static Set<String> instanceFieldNames(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("公开查询参数 ArticleQueryRequest 绝不能出现 status 字段")
    void publicQueryRequestMustNotHaveStatus() {
        Set<String> fields = instanceFieldNames(ArticleQueryRequest.class);

        assertFalse(fields.contains("status"),
                "ArticleQueryRequest 出现了 status 字段。公开接口一旦支持 status 过滤，"
                        + "访客就能用 GET /api/v1/articles?status=0 直接拉草稿，属于安全事故。"
                        + "管理端必须使用独立的 AdminArticleQueryRequest。");
    }

    @Test
    @DisplayName("公开列表 VO 不能泄露 status 与 updatedAt")
    void publicSummaryVoMustNotExposeStatusOrUpdatedAt() {
        Set<String> fields = instanceFieldNames(ArticleSummaryVO.class);

        assertFalse(fields.contains("status"),
                "ArticleSummaryVO 出现了 status 字段，等于把「这篇文章是草稿」告诉访客。"
                        + "管理端请使用独立的 AdminArticleSummaryVO。");
        assertFalse(fields.contains("updatedAt"),
                "ArticleSummaryVO 出现了 updatedAt 字段。该字段仅管理页「更新时间」列需要，"
                        + "管理端请使用独立的 AdminArticleSummaryVO。");
    }

    @Test
    @DisplayName("公开详情 VO 必须带 contentMd（访客阅读正文依赖它）")
    void publicDetailVoMustHaveContentMd() {
        Set<String> fields = instanceFieldNames(ArticleDetailVO.class);

        assertTrue(fields.contains("contentMd"),
                "ArticleDetailVO 缺少 contentMd，访客端详情页将无法显示正文。");
    }

    @Test
    @DisplayName("管理端详情 VO 必须带 status（编辑回填依赖，缺了会把已发布文章改回草稿）")
    void adminDetailVoMustHaveStatusForAdminEdit() {
        Set<String> fields = instanceFieldNames(AdminArticleDetailVO.class);

        assertTrue(fields.contains("status"),
                "AdminArticleDetailVO 缺少 status。ArticleManage.vue 保存时会把整个表单（含 status）"
                        + "PUT 回后端，详情不回填 status 就会取默认值 0，"
                        + "编辑一篇已发布文章会把它静默降级成草稿。");
        assertTrue(fields.contains("tagIds"),
                "AdminArticleDetailVO 缺少 tagIds，编辑页的标签选择器无法回填已绑定的标签。");
    }

    @Test
    @DisplayName("公开详情 VO 仍然不能出现 status（阶段 3 拆分管理端 VO 后不得回退）")
    void publicDetailVoMustNotExposeStatus() {
        Set<String> fields = instanceFieldNames(ArticleDetailVO.class);

        assertFalse(fields.contains("status"),
                "公开的 ArticleDetailVO 又出现了 status。管理端需要 status 请用 AdminArticleDetailVO，"
                        + "公开接口暴露状态既无信息量，又会诱导他人尝试 ?status=0 拉草稿。");
    }

    @Test
    @DisplayName("详情 VO 是列表 VO 的超集（多出正文相关字段）")
    void detailVoShouldBeSupersetOfSummaryVo() {
        Set<String> summary = instanceFieldNames(ArticleSummaryVO.class);
        Set<String> detail = instanceFieldNames(ArticleDetailVO.class);

        assertTrue(detail.containsAll(summary),
                "ArticleDetailVO 应当包含 ArticleSummaryVO 的全部字段，当前缺少: "
                        + summary.stream().filter(f -> !detail.contains(f)).collect(Collectors.toSet()));
        assertTrue(detail.contains("contentMd"),
                "详情相对列表多出来的核心字段就是 contentMd。");
    }
}
