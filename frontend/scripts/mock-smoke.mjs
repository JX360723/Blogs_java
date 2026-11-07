import { createMockAdapter } from '../src/mock/index.js'

const adapter = createMockAdapter()
const call = (method, url, data) =>
  adapter({ method, url: '/api/v1' + url, data: data ? JSON.stringify(data) : undefined, headers: {} })

let pass = 0, fail = 0
const check = (name, cond, extra = '') => {
  if (cond) { pass++; console.log('✅', name) }
  else { fail++; console.log('❌', name, extra) }
}

// 1. 公开详情带 tags 数组
let r = await call('get', '/articles/1')
check('公开详情返回 tags', Array.isArray(r.data.data.tags) && r.data.data.tags.length === 2,
  JSON.stringify(r.data.data.tags))
check('tags 元素含 id/name', r.data.data.tags[0].id && r.data.data.tags[0].name)

// 2. 管理端详情带 tagIds 与 tags
r = await call('get', '/admin/articles/1')
check('管理端详情 tagIds 是数组', Array.isArray(r.data.data.tagIds), JSON.stringify(r.data.data.tagIds))
check('管理端详情 tags 与 tagIds 数量一致',
  r.data.data.tags.length === r.data.data.tagIds.length)

// 3. tagId 筛选
r = await call('get', '/articles?tagId=2&pageNum=1&pageSize=10')
const ids = r.data.data.list.map((a) => a.id)
check('tagId=2 只命中已发布且带该标签的文章', ids.length === 1 && ids[0] === 2, JSON.stringify(ids))

// 4. 不存在的 tagId 返回空列表而不是报错
r = await call('get', '/articles?tagId=999&pageNum=1&pageSize=10')
check('不存在的 tagId 返回空列表', r.data.code === 200 && r.data.data.total === 0)

// 5. 删除标签级联清理文章 tagIds
r = await call('delete', '/admin/tags/1')
check('删除标签成功', r.data.code === 200, JSON.stringify(r.data))
r = await call('get', '/admin/articles/1')
check('删除后文章 tagIds 不再包含 1', !r.data.data.tagIds.includes(1), JSON.stringify(r.data.data.tagIds))
r = await call('get', '/articles/1')
check('删除后公开详情 tags 同步减少', r.data.data.tags.length === 1, JSON.stringify(r.data.data.tags))

// 6. 新建文章带 tagIds
r = await call('post', '/admin/articles', {
  title: '冒烟测试文章', contentMd: '# hi', categoryId: 1, status: 1, isTop: 0, tagIds: [2, 3]
})
const newId = r.data.data.id
r = await call('get', '/admin/articles/' + newId)
check('新建文章保留 tagIds', JSON.stringify(r.data.data.tagIds) === '[2,3]', JSON.stringify(r.data.data.tagIds))

// 7. 更新时不传 tagIds → 保留原值（三态语义）
r = await call('put', '/admin/articles/' + newId, { title: '只改标题', status: 1 })
r = await call('get', '/admin/articles/' + newId)
check('更新不传 tagIds 时标签保留', JSON.stringify(r.data.data.tagIds) === '[2,3]', JSON.stringify(r.data.data.tagIds))

// 8. 传 [] 清空
await call('put', '/admin/articles/' + newId, { title: '只改标题', status: 1, tagIds: [] })
r = await call('get', '/admin/articles/' + newId)
check('更新传 [] 时标签被清空', r.data.data.tagIds.length === 0)

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
process.exit(fail ? 1 : 0)
