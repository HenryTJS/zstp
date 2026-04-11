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
  <article v-if="selectedNode && !isUnjoinedPreviewMode" class="result-card student-kp-detail-root">
    <header class="student-kp-hero">
      <div class="student-kp-hero-main">
        <p class="student-kp-hero-eyebrow">当前知识点</p>
        <h2 class="student-kp-hero-title">{{ selectedNode.label }}</h2>
      </div>
      <div class="student-kp-hero-actions student-node-action-row">
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
    </header>

    <div class="student-kp-body">
      <div class="student-kp-grid-2">
        <section class="student-kp-block student-kp-block--mastery" aria-labelledby="kp-mastery-heading">
          <h3 id="kp-mastery-heading" class="student-kp-block__title">掌握程度</h3>
          <div class="student-kp-block__content">
            <p v-if="graphNodeMastery.noData" class="panel-subtitle student-kp-muted">暂无该知识点及其下级相关的练习记录。</p>
            <template v-else>
              <p class="student-kp-mastery-stat">
                <span class="student-kp-mastery-pct">{{ graphNodeMastery.ratio }}%</span>
                <span class="student-kp-mastery-meta">
                  {{ graphNodeMastery.score }} / {{ graphNodeMastery.full }} 分 · {{ graphNodeMastery.attemptCount }} 次练习
                </span>
              </p>
            </template>
          </div>
        </section>

        <section class="student-kp-block student-kp-block--suggest" aria-labelledby="kp-suggest-heading">
          <h3 id="kp-suggest-heading" class="student-kp-block__title">学习建议</h3>
          <div class="student-kp-block__content">
            <p v-if="suggestionLoading" class="panel-subtitle student-kp-muted">正在生成学习建议...</p>
            <p v-else-if="suggestionError" class="error-text">{{ suggestionError }}</p>
            <ul v-else class="student-kp-list">
              <li v-for="item in learningSuggestions" :key="item">{{ item }}</li>
              <li v-if="!learningSuggestions.length" class="panel-subtitle student-kp-muted">暂无建议。</li>
            </ul>
          </div>
        </section>
      </div>

      <section class="student-kp-block student-kp-block--relevance" aria-labelledby="kp-relevance-heading">
        <h3 id="kp-relevance-heading" class="student-kp-block__title">专业关联度分析</h3>
        <div class="student-kp-block__content">
          <p v-if="relevanceLoading" class="panel-subtitle student-kp-muted">正在分析该知识点与当前专业的关联度...</p>
          <p v-else-if="relevanceError" class="error-text">{{ relevanceError }}</p>
          <div v-else>
            <div v-if="majorRelevance.scoreLevel" class="relevance-meter-wrap student-kp-relevance-meter">
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
            <p v-if="majorRelevance.summary" class="panel-subtitle student-kp-relevance-summary">{{ majorRelevance.summary }}</p>
            <div v-if="majorRelevance.relatedContents?.length" class="student-kp-related">
              <p class="student-kp-related__label">相关内容</p>
              <ul class="student-kp-list">
                <li v-for="item in majorRelevance.relatedContents" :key="item">{{ item }}</li>
              </ul>
            </div>
            <p v-if="majorRelevance.lowRelevanceReason" class="panel-subtitle">
              <strong>低关联说明：</strong>{{ majorRelevance.lowRelevanceReason }}
            </p>
            <p v-if="!majorRelevance.scoreLevel" class="panel-subtitle student-kp-muted">请选择已设置专业后查看分析结果。</p>
          </div>
        </div>
      </section>

      <section class="student-kp-block student-kp-block--resources" aria-labelledby="kp-resources-heading">
        <h3 id="kp-resources-heading" class="student-kp-block__title">学习资源</h3>
        <div class="student-kp-block__content">
          <p v-if="resourcesLoading" class="panel-subtitle student-kp-muted">正在加载资源...</p>
          <p v-else-if="resourcesError" class="error-text">{{ resourcesError }}</p>
          <template v-else>
            <div
              v-if="!((resources.materials || []).length || (resources.tests || []).length)"
              class="panel-subtitle student-kp-muted"
            >
              当前知识点暂无资源与测试。
            </div>

            <div v-else class="student-kp-table-wrap">
              <table class="data-table student-kp-resource-table">
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
            </div>
          </template>
        </div>
      </section>

      <section class="student-kp-block student-kp-block--discussion" aria-labelledby="kp-disc-heading">
        <h3 id="kp-disc-heading" class="student-kp-block__title">交流区</h3>
        <div class="student-kp-block__content student-kp-block__content--flush">
          <KnowledgePointDiscussion
            :course-name="learningContextCourse"
            :point-name="selectedKnowledgePoint"
            :current-user-id="currentUser?.id"
            :user-role="currentUser?.role"
            :focus-post-id="discussionFocusPostId"
            :disabled="isUnjoinedPreviewMode"
            embedded
            hide-title
          />
        </div>
      </section>
    </div>
  </article>
</template>

<style src="./student-portal.css"></style>
