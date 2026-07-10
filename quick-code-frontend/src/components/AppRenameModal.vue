<template>
  <a-modal
    v-model:open="visible"
    title="重命名应用"
    :confirm-loading="submitting"
    ok-text="确定"
    cancel-text="取消"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form ref="formRef" :model="formData" :rules="rules" layout="vertical">
      <a-form-item label="应用名称" name="appName">
        <a-input
          v-model:value="formData.appName"
          placeholder="请输入应用名称"
          :maxlength="50"
          show-count
          @press-enter="handleSubmit"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import { updateApp } from '@/api/appController'

interface Props {
  open: boolean
  appId?: string | number
  appName?: string
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'success', appName: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const formData = reactive({
  appName: '',
})

const rules = {
  appName: [
    { required: true, message: '请输入应用名称', trigger: 'blur' },
    { min: 1, max: 50, message: '应用名称长度在1-50个字符', trigger: 'blur' },
  ],
}

const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

watch(
  () => props.open,
  (open) => {
    if (open) {
      formData.appName = props.appName || ''
      formRef.value?.clearValidate()
    }
  },
)

const handleCancel = () => {
  formData.appName = props.appName || ''
  formRef.value?.clearValidate()
}

const handleSubmit = async () => {
  if (!props.appId) {
    message.error('应用ID不存在')
    return
  }

  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  const trimmedName = formData.appName.trim()
  if (!trimmedName) {
    message.warning('请输入应用名称')
    return
  }

  if (trimmedName === (props.appName || '').trim()) {
    visible.value = false
    return
  }

  submitting.value = true
  try {
    const res = await updateApp({
      id: props.appId as number,
      appName: trimmedName,
    })
    if (res.data.code === 0) {
      message.success('重命名成功')
      emit('success', trimmedName)
      visible.value = false
    } else {
      message.error('重命名失败：' + res.data.message)
    }
  } catch (error) {
    console.error('重命名失败：', error)
    message.error('重命名失败')
  } finally {
    submitting.value = false
  }
}
</script>
