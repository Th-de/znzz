import api from './index'

export function listAll() {
  return api.get('/demand/all')
}

export function getDetail(id) {
  return api.get(`/demand/${id}`)
}

export function getCoverage(id) {
  return api.get(`/demand/${id}/coverage`)
}

export function returnToBuyer(id, reason) {
  return api.post(`/demand/${id}/return`, { reason })
}

export function audit(id, result, reason) {
  return api.post(`/demand/${id}/audit`, { result, reason })
}
