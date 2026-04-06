<script setup>
defineProps({
  visible: { type: Boolean, required: true },
  editProfileForm: { type: Object, required: true },
  colleges: { type: Array, required: true }
})

const emit = defineEmits(['close', 'save'])
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="emit('close')">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button type="button" class="modal-close" @click="emit('close')" aria-label="关闭">×</button>
        <h3>编辑个人资料</h3>
        <div class="grid-form single-col ui-mt-12">
          <label>
            用户名
            <input v-model="editProfileForm.username" class="match-height" />
          </label>
          <label>
            邮箱
            <input v-model="editProfileForm.email" type="email" class="match-height" />
          </label>
          <label>
            学院
            <select v-model="editProfileForm.college" class="match-height">
              <option value="">请选择学院</option>
              <option v-for="c in colleges" :key="c.code" :value="c.code">{{ c.name }}</option>
            </select>
          </label>
        </div>
        <div class="ui-actions-row">
          <button type="button" class="match-height match-button" @click="emit('save')">保存</button>
          <button type="button" class="match-height cancel-button" @click="emit('close')">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style src="./teacher-portal.css"></style>
