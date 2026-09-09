import { motionReady, prefersReducedMotion } from './core'
import { attachSpotlight } from './recipes'

export function staggerTable(root) {
  if (!root) return
  const rows = root.querySelectorAll('.el-table__body-wrapper tbody tr')
  if (!rows.length) return
  const gsap = motionReady()
  if (prefersReducedMotion()) {
    gsap.set(rows, { opacity: 1, y: 0 })
    return
  }
  gsap.fromTo(rows, { opacity: 0, y: 10 }, {
    opacity: 1,
    y: 0,
    duration: 0.36,
    stagger: 0.028,
    ease: 'power2.out',
    overwrite: true,
  })
}

export function bindTableSurface(root) {
  if (!root) return () => {}
  root.classList.add('motion-table')
  const offSpot = attachSpotlight(root)
  staggerTable(root)
  return () => {
    offSpot()
    root.classList.remove('motion-table')
  }
}
