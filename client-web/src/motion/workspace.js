import { nextTick } from 'vue'
import { attachSpotlight } from './recipes'
import { staggerTable } from './table'
import { animateFlowSteps } from './flow'

const cleanups = new WeakMap()

export async function refreshWorkspace(root) {
  if (!root) return
  await nextTick()
  animateFlowSteps(root.querySelector('.flow-steps'))
  root.querySelectorAll('.el-table').forEach((table) => {
    if (table.closest('.paged-box')) return
    if (!cleanups.has(table)) {
      table.classList.add('motion-table')
      cleanups.set(table, attachSpotlight(table))
    }
    staggerTable(table)
  })
}
