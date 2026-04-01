import { createRouter, createWebHistory } from 'vue-router'
import LoginPage from '../components/LoginPage.vue'
import StudentPortal from '../components/StudentPortal.vue'
import TeacherPortal from '../components/TeacherPortal.vue'
import AccountSecurityPanel from '../components/AccountSecurityPanel.vue'

const routes = [
  {
    path: '/',
    redirect: () => {
      const u = JSON.parse(localStorage.getItem('currentUser') || 'null')
      if (!u) return '/login'
      if (u.role === 'student') return '/student'
      if (u.role === 'teacher') return '/teacher'
      return '/login'
    }
  },
  { path: '/login', component: LoginPage },
  {
    path: '/student/:page?',
    component: StudentPortal,
    props: (route) => ({ currentUser: JSON.parse(localStorage.getItem('currentUser') || 'null'), activePage: route.params.page || 'home' })
  },
  { path: '/teacher/:page?', component: TeacherPortal, props: (route) => ({ currentUser: JSON.parse(localStorage.getItem('currentUser') || 'null'), activePage: route.params.page || 'profile' }) },
  { path: '/security', component: AccountSecurityPanel, props: () => ({ currentUser: JSON.parse(localStorage.getItem('currentUser') || 'null') }) }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
