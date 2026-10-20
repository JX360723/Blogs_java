import service from '../utils/request'

// 管理员登录：POST /auth/login → { token, userInfo }
export function login(data) {
  return service.post('/auth/login', data)
}

// 获取当前登录用户信息：GET /auth/me
export function getMe() {
  return service.get('/auth/me')
}
