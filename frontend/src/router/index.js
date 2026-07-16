import { createRouter, createWebHistory } from 'vue-router'
import LoginPage from '../shared/components/LoginPage.vue'
import StudentPortal from '../student/StudentPortal.vue'
import TeacherPortal from '../teacher/TeacherPortal.vue'
import AdminPortal from '../admin/AdminPortal.vue'
import AccountSecurityPanel from '../shared/components/AccountSecurityPanel.vue'

const routes = [
  {
    path: '/',
    redirect: () => {
      const u = JSON.parse(localStorage.getItem('currentUser') || 'null')
      if (!u) return '/login'
      if (u.role === 'student') return '/student/home'
      if (u.role === 'teacher') return '/teacher/profile'
      if (u.role === 'admin') return '/admin/profile'
      return '/login'
    }
  },
  { path: '/login', component: LoginPage },
  { path: '/student', redirect: '/student/home' },
  {
    path: '/student/:page',
    component: StudentPortal,
    props: (route) => ({
      currentUser: JSON.parse(localStorage.getItem('currentUser') || 'null'),
      activePage: route.params.page || 'home'
    })
  },
  { path: '/teacher', redirect: '/teacher/profile' },
  {
    path: '/teacher/:page',
    component: TeacherPortal,
    props: (route) => ({
      currentUser: JSON.parse(localStorage.getItem('currentUser') || 'null'),
      activePage: route.params.page || 'profile'
    })
  },
  { path: '/admin/course-permissions', redirect: '/admin/profile' },
  { path: '/admin', redirect: '/admin/profile' },
  {
    path: '/admin/:page',
    component: AdminPortal,
    props: (route) => ({
      currentUser: JSON.parse(localStorage.getItem('currentUser') || 'null'),
      activePage: route.params.page || 'profile'
    })
  },
  { path: '/security', component: AccountSecurityPanel, props: () => ({ currentUser: JSON.parse(localStorage.getItem('currentUser') || 'null') }) }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const readStoredUser = () => {
  try {
    return JSON.parse(localStorage.getItem('currentUser') || 'null')
  } catch {
    return null
  }
}

/** 检查 localStorage 中是否存在有效的 JWT token */
const hasValidToken = () => {
  try {
    return !!localStorage.getItem('authToken')
  } catch {
    return false
  }
}

/** 未登录或角色不符时禁止进入各端门户（localStorage 清空后必须离开受保护路由） */
router.beforeEach((to) => {
  const u = readStoredUser()
  const hasToken = hasValidToken()

  // 如果 localStorage 中有用户信息但没有 token，视为未登录
  if (to.path !== '/' && to.path !== '/login') {
    if (!u || !hasToken) {
      return { path: '/login', replace: true }
    }
  }

  if (to.path === '/' || to.path === '/login') return true

  if (to.path === '/security' && !u) {
    return { path: '/login', replace: true }
  }

  const studentPath = to.path === '/student' || to.path.startsWith('/student/')
  const teacherPath = to.path === '/teacher' || to.path.startsWith('/teacher/')
  const adminPath = to.path === '/admin' || to.path.startsWith('/admin/')

  if (studentPath && (!u || u.role !== 'student')) {
    return { path: '/login', replace: true }
  }
  if (teacherPath && (!u || u.role !== 'teacher')) {
    return { path: '/login', replace: true }
  }
  if (adminPath && (!u || u.role !== 'admin')) {
    return { path: '/login', replace: true }
  }

  return true
})

export default router

