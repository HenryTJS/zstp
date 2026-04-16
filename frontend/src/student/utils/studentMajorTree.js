/** 在专业树中查找目标 code 的路径（返回 code 数组，按层级） */
export const findMajorPath = (majors, targetCode) => {
  if (!Array.isArray(majors)) return []
  for (const m of majors) {
    if (m.code === targetCode) {
      return [m.code]
    }
    if (Array.isArray(m.subfields)) {
      const sub = findMajorPath(m.subfields, targetCode)
      if (sub.length) {
        return [m.code, ...sub]
      }
    }
  }
  return []
}
