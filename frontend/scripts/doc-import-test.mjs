// Markdown 文档导入解析器冒烟测试
//
// 运行：npm run test:doc
//
// 覆盖解析器全部对外行为：front matter（行内数组 / 多行列表 / 未闭合 / 引号）、
// 标题三级兜底（front matter → H1 → 文件名）、摘要语法剥离、相对路径图片计数、
// 标签匹配与上限截断、空文件与只有标题等边界。

import {
  parseMarkdownDoc,
  parseFrontMatter,
  titleFromFilename,
  extractSummary,
  matchTags
} from '../src/utils/docImport.js'

let pass = 0
let fail = 0

function eq(name, actual, expected) {
  if (JSON.stringify(actual) === JSON.stringify(expected)) {
    pass++
    console.log('✅', name)
  } else {
    fail++
    console.log(`❌ ${name}\n   期望: ${JSON.stringify(expected)}\n   实际: ${JSON.stringify(actual)}`)
  }
}

function ok(name, cond, extra = '') {
  if (cond) {
    pass++
    console.log('✅', name)
  } else {
    fail++
    console.log('❌', name, extra)
  }
}

// ---- 1. 完整 front matter ----
const doc1 = `---
title: Docker 部署踩坑记录
summary: 记录一次 Docker Compose 部署中遇到的三个坑
tags: [Docker, 运维]
isTop: 1
---

正文第一段。

## 小节

内容。
`
const r1 = parseMarkdownDoc(doc1, 'docker.md')
eq('front matter 标题', r1.title, 'Docker 部署踩坑记录')
eq('front matter 摘要', r1.summary, '记录一次 Docker Compose 部署中遇到的三个坑')
eq('行内数组标签', r1.tagNames, ['Docker', '运维'])
eq('置顶标记', r1.isTop, 1)
eq('默认导入为草稿', r1.status, 0)
ok('正文不含 front matter', !r1.contentMd.includes('title:'))
ok('正文保留二级标题', r1.contentMd.includes('## 小节'))

// ---- 2. tags 多行列表 ----
const r2 = parseMarkdownDoc(
  '---\ntitle: 测试\ntags:\n  - Vue\n  - Java\n  - 工程化\n---\n正文',
  'x.md'
)
eq('多行列表标签', r2.tagNames, ['Vue', 'Java', '工程化'])

// ---- 3. 无 front matter，用 H1 ----
const r3 = parseMarkdownDoc('# 我的第一篇博客\n\n这是正文内容，讲了 **很多东西**。\n', 'whatever.md')
eq('H1 作标题', r3.title, '我的第一篇博客')
ok('H1 已从正文移除', !r3.contentMd.startsWith('# 我的'))
ok('摘要由正文生成', r3.summary.includes('这是正文内容'))

// ---- 4. 都没有，用文件名 ----
const r4 = parseMarkdownDoc('随便写点东西', '2024-01-15-my-first-post.md')
eq('文件名兜底标题', r4.title, 'my first post')
ok('文件名兜底有警告', r4.warnings.some((w) => w.includes('文件名')))

// ---- 5. 文件名清洗 ----
eq('中文文件名原样保留', titleFromFilename('我的学习笔记.md'), '我的学习笔记')
eq('去掉序号前缀', titleFromFilename('01-入门指南.md'), '入门指南')
eq('去掉日期前缀', titleFromFilename('2024-01-15-笔记.md'), '笔记')

// ---- 6. 摘要剥离语法 ----
const summary = extractSummary(
  '## 标题\n\n这是**加粗**和`代码`以及[链接](http://a.com)还有![](./a.png)\n\n- 列表项',
  100
)
ok('摘要去掉加粗标记', !summary.includes('**'), summary)
ok('摘要保留链接文字', summary.includes('链接'), summary)
ok('摘要去掉图片', !summary.includes('a.png'), summary)
ok('摘要去掉列表符号', !summary.includes('- '), summary)

// ---- 7. 相对路径图片 ----
const r7 = parseMarkdownDoc(
  '# 标题\n\n![](./images/a.png)\n\n![](https://cdn.com/b.png)\n\n![](attachments/c.jpg)',
  'p.md'
)
eq('只统计本地图片', r7.stats.localImages, 2)
ok('本地图片有警告', r7.warnings.some((w) => w.includes('相对路径图片')))

// ---- 8. 空文件 ----
const r8 = parseMarkdownDoc('', 'empty.md')
eq('空文件用文件名作标题', r8.title, 'empty')
ok('空文件有警告', r8.warnings.includes('文件内容为空'))

// ---- 9. 只有 front matter 没正文 ----
const r9 = parseMarkdownDoc('---\ntitle: 只有标题\n---\n', 'a.md')
eq('只有标题时标题正确', r9.title, '只有标题')
ok('正文为空有警告', r9.warnings.some((w) => w.includes('正文为空')))

// ---- 10. 标签匹配 ----
const pool = [
  { id: 1, name: 'Vue' },
  { id: 2, name: 'Java' }
]
const m = matchTags(['vue', 'Java', '不存在的'], pool)
eq('标签匹配不区分大小写', m.tagIds, [1, 2])
eq('未匹配标签单独列出', m.missing, ['不存在的'])

// ---- 11. 标签超过 10 个 ----
const many = Array.from({ length: 12 }, (_, i) => 'tag' + i).join(', ')
const r11 = parseMarkdownDoc(`---\ntitle: T\ntags: ${many}\n---\n正文`, 'a.md')
eq('标签截断到 10 个', r11.tagNames.length, 10)
ok('标签截断有警告', r11.warnings.some((w) => w.includes('上限 10')))

// ---- 12. front matter 未闭合 ----
const r12 = parseMarkdownDoc('---\ntitle: 没有结束符\n\n正文', 'a.md')
ok('未闭合 front matter 有提示', r12.warnings.some((w) => w.includes('front matter')))

// ---- 13. draft 标记 ----
const r13 = parseMarkdownDoc('---\ntitle: T\ndraft: true\n---\n正文', 'a.md')
ok('draft 标记有提示', r13.warnings.some((w) => w.includes('draft')))

// ---- 14. 引号包裹的值 ----
const r14 = parseMarkdownDoc('---\ntitle: "带引号的标题"\n---\n正文', 'a.md')
eq('去掉包裹引号', r14.title, '带引号的标题')

// ---- 15. 正文中间的 --- 不是 front matter ----
const r15 = parseMarkdownDoc('# 标题\n\n上半部分\n\n---\n\n下半部分', 'a.md')
eq('中间分隔线不影响标题', r15.title, '标题')
ok('中间分隔线保留在正文', r15.contentMd.includes('---'))

// ---- 16. parseFrontMatter 直接调用 ----
const fm = parseFrontMatter('---\ntitle: X\n---\nbody here')
eq('parseFrontMatter 返回正文', fm.body.trim(), 'body here')
eq('parseFrontMatter 返回数据', fm.data.title, 'X')

// ---- 17. 标题超长截断 ----
const longTitle = 'A'.repeat(150)
const r17 = parseMarkdownDoc(`---\ntitle: ${longTitle}\n---\n正文`, 'a.md')
eq('标题截断到 120', r17.title.length, 120)
ok('标题截断有警告', r17.warnings.some((w) => w.includes('120')))

// ---- 18. tags 逗号分隔不带方括号 ----
const r18 = parseMarkdownDoc('---\ntitle: T\ntags: Vue, Java\n---\n正文', 'a.md')
eq('逗号分隔标签', r18.tagNames, ['Vue', 'Java'])

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
process.exit(fail ? 1 : 0)
