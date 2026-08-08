import { defineStore } from 'pinia'
import { getCurrentUser, login, logout as logoutRequest } from '../services/api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    username: '',
    role: '',
    authenticated: false,
    loaded: false,
  }),
  actions: {
    async loadCurrent() {
      try {
        const current = await getCurrentUser()
        this.username = current.username
        this.role = current.role
        this.authenticated = true
      } catch {
        this.authenticated = false
      } finally {
        this.loaded = true
      }
    },
    async signIn(username: string, password: string) {
      const current = await login(username, password)
      this.username = current.username
      this.role = current.role
      this.authenticated = true
      this.loaded = true
    },
    async logout() {
      if (this.authenticated) {
        await logoutRequest()
      }
      this.username = ''
      this.role = ''
      this.authenticated = false
    },
  },
})
