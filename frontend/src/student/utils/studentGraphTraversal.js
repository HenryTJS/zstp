/**
 * 知识图谱遍历（纯函数）
 * 按「包含」边（不含前置）从某节点向下遍历，得到该节点及所有下级节点对应的 label 集合。
 */
export const collectDescendantLabelsFromGraph = (nodes, rawEdges, startId) => {
  const nodeList = nodes || []
  const edges = rawEdges || []
  const byId = new Map(nodeList.map((n) => [n.id, n]))
  const childMap = new Map()
  for (const edge of edges) {
    if ((edge.label || '').toString().includes('前置')) continue
    if (!childMap.has(edge.source)) childMap.set(edge.source, [])
    childMap.get(edge.source).push(edge.target)
  }
  const visited = new Set()
  const stack = [startId]
  while (stack.length) {
    const id = stack.pop()
    if (!id || visited.has(id)) continue
    visited.add(id)
    for (const t of childMap.get(id) || []) {
      if (!visited.has(t)) stack.push(t)
    }
  }
  const labels = new Set()
  for (const id of visited) {
    const n = byId.get(id)
    const lab = n?.label && String(n.label).trim()
    if (lab) labels.add(lab)
  }
  return labels
}

/** 为某知识点 label 随机选一个直属子节点 label（若无则 null） */
export const pickRandomSubpointLabelForKnowledgePoint = (nodes, edges, label) => {
  const nodeList = nodes || []
  const edgeList = edges || []
  if (!nodeList.length) return null
  const trimmed = String(label || '').trim()
  if (!trimmed) return null
  const byId = new Map(nodeList.map((n) => [n.id, n]))
  const byLabel = new Map()
  for (const n of nodeList) {
    const lab = n?.label && String(n.label).trim()
    if (lab && !byLabel.has(lab)) {
      byLabel.set(lab, n.id)
    }
  }
  const startId = byLabel.get(trimmed)
  if (!startId) return null

  const childMap = new Map()
  for (const edge of edgeList) {
    if ((edge.label || '').toString().includes('前置')) continue
    if (!childMap.has(edge.source)) childMap.set(edge.source, [])
    childMap.get(edge.source).push(edge.target)
  }
  const children = childMap.get(startId) || []
  if (!children.length) return null
  const childNodes = children
    .map((id) => byId.get(id))
    .filter((n) => n && n.label && String(n.label).trim() && String(n.label).trim() !== trimmed)
  if (!childNodes.length) return null
  const rand = childNodes[Math.floor(Math.random() * childNodes.length)]
  return String(rand.label || '').trim() || null
}
