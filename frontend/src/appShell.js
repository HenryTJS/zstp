/**
 * App 根级 provide/inject：不依赖 router-view 向子组件透传事件。
 * 使用字符串 key，避免分包/热更新时 Symbol 多实例导致 inject 取不到 provide。
 */
export const appShellKey = 'zstp-app-shell'
