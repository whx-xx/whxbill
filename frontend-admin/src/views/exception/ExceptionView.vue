<template>
  <div class="exception-shell">
    <div class="exception-card">
      <div class="exception-mark">{{ code }}</div>
      <div class="exception-copy">
        <span class="exception-kicker">{{ kicker }}</span>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <div class="exception-actions">
        <el-button plain @click="goBack">返回上一页</el-button>
        <el-button type="primary" @click="goHome">{{ primaryText }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps<{
  code: string
}>()

const router = useRouter()

const pageMap: Record<string, { kicker: string; title: string; description: string; primaryText: string; homePath: string }> = {
  '403': {
    kicker: '权限不足',
    title: '当前账号无法访问该管理页面',
    description: '这通常是角色权限未开放导致的。你可以先返回管理首页，或联系超级管理员配置 RBAC 权限。',
    primaryText: '返回控制台',
    homePath: '/'
  },
  '404': {
    kicker: '页面不存在',
    title: '这个管理页面没有找到',
    description: '链接可能已调整，或者页面尚未启用。返回控制台后可以继续处理用户、角色和公告等内容。',
    primaryText: '返回控制台',
    homePath: '/'
  },
  '500': {
    kicker: '服务异常',
    title: '后台服务暂时不可用',
    description: '请求已经被统一异常处理器接管。你可以稍后重试，或者先回到控制台查看其他模块。',
    primaryText: '返回控制台',
    homePath: '/'
  }
}

const pageConfig = computed(() => pageMap[props.code] ?? pageMap['404'])
const kicker = computed(() => pageConfig.value.kicker)
const title = computed(() => pageConfig.value.title)
const description = computed(() => pageConfig.value.description)
const primaryText = computed(() => pageConfig.value.primaryText)

function goHome() {
  router.push(pageConfig.value.homePath)
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  goHome()
}
</script>
