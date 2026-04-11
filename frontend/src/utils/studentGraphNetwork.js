/**
 * 将图谱节点/边转为 ECharts graph series 所需的数据结构
 */
export const buildGraphNetworkData = (graphData) => {
  const nodes = graphData?.nodes || []
  const rawEdges = graphData?.edges || []
  const edges = rawEdges.filter((edge) => !((edge.label || '').toString().includes('前置')))
  if (!nodes.length) {
    return null
  }

  const nodeMap = new Map(nodes.map((node) => [node.id, node]))
  const childMap = new Map()
  const indegreeMap = new Map(nodes.map((node) => [node.id, 0]))

  for (const edge of edges) {
    if (!childMap.has(edge.source)) {
      childMap.set(edge.source, [])
    }
    childMap.get(edge.source).push(edge.target)
    indegreeMap.set(edge.target, (indegreeMap.get(edge.target) || 0) + 1)
  }

  const root = nodes.find((item) => item.id === 'root') || nodes[0]
  const depthMap = new Map([[root.id, 0]])
  const queue = [root.id]

  while (queue.length) {
    const currentId = queue.shift()
    const currentDepth = depthMap.get(currentId) || 0
    for (const childId of childMap.get(currentId) || []) {
      if (!depthMap.has(childId)) {
        depthMap.set(childId, currentDepth + 1)
        queue.push(childId)
      }
    }
  }

  const styledNodes = nodes.map((node) => {
    const depth = depthMap.get(node.id) ?? 1
    const isRoot = node.id === root.id
    const hasChildren = childMap.has(node.id)
    const color = isRoot
      ? '#24a148'
      : depth === 1
        ? '#c44536'
        : depth === 2
          ? '#2f7ed8'
          : '#9ed9ea'

    return {
      id: node.id,
      name: node.label,
      value: node.label,
      category: Math.min(depth, 3),
      symbolSize: isRoot ? 58 : hasChildren ? 46 : 38,
      draggable: true,
      itemStyle: {
        color,
        borderColor: '#eaf4fb',
        borderWidth: 3,
        shadowBlur: 10,
        shadowColor: 'rgba(33, 59, 89, 0.18)'
      },
      label: {
        show: true,
        color: '#000',
        fontWeight: 500,
        fontSize: isRoot ? 18 : depth === 1 ? 15 : depth === 2 ? 13 : 12,
        lineHeight: 18,
        formatter: node.label
      }
    }
  })

  const styledLinks = edges.map((edge) => {
    const sourceDepth = depthMap.get(edge.source) ?? 0
    return {
      source: edge.source,
      target: edge.target,
      value: edge.label || '包含',
      lineStyle: {
        color: sourceDepth === 0 ? '#7aa96b' : sourceDepth === 1 ? '#e1a692' : '#9db9d5',
        width: sourceDepth <= 1 ? 2.2 : 1.6,
        opacity: 0.95,
        curveness: 0.08
      }
    }
  })

  return {
    nodes: styledNodes,
    links: styledLinks,
    categories: [
      { name: '课程' },
      { name: '一级知识点' },
      { name: '二级知识点' },
      { name: '扩展知识点' }
    ],
    rootId: root.id,
    indegreeMap
  }
}
