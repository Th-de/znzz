import { motionReady, prefersReducedMotion } from './core'

export function riseIn(targets, opts = {}) {
  const gsap = motionReady()
  const nodes = typeof targets === 'string' ? document.querySelectorAll(targets) : targets
  if (!nodes || (nodes.length !== undefined && !nodes.length)) return
  if (prefersReducedMotion()) {
    gsap.set(nodes, { opacity: 1, y: 0 })
    return
  }
  gsap.fromTo(nodes, { opacity: 0, y: opts.y ?? 22 }, {
    opacity: 1,
    y: 0,
    duration: opts.duration ?? 0.7,
    stagger: opts.stagger ?? 0.08,
    ease: 'power2.out',
    overwrite: true,
  })
}

export function attachSpotlight(el) {
  if (!el) return () => {}
  const onMove = (e) => {
    const r = el.getBoundingClientRect()
    el.style.setProperty('--spot-x', `${e.clientX - r.left}px`)
    el.style.setProperty('--spot-y', `${e.clientY - r.top}px`)
  }
  el.addEventListener('pointermove', onMove)
  return () => el.removeEventListener('pointermove', onMove)
}
