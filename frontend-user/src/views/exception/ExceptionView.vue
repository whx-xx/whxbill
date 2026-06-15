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
    kicker: '访问受限',
    title: '当前页面没有访问权限',
    description: '你的账号暂未开通这项能力，可以返回首页继续使用其他功能，或联系管理员调整权限。',
    primaryText: '返回首页',
    homePath: '/bills'
  },
  '404': {
    kicker: '页面不存在',
    title: '这个页面似乎走丢了',
    description: '访问地址可能已经变更，或者链接已失效。你可以回到常用页面继续处理账单与预算。',
    primaryText: '回到账单',
    homePath: '/bills'
  },
  '500': {
    kicker: '服务异常',
    title: '系统刚刚遇到了一点问题',
    description: '我们已经拦下了这次错误。你可以稍后重试，或者先返回首页继续查看其他数据。',
    primaryText: '返回首页',
    homePath: '/bills'
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
