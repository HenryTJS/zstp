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
  { key: 'announcements', label: '公告' },
  { key: 'home', label: '个人中心' }
]

const teacherPageList = [
  { key: 'manage', label: '资料管理' },
  { key: 'announcements', label: '公告' },
  { key: 'profile', label: '个人中心' }
]

const adminPageList = [
  { key: 'import', label: '批量导入' },
  { key: 'announcements', label: '公告管理' },
  { key: 'student-stats', label: '学生统计' },
  { key: 'teacher-stats', label: '教师统计' },
  { key: 'profile', label: '个人中心' }
]

const roleLabelMap = {
  student: '学生端',
  teacher: '教师端',
  admin: '管理员端'
}

const currentRoleLabel = computed(() => roleLabelMap[currentUser.value?.role] || '未登录')
const isStudentUser = computed(() => currentUser.value?.role === 'student')
const isOnStudentRoute = computed(() => route.path.startsWith('/student'))
const isTeacherUser = computed(() => currentUser.value?.role === 'teacher')
const isOnTeacherRoute = computed(() => route.path.startsWith('/teacher'))
const isAdminUser = computed(() => currentUser.value?.role === 'admin')
const isOnAdminRoute = computed(() => route.path.startsWith('/admin'))

const handleLoginSuccess = (user) => {
  if (user?.role !== 'student' && user?.role !== 'teacher' && user?.role !== 'admin') {
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
  } else if (user.role === 'admin') {
    router.push('/admin')
  } else {
    router.push('/')
  }
}

const handleLogout = () => {
  currentUser.value = null
  localStorage.removeItem('currentUser')
  router.push('/login')
}

const handleUpdateUser = (patch) => {
  if (!currentUser.value || !patch) return
  currentUser.value = { ...currentUser.value, ...patch }
  try {
    localStorage.setItem('currentUser', JSON.stringify(currentUser.value))
  } catch (e) {}
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

          <nav v-else-if="isAdminUser && isOnAdminRoute" class="section-nav section-nav-inline" aria-label="管理员页面导航">
            <button
              v-for="page in adminPageList"
              :key="page.key"
              class="nav-btn section-nav-btn"
              :class="{ active: (route.params.page === page.key) || (!route.params.page && page.key === 'profile') }"
              @click="() => router.push(`/admin/${page.key}`)">
              {{ page.label }}
            </button>
          </nav>

          <div v-if="(isStudentUser && isOnStudentRoute) || (isTeacherUser && isOnTeacherRoute) || (isAdminUser && isOnAdminRoute)" class="top-user-card">
            <span class="top-user-name">{{ currentUser.username }}</span>
            <span class="top-user-role">{{
              isStudentUser && isOnStudentRoute ? '学生账号'
                : isTeacherUser && isOnTeacherRoute ? '教师账号'
                  : isAdminUser && isOnAdminRoute ? '管理员账号' : ''
            }}</span>
          </div>

          <nav v-else-if="currentUser" class="nav-actions" aria-label="主导航">
            <button
              class="nav-btn"
              @click="() => router.push(
                currentUser.role === 'student' ? '/student'
                  : currentUser.role === 'teacher' ? '/teacher'
                    : currentUser.role === 'admin' ? '/admin' : '/'
              )"
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
          <component
            :is="Component"
            @login-success="handleLoginSuccess"
            @logout="handleLogout"
            @update-user="handleUpdateUser"
          />
        </router-view>
      </main>
    </div>
  </div>
</template>
