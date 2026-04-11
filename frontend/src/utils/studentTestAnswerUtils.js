/** 测验与错题本共用的答案规范化（纯函数，无 Vue 依赖） */

export const unescapeNewlinesSafe = (t) => {
  if (t === null || t === undefined) return t
  return String(t)
}

export const parseOptionLetter = (opt) => {
  const s = String(opt || '').trim()
  const m = s.match(/^[A-D]\b/i)
  if (m) return m[0].toUpperCase()
  const m2 = s.match(/^[A-D][\.\、\)]/)
  if (m2) return s.charAt(0).toUpperCase()
  return ''
}

export const parseOptionText = (opt) => {
  const s = String(opt || '').trim()
  return s.replace(/^[A-D][\.\、\)]\s*/i, '')
}

export const resolveAnswerText = (q, answer) => {
  const qt = q?.question_type
  if (!qt) return String(answer || '').trim()
  if (qt === '选择题' || qt === '判断题') {
    const letter = Array.isArray(answer) ? String(answer[0] || '').trim() : String(answer || '').trim()
    const L = letter ? letter.toUpperCase().charAt(0) : ''
    const opt = (q.options || []).find((o) => parseOptionLetter(o) === L)
    return opt ? parseOptionText(opt) : L
  }
  if (qt === '多选题') {
    const list = Array.isArray(answer) ? answer : String(answer || '').split('')
    const letters = list.map((x) => String(x).toUpperCase().trim()).filter((x) => ['A', 'B', 'C', 'D'].includes(x))
    const ordered = ['A', 'B', 'C', 'D'].filter((l) => letters.includes(l))
    const texts = (q.options || []).filter((o) => ordered.includes(parseOptionLetter(o))).map(parseOptionText)
    return texts.join('、') || ''
  }
  return String(answer || '').trim()
}

export const gradeStudentAnswer = (q, a) => {
  const qt = q?.question_type
  if (!qt) return ''
  if (qt === '多选题') {
    if (!Array.isArray(a)) return ''
    return a
      .map((x) => String(x).toUpperCase())
      .filter((x) => ['A', 'B', 'C', 'D'].includes(x))
      .sort()
      .join('')
  }
  return String(a || '').trim()
}

/** 错题本客观题：从作答还原为规范选项字母串（多选支持字符串参考答案） */
export const wrongBookChoiceLetters = (q, rawAns) => {
  const qt = q?.question_type
  if (!qt) return ''
  if (qt === '多选题') {
    if (Array.isArray(rawAns)) {
      return rawAns
        .map((x) => String(x).toUpperCase().trim())
        .filter((x) => ['A', 'B', 'C', 'D'].includes(x))
        .sort()
        .join('')
    }
    const picked = []
    for (const ch of String(rawAns || '').toUpperCase()) {
      if (['A', 'B', 'C', 'D'].includes(ch)) picked.push(ch)
    }
    return [...new Set(picked)].sort().join('')
  }
  if (qt === '选择题' || qt === '判断题') {
    const letter = Array.isArray(rawAns) ? String(rawAns[0] || '').trim() : String(rawAns || '').trim()
    let L = letter ? letter.toUpperCase().charAt(0) : ''
    if (!['A', 'B', 'C', 'D'].includes(L)) {
      const opt = (q.options || []).find(
        (o) => parseOptionText(o) === letter || String(o).trim() === letter
      )
      L = opt ? parseOptionLetter(opt) : L
    }
    return L && ['A', 'B', 'C', 'D'].includes(L) ? L : ''
  }
  return ''
}
