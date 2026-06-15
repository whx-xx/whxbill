<template>
  <div class="login-shell">
    <section class="login-brand">
      <h1>WHX Bill</h1>
      <p>用更统一的品牌色把个人记账、预算提醒、OCR 识别和公告协作放在同一套体验里，保持柔和但清晰的专业感。</p>
    </section>
    <section class="login-card-wrap">
      <div class="login-card">
        <h2>{{ isRegisterMode ? '创建 WHX Bill 账号' : '欢迎使用 WHX Bill' }}</h2>
        <p style="color:var(--muted)">{{ isRegisterMode ? '注册后会自动创建默认账本，并直接进入用户端。' : '参考一木记账的前后端分离记账系统' }}</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="onSubmit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" />
          </el-form-item>
          <el-form-item v-if="isRegisterMode" label="昵称" prop="nickname">
            <el-input v-model="form.nickname" />
          </el-form-item>
          <el-form-item v-if="isRegisterMode" label="邮箱" prop="email">
            <el-input v-model="form.email" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password />
          </el-form-item>
          <el-form-item v-if="isRegisterMode" label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" show-password />
          </el-form-item>
          <el-button type="primary" style="width:100%" :loading="loading" @click="onSubmit">
            {{ isRegisterMode ? '注册并进入' : '登录' }}
          </el-button>
        </el-form>
        <div style="margin-top:16px;text-align:center">
          <el-button link type="primary" @click="toggleMode">
            {{ isRegisterMode ? '已有账号，去登录' : '没有账号，立即注册' }}
          </el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const isRegisterMode = ref(false)
const form = reactive({
  username: 'demo',
  nickname: '',
  email: '',
  password: 'Demo@123456',
  confirmPassword: ''
})
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isRegisterMode.value || /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(value)) callback()
        else callback(new Error('密码需为8到20位并同时包含字母和数字'))
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isRegisterMode.value || value === form.password) callback()
        else callback(new Error('两次输入的密码不一致'))
      },
      trigger: 'blur'
    }
  ]
}

function toggleMode() {
  isRegisterMode.value = !isRegisterMode.value
  form.username = isRegisterMode.value ? '' : 'demo'
  form.nickname = ''
  form.email = ''
  form.password = isRegisterMode.value ? '' : 'Demo@123456'
  form.confirmPassword = ''
  formRef.value?.clearValidate()
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || loading.value) return
  loading.value = true
  try {
    if (isRegisterMode.value) {
      await authStore.register(form)
      ElMessage.success('注册成功')
    } else {
      await authStore.login(form)
      ElMessage.success('登录成功')
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/bills'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>
