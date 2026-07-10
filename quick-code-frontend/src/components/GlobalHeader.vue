<template>
  <a-layout-sider class="sidebar" :width="220" theme="light">
    <RouterLink to="/" class="brand">
      <img class="logo" src="@/assets/logo.png" alt="Logo" />
      <span class="site-title">QuickCode</span>
    </RouterLink>

    <a-menu
      v-model:selectedKeys="selectedKeys"
      mode="inline"
      :items="menuItems"
      class="sidebar-menu"
      @click="handleMenuClick"
    />

    <div class="sidebar-footer">
      <div v-if="loginUserStore.loginUser.id" class="user-profile">
        <a-dropdown placement="topLeft">
          <div class="user-profile-trigger">
            <a-avatar :src="loginUserStore.loginUser.userAvatar" :size="36" />
            <div class="user-info">
              <span class="user-name">{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
              <span class="user-role">{{ userRoleLabel }}</span>
            </div>
          </div>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="goProfile">
                <UserOutlined />
                个人设置
              </a-menu-item>
              <a-menu-item @click="doLogout">
                <LogoutOutlined />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
      <a-button v-else type="primary" block href="/user/login">登录</a-button>
    </div>
  </a-layout-sider>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import {
  LogoutOutlined,
  HomeOutlined,
  TeamOutlined,
  AppstoreOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const selectedKeys = ref<string[]>(['/'])

router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

const userRoleLabel = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户'
})

const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '首页',
    title: '首页',
  },
  {
    key: '/admin/userManage',
    icon: () => h(TeamOutlined),
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    icon: () => h(AppstoreOutlined),
    label: '应用管理',
    title: '应用管理',
  },
]

const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  if (key.startsWith('/')) {
    router.push(key)
  }
}

const goProfile = () => {
  router.push('/user/profile')
}

const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.sidebar {
  background: #fff;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
}

.sidebar :deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  text-decoration: none;
  flex-shrink: 0;
}

.logo {
  height: 36px;
  width: 36px;
}

.site-title {
  font-size: 18px;
  font-weight: 600;
  color: #1890ff;
}

.sidebar-menu {
  flex: 1;
  border-inline-end: none !important;
  padding: 0 8px;
}

.sidebar-menu :deep(.ant-menu-item) {
  border-radius: 8px;
  margin: 4px 0;
}

.sidebar-menu :deep(.ant-menu-item-selected) {
  background: #e6f4ff !important;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.user-profile-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-profile-trigger:hover {
  background: #f5f5f5;
}

.user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 12px;
  color: #999;
}
</style>
