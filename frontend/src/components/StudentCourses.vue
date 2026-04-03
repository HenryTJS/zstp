<script setup>
import { computed } from 'vue'

const props = defineProps({
  joinedCourses: { type: Array, required: true },
  joinedCoursesSearch: { type: String, required: true },
  joinedCoursesPage: { type: Number, required: true },
  marketTotalPages: { type: Number, required: true },
  pagedMarketCourses: { type: Array, required: true },
  teachersByCourseLoading: { type: Boolean, required: true },
  formatTeachersForCourse: { type: Function, required: true },
  stateHydrated: { type: Boolean, required: true }
})

const emit = defineEmits([
  'update:joinedCoursesSearch',
  'update:joinedCoursesPage',
  'join',
  'view',
  'enter',
  'quit'
])

const searchModel = computed({
  get: () => props.joinedCoursesSearch,
  set: (v) => emit('update:joinedCoursesSearch', v)
})

const setPage = (p) => {
  emit('update:joinedCoursesPage', p)
}

const onJoin = (course) => emit('join', course)
const onView = (course) => emit('view', course)
const onEnter = (course) => emit('enter', course)
const onQuit = (course) => emit('quit', course)
</script>

<template>
  <section class="panel-stack">
    <article class="result-card">
      <div class="course-market-head">
        <h3>课程广场</h3>
        <input
          v-model="searchModel"
          class="match-height"
          placeholder="搜索课程名称"
          style="max-width:280px"
        />
      </div>

      <div v-if="pagedMarketCourses.length" class="course-market-grid">
        <article v-for="course in pagedMarketCourses" :key="course" class="course-market-card">
          <div class="course-market-card-body">
            <h4>{{ course }}</h4>
            <p class="panel-subtitle course-card-teachers">
              <template v-if="teachersByCourseLoading">正在加载教师信息…</template>
              <template v-else-if="formatTeachersForCourse(course)">{{ formatTeachersForCourse(course) }}</template>
              <template v-else>暂无拥有该课程权限的教师</template>
            </p>
          </div>
          <div class="course-market-card-actions course-market-card-actions--split">
            <template v-if="!joinedCourses.includes(course)">
              <button
                type="button"
                class="match-button"
                :disabled="!stateHydrated"
                @click="onJoin(course)"
              >
                {{ stateHydrated ? '加入课程' : '加载中…' }}
              </button>
              <button
                type="button"
                class="nav-btn"
                :disabled="!stateHydrated"
                @click="onView(course)"
              >
                查看课程
              </button>
            </template>
            <template v-else>
              <button
                type="button"
                class="match-button"
                :disabled="!stateHydrated"
                @click="onEnter(course)"
              >
                进入课程
              </button>
              <button
                type="button"
                class="cancel-button"
                :disabled="!stateHydrated"
                @click="onQuit(course)"
              >
                退出课程
              </button>
            </template>
          </div>
        </article>
      </div>
      <p v-else class="panel-subtitle" style="margin-top:12px">未找到匹配课程。</p>

      <nav v-if="marketTotalPages > 1" class="course-market-pagination" aria-label="课程列表分页">
        <button
          v-for="page in marketTotalPages"
          :key="page"
          type="button"
          class="course-page-num"
          :class="{ 'is-active': joinedCoursesPage === page }"
          :aria-current="joinedCoursesPage === page ? 'page' : undefined"
          @click="setPage(page)"
        >
          {{ page }}
        </button>
      </nav>
    </article>
  </section>
</template>

<style src="./student-portal.css"></style>

