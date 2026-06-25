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
    // 未读消息数用于菜单/顶部徽标展示。
    unreadCount: (state) => state.messages.filter((item) => item.readStatus === 0).length
  },
  actions: {
    // 推入实时消息，避免重复 id 消息造成列表重复。
    pushMessage(message: any) {
      if (!message?.id || this.messages.some((item) => item.id === message.id)) {
        return
      }
      this.messages.unshift(message)
    },
    // 从后端加载历史消息列表。
    async loadMessages() {
      this.messages = await request.get('/api/user/messages')
    },
    // 建立 STOMP WebSocket 连接，接收预算超支等实时通知。
    async connect() {
      const authStore = useAuthStore()
      if (!authStore.accessToken || this.client?.active) {
        return
      }
      await this.loadMessages()
      // WebSocket 连接同样带 token，后端握手拦截器会解析用户身份并绑定到个人消息通道。
      const wsURL = new URL('/ws', window.location.origin)
      wsURL.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      wsURL.search = `token=${encodeURIComponent(authStore.accessToken)}`
      const client = new Client({
        brokerURL: wsURL.toString(),
        reconnectDelay: 5000
      })
      client.onConnect = () => {
        this.connected = true
        // /user/queue/notifications 是 STOMP 的用户队列，只接收当前登录用户的私信通知。
        client.subscribe('/user/queue/notifications', (message) => {
          const payload = JSON.parse(message.body)
          this.pushMessage(payload)
        })
        if (authStore.profile?.userId) {
          client.subscribe(`/topic/notifications/${authStore.profile.userId}`, (message) => {
            const payload = JSON.parse(message.body)
            this.pushMessage(payload)
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
    // 退出登录或销毁页面时断开连接。
    disconnect() {
      this.client?.deactivate()
      this.client = null
      this.connected = false
    }
  }
})
