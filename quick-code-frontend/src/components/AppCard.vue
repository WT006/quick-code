<template>
  <div class="app-card" :class="{ 'app-card--featured': featured }">
    <div class="app-preview">
      <img v-if="app.cover" :src="app.cover" :alt="app.appName" />
      <div v-else class="app-placeholder">
        <span>🤖</span>
      </div>
      <div class="app-overlay">
        <div class="app-overlay-actions">
          <div class="action-row">
            <a-button type="primary" size="small" @click.stop="handleViewChat">查看对话</a-button>
            <a-button v-if="app.deployKey" type="default" size="small" @click.stop="handleViewWork">
              查看作品
            </a-button>
          </div>
          <div v-if="editable" class="action-row">
            <a-button type="default" size="small" @click.stop="handleRename">重命名</a-button>
            <a-popconfirm
              title="确定要删除这个应用吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="handleDelete"
            >
              <a-button danger size="small" @click.stop>删除</a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>
    </div>
    <div class="app-info">
      <div class="app-info-left">
        <a-avatar :src="app.user?.userAvatar" :size="40">
          {{ app.user?.userName?.charAt(0) || 'U' }}
        </a-avatar>
      </div>
      <div class="app-info-right">
        <h3 class="app-title">{{ app.appName || '未命名应用' }}</h3>
        <p class="app-author">
          {{ app.user?.userName || (featured ? '官方' : '未知用户') }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  app: API.AppVO
  featured?: boolean
  editable?: boolean
}

interface Emits {
  (e: 'view-chat', appId: string | number | undefined): void
  (e: 'view-work', app: API.AppVO): void
  (e: 'rename', app: API.AppVO): void
  (e: 'delete', app: API.AppVO): void
}

const props = withDefaults(defineProps<Props>(), {
  featured: false,
  editable: false,
})

const emit = defineEmits<Emits>()

const handleViewChat = () => {
  emit('view-chat', props.app.id)
}

const handleViewWork = () => {
  emit('view-work', props.app)
}

const handleRename = () => {
  emit('rename', props.app)
}

const handleDelete = () => {
  emit('delete', props.app)
}
</script>

<style scoped>
.app-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid #f0f0f0;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
  cursor: pointer;
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.app-preview {
  height: 160px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.app-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-placeholder {
  font-size: 40px;
  color: #d9d9d9;
}

.app-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.app-card:hover .app-overlay {
  opacity: 1;
}

.app-overlay-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.app-info {
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-info-left {
  flex-shrink: 0;
}

.app-info-right {
  flex: 1;
  min-width: 0;
}

.app-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 2px;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-author {
  font-size: 13px;
  color: #999;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
