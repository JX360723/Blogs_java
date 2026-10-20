<template>
  <div class="article-manage">
    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="statusFilter" @change="onFilterChange">
          <el-radio-button :value="''">全部</el-radio-button>
          <el-radio-button :value="1">已发布</el-radio-button>
          <el-radio-button :value="0">草稿</el-radio-button>
          <el-radio-button :value="2">已下架</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建文章</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="120">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="阅读" width="90" />
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

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑文章' : '新建文章'"
      width="680px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="文章标题" maxlength="120" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" placeholder="文章摘要（可选）" />
        </el-form-item>
        <el-form-item label="正文" prop="contentMd">
          <el-input v-model="form.contentMd" type="textarea" :rows="10" placeholder="支持 Markdown 文本（MVP 以纯文本展示）" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">草稿</el-radio>
            <el-radio :value="1">发布</el-radio>
            <el-radio :value="2">下架</el-radio>
          </el-radio-group>
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
import { getAdminArticles, createArticle, updateArticle, deleteArticle } from '../../api/articles'
import { getAdminCategories } from '../../api/categories'

const list = ref([])
const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const statusFilter = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({ id: null, title: '', summary: '', contentMd: '', categoryId: null, status: 0 })

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  contentMd: [{ required: true, message: '请输入正文', trigger: 'blur' }]
}

function statusText(s) {
  return { 0: '草稿', 1: '已发布', 2: '已下架' }[s] || '未知'
}
function statusTagType(s) {
  return { 0: 'info', 1: 'success', 2: 'warning' }[s] || 'info'
}
function categoryName(id) {
  return categories.value.find((c) => c.id === id)?.name || '未分类'
}
function formatDate(ts) {
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (statusFilter.value !== '') params.status = statusFilter.value
    const data = await getAdminArticles(params)
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  pageNum.value = 1
  load()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, title: '', summary: '', contentMd: '', categoryId: categories.value[0]?.id ?? null, status: 0 })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    title: row.title,
    summary: row.summary || '',
    contentMd: row.contentMd || '',
    categoryId: row.categoryId,
    status: row.status
  })
  dialogVisible.value = true
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateArticle(form.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createArticle({ ...form })
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
    await ElMessageBox.confirm(`确定删除文章「${row.title}」吗？`, '提示', { type: 'warning' })
    await deleteArticle(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 取消或失败 */
  }
}

onMounted(async () => {
  try {
    categories.value = (await getAdminCategories()) || []
  } catch (e) {
    /* 分类加载失败不阻塞 */
  }
  load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
