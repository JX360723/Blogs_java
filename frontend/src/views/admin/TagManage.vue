<template>
  <div class="tag-manage">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="hint">单篇文章最多绑定 10 个标签；删除标签会自动解绑所有关联文章</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建标签</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="名称" min-width="160">
          <template #default="{ row }">
            <el-tag size="small" type="primary" effect="plain">{{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <!-- TagVO 只返回 createdAt：后端刻意省掉 updatedAt，页面没有「标签更新时间」的需求 -->
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !list.length" description="还没有标签，点右上角新建" />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑标签' : '新建标签'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="标签名称" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="标签描述（可选）"
            maxlength="255"
            show-word-limit
          />
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
import { formatDateTime as formatDate } from '../../utils/datetime'

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
    // 只提交后端 TagSaveRequest 认的两个字段。把 id 一起 POST 过去虽然会被 Jackson
    // 忽略，但请求体里出现 id 容易让人误以为这里支持「指定 ID 新建」
    const payload = { name: form.name.trim(), description: form.description || null }
    if (isEdit.value) {
      await updateTag(form.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createTag(payload)
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
    await ElMessageBox.confirm(
      `确定删除标签「${row.name}」吗？删除后所有文章上的这个标签会一并解绑，且不可恢复。`,
      '删除标签',
      { type: 'warning', confirmButtonText: '确定删除' }
    )
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
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}
.hint {
  font-size: 12px;
  color: #909399;
}
</style>
