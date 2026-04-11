import { parseOptionText, unescapeNewlinesSafe } from './studentTestAnswerUtils'

export const inferWrongBookQuestionType = (item) => {
  const t = String(item?.questionType || item?.question_type || '').trim()
  if (t) {
    if (t === '简答题' || t === '问答题') return '解答题'
    return t
  }
  const opts = item?.options
  if (Array.isArray(opts) && opts.length >= 2) {
    const texts = opts.map((o) => String(parseOptionText(o) || '').trim())
    const judgeRe = /^(正确|错误|对|错|√|×|是|否|真|假)$/i
    if (opts.length === 2 && texts.every((x) => judgeRe.test(x) || x.length <= 4)) return '判断题'
    return '选择题'
  }
  const qtext = String(item?.question || '')
  if (/(___+|（\s*）|\(\s*\)|【\s*】|填空)/.test(qtext)) return '填空题'
  return '解答题'
}

export const referenceAnswerForWrongDrill = (item) => {
  const raw = item?.referenceAnswerRaw
  if (raw !== undefined && raw !== null && String(raw).trim() !== '') {
    return unescapeNewlinesSafe(raw)
  }
  const qt = inferWrongBookQuestionType(item)
  const letters = String(item?.correctOptionLetters || '').trim()
  if (['选择题', '判断题', '多选题'].includes(qt) && letters) return letters
  return String(item?.answer || '').trim()
}

export const wrongBookItemToGradeQuestion = (item) => {
  const qt = inferWrongBookQuestionType(item)
  return {
    question_type: qt,
    question: unescapeNewlinesSafe(item.question),
    options: Array.isArray(item.options) ? item.options.map(unescapeNewlinesSafe) : [],
    answer: referenceAnswerForWrongDrill(item)
  }
}

export const pickWrongDrillItemsFromBook = (course, wrongBookArray) => {
  const c = String(course || '').trim()
  if (!c) return []
  const pool = (wrongBookArray || []).filter((w) => {
    if (String(w.course || '').trim() !== c) return false
    if (Number(w.wrongTestConsecutiveCorrect || 0) >= 2) return false
    return true
  })
  const byId = new Map(pool.map((w) => [w.id, w]))
  const ids = [...byId.keys()]
  for (let i = ids.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[ids[i], ids[j]] = [ids[j], ids[i]]
  }
  return ids.slice(0, 5).map((id) => byId.get(id)).filter(Boolean)
}
