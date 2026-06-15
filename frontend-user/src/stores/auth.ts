import { defineStore } from 'pinia'
import request from '@/utils/request'
import type { LoginPayload } from '@/types'

const TOKEN_KEY = 'whx-user-token'
const REFRESH_KEY = 'whx-user-refresh'
const USER_KEY = 'whx-user-profile'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // 页面刷新后从 localStorage 恢复登录态，避免一刷新就掉线。
    accessToken: localStorage.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_KEY) || '',
    profile: JSON.parse(localStorage.getItem(USER_KEY) || 'null') as LoginPayload | null
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken)
  },
  actions: {
    async login(payload: { username: string; password: string }) {
      // 登录成功后同时保存 token 和用户资料。
      const result = await request.post('/api/auth/login', payload)
      this.setSession(result)
      await this.loadProfile()
    },
    async register(payload: { username: string; nickname: string; password: string; confirmPassword: string; email?: string }) {
      // 注册接口和登录接口返回结构一致，所以注册完可以直接进入已登录状态。
      const result = await request.post('/api/auth/register', payload)
      this.setSession(result)
      await this.loadProfile()
    },
    async loadProfile() {
      // 重新拉取一次当前用户资料和权限，给路由守卫和菜单使用。
      const result = await request.get('/api/user/profile')
      this.profile = {
        ...((this.profile || {}) as Partial<LoginPayload>),
        userId: result.userId,
        username: result.username,
        nickname: result.nickname,
        email: result.email,
        phone: result.phone,
        avatarUrl: result.avatarUrl,
        roles: result.roles,
        permissions: result.permissions,
        accessToken: this.accessToken,
        refreshToken: this.refreshToken
      }
      localStorage.setItem(USER_KEY, JSON.stringify(this.profile))
    },
    setSession(payload: LoginPayload) {
      // 统一写入本地缓存，页面刷新后还能保持登录。
      this.accessToken = payload.accessToken
      this.refreshToken = payload.refreshToken
      this.profile = payload
      localStorage.setItem(TOKEN_KEY, payload.accessToken)
      localStorage.setItem(REFRESH_KEY, payload.refreshToken)
      localStorage.setItem(USER_KEY, JSON.stringify(payload))
    },
    clearSession() {
      // 清掉本地缓存，相当于退出登录。
      this.accessToken = ''
      this.refreshToken = ''
      this.profile = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_KEY)
      localStorage.removeItem(USER_KEY)
    },
    async logout() {
      // 先通知后端失效 token，再清本地状态。
      try {
        if (this.accessToken) {
          await request.post('/api/auth/logout')
        }
      } finally {
        this.clearSession()
      }
    }
  }
})
