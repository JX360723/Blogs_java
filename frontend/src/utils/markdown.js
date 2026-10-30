// 轻量 Markdown 渲染器（MVP 自实现，不引入 marked 等重依赖）
// 支持：标题(#/##/###)、列表(-/*/数字)、引用(>)、代码块(```)、
//      行内 `代码` **加粗** *斜体* [链接](url)
// 安全：先转义 HTML，再处理 Markdown 语法，避免 XSS

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function inline(text) {
  let s = escapeHtml(text)
  // 行内代码（先处理，避免内部被其他规则破坏）
  s = s.replace(/`([^`]+)`/g, '<code>$1</code>')
  // 链接 [text](url)
  s = s.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
  // 加粗 **text**
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  // 斜体 *text*（避免误伤加粗结果）
  s = s.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
  return s
}

export function renderMarkdown(md) {
  if (!md) return ''
  const lines = String(md).split('\n')
  const html = []
  let inCode = false
  let codeBuf = []
  let inList = false
  let listType = 'ul'

  const closeList = () => {
    if (inList) {
      html.push(`</${listType}>`)
      inList = false
    }
  }

  for (const line of lines) {
    // 代码块
    if (/^```/.test(line.trim())) {
      if (inCode) {
        html.push('<pre><code>' + escapeHtml(codeBuf.join('\n')) + '</code></pre>')
        codeBuf = []
        inCode = false
      } else {
        closeList()
        inCode = true
      }
      continue
    }
    if (inCode) {
      codeBuf.push(line)
      continue
    }

    const trimmed = line.trim()
    if (!trimmed) {
      closeList()
      html.push('')
      continue
    }

    // 标题
    const h = trimmed.match(/^(#{1,3})\s+(.*)$/)
    if (h) {
      closeList()
      const level = h[1].length
      html.push(`<h${level}>${inline(h[2])}</h${level}>`)
      continue
    }

    // 引用
    if (/^>/.test(trimmed)) {
      closeList()
      html.push(`<blockquote>${inline(trimmed.replace(/^>\s?/, ''))}</blockquote>`)
      continue
    }

    // 列表
    const li = trimmed.match(/^([-*]|\d+\.)\s+(.*)$/)
    if (li) {
      const type = /^\d+\./.test(li[1]) ? 'ol' : 'ul'
      if (!inList || listType !== type) {
        closeList()
        inList = true
        listType = type
        html.push(`<${type}>`)
      }
      html.push(`<li>${inline(li[2])}</li>`)
      continue
    }
    closeList()

    // 段落
    html.push(`<p>${inline(trimmed)}</p>`)
  }

  if (inCode) {
    html.push('<pre><code>' + escapeHtml(codeBuf.join('\n')) + '</code></pre>')
  }
  closeList()

  return html.join('\n')
}

export default renderMarkdown
