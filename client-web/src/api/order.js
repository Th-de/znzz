import api from './index'
import { downloadAttachment } from './file'

export function listOrders() {
  return api.get('/order/all')
}

export function getOrder(id) {
  return api.get(`/order/${id}`)
}

export function listContracts(orderId) {
  return api.get(`/order/${orderId}/contracts`)
}

export function getContract(orderId) {
  return api.get(`/order/${orderId}/contract`)
}

export function uploadContract(orderId, attachmentId, factoryTenantId) {
  return api.post(`/order/${orderId}/contract/upload`, { attachmentId, factoryTenantId })
}

export function buyerSign(orderId, read, sign) {
  return api.post(`/order/${orderId}/contract/buyer-sign`, { read, sign })
}

export function factorySign(orderId, read, sign) {
  return api.post(`/order/${orderId}/contract/factory-sign`, { read, sign })
}

export function stages(orderId) {
  return api.get(`/order/${orderId}/stages`)
}

export function myDemandJobs() {
  return api.get('/order/my-jobs')
}

export function myStages() {
  return api.get('/order/my-stages')
}

export function startStage(stageId) {
  return api.post(`/order/stage/${stageId}/start`)
}

export function reportProgress(stageId, body) {
  return api.post(`/order/stage/${stageId}/progress`, body)
}

export function progressLog(stageId) {
  return api.get(`/order/stage/${stageId}/progress-log`)
}

export function inspectionOf(stageId) {
  return api.get(`/order/stage/${stageId}/inspection`)
}

export function deliver(stageId, body) {
  return api.post(`/order/stage/${stageId}/deliver`, body || {})
}

export function payInspectFee(stageId) {
  return api.post(`/order/stage/${stageId}/inspect-fee`)
}

export function payStage(stageId) {
  return api.post(`/order/stage/${stageId}/pay`)
}

export function decideInspect(stageId, body) {
  return api.post(`/order/stage/${stageId}/decision`, body)
}

export function accept(orderId) {
  return api.post(`/order/${orderId}/accept`)
}

export function submitSurvey(stageId, scores) {
  return api.post(`/order/stage/${stageId}/survey`, { scores })
}

export function uploadFile(file, bizType) {
  const fd = new FormData()
  fd.append('file', file)
  if (bizType) fd.append('bizType', bizType)
  return api.post('/file/upload', fd)
}

export { downloadAttachment }
