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
        <el-button :icon="Upload" @click="openImport">导入文档</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="置顶" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isTop === 1" type="danger" size="small" effect="plain">置顶</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="封面" width="140">
          <template #default="{ row }">
            <a v-if="row.coverImage" :href="row.coverImage" target="_blank" rel="noopener" class="cover-link" :title="row.coverImage">查看封面</a>
            <span v-else class="cover-empty">-</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="120">
          <!-- 后端 AdminArticleSummaryVO 返回 categoryName 时优先使用；缺失时回退本地分类表 -->
          <template #default="{ row }">{{ row.categoryName || categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="阅读" width="90">
          <template #default="{ row }">{{ row.viewCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 1"
              link
              type="success"
              :loading="statusLoadingId === row.id"
              @click="onChangeStatus(row, 1)"
            >
              发布
            </el-button>
            <el-button
              v-else
              link
              type="warning"
              :loading="statusLoadingId === row.id"
              @click="onChangeStatus(row, 2)"
            >
              下架
            </el-button>
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
      width="860px"
      destroy-on-close
      top="6vh"
    >
      <el-form ref="formRef" v-loading="formLoading" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="文章标题" maxlength="120" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <!-- 只允许从已有标签里选：后端 tagIds 只接受标签 ID，
               不做「提交时自动创建标签」，要新建标签请去标签管理页 -->
          <el-select
            v-model="form.tagIds"
            multiple
            filterable
            :multiple-limit="10"
            :loading="tagsLoading"
            :disabled="form.tagIds === undefined"
            :placeholder="form.tagIds === undefined ? '标签未加载，本次保存不会改动标签' : '选择标签（最多 10 个）'"
            style="width: 100%"
          >
            <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <span v-if="!tags.length && !tagsLoading" class="tip">还没有标签，去「标签管理」新建</span>
        </el-form-item>
        <el-form-item label="封面">
          <div class="cover-field">
            <el-input v-model="form.coverImage" placeholder="封面图片 URL，或点右侧上传" clearable />
            <el-upload
              action="#"
              :show-file-list="false"
              :auto-upload="false"
              :accept="IMAGE_ACCEPT"
              :on-change="onPickCover"
            >
              <el-button :icon="Picture" :loading="coverUploading">上传</el-button>
            </el-upload>
          </div>
          <div v-if="form.coverImage" class="cover-preview">
            <img :src="form.coverImage" alt="封面预览" />
            <el-button link type="danger" size="small" @click="form.coverImage = ''">清除</el-button>
          </div>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" placeholder="文章摘要（可选）" />
        </el-form-item>
        <el-form-item label="正文" prop="contentMd">
          <div class="md-editor">
            <div class="md-pane">
              <div class="pane-title">
                <span>编辑（Markdown）</span>
                <el-upload
                  action="#"
                  :show-file-list="false"
                  :auto-upload="false"
                  :accept="DOC_ACCEPT"
                  :on-change="onPickContent"
                >
                  <el-button link type="primary" size="small">从文件导入</el-button>
                </el-upload>
              </div>
              <el-input
                v-model="form.contentMd"
                type="textarea"
                :rows="16"
                class="md-input"
                placeholder="支持 Markdown：# 标题、**加粗**、- 列表、> 引用、``` 代码块、[链接](url)"
              />
            </div>
            <div class="md-pane">
              <div class="pane-title">预览</div>
              <div class="md-preview markdown-body" v-html="renderedMd"></div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
          <span class="tip">开启后文章在访客端列表置顶展示</span>
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

    <!-- 导入 Markdown 文档：文件只在浏览器里解析，不发往服务器 -->
    <el-dialog v-model="importVisible" title="导入 Markdown 文档" width="720px" top="8vh" destroy-on-close>
      <el-upload
        drag
        action="#"
        :show-file-list="false"
        :auto-upload="false"
        :accept="DOC_ACCEPT"
        :on-change="onPickDoc"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把 .md 文件拖到这里，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 .md / .markdown / .txt，单个不超过 2 MB。文件只在浏览器里解析，不会上传到服务器。
          </div>
        </template>
      </el-upload>

      <div v-if="importParsing" class="import-loading">正在解析…</div>

      <div v-else-if="importResult" class="import-result">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="文件">{{ importFileName }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ importResult.title || '（空）' }}</el-descriptions-item>
          <el-descriptions-item label="摘要">
            {{ importResult.summary || '（无，保存时用正文首段兜底）' }}
          </el-descriptions-item>
          <el-descriptions-item label="正文">
            {{ importResult.stats.lines }} 行 / {{ importResult.stats.chars }} 字
          </el-descriptions-item>
          <el-descriptions-item label="标签">
            <template v-if="importTagMatch.matched.length">
              <el-tag
                v-for="t in importTagMatch.matched"
                :key="t"
                size="small"
                type="success"
                effect="plain"
                class="tag-chip"
              >
                {{ t }}
              </el-tag>
            </template>
            <span v-else-if="!importTagMatch.missing.length" class="muted">无</span>
            <template v-if="importTagMatch.missing.length">
              <span class="muted">未匹配（需先到标签管理新建）：</span>
              <el-tag
                v-for="t in importTagMatch.missing"
                :key="t"
                size="small"
                type="warning"
                effect="plain"
                class="tag-chip"
              >
                {{ t }}
              </el-tag>
            </template>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-for="(w, i) in importResult.warnings"
          :key="i"
          :title="w"
          type="warning"
          :closable="false"
          show-icon
          class="import-warn"
        />

        <details class="import-body-preview">
          <summary>正文预览（前 40 行）</summary>
          <pre>{{ importPreviewBody }}</pre>
        </details>
      </div>

      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button :disabled="!importResult" :loading="importCreating" @click="importAsDraft">
          直接存为草稿
        </el-button>
        <el-button type="primary" :disabled="!importResult" @click="fillFromImport">
          填入编辑器
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Picture, UploadFilled } from '@element-plus/icons-vue'
import { getAdminArticles, getAdminArticle, createArticle, updateArticle, deleteArticle } from '../../api/articles'
import { getAdminCategories } from '../../api/categories'
import { getAdminTags } from '../../api/tags'
import { uploadFile, validateImage, IMAGE_ACCEPT } from '../../api/files'
import {
  DOC_ACCEPT,
  MAX_DOC_SIZE,
  readFileAsText,
  parseMarkdownDoc,
  matchTags
} from '../../utils/docImport'
import { renderMarkdown } from '../../utils/markdown'
import { formatDateTime as formatDate } from '../../utils/datetime'

const list = ref([])
const categories = ref([])
const tags = ref([])
const tagsLoading = ref(false)
const statusLoadingId = ref(null)
const loading = ref(false)
const saving = ref(false)
const formLoading = ref(false)
const statusFilter = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

// 文档导入相关状态
const importVisible = ref(false)
const importParsing = ref(false)
const importCreating = ref(false)
const importResult = ref(null)
const importFileName = ref('')
const coverUploading = ref(false)
// tagIds 的三态语义（对应后端 ArticleSaveRequest#tagIds）：
//   undefined = 本次不提交该字段，后端保留原绑定
//   []        = 清空标签
//   非空数组  = 全量覆盖
// 用 undefined 而不是 [] 做「未加载」的初始值，否则详情拉取失败时保存一次就把标签全清了
const form = reactive({
  id: null,
  title: '',
  summary: '',
  contentMd: '',
  categoryId: null,
  status: 0,
  coverImage: '',
  isTop: 0,
  tagIds: []
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  contentMd: [{ required: true, message: '请输入正文', trigger: 'blur' }]
}

// Markdown 实时预览（自实现轻量渲染，见 utils/markdown.js）
const renderedMd = computed(() => renderMarkdown(form.contentMd || ''))

// 导入结果里的标签名 → 系统已有标签 ID
const importTagMatch = computed(() => matchTags(importResult.value?.tagNames || [], tags.value))

// 正文预览只截前 40 行：整篇塞进 <pre> 在弹窗里滚动很难看
const importPreviewBody = computed(() => {
  const md = importResult.value?.contentMd || ''
  const lines = md.split(/\r?\n/)
  const head = lines.slice(0, 40).join('\n')
  return lines.length > 40 ? head + `\n…（共 ${lines.length} 行）` : head || '（空）'
})

function statusText(s) {
  return { 0: '草稿', 1: '已发布', 2: '已下架' }[s] || '未知'
}
function statusTagType(s) {
  return { 0: 'info', 1: 'success', 2: 'warning' }[s] || 'info'
}
function categoryName(id) {
  return categories.value.find((c) => c.id === id)?.name || '未分类'
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
  Object.assign(form, {
    id: null,
    title: '',
    summary: '',
    contentMd: '',
    categoryId: categories.value[0]?.id ?? null,
    status: 0,
    coverImage: '',
    isTop: 0,
    // 新建文章还没有任何绑定，传 [] 是准确的语义
    tagIds: []
  })
  dialogVisible.value = true
}

// 编辑必须先拉详情：列表接口不返回 contentMd（刻意排除大字段），
// 若直接拿列表行回填，正文会是空字符串，保存后原文即被清空。
async function openEdit(row) {
  isEdit.value = true
  dialogVisible.value = true
  formLoading.value = true
  try {
    const detail = await getAdminArticle(row.id)
    Object.assign(form, {
      id: detail.id,
      title: detail.title || '',
      summary: detail.summary || '',
      contentMd: detail.contentMd || '',
      categoryId: detail.categoryId,
      status: detail.status ?? 0,
      coverImage: detail.coverImage || '',
      isTop: detail.isTop || 0,
      // 详情没带 tagIds（老后端 / 字段缺失）时留 undefined：
      // 保存时这个键会被丢掉，后端按「不修改」处理，标签原样保留
      tagIds: Array.isArray(detail.tagIds) ? [...detail.tagIds] : undefined
    })
  } catch (e) {
    dialogVisible.value = false
    ElMessage.error('加载文章详情失败')
  } finally {
    formLoading.value = false
  }
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form }
    delete payload.id
    // undefined 会被 JSON.stringify 丢掉，等价于「不传该字段」
    if (payload.tagIds === undefined) delete payload.tagIds
    if (isEdit.value) {
      await updateArticle(form.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createArticle(payload)
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

// 列表里的快捷发布 / 下架
// 不能只提交 { status }：后端 ArticleSaveRequest.title 是 @NotBlank，会被校验拦成 400；
// 且 updateArticle 内部有 request.getTitle().trim()，title 为 null 直接 NPE。
// 所以这里先拉一次详情，改 status 后整份提交，同时把 tagIds 原样带上，
// 避免「点一下发布，标签被清空」。
async function onChangeStatus(row, status) {
  const actionText = status === 1 ? '发布' : '下架'
  try {
    await ElMessageBox.confirm(`确定${actionText}文章「${row.title}」吗？`, actionText, {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  statusLoadingId.value = row.id
  try {
    const detail = await getAdminArticle(row.id)
    const payload = {
      title: detail.title,
      summary: detail.summary,
      contentMd: detail.contentMd,
      categoryId: detail.categoryId,
      coverImage: detail.coverImage,
      isTop: detail.isTop,
      status
    }
    if (Array.isArray(detail.tagIds)) payload.tagIds = detail.tagIds
    await updateArticle(row.id, payload)
    ElMessage.success(`${actionText}成功`)
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    statusLoadingId.value = null
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除文章「${row.title}」吗？`, '提示', { type: 'warning' })
    await deleteArticle(row.id)
    ElMessage.success('已删除')
    // 删掉的是本页最后一条时往前翻一页，避免停在空白页
    if (list.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    load()
  } catch (e) {
    /* 取消或失败 */
  }
}

// ---------- 文档导入 ----------
// 全流程在浏览器里完成：读文件 → 解析 front matter / 标题 / 摘要 / 标签 → 填进编辑器。
// 不经过后端，所以后端没上传接口时这个功能也能用。

function openImport() {
  importResult.value = null
  importFileName.value = ''
  importVisible.value = true
}

async function onPickDoc(uploadItem) {
  const raw = uploadItem?.raw
  if (!raw) return
  if (raw.size > MAX_DOC_SIZE) {
    ElMessage.error(`文件 ${(raw.size / 1024 / 1024).toFixed(1)} MB，超过 2 MB 上限`)
    return
  }
  importParsing.value = true
  importResult.value = null
  importFileName.value = raw.name
  try {
    const text = await readFileAsText(raw)
    importResult.value = parseMarkdownDoc(text, raw.name)
  } catch (e) {
    ElMessage.error('读取文件失败：' + (e.message || '未知错误'))
  } finally {
    importParsing.value = false
  }
}

// 填进编辑器：先按新建文章初始化表单，再用解析结果覆盖。
// 不直接保存——导入的内容经常需要人工看一眼标题和标签再决定发不发。
function fillFromImport() {
  const r = importResult.value
  if (!r) return
  const match = importTagMatch.value
  importVisible.value = false
  openCreate()
  Object.assign(form, {
    title: r.title,
    summary: r.summary,
    contentMd: r.contentMd,
    coverImage: '',
    isTop: r.isTop,
    status: 0,
    tagIds: match.tagIds
  })
  if (match.missing.length) {
    ElMessage.warning(`标签「${match.missing.join('、')}」不存在，已跳过；需要的话先去标签管理新建`)
  }
}

// 直接存草稿：批量导入时省去逐篇打开编辑器
async function importAsDraft() {
  const r = importResult.value
  if (!r) return
  if (!categories.value.length) {
    ElMessage.warning('还没有分类，请先到「分类管理」新建一个')
    return
  }
  importCreating.value = true
  try {
    await createArticle({
      title: r.title,
      summary: r.summary,
      contentMd: r.contentMd,
      categoryId: categories.value[0].id,
      coverImage: '',
      isTop: r.isTop,
      status: 0,
      tagIds: importTagMatch.value.tagIds
    })
    ElMessage.success('已存为草稿')
    importVisible.value = false
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    importCreating.value = false
  }
}

// ---------- 文件上传 ----------

async function onPickCover(uploadItem) {
  const raw = uploadItem?.raw
  if (!raw) return
  const err = validateImage(raw)
  if (err) {
    ElMessage.error(err)
    return
  }
  coverUploading.value = true
  try {
    const res = await uploadFile(raw, { scope: 'cover' })
    form.coverImage = res.url
    ElMessage.success('封面已上传')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    coverUploading.value = false
  }
}

// 编辑器里的「从文件导入」：只替换正文，已有标题和摘要不覆盖
async function onPickContent(uploadItem) {
  const raw = uploadItem?.raw
  if (!raw) return
  if (raw.size > MAX_DOC_SIZE) {
    ElMessage.error('文件超过 2 MB 上限')
    return
  }
  try {
    const text = await readFileAsText(raw)
    const r = parseMarkdownDoc(text, raw.name)
    form.contentMd = r.contentMd
    if (!form.title) form.title = r.title
    if (!form.summary) form.summary = r.summary
    ElMessage.success(`已导入正文（${r.stats.chars} 字）`)
    if (r.warnings.length) ElMessage.warning(r.warnings[0])
  } catch (e) {
    ElMessage.error('读取文件失败')
  }
}

onMounted(async () => {
  try {
    categories.value = (await getAdminCategories()) || []
  } catch (e) {
    /* 分类加载失败不阻塞 */
  }
  tagsLoading.value = true
  try {
    tags.value = (await getAdminTags()) || []
  } catch (e) {
    /* 标签加载失败不阻塞：编辑弹窗里选择器会禁用，保存时不动标签 */
  } finally {
    tagsLoading.value = false
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
.cover-link {
  color: #409eff;
  font-size: 13px;
  text-decoration: none;
}
.cover-link:hover {
  text-decoration: underline;
}
.cover-empty {
  color: #c0c4cc;
}
.md-editor {
  display: flex;
  gap: 12px;
  width: 100%;
}
.md-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.pane-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.md-input :deep(.el-textarea__inner) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  line-height: 1.6;
}
.md-preview {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 8px 12px;
  min-height: 332px;
  max-height: 332px;
  overflow: auto;
  background: #fff;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  word-break: break-word;
}
.tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
/* ---- 封面上传 ---- */
.cover-field {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}
.cover-field .el-input {
  flex: 1;
}
.cover-preview {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.cover-preview img {
  max-height: 72px;
  max-width: 240px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  object-fit: cover;
}
/* ---- 文档导入弹窗 ---- */
.import-loading {
  margin-top: 16px;
  color: #909399;
  font-size: 13px;
}
.import-result {
  margin-top: 16px;
}
.tag-chip {
  margin-right: 6px;
}
.muted {
  font-size: 12px;
  color: #909399;
  margin-right: 6px;
}
.import-warn {
  margin-top: 10px;
}
.import-body-preview {
  margin-top: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px 12px;
  background: #fafafa;
}
.import-body-preview summary {
  cursor: pointer;
  font-size: 13px;
  color: #606266;
}
.import-body-preview pre {
  margin: 8px 0 0;
  max-height: 220px;
  overflow: auto;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
/* Markdown 预览基础排版（与访客端详情页保持一致） */
.markdown-body h1,
.markdown-body h2,
.markdown-body h3 {
  margin: 0.6em 0 0.3em;
  line-height: 1.4;
}
.markdown-body h1 {
  font-size: 20px;
}
.markdown-body h2 {
  font-size: 18px;
}
.markdown-body h3 {
  font-size: 16px;
}
.markdown-body p {
  margin: 0.4em 0;
}
.markdown-body ul,
.markdown-body ol {
  padding-left: 1.4em;
  margin: 0.4em 0;
}
.markdown-body blockquote {
  margin: 0.4em 0;
  padding: 4px 12px;
  border-left: 3px solid #409eff;
  background: #f5f7fa;
  color: #606266;
}
.markdown-body pre {
  background: #f6f8fa;
  border-radius: 4px;
  padding: 8px 12px;
  overflow: auto;
  margin: 0.4em 0;
}
.markdown-body pre code {
  background: transparent;
  padding: 0;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
}
.markdown-body code {
  background: #f6f8fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
}
.markdown-body a {
  color: #409eff;
}
.markdown-body strong {
  font-weight: 600;
}
</style>
