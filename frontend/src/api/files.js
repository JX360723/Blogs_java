import service from '../utils/request'
import { USE_MOCK } from '../mock'

// 管理端：上传文件（图片等）
// 契约：POST /api/v1/admin/files，multipart/form-data
//   字段 file   —— 文件本体（必填）
//   字段 scope  —— 用途，cover=封面 / image=正文配图，服务端可据此做不同校验
// 返回：{ url, name, size, contentType }
//
// 为什么单独一个文件而不是塞进 articles.js：上传是独立资源，后面标签、分类、
// 站点图标都可能用到，放这里避免各模块互相 import。
export async function uploadFile(file, { scope = 'image' } = {}) {
  if (USE_MOCK) {
    // mock 模式不发请求，直接生成本地 blob URL 供预览。
    // 注意 blob URL 只在当前页面生命周期内有效，刷新后封面会失效——
    // mock 只用于演示，真实保存必须走 USE_MOCK=false + 后端上传接口。
    return {
      url: URL.createObjectURL(file),
      name: file.name,
      size: file.size,
      contentType: file.type,
      mock: true
    }
  }

  const formData = new FormData()
  formData.append('file', file)
  formData.append('scope', scope)

  // 不要手动设 Content-Type：必须让浏览器自动带上 multipart boundary
  return service.post('/admin/files', formData, {
    // 上传比普通请求慢，单独放宽超时（默认 15s 对大图偏紧）
    timeout: 60000
  })
}

/** 图片体积上限：5 MB，与后端 spring.servlet.multipart.max-file-size 保持一致 */
export const MAX_IMAGE_SIZE = 5 * 1024 * 1024

/** 图片扩展名白名单，与后端校验保持一致 */
export const IMAGE_ACCEPT = '.png,.jpg,.jpeg,.gif,.webp'

/**
 * 前端预校验图片，返回错误文案；通过返回空字符串。
 *
 * 前端校验只为少一次无效上传，不能替代后端校验——
 * 用户完全可以绕过页面直接 POST，服务端必须重新校验类型与体积。
 */
export function validateImage(file) {
  if (!file) return '请选择图片'
  if (file.size > MAX_IMAGE_SIZE) {
    return `图片 ${(file.size / 1024 / 1024).toFixed(1)} MB，超过 5 MB 上限`
  }
  const ext = '.' + String(file.name).split('.').pop().toLowerCase()
  if (!IMAGE_ACCEPT.split(',').includes(ext)) {
    return `不支持的格式 ${ext}，仅允许 png / jpg / jpeg / gif / webp`
  }
  return ''
}
