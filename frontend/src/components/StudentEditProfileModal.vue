<script setup>
const props = defineProps({
  visible: { type: Boolean, required: true },
  editProfileForm: { type: Object, required: true },
  majorLevel1: { type: Array, required: true },
  majorLevel2: { type: Array, required: true },
  majorLevel3: { type: Array, required: true },
  onEditMajor1Change: { type: Function, required: true },
  onEditMajor2Change: { type: Function, required: true },
  onSave: { type: Function, required: true }
})

const emit = defineEmits(['close'])
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="emit('close')">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button class="modal-close" @click="emit('close')" aria-label="关闭">×</button>
        <h3>编辑个人资料</h3>
        <div class="grid-form single-col" style="margin-top:12px;">
          <label>
            用户名
            <input v-model="editProfileForm.username" class="match-height" />
          </label>
          <label>
            邮箱
            <input v-model="editProfileForm.email" type="email" class="match-height" />
          </label>
          <label>
            专业
            <div style="display:flex;gap:8px;">
              <select v-model="editProfileForm.major1" @change="onEditMajor1Change">
                <option value="">请选择</option>
                <option v-for="m in majorLevel1" :key="m.code" :value="m.code">{{ m.name }}</option>
              </select>
              <select
                v-model="editProfileForm.major2"
                :disabled="!(Array.isArray(majorLevel2) && majorLevel2.length)"
                @change="onEditMajor2Change"
              >
                <option value="">请选择</option>
                <option v-for="m in (majorLevel2 || [])" :key="m.code" :value="m.code">{{ m.name }}</option>
              </select>
              <select v-model="editProfileForm.major3" :disabled="!(Array.isArray(majorLevel3) && majorLevel3.length)">
                <option value="">请选择</option>
                <option v-for="m in (majorLevel3 || [])" :key="m.code" :value="m.code">{{ m.name }}</option>
              </select>
            </div>
          </label>
        </div>
        <div style="display:flex;gap:8px;margin-top:12px;">
          <button class="match-height match-button" @click="onSave">保存</button>
          <button class="match-height cancel-button" @click="emit('close')">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style src="./student-portal-modal.css"></style>

