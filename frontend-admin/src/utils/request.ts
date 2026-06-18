import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

function resolveApiBaseURL() {
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL
  }

  const { protocol, hostname } = window.location
  const userAgent = navigator.userAgent.toLowerCase()
  const isAndroid = userAgent.includes('android')
  const isCapacitor = protocol === 'capacitor:' || protocol === 'ionic:'

  if (isCapacitor || (isAndroid && (hostname === 'localhost' || hostname === '127.0.0.1'))) {
    return 'http://10.0.2.2:8080'
  }

  return '/api'
}

export const apiBaseURL = resolveApiBaseURL()

const instance = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000
})

function normalizeApiUrl(url: string) {
  if (apiBaseURL.replace(/\/$/, '').endsWith('/api') && url.startsWith('/api/')) {
    return url.slice(4)
  }
  return url
}

function routeToException(path: '/403' | '/500') {
  if (router.currentRoute.value.path !== path) {
    router.push(path)
  }
}

function isLoginRequest(url?: string) {
  return Boolean(url?.includes('/auth/login'))
}

function friendlyErrorMessage(status?: number, message?: string, url?: string) {
  if (status === 401 && isLoginRequest(url)) {
    return '用户名或密码不正确，请检查后再试'
  }
  if (status === 401) {
    return '登录状态已失效，请重新登录'
  }
  if (status === 403) {
    return '当前账号没有权限访问该功能'
  }
  if (status === 404 && url?.startsWith('/api/')) {
    return '接口暂时没有连通，请确认后端服务已启动'
  }
  if (status && status >= 500) {
    return '服务暂时开小差了，请稍后再试'
  }
  return message || '请求失败，请稍后再试'
}

instance.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.accessToken) {
    config.headers.Authorization = `Bearer ${authStore.accessToken}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const payload = response.data
    if (payload.code !== 200) {
      if (payload.code === 401) {
        useAuthStore().clearSession()
        if (!isLoginRequest(response.config.url) && router.currentRoute.value.path !== '/login') {
          router.push('/login')
        }
      } else if (payload.code === 403) {
        routeToException('/403')
      } else if (payload.code >= 500) {
        routeToException('/500')
      }
      ElMessage.error(friendlyErrorMessage(payload.code, payload.message, response.config.url))
      return Promise.reject(payload)
    }
    return payload.data
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      useAuthStore().clearSession()
      if (!isLoginRequest(error.config?.url) && router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    } else if (status === 403) {
      routeToException('/403')
    } else if (status && status >= 500) {
      routeToException('/500')
    }
    ElMessage.error(friendlyErrorMessage(status, error.response?.data?.message || error.message, error.config?.url))
    return Promise.reject(error)
  }
)

const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(normalizeApiUrl(url), config) as unknown as Promise<T>
  },
  post<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(normalizeApiUrl(url), data, config) as unknown as Promise<T>
  },
  put<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(normalizeApiUrl(url), data, config) as unknown as Promise<T>
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(normalizeApiUrl(url), config) as unknown as Promise<T>
  },
  download(url: string, config?: AxiosRequestConfig): Promise<Blob> {
    return instance.get(normalizeApiUrl(url), { ...config, responseType: 'blob' }) as unknown as Promise<Blob>
  }
}

export default request
