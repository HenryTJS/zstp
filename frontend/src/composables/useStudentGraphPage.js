import { computed, nextTick, ref } from 'vue'

/**
 * 图谱页（数据与选中状态）
 * - 拉取知识图谱
 * - 管理 force/branch 视图切换状态
 * - 管理选中节点与“当前知识点”
 *
 * 说明：此 composable 不直接负责渲染（渲染由 StudentGraphCanvasCard/useStudentForceGraph 承担）
 */
export function useStudentGraphPage({
  // refs
  learningContextCourseRef,
  previewUnjoinedCourseRef,
  pendingGraphPointLabelRef,
  selectedKnowledgePointRef,

  // callbacks
  fetchKnowledgeGraph,
  onKnowledgePointSelected
}) {
  const graphLoading = ref(false)
  const graphError = ref('')
  const graphData = ref({ title: '', nodes: [], edges: [], suggestions: [] })

  const selectedNodeId = ref('')
  const graphClickedNodeCategory = ref(null)
  const graphView = ref('force') // 'force' | 'branch'

  const isUnjoinedPreviewMode = computed(() => Boolean(previewUnjoinedCourseRef.value))
  const graphTopic = computed(() => learningContextCourseRef.value || previewUnjoinedCourseRef.value || '')

  const loadGraph = async () => {
    const topic = graphTopic.value
    const emptyGraph = () => {
      graphData.value = { title: '知识图谱', nodes: [], edges: [], suggestions: [] }
      selectedNodeId.value = ''
      selectedKnowledgePointRef.value = ''
    }

    if (!topic) {
      emptyGraph()
      return
    }

    graphLoading.value = true
    graphError.value = ''
    try {
      const { data } = await fetchKnowledgeGraph({ topic })
      graphData.value = {
        title: data.title || '知识图谱',
        nodes: Array.isArray(data.nodes) ? data.nodes : [],
        edges: Array.isArray(data.edges) ? data.edges : [],
        suggestions: Array.isArray(data.suggestions) ? data.suggestions : []
      }

      const rootNode = graphData.value.nodes.find((n) => n.id === 'root')
      const pendingLabel = pendingGraphPointLabelRef.value

      const applyRootSelection = async (label) => {
        selectedKnowledgePointRef.value = label
        if (!isUnjoinedPreviewMode.value) {
          await onKnowledgePointSelected?.(label)
        }
      }

      if (pendingLabel) {
        pendingGraphPointLabelRef.value = ''
        const target = graphData.value.nodes.find(
          (n) => String(n.label || '').trim() === String(pendingLabel).trim()
        )
        if (target?.label) {
          selectedNodeId.value = target.id
          graphClickedNodeCategory.value = typeof target.category === 'number' ? target.category : null
          await applyRootSelection(target.label)
        } else if (rootNode?.label) {
          selectedNodeId.value = 'root'
          graphClickedNodeCategory.value = typeof rootNode.category === 'number' ? rootNode.category : null
          await applyRootSelection(rootNode.label)
        }
      } else if (rootNode?.label) {
        selectedNodeId.value = 'root'
        graphClickedNodeCategory.value = typeof rootNode.category === 'number' ? rootNode.category : null
        await applyRootSelection(rootNode.label)
      }

      await nextTick()
    } catch (err) {
      graphError.value = err?.response?.data?.message || '知识图谱加载失败，请稍后重试。'
    } finally {
      graphLoading.value = false
    }
  }

  const selectKnowledgePointFromGraph = async ({ id, label, category }) => {
    if (isUnjoinedPreviewMode.value) return
    const nid = id != null ? String(id) : ''
    const name = String(label || '').trim()
    if (!nid || !name) return
    selectedNodeId.value = nid
    selectedKnowledgePointRef.value = name
    graphClickedNodeCategory.value = typeof category === 'number' ? category : null
    await onKnowledgePointSelected?.(name)
  }

  return {
    graphLoading,
    graphError,
    graphData,
    selectedNodeId,
    graphClickedNodeCategory,
    graphView,
    isUnjoinedPreviewMode,
    graphTopic,
    loadGraph,
    selectKnowledgePointFromGraph
  }
}

