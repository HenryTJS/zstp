<script setup>
const props = defineProps({
  annLoading: { type: Boolean, required: true },
  annError: { type: String, required: false, default: '' },
  announcements: { type: Array, required: true },
  formatAnnTime: { type: Function, required: true }
})
</script>

<template>
  <section class="panel-stack">
    <article class="result-card">
      <h3>平台公告</h3>
      <p v-if="annLoading && !announcements.length" class="panel-subtitle">加载中…</p>
      <p v-else-if="annError" class="error-text">{{ annError }}</p>
      <p v-else-if="!announcements.length" class="panel-subtitle">暂无公告。</p>
      <div v-else class="announcement-read-list">
        <article v-for="a in announcements" :key="a.id" class="result-card announcement-read-card">
          <h4 class="announcement-read-title">{{ a.title }}</h4>
          <p class="panel-subtitle announcement-read-meta">
            <span v-if="a.publisherName">{{ a.publisherName }}</span>
            <span v-if="a.publisherName && a.createdAt"> · </span>
            <span>{{ formatAnnTime(a.createdAt) }}</span>
          </p>
          <div class="announcement-read-body">{{ a.content }}</div>
        </article>
      </div>
    </article>
  </section>
</template>

<style src="./student-portal.css"></style>

