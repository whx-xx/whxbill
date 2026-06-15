import { apiBaseURL } from '@/utils/request'

export function buildProxyFileUrl(fileUrl?: string) {
  if (!fileUrl) return ''
  if (/^https?:\/\//i.test(fileUrl) || fileUrl.startsWith('data:')) return fileUrl
  return `${apiBaseURL}/api/files/content?fileUrl=${encodeURIComponent(fileUrl)}`
}
