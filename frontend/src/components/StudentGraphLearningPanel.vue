<script setup>
import KnowledgePointDiscussion from './KnowledgePointDiscussion.vue'

defineProps({
  selectedNode: { type: Object, default: null },
  isUnjoinedPreviewMode: { type: Boolean, required: true },
  graphNodeMastery: { type: Object, required: true },
  learningSuggestions: { type: Array, required: true },
  suggestionLoading: { type: Boolean, required: true },
  suggestionError: { type: String, default: '' },
  majorRelevance: { type: Object, required: true },
  relevanceLoading: { type: Boolean, required: true },
  relevanceError: { type: String, default: '' },
  relevanceLabel: { type: String, default: '' },
  courseProgress: { type: Object, default: null },
  resourcesLoading: { type: Boolean, required: true },
  resourcesError: { type: String, default: '' },
  resources: { type: Object, required: true },
  learningContextCourse: { type: String, default: '' },
  selectedKnowledgePoint: { type: String, default: '' },
  currentUser: { type: Object, default: null },
  discussionFocusPostId: { type: Number, default: null },
  practiceTestAllowed: { type: Boolean, required: true },
  enterFixedTestFromGraph: { type: Function, required: true },
  enterPaperFromGraph: { type: Function, required: true },
  enterTeacherKpTestFromGraph: { type: Function, required: true },
  isResourceCompleted: { type: Function, required: true },
  resourceKeyForMaterial: { type: Function, required: true },
  markCompletedSafe: { type: Function, required: true }
})
</script>

<template>
  <article v-if="selectedNode && !isUnjoinedPreviewMode" class="result-card">
    <h3>{{ selectedNode.label }}</h3>
    <div class="student-node-action-row">
      <button
        type="button"
        class="match-button"
        :disabled="!practiceTestAllowed"
        @click="enterFixedTestFromGraph"
      >
        进入测试
      </button>
      <button type="button" class="nav-btn" :disabled="!practiceTestAllowed" @click="enterPaperFromGraph">
        进入组卷
      </button>
    </div>
    <div style="margin-bottom:14px">
      <h3>掌握程度</h3>
      <p v-if="graphNodeMastery.noData" class="panel-subtitle">暂无该知识点及其下级相关的练习记录。</p>
      <template v-else>
        <p class="panel-subtitle">
          <strong style="font-size:1.15em">{{ graphNodeMastery.ratio }}%</strong>
          <span
            >（{{ graphNodeMastery.score }} / {{ graphNodeMastery.full }} 分，{{
              graphNodeMastery.attemptCount
            }}
            次练习）</span
          >
        </p>
      </template>
    </div>
    <div>
      <h3>学习建议</h3>
      <p v-if="suggestionLoading" class="panel-subtitle">正在生成学习建议...</p>
      <p v-else-if="suggestionError" class="error-text">{{ suggestionError }}</p>
      <ul v-else>
        <li v-for="item in learningSuggestions" :key="item">{{ item }}</li>
        <li v-if="!learningSuggestions.length" class="panel-subtitle">暂无建议。</li>
      </ul>
    </div>
    <div class="ui-mt-12">
      <h3>专业关联度分析</h3>
      <p v-if="relevanceLoading" class="panel-subtitle">正在分析该知识点与当前专业的关联度...</p>
      <p v-else-if="relevanceError" class="error-text">{{ relevanceError }}</p>
      <div v-else>
        <div v-if="majorRelevance.scoreLevel" class="relevance-meter-wrap">
          <div class="relevance-meter">
            <span
              v-for="i in 5"
              :key="'relevance-dot-' + i"
              class="relevance-dot"
              :class="{ active: i <= majorRelevance.scoreLevel }"
            />
          </div>
          <div class="relevance-meter-text">
            <strong>关联度等级：</strong>{{ majorRelevance.scoreLevel }} / 5
            <span v-if="relevanceLabel">（{{ relevanceLabel }}）</span>
          </div>
        </div>
        <p v-if="majorRelevance.summary" class="panel-subtitle">{{ majorRelevance.summary }}</p>
        <div v-if="majorRelevance.relatedContents?.length">
          <p><strong>相关内容：</strong></p>
          <ul>
            <li v-for="item in majorRelevance.relatedContents" :key="item">{{ item }}</li>
          </ul>
        </div>
        <p v-if="majorRelevance.lowRelevanceReason" class="panel-subtitle">
          <strong>低关联说明：</strong>{{ majorRelevance.lowRelevanceReason }}
        </p>
        <p v-if="!majorRelevance.scoreLevel" class="panel-subtitle">请选择已设置专业后查看分析结果。</p>
      </div>
    </div>
    <div class="ui-mt-12">
      <h3>学习资源</h3>
      <p v-if="courseProgress" class="panel-subtitle ui-mt-6">
        课程进度：<strong>{{ courseProgress.percent || 0 }}%</strong>
        <span>（{{ courseProgress.completed || 0 }} / {{ courseProgress.total || 0 }}）</span>
      </p>
      <p v-if="resourcesLoading" class="panel-subtitle">正在加载资源...</p>
      <p v-else-if="resourcesError" class="error-text">{{ resourcesError }}</p>
      <template v-else>
        <div
          v-if="!((resources.materials || []).length || (resources.tests || []).length)"
          class="panel-subtitle"
        >
          当前知识点暂无资源与测试。
        </div>

        <table v-else class="data-table">
          <thead>
            <tr>
              <th>类型</th>
              <th>标题</th>
              <th>文件名/题量</th>
              <th>上传者/更新时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in resources.materials || []" :key="'m-' + m.id">
              <td>{{ m.category || 'ATTACHMENT' }}</td>
              <td>{{ m.title }}</td>
              <td>{{ m.fileName || '-' }}</td>
              <td>
                {{ m.teacherName || '-' }}
                <span v-if="m.createdAt"> · {{ new Date(m.createdAt).toLocaleString() }}</span>
              </td>
              <td>
                <span v-if="isResourceCompleted(resourceKeyForMaterial(m))">已完成</span>
                <span v-else>未完成</span>
              </td>
              <td>
                <a
                  :href="`/api/materials/${m.id}/download`"
                  target="_blank"
                  @click="() => markCompletedSafe(resourceKeyForMaterial(m))"
                  >下载</a
                >
              </td>
            </tr>

            <tr v-for="t in resources.tests || []" :key="'t-' + t.id">
              <td>TEST</td>
              <td>{{ t.title || '教师发布测试' }}</td>
              <td>{{ t.questionCount || 0 }} 题</td>
              <td>
                <span v-if="t.updatedAt">{{ new Date(t.updatedAt).toLocaleString() }}</span>
                <span v-else>-</span>
              </td>
              <td>
                <span v-if="isResourceCompleted(t.resourceKey)">已完成</span>
                <span v-else>未完成</span>
              </td>
              <td>
                <button
                  type="button"
                  class="match-button"
                  @click="enterTeacherKpTestFromGraph"
                  :disabled="isResourceCompleted(t.resourceKey)"
                >
                  {{ isResourceCompleted(t.resourceKey) ? '已完成' : '开始测试' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </template>
    </div>
    <KnowledgePointDiscussion
      :course-name="learningContextCourse"
      :point-name="selectedKnowledgePoint"
      :current-user-id="currentUser?.id"
      :user-role="currentUser?.role"
      :focus-post-id="discussionFocusPostId"
      :disabled="isUnjoinedPreviewMode"
    />
  </article>
</template>

<style src="./student-portal.css"></style>
