<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div>
        <span class="admin-page-kicker">RBAC</span>
        <h2>角色权限</h2>
        <p>维护角色定义、启停状态和权限树授权。</p>
      </div>
    </div>

    <div class="admin-stats">
      <div class="admin-stat"><div class="admin-stat-label">角色数量</div><div class="admin-stat-value">{{ roles.length }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">启用角色</div><div class="admin-stat-value">{{ roles.filter((item) => item.status === 1).length }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">权限点</div><div class="admin-stat-value">{{ permissions.length }}</div></div>
    </div>

    <div class="admin-card">
      <div class="admin-filter-bar">
        <el-input v-model="filters.keyword" placeholder="搜索角色编码 / 名称" clearable />
        <el-select v-model="filters.status" clearable placeholder="筛选状态">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <div class="admin-toolbar-right">
          <el-button @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </div>
    </div>

    <div class="admin-card admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <h3 class="admin-card-title">角色列表</h3>
          <span class="admin-muted">当前 {{ filteredRoles.length }} 条结果</span>
        </div>
        <div class="admin-toolbar-right">
          <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
        </div>
      </div>
      <el-table :data="pagedRoles" stripe height="520">
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" inline-prompt @change="quickSaveRole(row)" />
          </template>
        </el-table-column>
        <el-table-column label="权限" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ permissionSummary(row.id) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeRole(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="admin-table-footer">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :total="filteredRoles.length" />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="47.5rem">
      <el-form :model="form" label-position="top">
        <div class="admin-form-grid">
          <el-form-item label="角色编码"><el-input v-model="form.roleCode" placeholder="例如 ROLE_ADMIN" /></el-form-item>
          <el-form-item label="角色名称"><el-input v-model="form.roleName" placeholder="请输入角色名称" /></el-form-item>
        </div>
        <el-form-item label="角色说明"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限分配">
          <el-tree ref="permissionTreeRef" node-key="id" show-checkbox default-expand-all style="width: 100%; border: 0.0625rem solid #e6ecef; border-radius: 0.5rem; padding: 0.75rem" :data="permissionTree" :props="{ label: 'label', children: 'children' }" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import request from '@/utils/request'

const roles = ref<any[]>([])
const permissions = ref<any[]>([])
const rolePermissions = ref<any[]>([])
const permissionTree = ref<any[]>([])
const permissionTreeRef = ref<InstanceType<typeof ElTree>>()
const dialogVisible = ref(false)
const filters = reactive({ keyword: '', status: undefined as number | undefined })
const pagination = reactive({ page: 1, pageSize: 10 })
const emptyForm = () => ({ id: undefined as number | undefined, roleCode: '', roleName: '', description: '', status: 1 })
const form = reactive(emptyForm())

const filteredRoles = computed(() => roles.value.filter((item) => {
  const keyword = filters.keyword.trim().toLowerCase()
  const matchKeyword = !keyword || item.roleCode?.toLowerCase().includes(keyword) || item.roleName?.toLowerCase().includes(keyword)
  const matchStatus = filters.status === undefined || item.status === filters.status
  return matchKeyword && matchStatus
}))
const pagedRoles = computed(() => filteredRoles.value.slice((pagination.page - 1) * pagination.pageSize, pagination.page * pagination.pageSize))

async function loadData() {
  const [roleList, permissionList, rolePermissionList] = await Promise.all([request.get('/api/admin/roles'), request.get('/api/admin/permissions'), request.get('/api/admin/role-permissions')])
  roles.value = roleList
  permissions.value = permissionList
  rolePermissions.value = rolePermissionList
  permissionTree.value = buildPermissionTree(permissionList)
}
function buildPermissionTree(items: any[]) {
  const map = new Map<number, any>()
  const roots: any[] = []
  items.forEach((item) => map.set(item.id, { id: item.id, label: `${item.permissionName} (${item.permissionCode})`, children: [] }))
  items.forEach((item) => {
    const node = map.get(item.id)
    if (item.parentId && map.has(item.parentId)) map.get(item.parentId).children.push(node)
    else roots.push(node)
  })
  return roots
}
function resetFilters() { filters.keyword = ''; filters.status = undefined }
function resetForm() { Object.assign(form, emptyForm()) }
function permissionSummary(roleId: number) { return rolePermissions.value.find((item: any) => item.roleId === roleId)?.permissions?.map((item: any) => item.permissionName).join('、') || '-' }
function openCreate() { resetForm(); dialogVisible.value = true; nextTick(() => permissionTreeRef.value?.setCheckedKeys([])) }
function openEdit(row: any) {
  resetForm()
  Object.assign(form, row)
  dialogVisible.value = true
  const current = rolePermissions.value.find((item: any) => item.roleId === row.id)
  nextTick(() => permissionTreeRef.value?.setCheckedKeys(current?.permissions?.map((item: any) => item.id) || []))
}
async function saveRole() {
  const checkedKeys = (permissionTreeRef.value?.getCheckedKeys(false) || []) as number[]
  if (form.id) await request.put(`/api/admin/roles/${form.id}`, { ...form, permissionIds: checkedKeys })
  else await request.post('/api/admin/roles', { ...form, permissionIds: checkedKeys })
  ElMessage.success('角色已保存')
  dialogVisible.value = false
  await loadData()
}
async function quickSaveRole(row: any) { await request.put(`/api/admin/roles/${row.id}`, { ...row, permissionIds: rolePermissions.value.find((item: any) => item.roleId === row.id)?.permissions?.map((item: any) => item.id) || [] }); ElMessage.success('状态已更新'); await loadData() }
async function removeRole(roleId: number) {
  await ElMessageBox.confirm('删除角色后将移除其权限关联，是否继续？', '删除角色', { type: 'warning' })
  await request.delete(`/api/admin/roles/${roleId}`)
  ElMessage.success('角色已删除')
  await loadData()
}
watch(filters, () => { pagination.page = 1 })
onMounted(loadData)
</script>
