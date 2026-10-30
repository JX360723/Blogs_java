import service from '../utils/request'

// 公开：标签列表 GET /tags（访客端标签筛选用）
export function getTags() {
  return service.get('/tags')
}

// 管理端：标签列表 GET /admin/tags
export function getAdminTags() {
  return service.get('/admin/tags')
}

// 管理端：新建标签 POST /admin/tags
export function createTag(data) {
  return service.post('/admin/tags', data)
}

// 管理端：更新标签 PUT /admin/tags/{id}
export function updateTag(id, data) {
  return service.put(`/admin/tags/${id}`, data)
}

// 管理端：删除标签 DELETE /admin/tags/{id}
export function deleteTag(id) {
  return service.delete(`/admin/tags/${id}`)
}
