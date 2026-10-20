import { defineStore } from 'pinia'
import { login as apiLogin, getMe } from '../api/auth'

const TOKEN_KEY = 'blog_token'
const USER_KEY = 'blog_user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLoggedIn: (state) => !!state.token
  },
  actions: {
    async login(username, password) {
      const data = await apiLogin({ username, password })
      this.token = data.token
      this.userInfo = data.userInfo
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.userInfo))
      return data
    },
    async fetchMe() {
      if (!this.token) return null
      try {
        const me = await getMe()
        this.userInfo = me
        localStorage.setItem(USER_KEY, JSON.stringify(me))
        return me
      } catch (e) {
        return null
      }
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
