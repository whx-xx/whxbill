import { defineStore } from 'pinia'
import { Client } from '@stomp/stompjs'
import request from '@/utils/request'
import { apiBaseURL } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    messages: [] as any[],
    connected: false,
    client: null as Client | null
  }),
  getters: {
    unreadCount: (state) => state.messages.filter((item) => item.readStatus === 0).length
  },
  actions: {
    async loadMessages() {
      this.messages = await request.get('/api/user/messages')
    },
    async connect() {
      const authStore = useAuthStore()
      if (!authStore.accessToken || this.client?.active) {
        return
      }
      await this.loadMessages()
      const wsURL = new URL('/ws', window.location.origin)
      wsURL.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      wsURL.search = `token=${encodeURIComponent(authStore.accessToken)}`
      const client = new Client({
        brokerURL: wsURL.toString(),
        reconnectDelay: 5000
      })
      client.onConnect = () => {
        this.connected = true
        client.subscribe('/user/queue/notifications', (message) => {
          const payload = JSON.parse(message.body)
          this.messages.unshift(payload)
        })
        if (authStore.profile?.userId) {
          client.subscribe(`/topic/notifications/${authStore.profile.userId}`, (message) => {
            const payload = JSON.parse(message.body)
            this.messages.unshift(payload)
          })
        }
      }
      client.onStompError = () => {
        this.connected = false
      }
      client.onWebSocketClose = () => {
        this.connected = false
      }
      client.activate()
      this.client = client
    },
    disconnect() {
      this.client?.deactivate()
      this.client = null
      this.connected = false
    }
  }
})
