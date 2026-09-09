import Lenis from 'lenis'
import 'lenis/dist/lenis.css'
import { motionReady, prefersReducedMotion } from './core'

export function startLandingScroll(onScroll) {
  if (prefersReducedMotion()) return { lenis: null, stop() {} }
  const gsap = motionReady()
  const lenis = new Lenis({
    autoRaf: false,
    lerp: 0.09,
    smoothWheel: true,
    syncTouch: false,
  })
  const tick = (time) => { lenis.raf(time * 1000) }
  gsap.ticker.add(tick)
  gsap.ticker.lagSmoothing(0)
  if (onScroll) lenis.on('scroll', onScroll)
  return {
    lenis,
    stop() {
      gsap.ticker.remove(tick)
      lenis.destroy()
    },
  }
}
