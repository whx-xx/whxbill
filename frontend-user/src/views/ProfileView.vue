<template>
  <div class="profile-shell">
    <section class="profile-head">
      <div>
        <div class="manage-section-eyebrow">Profile</div>
        <h2>个人资料</h2>
        <p>维护头像、昵称和联系方式，右上角用户信息会同步更新。</p>
      </div>
      <el-button type="primary" :loading="saving" @click="saveProfile()">保存资料</el-button>
    </section>

    <section class="profile-layout">
      <aside class="profile-card">
        <div class="profile-avatar-wrap">
          <el-avatar :size="118" :src="avatarPreviewUrl || undefined">{{ userInitial }}</el-avatar>
          <span class="profile-status-dot"></span>
        </div>
        <h3>{{ form.nickname || authStore.profile?.username || '记账用户' }}</h3>
        <p>{{ authStore.profile?.username || '-' }}</p>
        <div class="profile-role-list">
          <el-tag v-for="role in roleList" :key="role" effect="plain">{{ role }}</el-tag>
        </div>
        <el-upload :show-file-list="false" :auto-upload="false" accept="image/*" :on-change="uploadAvatar">
          <el-button :loading="uploading" style="width: 100%">上传头像</el-button>
        </el-upload>
      </aside>

      <main class="profile-form-card">
        <div class="profile-section-head">
          <div>
            <h3>基础信息</h3>
            <span>昵称为必填项，邮箱和手机号用于后续提醒与通知展示。</span>
          </div>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <div class="profile-form-grid">
            <el-form-item label="用户名">
              <el-input :model-value="authStore.profile?.username || ''" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model.trim="form.nickname" maxlength="24" show-word-limit placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model.trim="form.email" placeholder="name@example.com" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model.trim="form.phone" placeholder="请输入 11 位手机号" />
            </el-form-item>
            <el-form-item label="头像地址" prop="avatarUrl" class="profile-full-row">
              <el-input v-model.trim="form.avatarUrl" placeholder="上传后自动生成，也可以填写已有图片地址" @change="syncAvatarPreview" />
            </el-form-item>
          </div>
        </el-form>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import request from '@/utils/request'
import { buildProxyFileUrl } from '@/utils/fileUrl'

interface ProfileForm {
  nickname: string
  email: string
  phone: string
  avatarUrl: string
}

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const avatarPreviewUrl = ref('')
const saving = ref(false)
const uploading = ref(false)

const form = reactive<ProfileForm>({
  nickname: '',
  email: '',
  phone: '',
  avatarUrl: ''
})

const rules: FormRules<ProfileForm> = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 24, message: '昵称长度为 2-24 个字符', trigger: 'blur' }
  ],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  phone: [{ validator: validatePhone, trigger: 'blur' }]
}

const userInitial = computed(() => (form.nickname || authStore.profile?.username || 'U').slice(0, 1))
const roleList = computed(() => authStore.profile?.roles?.length ? authStore.profile.roles : ['ROLE_USER'])

async function loadProfile() {
  await authStore.loadProfile()
  form.nickname = authStore.profile?.nickname || ''
  form.email = (authStore.profile as any)?.email || ''
  form.phone = (authStore.profile as any)?.phone || ''
  form.avatarUrl = (authStore.profile as any)?.avatarUrl || ''
  syncAvatarPreview()
}

function validateEmail(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) {
    callback()
    return
  }
  callback(/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) ? undefined : new Error('请输入正确的邮箱地址'))
}

function validatePhone(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) {
    callback()
    return
  }
  callback(/^1[3-9]\d{9}$/.test(value) ? undefined : new Error('请输入正确的手机号'))
}

async function uploadAvatar(file: UploadFile) {
  if (!file.raw || uploading.value) return
  if (!file.raw.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file.raw)
    const result = await request.post('/api/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    form.avatarUrl = result.fileUrl
    syncAvatarPreview()
    await saveProfile('头像上传并保存成功')
  } finally {
    uploading.value = false
  }
}

async function saveProfile(successMessage = '资料已保存') {
  if (saving.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const result = await request.post('/api/user/profile', {
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
      avatarUrl: form.avatarUrl.trim()
    })
    authStore.profile = {
      ...(authStore.profile as any),
      ...result,
      accessToken: authStore.accessToken,
      refreshToken: authStore.refreshToken
    }
    localStorage.setItem('whx-user-profile', JSON.stringify(authStore.profile))
    syncAvatarPreview()
    ElMessage.success(successMessage)
  } finally {
    saving.value = false
  }
}

function syncAvatarPreview() {
  avatarPreviewUrl.value = buildProxyFileUrl(form.avatarUrl)
}

onMounted(loadProfile)
</script>

<style scoped lang="stylus">
.profile-shell {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.profile-head,
.profile-card,
.profile-form-card {
  background: #fff;
  border: 0.0625rem solid #e5ebf0;
  border-radius: 0.5rem;
}

.profile-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
}

.profile-head h2,
.profile-section-head h3,
.profile-card h3 {
  margin: 0;
  color: #10242f;
}

.profile-head h2 {
  font-size: 1.5rem;
}

.profile-head p,
.profile-section-head span,
.profile-card p {
  color: #7d8d9a;
  font-size: 0.8125rem;
}

.profile-head p {
  margin: 0.5rem 0 0;
}

.profile-layout {
  display: grid;
  grid-template-columns: 18.75rem minmax(0, 1fr);
  gap: 0.875rem;
  align-items: start;
}

.profile-card {
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  position: sticky;
  top: 5.125rem;
}

.profile-avatar-wrap {
  position: relative;
}

.profile-status-dot {
  position: absolute;
  right: 0.625rem;
  bottom: 0.625rem;
  width: 0.875rem;
  height: 0.875rem;
  border: 0.125rem solid #fff;
  border-radius: 50%;
  background: #22b573;
}

.profile-card p {
  margin: -0.25rem 0 0;
}

.profile-role-list {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  width: 100%;
}

.profile-form-card {
  padding: 1.125rem 1.25rem 1.25rem;
}

.profile-section-head {
  margin-bottom: 1rem;
}

.profile-section-head h3 {
  font-size: 1rem;
}

.profile-section-head span {
  display: inline-block;
  margin-top: 0.375rem;
}

.profile-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 0.875rem;
}

.profile-full-row {
  grid-column: 1 / -1;
}

@media (max-width: 60rem) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .profile-card {
    position: static;
  }
}

@media (max-width: 45rem) {
  .profile-head {
    align-items: stretch;
    flex-direction: column;
  }

  .profile-head .el-button {
    width: 100%;
  }

  .profile-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
