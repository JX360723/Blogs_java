import service from '../utils/request'

// 公开：文章列表（仅已发布）GET /articles?pageNum=&pageSize=&categoryId=
export function getPublicArticles(params) {
  return service.get('/articles', { params })
}

// 公开：文章详情 GET /articles/{id}
export function getPublicArticle(id) {
  return service.get(`/articles/${id}`)
}

// 管理端：文章列表（含草稿/下架）GET /admin/articles?pageNum=&pageSize=&status=
export function getAdminArticles(params) {
  return service.get('/admin/articles', { params })
}

// 管理端：文章详情 GET /admin/articles/{id}（含 contentMd 全文，编辑回填用）
// 注意：管理端列表接口 AdminArticleSummaryVO 刻意排除了 contentMd 等大字段，
// 直接拿列表行去回填编辑表单会导致正文为空、保存即丢正文，必须走这个详情接口。
export function getAdminArticle(id) {
  return service.get(`/admin/articles/${id}`)
}

// 管理端：新建文章 POST /admin/articles
export function createArticle(data) {
  return service.post('/admin/articles', data)
}

// 管理端：更新文章 PUT /admin/articles/{id}
export function updateArticle(id, data) {
  return service.put(`/admin/articles/${id}`, data)
}

// 管理端：删除文章 DELETE /admin/articles/{id}
export function deleteArticle(id) {
  return service.delete(`/admin/articles/${id}`)
}
