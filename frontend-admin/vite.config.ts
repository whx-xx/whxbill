import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, '')
  // 管理端如果部署在 http://域名/admin/ 下，打包时必须让 base 变成 /admin/，
  // 这样生成出来的 js/css 资源路径才会带上 /admin/ 前缀，不然页面会白屏。
  //
  // 常见用法：
  // 1. 本地开发：Vite 自带 dev server，通常保持 VITE_APP_BASE=/ 即可。
  // 2. 服务器部署到 /admin/：设置 VITE_APP_BASE=/admin/，或者直接使用下面这个默认值。
  const appBase = env.VITE_APP_BASE || '/admin/'

  return {
    base: '/',
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    server: {
      port: 5174,
      proxy: {
        '/api': {
          target: env.VITE_DEV_API_TARGET || 'http://localhost:8080',
          changeOrigin: true
        },
        '/ws': {
          target: env.VITE_DEV_API_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          ws: true
        }
      }
    }
  }
})
