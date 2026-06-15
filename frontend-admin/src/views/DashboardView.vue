<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div>
        <span class="admin-page-kicker">运营工作台</span>
        <h2>平台运行概览</h2>
        <p>集中查看用户、公告、权限、字典和日志相关的关键运营状态。</p>
      </div>
      <div class="admin-actions">
        <el-button :icon="Refresh" @click="loadOverview">刷新数据</el-button>
        <el-button :icon="User" @click="$router.push('/users')">用户管理</el-button>
        <el-button type="primary" :icon="Notebook" @click="$router.push('/notices')">发布公告</el-button>
      </div>
    </div>

    <div class="admin-stats">
      <div class="admin-stat" v-for="card in cards" :key="card.label">
        <div class="admin-stat-label">{{ card.label }}</div>
        <div class="admin-stat-value">{{ card.value }}</div>
      </div>
    </div>

    <div class="admin-split">
      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <h3 class="admin-card-title">近 7 日平台趋势</h3>
            <div class="admin-muted">活跃用户与新增账单趋势</div>
          </div>
          <el-segmented v-model="chartMode" :options="['活跃', '账单']" size="small" />
        </div>
        <div class="admin-card-inner">
          <v-chart :option="chartOption" autoresize style="height: 360px" />
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <h3 class="admin-card-title">运营快捷入口</h3>
            <div class="admin-muted">常用管理任务</div>
          </div>
        </div>
        <div class="admin-card-inner admin-todo-list">
          <button v-for="todo in todos" :key="todo.title" class="admin-todo admin-todo-button" type="button" @click="$router.push(todo.path)">
            <div class="admin-todo-row">
              <strong>{{ todo.title }}</strong>
              <el-tag size="small" :type="todo.type">{{ todo.badge }}</el-tag>
            </div>
            <p class="admin-muted" style="margin: 8px 0 0">{{ todo.desc }}</p>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import VChart from 'vue-echarts'
import { Notebook, Refresh, User } from '@element-plus/icons-vue'

const chartMode = ref('活跃')
const cards = ref([
  { label: '注册用户', value: 128 },
  { label: '本月账单', value: 2460 },
  { label: '公告推送', value: 18 },
  { label: '预算预警', value: 12 }
])

const todos = [
  { title: '维护用户角色', badge: '用户', type: 'success', path: '/users', desc: '查看用户资料并调整角色授权。' },
  { title: '复核角色权限', badge: 'RBAC', type: 'warning', path: '/roles', desc: '检查权限树授权范围，避免越权。' },
  { title: '查看审计日志', badge: '日志', type: 'danger', path: '/logs', desc: '追踪后台关键操作和异常行为。' }
]

const chartOption = computed(() => ({
  color: chartMode.value === '活跃' ? ['#26A69A'] : ['#80CBC4'],
  tooltip: { trigger: 'axis' },
  grid: { left: 36, right: 24, top: 32, bottom: 28 },
  xAxis: {
    type: 'category',
    data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    axisLine: { lineStyle: { color: '#9ab6b4' } }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: 'rgba(64,141,134,0.12)' } }
  },
  series: [
    chartMode.value === '活跃'
      ? { name: '活跃用户', type: 'line', smooth: true, data: [30, 48, 62, 77, 71, 54, 45], areaStyle: { color: 'rgba(38,166,154,0.12)' } }
      : { name: '新增账单', type: 'bar', barMaxWidth: 24, data: [15, 28, 36, 41, 44, 32, 27] }
  ]
}))

function loadOverview() {
  cards.value = [...cards.value]
}
</script>
