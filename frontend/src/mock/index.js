// 统一 mock 适配器：当 USE_MOCK=true 时，axios 请求不再发出网络，而是由本适配器按
// 「方法 + URL 模式」匹配到本地处理器并返回契约规定的 Result<T> 包络。
//
// 15:00 切真实接口：只需把 USE_MOCK 置为 false（.env.local 里 VITE_USE_MOCK=false），
// API 层零改动，请求将直接走 vite proxy → http://localhost:8080。
import { mockArticles, mockCategories, mockAdmin, mockAdminDisabled } from './data.js'

// 默认开启 mock；可用 VITE_USE_MOCK=false 关闭（node 冒烟测试环境无 import.meta.env 时按开启处理）
export const USE_MOCK = (import.meta.env && import.meta.env.VITE_USE_MOCK) !== 'false'

let nextArticleId = 100

// ---- Result<T> 包络（对齐 API_CONTRACT.md）----
function ok(data) {
  return { code: 200, message: 'ok', data, timestamp: Date.now() }
}
function fail(code, message) {
  return { code, message, data: null, timestamp: Date.now() }
}

// ---- 分页工具 ----
function paginate(list, params) {
  const pageNum = Math.max(1, Number(params?.pageNum) || 1)
  const pageSize = Math.max(1, Number(params?.pageSize) || 10)
  const start = (pageNum - 1) * pageSize
  const items = list.slice(start, start + pageSize)
  return {
    list: items,
    total: list.length,
    pageNum,
    pageSize,
    pages: Math.ceil(list.length / pageSize)
  }
}

// ---- 处理器注册与匹配 ----
const routes = []

function route(method, pattern, handler) {
  routes.push({ method: method.toUpperCase(), pattern, handler })
}

function match(config) {
  // axios adapter 收到的 config.url 已拼接 baseURL（/api/v1 + 接口路径）
  // 这里剥离统一前缀，路由 pattern 按接口路径书写
  const fullUrl = (config.url || '').split('?')[0]
  const url = fullUrl.replace(/^\/api\/v1/, '') || '/'
  const method = (config.method || 'get').toUpperCase()
  for (const r of routes) {
    if (r.method !== method) continue
    const m = url.match(r.pattern)
    if (m) {
      return { handler: r.handler, params: m.slice(1) }
    }
  }
  return null
}

// 供页面读取用：处理查询参数
function queryParams(config) {
  const url = config.url || ''
  const qs = url.includes('?') ? url.split('?')[1] : ''
  const out = {}
  if (qs) {
    qs.split('&').forEach((kv) => {
      const [k, v] = kv.split('=')
      if (k) out[decodeURIComponent(k)] = decodeURIComponent(v || '')
    })
  }
  return out
}

function parseBody(config) {
  if (typeof config.data === 'string') {
    try {
      return JSON.parse(config.data)
    } catch {
      return {}
    }
  }
  return config.data || {}
}

// ---- 接口实现（对齐 API_CONTRACT.md MVP 主链路）----

// 公开：文章列表（仅已发布；支持分页/分类过滤）
route('GET', /^\/articles$/, (config) => {
  const p = queryParams(config)
  const status = p.status === undefined ? 1 : Number(p.status)
  const catId = p.categoryId ? Number(p.categoryId) : null
  let list = mockArticles.filter((a) => a.status === status)
  if (catId) list = list.filter((a) => a.categoryId === catId)
  list = [...list].sort((a, b) => (b.isTop || 0) - (a.isTop || 0) || b.createdAt - a.createdAt)
  return ok(paginate(list, p))
})

// 公开：文章详情（仅已发布；草稿/下架对游客 404）
route('GET', /^\/articles\/(\d+)$/, (config, [id]) => {
  const article = mockArticles.find((a) => a.id === Number(id))
  if (!article || article.status !== 1) return fail(404, '文章不存在或未发布')
  const cat = mockCategories.find((c) => c.id === article.categoryId)
  return ok({ ...article, categoryName: cat?.name || '' })
})

// 公开：分类列表
route('GET', /^\/categories$/, () => ok([...mockCategories].sort((a, b) => a.sortOrder - b.sortOrder)))

// 管理端：登录（对齐 API_CONTRACT.md 6.2.1 冻结规范 2026-10-20）
// 成功 data：{ token, tokenType:'Bearer', expiresIn:86400, userInfo }
// 失败：401 用户名或密码错误（统一文案）/ 403 账号已被禁用 / 400 参数校验
route('POST', /^\/auth\/login$/, (config) => {
  const body = parseBody(config)
  const username = typeof body.username === 'string' ? body.username.trim() : ''
  const password = typeof body.password === 'string' ? body.password : ''

  // 参数校验（对齐契约：username 3-50 去空格非空；password 6-64 不去空格）→ 400
  if (!username || username.length < 3 || username.length > 50) {
    return fail(400, '用户名长度需为 3-50 位')
  }
  if (!password || password.length < 6 || password.length > 64) {
    return fail(400, '密码长度不能少于6位')
  }

  const user = [mockAdmin, mockAdminDisabled].find((u) => u.username === username)
  if (!user) return fail(401, '用户名或密码错误')
  if (user.status === 0) return fail(403, '账号已被禁用')
  if (user.password !== password) return fail(401, '用户名或密码错误')

  return ok({
    token: `mock-jwt-token-${Date.now()}`,
    tokenType: 'Bearer',
    expiresIn: 86400,
    userInfo: { ...user.userInfo }
  })
})

// 管理端：当前登录用户信息
route('GET', /^\/auth\/me$/, (config) => {
  const auth = config.headers?.Authorization || ''
  if (!auth.startsWith('Bearer ')) return fail(401, '未登录')
  return ok({ ...mockAdmin.userInfo })
})

// 管理端：文章管理列表（含草稿/下架，支持状态过滤）
route('GET', /^\/admin\/articles$/, (config) => {
  const p = queryParams(config)
  let list = [...mockArticles]
  if (p.status !== undefined && p.status !== '') list = list.filter((a) => a.status === Number(p.status))
  list.sort((a, b) => b.createdAt - a.createdAt)
  return ok(paginate(list, p))
})

// 管理端：新建文章
route('POST', /^\/admin\/articles$/, (config) => {
  const body = parseBody(config)
  const id = nextArticleId++
  const article = {
    id,
    title: body.title || '未命名文章',
    summary: body.summary || '',
    contentMd: body.contentMd || '',
    contentHtml: '',
    categoryId: body.categoryId || 0,
    userId: 1,
    status: body.status === undefined ? 0 : Number(body.status),
    coverImage: body.coverImage || '',
    viewCount: 0,
    isTop: body.isTop || 0,
    tagIds: body.tagIds || [],
    createdAt: Date.now(),
    updatedAt: Date.now()
  }
  mockArticles.unshift(article)
  return ok(article)
})

// 管理端：更新文章
route('PUT', /^\/admin\/articles\/(\d+)$/, (config, [id]) => {
  const article = mockArticles.find((a) => a.id === Number(id))
  if (!article) return fail(404, '文章不存在')
  const body = parseBody(config)
  Object.assign(article, {
    title: body.title ?? article.title,
    summary: body.summary ?? article.summary,
    contentMd: body.contentMd ?? article.contentMd,
    categoryId: body.categoryId ?? article.categoryId,
    status: body.status ?? article.status,
    isTop: body.isTop ?? article.isTop,
    updatedAt: Date.now()
  })
  return ok(article)
})

// 管理端：删除文章（逻辑删除）
route('DELETE', /^\/admin\/articles\/(\d+)$/, (config, [id]) => {
  const idx = mockArticles.findIndex((a) => a.id === Number(id))
  if (idx < 0) return fail(404, '文章不存在')
  mockArticles.splice(idx, 1)
  return ok(null)
})

// 管理端：分类列表（与公开一致，管理页复用）
route('GET', /^\/admin\/categories$/, () => ok([...mockCategories].sort((a, b) => a.sortOrder - b.sortOrder)))

// 管理端：新建分类
route('POST', /^\/admin\/categories$/, (config) => {
  const body = parseBody(config)
  const id = Math.max(...mockCategories.map((c) => c.id)) + 1
  const cat = {
    id,
    name: body.name || '未命名分类',
    parentId: body.parentId || 0,
    description: body.description || '',
    sortOrder: body.sortOrder || mockCategories.length + 1,
    createdAt: Date.now(),
    updatedAt: Date.now()
  }
  mockCategories.push(cat)
  return ok(cat)
})

// 管理端：更新分类
route('PUT', /^\/admin\/categories\/(\d+)$/, (config, [id]) => {
  const cat = mockCategories.find((c) => c.id === Number(id))
  if (!cat) return fail(404, '分类不存在')
  const body = parseBody(config)
  Object.assign(cat, {
    name: body.name ?? cat.name,
    description: body.description ?? cat.description,
    sortOrder: body.sortOrder ?? cat.sortOrder,
    updatedAt: Date.now()
  })
  return ok(cat)
})

// 管理端：删除分类
route('DELETE', /^\/admin\/categories\/(\d+)$/, (config, [id]) => {
  const idx = mockCategories.findIndex((c) => c.id === Number(id))
  if (idx < 0) return fail(404, '分类不存在')
  mockCategories.splice(idx, 1)
  return ok(null)
})

// ---- 适配器入口 ----
export function createMockAdapter() {
  return async (config) => {
    // 模拟网络延迟，贴近真实体验
    await new Promise((resolve) => setTimeout(resolve, 150 + Math.random() * 250))

    const matched = match(config)
    let result
    if (matched) {
      try {
        result = matched.handler(config, matched.params)
      } catch (e) {
        result = fail(500, `mock 处理器异常: ${e.message}`)
      }
    } else {
      result = fail(404, `mock 未实现的接口 ${config.method.toUpperCase()} ${config.url}`)
    }

    return {
      data: result,
      status: result.code === 200 ? 200 : result.code,
      statusText: 'OK',
      headers: { 'content-type': 'application/json' },
      config,
      request: {}
    }
  }
}
