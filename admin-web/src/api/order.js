import api from './index'

export function pendingContracts() {
  return api.get('/order/contracts/pending')
}

export function approveContract(orderId, factoryTenantId) {
  return api.post(`/order/${orderId}/contract/approve`, null, { params: { factoryTenantId } })
}

export function getContract(orderId) {
  return api.get(`/order/${orderId}/contract`)
}

export function inspectQueue() {
  return api.get('/order/inspect/queue')
}

export function listOrders() {
  return api.get('/order/all')
}

export function listContracts(orderId) {
  return api.get(`/order/${orderId}/contracts`)
}

export function stages(orderId) {
  return api.get(`/order/${orderId}/stages`)
}

export function openNextPeriod(orderId, factoryId) {
  return api.post(`/order/${orderId}/factory/${factoryId}/open-next-period`)
}

export function progressLog(stageId) {
  return api.get(`/order/stage/${stageId}/progress-log`)
}

export function inspect(stageId, body) {
  return api.post(`/order/stage/${stageId}/inspect`, body)
}

export function approveInspect(stageId, body) {
  return api.post(`/order/stage/${stageId}/inspect-approve`, body)
}

export function inspectionOf(stageId) {
  return api.get(`/order/stage/${stageId}/inspection`)
}
