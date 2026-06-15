import { defineStore } from 'pinia'
import request from '@/utils/request'

const TOKEN_KEY = 'whx-admin-token'
const REFRESH_KEY = 'whx-admin-refresh'
const PROFILE_KEY = 'whx-admin-profile'

export const useAuthStore = defineStore('admin-auth', {
  state: () => ({
    accessToken: localStorage.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_KEY) || '',
    profile: JSON.parse(localStorage.getItem(PROFILE_KEY) || 'null') as any
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken),
    permissions: (state) => state.profile?.permissions || []
  },
  actions: {
    async login(payload: { username: string; password: string }) {
      const result = await request.post('/api/auth/login', payload)
      this.accessToken = result.accessToken
      this.refreshToken = result.refreshToken
      this.profile = result
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      localStorage.setItem(REFRESH_KEY, result.refreshToken)
      localStorage.setItem(PROFILE_KEY, JSON.stringify(result))
    },
    async loadProfile() {
      const result = await request.get('/api/user/profile')
      this.profile = {
        ...(this.profile || {}),
        ...result,
        accessToken: this.accessToken,
        refreshToken: this.refreshToken
      }
      localStorage.setItem(PROFILE_KEY, JSON.stringify(this.profile))
    },
    clearSession() {
      this.accessToken = ''
      this.refreshToken = ''
      this.profile = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_KEY)
      localStorage.removeItem(PROFILE_KEY)
    },
    async logout() {
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
