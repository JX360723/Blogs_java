package com.xdl.blogscore.support;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

/**
 * Markdown 渲染器
 * <p>
 * 决策依据：docs/BACKEND_DESIGN_DB_AND_API.md 第六节待决策项 1「contentHtml 由谁生成」，
 * 建议方案为后端渲染、前端只提交 contentMd。这里按该方案落地，
 * 管理端保存文章时统一由服务端生成 contentHtml 入库，前端无需自行渲染。
 * <p>
 * 安全策略：{@code escapeHtml(true)} 会把 Markdown 正文中的内联 HTML 转义成纯文本，
 * 而不是原样透传。理由是与前端 {@code frontend/src/utils/markdown.js}「先转义再解析」的策略保持一致，
 * 避免同一条内容在编辑预览与真实详情页出现「一个转义、一个执行」的差异，
 * 也防止正文里粘贴的 {@code <script>} 在详情页被 {@code v-html} 执行。
 * <p>
 * {@link Parser} 与 {@link HtmlRenderer} 均为线程安全且可复用，故在单例中持有。
 *
 * @author xdl
 */
@Component
public class MarkdownRenderer {

    /**
     * Markdown 解析器，无扩展语法（MVP 只用标准 Markdown）
     */
    private final Parser parser = Parser.builder().build();

    /**
     * HTML 渲染器，转义正文中的原始 HTML
     */
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .build();

    /**
     * 把 Markdown 渲染成 HTML
     *
     * @param markdown Markdown 原文，可为 null
     * @return 渲染后的 HTML；入参为空时返回 null，表示「无正文」而非空字符串
     */
    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
}
