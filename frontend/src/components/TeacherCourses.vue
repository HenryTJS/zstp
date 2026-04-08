<script setup>
const props = defineProps({
  myCourseCatalog: { type: Array, required: true },
  courseInitDone: { type: Boolean, required: true },
  pendingPermissionRequests: { type: Array, required: true }
})

const emit = defineEmits([
  'enter-course',
  'quit-course'
])
</script>

<template>
  <article class="result-card">
    <div class="course-market-head">
      <h3>我的课程</h3>
    </div>

    <div v-if="myCourseCatalog.length" class="course-market-grid">
      <article v-for="course in myCourseCatalog" :key="course.courseName" class="course-market-card">
        <div class="course-market-card-body">
          <img :src="course.coverUrl" alt="" style="width: 100%; height: 140px; object-fit: cover; border-radius: 8px" />
          <h4 class="ui-mt-8">{{ course.courseName }}</h4>
          <p class="panel-subtitle">{{ course.summary }}</p>
        </div>

        <div class="course-market-card-actions course-market-card-actions--split">
          <button
            type="button"
            class="match-button"
            :disabled="!courseInitDone"
            @click="emit('enter-course', course.courseName)"
          >
            进入课程
          </button>
          <button
            type="button"
            class="cancel-button"
            :disabled="!courseInitDone"
            @click="emit('quit-course', course.courseName)"
          >
            退出课程
          </button>
        </div>
      </article>
    </div>

    <p v-else class="panel-subtitle ui-mt-12">当前没有已授权课程，请在导航栏搜索课程并申请权限。</p>

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

<style src="./teacher-portal.css"></style>

