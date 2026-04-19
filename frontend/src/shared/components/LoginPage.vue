<script setup>
import { inject, ref } from 'vue'
import { loginUser, changePassword } from '../../api/client'
import { appShellKey } from '../../appShell'

const emit = defineEmits(['login-success'])
const shell = inject(appShellKey, null)

const loading = ref(false)
const message = ref('')
const error = ref('')
const loginForm = ref({ identity: '', password: '' })
const forgotVisible = ref(false)
const forgotLoading = ref(false)
const forgotMessage = ref('')
const forgotError = ref('')
const forgotForm = ref({ username: '', workId: '', newPassword: '', confirmPassword: '' })

const doLogin = async () => {
  loading.value = true
  message.value = ''
  error.value = ''
  try {
    const { data } = await loginUser(loginForm.value)
    message.value = `登录成功，欢迎 ${data.user?.username ?? ''}`
    if (typeof shell?.loginSuccess === 'function') shell.loginSuccess(data.user)
    else emit('login-success', data.user)
  } catch (err) {
    error.value = err?.response?.data?.message || '登录失败。'
  } finally {
    loading.value = false
  }
}

const openForgot = () => {
  forgotVisible.value = true
  forgotLoading.value = false
  forgotError.value = ''
  forgotMessage.value = ''
  forgotForm.value = { username: '', workId: '', newPassword: '', confirmPassword: '' }
  error.value = ''
  message.value = ''
}

const closeForgot = () => {
  forgotVisible.value = false
  forgotMessage.value = ''
  forgotError.value = ''
}

const submitForgot = async () => {
  forgotMessage.value = ''
  forgotError.value = ''
  const username = String(forgotForm.value.username || '').trim()
  const workId = String(forgotForm.value.workId || '').trim()
  const newPassword = String(forgotForm.value.newPassword || '')
  const confirmPassword = String(forgotForm.value.confirmPassword || '')

  if (!username || !workId || !newPassword) {
    forgotError.value = '请填写姓名、学工号和新密码。'
    return
  }
  if (newPassword.length < 6) {
    forgotError.value = '新密码长度至少 6 位。'
    return
  }
  if (newPassword !== confirmPassword) {
    forgotError.value = '两次输入的新密码不一致。'
    return
  }

  forgotLoading.value = true
  try {
    await changePassword({ username, workId, newPassword })
    forgotMessage.value = '密码重置成功，请使用新密码登录。'
    forgotForm.value.newPassword = ''
    forgotForm.value.confirmPassword = ''
  } catch (err) {
    forgotError.value = err?.response?.data?.message || '重置失败，请检查姓名与学工号。'
  } finally {
    forgotLoading.value = false
  }
}
</script>

<template>
  <section class="login-page" aria-label="登录">
    <div class="login-page-bleed">
      <div class="login-page-inner">
        <div class="login-panel">
          <template v-if="!forgotVisible">
            <header class="login-panel-head">
              <h3 class="login-panel-title">账户登录</h3>
              <p class="login-panel-sub">请输入学工号或邮箱与密码</p>
            </header>

            <form class="login-form" @submit.prevent="doLogin">
              <label class="login-field">
                <span class="login-label">学工号或邮箱</span>
                <input
                  v-model="loginForm.identity"
                  type="text"
                  name="identity"
                  autocomplete="username"
                  placeholder="例如：学号或学校邮箱"
                  :disabled="loading"
                />
              </label>
              <label class="login-field">
                <span class="login-label">密码</span>
                <input
                  v-model="loginForm.password"
                  type="password"
                  name="password"
                  autocomplete="current-password"
                  placeholder="请输入密码"
                  :disabled="loading"
                />
              </label>

              <p v-if="error" class="login-alert login-alert--error" role="alert">{{ error }}</p>
              <p v-if="message" class="login-alert login-alert--ok">{{ message }}</p>

              <button type="submit" class="login-submit" :disabled="loading">
                {{ loading ? '登录中…' : '登录' }}
              </button>

              <div class="login-row">
                <button type="button" class="login-link" :disabled="loading" @click="openForgot">忘记密码？</button>
              </div>
            </form>
          </template>

          <template v-else>
            <header class="login-panel-head">
              <h3 class="login-panel-title">忘记密码</h3>
              <p class="login-panel-sub">通过姓名与学工号验证后设置新密码</p>
            </header>

            <form class="login-form" @submit.prevent="submitForgot">
              <label class="login-field">
                <span class="login-label">姓名</span>
                <input
                  v-model="forgotForm.username"
                  type="text"
                  autocomplete="name"
                  placeholder="与账号一致的姓名"
                  :disabled="forgotLoading"
                />
              </label>
              <label class="login-field">
                <span class="login-label">学工号</span>
                <input
                  v-model="forgotForm.workId"
                  type="text"
                  autocomplete="username"
                  placeholder="学工号"
                  :disabled="forgotLoading"
                />
              </label>
              <label class="login-field">
                <span class="login-label">新密码</span>
                <input
                  v-model="forgotForm.newPassword"
                  type="password"
                  autocomplete="new-password"
                  placeholder="至少 6 位"
                  :disabled="forgotLoading"
                />
              </label>
              <label class="login-field">
                <span class="login-label">确认新密码</span>
                <input
                  v-model="forgotForm.confirmPassword"
                  type="password"
                  autocomplete="new-password"
                  placeholder="再次输入新密码"
                  :disabled="forgotLoading"
                />
              </label>

              <p v-if="forgotError" class="login-alert login-alert--error" role="alert">{{ forgotError }}</p>
              <p v-if="forgotMessage" class="login-alert login-alert--ok">{{ forgotMessage }}</p>

              <div class="login-forgot-actions">
                <button type="submit" class="login-submit" :disabled="forgotLoading">
                  {{ forgotLoading ? '提交中…' : '重置密码' }}
                </button>
                <button type="button" class="login-btn-secondary" :disabled="forgotLoading" @click="closeForgot">
                  返回登录
                </button>
              </div>
            </form>
          </template>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.login-page {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 0;
}

/* 与 App 中 app-shell--login 配合：横向铺满、纵向吃满顶栏下剩余视口，卡片垂直居中 */
.login-page-bleed {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: none;
  margin: 0;
  box-sizing: border-box;
  padding: clamp(24px, 4.5vh, 48px) clamp(16px, 3.5vw, 36px) clamp(32px, 5vh, 56px);
  background-color: #1e293b;
  background-image:
    linear-gradient(180deg, rgba(15, 23, 42, 0.42) 0%, rgba(15, 23, 42, 0.32) 45%, rgba(15, 23, 42, 0.48) 100%),
    url('/bj.jpg');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  border-bottom: none;
}

.login-page-inner {
  width: 100%;
  max-width: 400px;
  margin: 0 auto;
}

.login-panel {
  width: 100%;
  border-radius: 20px;
  padding: 28px 26px 26px;
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(20px) saturate(1.35);
  -webkit-backdrop-filter: blur(20px) saturate(1.35);
  border: 1px solid rgba(255, 255, 255, 0.38);
  box-shadow:
    0 4px 24px rgba(0, 0, 0, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.45);
}

.login-panel-head {
  margin-bottom: 22px;
}

.login-panel-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
  color: #f8fafc;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}

.login-panel-sub {
  margin: 0;
  font-size: 14px;
  color: rgba(248, 250, 252, 0.82);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.login-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
}

.login-label {
  font-size: 13px;
  font-weight: 600;
  color: rgba(248, 250, 252, 0.9);
}

.login-field input {
  width: 100%;
  min-height: 42px;
  padding: 9px 12px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 10px;
  font-size: 15px;
  color: #0f172a;
  background: rgba(255, 255, 255, 0.88);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background-color 0.15s ease;
}

.login-field input::placeholder {
  color: #94a3b8;
}

.login-field input:hover:not(:disabled) {
  border-color: #94a3b8;
}

.login-field input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
}

.login-field input:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  background: rgba(248, 250, 252, 0.55);
}

.login-alert {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
}

.login-alert--error {
  color: #fecdd3;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.4);
}

.login-alert--ok {
  color: #99f6e4;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}

.login-submit {
  margin-top: 4px;
  width: 100%;
  min-height: 44px;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(180deg, #6366f1 0%, #4f46e5 100%);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  transition: filter 0.15s ease, box-shadow 0.15s ease;
}

.login-submit:hover:not(:disabled) {
  filter: brightness(1.05);
  box-shadow: 0 4px 14px -2px rgba(79, 70, 229, 0.45);
}

.login-submit:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.login-row {
  display: flex;
  justify-content: flex-end;
  margin-top: -4px;
}

.login-link {
  border: none;
  background: none;
  padding: 6px 0;
  font-size: 14px;
  font-weight: 600;
  color: #c7d2fe;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}

.login-link:hover:not(:disabled) {
  color: #e0e7ff;
}

.login-link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.login-forgot-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.login-btn-secondary {
  width: 100%;
  min-height: 42px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.45);
  background: rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.2) inset;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.login-btn-secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.22);
  border-color: rgba(255, 255, 255, 0.55);
}

.login-btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 880px) {
  .login-page-inner {
    max-width: min(400px, 100%);
  }
}
</style>
