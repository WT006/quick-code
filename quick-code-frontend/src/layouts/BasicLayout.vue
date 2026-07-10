<template>
  <a-layout class="basic-layout">
    <GlobalHeader />
    <a-layout class="main-layout">
      <a-layout-content class="main-content">
        <router-view v-slot="{ Component }">
          <keep-alive :include="['AppChatPage']">
            <component :is="Component" :key="$route.fullPath" />
          </keep-alive>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: #f5f7fa;
}

.main-layout {
  margin-left: 220px;
  min-height: 100vh;
}

.main-content {
  width: 100%;
  padding: 0;
  background: none;
  margin: 0;
  min-height: 100vh;
}
</style>
