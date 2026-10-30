/**
 * 时间格式化工具
 *
 * 背景：后端返回 "yyyy-MM-dd HH:mm:ss"（空格分隔）格式。
 * Chrome 能解析该字符串，但 Safari / iOS WebView 按 ECMAScript 规范
 * 只保证支持 ISO 8601（"yyyy-MM-ddTHH:mm:ss"），遇到空格分隔会返回
 * Invalid Date，导致页面显示 NaN-NaN-NaN。
 *
 * 因此这里不使用 new Date(string) 直接解析，而是用正则手动拆出
 * 年月日时分秒，再构造本地时间 Date 对象，彻底规避各浏览器对
 * 非标准日期字符串的解析差异。
 *
 * 同时兼容：ISO 8601 字符串、时间戳（毫秒）、Date 实例、空值。
 */

const DATE_TIME_RE = /^(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?/

const pad = (n) => String(n).padStart(2, '0')

/**
 * 把后端返回的时间值安全地转成 Date 对象
 * @param {string|number|Date|null|undefined} value 时间值
 * @returns {Date|null} 解析失败返回 null
 */
export function toDate(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value
  }
  if (typeof value === 'number') {
    const d = new Date(value)
    return Number.isNaN(d.getTime()) ? null : d
  }

  const s = String(value).trim()
  const m = s.match(DATE_TIME_RE)
  if (m) {
    // 月份从 0 开始；缺省时分秒按 0 处理
    return new Date(
      Number(m[1]),
      Number(m[2]) - 1,
      Number(m[3]),
      Number(m[4] || 0),
      Number(m[5] || 0),
      Number(m[6] || 0)
    )
  }

  // 兜底：交给原生解析（如 "2026/10/01" 等浏览器普遍支持的格式）
  const fallback = new Date(s)
  return Number.isNaN(fallback.getTime()) ? null : fallback
}

/**
 * 格式化为 "yyyy-MM-dd"
 * @param {string|number|Date|null|undefined} value 时间值
 * @returns {string} 解析失败返回 "-"
 */
export function formatDate(value) {
  const d = toDate(value)
  if (!d) return '-'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 格式化为 "yyyy-MM-dd HH:mm"
 * @param {string|number|Date|null|undefined} value 时间值
 * @returns {string} 解析失败返回 "-"
 */
export function formatDateTime(value) {
  const d = toDate(value)
  if (!d) return '-'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
