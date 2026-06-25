import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

// 后台管理端接口地址解析，默认同样走 /api 代理到 Spring Boot 后端。
function resolveApiBaseURL() {
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL
  }

  const { protocol, hostname } = window.location
  const userAgent = navigator.userAgent.toLowerCase()
  const isAndroid = userAgent.includes('android')
  const isCapacitor = protocol === 'capacitor:' || protocol === 'ionic:'

  if (isCapacitor || (isAndroid && (hostname === 'localhost' || hostname === '127.0.0.1'))) {
    // Android 模拟器访问电脑本机后端时不能直接用 localhost。
    return 'http://10.0.2.2:8080'
  }

  // 默认由 Vite 或部署网关代理到后端服务。
  return '/api'
}

export const apiBaseURL = resolveApiBaseURL()

// 管理端普通业务请求实例。
const instance = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000
})

// 刷新 token 单独用一个 axios 实例，避免它被普通响应拦截器递归拦截。
const refreshClient = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000
})

let refreshPromise: Promise<boolean> | null = null

// _retry 用来标记请求是否已经重试过，防止 401 后无限循环。
type RetriableRequestConfig = AxiosRequestConfig & {
  _retry?: boolean
}

// 兼容 /api 前缀：baseURL 已包含 /api 时，具体 URL 就去掉 /api。
function normalizeApiUrl(url: string) {
  if (apiBaseURL.replace(/\/$/, '').endsWith('/api') && url.startsWith('/api/')) {
    return url.slice(4)
  }
  return url
}

// 统一跳转异常页。
function routeToException(path: '/403' | '/500') {
  if (router.currentRoute.value.path !== path) {
    router.push(path)
  }
}

// 登录请求失败时不做无感续期，否则用户名密码错误会被误判成 token 过期。
function isLoginRequest(url?: string) {
  return Boolean(url?.includes('/auth/login'))
}

// 刷新请求本身不能再次触发刷新。
function isRefreshRequest(url?: string) {
  return Boolean(url?.includes('/auth/refresh'))
}

// 被禁用账号和登录/刷新接口都跳过 refreshToken 重试。
function shouldSkipRefresh(url?: string, message?: string) {
  return isLoginRequest(url) || isRefreshRequest(url) || message?.includes('账号已被禁用')
}

// 根据 HTTP 状态和后端 message 生成更友好的 Element Plus 提示。
function friendlyErrorMessage(status?: number, message?: string, url?: string) {
  if (status === 401 && isLoginRequest(url)) {
    return '用户名或密码不正确，请检查后再试'
  }
  if (status === 401) {
    return message || '登录状态已失效，请重新登录'
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

// 使用 refreshToken 续期，成功后会更新 Pinia 和 localStorage。
async function refreshSession() {
  const authStore = useAuthStore()
  if (!authStore.refreshToken) return false
  if (!refreshPromise) {
    // 并发 401 只共享一个 refresh 请求，避免多次刷新导致旧 token 被覆盖。
    refreshPromise = refreshClient
      .post(normalizeApiUrl('/api/auth/refresh'), { refreshToken: authStore.refreshToken })
      .then((response) => {
        const payload = response.data
        if (payload?.code !== 200 || !payload.data?.accessToken) {
          return false
        }
        authStore.setSession(payload.data)
        return true
      })
      .catch(() => false)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

// accessToken 过期后的自动重试逻辑。
async function retryWithFreshToken(config?: RetriableRequestConfig, message?: string) {
  if (!config || config._retry || shouldSkipRefresh(config.url, message)) return null
  config._retry = true
  const refreshed = await refreshSession()
  if (!refreshed) return null
  const authStore = useAuthStore()
  config.headers = config.headers || {}
  // refresh 成功后自动重试原请求，后台管理端也保持无感续期。
  ;(config.headers as any).Authorization = `Bearer ${authStore.accessToken}`
  return instance(config)
}

// 续期失败，清空本地登录状态并回到登录页。
function handleAuthExpired(url?: string, message?: string) {
  const authStore = useAuthStore()
  authStore.clearSession()
  if (!isLoginRequest(url) && router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
  ElMessage.error(friendlyErrorMessage(401, message, url))
}

// 每次请求前统一附加 Authorization。
instance.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.accessToken) {
    // 管理端所有接口都从这里统一附加 Authorization。
    config.headers.Authorization = `Bearer ${authStore.accessToken}`
  }
  return config
})

// 每次响应后统一拆包、处理错误码、必要时刷新 token 并重试原请求。
instance.interceptors.response.use(
  async (response) => {
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const payload = response.data
    if (payload.code !== 200) {
      if (payload.code === 401) {
        const retryResult = await retryWithFreshToken(response.config as RetriableRequestConfig, payload.message)
        if (retryResult) return retryResult
        handleAuthExpired(response.config.url, payload.message)
      } else if (payload.code === 403) {
        routeToException('/403')
        ElMessage.error(friendlyErrorMessage(payload.code, payload.message, response.config.url))
      } else if (payload.code >= 500) {
        routeToException('/500')
        ElMessage.error(friendlyErrorMessage(payload.code, payload.message, response.config.url))
      } else {
        ElMessage.error(friendlyErrorMessage(payload.code, payload.message, response.config.url))
      }
      return Promise.reject(payload)
    }
    return payload.data
  },
  async (error) => {
    const status = error.response?.status
    if (status === 401) {
      const message = error.response?.data?.message || error.message
      const retryResult = await retryWithFreshToken(error.config as RetriableRequestConfig, message)
      if (retryResult) return retryResult
      handleAuthExpired(error.config?.url, message)
    } else if (status === 403) {
      routeToException('/403')
      ElMessage.error(friendlyErrorMessage(status, error.response?.data?.message || error.message, error.config?.url))
    } else if (status && status >= 500) {
      routeToException('/500')
      ElMessage.error(friendlyErrorMessage(status, error.response?.data?.message || error.message, error.config?.url))
    } else {
      ElMessage.error(friendlyErrorMessage(status, error.response?.data?.message || error.message, error.config?.url))
    }
    return Promise.reject(error)
  }
)

// 页面层只调用这个 request 对象，不直接感知 Axios 原始结构。
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
