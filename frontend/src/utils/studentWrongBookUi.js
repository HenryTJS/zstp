/** 错题本卡片预览：弱化公式占位，避免格子内过长 */
export const wrongBookQuestionPreview = (raw) => {
  let s = String(raw || '')
    .replace(/\$\$[\s\S]*?\$\$/g, '〔公式〕')
    .replace(/\$[^$\n]+?\$/g, '〔式〕')
    .replace(/\s+/g, ' ')
    .trim()
  if (s.length > 72) s = `${s.slice(0, 72)}…`
  return s || '（无题干）'
}
