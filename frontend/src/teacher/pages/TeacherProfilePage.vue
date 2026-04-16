<script setup>
const props = defineProps({
  currentUser: { type: Object, required: true },
  userInitial: { type: String, default: '' },
  profileForm: { type: Object, required: true },
  colleges: { type: Array, required: true },
  selectedCollege: { type: String, default: '' },
  authorizedCourseCount: { type: Number, default: 0 },
  accessibleMaterialsCount: { type: Number, default: 0 },
  publishedTestCount: { type: Number, default: 0 },
  profileMessage: { type: String, default: '' }
})

const emit = defineEmits(['edit-profile', 'change-password', 'logout'])
</script>

<template>
  <article class="result-card profile-hero-card">
    <div class="profile-hero-main">
      <div class="profile-avatar">{{ userInitial }}</div>
      <div>
        <h3>{{ profileForm.username || currentUser.username }}</h3>
      </div>
    </div>
  </article>

  <div class="profile-grid">
    <article class="result-card profile-overview-card">
      <h3 class="portal-section-title">教学统计</h3>
      <div class="profile-stat-list">
        <div>
          <span>已授权课程</span>
          <strong>{{ authorizedCourseCount }}</strong>
        </div>
        <div>
          <span>已上传资料</span>
          <strong>{{ accessibleMaterialsCount }}</strong>
        </div>
        <div>
          <span>已发布测试</span>
          <strong>{{ publishedTestCount }}</strong>
        </div>
      </div>
    </article>

    <article class="result-card profile-detail-card">
      <h3 class="portal-section-title portal-section-title--teal">资料设置</h3>
      <div class="grid-form">
        <label>
          用户名
          <div class="panel-subtitle">{{ profileForm.username || currentUser.username }}</div>
        </label>
        <label>
          学工号
          <div class="panel-subtitle">{{ currentUser.workId || '未设置' }}</div>
        </label>
        <label>
          邮箱
          <div class="panel-subtitle">{{ profileForm.email || currentUser.email }}</div>
        </label>
        <label>
          学院
          <div class="panel-subtitle">
            {{ (colleges.find((c) => c.code === selectedCollege) || {}).name || '未设置' }}
          </div>
        </label>
      </div>
      <div class="profile-btn-row">
        <button type="button" class="nav-btn" @click="emit('edit-profile')">编辑资料</button>
        <button type="button" class="nav-btn" @click="emit('change-password')">修改密码</button>
        <button type="button" class="danger-btn profile-logout-btn" @click="emit('logout')">退出登录</button>
      </div>
      <p v-if="profileMessage" class="ok-text">{{ profileMessage }}</p>
    </article>
  </div>
</template>

