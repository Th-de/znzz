import axios from 'axios'

export async function fetchAttachment(id, preview = false) {
  const token = sessionStorage.getItem('token')
  const res = await axios.get(preview ? `/api/file/${id}/preview` : `/api/file/${id}`, {
    responseType: 'blob',
    headers: { Authorization: 'Bearer ' + token },
  })
  const type = res.headers['content-type'] || ''
  if (type.includes('application/json')) {
    const text = await res.data.text()
    let message = '无法打开文件'
    try { message = JSON.parse(text).message || message } catch { /* ignore */ }
    throw new Error(message)
  }
  let fileName = '附件'
  const cd = res.headers['content-disposition'] || ''
  const star = cd.match(/filename\*=UTF-8''([^;]+)/i)
  const plain = cd.match(/filename="?([^"]+)"?/i)
  if (star) fileName = decodeURIComponent(star[1])
  else if (plain) fileName = decodeURIComponent(plain[1])
  return { blob: res.data, fileName }
}

export function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName || '附件'
  a.click()
  URL.revokeObjectURL(url)
}

export async function downloadAttachment(id) {
  const { blob, fileName } = await fetchAttachment(id)
  saveBlob(blob, fileName)
}
