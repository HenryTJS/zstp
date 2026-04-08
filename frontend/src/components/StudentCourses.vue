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
            <img :src="course.coverUrl" alt="" style="width: 100%; height: 140px; object-fit: cover; border-radius: 8px" />
            <h4 class="ui-mt-8">{{ course.courseName }}</h4>
            <p class="panel-subtitle">{{ course.summary }}</p>
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

