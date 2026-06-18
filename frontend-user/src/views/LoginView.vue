<template>
  <main class="auth-page">
    <img class="auth-page-reveal-image" :src="authRevealBg" alt="" />
    <canvas ref="revealCanvasRef" class="auth-page-reveal-canvas"></canvas>
    <section class="auth-panel">
      <div class="auth-visual" aria-hidden="true">
        <canvas ref="canvasRef" class="auth-canvas"></canvas>
        <div class="auth-brand">
          <div class="auth-logo">W</div>
          <h1>WHX Bill</h1>
          <p>把每一笔收支连接成清晰的生活账本</p>
        </div>
      </div>

      <div class="auth-form-area">
        <div class="auth-form-card" :class="{ 'is-register': isRegisterMode }">
          <div class="auth-form-head">
            <span>{{ isRegisterMode ? '创建账号' : '欢迎回来' }}</span>
            <h2>{{ isRegisterMode ? '注册 WHX Bill' : '登录 WHX Bill' }}</h2>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="onSubmit">
            <el-form-item label="用户名" prop="username">
              <el-input v-model.trim="form.username" placeholder="请输入用户名" size="large" />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                show-password
                size="large"
                @keyup.enter="onSubmit"
              />
            </el-form-item>

            <el-form-item v-if="isRegisterMode" label="确认密码" prop="confirmPassword">
              <div
                class="confirm-assist"
                :class="{ 'is-match': passwordsMatch, 'is-shaking': confirmShake, 'is-empty': !form.confirmPassword }"
              >
                <div class="confirm-guide" aria-hidden="true">
                  <span
                    v-for="(_, index) in passwordSlots"
                    :key="index"
                    class="confirm-guide-cell"
                    :class="confirmCellClass(index)"
                  >
                    <i></i>
                  </span>
                  <span
                    v-if="confirmFieldFocused"
                    class="confirm-caret"
                    :style="{ left: `${confirmCaretOffset}rem` }"
                  ></span>
                </div>
                <div class="confirm-input-shell">
                  <input
                    ref="confirmInputRef"
                    class="confirm-input"
                    type="password"
                    :value="form.confirmPassword"
                    placeholder="请再次输入密码"
                    autocomplete="new-password"
                    @input="onConfirmPasswordInput"
                    @focus="onConfirmFocus"
                    @blur="onConfirmBlur"
                    @keyup.enter="onSubmit"
                  />
                </div>
              </div>
            </el-form-item>

            <el-button class="auth-submit" type="primary" size="large" :loading="loading" @click="onSubmit">
              {{ isRegisterMode ? '注册并进入' : '登录' }}
            </el-button>
          </el-form>

          <button class="auth-switch" type="button" @click="toggleMode">
            {{ isRegisterMode ? '已有账号，去登录' : '没有账号，立即注册' }}
          </button>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import authRevealBg from '@/assets/auth-reveal-bg.jpg'

type RoutePoint = {
  x: number
  y: number
  delay: number
}

type InkStamp = {
  x: number
  y: number
  born: number
  seed: number
  radius: number
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const canvasRef = ref<HTMLCanvasElement>()
const revealCanvasRef = ref<HTMLCanvasElement>()
const confirmInputRef = ref<HTMLInputElement>()
const loading = ref(false)
const isRegisterMode = ref(false)
const form = reactive({
  username: 'demo',
  password: 'Demo@123456',
  confirmPassword: ''
})
const confirmShake = ref(false)
const confirmFieldFocused = ref(false)
const passwordSlots = computed(() => form.password.split(''))
const passwordsMatch = computed(() => Boolean(form.password) && form.confirmPassword === form.password)
const confirmCaretOffset = computed(() => {
  const currentLength = Math.min(form.confirmPassword.length, form.password.length)
  return currentLength * 1.125
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isRegisterMode.value || /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(value)) {
          callback()
        } else {
          callback(new Error('密码需为8到20位并同时包含字母和数字'))
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isRegisterMode.value || value === form.password) {
          callback()
        } else {
          callback(new Error('两次输入的密码不一致'))
        }
      },
      trigger: 'blur'
    }
  ]
}

let ledgerAnimationFrame = 0
let revealAnimationFrame = 0
let ledgerResizeObserver: ResizeObserver | null = null
let revealResizeObserver: ResizeObserver | null = null
let removeRevealListeners: (() => void) | undefined

function toggleMode() {
  isRegisterMode.value = !isRegisterMode.value
  form.username = isRegisterMode.value ? '' : 'demo'
  form.password = isRegisterMode.value ? '' : 'Demo@123456'
  form.confirmPassword = ''
  formRef.value?.clearValidate()
}

function confirmCellClass(index: number) {
  if (!form.confirmPassword[index]) return ''
  return form.confirmPassword[index] === form.password[index] ? 'is-right' : 'is-wrong'
}

function keepConfirmCaretAtEnd(input = confirmInputRef.value) {
  nextTick(() => {
    const length = input?.value.length ?? 0
    input?.setSelectionRange(length, length)
  })
}

function onConfirmFocus() {
  confirmFieldFocused.value = true
  keepConfirmCaretAtEnd()
}

function onConfirmBlur() {
  confirmFieldFocused.value = false
  formRef.value?.validateField('confirmPassword')
}

function onConfirmPasswordInput(event: Event) {
  const input = event.target as HTMLInputElement
  const nextValue = input.value
  const maxLength = form.password.length
  if (maxLength && nextValue.length > form.confirmPassword.length && nextValue.length > maxLength) {
    confirmShake.value = false
    requestAnimationFrame(() => {
      confirmShake.value = true
      window.setTimeout(() => {
        confirmShake.value = false
      }, 480)
    })
    input.value = form.confirmPassword
    keepConfirmCaretAtEnd(input)
    return
  }
  form.confirmPassword = nextValue
  keepConfirmCaretAtEnd(input)
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || loading.value) return
  loading.value = true
  try {
    if (isRegisterMode.value) {
      await authStore.register({
        username: form.username,
        nickname: form.username,
        password: form.password,
        confirmPassword: form.confirmPassword
      })
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

function drawLedgerMap(canvas: HTMLCanvasElement) {
  const context = canvas.getContext('2d')
  const parent = canvas.parentElement
  if (!context || !parent) return
  const ctx = context

  const ratio = window.devicePixelRatio || 1
  const rect = parent.getBoundingClientRect()
  const width = Math.max(1, rect.width)
  const height = Math.max(1, rect.height)
  canvas.width = width * ratio
  canvas.height = height * ratio
  canvas.style.width = `${width / 16}rem`
  canvas.style.height = `${height / 16}rem`
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0)

  const routes = buildRoutes(width, height)
  let startTime = Date.now()

  function animate() {
    const elapsed = (Date.now() - startTime) / 1000
    if (elapsed > 16) startTime = Date.now()

    ctx.clearRect(0, 0, width, height)
    drawSoftGrid(ctx, width, height, elapsed)
    drawLedgerCard(ctx, width, height, elapsed)
    drawFlowRoutes(ctx, routes, elapsed)

    ledgerAnimationFrame = requestAnimationFrame(animate)
  }

  cancelAnimationFrame(ledgerAnimationFrame)
  animate()
}

function drawSoftGrid(context: CanvasRenderingContext2D, width: number, height: number, elapsed: number) {
  const glow = context.createRadialGradient(width * 0.5, height * 0.48, 0, width * 0.5, height * 0.48, width * 0.44)
  glow.addColorStop(0, 'rgba(38, 166, 154, 0.18)')
  glow.addColorStop(0.58, 'rgba(38, 166, 154, 0.06)')
  glow.addColorStop(1, 'rgba(38, 166, 154, 0)')
  context.fillStyle = glow
  context.fillRect(0, 0, width, height)

  context.save()
  context.strokeStyle = 'rgba(64, 141, 134, 0.08)'
  context.lineWidth = 1
  const gap = Math.max(18, Math.min(width, height) * 0.055)
  const shift = (elapsed * 5) % gap
  for (let x = -gap + shift; x < width + gap; x += gap) {
    context.beginPath()
    context.moveTo(x, height * 0.16)
    context.lineTo(x + height * 0.12, height * 0.84)
    context.stroke()
  }
  for (let y = height * 0.18; y < height * 0.86; y += gap) {
    context.beginPath()
    context.moveTo(width * 0.12, y)
    context.lineTo(width * 0.88, y - width * 0.04)
    context.stroke()
  }
  context.restore()
}

function drawLedgerCard(context: CanvasRenderingContext2D, width: number, height: number, elapsed: number) {
  const cardWidth = Math.min(width * 0.58, 19.5 * 16)
  const cardHeight = Math.min(height * 0.46, 14.5 * 16)
  const x = (width - cardWidth) / 2
  const y = height * 0.48 - cardHeight / 2
  const pulse = Math.sin(elapsed * 1.4) * 0.5 + 0.5

  context.save()
  context.shadowColor = 'rgba(38, 166, 154, 0.18)'
  context.shadowBlur = 26
  context.shadowOffsetY = 14
  roundedRect(context, x, y, cardWidth, cardHeight, 18)
  context.fillStyle = 'rgba(255, 255, 255, 0.72)'
  context.fill()
  context.shadowColor = 'transparent'
  context.strokeStyle = 'rgba(38, 166, 154, 0.28)'
  context.lineWidth = 1
  context.stroke()

  context.fillStyle = '#26a69a'
  roundedRect(context, x + cardWidth * 0.1, y + cardHeight * 0.16, cardWidth * 0.34, 8, 4)
  context.fill()
  context.fillStyle = 'rgba(64, 141, 134, 0.22)'
  roundedRect(context, x + cardWidth * 0.1, y + cardHeight * 0.25, cardWidth * 0.54, 6, 3)
  context.fill()

  const bars = [0.46, 0.68, 0.4, 0.82, 0.58]
  bars.forEach((bar, index) => {
    const barWidth = cardWidth * 0.07
    const barHeight = cardHeight * 0.32 * (bar + pulse * 0.05)
    const barX = x + cardWidth * (0.12 + index * 0.1)
    const barY = y + cardHeight * 0.78 - barHeight
    const gradient = context.createLinearGradient(0, barY, 0, barY + barHeight)
    gradient.addColorStop(0, '#26a69a')
    gradient.addColorStop(1, 'rgba(64, 141, 134, 0.35)')
    roundedRect(context, barX, barY, barWidth, barHeight, 5)
    context.fillStyle = gradient
    context.fill()
  })

  const chartPoints = [
    [x + cardWidth * 0.58, y + cardHeight * 0.7],
    [x + cardWidth * 0.66, y + cardHeight * 0.58],
    [x + cardWidth * 0.74, y + cardHeight * 0.64],
    [x + cardWidth * 0.83, y + cardHeight * 0.42],
    [x + cardWidth * 0.9, y + cardHeight * 0.5]
  ]
  context.beginPath()
  chartPoints.forEach(([pointX, pointY], index) => {
    if (index === 0) context.moveTo(pointX, pointY)
    else context.lineTo(pointX, pointY)
  })
  context.strokeStyle = '#169980'
  context.lineWidth = 2.4
  context.stroke()
  chartPoints.forEach(([pointX, pointY]) => drawPoint(context, pointX, pointY, '#169980', 3.4))
  context.restore()
}

function buildRoutes(width: number, height: number) {
  return [
    {
      start: { x: width * 0.08, y: height * 0.3, delay: 0 },
      controlA: { x: width * 0.26, y: height * 0.16, delay: 0 },
      controlB: { x: width * 0.42, y: height * 0.34, delay: 0 },
      end: { x: width * 0.56, y: height * 0.28, delay: 0 },
      duration: 4.8,
      color: '#169980'
    },
    {
      start: { x: width * 0.14, y: height * 0.72, delay: 1.1 },
      controlA: { x: width * 0.32, y: height * 0.58, delay: 0 },
      controlB: { x: width * 0.48, y: height * 0.72, delay: 0 },
      end: { x: width * 0.72, y: height * 0.58, delay: 0 },
      duration: 5.2,
      color: '#408d86'
    },
    {
      start: { x: width * 0.32, y: height * 0.18, delay: 2.2 },
      controlA: { x: width * 0.48, y: height * 0.1, delay: 0 },
      controlB: { x: width * 0.64, y: height * 0.24, delay: 0 },
      end: { x: width * 0.88, y: height * 0.2, delay: 0 },
      duration: 4.5,
      color: '#26a69a'
    },
    {
      start: { x: width * 0.44, y: height * 0.82, delay: 3.4 },
      controlA: { x: width * 0.56, y: height * 0.72, delay: 0 },
      controlB: { x: width * 0.66, y: height * 0.88, delay: 0 },
      end: { x: width * 0.9, y: height * 0.68, delay: 0 },
      duration: 5,
      color: '#1c8f7d'
    }
  ]
}

function drawFlowRoutes(
  context: CanvasRenderingContext2D,
  routes: Array<{
    start: RoutePoint
    controlA: RoutePoint
    controlB: RoutePoint
    end: RoutePoint
    duration: number
    color: string
  }>,
  elapsed: number
) {
  routes.forEach((route) => {
    context.beginPath()
    context.moveTo(route.start.x, route.start.y)
    context.bezierCurveTo(route.controlA.x, route.controlA.y, route.controlB.x, route.controlB.y, route.end.x, route.end.y)
    context.strokeStyle = 'rgba(64, 141, 134, 0.16)'
    context.lineWidth = 1.2
    context.stroke()

    const progress = ((elapsed - route.start.delay + route.duration) % route.duration) / route.duration
    const point = cubicPoint(route.start, route.controlA, route.controlB, route.end, progress)
    drawPoint(context, point.x, point.y, route.color, 3.2)
    drawPoint(context, point.x, point.y, 'rgba(38, 166, 154, 0.2)', 10)
  })
}

function cubicPoint(start: RoutePoint, controlA: RoutePoint, controlB: RoutePoint, end: RoutePoint, progress: number) {
  const reverse = 1 - progress
  return {
    x:
      reverse ** 3 * start.x +
      3 * reverse ** 2 * progress * controlA.x +
      3 * reverse * progress ** 2 * controlB.x +
      progress ** 3 * end.x,
    y:
      reverse ** 3 * start.y +
      3 * reverse ** 2 * progress * controlA.y +
      3 * reverse * progress ** 2 * controlB.y +
      progress ** 3 * end.y
  }
}

function roundedRect(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
  const corner = Math.min(radius, width / 2, height / 2)
  context.beginPath()
  context.moveTo(x + corner, y)
  context.lineTo(x + width - corner, y)
  context.quadraticCurveTo(x + width, y, x + width, y + corner)
  context.lineTo(x + width, y + height - corner)
  context.quadraticCurveTo(x + width, y + height, x + width - corner, y + height)
  context.lineTo(x + corner, y + height)
  context.quadraticCurveTo(x, y + height, x, y + height - corner)
  context.lineTo(x, y + corner)
  context.quadraticCurveTo(x, y, x + corner, y)
  context.closePath()
}

function drawPoint(context: CanvasRenderingContext2D, x: number, y: number, color: string, radius: number) {
  context.beginPath()
  context.arc(x, y, radius, 0, Math.PI * 2)
  context.fillStyle = color
  context.fill()
}

function setupInkReveal(canvas: HTMLCanvasElement) {
  const parent = canvas.parentElement
  const context = canvas.getContext('2d')
  if (!parent || !context) return
  const visualParent = parent
  const ctx = context

  const stamps: InkStamp[] = []
  let lastPos: { x: number; y: number } | null = null
  let width = 0
  let height = 0
  let running = false

  const lifetime = 900
  const brushSize = 8.5 * 16
  const stampStep = 0.75 * 16
  const startRadius = 0.625 * 16
  const maxStamps = 220
  const segments = 38

  function paintMask() {
    const gradient = ctx.createLinearGradient(0, 0, width, height)
    gradient.addColorStop(0, '#e6f7f4')
    gradient.addColorStop(0.55, '#f4fcfb')
    gradient.addColorStop(1, '#d5efeb')
    ctx.globalCompositeOperation = 'source-over'
    ctx.fillStyle = gradient
    ctx.fillRect(0, 0, width, height)

    const glow = ctx.createRadialGradient(width * 0.5, height * 0.42, 0, width * 0.5, height * 0.42, width * 0.65)
    glow.addColorStop(0, 'rgba(38, 166, 154, 0.16)')
    glow.addColorStop(0.62, 'rgba(128, 203, 196, 0.08)')
    glow.addColorStop(1, 'rgba(128, 203, 196, 0)')
    ctx.fillStyle = glow
    ctx.fillRect(0, 0, width, height)
  }

  function resize() {
    const ratio = Math.min(window.devicePixelRatio || 1, 2)
    const rect = visualParent.getBoundingClientRect()
    width = Math.max(1, rect.width)
    height = Math.max(1, rect.height)
    canvas.width = Math.round(width * ratio)
    canvas.height = Math.round(height * ratio)
    canvas.style.width = `${width / 16}rem`
    canvas.style.height = `${height / 16}rem`
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
    paintMask()
  }

  function carveStamp(stamp: InkStamp, progress: number) {
    const ease = 1 - (1 - progress) ** 3
    const radius = startRadius + (stamp.radius - startRadius) * ease
    const alpha = 1 - progress * progress
    const gradient = ctx.createRadialGradient(stamp.x, stamp.y, radius * 0.18, stamp.x, stamp.y, radius)
    gradient.addColorStop(0, `rgba(0, 0, 0, ${0.98 * alpha})`)
    gradient.addColorStop(0.5, `rgba(0, 0, 0, ${0.86 * alpha})`)
    gradient.addColorStop(1, 'rgba(0, 0, 0, 0)')
    ctx.fillStyle = gradient
    ctx.beginPath()
    for (let index = 0; index <= segments; index += 1) {
      const angle = (index / segments) * Math.PI * 2
      const wobble =
        0.78 +
        0.14 * Math.sin(angle * 3 + stamp.seed) +
        0.08 * Math.sin(angle * 5 + stamp.seed * 2.1) +
        0.05 * Math.sin(angle * 7 + stamp.seed * 0.7)
      const pointX = stamp.x + Math.cos(angle) * radius * wobble
      const pointY = stamp.y + Math.sin(angle) * radius * wobble
      if (index === 0) ctx.moveTo(pointX, pointY)
      else ctx.lineTo(pointX, pointY)
    }
    ctx.closePath()
    ctx.fill()
  }

  function draw() {
    const now = performance.now()
    paintMask()
    ctx.globalCompositeOperation = 'destination-out'
    for (let index = stamps.length - 1; index >= 0; index -= 1) {
      const progress = (now - stamps[index].born) / lifetime
      if (progress >= 1) {
        stamps.splice(index, 1)
        continue
      }
      carveStamp(stamps[index], progress)
    }
    if (stamps.length) {
      revealAnimationFrame = requestAnimationFrame(draw)
    } else {
      running = false
      ctx.globalCompositeOperation = 'source-over'
    }
  }

  function start() {
    if (!running) {
      running = true
      revealAnimationFrame = requestAnimationFrame(draw)
    }
  }

  function addStamp(x: number, y: number) {
    if (stamps.length >= maxStamps) stamps.shift()
    stamps.push({
      x,
      y,
      born: performance.now(),
      seed: Math.random() * Math.PI * 2,
      radius: brushSize * (0.62 + Math.random() * 0.38)
    })
  }

  function stampAlong(x: number, y: number) {
    if (!lastPos) {
      addStamp(x, y)
    } else {
      const distance = Math.hypot(x - lastPos.x, y - lastPos.y)
      const steps = Math.max(1, Math.ceil(distance / stampStep))
      for (let index = 1; index <= steps; index += 1) {
        addStamp(lastPos.x + ((x - lastPos.x) * index) / steps, lastPos.y + ((y - lastPos.y) * index) / steps)
      }
    }
    lastPos = { x, y }
    start()
  }

  function relativePoint(event: PointerEvent) {
    const rect = canvas.getBoundingClientRect()
    return {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    }
  }

  function handlePointerMove(event: PointerEvent) {
    const point = relativePoint(event)
    if (point.x < 0 || point.y < 0 || point.x > width || point.y > height) {
      lastPos = null
      return
    }
    stampAlong(point.x, point.y)
  }

  function resetPointerTrail() {
    lastPos = null
  }

  revealResizeObserver = new ResizeObserver(resize)
  revealResizeObserver.observe(visualParent)
  resize()
  window.addEventListener('pointermove', handlePointerMove, { passive: true })
  window.addEventListener('blur', resetPointerTrail)
  document.addEventListener('pointerleave', resetPointerTrail)
  removeRevealListeners = () => {
    window.removeEventListener('pointermove', handlePointerMove)
    window.removeEventListener('blur', resetPointerTrail)
    document.removeEventListener('pointerleave', resetPointerTrail)
  }
}

onMounted(() => {
  const revealCanvas = revealCanvasRef.value
  if (revealCanvas?.parentElement) setupInkReveal(revealCanvas)

  const canvas = canvasRef.value
  if (!canvas?.parentElement) return
  drawLedgerMap(canvas)
  ledgerResizeObserver = new ResizeObserver(() => drawLedgerMap(canvas))
  ledgerResizeObserver.observe(canvas.parentElement)
})

onBeforeUnmount(() => {
  removeRevealListeners?.()
  ledgerResizeObserver?.disconnect()
  revealResizeObserver?.disconnect()
  cancelAnimationFrame(ledgerAnimationFrame)
  cancelAnimationFrame(revealAnimationFrame)
})
</script>

<style scoped lang="stylus">
.auth-page
  position relative
  min-height 100vh
  display grid
  place-items center
  padding 1.5rem
  overflow hidden
  background radial-gradient(circle at 14% 12%, rgba(128, 203, 196, 0.36), transparent 21%), linear-gradient(135deg, #effbf9 0%, #d0ebea 45%, #f8fcfb 100%)

.auth-page-reveal-image,
.auth-page-reveal-canvas
  position absolute
  inset 0
  width 100%
  height 100%
  pointer-events none

.auth-page-reveal-image
  z-index 0
  object-fit cover
  opacity 0.82
  filter saturate(0.95) contrast(0.9) brightness(1.08)
  transform scale(1.02)

.auth-page-reveal-canvas
  z-index 1

.auth-panel
  position relative
  z-index 2
  width 58rem
  max-width calc(100vw - 3rem)
  min-height 36rem
  display grid
  grid-template-columns minmax(0, 1fr) minmax(21rem, 0.88fr)
  overflow hidden
  border-radius 1.5rem
  background linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(244, 252, 251, 0.48))
  border 0.0625rem solid rgba(255, 255, 255, 0.76)
  box-shadow 0 1.5rem 4.5rem rgba(22, 75, 78, 0.18), inset 0 0 0 0.0625rem rgba(255, 255, 255, 0.48)
  backdrop-filter blur(1.125rem) saturate(1.28)

.auth-visual
  position relative
  min-height 36rem
  overflow hidden
  background linear-gradient(145deg, rgba(230, 247, 244, 0.84) 0%, rgba(244, 252, 251, 0.72) 55%, rgba(213, 239, 235, 0.84) 100%)
  border-right 0.0625rem solid rgba(64, 141, 134, 0.12)
  backdrop-filter blur(0.625rem) saturate(1.18)

.auth-canvas
  position absolute
  inset 0
  z-index 1
  width 100%
  height 100%

.auth-brand
  position absolute
  inset 0
  z-index 2
  display grid
  place-items center
  align-content center
  gap 0.75rem
  padding 2rem
  text-align center
  color #17313f
  pointer-events none

.auth-logo
  width 3.5rem
  height 3.5rem
  display grid
  place-items center
  border-radius 50%
  background linear-gradient(135deg, #26a69a 0%, #408d86 100%)
  color #fff
  font-size 1.5rem
  font-weight 900
  box-shadow 0 0.75rem 1.75rem rgba(38, 166, 154, 0.26)

.auth-brand h1
  margin 0
  font-size 2.5rem
  line-height 1.05
  color #12313a

.auth-brand p
  margin 0
  max-width 18rem
  color #607985
  font-size 0.9375rem
  line-height 1.7

.auth-form-area
  position relative
  display grid
  place-items center
  padding 2.25rem
  overflow hidden
  background linear-gradient(145deg, rgba(255, 255, 255, 0.84), rgba(247, 252, 251, 0.66))
  backdrop-filter blur(1.5rem) saturate(1.18)

.auth-form-area::before
  content ''
  position absolute
  inset 1rem
  border-radius 1.125rem
  background radial-gradient(circle at 18% 8%, rgba(255, 255, 255, 0.84), transparent 38%), linear-gradient(160deg, rgba(255, 255, 255, 0.7), rgba(255, 255, 255, 0.38))
  box-shadow inset 0 0 0 0.0625rem rgba(255, 255, 255, 0.52)
  pointer-events none

.auth-form-card
  position relative
  z-index 1
  width min(22rem, 100%)

.auth-form-head
  margin-bottom 1.75rem

.auth-form-head span
  display inline-flex
  align-items center
  min-height 1.75rem
  padding 0 0.75rem
  border-radius 62.4375rem
  background #e7f7f4
  color #168a78
  font-size 0.75rem
  font-weight 800

.auth-form-head h2
  margin 0.75rem 0 0
  color #17252e
  font-size 1.875rem
  line-height 1.18

.auth-form-card :deep(.el-form-item)
  margin-bottom 1rem

.auth-form-card :deep(.el-form-item__content)
  display block

.auth-form-card :deep(.el-form-item__error)
  position static
  margin-top 0.375rem
  padding-top 0
  line-height 1.35
  font-size 0.75rem
  overflow-wrap anywhere

.auth-form-card :deep(.el-form-item__label)
  color #405862
  font-weight 700

.auth-form-card :deep(.el-input__wrapper)
  min-height 2.75rem
  border-radius 0.75rem
  background rgba(248, 252, 251, 0.96)
  box-shadow 0 0 0 0.0625rem rgba(210, 229, 226, 0.96) inset, 0 0.5rem 1.25rem rgba(21, 73, 78, 0.06)

.auth-form-card :deep(.el-input__wrapper.is-focus)
  box-shadow 0 0 0 0.0625rem #26a69a inset, 0 0 0 0.1875rem rgba(38, 166, 154, 0.12)

.confirm-assist
  position relative
  min-height 2.75rem
  overflow hidden
  border-radius 0.75rem
  background rgba(248, 252, 251, 0.96)
  box-shadow 0 0 0 0.0625rem rgba(210, 229, 226, 0.96) inset, 0 0.5rem 1.25rem rgba(21, 73, 78, 0.06)
  transition transform 0.18s ease

.confirm-assist.is-shaking
  animation confirm-shake 0.42s ease

.confirm-guide
  position absolute
  left 0.625rem
  right 0.625rem
  top 50%
  transform translateY(-50%)
  z-index 1
  min-height 1.875rem
  display flex
  align-items center
  gap 0.125rem
  overflow hidden
  pointer-events none
  opacity 1
  transition opacity 0.16s ease

.confirm-assist.is-empty .confirm-guide
  opacity 0

.confirm-guide-cell
  width 1rem
  height 1.375rem
  display grid
  place-items center
  flex 0 0 1rem
  border-radius 0.375rem
  transition background 0.18s ease, transform 0.18s ease

.confirm-guide-cell i
  width 0.3125rem
  height 0.3125rem
  display block
  border-radius 50%
  background #22343d

.confirm-guide-cell.is-right
  background rgba(38, 166, 154, 0.16)

.confirm-guide-cell.is-wrong
  background rgba(227, 93, 106, 0.16)

.confirm-guide-cell.is-wrong i
  background #e35d6a

.confirm-caret
  position absolute
  top 50%
  width 0.0625rem
  height 1.25rem
  border-radius 62.4375rem
  background #17252e
  transform translateY(-50%)
  animation confirm-caret-blink 1s step-end infinite
  pointer-events none

.confirm-input-shell
  position relative
  z-index 2
  width 100%
  min-height 2.5rem

.confirm-input
  width 100%
  min-height 2.75rem
  border 0
  outline none
  border-radius 0.75rem
  background transparent
  color transparent
  caret-color transparent
  padding 0 0.875rem
  font inherit
  letter-spacing 0.36rem

.confirm-input::placeholder
  color #8aa0a8
  letter-spacing 0

.confirm-assist:focus-within
  box-shadow 0 0 0 0.0625rem #26a69a inset, 0 0 0 0.1875rem rgba(38, 166, 154, 0.12)

.confirm-assist.is-match
  box-shadow 0 0 0 0.0625rem #26a69a inset, 0 0 0 0.1875rem rgba(38, 166, 154, 0.1)

.auth-submit
  width 100%
  min-height 2.75rem
  margin-top 0.25rem
  border 0
  border-radius 0.75rem
  font-weight 800
  background linear-gradient(135deg, #26a69a 0%, #408d86 100%)
  box-shadow 0 0.75rem 1.5rem rgba(38, 166, 154, 0.22)

.auth-form-card.is-register
  transform translateY(-0.5rem)

.auth-form-card.is-register .auth-form-head
  margin-bottom 1.125rem

.auth-form-card.is-register .auth-form-head h2
  margin-top 0.5rem
  font-size 1.625rem

.auth-form-card.is-register :deep(.el-form-item)
  margin-bottom 0.875rem

.auth-form-card.is-register :deep(.el-input__wrapper)
  min-height 2.5rem

.auth-form-card.is-register .confirm-assist,
.auth-form-card.is-register .confirm-input
  min-height 2.5rem

.auth-form-card.is-register .auth-submit
  min-height 2.5rem
  margin-top 0

.auth-submit:hover
  background linear-gradient(135deg, #23998e 0%, #377f79 100%)

.auth-switch
  width 100%
  margin-top 1rem
  border 0
  background transparent
  color #168a78
  font-size 0.875rem
  font-weight 700
  cursor pointer

.auth-switch:hover
  color #0f7467

@keyframes confirm-shake
  0%
    transform translateX(0)
  20%
    transform translateX(-0.5rem)
  40%
    transform translateX(0.5rem)
  60%
    transform translateX(-0.375rem)
  80%
    transform translateX(0.375rem)
  100%
    transform translateX(0)

@keyframes confirm-caret-blink
  0%,
  49%
    opacity 1
  50%,
  100%
    opacity 0

@media (max-width: 52rem)
  .auth-page
    padding 1rem

  .auth-panel
    grid-template-columns 1fr
    width 100%
    min-height auto

  .auth-visual
    min-height 14rem
    border-right 0
    border-bottom 0.0625rem solid rgba(64, 141, 134, 0.14)

  .auth-brand h1
    font-size 2rem

  .auth-brand p
    display none

  .auth-form-area
    padding 1.5rem
    background rgba(255, 255, 255, 0.9)

  .auth-form-area::before
    inset 0.625rem
    background rgba(255, 255, 255, 0.54)
</style>
