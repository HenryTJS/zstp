<script setup>
import { ref } from 'vue'

const emit = defineEmits(['change'])
const el = ref(null)

defineExpose({
  open: () => el.value?.click()
})
</script>

<template>
  <input
    ref="el"
    type="file"
    accept=".md,text/markdown"
    style="display:none"
    @change="emit('change', $event)"
  />
</template>

<style>
@import '@/teacher/styles/teacher-portal.css';
</style>
