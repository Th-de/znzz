import { motionReady, prefersReducedMotion } from './core'

export function drawStroke(el, duration = 1.1) {
  if (!el) return
  const gsap = motionReady()
  const len = el.getTotalLength ? el.getTotalLength() : 800
  gsap.set(el, { strokeDasharray: len, strokeDashoffset: len })
  if (prefersReducedMotion()) {
    gsap.set(el, { strokeDashoffset: 0 })
    return
  }
  gsap.to(el, { strokeDashoffset: 0, duration, ease: 'power2.inOut', overwrite: true })
}

export function popDots(nodes, delay = 0.35) {
  if (!nodes || !nodes.length) return
  const gsap = motionReady()
  if (prefersReducedMotion()) {
    gsap.set(nodes, { opacity: 1, scale: 1 })
    return
  }
  gsap.fromTo(nodes, { opacity: 0, scale: 0.2 }, {
    opacity: 1,
    scale: 1,
    duration: 0.35,
    stagger: 0.018,
    delay,
    ease: 'back.out(2.2)',
    transformOrigin: 'center',
    overwrite: true,
  })
}

export function fillArea(el, delay = 0.2) {
  if (!el) return
  const gsap = motionReady()
  if (prefersReducedMotion()) {
    gsap.set(el, { opacity: 1 })
    return
  }
  gsap.fromTo(el, { opacity: 0 }, { opacity: 1, duration: 0.7, delay, ease: 'power2.out', overwrite: true })
}
