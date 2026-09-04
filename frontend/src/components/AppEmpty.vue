<template>
  <el-empty :image-size="size" :description="description">
    <template v-if="$slots.default" #default><slot /></template>
  </el-empty>
</template>

<script setup>
defineProps({
  description: { type: String, default: '暂无数据' },
  size: { type: Number, default: 90 }
})
</script>