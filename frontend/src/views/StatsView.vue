<template>
  <div class="page page-shell">
    <PageHeader title="数据概览" subtitle="笔记产出、热门内容与操作记录一览">
      <el-button size="small" @click="loadAll">刷新</el-button>
    </PageHeader>

    <main class="page-container">
      <el-row :gutter="16" class="cards">
        <el-col :xs="12" :md="6">
          <el-card class="stat-card card-hover">
            <div class="stat-icon i-primary"><el-icon :size="20"><Document /></el-icon></div>
            <el-statistic title="笔记数" :value="num(stats.noteCount)" />
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card class="stat-card card-hover">
            <div class="stat-icon i-cyan"><el-icon :size="20"><Collection /></el-icon></div>
            <el-statistic title="知识库" :value="num(stats.kbCount)" />
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card class="stat-card card-hover">
            <div class="stat-icon i-warning"><el-icon :size="20"><Delete /></el-icon></div>
            <el-statistic title="回收站" :value="num(stats.recycleCount)" />
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card class="stat-card card-hover">
            <div class="stat-icon i-green"><el-icon :size="20"><View /></el-icon></div>
            <el-statistic title="总浏览" :value="num(stats.totalViewCount)" />
          </el-card>
        </el-col>
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
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { Document, Collection, Delete, View } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { statsApi, logApi } from '@/api'
import { formatDateTime, formatDate } from '@/utils/format'

// 按需注册 echarts 组件，避免全量引入以减小体积
echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

// 统一清爽配色（与品牌渐变同系）
const CHART_COLORS = ['#409eff', '#36cfc9', '#9254de', '#ffc53d', '#ff7a45', '#73d13d']

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
    color: [CHART_COLORS[0]],
    tooltip: { trigger: 'axis' }, grid: { left: 32, right: 16, top: 24, bottom: 40 },
    xAxis: { type: 'category', boundaryGap: false, data: trend.map((t) => t.date.slice(5)), axisLabel: { interval: 4, rotate: 30 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'line', smooth: true, symbolSize: 6,
      data: trend.map((t) => Number(t.count)),
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64, 158, 255, 0.28)' },
          { offset: 1, color: 'rgba(64, 158, 255, 0.02)' }
        ])
      }
    }]
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
    series: [{
      type: 'bar', data: hot.map((h) => Number(h.viewCount)), barWidth: 14,
      itemStyle: {
        borderRadius: [0, 7, 7, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#36cfc9' }
        ])
      }
    }]
  })

  const cats = stats.value.categoryStats || []
  catChart = echarts.init(catRef.value)
  catChart.setOption({
    color: CHART_COLORS,
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
.cards { margin-bottom: 16px; }
.recent { margin-bottom: 16px; }
.chart { height: 240px; }
.mt { margin-top: 16px; }
.muted { color: var(--c-text-sub); font-size: 13px; }

/* 统计卡片：图标 + 数值横向布局 */
.stat-card :deep(.el-card__body) {
  display: flex; align-items: center; gap: 14px;
  padding: 18px 20px;
}
.stat-icon {
  flex-shrink: 0;
  width: 46px; height: 46px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 12px;
}
.i-primary { background: rgba(64, 158, 255, 0.12); color: #409eff; }
.i-cyan { background: rgba(54, 207, 201, 0.14); color: #13c2c2; }
.i-warning { background: rgba(230, 162, 60, 0.14); color: #e6a23c; }
.i-green { background: rgba(103, 194, 58, 0.14); color: #67c23a; }
.stat-card :deep(.el-statistic) { flex: 1; }
</style>