<script setup>
const props = defineProps({
  visible: { type: Boolean, required: true },
  editingPoint: { type: Object, default: null },
  editPointForm: { type: Object, required: true },
  points: { type: Array, required: true },
  getPointNumber: { type: Function, required: true },
  isCourseRootPoint: { type: Function, required: true }
})

const emit = defineEmits(['close', 'save'])
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="emit('close')">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button type="button" class="modal-close" @click="emit('close')" aria-label="关闭">×</button>
        <h3 class="portal-section-title portal-section-title--violet">{{ editingPoint ? '编辑知识点' : '新增知识点' }}</h3>
        <div class="grid-form single-col">
          <label>
            知识点名称
            <input v-model="editPointForm.pointName" class="match-height" />
          </label>
          <label>
            父节点（层级关系）
            <select v-model="editPointForm.parentId" class="match-height">
              <option :value="null">无</option>
              <option
                v-for="point in points"
                :key="point.id"
                :value="point.id"
                :disabled="isCourseRootPoint(point)"
              >
                {{
                  getPointNumber(point)
                    ? getPointNumber(point) + ' · '
                    : ''
                }}{{
                  point.pointName
                }}{{ point.id != null ? ` (#${point.id})` : '' }}
              </option>
            </select>
          </label>
        </div>
        <div class="inline-form">
          <div class="button-row">
            <button type="button" class="match-height match-button" @click="emit('save')">保存</button>
            <button type="button" class="match-height cancel-button" @click="emit('close')">取消</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
@import '@/teacher/styles/teacher-portal.css';
</style>
