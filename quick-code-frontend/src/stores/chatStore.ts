import { defineStore } from 'pinia'
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { getAppVoById } from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'
import {
  appendContentSegment,
  appendStatusSegment,
  appendToolSegment,
  clearStreamingCodeContent,
  parseChatHistoryMessage,
  type MessageSegment,
} from '@/utils/chatMessageParser'
import request from '@/request'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'
import { useLoginUserStore } from '@/stores/loginUser'

export interface ChatMessage {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
  currentStatus?: string
  statuses?: string[]
  tools?: string[]
  segments?: MessageSegment[]
}

interface AppChatSession {
  appId: string
  appInfo?: API.AppVO
  messages: ChatMessage[]
  isGenerating: boolean
  userInput: string
  loadingHistory: boolean
  hasMoreHistory: boolean
  lastCreateTime?: string
  historyLoaded: boolean
  previewUrl: string
  initialized: boolean
}

const eventSources = new Map<string, EventSource>()
const streamCompletedFlags = new Map<string, boolean>()

function createEmptySession(appId: string): AppChatSession {
  return {
    appId,
    messages: [],
    isGenerating: false,
    userInput: '',
    loadingHistory: false,
    hasMoreHistory: false,
    historyLoaded: false,
    previewUrl: '',
    initialized: false,
  }
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<Record<string, AppChatSession>>({})

  function getSession(appId: string): AppChatSession {
    if (!sessions.value[appId]) {
      sessions.value[appId] = createEmptySession(appId)
    }
    return sessions.value[appId]
  }

  function isVueProject(appId: string): boolean {
    return getSession(appId).appInfo?.codeGenType === CodeGenTypeEnum.VUE_PROJECT
  }

  function updatePreview(appId: string) {
    const session = getSession(appId)
    const codeGenType = session.appInfo?.codeGenType || CodeGenTypeEnum.HTML
    session.previewUrl = `${getStaticPreviewUrl(codeGenType, appId)}?t=${Date.now()}`
  }

  async function loadChatHistory(appId: string, isLoadMore = false, force = false) {
    const session = getSession(appId)
    if (!appId || session.loadingHistory) return
    if (session.isGenerating && !force) return

    session.loadingHistory = true
    try {
      const params: API.listAppChatHistoryParams = {
        appId: appId as unknown as number,
        pageSize: 10,
      }
      if (isLoadMore && session.lastCreateTime) {
        params.lastCreateTime = session.lastCreateTime
      }
      const res = await listAppChatHistory(params)
      if (res.data.code === 0 && res.data.data) {
        const chatHistories = res.data.data.records || []
        if (chatHistories.length > 0) {
          const historyMessages: ChatMessage[] = chatHistories
            .map((chat) => {
              const type = (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai'
              if (type === 'user') {
                return {
                  type,
                  content: chat.message || '',
                  createTime: chat.createTime,
                }
              }
              const parsed = parseChatHistoryMessage(chat.message || '')
              return {
                type,
                content: parsed.content,
                statuses: parsed.statuses,
                tools: parsed.tools,
                segments: parsed.segments,
                createTime: chat.createTime,
              }
            })
            .reverse()

          if (isLoadMore) {
            session.messages.unshift(...historyMessages)
          } else {
            session.messages = historyMessages
          }
          session.lastCreateTime = chatHistories[chatHistories.length - 1]?.createTime
          session.hasMoreHistory = chatHistories.length === 10
        } else {
          session.hasMoreHistory = false
        }
        session.historyLoaded = true
      }
    } catch (error) {
      console.error('加载对话历史失败：', error)
      message.error('加载对话历史失败')
    } finally {
      session.loadingHistory = false
    }
  }

  async function syncIfPendingAi(appId: string) {
    const session = getSession(appId)
    if (session.isGenerating || session.messages.length === 0) return
    const last = session.messages[session.messages.length - 1]
    if (last.type === 'user') {
      await loadChatHistory(appId, false, true)
      if (session.messages.length >= 1) {
        updatePreview(appId)
      }
    }
  }

  async function fetchAppInfo(appId: string) {
    const session = getSession(appId)
    if (session.initialized && session.isGenerating) {
      return
    }

    try {
      const res = await getAppVoById({ id: appId as unknown as number })
      if (res.data.code === 0 && res.data.data) {
        session.appInfo = res.data.data

        if (!session.isGenerating) {
          await loadChatHistory(appId)
        }
        if (session.messages.length >= 1) {
          updatePreview(appId)
        }

        const loginUserStore = useLoginUserStore()
        const isOwner = session.appInfo.userId === loginUserStore.loginUser.id
        if (
          session.appInfo.initPrompt &&
          isOwner &&
          session.messages.length === 0 &&
          session.historyLoaded &&
          !session.isGenerating
        ) {
          await sendInitialMessage(appId, session.appInfo.initPrompt)
        }
        session.initialized = true
      } else {
        message.error('获取应用信息失败')
        throw new Error('获取应用信息失败')
      }
    } catch (error) {
      console.error('获取应用信息失败：', error)
      message.error('获取应用信息失败')
      throw error
    }
  }

  async function ensureInitialized(appId: string) {
    const session = getSession(appId)
    if (!session.initialized) {
      await fetchAppInfo(appId)
    } else if (!session.isGenerating) {
      await syncIfPendingAi(appId)
    }
  }

  function addAiPlaceholder(session: AppChatSession): number {
    session.messages.push({
      type: 'ai',
      content: '',
      loading: true,
      currentStatus: '',
      statuses: [],
      tools: [],
      segments: [],
    })
    return session.messages.length - 1
  }

  async function sendInitialMessage(appId: string, prompt: string) {
    const session = getSession(appId)
    session.messages.push({ type: 'user', content: prompt })
    const aiMessageIndex = addAiPlaceholder(session)
    session.isGenerating = true
    await generateCode(appId, prompt, aiMessageIndex)
  }

  async function sendMessage(appId: string, content: string) {
    const session = getSession(appId)
    if (!content.trim() || session.isGenerating) return

    session.messages.push({ type: 'user', content })
    const aiMessageIndex = addAiPlaceholder(session)
    session.isGenerating = true
    await generateCode(appId, content, aiMessageIndex)
  }

  function handleStreamError(appId: string, aiMessageIndex: number, error?: unknown) {
    console.error('生成代码失败：', error)
    const session = getSession(appId)
    if (session.messages[aiMessageIndex]) {
      session.messages[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
      session.messages[aiMessageIndex].loading = false
    }
    message.error('生成失败，请重试')
    session.isGenerating = false
    streamCompletedFlags.set(appId, true)
    closeEventSource(appId)
  }

  function finishGeneration(appId: string, aiMessageIndex: number) {
    const session = getSession(appId)
    streamCompletedFlags.set(appId, true)
    session.isGenerating = false

    const aiMsg = session.messages[aiMessageIndex]
    if (aiMsg) {
      clearStreamingCodeContent(aiMsg)
      const hasDisplayContent = isVueProject(appId)
        ? (aiMsg.segments?.some((s) => s.type === 'status' || s.type === 'tool') ?? false)
        : (aiMsg.statuses?.length ?? 0) > 0 || (aiMsg.tools?.length ?? 0) > 0
      if (!hasDisplayContent) {
        const fallback = '代码生成已完成，请在右侧预览网站。'
        aiMsg.statuses = [...(aiMsg.statuses || []), fallback]
        if (isVueProject(appId)) {
          aiMsg.segments = appendStatusSegment(aiMsg.segments, fallback)
        }
      }
      aiMsg.loading = false
    }

    closeEventSource(appId)
    setTimeout(async () => {
      await loadChatHistory(appId, false, true)
      updatePreview(appId)
    }, 1000)
  }

  function closeEventSource(appId: string) {
    const es = eventSources.get(appId)
    if (es) {
      es.close()
      eventSources.delete(appId)
    }
  }

  async function generateCode(appId: string, userMessage: string, aiMessageIndex: number) {
    const session = getSession(appId)
    closeEventSource(appId)
    streamCompletedFlags.set(appId, false)

    try {
      const baseURL = request.defaults.baseURL || API_BASE_URL
      const params = new URLSearchParams({
        appId,
        message: userMessage,
      })
      const url = `${baseURL}/app/chat/gen/code?${params}`
      const eventSource = new EventSource(url, { withCredentials: true })
      eventSources.set(appId, eventSource)

      let fullContent = ''

      eventSource.onmessage = (event) => {
        if (streamCompletedFlags.get(appId)) return

        try {
          const parsed = JSON.parse(event.data)
          const chunkType = parsed.t || 'content'
          const chunkData = parsed.d
          if (chunkData === undefined || chunkData === null) return

          const aiMessage = session.messages[aiMessageIndex]
          if (!aiMessage) return

          if (chunkType === 'status') {
            if (!aiMessage.statuses) aiMessage.statuses = []
            aiMessage.statuses.push(chunkData)
            aiMessage.currentStatus = chunkData
            if (isVueProject(appId)) {
              aiMessage.segments = appendStatusSegment(aiMessage.segments, chunkData)
            }
            if (typeof chunkData === 'string' && chunkData.includes('重新生成')) {
              fullContent = ''
              aiMessage.content = ''
              aiMessage.segments = []
              aiMessage.tools = []
            }
            if (session.isGenerating) {
              aiMessage.loading = true
            }
          } else if (chunkType === 'reset') {
            fullContent = ''
            aiMessage.content = ''
            aiMessage.segments = []
            aiMessage.tools = []
            if (session.isGenerating) {
              aiMessage.loading = true
            }
          } else if (chunkType === 'tool') {
            if (isVueProject(appId)) {
              aiMessage.segments = appendToolSegment(aiMessage.segments, chunkData)
            }
            if (!aiMessage.tools) aiMessage.tools = []
            aiMessage.tools.push(chunkData)
            if (session.isGenerating) {
              aiMessage.loading = true
            }
          } else {
            fullContent += chunkData
            aiMessage.content = fullContent
            if (isVueProject(appId)) {
              aiMessage.segments = appendContentSegment(aiMessage.segments, chunkData)
            }
            aiMessage.loading = false
          }
        } catch (error) {
          console.error('解析消息失败:', error)
          handleStreamError(appId, aiMessageIndex, error)
        }
      }

      eventSource.addEventListener('done', () => {
        if (streamCompletedFlags.get(appId)) return
        finishGeneration(appId, aiMessageIndex)
      })

      eventSource.addEventListener('business-error', (event: MessageEvent) => {
        if (streamCompletedFlags.get(appId)) return
        try {
          const errorData = JSON.parse(event.data)
          const errorMessage = errorData.message || '生成过程中出现错误'
          if (session.messages[aiMessageIndex]) {
            session.messages[aiMessageIndex].content = `❌ ${errorMessage}`
            session.messages[aiMessageIndex].loading = false
          }
          message.error(errorMessage)
          streamCompletedFlags.set(appId, true)
          session.isGenerating = false
          closeEventSource(appId)
        } catch (parseError) {
          handleStreamError(appId, aiMessageIndex, parseError)
        }
      })

      eventSource.onerror = () => {
        if (streamCompletedFlags.get(appId) || !session.isGenerating) return
        if (eventSource.readyState === EventSource.CONNECTING) {
          finishGeneration(appId, aiMessageIndex)
        } else {
          handleStreamError(appId, aiMessageIndex, new Error('SSE连接错误'))
        }
      }
    } catch (error) {
      handleStreamError(appId, aiMessageIndex, error)
    }
  }

  function removeSession(appId: string) {
    closeEventSource(appId)
    streamCompletedFlags.delete(appId)
    delete sessions.value[appId]
  }

  return {
    sessions,
    getSession,
    isVueProject,
    updatePreview,
    loadChatHistory,
    syncIfPendingAi,
    fetchAppInfo,
    ensureInitialized,
    sendInitialMessage,
    sendMessage,
    removeSession,
  }
})
