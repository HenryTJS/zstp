<script setup>
const props = defineProps({
  visible: { type: Boolean, required: true },
  /** join：广场已有课程；create：新课程（课程名在弹窗内填写） */
  mode: { type: String, default: 'join' },
  courseName: { type: String, required: true },
  requestText: { type: String, required: true },
  submitting: { type: Boolean, required: true },
  error: { type: String, required: false, default: '' }
})

const emit = defineEmits(['close', 'update:requestText', 'update:courseName', 'submit'])
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="emit('close')">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button type="button" class="modal-close" @click="emit('close')" aria-label="关闭">×</button>
        <h3 class="portal-section-title portal-section-title--teal">{{ mode === 'create' ? '申请新增课程' : `申请课程权限 — ${courseName || '（未选课程）'}` }}</h3>
        <p v-if="mode === 'create'" class="panel-subtitle" style="margin-top: 8px">
          提交后由管理员审批。通过后系统将自动把该课程加入课程目录，并为您开通此课程的授课权限。
        </p>

        <div class="grid-form single-col ui-mt-12">
          <label v-if="mode === 'create'">
            新课程名称
            <input
              class="match-height"
              :value="courseName"
              placeholder="例如：离散数学"
              @input="emit('update:courseName', $event.target.value)"
            />
          </label>
          <label>
            申请内容
            <textarea
              :value="requestText"
              rows="6"
              :placeholder="
                mode === 'create'
                  ? '请说明课程定位、面向对象、大纲设想等，便于管理员审核。'
                  : '请写明希望获得该课程权限的理由、计划或相关经验。'
              "
              @input="emit('update:requestText', $event.target.value)"
            ></textarea>
          </label>
        </div>

        <div class="ui-actions-row">
          <button
            type="button"
            class="match-height match-button"
            :disabled="submitting"
            @click="emit('submit')"
          >
            {{ submitting ? '提交中…' : '提交申请' }}
          </button>
          <button type="button" class="match-height cancel-button" @click="emit('close')">取消</button>
        </div>

        <p v-if="error" class="error-text" style="margin-top:10px">{{ error }}</p>
      </div>
    </div>
  </div>
</template>

<style>
@import '@/styles/teacher/teacher-portal.css';
</style>

