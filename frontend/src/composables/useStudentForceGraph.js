import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

export function useStudentForceGraph({
  enabledRef,
  disabledRef,
  graphNetworkDataRef,
  onNodeClick
}) {
  const graphChartRef = ref(null)
  let chart = null
  let wheelBound = false

  const dispose = () => {
    if (chart) {
      chart.dispose()
      chart = null
    }
    wheelBound = false
  }

  const ensureChart = () => {
    const el = graphChartRef.value
    if (!el) return null

    if (chart && chart.getDom() !== el) {
      dispose()
    }
    if (!chart) {
      chart = echarts.init(el)
      chart.on('click', (params) => {
        if (disabledRef?.value) return
        if (params?.data?.id) {
          const id = params.data.id
          const label = params.data.name
          const category = typeof params?.data?.category === 'number' ? params.data.category : null
          onNodeClick?.({ id, label, category })
        }
      })
    }
    return chart
  }

  const bindWheelZoom = () => {
    if (!chart || wheelBound) return
    wheelBound = true
    const zr = chart.getZr()
    zr.off('mousewheel')
    zr.on('mousewheel', (e) => {
      try {
        e?.event?.preventDefault?.()
      } catch {
        /* ignore */
      }
      const w = Number(e?.wheelDelta ?? 0)
      const factor = w > 0 ? 1.12 : 0.9
      const originX = Number(e?.offsetX ?? 0)
      const originY = Number(e?.offsetY ?? 0)
      chart.dispatchAction({
        type: 'graphRoam',
        seriesIndex: 0,
        zoom: factor,
        originX,
        originY
      })
    })
  }

  const render = () => {
    if (!enabledRef?.value) {
      dispose()
      return
    }
    const data = graphNetworkDataRef?.value
    if (!data) return
    const c = ensureChart()
    if (!c) return

    bindWheelZoom()

    c.setOption({
      tooltip: {
        trigger: 'item',
        triggerOn: 'mousemove',
        formatter: (params) => {
          if (params.dataType === 'edge') {
            return `${params.data.source} -> ${params.data.target}`
          }
          return params.data?.name || ''
        }
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          data: data.nodes,
          links: data.links,
          categories: data.categories,
          roam: true,
          draggable: true,
          edgeSymbol: ['none', 'none'],
          force: {
            repulsion: 520,
            gravity: 0.06,
            edgeLength: 120,
            friction: 0.12,
            layoutAnimation: true
          },
          lineStyle: {
            width: 2,
            opacity: 0.9
          },
          label: {
            position: 'inside',
            color: '#000'
          },
          emphasis: {
            focus: 'adjacency',
            scale: 1.08,
            lineStyle: { width: 3 }
          },
          animationDurationUpdate: 600,
          animationEasingUpdate: 'quinticInOut'
        }
      ]
    })
  }

  const resize = () => {
    if (enabledRef?.value) chart?.resize()
  }

  const onWindowResize = () => resize()

  onMounted(() => {
    window.addEventListener('resize', onWindowResize)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('resize', onWindowResize)
    dispose()
  })

  watch(
    () => enabledRef?.value,
    () => render()
  )
  watch(
    () => graphNetworkDataRef?.value,
    () => render(),
    { deep: true }
  )

  return { graphChartRef, render, resize, dispose }
}

