<script setup>
import { computed, provide, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import DiscussionNotificationBell from './shared/components/DiscussionNotificationBell.vue'
import AdminPermissionRequestBell from './admin/components/AdminPermissionRequestBell.vue'
import { listCourseCatalog, listTeachersForCourses } from './api/client'
import { appShellKey } from './appShell'

const router = useRouter()
const route = useRoute()

const currentUser = ref(JSON.parse(localStorage.getItem('currentUser') || 'null'))

const studentPageList = [
  { key: 'courses', label: '课程广场' },
  { key: 'home', label: '个人中心' }
]

const teacherPageList = [
  { key: 'courses', label: '课程广场' },
  { key: 'profile', label: '个人中心' }
]

const adminPageList = [
  { key: 'user-stats', label: '用户与导入' },
  { key: 'announcements', label: '公告管理' },
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
const isLoginRoute = computed(() => route.path === '/login')

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
    router.push('/student/home')
  } else if (user.role === 'teacher') {
    router.push('/teacher/profile')
  } else if (user.role === 'admin') {
    router.push('/admin/profile')
  } else {
    router.push('/')
  }
}

const handleLogout = () => {
  try {
    localStorage.removeItem('currentUser')
  } catch {
    /* ignore */
  }
  currentUser.value = null
  // 整页进入登录页，避免仅替换 router-view 导致地址栏与页面偶发不同步
  try {
    const { href } = router.resolve({ path: '/login' })
    window.location.assign(href)
  } catch {
    window.location.assign('/login')
  }
}

const handleUpdateUser = (patch) => {
  if (!currentUser.value || !patch) return
  currentUser.value = { ...currentUser.value, ...patch }
  try {
    localStorage.setItem('currentUser', JSON.stringify(currentUser.value))
  } catch (e) {}
}

provide(appShellKey, {
  loginSuccess: handleLoginSuccess,
  logout: handleLogout,
  updateUser: handleUpdateUser
})

const navCourseSearchDraft = ref('')
const navCourseSearchApplied = ref('')
const navCourseCatalog = ref([])
const navTeachersByCourse = ref({})
const navSearchLoading = ref(false)
const navSearchError = ref('')
const isSearchOpen = computed(() =>
  ((isStudentUser.value && isOnStudentRoute.value) || (isTeacherUser.value && isOnTeacherRoute.value)) &&
  String(navCourseSearchApplied.value || '').trim().length > 0
)
const showSearchResultsOnly = computed(() => isSearchOpen.value)

const normalize = (s) => String(s || '').trim().toLowerCase()
const scoreCourse = (query, c, teachersByCourse) => {
  const q = normalize(query)
  const name = normalize(c?.courseName)
  const rawTeacherList = teachersByCourse?.[String(c?.courseName || '').trim()]
  const teacherText = Array.isArray(rawTeacherList)
    ? rawTeacherList.map((t) => String(t?.username || '').trim()).filter(Boolean).join(' ')
    : ''
  const teachers = normalize(teacherText)

  if (!q) return 0

  const scoreField = (field) => {
    if (!field) return 0
    if (field === q) return 1000
    if (field.startsWith(q)) return 800
    const idx = field.indexOf(q)
    if (idx >= 0) return 600 - Math.min(idx, 50)
    let hit = 0
    for (const ch of q) if (field.includes(ch)) hit += 1
    return hit > 0 ? 200 + hit : 0
  }

  const nameScore = scoreField(name)
  const teacherScore = scoreField(teachers)
  // 课程名匹配优先，但教师匹配也可以命中
  return Math.max(nameScore, Math.floor(teacherScore * 0.9))
}

const rankedSearchCourses = computed(() => {
  const q = String(navCourseSearchApplied.value || '').trim()
  if (!q) return []
  return (Array.isArray(navCourseCatalog.value) ? navCourseCatalog.value : [])
    .map((c) => ({ ...c, _score: scoreCourse(q, c, navTeachersByCourse.value) }))
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
  // 先完成路由跳转再清空搜索词，避免先卸载视图造成页面停留异常
  try {
    if (role === 'student') {
      await router.push({ path: '/student/course-detail', query: { course: cn } })
    } else if (role === 'teacher') {
      await router.push({ path: '/teacher/course-detail', query: { course: cn } })
    }
  } finally {
    navCourseSearchDraft.value = ''
    navCourseSearchApplied.value = ''
  }
}

const teachersTextForCourse = (courseName) => {
  const list = navTeachersByCourse.value[String(courseName || '').trim()]
  if (!Array.isArray(list) || !list.length) return '暂无授课教师信息'
  return list.map((t) => String(t?.username || '').trim()).filter(Boolean).join('、') || '暂无授课教师信息'
}

const markdownToPlainText = (mdText) => {
  const s = String(mdText || '')
  return s
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]+\)/g, ' ')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+\.\s+/gm, '')
    .replace(/[*_~>]/g, '')
    .replace(/\r?\n+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

const courseSummaryExcerpt = (course) => {
  const raw = String(course?.summary || '').trim() || String(course?.syllabus || '').trim()
  const plain = markdownToPlainText(raw)
  if (!plain) return '暂无课程简介'
  return plain.length > 70 ? `${plain.slice(0, 70)}...` : plain
}

const applyNavCourseSearch = async () => {
  const q = String(navCourseSearchDraft.value || '').trim()
  navCourseSearchApplied.value = q
  if (!q) return
  // 若用户尚未进入过门户或刚登录，尽量保证课程数据就绪
  if (!Array.isArray(navCourseCatalog.value) || navCourseCatalog.value.length === 0) {
    await refreshNavCourseCatalog()
  }
}

/** 从 path 取首段，避免可选参数在部分环境下为空导致导航高亮错位 */
const pathSegmentAfterPrefix = (fullPath, prefix) => {
  const p = String(fullPath || '')
  if (!p.startsWith(prefix)) return ''
  let rest = p.slice(prefix.length)
  if (rest.startsWith('/')) rest = rest.slice(1)
  return (rest.split('/')[0] || '').trim()
}

const studentNavSegment = computed(() => {
  if (!isStudentUser.value || !isOnStudentRoute.value) return ''
  return pathSegmentAfterPrefix(route.path, '/student') || 'home'
})

const teacherNavSegment = computed(() => {
  if (!isTeacherUser.value || !isOnTeacherRoute.value) return ''
  return pathSegmentAfterPrefix(route.path, '/teacher') || 'profile'
})

const adminNavSegment = computed(() => {
  if (!isAdminUser.value || !isOnAdminRoute.value) return ''
  return pathSegmentAfterPrefix(route.path, '/admin') || 'profile'
})

</script>

<template>
  <div class="app-page" :class="{ 'app-page--login': isLoginRoute }">
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
            <h1>AI智能教学助手工作台</h1>
            <p class="brand-en">AI Intelligent Teaching Assistant Workspace</p>
          </div>

          <nav v-if="isStudentUser && isOnStudentRoute" class="section-nav section-nav-inline" aria-label="学生页面导航">
            <button
              v-for="page in studentPageList"
              :key="page.key"
              type="button"
              class="nav-btn section-nav-btn"
              :class="{ active: studentNavSegment === page.key }"
              @click="() => { navCourseSearchDraft = ''; navCourseSearchApplied = ''; router.push(`/student/${page.key}`) }">
              {{ page.label }}
            </button>
          </nav>

          <nav v-else-if="isTeacherUser && isOnTeacherRoute" class="section-nav section-nav-inline" aria-label="教师页面导航">
            <button
              v-for="page in teacherPageList"
              :key="page.key"
              type="button"
              class="nav-btn section-nav-btn"
              :class="{ active: teacherNavSegment === page.key }"
              @click="() => { navCourseSearchDraft = ''; navCourseSearchApplied = ''; router.push(`/teacher/${page.key}`) }">
              {{ page.label }}
            </button>
          </nav>

          <div v-if="(isStudentUser && isOnStudentRoute) || (isTeacherUser && isOnTeacherRoute)" class="nav-course-search">
            <input
              v-model="navCourseSearchDraft"
              class="match-height nav-course-search-input"
              placeholder="搜索课程/授课教师"
              @keydown.enter.prevent="applyNavCourseSearch"
            />
            <button
              type="button"
              class="nav-btn nav-course-search-btn"
              :disabled="!String(navCourseSearchDraft || '').trim()"
              @click="applyNavCourseSearch"
            >
              搜索
            </button>
          </div>

          <nav v-else-if="isAdminUser && isOnAdminRoute" class="section-nav section-nav-inline" aria-label="管理员页面导航">
            <button
              v-for="page in adminPageList"
              :key="page.key"
              type="button"
              class="nav-btn section-nav-btn"
              :class="{ active: adminNavSegment === page.key }"
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
                currentUser.role === 'student' ? '/student/home'
                  : currentUser.role === 'teacher' ? '/teacher/profile'
                    : currentUser.role === 'admin' ? '/admin/profile' : '/'
              )"
            >
              {{ currentRoleLabel }}主页
            </button>
          </nav>
        </div>
      </div>
    </header>

    <div class="app-shell" :class="{ 'app-shell--login': isLoginRoute }">
      <main class="panel-wrap app-main-with-search">
        <!-- 登录/登出/资料更新由 appShellKey provide 注入；避免额外包裹导致 activePage 错乱 -->
        <router-view v-if="!showSearchResultsOnly" />
        <section
          v-if="showSearchResultsOnly"
          class="result-card nav-search-page-block"
          role="region"
          aria-label="课程搜索结果"
        >
          <div class="nav-search-header">
            <h3>课程搜索结果</h3>
            <p class="panel-subtitle">关键词：{{ navCourseSearchApplied }}</p>
          </div>
          <p v-if="navSearchLoading" class="panel-subtitle">加载课程中…</p>
          <p v-else-if="navSearchError" class="error-text">{{ navSearchError }}</p>
          <template v-else>
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
                  <span class="nav-search-summary">{{ courseSummaryExcerpt(c) }}</span>
                </span>
                <span class="nav-search-action" :class="{ 'is-access': c.hasAccess }">课程介绍</span>
              </button>
            </div>
          </template>
        </section>
      </main>
    </div>
    <footer v-if="!isLoginRoute" class="app-footer-bar" role="contentinfo">
      <span>备案号：陕ICP备2026003727号</span>
    </footer>
  </div>
</template>

<style scoped>
.app-main-with-search {
  position: relative;
  min-height: min(70vh, 720px);
}

.app-page.app-page--login .app-main-with-search {
  min-height: 0;
}
.app-footer-bar {
  margin-top: 18px;
  padding: 12px 16px;
  border-top: 1px solid var(--ui-card-border);
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
  text-align: center;
}
.nav-course-search { position: relative; }
.nav-course-search {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nav-course-search-btn {
  height: 38px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--ui-card-border);
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  color: #0f172a;
}
.nav-course-search-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
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
  grid-template-columns: 236px minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
  border: 1px solid var(--ui-card-border);
  border-radius: 12px;
  min-height: 160px;
  padding: 14px;
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
  width: 236px;
  aspect-ratio: 16 / 9;
  height: auto;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--ui-accent-100);
}
.nav-search-main { display: grid; gap: 4px; min-width: 0; }
.nav-search-name { font-size: 18px; font-weight: 700; color: #0f172a; letter-spacing: -0.02em; }
.nav-search-teachers { font-size: 15px; color: #64748b; }
.nav-search-summary {
  font-size: 16px;
  color: #475569;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
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
  align-self: start;
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
  .nav-search-cover { width: 100%; aspect-ratio: 16 / 9; height: auto; }
  .nav-search-action { justify-self: start; }
}
</style>

