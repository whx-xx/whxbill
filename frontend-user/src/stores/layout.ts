import { defineStore } from 'pinia'

const SIDEBAR_KEY = 'whx-layout-sidebar-collapsed'

export const useLayoutStore = defineStore('layout', {
  state: () => ({
    sidebarCollapsed: localStorage.getItem(SIDEBAR_KEY) === 'true'
  }),
  actions: {
    initialize() {
      document.documentElement.removeAttribute('data-theme')
      document.documentElement.classList.remove('dark')
      localStorage.removeItem('whx-layout-theme')
    },
    setSidebarCollapsed(value: boolean) {
      this.sidebarCollapsed = value
      localStorage.setItem(SIDEBAR_KEY, String(value))
    },
    toggleSidebar() {
      this.setSidebarCollapsed(!this.sidebarCollapsed)
    }
  }
})
