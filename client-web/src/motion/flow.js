import { motionReady, prefersReducedMotion } from './core'

export function animateFlowSteps(root) {
  if (!root) return
  const gsap = motionReady()
  const items = root.querySelectorAll('.el-step')
  if (!items.length) return
  if (prefersReducedMotion()) {
    gsap.set(items, { opacity: 1, y: 0 })
    return
  }
  gsap.fromTo(items, { opacity: 0, y: 12 }, {
    opacity: 1,
    y: 0,
    duration: 0.42,
    stagger: 0.07,
    ease: 'power2.out',
    overwrite: true,
  })
}

export function watchFlowIn(root) {
  if (!root) return () => {}
  const run = () => animateFlowSteps(root.querySelector('.flow-steps'))
  run()
  const mo = new MutationObserver(() => {
    if (root.querySelector('.flow-steps')) run()
  })
  mo.observe(root, { childList: true, subtree: true })
  return () => mo.disconnect()
}
