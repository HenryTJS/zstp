<script setup>
import { computed } from 'vue'
const props = defineProps({
  // 表格数据
  points: { type: Array, required: true },
  selectedPointIds: { type: Array, required: true },

  // 顶部状态提示
  pointMessage: { type: String, required: false, default: '' },
  pointError: { type: String, required: false, default: '' },
  mdImportLoading: { type: Boolean, required: true },
  mdImportError: { type: String, required: false, default: '' },
  mdImportResult: { type: String, required: false, default: '' },

  // 工具函数
  getPointNumber: { type: Function, required: true },

  // 交互回调
  onDownloadTemplate: { type: Function, required: true },
  onOpenMdImport: { type: Function, required: true },
  onOpenAddPoint: { type: Function, required: true },
  onDeleteSelectedPoints: { type: Function, required: true },
  onOpenEditPoint: { type: Function, required: true },
  onOpenUploadModal: { type: Function, required: true },
  onOpenViewMaterials: { type: Function, required: true }
})

const emit = defineEmits(['update:selectedPointIds'])

const selectedPointIdsModel = computed({
  get: () => props.selectedPointIds || [],
  set: (v) => emit('update:selectedPointIds', v)
})

const isCourseRootPoint = (item) => Boolean(item?.courseRoot)

const onDeleteSelected = () => props.onDeleteSelectedPoints()
</script>

<template>
  <article class="result-card">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
      <h3>课程知识点设置</h3>
      <div style="display:flex;gap:8px;align-items:center;">
        <button type="button" class="cancel-button" @click="onDownloadTemplate">下载 MD 模板</button>
        <button type="button" class="cancel-button" @click="onOpenMdImport">导入 MD</button>
        <button type="button" class="match-button" @click="onOpenAddPoint">新增知识点</button>
        <button
          type="button"
          class="cancel-button"
          :disabled="!selectedPointIdsModel.length"
          @click="onDeleteSelected"
        >
          删除选中
        </button>
      </div>
    </div>

    <div class="grid-form four-col">
      <label><!-- 占位，保持四列布局 --></label>
      <label><!-- 占位，保持四列布局 --></label>
      <label><!-- 占位，保持四列布局 --></label>
      <label><!-- 占位，保持四列布局 --></label>
    </div>

    <p v-if="pointMessage" class="ok-text">{{ pointMessage }}</p>
    <p v-if="pointError" class="error-text">{{ pointError }}</p>
    <p v-if="mdImportLoading" class="ok-text">导入中，请稍候...</p>
    <p v-if="mdImportError" class="error-text">{{ mdImportError }}</p>
    <p v-if="mdImportResult" class="ok-text">{{ mdImportResult }}</p>

    <div style="max-height:520px;overflow:auto;">
      <table class="data-table">
        <thead>
          <tr>
            <th>多选</th>
            <th>课程</th>
            <th>编号</th>
            <th>知识点</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in points" :key="item.id">
            <td>
              <input
                type="checkbox"
                v-model="selectedPointIdsModel"
                :value="item.id"
                :disabled="isCourseRootPoint(item)"
                aria-label="选择知识点"
              />
            </td>
            <td>{{ item.courseName }}</td>
            <td>{{ getPointNumber(item.pointName) || '-' }}</td>
            <td>
              {{ item.pointName
              }}<span v-if="isCourseRootPoint(item)" class="panel-subtitle" style="margin-left:6px">（课程根）</span>
            </td>
            <td>
              <button v-if="!isCourseRootPoint(item)" type="button" @click="onOpenEditPoint(item)">编辑</button>
              <button @click="onOpenUploadModal(item)" style="margin-left:8px;">上传资料</button>
              <button @click="onOpenViewMaterials(item)" style="margin-left:8px;">查看资料</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </article>
</template>

<style src="./teacher-portal.css"></style>

