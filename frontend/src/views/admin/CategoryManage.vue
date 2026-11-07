<template>
  <div class="category-manage">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="hint">支持多级分类；上级分类选空即为顶级分类，不允许把分类挂到自己的子孙下面</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建分类</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="名称" min-width="200">
          <template #default="{ row }">
            <!-- 按层级缩进，让父子关系在扁平表格里也能看出来 -->
            <span :style="{ paddingLeft: depthOf(row.id) * 18 + 'px' }">
              <el-tag v-if="depthOf(row.id) > 0" size="small" type="info" effect="plain" class="lv-tag">
                L{{ depthOf(row.id) }}
              </el-tag>
              {{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="上级分类" width="140">
          <template #default="{ row }">
            {{ row.parentId ? nameOf(row.parentId) : '顶级' }}
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑分类' : '新建分类'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="分类名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="上级分类">
          <!-- 编辑时自身与所有子孙已从候选里剔除，避免前端就选出环 -->
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'label', children: 'children' }"
            node-key="value"
            check-strictly
            clearable
            default-expand-all
            placeholder="不选则为顶级分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="分类描述（可选）"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
          <span class="tip">数字越小越靠前</span>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getAdminCategories, createCategory, updateCategory, deleteCategory } from '../../api/categories'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({ id: null, name: '', parentId: null, description: '', sortOrder: 0 })

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const nameOf = (id) => list.value.find((c) => c.id === id)?.name || '-'

// 每个分类的层级深度（顶级为 0）。带深度上限兜底：万一库里已有脏数据成环，
// 这里不能跟着无限递归把页面卡死
const depthMap = computed(() => {
  const parentOf = new Map(list.value.map((c) => [c.id, c.parentId]))
  const depth = (id, guard = 0) => {
    if (guard > 10) return 0
    const p = parentOf.get(id)
    return p ? 1 + depth(p, guard + 1) : 0
  }
  const m = new Map()
  list.value.forEach((c) => m.set(c.id, depth(c.id)))
  return m
})
const depthOf = (id) => depthMap.value.get(id) || 0

// 扁平列表组装成树，供 el-tree-select 使用
const categoryTree = computed(() => {
  const map = new Map()
  const roots = []
  list.value.forEach((c) => map.set(c.id, { value: c.id, label: c.name, children: [] }))
  list.value.forEach((c) => {
    const node = map.get(c.id)
    const parent = c.parentId ? map.get(c.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  const strip = (nodes) => {
    nodes.forEach((n) => {
      if (!n.children.length) delete n.children
      else strip(n.children)
    })
  }
  strip(roots)
  return roots
})

// 编辑时把自己和所有子孙从「上级分类」候选里去掉：
// 后端 validateParent 会沿 parent_id 回溯拦成 400，前端先拦掉体验更好
const parentOptions = computed(() => {
  if (!isEdit.value || !form.id) return categoryTree.value
  const banned = new Set([form.id])
  let changed = true
  while (changed) {
    changed = false
    list.value.forEach((c) => {
      if (c.parentId && banned.has(c.parentId) && !banned.has(c.id)) {
        banned.add(c.id)
        changed = true
      }
    })
  }
  const filter = (nodes) =>
    nodes
      .filter((n) => !banned.has(n.value))
      .map((n) => (n.children ? { ...n, children: filter(n.children) } : { ...n }))
  return filter(categoryTree.value)
})

async function load() {
  loading.value = true
  try {
    list.value = (await getAdminCategories()) || []
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: null,
    name: '',
    parentId: null,
    description: '',
    sortOrder: list.value.length + 1
  })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    name: row.name,
    parentId: row.parentId ?? null,
    description: row.description || '',
    sortOrder: row.sortOrder || 0
  })
  dialogVisible.value = true
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    // 只提交后端 CategorySaveRequest 认的四个字段，id 走路径参数
    const payload = {
      name: form.name.trim(),
      parentId: form.parentId || null,
      description: form.description || null,
      sortOrder: form.sortOrder ?? 0
    }
    if (isEdit.value) {
      await updateCategory(form.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createCategory(payload)
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
  const hasChildren = list.value.some((c) => c.parentId === row.id)
  try {
    await ElMessageBox.confirm(
      hasChildren
        ? `分类「${row.name}」下还有子分类，删除后子分类会变成顶级分类。确定删除吗？`
        : `确定删除分类「${row.name}」吗？`,
      '提示',
      { type: 'warning' }
    )
    await deleteCategory(row.id)
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
.tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.lv-tag {
  margin-right: 6px;
}
</style>
