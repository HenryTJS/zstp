<script setup>
import { computed, reactive, ref, watch } from 'vue'

const props = defineProps({
  myCourseCatalog: { type: Array, required: true },
  courseInitDone: { type: Boolean, required: true },
  pendingPermissionRequests: { type: Array, required: true },
  /** 加载申请列表失败时的提示（可选） */
  permissionRequestsError: { type: String, default: '' },
  teachersByCourse: { type: Object, default: () => ({}) },
  teachersLoading: { type: Boolean, default: false }
})

const teachersLine = (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return '暂无授课教师信息'
  const list = props.teachersByCourse[cn]
  if (!Array.isArray(list) || !list.length) return '暂无授课教师信息'
  return list.map((t) => String(t?.username || '').trim()).filter(Boolean).join('、') || '暂无授课教师信息'
}

const emit = defineEmits(['enter-course', 'apply-new-course'])

/** 外网默认封面（如 dummyimage）失败时用占位，避免裂图 */
const coverFailed = reactive({})

const onCardCoverError = (courseName) => {
  const k = String(courseName || '').trim()
  if (k) coverFailed[k] = true
}

const onCardClick = (courseName) => {
  if (!props.courseInitDone) return
  emit('enter-course', String(courseName || '').trim())
}

const MY_COURSE_PAGE_SIZE = 6
const currentPage = ref(1)

const totalPages = computed(() => {
  const total = Array.isArray(props.myCourseCatalog) ? props.myCourseCatalog.length : 0
  return Math.max(1, Math.ceil(total / MY_COURSE_PAGE_SIZE))
})

const pagedMyCourses = computed(() => {
  const all = Array.isArray(props.myCourseCatalog) ? props.myCourseCatalog : []
  const start = (currentPage.value - 1) * MY_COURSE_PAGE_SIZE
  return all.slice(start, start + MY_COURSE_PAGE_SIZE)
})

watch(
  () => props.myCourseCatalog.length,
  () => {
    if (currentPage.value > totalPages.value) currentPage.value = totalPages.value
  }
)

const goPage = (p) => {
  const n = Number(p)
  if (!Number.isFinite(n)) return
  currentPage.value = Math.max(1, Math.min(totalPages.value, Math.trunc(n)))
}
</script>

<template>
  <article class="result-card">
    <div class="course-market-head">
      <h3 class="portal-section-title portal-section-title--orange">我的课程</h3>
      <div class="course-market-head-actions">
        <button type="button" class="nav-btn" @click="emit('apply-new-course')">申请新增课程</button>
      </div>
    </div>

    <div v-if="myCourseCatalog.length" class="course-market-grid my-course-grid-fixed">
      <article
        v-for="course in pagedMyCourses"
        :key="course.courseName"
        class="course-market-card course-market-card--clickable my-course-card-fixed"
        :class="{ 'is-disabled': !courseInitDone }"
        role="button"
        :tabindex="courseInitDone ? 0 : -1"
        @click="onCardClick(course.courseName)"
        @keydown.enter.prevent="onCardClick(course.courseName)"
        @keydown.space.prevent="onCardClick(course.courseName)"
      >
        <div class="course-market-card-body">
          <img
            v-if="course.coverUrl && !coverFailed[course.courseName]"
            :src="course.coverUrl"
            alt=""
            class="my-course-cover"
            @error="onCardCoverError(course.courseName)"
          />
          <div
            v-else
            class="my-course-cover my-course-cover--placeholder"
            :aria-label="`${course.courseName} 封面`"
          >
            <span>{{ course.courseName }}</span>
          </div>
          <h4 class="my-course-title ui-mt-8">{{ course.courseName }}</h4>
          <p class="my-course-teachers">
            授课教师：<template v-if="teachersLoading">加载中…</template><template v-else>{{ teachersLine(course.courseName) }}</template>
          </p>
        </div>
      </article>
    </div>
    <div v-if="myCourseCatalog.length > MY_COURSE_PAGE_SIZE" class="course-market-pagination my-course-dot-pagination">
      <button
        v-for="p in totalPages"
        :key="'teacher-my-course-page-' + p"
        type="button"
        class="my-course-page-dot"
        :class="{ 'is-active': currentPage === p }"
        :aria-label="'第 ' + p + ' 页'"
        @click="goPage(p)"
      />
    </div>

    <p v-if="permissionRequestsError" class="error-text ui-mt-8" role="alert">{{ permissionRequestsError }}</p>

    <div
      v-if="pendingPermissionRequests.length"
      class="panel-subtitle"
      style="margin-top: 16px; padding: 12px 14px; border-radius: 8px; background: rgba(0, 0, 0, 0.04)"
    >
      <strong>待审批申请</strong>
      <ul class="panel-bullets" style="margin-top: 8px; margin-bottom: 0">
        <li v-for="r in pendingPermissionRequests" :key="r.id">
          {{ r.courseName }}
          <span class="ui-muted-tag">
            {{ r.requestKind === 'CREATE_NEW' ? '（新课程）' : '（加入已有课程）' }}
          </span>
        </li>
      </ul>
    </div>

  </article>
</template>

<style>
@import '@/styles/teacher/teacher-portal.css';
</style>
<style scoped>
.my-course-cover{
  width:100%;
  height:168px;
  object-fit:cover;
  border-radius:10px;
  border:1px solid var(--ui-card-border);
}
.my-course-cover--placeholder{
  display:flex;
  align-items:center;
  justify-content:center;
  text-align:center;
  padding:10px;
  box-sizing:border-box;
  background:linear-gradient(145deg,#eef2ff 0%,#e0e7ff 55%,#dbeafe 100%);
  color:#3730a3;
  font-weight:700;
  font-size:14px;
  line-height:1.35;
}
.my-course-title{
  margin-bottom:4px;
  font-size:17px;
  font-weight:700;
  color:#0f172a;
}
.my-course-teachers{
  margin:0 0 4px;
  font-size:13px;
  color:#475569;
  line-height:1.45;
}
.course-market-card--clickable{
  cursor:pointer;
}
.course-market-card--clickable.is-disabled{
  cursor:not-allowed;
  opacity:.55;
  pointer-events:none;
}
.my-course-grid-fixed{
  grid-template-columns:repeat(3,minmax(0,1fr));
  min-height:auto;
  align-content:start;
}
.my-course-dot-pagination{
  margin-top:12px;
  gap:10px;
}
.my-course-page-dot{
  -webkit-appearance:none;
  appearance:none;
  display:inline-block;
  width:10px;
  height:10px;
  min-width:10px;
  min-height:10px;
  border-radius:50%;
  border:none;
  padding:0;
  background:#cbd5e1;
  cursor:pointer;
  line-height:0;
  font-size:0;
  box-shadow:none;
}
.my-course-page-dot.is-active{
  background:#4f46e5;
}
.my-course-card-fixed{
  height:260px;
}
.my-course-summary{
  margin:0;
  color:#475569;
  line-height:1.6;
  display:-webkit-box;
  -webkit-line-clamp:3;
  -webkit-box-orient:vertical;
  overflow:hidden;
  min-height:4.8em;
}
@media (max-width:768px){
  .my-course-grid-fixed{
    grid-template-columns:1fr;
  }
}
</style>

