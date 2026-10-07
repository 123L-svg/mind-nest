<template>
  <header class="page-header">
    <div class="page-header-inner">
      <div class="ph-left">
        <el-button v-if="showBack" class="ph-back" text @click="onBack" aria-label="返回">
          <el-icon :size="19"><ArrowLeft /></el-icon>
        </el-button>
        <div class="ph-titles">
          <h1 class="ph-title">{{ title }}</h1>
          <p v-if="subtitle" class="ph-subtitle">{{ subtitle }}</p>
        </div>
      </div>
      <div v-if="$slots.default" class="ph-actions">
        <slot />
      </div>
    </div>
  </header>
</template>

<script setup>
/**
 * 次级页面统一页头：返回 + 标题/副标题 + 右侧操作区。
 * 固定吸顶、毛玻璃底；默认返回 /dashboard，可通过 backTo 自定义。
 */
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  showBack: { type: Boolean, default: true },
  backTo: { type: String, default: '/dashboard' }
})

const router = useRouter()
function onBack() {
  router.push(props.backTo)
}
</script>

<style scoped>
.page-header {
  position: sticky; top: 0; z-index: 100;
  height: var(--header-h);
  background: color-mix(in srgb, var(--c-surface) 88%, transparent);
  backdrop-filter: saturate(180%) blur(10px);
  -webkit-backdrop-filter: saturate(180%) blur(10px);
  border-bottom: 1px solid var(--c-border);
}
.page-header-inner {
  height: 100%;
  max-width: var(--c-page-max);
  margin: 0 auto;
  padding: 0 20px;
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
}
.ph-left { display: flex; align-items: center; gap: 10px; min-width: 0; }
.ph-back {
  flex-shrink: 0;
  width: 34px; height: 34px;
  padding: 0;
  border-radius: var(--r-md);
}
.ph-titles { min-width: 0; }
.ph-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 17px; font-weight: 600;
  color: var(--c-text);
  line-height: 1.3;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.ph-subtitle {
  margin: 0;
  font-size: 12px; color: var(--c-text-sub);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.ph-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

@media (max-width: 640px) {
  .page-header-inner { padding: 0 12px; }
  .ph-subtitle { display: none; }
}
</style>
