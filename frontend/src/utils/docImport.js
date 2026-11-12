// Markdown 文档导入解析器
//
// 纯前端实现：文件在浏览器里读、在浏览器里解析，不上传到服务器。
// 这样做的理由——导入是「一次性把本地文件搬进编辑器」，没必要让后端
// 承担一次文件传输 + 一次磁盘落地 + 一次清理；而且后端没上传接口时也能用。
//
// 支持的 front matter 写法（YAML 的极小子集，够覆盖 Obsidian / Hexo / Hugo 常见用法）：
//
//   ---
//   title: 我的文章
//   summary: 一句话摘要
//   tags: [Vue, 工程化]
//   date: 2024-01-01
//   isTop: 1
//   draft: true
//   ---
//
//   # 正文标题
//
// 也支持 tags 写成多行列表：
//
//   tags:
//     - Vue
//     - 工程化
//
// 解析失败不抛异常，全部以 warnings 形式返回，由界面提示用户。

/** 单个文档大小上限：2 MB（纯文本再大也不合理，超过基本是选错文件） */
export const MAX_DOC_SIZE = 2 * 1024 * 1024

/** 允许导入的扩展名 */
export const DOC_ACCEPT = '.md,.markdown,.txt'

/**
 * 读取文件文本内容，自动处理 BOM 与编码。
 *
 * 编码判定顺序：UTF-8 BOM → UTF-8 严格解码 → GBK 回退 → UTF-8 宽松解码。
 * 为什么不是直接 TextDecoder('utf-8')：国内 Windows 记事本另存的 .md 常是 GBK，
 * 直接 UTF-8 解码会得到满屏 U+FFFD 乱码，而用户往往意识不到是编码问题。
 *
 * @param {File} file 浏览器 File 对象
 * @returns {Promise<string>} 文本内容
 */
export async function readFileAsText(file) {
  const buffer = await file.arrayBuffer()
  const bytes = new Uint8Array(buffer)

  // UTF-8 BOM：不剥掉的话，第一行的 `---` 会变成 `\uFEFF---`，front matter 直接识别不到
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    return new TextDecoder('utf-8').decode(buffer.slice(3))
  }

  try {
    // fatal=true：遇到非法字节序列直接抛错，用于判断「这是不是合法 UTF-8」
    return new TextDecoder('utf-8', { fatal: true }).decode(buffer)
  } catch (e) {
    try {
      // Chrome / Edge / Firefox 均支持 gbk 解码器
      return new TextDecoder('gbk').decode(buffer)
    } catch (e2) {
      // 极老浏览器兜底：宽松 UTF-8，乱码但至少不崩
      return new TextDecoder('utf-8').decode(buffer)
    }
  }
}

/** 去掉字符串两端的引号（YAML 里 `title: "xxx"` 的写法） */
function unquote(value) {
  const v = String(value == null ? '' : value).trim()
  if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
    return v.slice(1, -1)
  }
  return v
}

/**
 * 拆出 front matter 与正文。
 *
 * 只认文件开头（允许前面有空行）的 `---` 块，正文中间的 `---` 是分隔线，不能当 front matter。
 *
 * @param {string} text 全文
 * @returns {{ data: Object|null, body: string }}
 */
export function parseFrontMatter(text) {
  const src = String(text || '').replace(/^\s+/, '')
  if (!src.startsWith('---')) return { data: null, body: text }

  // 第一行 --- 之后，到下一个独立成行的 --- 结束
  const m = src.match(/^---[ \t]*\r?\n([\s\S]*?)\r?\n---[ \t]*(?:\r?\n|$)/)
  if (!m) return { data: null, body: text }

  const data = {}
  let lastKey = null

  for (const line of m[1].split(/\r?\n/)) {
    if (!line.trim() || line.trim().startsWith('#')) continue

    // 列表项：`  - Vue`
    const listItem = line.match(/^\s*-\s+(.*)$/)
    if (listItem && lastKey) {
      if (!Array.isArray(data[lastKey])) data[lastKey] = []
      const item = unquote(listItem[1])
      if (item) data[lastKey].push(item)
      continue
    }

    // 键值对：`title: xxx`
    const kv = line.match(/^([A-Za-z0-9_-]+)\s*:\s*(.*)$/)
    if (!kv) continue

    const key = kv[1].toLowerCase()
    const rawValue = kv[2].trim()
    lastKey = key

    if (rawValue === '') {
      // 值留空：可能是下面的列表块，先占位 null，遇到列表项会自动变成数组
      data[key] = null
    } else if (rawValue.startsWith('[') && rawValue.endsWith(']')) {
      // 行内数组：`tags: [Vue, Java]`
      data[key] = rawValue
        .slice(1, -1)
        .split(',')
        .map((s) => unquote(s))
        .filter(Boolean)
    } else {
      data[key] = unquote(rawValue)
    }
  }

  return { data, body: src.slice(m[0].length) }
}

/**
 * 从正文里摘出第一个一级标题，并从正文中移除该行。
 *
 * 只在前 20 行里找：正文深处的 `# xxx` 多半是小节标题，拿来当文章标题会张冠李戴。
 */
function extractH1(body) {
  const lines = body.split(/\r?\n/)
  const limit = Math.min(lines.length, 20)
  for (let i = 0; i < limit; i++) {
    const m = lines[i].match(/^\s{0,3}#\s+(.+?)\s*#*\s*$/)
    if (m) {
      const title = m[1].trim()
      lines.splice(i, 1)
      return { title, body: lines.join('\n') }
    }
  }
  return { title: '', body }
}

/**
 * 由文件名推断标题。
 *
 * 去掉扩展名、日期前缀（2024-01-01-）、序号前缀（01-），把连字符/下划线换成空格。
 * 中文文件名原样保留，不擅自改大小写。
 */
export function titleFromFilename(filename) {
  let base = String(filename || '').replace(/\.[^.]+$/, '')
  base = base.replace(/^\d{4}[-_.]\d{1,2}[-_.]\d{1,2}[-_.\s]*/, '')
  base = base.replace(/^\d{1,3}[-_.\s]+/, '')
  base = base.replace(/[-_]+/g, ' ').trim()
  return base
}

/**
 * 从正文生成摘要：剥掉 Markdown 语法，取前 N 个字符的纯文本。
 */
export function extractSummary(body, maxLength = 150) {
  const plain = String(body || '')
    .replace(/```[\s\S]*?```/g, ' ') // 代码块整体丢弃
    .replace(/~~~[\s\S]*?~~~/g, ' ')
    .replace(/`[^`]*`/g, ' ') // 行内代码
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ') // 图片
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1') // 链接保留文字
    .replace(/^\s{0,3}#{1,6}\s+/gm, '') // 标题标记
    .replace(/^\s{0,3}>\s?/gm, '') // 引用
    .replace(/^\s*[-*+]\s+/gm, '') // 无序列表
    .replace(/^\s*\d+\.\s+/gm, '') // 有序列表
    .replace(/[*_~|]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return plain.slice(0, maxLength)
}

/**
 * 统计正文里指向本地相对路径的图片。
 *
 * 这是导入最容易踩的坑：从 Obsidian / Typora 导出的 md，图片常写成
 * `![](./images/a.png)` 或 `![](attachments/a.png)`，导入后浏览器根本取不到，
 * 文章一发布就是一片破图。与其等用户发布后才发现，不如导入时就明确提示。
 */
function countLocalImages(body) {
  const matches = String(body || '').match(/!\[[^\]]*\]\((?!https?:\/\/|data:|\/\/)[^)]+\)/g) || []
  return matches.filter((m) => {
    const url = m.replace(/^!\[[^\]]*\]\(/, '').replace(/\)$/, '').trim()
    return url && !url.startsWith('/')
  }).length
}

/** front matter 里表示「真」的写法归一化 */
function truthy(value) {
  const v = String(value == null ? '' : value).trim().toLowerCase()
  return ['1', 'true', 'yes', 'y', 'on'].includes(v)
}

/**
 * 解析 Markdown 文档，产出可直接填入文章编辑器的字段。
 *
 * @param {string} text     全文（已解码）
 * @param {string} filename 原始文件名，用于兜底标题
 * @returns {{
 *   title: string, summary: string, contentMd: string,
 *   tagNames: string[], isTop: number, status: number,
 *   warnings: string[], stats: { chars: number, lines: number, localImages: number }
 * }}
 */
export function parseMarkdownDoc(text, filename = '') {
  const warnings = []
  const source = String(text || '')

  if (!source.trim()) {
    return {
      title: titleFromFilename(filename),
      summary: '',
      contentMd: '',
      tagNames: [],
      isTop: 0,
      status: 0,
      warnings: ['文件内容为空'],
      stats: { chars: 0, lines: 0, localImages: 0 }
    }
  }

  if (source.includes('\uFFFD')) {
    warnings.push('检测到乱码字符，文件可能是 GBK 或二进制格式，请确认内容是否正确')
  }

  const { data, body: afterFrontMatter } = parseFrontMatter(source)
  if (data === null && /^\s*---/.test(source)) {
    warnings.push('开头有 --- 但没能解析成 front matter（缺少结束的 ---？），已按普通正文处理')
  }

  // ---- 标题：front matter > 第一个 H1 > 文件名 ----
  let title = ''
  let body = afterFrontMatter

  const fmTitle = data && (data.title || data.name)
  if (fmTitle) {
    title = String(fmTitle).trim()
  } else {
    const h1 = extractH1(afterFrontMatter)
    title = h1.title
    body = h1.body
  }

  if (!title) {
    title = titleFromFilename(filename)
    if (title) warnings.push('文件里没有 front matter 标题也没有一级标题，已用文件名作为标题')
  }

  if (title.length > 120) {
    title = title.slice(0, 120)
    warnings.push('标题超过 120 字，已截断（后端 title 字段上限 120）')
  }

  // ---- 摘要：front matter > 正文首段纯文本 ----
  let summary = ''
  if (data) {
    summary = String(data.summary || data.description || data.excerpt || '').trim()
  }
  if (!summary) {
    summary = extractSummary(body)
  }
  if (summary.length > 500) {
    summary = summary.slice(0, 500)
    warnings.push('摘要超过 500 字，已截断（后端 summary 字段上限 500）')
  }

  // ---- 标签：front matter.tags 字符串数组 ----
  let tagNames = []
  if (data && data.tags) {
    if (Array.isArray(data.tags)) {
      tagNames = data.tags.map((t) => String(t).trim()).filter(Boolean)
    } else if (typeof data.tags === 'string') {
      // 支持 `tags: Vue, Java` 这种没加方括号的写法
      tagNames = data.tags
        .split(/[,，]/)
        .map((t) => t.trim())
        .filter(Boolean)
    }
    // 去重（不区分大小写）
    const seen = new Set()
    tagNames = tagNames.filter((t) => {
      const k = t.toLowerCase()
      if (seen.has(k)) return false
      seen.add(k)
      return true
    })
    if (tagNames.length > 10) {
      warnings.push(`front matter 里有 ${tagNames.length} 个标签，超过上限 10，只保留前 10 个`)
      tagNames = tagNames.slice(0, 10)
    }
  }

  // ---- 置顶 / 状态 ----
  const isTop = data && truthy(data.istop ?? data.top) ? 1 : 0
  // 导入默认草稿：批量导入时不该有一堆文章直接进公开列表
  const status = 0
  if (data && truthy(data.draft)) {
    warnings.push('front matter 标记为 draft，已按草稿导入')
  }

  // ---- 正文 ----
  body = body.replace(/^\s+/, '')
  const contentMd = body
  if (!contentMd.trim()) {
    warnings.push('正文为空，只有标题')
  }
  if (contentMd.length > 200000) {
    warnings.push(`正文 ${contentMd.length} 字，超过后端上限 200000，保存会失败`)
  }

  const localImages = countLocalImages(contentMd)
  if (localImages > 0) {
    warnings.push(`正文含 ${localImages} 处相对路径图片，导入后无法显示，需要改成外链或重新上传`)
  }

  return {
    title,
    summary,
    contentMd,
    tagNames,
    isTop,
    status,
    warnings,
    stats: {
      chars: contentMd.length,
      lines: contentMd ? contentMd.split(/\r?\n/).length : 0,
      localImages
    }
  }
}

/**
 * 把 front matter 里的标签名映射到系统已有标签 ID。
 *
 * 后端 tagIds 只接受已存在的标签 ID，不会在提交文章时自动创建标签，
 * 所以匹配不上的标签只能提示用户先去「标签管理」建好。
 *
 * @param {string[]} tagNames   文档里声明的标签名
 * @param {Array<{id:number,name:string}>} existing 系统已有标签
 * @returns {{ tagIds: number[], matched: string[], missing: string[] }}
 */
export function matchTags(tagNames, existing) {
  const pool = Array.isArray(existing) ? existing : []
  const tagIds = []
  const matched = []
  const missing = []

  for (const name of tagNames || []) {
    const hit = pool.find((t) => String(t.name).toLowerCase() === String(name).toLowerCase())
    if (hit) {
      if (!tagIds.includes(hit.id)) {
        tagIds.push(hit.id)
        matched.push(hit.name)
      }
    } else {
      missing.push(name)
    }
  }
  return { tagIds, matched, missing }
}
