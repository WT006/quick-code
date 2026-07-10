<template>
  <div class="profile-page">
    <div class="profile-container">
      <div class="page-header">
        <h1 class="page-title">个人设置</h1>
        <p class="page-desc">管理你的账号信息与安全设置</p>
      </div>

      <a-card class="profile-card" title="基本信息">
        <div class="avatar-section">
          <a-avatar :src="avatarUrl" :size="80">
            {{ displayName.charAt(0) || 'U' }}
          </a-avatar>
          <div class="avatar-actions">
            <a-upload
              :show-upload-list="false"
              accept="image/jpeg,image/png,image/webp"
              :before-upload="handleAvatarUpload"
            >
              <a-button :loading="avatarUploading">更换头像</a-button>
            </a-upload>
            <p class="avatar-tip">支持 JPG、PNG、WEBP，大小不超过 2MB</p>
          </div>
        </div>

        <a-form
          :model="profileForm"
          layout="vertical"
          class="profile-form"
          @finish="handleProfileSubmit"
        >
          <a-form-item label="账号">
            <a-input :value="loginUserStore.loginUser.userAccount" disabled />
          </a-form-item>
          <a-form-item
            label="用户名"
            name="userName"
            :rules="[
              { required: true, message: '请输入用户名' },
              { max: 20, message: '用户名不能超过 20 个字符' },
            ]"
          >
            <a-input v-model:value="profileForm.userName" placeholder="请输入用户名" />
          </a-form-item>
          <a-form-item
            label="个人简介"
            name="userProfile"
            :rules="[{ max: 200, message: '简介不能超过 200 个字符' }]"
          >
            <a-textarea
              v-model:value="profileForm.userProfile"
              placeholder="介绍一下自己（选填）"
              :rows="3"
              show-count
              :maxlength="200"
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" html-type="submit" :loading="profileSaving">
              保存资料
            </a-button>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card class="profile-card" title="修改密码">
        <a-form
          :model="passwordForm"
          layout="vertical"
          class="profile-form"
          @finish="handlePasswordSubmit"
        >
          <a-form-item
            label="原密码"
            name="oldPassword"
            :rules="[{ required: true, message: '请输入原密码' }]"
          >
            <a-input-password v-model:value="passwordForm.oldPassword" placeholder="请输入原密码" />
          </a-form-item>
          <a-form-item
            label="新密码"
            name="newPassword"
            :rules="[
              { required: true, message: '请输入新密码' },
              { min: 8, message: '密码长度不能小于 8 位' },
            ]"
          >
            <a-input-password v-model:value="passwordForm.newPassword" placeholder="请输入新密码" />
          </a-form-item>
          <a-form-item
            label="确认新密码"
            name="checkPassword"
            :rules="[
              { required: true, message: '请再次输入新密码' },
              { validator: validateCheckPassword },
            ]"
          >
            <a-input-password
              v-model:value="passwordForm.checkPassword"
              placeholder="请再次输入新密码"
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" html-type="submit" :loading="passwordSaving">
              修改密码
            </a-button>
          </a-form-item>
        </a-form>
      </a-card>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { updateMyProfile, updatePassword, uploadAvatar } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()

const profileForm = reactive({
  userName: '',
  userProfile: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  checkPassword: '',
})

const avatarUploading = ref(false)
const profileSaving = ref(false)
const passwordSaving = ref(false)

const avatarUrl = computed(() => loginUserStore.loginUser.userAvatar)
const displayName = computed(() => loginUserStore.loginUser.userName || 'U')

const initForm = () => {
  profileForm.userName = loginUserStore.loginUser.userName || ''
  profileForm.userProfile = loginUserStore.loginUser.userProfile || ''
}

onMounted(async () => {
  await loginUserStore.fetchLoginUser()
  initForm()
})

const validateCheckPassword = async (_rule: Rule, value: string) => {
  if (value && value !== passwordForm.newPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

const handleAvatarUpload = async (file: File) => {
  const isImage = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
  if (!isImage) {
    message.error('仅支持 JPG、PNG、WEBP 格式')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    message.error('文件大小不能超过 2MB')
    return false
  }

  avatarUploading.value = true
  try {
    const res = await uploadAvatar(file)
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchLoginUser()
      message.success('头像更新成功')
    } else {
      message.error('头像上传失败，' + res.data.message)
    }
  } catch {
    message.error('头像上传失败')
  } finally {
    avatarUploading.value = false
  }
  return false
}

const handleProfileSubmit = async () => {
  profileSaving.value = true
  try {
    const res = await updateMyProfile({
      userName: profileForm.userName,
      userProfile: profileForm.userProfile,
    })
    if (res.data.code === 0 && res.data.data) {
      loginUserStore.setLoginUser(res.data.data)
      message.success('资料保存成功')
    } else {
      message.error('保存失败，' + res.data.message)
    }
  } finally {
    profileSaving.value = false
  }
}

const handlePasswordSubmit = async () => {
  passwordSaving.value = true
  try {
    const res = await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      checkPassword: passwordForm.checkPassword,
    })
    if (res.data.code === 0) {
      message.success('密码修改成功')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.checkPassword = ''
    } else {
      message.error('修改失败，' + res.data.message)
    }
  } finally {
    passwordSaving.value = false
  }
}
</script>

<style scoped>
.profile-page {
  min-height: calc(100vh - 48px);
  padding: 32px 24px;
}

.profile-container {
  max-width: 640px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
}

.page-desc {
  margin: 0;
  color: #999;
  font-size: 14px;
}

.profile-card {
  margin-bottom: 24px;
  border-radius: 12px;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.avatar-tip {
  margin: 0;
  font-size: 12px;
  color: #999;
}

.profile-form {
  max-width: 480px;
}
</style>
