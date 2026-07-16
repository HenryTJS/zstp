<script setup>
import { computed } from 'vue'

const props = defineProps({
  username: { type: String, required: true },
  avatarUrl: { type: String, default: '' },
  size: { type: Number, default: 52 }
})

// 基于用户名生成稳定的颜色
const colorPalette = [
  '#4f46e5', '#0891b2', '#059669', '#d97706',
  '#dc2626', '#7c3aed', '#db2777', '#2563eb',
  '#0d9488', '#ca8a04', '#9333ea', '#e11d48'
]

const initial = computed(() => {
  if (!props.username) return '?'
  return props.username.charAt(0).toUpperCase()
})

const bgColor = computed(() => {
  if (!props.username) return colorPalette[0]
  let hash = 0
  for (let i = 0; i < props.username.length; i++) {
    hash = props.username.charCodeAt(i) + ((hash << 5) - hash)
  }
  const index = Math.abs(hash) % colorPalette.length
  return colorPalette[index]
})

const fontSize = computed(() => Math.round(props.size * 0.42))
</script>

<template>
  <div v-if="avatarUrl" class="default-avatar-img" :style="{ width: size + 'px', height: size + 'px' }">
    <img :src="avatarUrl" :style="{ width: size + 'px', height: size + 'px' }" alt="头像" />
  </div>
  <div v-else class="default-avatar-svg" :style="{
    width: size + 'px',
    height: size + 'px',
    backgroundColor: bgColor,
    fontSize: fontSize + 'px'
  }">
    {{ initial }}
  </div>
</template>

<style scoped>
.default-avatar-img,
.default-avatar-svg {
  border-radius: 50%;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.default-avatar-svg {
  color: #fff;
  font-weight: 700;
  line-height: 1;
  user-select: none;
  -webkit-user-select: none;
}

.default-avatar-img img {
  object-fit: cover;
  display: block;
  border-radius: 50%;
}
</style>
