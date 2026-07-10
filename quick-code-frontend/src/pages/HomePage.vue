<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)

// 我的应用数据
const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 精选应用数据
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

// 优化提示词功能已移除

// 创建应用
const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      // 跳转到对话页面，确保ID是字符串类型
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

// 加载我的应用
const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

// 加载精选应用
const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 格式化时间函数已移除，不再需要显示创建时间

// 页面加载时获取数据
onMounted(() => {
  loadMyApps()
  loadFeaturedApps()

  // 鼠标跟随光效
  const handleMouseMove = (e: MouseEvent) => {
    const { clientX, clientY } = e
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100

    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)

  // 清理事件监听器
  return () => {
    document.removeEventListener('mousemove', handleMouseMove)
  }
})
</script>

<template>
  <div id="homePage">
    <div class="container">
      <!-- Hero 区域 -->
      <div class="hero-card">
        <div class="hero-content">
          <h1 class="hero-title">AI <span class="highlight">应用生成平台</span></h1>
          <p class="hero-description">一句话轻松创建网站应用</p>

          <div class="prompt-bar">
            <a-textarea
              v-model:value="userPrompt"
              placeholder="描述你想创建的应用，例如：创建一个企业官网"
              :maxlength="1000"
              :auto-size="{ minRows: 1, maxRows: 4 }"
              class="prompt-input"
            />
            <a-button type="primary" size="large" class="generate-btn" @click="createApp" :loading="creating">
              生成应用 →
            </a-button>
          </div>

          <div class="quick-actions">
            <a-button
              type="default"
              @click="
                setPrompt(
                  '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
                )
              "
              >企业官网</a-button
            >
            <a-button
              type="default"
              @click="
                setPrompt(
                  '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
                )
              "
              >个人博客</a-button
            >
            <a-button
              type="default"
              @click="
                setPrompt(
                  '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
                )
              "
              >在线商城</a-button
            >
            <a-button
              type="default"
              @click="
                setPrompt(
                  '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
                )
              "
              >作品展示</a-button
            >
            <a-button
              type="default"
              @click="
                setPrompt(
                  '创建一个功能完善的管理后台系统，包含用户管理、数据统计仪表盘、内容管理、权限控制和系统设置等模块。采用现代化的侧边栏布局，支持数据可视化图表展示。',
                )
              "
              >管理后台</a-button
            >
          </div>
        </div>
        <img class="hero-robot" src="@/assets/photo.png" alt="AI Assistant" />
      </div>

      <!-- 我的应用 -->
      <div class="section">
        <h2 class="section-title">我的应用</h2>
        <div class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="loadMyApps"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div class="section">
        <h2 class="section-title">精选案例</h2>
        <div class="featured-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="loadFeaturedApps"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

/* Hero 卡片 */
.hero-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #e8f4fd 0%, #dbeafe 50%, #e0e7ff 100%);
  border-radius: 20px;
  padding: 48px 40px;
  margin-bottom: 32px;
  position: relative;
  overflow: hidden;
}

.hero-content {
  flex: 1;
  max-width: 640px;
  z-index: 1;
}

.hero-title {
  font-size: 36px;
  font-weight: 700;
  margin: 0 0 12px;
  color: #1a1a1a;
  line-height: 1.3;
}

.hero-title .highlight {
  color: #1890ff;
}

.hero-description {
  font-size: 16px;
  color: #666;
  margin: 0 0 28px;
}

.prompt-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 8px 12px 8px 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  margin-bottom: 20px;
}

.prompt-input {
  flex: 1;
  border: none !important;
  box-shadow: none !important;
  font-size: 15px;
  line-height: 24px;
  resize: none;
  padding: 0;
}

.prompt-input:focus,
.prompt-input:hover {
  box-shadow: none !important;
}

.prompt-input :deep(.ant-input) {
  border: none;
  box-shadow: none;
  padding: 10px 0;
  word-break: break-word;
  white-space: pre-wrap;
  line-height: 24px;
  min-height: 24px;
}

.prompt-input :deep(.ant-input:focus) {
  box-shadow: none;
}

.generate-btn {
  border-radius: 8px;
  flex-shrink: 0;
  height: 44px;
  padding: 0 24px;
  font-size: 15px;
}

.quick-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.quick-actions .ant-btn {
  border-radius: 20px;
  padding: 4px 16px;
  height: auto;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(24, 144, 255, 0.15);
  color: #555;
}

.quick-actions .ant-btn:hover {
  color: #1890ff;
  border-color: #1890ff;
  background: #fff;
}

.hero-robot {
  width: 280px;
  height: 280px;
  object-fit: contain;
  flex-shrink: 0;
  margin-left: 24px;
}

/* 区域 */
.section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 20px;
  color: #1a1a1a;
}

.app-grid,
.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
}

@media (max-width: 768px) {
  #homePage {
    padding: 16px;
  }

  .hero-card {
    flex-direction: column;
    padding: 32px 24px;
    text-align: center;
  }

  .hero-content {
    max-width: 100%;
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-robot {
    width: 180px;
    height: 180px;
    margin: 24px 0 0;
  }

  .prompt-bar {
    flex-direction: column;
    padding: 12px;
  }

  .generate-btn {
    width: 100%;
  }

  .quick-actions {
    justify-content: center;
  }

  .app-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }
}
</style>
