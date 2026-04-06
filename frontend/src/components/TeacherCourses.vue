<script setup>
import { computed } from 'vue'

const props = defineProps({
  teacherCoursesSearch: { type: String, required: true },
  pagedMarketCourses: { type: Array, required: true },
  allMarketCourses: { type: Array, required: true },
  marketCoursesLoading: { type: Boolean, required: true },
  marketCoursesError: { type: String, required: false, default: '' },

  catalogCourses: { type: Array, required: true },
  permissionRequestByCourse: { type: Object, required: true },
  pendingPermissionRequests: { type: Array, required: true },
  teacherPermissionRequestsLoading: { type: Boolean, required: true },

  courseInitDone: { type: Boolean, required: true },

  teacherCoursesPage: { type: Number, required: true },
  marketTotalPages: { type: Number, required: true }
})

const emit = defineEmits([
  'update:teacherCoursesSearch',
  'update:teacherCoursesPage',
  'enter-course',
  'quit-course',
  'open-permission-request',
  'open-new-course-permission-request'
])

const searchModel = computed({
  get: () => props.teacherCoursesSearch,
  set: (v) => emit('update:teacherCoursesSearch', v)
})

const setPage = (p) => emit('update:teacherCoursesPage', p)
</script>

<template>
  <article class="result-card">
    <div class="course-market-head">
      <h3>课程广场</h3>
      <div class="course-market-head-actions">
        <input
          v-model="searchModel"
          class="match-height"
          placeholder="搜索课程名称"
          style="max-width: 280px"
        />
        <button type="button" class="match-button match-height" @click="emit('open-new-course-permission-request')">
          申请新课程
        </button>
      </div>
    </div>

    <p v-if="marketCoursesLoading && !allMarketCourses.length" class="panel-subtitle ui-mt-12">
      加载中…
    </p>
    <p v-else-if="marketCoursesError" class="error-text ui-mt-12">{{ marketCoursesError }}</p>

    <div v-if="pagedMarketCourses.length" class="course-market-grid">
      <article v-for="course in pagedMarketCourses" :key="course" class="course-market-card">
        <div class="course-market-card-body">
          <h4>{{ course }}</h4>
          <p class="panel-subtitle course-card-teachers">
            <template v-if="catalogCourses.includes(course)">
              已授权
            </template>
            <template v-else-if="permissionRequestByCourse[course]?.status === 'PENDING'">
              申请中
            </template>
            <template v-else-if="permissionRequestByCourse[course]?.status === 'REJECTED'">
              已拒绝：{{ permissionRequestByCourse[course]?.adminReason || '（无理由）' }}
            </template>
            <template v-else-if="permissionRequestByCourse[course]?.status === 'APPROVED'">
              已通过：等待权限同步
            </template>
            <template v-else>
              未获得权限
            </template>
          </p>
        </div>

        <div class="course-market-card-actions course-market-card-actions--split">
          <template v-if="catalogCourses.includes(course)">
            <button
              type="button"
              class="match-button"
              :disabled="!courseInitDone"
              @click="emit('enter-course', course)"
            >
              进入课程
            </button>
            <button
              type="button"
              class="cancel-button"
              :disabled="!courseInitDone"
              @click="emit('quit-course', course)"
            >
              退出课程
            </button>
          </template>

          <template v-else>
            <button
              type="button"
              class="match-button"
              :disabled="
                !courseInitDone ||
                teacherPermissionRequestsLoading ||
                permissionRequestByCourse[course]?.status === 'PENDING' ||
                permissionRequestByCourse[course]?.status === 'APPROVED'
              "
              @click="emit('open-permission-request', course)"
            >
              {{
                permissionRequestByCourse[course]?.status === 'PENDING'
                  ? '申请中'
                  : permissionRequestByCourse[course]?.status === 'APPROVED'
                    ? '已审批通过'
                    : '加入课程'
              }}
            </button>
          </template>
        </div>
      </article>
    </div>

    <p v-else class="panel-subtitle ui-mt-12">暂无课程。</p>

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

    <nav v-if="marketTotalPages > 1" class="course-market-pagination" aria-label="课程列表分页">
      <button
        v-for="page in marketTotalPages"
        :key="page"
        type="button"
        class="course-page-num"
        :class="{ 'is-active': teacherCoursesPage === page }"
        :aria-current="teacherCoursesPage === page ? 'page' : undefined"
        @click="setPage(page)"
      >
        {{ page }}
      </button>
    </nav>
  </article>
</template>

<style src="./teacher-portal.css"></style>

