<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as d3 from 'd3'

const props = defineProps({
  graphData: { type: Object, required: true },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['node-click'])

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

const treeRoot = computed(() => normalizeGraphToTree(props.graphData))

const assignSides = (root) => {
  // Root centered; split first-level children alternately to right/left.
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

  // Split into two sides by first-level branches.
  assignSides(root)

  // Convert "depth to the right" into "depth to left/right from center".
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
  // curly brace spanning [xStart, xEnd] at horizontal position y
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
    const y = d.y + (openRight ? 78 : -78) // brace sits between parent and children labels
    if (xMax - xMin < 18) return
    braces.push({ key: d.data.id, d: bracePath(xMin, xMax, y, 14, openRight) })
  })
  return braces
})

const onNodeClick = (node) => {
  if (props.disabled) return
  const label = String(node?.data?.label || '')
  const id = String(node?.data?.id || '')
  if (!id || !label) return
  emit('node-click', { id, label, raw: node?.data?.raw || null })
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
        // Allow wheel/pan; avoid right button drag.
        if (event?.type === 'mousedown' && event?.button != null && event.button !== 0) return false
        return true
      })
      .on('zoom', (event) => {
        zoomTransform.value = event.transform
      })
    d3.select(svg).call(zoomBehavior)
    // Set initial transform explicitly so it matches our state.
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
  () => props.graphData,
  () => {
    resetView()
  },
  { deep: true }
)
</script>

<template>
  <div ref="wrapRef" class="branch-graph-wrap">
    <div v-if="!layout" class="branch-graph-empty">暂无图谱数据</div>
    <template v-else>

      <svg
        class="branch-graph-svg"
        ref="svgRef"
        :width="width || '100%'"
        :height="height || 420"
        :viewBox="`${layout.vb.vbX} ${layout.vb.vbY} ${layout.innerW} ${layout.innerH}`"
      >
        <g :transform="zoomTransform.toString()">
          <!-- links -->
          <g fill="none" stroke="rgba(15, 23, 42, 0.25)" stroke-width="1.4">
            <path
              v-for="link in layout.root.links()"
              :key="`${link.source.data.id}->${link.target.data.id}`"
              :d="`M ${link.source.y + (link.target.y >= link.source.y ? 18 : -18)} ${link.source.x} H ${link.target.y + (link.target.y >= link.source.y ? -18 : 18)} V ${link.target.x} H ${link.target.y}`"
            />
          </g>

          <!-- braces -->
          <g fill="none" stroke="rgba(124, 58, 237, 0.55)" stroke-width="2">
            <path v-for="b in childBraces" :key="b.key" :d="b.d" />
          </g>

          <!-- nodes -->
          <g>
            <g
              v-for="n in layout.root.descendants()"
              :key="n.data.id"
              class="branch-node"
              :transform="`translate(${n.y},${n.x})`"
              @click.stop="onNodeClick(n)"
            >
              <circle
                :r="n.depth === 0 ? 9 : (n.children && n.children.length ? 7 : 6)"
                :fill="n.depth === 0 ? '#24a148' : (n.depth === 1 ? '#c44536' : (n.depth === 2 ? '#2f7ed8' : '#94a3b8'))"
                stroke="#fff"
                stroke-width="2"
              />
              <text
                :x="n.y < 0 ? -14 : 14"
                y="5"
                class="branch-node-label"
                :text-anchor="n.y < 0 ? 'end' : 'start'"
                :style="{ fontSize: fontSizePx + 'px' }"
              >
                {{ n.data.label }}
              </text>
            </g>
          </g>
        </g>
      </svg>
    </template>
  </div>
</template>

<style scoped>
.branch-graph-wrap{
  width:100%;
  height:420px;
  position:relative;
  border-radius:12px;
  border:1px solid rgba(15,23,42,.08);
  background:linear-gradient(180deg,#ffffff 0%,#f8fafc 100%);
  overflow:hidden;
}

.branch-graph-toolbar{
  position:absolute;
  left:10px;
  top:10px;
  display:flex;
  align-items:center;
  gap:10px;
  z-index:2;
  pointer-events:auto;
}

.branch-graph-hint{
  font-size:12px;
  color:rgba(15,23,42,.65);
  background:rgba(255,255,255,.8);
  border:1px solid rgba(15,23,42,.08);
  padding:6px 8px;
  border-radius:999px;
}

.branch-graph-svg{
  width:100%;
  height:100%;
  touch-action:none;
  cursor:grab;
}
.branch-graph-svg:active{ cursor:grabbing; }

.branch-graph-empty{
  height:100%;
  display:flex;
  align-items:center;
  justify-content:center;
  color:rgba(15,23,42,.6);
}

.branch-node{
  cursor:pointer;
}
.branch-node-label{
  font-size:13px;
  font-weight:600;
  fill:#0f172a;
  paint-order:stroke;
  stroke:rgba(255,255,255,.85);
  stroke-width:3px;
  stroke-linejoin:round;
}
</style>

