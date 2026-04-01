<script setup>
import { ref } from 'vue'
import { loginUser } from '../api/client'

const emit = defineEmits(['login-success'])

const loading = ref(false)
const message = ref('')
const error = ref('')
const loginForm = ref({ identity: '', password: '' })

const doLogin = async () => {
  loading.value = true
  message.value = ''
  error.value = ''
  try {
    const { data } = await loginUser(loginForm.value)
    message.value = `登录成功，欢迎 ${data.user.username}`
    emit('login-success', data.user)
  } catch (err) {
    error.value = err?.response?.data?.message || '登录失败。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="login-shell">
    <div class="login-grid single-card">
      <article class="result-card login-card">
        <h3>账户登录</h3>
        <div class="grid-form">
          <label>
            用户名或邮箱
            <input v-model="loginForm.identity" />
          </label>
          <label>
            密码
            <input v-model="loginForm.password" type="password" />
          </label>
          <button :disabled="loading" @click="doLogin">登录</button>
        </div>
      </article>
    </div>

    <p v-if="message" class="ok-text">{{ message }}</p>
    <p v-if="error" class="error-text">{{ error }}</p>
  </section>
</template>
