import service from '../utils/request'

// 公开：分类列表 GET /categories
export function getCategories() {
  return service.get('/categories')
}

// 管理端：分类列表 GET /admin/categories
export function getAdminCategories() {
  return service.get('/admin/categories')
}

// 管理端：新建分类 POST /admin/categories
export function createCategory(data) {
  return service.post('/admin/categories', data)
}

// 管理端：更新分类 PUT /admin/categories/{id}
export function updateCategory(id, data) {
  return service.put(`/admin/categories/${id}`, data)
}

// 管理端：删除分类 DELETE /admin/categories/{id}
export function deleteCategory(id) {
  return service.delete(`/admin/categories/${id}`)
}
