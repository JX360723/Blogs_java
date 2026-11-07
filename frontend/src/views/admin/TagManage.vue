<template>
  <div class="tag-manage">
    <el-alert
      v-if="!tagsReady"
      class="stage-alert"
      type="info"
      show-icon
      :closable="false"
      title="标签能力属阶段 3，后端接口尚未交付"
      description="当前连的是真实后端，/api/v1/admin/tags 还未实现，因此本页暂不发起请求（避免 404 误报）。标签表与接口落地后，把本文件里的 tagsReady 置为 true 即可启用。"
    />
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" :disabled="!tagsReady" @click="openCreate">新建标签</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160">
          <template #default="{ row }">
            <el-tag size="small" type="primary" effect="plain">{{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑标签' : '新建标签'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="标签名称" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="标签描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getAdminTags, createTag, updateTag, deleteTag } from '../../api/tags'
import { USE_MOCK } from '../../mock'
import { formatDateTime as formatDate } from '../../utils/datetime'

// 后端标签模块属阶段 3：tag / article_tag 表与 /api/v1/admin/tags 尚未交付。
// 连真实后端时若照常发请求，会被 404 拦截并弹出「资源不存在」，看起来像接口坏了。
// 所以这里显式降级：mock 模式（演示）正常跑，真实后端模式不请求、页面给出说明。
// 标签接口上线后把它改成 true（或改为按后端能力探测）即可，其余代码无需改动。
const tagsReady = USE_MOCK

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({ id: null, name: '', description: '' })

const rules = {
  name: [{ required: true, message: '请输入标签名称', trigger: 'blur' }]
}

async function load() {
  // 阶段 3 未交付：不发请求，避免 404 噪音
  if (!tagsReady) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = (await getAdminTags()) || []
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, name: '', description: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { id: row.id, name: row.name, description: row.description || '' })
  dialogVisible.value = true
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateTag(form.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createTag({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除标签「${row.name}」吗？`, '提示', { type: 'warning' })
    await deleteTag(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 取消或失败 */
  }
}

onMounted(load)
</script>

<style scoped>
.stage-alert {
  margin-bottom: 16px;
}
.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
</style>
