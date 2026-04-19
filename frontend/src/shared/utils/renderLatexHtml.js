import katex from 'katex'

export const escapeHtml = (text) => {
  return String(text || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

export const renderLatexText = (text) => {
  // 去掉末尾空白与换行，避免转成大量 <br/> 在题面/解析下方留出大块空白
  const source = String(text || '').trimEnd()
  if (!source.trim()) {
    return ''
  }

  const parts = source.split(/(\$\$[\s\S]+?\$\$|\$[\s\S]+?\$|\\\([\s\S]+?\\\)|\\\[[\s\S]+?\\\])/g)

  const normalizeLatex = (s) => String(s || '').replace(/-\>/g, '\\\\to')

  const renderNonLatex = (s) => {
    return escapeHtml(s).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replaceAll('\n', '<br/>')
  }

  return parts
    .map((part) => {
      if (!part) {
        return ''
      }

      if (part.startsWith('$$') && part.endsWith('$$')) {
        const latex = normalizeLatex(part.slice(2, -2))
        return katex.renderToString(latex, { displayMode: true, throwOnError: false })
      }

      if (part.startsWith('$') && part.endsWith('$')) {
        const latex = normalizeLatex(part.slice(1, -1))
        return katex.renderToString(latex, { displayMode: false, throwOnError: false })
      }

      if (part.startsWith('\\(') && part.endsWith('\\)')) {
        const latex = normalizeLatex(part.slice(2, -2))
        return katex.renderToString(latex, { displayMode: false, throwOnError: false })
      }

      if (part.startsWith('\\[') && part.endsWith('\\]')) {
        const latex = normalizeLatex(part.slice(2, -2))
        return katex.renderToString(latex, { displayMode: true, throwOnError: false })
      }

      return renderNonLatex(part)
    })
    .join('')
}
