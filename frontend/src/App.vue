<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute, isNavigationFailure, NavigationFailureType } from 'vue-router'
import DiscussionNotificationBell from './components/DiscussionNotificationBell.vue'
import AdminPermissionRequestBell from './components/AdminPermissionRequestBell.vue'
import { listCourseCatalog, listTeachersForCourses } from './api/client'

const router = useRouter()
const route = useRoute()

const currentUser = ref(JSON.parse(localStorage.getItem('currentUser') || 'null'))

const studentPageList = [
  { key: 'courses', label: '课程广场' },
  { key: 'review', label: '错题与记录' },
  { key: 'home', label: '个人中心' }
]

const teacherPageList = [
  { key: 'courses', label: '课程广场' },
  { key: 'profile', label: '个人中心' }
]

const adminPageList = [
  { key: 'user-stats', label: '用户与导入' },
  { key: 'announcements', label: '公告管理' },
  { key: 'course-configs', label: '课程权重与学分' },
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

const handleLogout = async () => {
  // 先离开受保护路由再清登录态，避免 StudentPortal 等仍在 /student 时收到 currentUser=null（required prop）导致渲染异常、视图卡在个人中心
  try {
    await router.replace({ path: '/login' })
  } catch (e) {
    if (!isNavigationFailure(e, NavigationFailureType.duplicated)) {
      window.location.assign('/login')
      return
    }
  }
  localStorage.removeItem('currentUser')
  currentUser.value = null
}

const handleUpdateUser = (patch) => {
  if (!currentUser.value || !patch) return
  currentUser.value = { ...currentUser.value, ...patch }
  try {
    localStorage.setItem('currentUser', JSON.stringify(currentUser.value))
  } catch (e) {}
}

const navCourseSearch = ref('')
const navCourseCatalog = ref([])
const navTeachersByCourse = ref({})
const navSearchLoading = ref(false)
const navSearchError = ref('')
const isSearchOpen = computed(() =>
  ((isStudentUser.value && isOnStudentRoute.value) || (isTeacherUser.value && isOnTeacherRoute.value)) &&
  String(navCourseSearch.value || '').trim().length > 0
)
const showSearchResultsOnly = computed(() => isSearchOpen.value)

const normalize = (s) => String(s || '').trim().toLowerCase()
const scoreCourse = (query, c) => {
  const q = normalize(query)
  const name = normalize(c?.courseName)
  if (!q || !name) return 0
  if (name === q) return 1000
  if (name.startsWith(q)) return 800
  const idx = name.indexOf(q)
  if (idx >= 0) return 600 - Math.min(idx, 50)
  let hit = 0
  for (const ch of q) if (name.includes(ch)) hit += 1
  return hit > 0 ? 200 + hit : 0
}

const rankedSearchCourses = computed(() => {
  const q = String(navCourseSearch.value || '').trim()
  if (!q) return []
  return (Array.isArray(navCourseCatalog.value) ? navCourseCatalog.value : [])
    .map((c) => ({ ...c, _score: scoreCourse(q, c) }))
    .sort((a, b) => (b._score - a._score) || String(a.courseName || '').localeCompare(String(b.courseName || ''), 'zh-CN'))
})

const refreshNavCourseCatalog = async () => {
  if (!currentUser.value?.id) {
    navCourseCatalog.value = []
    return
  }
  navSearchLoading.value = true
  navSearchError.value = ''
  try {
    const { data } = await listCourseCatalog(currentUser.value.id)
    const items = Array.isArray(data?.items) ? data.items : []
    navCourseCatalog.value = items
    try {
      const names = items.map((x) => String(x?.courseName || '').trim()).filter(Boolean)
      if (names.length) {
        const { data: tData } = await listTeachersForCourses(names)
        navTeachersByCourse.value = tData && typeof tData === 'object' ? tData : {}
      } else {
        navTeachersByCourse.value = {}
      }
    } catch {
      navTeachersByCourse.value = {}
    }
  } catch (e) {
    navCourseCatalog.value = []
    navTeachersByCourse.value = {}
    navSearchError.value = e?.response?.data?.message || '课程搜索加载失败'
  } finally {
    navSearchLoading.value = false
  }
}

watch(
  () => [currentUser.value?.id, route.path],
  () => {
    const onPortal = (isStudentUser.value && isOnStudentRoute.value) || (isTeacherUser.value && isOnTeacherRoute.value)
    if (!onPortal) return
    void refreshNavCourseCatalog()
  },
  { immediate: true }
)

const openCourseFromSearch = async (course) => {
  const cn = String(course?.courseName || '').trim()
  if (!cn) return
  const role = currentUser.value?.role
  // 先完成路由跳转再清空搜索词，避免「先卸载 router-view 再导航」导致仍停留在课程广场等旧页
  try {
    if (role === 'student') {
      await router.push({ path: '/student/course-detail', query: { course: cn } })
    } else if (role === 'teacher') {
      await router.push({ path: '/teacher/course-detail', query: { course: cn } })
    }
  } finally {
    navCourseSearch.value = ''
  }
}

const teachersTextForCourse = (courseName) => {
  const list = navTeachersByCourse.value[String(courseName || '').trim()]
  if (!Array.isArray(list) || !list.length) return '暂无授课教师信息'
  return list.map((t) => String(t?.username || '').trim()).filter(Boolean).join('、') || '暂无授课教师信息'
}

</script>

<template>
  <div class="app-page">
    <header class="top-nav">
      <div class="top-nav-shell">
        <div
          class="top-nav-inner"
          :class="{
            'top-nav-inner-student': isStudentUser && isOnStudentRoute,
            'top-nav-inner-teacher': isTeacherUser && isOnTeacherRoute,
            'top-nav-inner-admin': isAdminUser && isOnAdminRoute
          }"
        >
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

          <div v-if="(isStudentUser && isOnStudentRoute) || (isTeacherUser && isOnTeacherRoute)" class="nav-course-search">
            <input
              v-model="navCourseSearch"
              class="match-height nav-course-search-input"
              placeholder="搜索全部课程"
            />
          </div>

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

          <DiscussionNotificationBell
            v-if="currentUser?.id && ((isStudentUser && isOnStudentRoute) || (isTeacherUser && isOnTeacherRoute))"
            :user-id="currentUser.id"
          />

          <AdminPermissionRequestBell
            v-if="currentUser?.id && isAdminUser && isOnAdminRoute"
            :admin-user-id="currentUser.id"
          />

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
      <main class="panel-wrap app-main-with-search">
        <router-view v-slot="{ Component }">
          <component
            :is="Component"
            :key="route.fullPath"
            @login-success="handleLoginSuccess"
            @logout="handleLogout"
            @update-user="handleUpdateUser"
          />
        </router-view>
        <div
          v-if="showSearchResultsOnly"
          class="nav-search-overlay"
          role="dialog"
          aria-modal="true"
          aria-label="课程搜索结果"
          @click.self="navCourseSearch = ''"
        >
          <section class="result-card nav-search-page-block">
            <div class="nav-search-header">
              <h3>课程搜索结果</h3>
              <p class="panel-subtitle">关键词：{{ navCourseSearch }}</p>
            </div>
            <p v-if="navSearchLoading" class="panel-subtitle">加载课程中...</p>
            <p v-else-if="navSearchError" class="error-text">{{ navSearchError }}</p>
            <template v-else>
              <p class="panel-subtitle nav-search-count">共 {{ rankedSearchCourses.length }} 门课程，按匹配度从高到低</p>
              <div v-if="rankedSearchCourses.length" class="nav-search-list">
                <button
                  v-for="c in rankedSearchCourses"
                  :key="c.courseName"
                  type="button"
                  class="nav-search-row"
                  @click.stop="openCourseFromSearch(c)"
                >
                  <img :src="c.coverUrl" alt="" class="nav-search-cover" />
                  <span class="nav-search-main">
                    <span class="nav-search-name">{{ c.courseName }}</span>
                    <span class="nav-search-teachers">授课教师：{{ teachersTextForCourse(c.courseName) }}</span>
                    <span class="nav-search-summary">{{ c.summary || '暂无课程简介' }}</span>
                  </span>
                  <span class="nav-search-action" :class="{ 'is-access': c.hasAccess }">课程介绍</span>
                </button>
              </div>
            </template>
          </section>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-main-with-search {
  position: relative;
  min-height: min(70vh, 720px);
}
.nav-search-overlay {
  position: absolute;
  inset: 0;
  z-index: 40;
  overflow: auto;
  padding: 4px 0 20px;
  background: rgba(248, 250, 252, 0.88);
  backdrop-filter: blur(10px);
}
.nav-course-search { position: relative; }
.nav-search-page-block {
  margin-bottom: 14px;
  border-radius: var(--ui-card-radius);
  border: 1px solid var(--ui-card-border);
  background: linear-gradient(165deg, #ffffff 0%, #fafaff 50%, #f5f3ff 100%);
  box-shadow: var(--shadow-card);
}
.nav-search-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 6px;
}
.nav-search-count { margin-bottom: 10px; }
.nav-search-list { display: grid; gap: 10px; }
.nav-search-row {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--ui-card-border);
  border-radius: 12px;
  padding: 10px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: all .18s ease;
}
.nav-search-row:hover {
  transform: translateY(-2px);
  border-color: rgba(99, 102, 241, 0.35);
  box-shadow: var(--shadow-card-hover);
}
.nav-search-cover {
  width: 96px;
  height: 60px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--ui-accent-100);
}
.nav-search-main { display: grid; gap: 4px; min-width: 0; }
.nav-search-name { font-weight: 700; color: #0f172a; letter-spacing: -0.02em; }
.nav-search-teachers { font-size: 12px; color: #64748b; }
.nav-search-summary {
  font-size: 13px;
  color: #475569;
  white-space: normal;
  word-break: break-word;
  line-height: 1.45;
}
.nav-search-action {
  color: var(--ui-accent-700);
  font-size: 12px;
  border: 1px solid var(--ui-accent-200);
  background: var(--ui-accent-50);
  border-radius: 999px;
  padding: 5px 10px;
  white-space: nowrap;
}
.nav-search-action.is-access {
  background: #ecfdf3;
  color: #0f766e;
  border-color: #bbf7d0;
}
@media (max-width: 900px) {
  .nav-search-row {
    grid-template-columns: 1fr;
    align-items: start;
  }
  .nav-search-cover { width: 100%; height: 140px; }
  .nav-search-action { justify-self: start; }
}
</style>
