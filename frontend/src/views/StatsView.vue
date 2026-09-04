<template>
  <div class="page">
    <el-header class="bar">
      <el-button @click="router.push('/dashboard')">返回</el-button>
      <div class="title">数据概览</div>
      <el-button @click="loadAll">刷新</el-button>
    </el-header>
    <el-main class="body">
      <el-row :gutter="16" class="cards">
        <el-col :xs="12" :md="6"><el-card><el-statistic title="笔记数" :value="num(stats.noteCount)" /></el-card></el-col>
        <el-col :xs="12" :md="6"><el-card><el-statistic title="知识库" :value="num(stats.kbCount)" /></el-card></el-col>
        <el-col :xs="12" :md="6"><el-card><el-statistic title="回收站" :value="num(stats.recycleCount)" /></el-card></el-col>
        <el-col :xs="12" :md="6"><el-card><el-statistic title="总浏览" :value="num(stats.totalViewCount)" /></el-card></el-col>
      </el-row>

      <el-card v-if="stats.recentTitle" class="recent">
        <span class="muted">最近编辑：</span><el-tag>{{ stats.recentTitle }}</el-tag>
        <span class="muted" style="margin-left:8px">{{ (stats.recentUpdateTime || '').slice(0,19) }}</span>
      </el-card>

      <el-row :gutter="16">
        <el-col :xs="24" :md="8"><el-card><template #header><b>近 30 日新增笔记</b></template><div ref="trendRef" class="chart"></div></el-card></el-col>
        <el-col :xs="24" :md="8"><el-card><template #header><b>热门笔记 TOP5</b></template><div ref="hotRef" class="chart"></div></el-card></el-col>
        <el-col :xs="24" :md="8"><el-card><template #header><b>分类占比</b></template><div ref="catRef" class="chart"></div></el-card></el-col>
      </el-row>

      <el-card class="mt">
        <template #header><b>操作日志</b></template>
        <el-table :data="logs" empty-text="暂无操作日志">
          <el-table-column prop="module" label="模块" width="110" />
          <el-table-column prop="action" label="操作" min-width="120" />
          <el-table-column prop="method" label="方法" min-width="140" />
          <el-table-column label="耗时" width="80" align="center">
            <template #default="{ row }">{{ row.duration }}ms</template>
          </el-table-column>
          <el-table-column label="时间" width="180">
            <template #default="{ row }">{{ (row.createTime || '').slice(0, 19) }}</template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="120" />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { statsApi, logApi } from '@/api'
import { formatDateTime, formatDate } from '@/utils/format'

// 按需注册 echarts 组件，避免全量引入以减小体积
echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()
const stats = ref({})
const logs = ref([])
const trendRef = ref(null)
const hotRef = ref(null)
const catRef = ref(null)

let trendChart = null
let hotChart = null
let catChart = null

const num = (v) => Number(v || 0)

function disposeAll() {
  trendChart?.dispose(); trendChart = null
  hotChart?.dispose(); hotChart = null
  catChart?.dispose(); catChart = null
}

function renderCharts() {
  disposeAll()
  if (!trendRef.value || !hotRef.value || !catRef.value) return
  const trend = stats.value.trend || []
  trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' }, grid: { left: 32, right: 16, top: 24, bottom: 40 },
    xAxis: { type: 'category', data: trend.map((t) => t.date.slice(5)), axisLabel: { interval: 4, rotate: 30 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'line', smooth: true, data: trend.map((t) => Number(t.count)), areaStyle: {}, itemStyle: { color: '#409eff' } }]
  })

  const hot = stats.value.hotNotes || []
  hotChart = echarts.init(hotRef.value)
  hotChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (ps) => {
        const p = ps[0]
        const h = hot[p.dataIndex]
        const date = formatDate(h?.updateTime) || '-'
        return `${h?.title || ''}<br/>浏览：${p.value}<br/>更新：${date}`
      } },
    grid: { left: 90, right: 20, top: 16, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', inverse: true, data: hot.map((h) => (h.title || '').slice(0, 12)) },
    series: [{ type: 'bar', data: hot.map((h) => Number(h.viewCount)), itemStyle: { color: '#36cfc9' }, barWidth: 14 }]
  })

  const cats = stats.value.categoryStats || []
  catChart = echarts.init(catRef.value)
  catChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{ type: 'pie', radius: ['40%', '68%'], center: ['50%', '44%'],
      data: cats.map((c) => ({ name: c.categoryName, value: Number(c.noteCount) })), label: { formatter: '{b}: {c}' } }]
  })
  if (!cats.length) catChart.setOption({
    graphic: { type: 'text', left: 'center', top: '44%', style: { text: '暂无分类数据', fill: '#a8abb2' } }
  })
}

async function loadAll() {
  stats.value = (await statsApi.overview()).data || {}
  logs.value = (await logApi.list({ page: 1, size: 20 })).data.records || []
  renderCharts()
}

function resize() { trendChart?.resize(); hotChart?.resize(); catChart?.resize() }

onMounted(() => { loadAll(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); disposeAll() })
</script>

<style scoped>
.page { min-height: 100vh; }
.bar { display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--c-border); }
.title { flex: 1; font-weight: 600; }
.body { background: #f5f7fa; }
.cards { margin-bottom: 16px; }
.recent { margin-bottom: 16px; }
.chart { height: 240px; }
.mt { margin-top: 16px; }
.muted { color: #909399; font-size: 13px; }
</style>