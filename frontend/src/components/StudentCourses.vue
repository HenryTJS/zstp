<script setup>
import { computed, reactive } from 'vue'

const props = defineProps({
  joinedCourses: { type: Array, required: true },
  myCourseCatalog: { type: Array, required: true },
  stateHydrated: { type: Boolean, required: true },
  /** 课程名 -> [{ teacherId, username }] */
  teachersByCourse: { type: Object, default: () => ({}) },
  teachersLoading: { type: Boolean, default: false },
  /** 课程名 -> { percent, completed, total } | null（加载失败） */
  courseProgressByCourse: { type: Object, default: () => ({}) },
  courseProgressLoading: { type: Boolean, default: false }
})

const teachersLine = (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return '暂无授课教师信息'
  const list = props.teachersByCourse[cn]
  if (!Array.isArray(list) || !list.length) return '暂无授课教师信息'
  return list.map((t) => String(t?.username || '').trim()).filter(Boolean).join('、') || '暂无授课教师信息'
}

const emit = defineEmits(['open-detail'])

const coverFailed = reactive({})
const onCardCoverError = (courseName) => {
  const k = String(courseName || '').trim()
  if (k) coverFailed[k] = true
}

const onCardClick = (courseName) => {
  if (!props.stateHydrated) return
  emit('open-detail', String(courseName || '').trim())
}

const myCourses = computed(() => {
  const joined = new Set((Array.isArray(props.joinedCourses) ? props.joinedCourses : []).map((x) => String(x || '').trim()))
  const all = Array.isArray(props.myCourseCatalog) ? props.myCourseCatalog : []
  return all.filter((c) => joined.has(String(c?.courseName || '').trim()))
})

const progressFor = (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return undefined
  const v = props.courseProgressByCourse[cn]
  return v
}
</script>

<template>
  <section class="panel-stack">
    <article class="result-card">
      <div class="course-market-head">
        <div>
          <h3 class="portal-section-title portal-section-title--sky">我的课程</h3>
        </div>
      </div>

      <div v-if="myCourses.length" class="course-market-grid">
        <article
          v-for="course in myCourses"
          :key="course.courseName"
          class="course-market-card course-market-card--clickable"
          :class="{ 'is-disabled': !stateHydrated }"
          role="button"
          :tabindex="stateHydrated ? 0 : -1"
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
              <template v-if="teachersLoading">加载中…</template><template v-else>{{ teachersLine(course.courseName) }}</template>
            </p>
            <div class="my-course-progress">
              <template v-if="courseProgressLoading && progressFor(course.courseName) === undefined">
                <span class="my-course-progress-hint">进度加载中…</span>
              </template>
              <template v-else-if="progressFor(course.courseName) === null">
                <span class="my-course-progress-hint">进度暂不可用</span>
              </template>
              <template v-else-if="progressFor(course.courseName)">
                <div class="my-course-progress-head">
                  <span>课程进度</span>
                  <strong>{{ progressFor(course.courseName).percent || 0 }}%</strong>
                </div>
                <div class="my-course-progress-bar-wrap" aria-hidden="true">
                  <div
                    class="my-course-progress-bar"
                    :style="{ width: Math.min(100, Math.max(0, Number(progressFor(course.courseName).percent) || 0)) + '%' }"
                  />
                </div>
                <p class="my-course-progress-meta">
                  {{ progressFor(course.courseName).completed || 0 }} / {{ progressFor(course.courseName).total || 0 }} 项已完成
                </p>
              </template>
            </div>
          </div>
        </article>
      </div>
    </article>
  </section>
</template>

<style>
@import './student-portal.css';
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
  margin:0 0 8px;
  font-size:13px;
  color:#475569;
  line-height:1.45;
}

.course-market-sub{
  margin:4px 0 0;
  font-size:12px;
  color:#64748b;
  line-height:1.4;
}

.my-course-progress{
  margin-top:auto;
  padding-top:10px;
  border-top:1px solid rgba(15,23,42,.06);
}

.my-course-progress-head{
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:8px;
  font-size:12px;
  color:#64748b;
  margin-bottom:6px;
}

.my-course-progress-head strong{
  font-size:14px;
  font-weight:800;
  color:#4f46e5;
  font-variant-numeric:tabular-nums;
}

.my-course-progress-bar-wrap{
  height:6px;
  border-radius:999px;
  background:#e2e8f0;
  overflow:hidden;
}

.my-course-progress-bar{
  height:100%;
  border-radius:999px;
  background:linear-gradient(90deg,#6366f1,#8b5cf6);
  transition:width .25s ease;
}

.my-course-progress-meta{
  margin:6px 0 0;
  font-size:11px;
  color:#94a3b8;
}

.my-course-progress-hint{
  font-size:12px;
  color:#94a3b8;
}
.course-market-card--clickable{
  cursor:pointer;
}
.course-market-card--clickable.is-disabled{
  cursor:not-allowed;
  opacity:.55;
  pointer-events:none;
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
</style>

