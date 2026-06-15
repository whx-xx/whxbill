<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div>
        <span class="admin-page-kicker">账号中心</span>
        <h2>用户管理</h2>
        <p>维护用户资料、角色绑定、启停状态和导出操作。</p>
      </div>
    </div>

    <div class="admin-stats">
      <div class="admin-stat"><div class="admin-stat-label">用户总数</div><div class="admin-stat-value">{{ users.length }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">启用账号</div><div class="admin-stat-value">{{ enabledCount }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">管理员账号</div><div class="admin-stat-value">{{ adminCount }}</div></div>
    </div>

    <div class="admin-card">
      <div class="admin-filter-bar">
        <el-input v-model="filters.keyword" placeholder="搜索用户名 / 昵称 / 邮箱" clearable />
        <el-select v-model="filters.roleId" clearable placeholder="筛选角色">
          <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="筛选状态">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <div class="admin-toolbar-right">
          <el-button @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" @click="loadUsers">刷新</el-button>
        </div>
      </div>
    </div>

    <div class="admin-card admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <h3 class="admin-card-title">账号列表</h3>
          <span class="admin-muted">当前 {{ filteredUsers.length }} 条结果</span>
        </div>
        <div class="admin-toolbar-right">
          <el-button :icon="Download" @click="exportUsers">导出 Excel</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
        </div>
      </div>
      <el-table :data="pagedUsers" stripe height="520">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">{{ (row.roleNames || []).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
              inline-prompt
              @change="quickSaveUser(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeUser(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="admin-table-footer">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="filteredUsers.length"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="640px">
      <el-form :model="form" label-position="top">
        <div class="admin-form-grid">
          <el-form-item label="用户名"><el-input v-model="form.username" :disabled="Boolean(form.id)" placeholder="请输入用户名" /></el-form-item>
          <el-form-item label="昵称"><el-input v-model="form.nickname" placeholder="请输入昵称" /></el-form-item>
        </div>
        <div class="admin-form-grid">
          <el-form-item :label="form.id ? '重置密码（留空则不修改）' : '初始密码'">
            <el-input v-model="form.password" type="password" show-password />
          </el-form-item>
          <el-form-item :label="form.id ? '确认重置密码' : '确认初始密码'">
            <el-input v-model="form.confirmPassword" type="password" show-password />
          </el-form-item>
        </div>
        <div class="admin-form-grid">
          <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        </div>
        <el-form-item label="头像地址"><el-input v-model="form.avatarUrl" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
        <div class="admin-form-grid">
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="用户类型">
            <el-select v-model="form.userType" style="width: 100%">
              <el-option label="普通用户" :value="1" />
              <el-option label="管理员" :value="2" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { apiBaseURL } from '@/utils/request'
import request from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

const users = ref<any[]>([])
const roles = ref<any[]>([])
const dialogVisible = ref(false)
const authStore = useAuthStore()
const filters = reactive({ keyword: '', roleId: undefined as number | undefined, status: undefined as number | undefined })
const pagination = reactive({ page: 1, pageSize: 10 })
const emptyForm = () => ({ id: undefined as number | undefined, username: '', nickname: '', password: '', confirmPassword: '', email: '', phone: '', avatarUrl: '', status: 1, userType: 1, roleIds: [] as number[] })
const form = reactive(emptyForm())

const enabledCount = computed(() => users.value.filter((item) => item.status === 1).length)
const adminCount = computed(() => users.value.filter((item) => item.userType === 2).length)
const filteredUsers = computed(() => users.value.filter((item) => {
  const keyword = filters.keyword.trim().toLowerCase()
  const matchKeyword = !keyword || item.username?.toLowerCase().includes(keyword) || item.nickname?.toLowerCase().includes(keyword) || item.email?.toLowerCase().includes(keyword)
  const matchRole = !filters.roleId || item.roleIds?.includes(filters.roleId)
  const matchStatus = filters.status === undefined || item.status === filters.status
  return matchKeyword && matchRole && matchStatus
}))
const pagedUsers = computed(() => filteredUsers.value.slice((pagination.page - 1) * pagination.pageSize, pagination.page * pagination.pageSize))

async function loadUsers() { users.value = await request.get('/api/admin/users') }
async function loadRoles() { roles.value = await request.get('/api/admin/roles') }
function resetFilters() { filters.keyword = ''; filters.roleId = undefined; filters.status = undefined }
function resetForm() { Object.assign(form, emptyForm()) }
function openCreate() { resetForm(); dialogVisible.value = true }
function openEdit(row: any) { Object.assign(form, { ...emptyForm(), ...row, roleIds: [...(row.roleIds || [])], password: '', confirmPassword: '' }); dialogVisible.value = true }
function validateUserForm() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return false
  }
  if (!form.nickname.trim()) {
    ElMessage.warning('请输入昵称')
    return false
  }
  const password = form.password.trim()
  const confirmPassword = form.confirmPassword.trim()
  if (!form.id && !password) {
    ElMessage.warning('请输入初始密码')
    return false
  }
  if ((password || confirmPassword) && password !== confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return false
  }
  if (password && !/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(password)) {
    ElMessage.warning('密码需为8到20位并同时包含字母和数字')
    return false
  }
  return true
}
async function saveUser() {
  if (!validateUserForm()) return
  await request.post('/api/admin/users', form)
  ElMessage.success('用户已保存')
  dialogVisible.value = false
  await loadUsers()
}
async function quickSaveUser(row: any) { await request.post('/api/admin/users', { ...row, password: '', confirmPassword: '' }); ElMessage.success('状态已更新'); await loadUsers() }
async function removeUser(userId: number) {
  await ElMessageBox.confirm('删除后将无法恢复，是否继续？', '删除用户', { type: 'warning' })
  await request.delete(`/api/admin/users/${userId}`)
  ElMessage.success('用户已删除')
  await loadUsers()
}
async function exportUsers() {
  const response = await fetch(`${apiBaseURL}/api/admin/users/export`, { headers: { Authorization: `Bearer ${authStore.accessToken}` } })
  const blob = await response.blob()
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'users.xlsx'
  link.click()
  window.URL.revokeObjectURL(url)
}

watch(filters, () => { pagination.page = 1 })
onMounted(async () => { await Promise.all([loadUsers(), loadRoles()]) })
</script>
