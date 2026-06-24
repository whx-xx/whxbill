import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, '')
  // 本地开发默认走根路径；如果部署在 /admin/ 下，可通过 VITE_APP_BASE=/admin/ 覆盖。
  const appBase = env.VITE_APP_BASE || '/'

  return {
    base: appBase,
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    server: {
      port: 5174,
      strictPort: true,
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
