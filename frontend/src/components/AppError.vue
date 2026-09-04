<template>
  <el-result icon="error" :title="title">
    <template #extra>
      <el-button type="primary" @click="$emit('retry')">{{ retryText }}</el-button>
    </template>
  </el-result>
</template>

<script setup>
defineProps({
  title: { type: String, default: '加载失败，请稍后重试' },
  retryText: { type: String, default: '重试' }
})
defineEmits(['retry'])
</script>