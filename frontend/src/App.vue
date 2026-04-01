<script setup>
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import StudentPortal from './components/StudentPortal.vue'
import TeacherPortal from './components/TeacherPortal.vue'
import LoginPage from './components/LoginPage.vue'
import AccountSecurityPanel from './components/AccountSecurityPanel.vue'

const router = useRouter()
const route = useRoute()

const currentUser = ref(JSON.parse(localStorage.getItem('currentUser') || 'null'))

const studentPageList = [
  { key: 'graph', label: '知识图谱' },
  { key: 'exercise', label: '出题与做题' },
  { key: 'review', label: '错题与记录' },
  { key: 'home', label: '个人中心' }
]

const teacherPageList = [
  { key: 'profile', label: '个人中心' },
  { key: 'manage', label: '资料管理' }
]

const roleLabelMap = {
  student: '学生端',
  teacher: '教师端'
}

const currentRoleLabel = computed(() => roleLabelMap[currentUser.value?.role] || '未登录')
const isStudentUser = computed(() => currentUser.value?.role === 'student')
const isOnStudentRoute = computed(() => route.path.startsWith('/student'))
const isTeacherUser = computed(() => currentUser.value?.role === 'teacher')
const isOnTeacherRoute = computed(() => route.path.startsWith('/teacher'))

const handleLoginSuccess = (user) => {
  if (user?.role !== 'student' && user?.role !== 'teacher') {
    currentUser.value = null
    localStorage.removeItem('currentUser')
    router.push('/login')
    return
  }

  currentUser.value = user
  localStorage.setItem('currentUser', JSON.stringify(user))
  if (user.role === 'student') {
    router.push('/student')
  } else if (user.role === 'teacher') {
    router.push('/teacher')
  } else {
    router.push('/')
  }
}

const handleLogout = () => {
  currentUser.value = null
  localStorage.removeItem('currentUser')
  router.push('/login')
}

</script>

<template>
  <div class="app-page">
    <header class="top-nav">
      <div class="top-nav-shell">
        <div class="top-nav-inner" :class="{ 'top-nav-inner-student': isStudentUser && isOnStudentRoute }">
          <div class="brand-block">
            <h1>学生自学平台</h1>
            <p class="brand-en">AI Self-Learning Platform</p>
          </div>

          <nav v-if="isStudentUser && isOnStudentRoute" class="section-nav section-nav-inline" aria-label="学生页面导航">
            <button
              v-for="page in studentPageList"
              :key="page.key"
              class="nav-btn section-nav-btn"
              :class="{ active: route.params.page === page.key || (!route.params.page && page.key === 'home') }"
              @click="() => router.push(`/student/${page.key}`)">
              {{ page.label }}
            </button>
          </nav>

          <nav v-else-if="isTeacherUser && isOnTeacherRoute" class="section-nav section-nav-inline" aria-label="教师页面导航">
            <button
              v-for="page in teacherPageList"
              :key="page.key"
              class="nav-btn section-nav-btn"
              :class="{ active: (route.params.page === page.key) || (!route.params.page && page.key === 'profile') }"
              @click="() => router.push(`/teacher/${page.key}`)">
              {{ page.label }}
            </button>
          </nav>

          <div v-if="(isStudentUser && isOnStudentRoute) || (isTeacherUser && isOnTeacherRoute)" class="top-user-card">
            <span class="top-user-name">{{ currentUser.username }}</span>
            <span class="top-user-role">{{ isStudentUser && isOnStudentRoute ? '学生账号' : (isTeacherUser && isOnTeacherRoute ? '教师账号' : '') }}</span>
          </div>

          <nav v-else-if="currentUser" class="nav-actions" aria-label="主导航">
            <button
              class="nav-btn"
              @click="() => router.push(currentUser && currentUser.role === 'student' ? '/student' : currentUser && currentUser.role === 'teacher' ? '/teacher' : '/')"
            >
              {{ currentRoleLabel }}主页
            </button>
          </nav>
        </div>
      </div>
    </header>

    <div class="app-shell">
      <main class="panel-wrap">
        <router-view v-slot="{ Component }">
          <component :is="Component" @login-success="handleLoginSuccess" @logout="handleLogout" />
        </router-view>
      </main>
    </div>
  </div>
</template>
