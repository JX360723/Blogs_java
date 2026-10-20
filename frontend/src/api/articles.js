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
