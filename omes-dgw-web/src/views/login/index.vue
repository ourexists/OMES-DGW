<template>
  <div class="login-page">
    <div class="login-page__bg" aria-hidden="true">
      <div class="login-page__grid" />
      <div class="login-page__glow login-page__glow--primary" />
      <div class="login-page__glow login-page__glow--cyan" />
    </div>

    <main class="login-main">
      <section class="login-brand">
        <div class="login-brand__logo-wrap">
          <img class="login-brand__logo" src="@/assets/brand/omes-icon.png" alt="OMES DGW" />
        </div>
        <h1 class="login-brand__title">OMES DGW</h1>
        <p class="login-brand__desc">设备接入、协议采集与边缘侧实时数据管理</p>
      </section>

      <section class="login-panel">
        <div class="login-card">
          <div class="login-card__head">
            <h2>登录</h2>
            <p>使用账号密码进入系统</p>
          </div>
          <a-form :model="formState" :rules="rules" ref="formRef" layout="vertical" class="login-form">
            <a-form-item name="username" label="用户名">
              <a-input
                v-model:value="formState.username"
                placeholder="请输入用户名"
                size="large"
                autocomplete="username"
              >
                <template #prefix>
                  <UserOutlined class="login-form__prefix" />
                </template>
              </a-input>
            </a-form-item>
            <a-form-item name="password" label="密码">
              <a-input-password
                v-model:value="formState.password"
                placeholder="请输入密码"
                size="large"
                autocomplete="current-password"
                @pressEnter="handleLogin"
              >
                <template #prefix>
                  <LockOutlined class="login-form__prefix" />
                </template>
              </a-input-password>
            </a-form-item>
            <a-form-item class="login-btn-wrap">
              <a-button
                type="primary"
                block
                size="large"
                :loading="loading"
                @click="handleLogin"
                class="login-btn"
              >
                登 录
              </a-button>
            </a-form-item>
          </a-form>
          <div class="login-footer">
            <p>OMES DGW ©2026</p>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { setupDynamicRoutes } from '@/router'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const formState = reactive({
  username: import.meta.env.VITE_LOGIN_USERNAME || '',
  password: import.meta.env.VITE_LOGIN_PASSWORD || ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = () => {
  formRef.value.validate().then(async () => {
    loading.value = true
    try {
      await userStore.login(formState.username, formState.password)
      setupDynamicRoutes(userStore.menus)
      message.success('登录成功')
      router.push(userStore.getFirstMenuPath())
    } catch (error) {
      message.error(error.message || '登录失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: var(--omes-color-bg-standalone);
  color: #fff;
}

.login-page__bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.login-page__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.06) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 80% 70% at 30% 40%, #000 20%, transparent 75%);
}

.login-page__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.55;
}

.login-page__glow--primary {
  top: -120px;
  left: -80px;
  width: 420px;
  height: 420px;
  background: rgba(0, 120, 212, 0.35);
}

.login-page__glow--cyan {
  right: -60px;
  bottom: -100px;
  width: 360px;
  height: 360px;
  background: rgba(0, 183, 195, 0.22);
}

.login-main {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 56px;
  min-height: 100vh;
  padding: 24px 48px 48px;
}

.login-brand {
  flex: 1.15;
  max-width: 560px;
}

.login-brand__logo-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  margin-bottom: 28px;
  border-radius: 22px;
  background: linear-gradient(145deg, rgba(0, 120, 212, 0.22), rgba(0, 183, 195, 0.12));
  box-shadow:
    0 0 0 1px rgba(148, 163, 184, 0.16),
    0 16px 40px rgba(0, 0, 0, 0.28);
}

.login-brand__logo {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  object-fit: cover;
}

.login-brand__title {
  margin: 0 0 12px;
  font-size: 40px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #fff;
  line-height: 1.35;
}

.login-brand__tagline {
  margin: 0 0 16px;
  font-size: 18px;
  color: rgba(226, 232, 240, 0.92);
}

.login-brand__desc {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: rgba(148, 163, 184, 0.95);
  max-width: 420px;
}

.login-panel {
  flex: 0 0 auto;
}

.login-card {
  width: 400px;
  background: var(--omes-color-bg-container);
  border-radius: var(--omes-border-radius-x-large);
  padding: 36px 32px 28px;
  box-shadow: var(--omes-shadow-card), 0 16px 40px rgba(0, 0, 0, 0.28);
  color: var(--omes-color-text);
}

.login-card__head {
  margin-bottom: 28px;
}

.login-card__head h2 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: var(--omes-font-weight-semibold);
  color: var(--omes-color-text);
}

.login-card__head p {
  margin: 0;
  font-size: 14px;
  color: var(--omes-color-text-secondary);
}

.login-form :deep(.ant-form-item-label > label) {
  color: var(--omes-color-text-label);
  font-weight: 500;
}

.login-form__prefix {
  color: var(--omes-color-primary);
}

.login-btn-wrap {
  margin-top: 12px;
  margin-bottom: 0;
}

.login-btn {
  height: 44px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 2px;
  border-radius: var(--omes-border-radius-medium);
}

.login-footer {
  text-align: center;
  padding-top: 20px;
  margin-top: 8px;
  border-top: 1px solid var(--omes-color-border);
}

.login-footer p {
  color: var(--omes-color-text-tertiary);
  font-size: 13px;
  margin: 0;
}

@media screen and (max-width: 1024px) {
  .login-main {
    flex-direction: column;
    gap: 32px;
    padding: 32px 24px;
  }

  .login-brand {
    text-align: center;
    max-width: 100%;
  }

  .login-brand__desc {
    margin: 0 auto;
  }

  .login-brand__title {
    font-size: 32px;
  }

  .login-card {
    width: min(400px, 92vw);
  }
}

@media screen and (max-width: 768px) {
  .login-brand__title {
    font-size: 28px;
  }

  .login-brand__logo-wrap {
    width: 72px;
    height: 72px;
  }

  .login-brand__logo {
    width: 52px;
    height: 52px;
  }
}
</style>
