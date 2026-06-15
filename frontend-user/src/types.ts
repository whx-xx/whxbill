export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface LoginPayload {
  userId: number
  username: string
  nickname: string
  email?: string
  phone?: string
  avatarUrl?: string
  accessToken: string
  refreshToken: string
  roles: string[]
  permissions: string[]
}
