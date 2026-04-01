import { http } from './client'

export const deleteKnowledgePoint = (id) => http.delete(`/knowledge-points/${id}`)
export const updateKnowledgePoint = (id, payload) => http.put(`/knowledge-points/${id}`, payload)
export const deleteMaterial = (id) => http.delete(`/materials/${id}`)
