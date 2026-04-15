import fs from 'node:fs'
import path from 'node:path'

const vuePath = path.resolve('E:/zstp/frontend/src/modules/teacher/TeacherPortal.vue')
const s = fs.readFileSync(vuePath, 'utf8')
const lines = s.split(/\r?\n/)

// 1-based file line numbers
function getSlice(startLine, endLineInclusive) {
  return lines.slice(startLine - 1, endLineInclusive).join('\n')
}

const knowledgeHead = [
  getSlice(84, 274),
  lines[277], // publishedTestCount line278
  getSlice(631, 1331)
].join('\n')

const courseBlock = getSlice(282, 623)
const watchRouteCourse = getSlice(1518, 1529)

fs.mkdirSync('E:/zstp/frontend/src/modules/teacher/composables/_gen', { recursive: true })
fs.writeFileSync('E:/zstp/frontend/src/modules/teacher/composables/_gen/knowledge-body.txt', knowledgeHead)
fs.writeFileSync('E:/zstp/frontend/src/modules/teacher/composables/_gen/course-body.txt', [courseBlock, watchRouteCourse].join('\n\n'))
console.log('wrote _gen snippets')
