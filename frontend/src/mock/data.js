// mock 数据集中在此文件。字段对齐 docs/API_CONTRACT.md 草案（待审计确认）
// 注意：以下均为演示数据，不表示后端已实现这些字段；对接后以真实接口为准。

const now = Date.now()

export const mockCategories = [
  { id: 1, name: '技术笔记', parentId: 0, description: '编程与技术实践', sortOrder: 1, createdAt: now, updatedAt: now },
  { id: 2, name: '读书思考', parentId: 0, description: '阅读与个人思考', sortOrder: 2, createdAt: now, updatedAt: now },
  { id: 3, name: '生活随笔', parentId: 0, description: '日常生活记录', sortOrder: 3, createdAt: now, updatedAt: now }
]

// 标签（MVP 已纳入，排在分类之后；后端 tag 表为阶段3，先 mock 先行）
export const mockTags = [
  { id: 1, name: 'Vue', description: 'Vue.js 相关', createdAt: now, updatedAt: now },
  { id: 2, name: 'Java', description: 'Java/Spring 相关', createdAt: now, updatedAt: now },
  { id: 3, name: '阅读', description: '读书与思考', createdAt: now, updatedAt: now },
  { id: 4, name: '工程化', description: '工程实践与工具链', createdAt: now, updatedAt: now }
]

function makeArticle(id, title, categoryId, status, extra = {}) {
  const created = now - (100 - id) * 3600 * 1000
  return {
    id,
    title,
    summary: extra.summary || `${title}——这是演示摘要内容，用于展示文章列表的概要信息。`,
    contentMd: extra.contentMd || `# ${title}\n\n这是 **${title}** 的演示正文（Markdown）。\n\n- 要点一\n- 要点二\n\n> 引用示例：知识需要积累与分享。`,
    contentHtml: '',
    categoryId,
    userId: 1,
    status, // 0=草稿 1=已发布 2=已下架
    coverImage: extra.coverImage || '',
    viewCount: extra.viewCount || Math.floor(Math.random() * 500) + 10,
    isTop: extra.isTop || 0,
    tagIds: extra.tagIds || [],
    createdAt: created,
    updatedAt: created
  }
}

export const mockArticles = [
  makeArticle(1, 'Vue3 组合式 API 实战总结', 1, 1, { viewCount: 320, tagIds: [1, 4], isTop: 1 }),
  makeArticle(2, 'Spring Boot 多模块工程搭建', 1, 1, { viewCount: 186, tagIds: [2, 4] }),
  makeArticle(3, '读完《刻意练习》的一些想法', 2, 1, { viewCount: 95, tagIds: [3] }),
  makeArticle(4, '本周跑步与作息记录', 3, 1, { viewCount: 42, tagIds: [] }),
  makeArticle(5, '前端工程化：Vite 与代理配置', 1, 1, { viewCount: 210, tagIds: [4] }),
  makeArticle(6, '草稿：待发布的深度技术文章', 1, 0, { viewCount: 0, tagIds: [2] }),
  makeArticle(7, '已下架的历史文章', 2, 2, { viewCount: 8, tagIds: [3] })
]

// 管理员账号（mock 专用，非真实凭证；真实登录由后端实现，初始密码经 ADMIN_INIT_PASSWORD 注入）
export const mockAdmin = {
  username: 'admin',
  password: 'admin123',
  status: 1, // 1启用/0禁用
  userInfo: { id: 1, username: 'admin', nickname: '系统管理员', avatar: '', role: 'ADMIN' }
}

// 禁用账号（mock 专用，演示登录 403 场景）
export const mockAdminDisabled = {
  username: 'disabled',
  password: 'admin123',
  status: 0,
  userInfo: { id: 2, username: 'disabled', nickname: '已禁用账号', avatar: '', role: 'ADMIN' }
}
