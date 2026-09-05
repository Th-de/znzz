import api from './index'

export function listMine() {
  return api.get('/demand/mine')
}

export function getDetail(id) {
  return api.get(`/demand/${id}`)
}

export function getCoverage(id) {
  return api.get(`/demand/${id}/coverage`)
}

export function listBidFactories(id) {
  return api.get(`/demand/${id}/factories`)
}

export function getCancelStats() {
  return api.get('/demand/cancel-stats')
}

export function publish(body) {
  return api.post('/demand/publish', body)
}

export function republish(id, body) {
  return api.post(`/demand/${id}/republish`, body)
}

export function cancelPublished(id, reason) {
  return api.post(`/flow/${id}/cancel-intention`, { reason })
}

export function cancelDemand(id, reason) {
  return api.post(`/demand/${id}/cancel`, { reason })
}

export function decide(id, action, reason) {
  return api.post(`/flow/${id}/decide?action=${action}`, reason ? { reason } : {})
}

export function buyerDecide(id, action, reason) {
  return api.post(`/flow/${id}/buyer-decide?action=${action}`, reason ? { reason } : {})
}

export function closeSolution(id, reason) {
  return api.post(`/flow/${id}/close-solution`, reason ? { reason } : {})
}
