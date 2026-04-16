import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as d3 from 'd3'

export function useStudentBranchGraph({ graphDataRef, disabledRef, emitNodeClick }) {
  const wrapRef = ref(null)
  const svgRef = ref(null)
  const width = ref(0)
  const height = ref(0)
  let ro = null
  let zoomBehavior = null

  const normalizeGraphToTree = (graphData) => {
    const nodes = Array.isArray(graphData?.nodes) ? graphData.nodes : []
    const edges = Array.isArray(graphData?.edges) ? graphData.edges : []
    if (!nodes.length) return null

    const byId = new Map(nodes.map((n) => [String(n.id), n]))
    const childMap = new Map()
    for (const e of edges) {
      if ((e?.label || '').toString().includes('前置')) continue
      const s = String(e?.source ?? '')
      const t = String(e?.target ?? '')
      if (!s || !t) continue
      if (!childMap.has(s)) childMap.set(s, [])
      childMap.get(s).push(t)
    }

    const rootNode = nodes.find((n) => n.id === 'root') || nodes[0]
    const rootId = String(rootNode.id)
    const visited = new Set()

    const build = (id) => {
      if (!id || visited.has(id)) return null
      visited.add(id)
      const n = byId.get(id)
      if (!n) return null
      const children = (childMap.get(id) || [])
        .map((cid) => build(String(cid)))
        .filter(Boolean)
      return {
        id: String(n.id),
        label: String(n.label ?? n.name ?? ''),
        raw: n,
        children
      }
    }

    return build(rootId)
  }

  const treeRoot = computed(() => normalizeGraphToTree(graphDataRef.value))

  const assignSides = (root) => {
    root.data.__side = 0
    const kids = root.children || []
    kids.forEach((c, i) => {
      const side = i % 2 === 0 ? 1 : -1
      c.each((d) => {
        d.data.__side = side
      })
    })
  }

  const buildViewBox = (root, margin) => {
    let minX = Infinity
    let maxX = -Infinity
    let minY = Infinity
    let maxY = -Infinity
    root.each((d) => {
      minX = Math.min(minX, d.x)
      maxX = Math.max(maxX, d.x)
      minY = Math.min(minY, d.y)
      maxY = Math.max(maxY, d.y)
    })
    if (!Number.isFinite(minX)) return { vbX: 0, vbY: 0, vbW: 0, vbH: 0 }
    const vbX = minY - margin.left
    const vbY = minX - margin.top
    const vbW = (maxY - minY) + margin.left + margin.right
    const vbH = (maxX - minX) + margin.top + margin.bottom
    return { vbX, vbY, vbW, vbH }
  }

  const layout = computed(() => {
    if (!treeRoot.value) return null

    const root = d3.hierarchy(treeRoot.value)
    const dx = 34
    const dy = 180
    const t = d3.tree().nodeSize([dx, dy])
    t(root)

    assignSides(root)

    root.each((d) => {
      const side = Number(d.data.__side || 0)
      if (d.depth === 0) {
        d.y = 0
        return
      }
      d.y = side * (d.depth * dy)
    })

    const margin = { top: 34, right: 240, bottom: 34, left: 240 }
    const vb = buildViewBox(root, margin)
    const innerW = Math.max(520, vb.vbW)
    const innerH = Math.max(240, vb.vbH)

    return { root, innerW, innerH, vb, margin }
  })

  const zoomTransform = ref(d3.zoomIdentity)

  const resetView = () => {
    const svg = svgRef.value
    if (!svg || !zoomBehavior) {
      zoomTransform.value = d3.zoomIdentity
      return
    }
    d3.select(svg).call(zoomBehavior.transform, d3.zoomIdentity)
  }

  const fontSizePx = computed(() => 17)

  const bracePath = (xStart, xEnd, y, w = 14, openToRight = true) => {
    const dir = openToRight ? 1 : -1
    const y0 = y
    const y1 = y + dir * w
    const x1 = Math.min(xStart, xEnd)
    const x2 = Math.max(xStart, xEnd)
    const xm = (x1 + x2) / 2
    return [
      `M ${y0} ${x1}`,
      `C ${y0} ${x1}, ${y1} ${x1}, ${y1} ${x1 + dir * w}`,
      `C ${y1} ${xm - w}, ${y0} ${xm - w}, ${y0} ${xm}`,
      `C ${y0} ${xm + w}, ${y1} ${xm + w}, ${y1} ${xm + w}`,
      `C ${y1} ${x2 - dir * w}, ${y0} ${x2 - dir * w}, ${y0} ${x2}`,
      `C ${y0} ${x2}, ${y1} ${x2}, ${y1} ${x2 - dir * w}`,
      ''
    ].join(' ')
  }

  const childBraces = computed(() => {
    if (!layout.value) return []
    const braces = []
    layout.value.root.each((d) => {
      const kids = d.children || []
      if (!kids.length) return
      const xs = kids.map((k) => k.x)
      const xMin = Math.min(...xs)
      const xMax = Math.max(...xs)
      const side = Number(d.data.__side || 0)
      const openRight = side >= 0
      const y = d.y + (openRight ? 78 : -78)
      if (xMax - xMin < 18) return
      braces.push({ key: d.data.id, d: bracePath(xMin, xMax, y, 14, openRight) })
    })
    return braces
  })

  const onNodeClick = (node) => {
    if (disabledRef.value) return
    const label = String(node?.data?.label || '')
    const id = String(node?.data?.id || '')
    if (!id || !label) return
    emitNodeClick?.({ id, label, raw: node?.data?.raw || null })
  }

  const updateSize = () => {
    const el = wrapRef.value
    if (!el) return
    const rect = el.getBoundingClientRect()
    width.value = Math.max(0, Math.floor(rect.width))
    height.value = Math.max(0, Math.floor(rect.height))
  }

  onMounted(() => {
    updateSize()
    const svg = svgRef.value
    if (svg) {
      zoomBehavior = d3.zoom()
        .scaleExtent([0.4, 4])
        .filter((event) => {
          if (event?.type === 'mousedown' && event?.button != null && event.button !== 0) return false
          return true
        })
        .on('zoom', (event) => {
          zoomTransform.value = event.transform
        })
      d3.select(svg).call(zoomBehavior)
      d3.select(svg).call(zoomBehavior.transform, zoomTransform.value)
    }
    if (typeof ResizeObserver !== 'undefined') {
      ro = new ResizeObserver(() => updateSize())
      if (wrapRef.value) ro.observe(wrapRef.value)
    } else {
      window.addEventListener('resize', updateSize)
    }
  })

  onBeforeUnmount(() => {
    if (ro) {
      try {
        if (wrapRef.value) ro.unobserve(wrapRef.value)
      } catch {
        /* ignore */
      }
      ro = null
    } else {
      window.removeEventListener('resize', updateSize)
    }
    zoomBehavior = null
  })

  watch(
    () => graphDataRef.value,
    () => {
      resetView()
    },
    { deep: true }
  )

  return {
    wrapRef,
    svgRef,
    width,
    height,
    layout,
    zoomTransform,
    childBraces,
    fontSizePx,
    onNodeClick,
    resetView
  }
}

