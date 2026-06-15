<template>
  <div class="login-shell">
    <section class="login-brand">
      <div class="login-brand-mark">A</div>
      <h1>WHX Admin</h1>
      <p>面向平台运营和系统管理的后台入口，集中处理用户、角色、字典、公告和操作日志。</p>
    </section>
    <section class="login-card-wrap">
      <div class="login-card">
        <h2>后台登录</h2>
        <p>请输入管理员账号，登录后将根据 RBAC 权限显示可访问功能。</p>
        <el-form :model="form" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
          </el-form-item>
          <el-button type="primary" style="width: 100%" :loading="submitting" @click="submit">登录</el-button>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const submitting = ref(false)
const form = reactive({
  username: 'admin',
  password: 'Admin@123456'
})

async function submit() {
  submitting.value = true
  try {
    await authStore.login(form)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } finally {
    submitting.value = false
  }
}
</script>
