import api from './index'

export function intention(body) {
  return api.post('/bidding/intention', body)
}

export function payIntention(id) {
  return api.post(`/bidding/${id}/pay-intention`)
}

export function lock(body) {
  return api.post('/bidding/lock', body)
}

export function listMine() {
  return api.get('/bidding/mine')
}

export function commit(body) {
  return api.post('/bidding/commit', body)
}

export function exitDemand(demandId) {
  return api.post(`/bidding/exit/${demandId}`)
}
