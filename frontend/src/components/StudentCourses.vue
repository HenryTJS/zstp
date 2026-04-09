<script setup>
import { computed } from 'vue'

const props = defineProps({
  joinedCourses: { type: Array, required: true },
  myCourseCatalog: { type: Array, required: true },
  stateHydrated: { type: Boolean, required: true }
})

const emit = defineEmits([
  'enter',
  'quit'
])
const onEnter = (course) => emit('enter', course)
const onQuit = (course) => emit('quit', course)

const myCourses = computed(() => {
  const joined = new Set((Array.isArray(props.joinedCourses) ? props.joinedCourses : []).map((x) => String(x || '').trim()))
  const all = Array.isArray(props.myCourseCatalog) ? props.myCourseCatalog : []
  return all.filter((c) => joined.has(String(c?.courseName || '').trim()))
})
</script>

<template>
  <section class="panel-stack">
    <article class="result-card">
      <div class="course-market-head">
        <h3>我的课程</h3>
      </div>

      <div v-if="myCourses.length" class="course-market-grid">
        <article v-for="course in myCourses" :key="course.courseName" class="course-market-card">
          <div class="course-market-card-body">
            <img :src="course.coverUrl" alt="" class="my-course-cover" />
            <h4 class="my-course-title ui-mt-8">{{ course.courseName }}</h4>
          </div>
          <div class="course-market-card-actions course-market-card-actions--split">
            <button
              type="button"
              class="match-button"
              :disabled="!stateHydrated"
              @click="onEnter(course.courseName)"
            >
              进入课程
            </button>
            <button
              type="button"
              class="cancel-button"
              :disabled="!stateHydrated"
              @click="onQuit(course.courseName)"
            >
              退出课程
            </button>
          </div>
        </article>
      </div>
      <p v-else class="panel-subtitle ui-mt-12">当前没有已加入课程，请在导航栏搜索课程并进入详情页加入。</p>
    </article>
  </section>
</template>

<style src="./student-portal.css"></style>
<style scoped>
.my-course-cover{
  width:100%;
  height:168px;
  object-fit:cover;
  border-radius:10px;
  border:1px solid var(--ui-card-border);
}
.my-course-title{
  margin-bottom:6px;
  font-size:17px;
  font-weight:700;
  color:#0f172a;
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

