<template>
  <footer class="icp-footer">
    <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">陕ICP备2026028609号</a>
  </footer>
</template>

<style scoped>
/* 极简备案条：颜色继承页面主题，各页面自行控制定位与留白 */
.icp-footer {
  font-size: 12px;
  color: inherit;
  opacity: 0.55;
  text-align: center;
  user-select: none;
}
.icp-footer a {
  color: inherit;
  text-decoration: none;
  transition: opacity 0.2s ease;
}
.icp-footer a:hover {
  opacity: 1;
}
</style>
