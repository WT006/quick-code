<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
        <a-button
          v-if="isOwner"
          type="text"
          size="small"
          class="rename-btn"
          @click="showRenameModal"
        >
          <template #icon>
            <FormOutlined />
          </template>
        </a-button>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
      </div>
      <div class="header-right">
        <a-button type="default" @click="showAppDetail">
          <template #icon>
            <InfoCircleOutlined />
          </template>
          应用详情
        </a-button>
        <a-button
            type="primary"
            ghost
            @click="downloadCode"
            :loading="downloading"
            :disabled="!isOwner"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
        </a-button>
        <a-button type="primary" @click="deployApp" :loading="deploying">
          <template #icon>
            <CloudUploadOutlined />
          </template>
          部署
        </a-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <!-- 左侧对话区域 -->
      <div class="chat-section">
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesContainer">
          <!-- 加载更多按钮 -->
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>
          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <!-- Vue：状态 + 开头 + 步骤 + 结尾，统一时间线 -->
                <template v-if="isVueProject">
                  <div
                      v-if="message.currentStatus && !message.segments?.length"
                      class="workflow-status"
                  >
                    <a-spin v-if="message.loading" size="small" />
                    <span>{{ message.currentStatus }}</span>
                  </div>
                  <div v-if="message.segments?.length" class="message-timeline">
                    <template v-for="(seg, segIndex) in message.segments" :key="segIndex">
                      <div v-if="seg.type === 'status'" class="workflow-status-item">
                        <a-spin
                            v-if="message.loading && segIndex === message.segments!.length - 1"
                            size="small"
                            class="status-spinner"
                        />
                        <span>{{ seg.value }}</span>
                      </div>
                      <div v-else-if="seg.type === 'tool'" :class="getToolSegmentClass(seg.value)">
                        <MarkdownRenderer :content="seg.value" />
                      </div>
                      <MarkdownRenderer
                          v-else-if="seg.value"
                          :content="seg.value"
                          :streaming="isGenerating && index === messages.length - 1 && message.type === 'ai' && segIndex === message.segments!.length - 1"
                      />
                    </template>
                  </div>
                </template>
                <!-- HTML / 多文件 -->
                <template v-else>
                  <div v-if="message.currentStatus && !message.statuses?.length" class="workflow-status">
                    <a-spin v-if="message.loading" size="small" />
                    <span>{{ message.currentStatus }}</span>
                  </div>
                  <div v-if="message.statuses?.length" class="workflow-status-history">
                    <div v-for="(status, statusIndex) in message.statuses" :key="statusIndex" class="workflow-status-item">
                      <a-spin v-if="message.loading && statusIndex === message.statuses!.length - 1" size="small" class="status-spinner" />
                      <span>{{ status }}</span>
                    </div>
                  </div>
                  <div v-if="message.tools?.length" class="tool-calls-panel">
                    <div
                        v-for="(tool, toolIndex) in message.tools"
                        :key="toolIndex"
                        :class="getToolSegmentClass(tool)"
                    >
                      <MarkdownRenderer :content="tool" />
                    </div>
                  </div>
                  <MarkdownRenderer
                      v-if="message.content"
                      :content="message.content"
                      :streaming="isGenerating && index === messages.length - 1 && message.type === 'ai'"
                  />
                </template>
              </div>
            </div>
          </div>
        </div>

        <!-- 选中元素信息展示 -->
        <a-alert
            v-if="selectedElementInfo"
            class="selected-element-alert"
            type="info"
            closable
            @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <div class="element-header">
                <span class="element-tag">
                  选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                </span>
                <span v-if="selectedElementInfo.id" class="element-id">
                  #{{ selectedElementInfo.id }}
                </span>
                <span v-if="selectedElementInfo.className" class="element-class">
                  .{{ selectedElementInfo.className.split(' ').join('.') }}
                </span>
              </div>
              <div class="element-details">
                <div v-if="selectedElementInfo.textContent" class="element-item">
                  内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                  {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                </div>
                <div v-if="selectedElementInfo.pagePath" class="element-item">
                  页面路径: {{ selectedElementInfo.pagePath }}
                </div>
                <div class="element-item">
                  选择器:
                  <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                </div>
              </div>
            </div>
          </template>
        </a-alert>

        <!-- 用户消息输入框 -->
        <div class="input-container">
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <a-textarea
                  v-model:value="userInput"
                  :placeholder="getInputPlaceholder()"
                  :rows="4"
                  :maxlength="1000"
                  @keydown.enter.prevent="sendMessage"
                  :disabled="isGenerating || !isOwner"
              />
            </a-tooltip>
            <a-textarea
                v-else
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                @keydown.enter.prevent="sendMessage"
                :disabled="isGenerating"
            />
            <div class="input-actions">
              <a-button
                  type="primary"
                  @click="sendMessage"
                  :loading="isGenerating"
                  :disabled="!isOwner"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧网页展示区域 -->
      <div class="preview-section">
        <div class="preview-header">
          <h3>生成后的网页展示</h3>
          <div class="preview-actions">
            <a-button
                v-if="isOwner && previewUrl"
                type="link"
                :danger="isEditMode"
                @click="toggleEditMode"
                :class="{ 'edit-mode-active': isEditMode }"
                style="padding: 0; height: auto; margin-right: 12px"
            >
              <template #icon>
                <EditOutlined />
              </template>
              {{ isEditMode ? '退出编辑' : '编辑模式' }}
            </a-button>
            <a-button v-if="previewUrl" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
          </div>
        </div>
        <div class="preview-content">
          <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
            <div class="placeholder-icon">🌐</div>
            <p>网站文件生成完成后将在这里展示</p>
          </div>
          <div v-else-if="isGenerating" class="preview-loading">
            <a-spin size="large" />
            <p>正在生成网站...</p>
          </div>
          <iframe
              v-else
              :src="previewUrl"
              class="preview-iframe"
              frameborder="0"
              @load="onIframeLoad"
          ></iframe>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
        v-model:open="appDetailVisible"
        :app="appInfo"
        :show-actions="isOwner || isAdmin"
        @rename="showRenameModal"
        @delete="deleteApp"
    />

    <AppRenameModal
        v-model:open="renameModalVisible"
        :app-id="appInfo?.id"
        :app-name="appInfo?.appName"
        @success="handleRenameSuccess"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
        v-model:open="deployModalVisible"
        :deploy-url="deployUrl"
        @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onActivated, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { useChatStore } from '@/stores/chatStore'
import {
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
} from '@/api/appController'
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import { getToolSegmentClass } from '@/utils/chatMessageParser'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import AppRenameModal from '@/components/AppRenameModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  FormOutlined,
} from '@ant-design/icons-vue'

defineOptions({
  name: 'AppChatPage',
})

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const chatStore = useChatStore()

const appId = computed(() => String(route.params.id || ''))
const session = computed(() => chatStore.getSession(appId.value))

const messages = computed(() => session.value.messages)
const isGenerating = computed(() => session.value.isGenerating)
const loadingHistory = computed(() => session.value.loadingHistory)
const hasMoreHistory = computed(() => session.value.hasMoreHistory)
const previewUrl = computed(() => session.value.previewUrl)
const appInfo = computed(() => session.value.appInfo)

const userInput = computed({
  get: () => session.value.userInput,
  set: (val: string) => {
    session.value.userInput = val
  },
})

const messagesContainer = ref<HTMLElement>()
const previewReady = ref(false)

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// 下载相关
const downloading = ref(false)

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
})

// 权限相关
const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

const isVueProject = computed(() => appInfo.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT)

const appDetailVisible = ref(false)
const renameModalVisible = ref(false)

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

const showRenameModal = () => {
  appDetailVisible.value = false
  renameModalVisible.value = true
}

const handleRenameSuccess = (newName: string) => {
  chatStore.updateAppName(appId.value, newName)
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await chatStore.loadChatHistory(appId.value, true)
}

// 发送消息
const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value) {
    return
  }

  let content = userInput.value.trim()
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`
    }
    content += elementContext
  }
  userInput.value = ''

  if (selectedElementInfo.value) {
    clearSelectedElement()
    if (isEditMode.value) {
      toggleEditMode()
    }
  }

  await chatStore.sendMessage(appId.value, content)
  await nextTick()
  scrollToBottom()
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 初始化页面
const initPage = async () => {
  const id = appId.value
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }
  try {
    await chatStore.ensureInitialized(id)
    await nextTick()
    scrollToBottom()
  } catch {
    router.push('/')
  }
}

watch(
  () => session.value.messages,
  async () => {
    await nextTick()
    scrollToBottom()
  },
  { deep: true }
)

watch(isGenerating, async (generating) => {
  if (!generating) {
    await nextTick()
    scrollToBottom()
  }
})

// 下载代码
const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  downloading.value = true
  try {
    const API_BASE_URL = request.defaults.baseURL || ''
    const url = `${API_BASE_URL}/app/download/${appId.value}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    // 获取文件名
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
    // 下载文件
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.click()
    // 清理
    URL.revokeObjectURL(downloadUrl)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 部署应用
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value as unknown as number,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true
      message.success('部署成功')
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 在新窗口打开预览
const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  previewReady.value = true
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (iframe) {
    visualEditor.init(iframe)
    visualEditor.onIframeLoad()
  }
}

// 删除应用
const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      chatStore.removeSession(String(appInfo.value.id))
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

// 可视化编辑相关函数
const toggleEditMode = () => {
  // 检查 iframe 是否已经加载
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (!iframe) {
    message.warning('请等待页面加载完成')
    return
  }
  // 确保 visualEditor 已初始化
  if (!previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '请描述你想生成的网站，越详细效果越好哦'
}

// 页面加载时获取应用信息
onMounted(() => {
  initPage()
  window.addEventListener('message', (event) => {
    visualEditor.handleIframeMessage(event)
  })
})

// 从其他页面返回时恢复状态
onActivated(() => {
  initPage()
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #fdfdfd;
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.rename-btn {
  color: #8c8c8c;
  padding: 0 4px;
}

.rename-btn:hover {
  color: #1890ff;
}

.header-right {
  display: flex;
  gap: 12px;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
}

/* 左侧对话区域 */
.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
}

.user-message .message-content {
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: #f5f5f5;
  color: #1a1a1a;
  padding: 8px 12px;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

.workflow-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #f0f5ff;
  border: 1px solid #d6e4ff;
  border-radius: 8px;
  color: #1677ff;
  font-size: 13px;
}

.workflow-status-history {
  margin-bottom: 8px;
}

.workflow-status-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  margin-bottom: 4px;
  background: #f0f5ff;
  border-left: 3px solid #1677ff;
  border-radius: 4px;
  color: #1677ff;
  font-size: 13px;
}

.status-spinner {
  flex-shrink: 0;
}

.workflow-status-item:last-child {
  margin-bottom: 0;
}

.tool-calls-panel {
  margin-bottom: 10px;
}

.message-timeline {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-timeline .workflow-status-item {
  margin-bottom: 0;
}

.message-timeline .tool-call-item {
  margin-bottom: 0;
}

.tool-call-item {
  padding: 8px 12px;
  margin-bottom: 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #434343;
}

.tool-call-item.tool-write {
  background: #f6ffed;
  border-left: 3px solid #52c41a;
  color: #389e0d;
}

.tool-call-item.tool-read {
  background: #f9f0ff;
  border-left: 3px solid #722ed1;
  color: #531dab;
}

.tool-call-item.tool-modify {
  background: #fff7e6;
  border-left: 3px solid #fa8c16;
  color: #d46b08;
}

.tool-call-item.tool-delete {
  background: #fff2f0;
  border-left: 3px solid #ff4d4f;
  color: #cf1322;
}

.tool-call-item.tool-other {
  background: #fafafa;
  border-left: 3px solid #8c8c8c;
  color: #595959;
}

.tool-call-item :deep(p) {
  margin: 0;
}

/* 加载更多按钮 */
.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
}

.input-wrapper {
  position: relative;
}

.input-wrapper .ant-input {
  padding-right: 50px;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
}

/* 右侧预览区域 */
.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.preview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.preview-loading p {
  margin-top: 16px;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.selected-element-alert {
  margin: 0 16px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section,
  .preview-section {
    flex: none;
    height: 50vh;
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 12px 16px;
  }

  .app-name {
    font-size: 16px;
  }

  .main-content {
    padding: 8px;
    gap: 8px;
  }

  .message-content {
    max-width: 85%;
  }

  /* 选中元素信息样式 */
  .selected-element-alert {
    margin: 0 16px;
  }

  .selected-element-info {
    line-height: 1.4;
  }

  .element-header {
    margin-bottom: 8px;
  }

  .element-details {
    margin-top: 8px;
  }

  .element-item {
    margin-bottom: 4px;
    font-size: 13px;
  }

  .element-item:last-child {
    margin-bottom: 0;
  }

  .element-tag {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 14px;
    font-weight: 600;
    color: #007bff;
  }

  .element-id {
    color: #28a745;
    margin-left: 4px;
  }

  .element-class {
    color: #ffc107;
    margin-left: 4px;
  }

  .element-selector-code {
    font-family: 'Monaco', 'Menlo', monospace;
    background: #f6f8fa;
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
    color: #d73a49;
    border: 1px solid #e1e4e8;
  }

  /* 编辑模式按钮样式 */
  .edit-mode-active {
    background-color: #52c41a !important;
    border-color: #52c41a !important;
    color: white !important;
  }

  .edit-mode-active:hover {
    background-color: #73d13d !important;
    border-color: #73d13d !important;
  }
}
</style>
