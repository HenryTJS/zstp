<script setup>
const props = defineProps({
  myCourseCatalog: { type: Array, required: true },
  courseInitDone: { type: Boolean, required: true },
  pendingPermissionRequests: { type: Array, required: true },
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
          <img :src="course.coverUrl" alt="" class="my-course-cover" />
          <h4 class="my-course-title ui-mt-8">{{ course.courseName }}</h4>
          <p class="my-course-teachers">
            授课教师：<template v-if="teachersLoading">加载中…</template><template v-else>{{ teachersLine(course.courseName) }}</template>
          </p>
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
<style scoped>
.my-course-cover{
  width:100%;
  height:168px;
  object-fit:cover;
  border-radius:10px;
  border:1px solid var(--ui-card-border);
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

