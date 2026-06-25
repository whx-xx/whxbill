import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

// 计算接口基础地址：Web 端默认走 /api 代理，移动端模拟器需要特殊地址。
function resolveApiBaseURL() {
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL
  }

  const { protocol, hostname } = window.location
  const userAgent = navigator.userAgent.toLowerCase()
  const isAndroid = userAgent.includes('android')
  const isCapacitor = protocol === 'capacitor:' || protocol === 'ionic:'

  if (isCapacitor || (isAndroid && (hostname === 'localhost' || hostname === '127.0.0.1'))) {
    // Android 模拟器访问宿主机 localhost 要使用 10.0.2.2。
    return 'http://10.0.2.2:8080'
  }

  // Web 开发环境走 Vite 代理，生产环境也可以由同域网关转发 /api。
  return '/api'
}

export const apiBaseURL = resolveApiBaseURL()

// 普通业务请求用，比如查账单、查分类、查统计
const instance = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000
})

// 专门刷新 token 用
const refreshClient = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000
})

let refreshPromise: Promise<boolean> | null = null

// 给 Axios 请求配置额外加一个 _retry 标记，避免同一个请求无限刷新重试。
type RetriableRequestConfig = AxiosRequestConfig & {
  _retry?: boolean
}

// 如果 baseURL 已经是 /api，传入 /api/auth/login 时要去掉重复前缀。
function normalizeApiUrl(url: string) {
  if (apiBaseURL.replace(/\/$/, '').endsWith('/api') && url.startsWith('/api/')) {
    return url.slice(4)
  }
  return url
}

// 权限和服务错误跳到统一异常页，页面上给用户更明确的反馈。
function routeToException(path: '/403' | '/500') {
  if (router.currentRoute.value.path !== path) {
    router.push(path)
  }
}

// 判断当前请求是不是登录接口，登录失败不应该触发 refreshToken 逻辑。
function isLoginRequest(url?: string) {
  return Boolean(url?.includes('/auth/login'))
}

// 判断当前请求是不是刷新接口，刷新失败也不能再递归刷新。
function isRefreshRequest(url?: string) {
  return Boolean(url?.includes('/auth/refresh'))
}

// 这些场景不做无感续期，直接进入登录失效或错误提示逻辑。
function shouldSkipRefresh(url?: string, message?: string) {
  return isLoginRequest(url) || isRefreshRequest(url) || message?.includes('账号已被禁用')
}

// 把后端状态码和错误信息转换成前端用户能看懂的提示。
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

// 使用 refreshToken 向后端换一组新的 accessToken/refreshToken。
async function refreshSession() {
  const authStore = useAuthStore()
  if (!authStore.refreshToken) return false
  if (!refreshPromise) { // 多个接口同时 401 时，只发起一个 refresh 请求，避免重复续期。
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

// 决定要不要刷新
async function retryWithFreshToken(config?: RetriableRequestConfig, message?: string) {
  if (!config || config._retry || shouldSkipRefresh(config.url, message)) return null
  config._retry = true
  const refreshed = await refreshSession()
  if (!refreshed) return null
  const authStore = useAuthStore()
  config.headers = config.headers || {}
  // 续期成功后，用新的 accessToken 重放原请求，用户无感知。
  ;(config.headers as any).Authorization = `Bearer ${authStore.accessToken}`
  return instance(config)
}

// refreshToken 也失效时，清除本地登录态并跳回登录页。
function handleAuthExpired(url?: string, message?: string) {
  const authStore = useAuthStore()
  authStore.clearSession()
  if (!isLoginRequest(url) && router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
  ElMessage.error(friendlyErrorMessage(401, message, url))
}

// 请求拦截器：每个业务请求发出前自动带上 accessToken。
instance.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.accessToken) {
    // 所有受保护接口统一在请求头携带 JWT。
    config.headers.Authorization = `Bearer ${authStore.accessToken}`
  }
  return config
})

// 响应拦截器：统一处理后端 ApiResponse 包装、401 续期、403/500 异常页和 blob 下载。
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

// 对外暴露简化版 request，页面里不用直接接触 axios 的 response.data.code 结构。
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
