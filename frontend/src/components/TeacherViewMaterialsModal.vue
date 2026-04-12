<script setup>
defineProps({
  visible: { type: Boolean, required: true },
  pointName: { type: String, default: '' },
  materials: { type: Array, required: true }
})

const emit = defineEmits(['close', 'delete-material'])
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="emit('close')">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button type="button" class="modal-close" @click="emit('close')" aria-label="关闭">×</button>
        <h3 class="portal-section-title portal-section-title--emerald">已上传资料 - {{ pointName }}</h3>
        <div v-if="!materials.length" class="panel-subtitle">暂无资料。</div>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>标题</th>
              <th>描述</th>
              <th>文件名</th>
              <th>上传者</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in materials" :key="m.id">
              <td>{{ m.title }}</td>
              <td>{{ m.description || '-' }}</td>
              <td>{{ m.fileName || '-' }}</td>
              <td>{{ m.teacherName || '-' }}</td>
              <td>{{ m.createdAt ? new Date(m.createdAt).toLocaleString() : '-' }}</td>
              <td>
                <button type="button" class="danger-btn" @click="emit('delete-material', m.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style>
@import './teacher-portal.css';
</style>
