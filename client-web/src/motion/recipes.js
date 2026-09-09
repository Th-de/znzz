import { motionReady, prefersReducedMotion } from './core'

export function attachMagnet(el, strength = 12) {
  if (!el || prefersReducedMotion()) return () => {}
  const gsap = motionReady()
  const onMove = (e) => {
    const r = el.getBoundingClientRect()
    const x = ((e.clientX - r.left) / r.width - 0.5) * strength
    const y = ((e.clientY - r.top) / r.height - 0.5) * strength
    gsap.to(el, { x, y, duration: 0.4, ease: 'power3.out', overwrite: 'auto' })
  }
  const onLeave = () => {
    gsap.to(el, { x: 0, y: 0, duration: 0.55, ease: 'power3.out', overwrite: 'auto' })
  }
  el.addEventListener('pointermove', onMove)
  el.addEventListener('pointerleave', onLeave)
  return () => {
    el.removeEventListener('pointermove', onMove)
    el.removeEventListener('pointerleave', onLeave)
    gsap.set(el, { clearProps: 'transform' })
  }
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

export function riseIn(targets, opts = {}) {
  const gsap = motionReady()
  const nodes = typeof targets === 'string' ? document.querySelectorAll(targets) : targets
  if (!nodes || (nodes.length !== undefined && !nodes.length)) return
  if (prefersReducedMotion()) {
    gsap.set(nodes, { opacity: 1, y: 0, clearProps: 'transform' })
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
