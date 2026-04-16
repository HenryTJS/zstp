<script setup>
import { computed } from 'vue'
import { useStudentBranchGraph } from '../../composables/useStudentBranchGraph'

const props = defineProps({
  graphData: { type: Object, required: true },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['node-click'])

const graphDataRef = computed(() => props.graphData)
const disabledRef = computed(() => props.disabled)

const {
  wrapRef,
  svgRef,
  width,
  height,
  layout,
  zoomTransform,
  childBraces,
  fontSizePx,
  onNodeClick
} = useStudentBranchGraph({
  graphDataRef,
  disabledRef,
  emitNodeClick: (payload) => emit('node-click', payload)
})
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
          <g fill="none" stroke="rgba(15, 23, 42, 0.25)" stroke-width="1.4">
            <path
              v-for="link in layout.root.links()"
              :key="`${link.source.data.id}->${link.target.data.id}`"
              :d="`M ${link.source.y + (link.target.y >= link.source.y ? 18 : -18)} ${link.source.x} H ${link.target.y + (link.target.y >= link.source.y ? -18 : 18)} V ${link.target.x} H ${link.target.y}`"
            />
          </g>

          <g fill="none" stroke="rgba(124, 58, 237, 0.55)" stroke-width="2">
            <path v-for="b in childBraces" :key="b.key" :d="b.d" />
          </g>

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

<style scoped src="../../styles/branch-graph.css"></style>

