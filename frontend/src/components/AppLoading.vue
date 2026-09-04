<template>
  <div class="app-loading">
    <el-skeleton :rows :animated />
  </div>
</template>

<script setup>
defineProps({
  rows: { type: Number, default: 5 }
})
</script>

<style scoped>
.app-loading { padding: 12px 4px; }
</style>