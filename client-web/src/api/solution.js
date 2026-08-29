import api from './index'

export function listByDemand(demandId) {
  return api.get(`/solution/${demandId}`)
}

export function alternatives(solutionId, processNo) {
  return api.get(`/solution/item/${solutionId}/alternatives`, { params: { processNo } })
}

export function replaceFactory(solutionId, processNo, factoryId) {
  return api.post(`/solution/item/${solutionId}/replace-factory`, { processNo, factoryId })
}

export function selectSolution(demandId, solutionId) {
  return api.post(`/order/${demandId}/select/${solutionId}`)
}

export function generateAi(demandId) {
  return api.post(`/solution/${demandId}/generate-ai`, null, { timeout: 90000 })
}

export function allocationCandidates(solutionId, processNo) {
  return api.get(`/solution/item/${solutionId}/candidates`, { params: { processNo } })
}

export function reallocate(solutionId, processNo, allocations) {
  return api.post(`/solution/item/${solutionId}/reallocate?processNo=${processNo}`, allocations)
}
