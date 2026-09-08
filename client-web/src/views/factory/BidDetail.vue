<template>
  <div v-loading="loading">
    <el-steps :active="stepActive" :process-status="lost ? 'error' : 'process'" finish-status="success" align-center class="flow-steps">
      <el-step title="发布需求" />
      <el-step title="意向期" />
      <el-step title="方案确定" :description="lost ? '已落选' : ''" />
      <el-step title="合同签署" />
      <el-step title="生产与质检" />
      <el-step title="订单结算" />
    </el-steps>

    <el-tabs type="border-card" stretch class="spec-tabs">
      <el-tab-pane label="基础">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题" :span="2">{{ demand.title }}</el-descriptions-item>
          <el-descriptions-item label="产品">{{ demand.productName }}</el-descriptions-item>
          <el-descriptions-item label="产品类别">{{ demand.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="图号/版本">{{ demand.partRevision || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ demand.quantity }}</el-descriptions-item>
          <el-descriptions-item label="阶段">{{ lost ? '已落选' : label(DEMAND_STATUS, demand.status) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ fmtTime(demand.createdAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane label="技术规格">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="材料">{{ demand.material }}</el-descriptions-item>
          <el-descriptions-item label="一般公差">{{ demand.generalTolerance || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关键公差">{{ demand.tolerance || '-' }}</el-descriptions-item>
          <el-descriptions-item label="粗糙度 Ra">{{ extra.roughness || '-' }}</el-descriptions-item>
          <el-descriptions-item label="表面处理">{{ demand.surfaceTreatment || '-' }}</el-descriptions-item>
          <el-descriptions-item label="热处理">{{ extra.heatTreatment || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane label="质量门槛">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="检验方式">{{ formatInspectMode(demand.inspectMode) }}</el-descriptions-item>
          <el-descriptions-item label="AQL">{{ demand.aql || '-' }}</el-descriptions-item>
          <el-descriptions-item label="认证">{{ demand.certification || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="demand.minYield != null" label="最低良率">{{ demand.minYield }}</el-descriptions-item>
          <el-descriptions-item label="最低信用分">{{ demand.minCreditScore }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane label="交期与交付">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="硬交期">{{ demand.deadlineHard }}</el-descriptions-item>
          <el-descriptions-item label="分期交付">{{ demand.deliveryTimes || 1 }} 期</el-descriptions-item>
          <el-descriptions-item label="交付地址" :span="2">{{ demand.deliveryAddress }}</el-descriptions-item>
          <el-descriptions-item label="包装">{{ demand.packaging || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="deliveryRows.length" :data="deliveryRows" border size="small" style="margin-top:12px">
          <el-table-column label="期次" width="90">
            <template #default="{ $index }">第{{ $index + 1 }}期</template>
          </el-table-column>
          <el-table-column prop="percent" label="交付比例" width="120" />
          <el-table-column prop="startAt" label="开始日期" width="140" />
          <el-table-column prop="endAt" label="截止日期" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="工序意向图纸">
        <el-table :data="processes" size="small" border>
          <el-table-column prop="processNo" label="#" width="50" />
          <el-table-column prop="processName" label="工序" />
          <el-table-column prop="requirement" label="要求" />
        </el-table>
        <h4>图纸/附件</h4>
        <div v-if="attachments.length">
          <div v-for="a in attachments" :key="a.id">
            <el-button link type="primary" @click="downloadAttachment(a.id)">{{ a.fileName }}</el-button>
          </div>
        </div>
        <p v-else class="hint">无附件</p>
      </el-tab-pane>
    </el-tabs>

    <el-card shadow="never" class="phase">
      <template #header>买家信息</template>
      <BuyerInfoBlock :buyer="buyer" />
    </el-card>

    <el-card shadow="never" class="phase">
      <template #header>发布需求</template>
      <el-alert v-if="demand.status === 'CANCELLED'" type="info" :closable="false" :title="'需求已取消' + (demand.cancelReason ? '：' + demand.cancelReason : '')" />
      <el-alert v-else-if="demand.status === 'FLOW_FAILED'" type="warning" :closable="false" title="需求流拍，本报名已结束。" />
      <el-alert v-else type="success" :closable="false" title="需求已发布。下方按阶段展示本厂报名与待办。" />
    </el-card>

    <el-card v-if="reached(1)" shadow="never" class="phase">
      <template #header>意向期</template>
      <IntentionCountdown v-if="demand.status==='PUBLISHED'" :end-at="demand.intentionEndAt" />
      <IntentionCountdown v-else-if="demand.status==='FACTORY_THINKING'" :end-at="demand.factoryThinkingEndAt" />
      <IntentionCountdown v-else-if="demand.status==='BUYER_THINKING'" :end-at="demand.buyerThinkingEndAt" />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="承接区间">{{ qtyRangeLabel }}</el-descriptions-item>
        <el-descriptions-item label="意向金">{{ intentionLabel }}</el-descriptions-item>
        <el-descriptions-item label="保证金">{{ depositLabel }}</el-descriptions-item>
        <el-descriptions-item label="报名时间">{{ fmtTime(row.createdAt) }}</el-descriptions-item>
      </el-descriptions>
      <div class="phase-actions">
        <el-button v-if="actionKey==='PAY'" type="primary" @click="pay">继续支付意向金</el-button>
        <template v-else-if="actionKey==='WAIT_INTENTION'">
          <span class="op-text">已报名，等待意向期结束</span>
          <el-button type="danger" @click="cancelBid">取消报名</el-button>
        </template>
      </div>
    </el-card>

    <el-card v-if="reached(1) && showThinkingCard" shadow="never" class="phase">
      <template #header>思考期报价</template>
      <p class="hint">只展示本厂报名与报价，不含其他工厂、不含整单方案。</p>
      <el-descriptions v-if="hasQuoted" :column="2" border>
        <el-descriptions-item label="单价">{{ unitPriceLabel || '-' }} 元/件</el-descriptions-item>
        <el-descriptions-item label="总报价">{{ totalPriceLabel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实施方案" :span="2">{{ quote.planText || '-' }}</el-descriptions-item>
      </el-descriptions>
      <p v-else class="hint">尚未提交报价。</p>
      <div class="phase-actions">
        <el-button v-if="actionKey==='COMMIT'" type="warning" @click="openCommit">填报报价</el-button>
        <el-button v-if="actionKey==='COMMIT'" type="danger" @click="cancelBid">取消报名</el-button>
        <span v-else-if="actionKey==='WAIT_BUYER'" class="op-text">已报价，等待买家决定</span>
        <span v-else-if="actionKey==='WAIT_SOLUTION'" class="op-text">等待买家确认方案</span>
        <span v-else-if="actionKey==='WAIT_DISPATCH'" class="op-text">等待进入合同签署</span>
      </div>
    </el-card>

    <el-card v-if="reached(2)" shadow="never" class="phase">
      <template #header>方案确定</template>
      <el-alert v-if="lost" type="error" :closable="false" title="已落选" description="方案已确定，本厂未入选。保证金将按规则退还，无需签署合同。" />
      <template v-else-if="won">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="分配件数">{{ wonQty }}</el-descriptions-item>
          <el-descriptions-item label="本厂金额">{{ wonAmount }}</el-descriptions-item>
          <el-descriptions-item label="单价">{{ unitPriceLabel || '-' }} 元/件</el-descriptions-item>
        </el-descriptions>
      </template>
      <p v-else class="hint">买家确认方案后，此处仅显示分配给本厂的件数与金额。</p>
    </el-card>

    <el-card v-if="reached(3) && !lost" shadow="never" class="phase">
      <template #header>合同签署</template>
      <p v-if="!row.orderId" class="hint">尚未生成与本厂的订单。</p>
      <el-descriptions v-else :column="1" border>
        <el-descriptions-item label="订单">#{{ row.orderId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ alreadySigned ? '已签约' : (contract.status || '待签署') }}</el-descriptions-item>
        <el-descriptions-item label="合同文件">
          <el-button v-if="contract.attachmentId" size="small" link type="primary" @click="openContractFile(contract.attachmentId)">{{ contract.fileName || ('附件 #' + contract.attachmentId) }}</el-button>
          <span v-else>买家尚未下发与你的合同</span>
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="row.orderId" class="phase-actions">
        <el-button v-if="actionKey==='SIGN'" type="primary" @click="openSign">签署合同</el-button>
        <el-button v-if="actionKey==='SIGN'" type="danger" @click="cancelOrder">取消订单</el-button>
        <span v-else-if="actionKey==='WAIT_ISSUE'" class="op-text">待下发合同</span>
        <span v-else-if="actionKey==='WAIT_BUYER_CONFIRM' || actionKey==='PENDING_REVIEW'" class="op-text">已提交买家确认</span>
        <span v-else-if="actionKey==='SIGNED'" class="op-text">已签约</span>
      </div>
    </el-card>

    <el-card v-if="showStages && !lost" shadow="never" class="phase">
      <template #header>生产与质检 {{ job.progress || 0 }}%</template>
      <el-progress :percentage="job.progress || 0" style="margin-bottom:10px" />
      <el-alert type="info" :closable="false" title="到开始时间后状态变为进行中，才能上报进度和交付。未到开始时间为待开启。" style="margin-bottom:12px" />
      <el-descriptions :column="2" border style="margin-bottom:12px">
        <el-descriptions-item label="总计工费">{{ job.totalAmount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ jobStatusText(job) }}</el-descriptions-item>
        <el-descriptions-item label="开始">
          <div class="dt-2">
            <div>{{ fmtDateLine(factoryWorkSpan(job.periods).start) }}</div>
            <div v-if="fmtTimeLine(factoryWorkSpan(job.periods).start)" class="dt-clock">{{ fmtTimeLine(factoryWorkSpan(job.periods).start) }}</div>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="截止">
          <div class="dt-2">
            <div>{{ fmtDateLine(factoryWorkSpan(job.periods).end) }}</div>
            <div v-if="fmtTimeLine(factoryWorkSpan(job.periods).end)" class="dt-clock">{{ fmtTimeLine(factoryWorkSpan(job.periods).end) }}</div>
          </div>
        </el-descriptions-item>
      </el-descriptions>
            <el-table :data="nestReworkPeriods(job.periods || [])" border size="small">
        <el-table-column label="期" width="90">
          <template #default="{ row: p }">{{ p.periodLabel || ('第' + (p.periodNo || '-') + '期') }}</template>
        </el-table-column>
        <el-table-column label="约定数量" width="90">
          <template #default="{ row: p }">{{ p.quantity ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="实交数量" width="90">
          <template #default="{ row: p }">{{ p.deliveredQty ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="开始" width="108">
          <template #default="{ row: p }">
            <div class="dt-2">
              <div>{{ fmtDateLine(p.periodStart) }}</div>
              <div v-if="fmtTimeLine(p.periodStart)" class="dt-clock">{{ fmtTimeLine(p.periodStart) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="截止" width="108">
          <template #default="{ row: p }">
            <div class="dt-2">
              <div>{{ fmtDateLine(p.periodEnd || p.promisedDate) }}</div>
              <div v-if="fmtTimeLine(p.periodEnd || p.promisedDate)" class="dt-clock">{{ fmtTimeLine(p.periodEnd || p.promisedDate) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="本期工费" width="100">
          <template #default="{ row: p }">{{ p.amount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row: p }">
            <span v-if="isReworking(p)" class="rework-status" @click="openReworkReason(p)">返工生产中</span>
            <span v-else>{{ stageProgressLabel(p) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="托管" width="100">
          <template #default="{ row: p }">{{ label(ESCROW_STATUS, p.escrowStatus) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="280">
          <template #default="{ row: p }">
            <el-button v-if="canProduce(p)" size="small" type="warning" @click="openProg(p)">上报进度</el-button>
            <el-button v-if="canProduce(p)" size="small" type="primary" @click="openDeliver(p)">交付</el-button>
            <el-button v-if="canPayInspectFee(p, true)" size="small" type="warning" @click="payFee(p)">提交质检费 ¥{{ p.inspectFeeAmount ?? 0 }}</el-button>
            <el-button v-if="canShowInspectReport(p)" size="small" @click="openInsp(p)">质检报告</el-button>
            <el-button v-if="p.status==='PASS' && !p.surveyed" size="small" @click="openSurvey(p)">评价</el-button>
            <span v-if="p.status==='WAITING_OPEN' && p.contractSigned" class="hint">未到开始时间</span>
            <span v-if="!p.contractSigned" class="hint">请先在「合同签署」完成签约</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="!lost && (reached(5) || settledHint)" shadow="never" class="phase">
      <template #header>订单结算</template>
      <p v-if="demand.status==='COMPLETED'" class="hint">本需求已完成结算。本厂工单见上方生产与质检。</p>
      <p v-else class="hint">工单质检通过并评价后进入结算，资金按托管规则解冻。</p>
    </el-card>

    <el-dialog v-model="commitDialog" title="填报报价" width="560px" :close-on-click-modal="false">
      <p class="hint">报该品全部工序的一件单价。承接量为意向报名时填写的区间，不可改。提交后按「单价 × 承接区间最高值」冻结 5% 保证金（只冻一次）。</p>
      <el-form label-width="120px">
        <el-form-item label="实施方案" required>
          <el-input v-model="commitForm.planText" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="承接区间">{{ qtyRangeLabel }}</el-form-item>
        <el-form-item label="单价(元/件)" required>
          <el-input-number v-model="commitUnit" :min="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label=" ">
          <el-checkbox v-model="commitForm.confirm">确认提交并冻结保证金 ￥{{ commitDeposit }}</el-checkbox>
        </el-form-item>
      </el-form>
      <div class="summary">总报价：<b>￥{{ commitTotal }}</b></div>
      <template #footer>
        <el-button @click="commitDialog=false">取消</el-button>
        <el-button type="warning" :disabled="!commitForm.confirm" @click="submitCommit">提交方案并冻结保证金</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="signOpen" :title="alreadySigned ? '合同详情' : '阅读并签署合同'" width="520px" :close-on-click-modal="false">
      <p>订单 #{{ row.orderId }}</p>
      <p>状态：{{ alreadySigned ? '已签约' : (contract.status || '待签署') }}</p>
      <p v-if="contract.attachmentId">
        合同文件
        <el-button size="small" link type="primary" @click="openContractFile(contract.attachmentId)">{{ contract.fileName || ('附件 #' + contract.attachmentId) }}</el-button>
      </p>
      <p v-else>买家尚未下发与你的合同</p>
      <template v-if="!alreadySigned">
        <el-checkbox v-model="signRead">我已阅读合同全文</el-checkbox>
        <p>手写签名</p>
        <SignPad @change="signData = $event" />
      </template>
      <template #footer>
        <el-button v-if="alreadySigned" type="primary" @click="signOpen=false">关闭</el-button>
        <template v-else>
          <el-button @click="signOpen=false">取消</el-button>
          <el-button type="primary" :disabled="!signRead || !signData || !contract.attachmentId" @click="doSign">提交签名</el-button>
        </template>
      </template>
    </el-dialog>

    <el-dialog v-model="progOpen" title="上报进度" width="480px">
      <el-form label-width="100px">
        <el-form-item label="完成件数">
          <el-input-number v-model="progForm.doneQty" :min="1" :max="progStage?.quantity || 1" />
          <span class="hint"> / {{ progStage?.quantity }}</span>
        </el-form-item>
        <el-form-item label="说明" required>
          <el-input v-model="progForm.remark" type="textarea" />
        </el-form-item>
        <el-form-item label="现场照片">
          <el-upload :auto-upload="false" :limit="1" :on-change="onProgFile">
            <el-button>选择图片</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progOpen=false">取消</el-button>
        <el-button type="primary" @click="submitProg">提交</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="delOpen" title="确认交付" width="420px" :close-on-click-modal="false">
      <p>约定 {{ delStage?.quantity }} 件。请填写本次实交件数，不足约定将按数量不达标质检。</p>
      <el-form label-width="100px" style="margin-top:12px">
        <el-form-item label="实交件数">
          <el-input-number v-model="delQty" :min="1" :max="delStage?.quantity || 1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delOpen=false">取消</el-button>
        <el-button type="primary" @click="submitDeliver">确认交付</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="reworkOpen" title="返工原因" width="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="本期">{{ reworkStage?.periodLabel || reworkStage?.processName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="返工截止">{{ fmtTime(reworkStage?.reworkDeadlineAt) }}</el-descriptions-item>
        <el-descriptions-item label="返工原因">{{ reworkReasonText }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="reworkOpen=false">关闭</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="inspOpen" title="质检报告" width="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="结论">{{ inspReport.result || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实交数量">{{ inspJson.deliveredQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="抽检数">{{ inspJson.sampleCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="inspJson.aqlAc != null" label="AQL Ac/Re">{{ inspJson.aqlAc }} / {{ inspJson.aqlRe }}</el-descriptions-item>
        <el-descriptions-item label="关键公差不合格">{{ inspJson.criticalFailCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="一般公差不合格">{{ inspJson.generalFailCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="合格比例">{{ inspJson.actualYield ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="公差是否合格">{{ inspJson.toleranceOk === true ? '是' : inspJson.toleranceOk === false ? '否' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量达标">{{ inspJson.quantityOk ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="关键尺寸">{{ inspJson.keyDimensions || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ inspJson.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
    <el-dialog v-model="surveyOpen" title="总体评价" width="420px">
      <el-form label-width="100px">
        <el-form-item label="总体评价">
          <el-rate v-model="overall" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="surveyOpen=false">取消</el-button>
        <el-button type="primary" @click="doSurvey">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { payIntention, commit } from '../../api/bidding'
import { getContract, factorySign, cancelByFactory, myDemandJobs, deliver, submitSurvey, reportProgress, inspectionOf, uploadFile, payInspectFee } from '../../api/order'
import { downloadAttachment } from '../../api/file'
import SignPad from '../../components/SignPad.vue'
import IntentionCountdown from '../../components/IntentionCountdown.vue'
import BuyerInfoBlock from '../../components/BuyerInfoBlock.vue'
import { DEMAND_STATUS, INTENTION_STATUS, DEPOSIT_STATUS, STAGE_STATUS, ESCROW_STATUS, label, fmtTime, fmtDateLine, fmtTimeLine, formatInspectMode, stageProgressLabel, nestReworkPeriods, canShowInspectReport, canPayInspectFee, factoryWorkSpan } from '../../utils/labels'

const route = useRoute()
const loading = ref(false)
const demand = ref({})
const processes = ref([])
const attachments = ref([])
const buyer = ref({})
const quotes = ref([])
const job = ref({ periods: [] })
const contract = ref({})
const extra = computed(() => {
  try { return JSON.parse(demand.value.extraJson || '{}') } catch { return {} }
})

const row = computed(() => {
  const qs = quotes.value
  const g = {
    demandId: Number(route.params.id),
    createdAt: qs[0]?.createdAt,
    actionKey: 'NONE',
    orderId: null,
    quotes: qs,
  }
  const prefer = { LOSE: 10, PAY: 6, COMMIT: 6, WAIT_INTENTION: 5, SIGN: 5, WAIT_BUYER_CONFIRM: 5, PENDING_REVIEW: 5, WAIT_ISSUE: 4, WAIT_BUYER: 4, WAIT_SOLUTION: 4, WAIT_DISPATCH: 4, SIGNED: 4, REAPPLY: 2, NONE: 0 }
  for (const q of qs) {
    if (q.createdAt && (!g.createdAt || q.createdAt < g.createdAt)) g.createdAt = q.createdAt
    if ((prefer[q.actionKey] || 0) > (prefer[g.actionKey] || 0)) {
      g.actionKey = q.actionKey
      g.orderId = q.orderId || g.orderId
    }
    if (q.orderId) g.orderId = q.orderId
  }
  if (qs.some(q => q.status === 'LOSE') && !qs.some(q => q.status === 'WIN')) {
    g.actionKey = 'LOSE'
  }
  return g
})
const actionKey = computed(() => row.value.actionKey)
const quote = computed(() => quotes.value[0] || {})

function statusIndex(st) {
  if (['PENDING_AUDIT', 'RETURNED', 'DRAFT'].includes(st)) return 0
  if (['PUBLISHED', 'FACTORY_THINKING', 'BUYER_THINKING', 'THINKING', 'LOCKING', 'REVIEWING'].includes(st)) return 1
  if (['SOLUTION_GENERATED', 'SOLUTION_CONFIRMED'].includes(st)) return 2
  if (st === 'SOLUTION_SELECTED') return 3
  if (['CONTRACTED', 'IN_PRODUCTION'].includes(st)) return 4
  if (st === 'COMPLETED') return 5
  if (st === 'CANCELLED' || st === 'FLOW_FAILED') return demand.value.publishedAt ? 1 : 0
  return 0
}
const won = computed(() => quotes.value.some(q => q.status === 'WIN'))
const lost = computed(() => {
  const st = demand.value.status
  const after = ['SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED']
  if (!after.includes(st) || won.value) return false
  return quotes.value.some(q => q.status === 'LOSE' || q.actionKey === 'LOSE') || actionKey.value === 'LOSE'
})
const flowStep = computed(() => lost.value ? 2 : statusIndex(demand.value.status))
const stepActive = computed(() => {
  if (lost.value) return 2
  return demand.value.status === 'COMPLETED' ? 6 : flowStep.value
})
function reached(n) {
  return flowStep.value >= n
}

const showThinkingCard = computed(() => {
  const st = demand.value.status
  return ['FACTORY_THINKING', 'BUYER_THINKING', 'THINKING', 'LOCKING', 'REVIEWING', 'SOLUTION_GENERATED', 'SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(st)
    || hasQuoted.value
})
const hasQuoted = computed(() => quotes.value.some(q => q.unitPrice != null || ['LOCKED', 'WIN', 'LOSE'].includes(q.status)))
const wonQty = computed(() => {
  const q = quotes.value.find(x => x.status === 'WIN') || quotes.value[0]
  return q?.quantity ?? job.value?.periods?.reduce((s, p) => s + (Number(p.quantity) || 0), 0) ?? '-'
})
const wonAmount = computed(() => {
  const q = quotes.value.find(x => x.status === 'WIN' && x.price != null) || quotes.value.find(x => x.price != null)
  return q?.price ?? job.value.totalAmount ?? '-'
})
const showStages = computed(() => (job.value.periods || []).length > 0 && reached(3))
const settledHint = computed(() => (job.value.periods || []).some(p => p.status === 'CLOSED' || p.escrowStatus === 'SETTLED'))

const deliveryRows = computed(() => {
  try {
    const arr = JSON.parse(demand.value.deliveryPlanJson || '[]')
    if (!Array.isArray(arr)) return []
    return arr.map((x) => ({
      percent: x?.percent != null ? (Number(x.percent) + '%') : '-',
      startAt: x?.startAt || '-',
      endAt: x?.endAt || '-',
    }))
  } catch { return [] }
})

const intentionLabel = computed(() => {
  const q = quotes.value.find(x => x.intentionStatus === 'FROZEN')
    || quotes.value.find(x => x.intentionStatus === 'COVERED')
    || quotes.value[0]
  return q ? label(INTENTION_STATUS, q.intentionStatus) : '-'
})
const depositLabel = computed(() => {
  const q = quotes.value.find(x => x.depositStatus && x.depositStatus !== 'NONE') || quotes.value[0]
  return q ? label(DEPOSIT_STATUS, q.depositStatus) : '-'
})
const unitPriceLabel = computed(() => {
  const priced = quotes.value.filter(q => q.unitPrice != null)
  return priced.length ? priced[0].unitPrice : ''
})
const totalPriceLabel = computed(() => {
  const priced = quotes.value.filter(q => q.price != null)
  if (!priced.length) return ''
  const nums = priced.map(q => Number(q.price))
  const uniq = [...new Set(nums)]
  return (uniq.length === 1 ? uniq[0] : Math.max(...nums)).toFixed(2)
})
const qtyRangeLabel = computed(() => {
  const q = quotes.value[0]
  if (!q) return '-'
  const min = q.minQty ?? 1
  const max = q.maxQty ?? q.quantity
  if (max == null) return '-'
  return min + ' ~ ' + max + ' 件'
})

const commitDialog = ref(false)
const commitForm = reactive({ planText: '', confirm: false })
const commitUnit = ref(null)
const commitMaxQty = computed(() => {
  const q = quotes.value[0]
  const max = Number(q?.maxQty)
  const qty = Number(q?.quantity)
  const min = Number(q?.minQty) || 1
  return Math.max(Number.isFinite(max) ? max : 0, Number.isFinite(qty) ? qty : 0, min)
})
const commitTotal = computed(() => ((commitUnit.value || 0) * (commitMaxQty.value || 0)).toFixed(2))
const commitDeposit = computed(() => (Number(commitTotal.value) * 0.05).toFixed(2))

const signOpen = ref(false)
const signRead = ref(false)
const signData = ref('')
const alreadySigned = computed(() => contract.value.status === 'SIGNED')

const surveyOpen = ref(false)
const surveyStage = ref(null)
const overall = ref(5)
const progOpen = ref(false)
const progStage = ref(null)
const progForm = reactive({ doneQty: 1, remark: '' })
const progFile = ref(null)
const inspOpen = ref(false)
const inspReport = ref({})
const delOpen = ref(false)
const delStage = ref(null)
const delQty = ref(1)
const reworkOpen = ref(false)
const reworkStage = ref(null)
const reworkReasonText = ref('')
const inspJson = computed(() => {
  try { return inspReport.value.reportJson ? JSON.parse(inspReport.value.reportJson) : {} } catch { return {} }
})

async function load() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const d = await api.get(`/demand/${id}`)
    demand.value = d.demand || {}
    processes.value = d.processes || []
    attachments.value = d.attachments || []
    buyer.value = d.buyer || {}
    const mine = await api.get('/bidding/mine')
    quotes.value = (mine || []).filter(q => Number(q.demandId) === id)
    const jobs = await myDemandJobs()
    job.value = (jobs || []).find(j => Number(j.demandId) === id) || { periods: [] }
    if (row.value.orderId) {
      try { contract.value = await getContract(row.value.orderId) } catch { contract.value = {} }
    } else {
      contract.value = {}
    }
  } finally {
    loading.value = false
  }
}

function openCommit() {
  const mine = quotes.value.filter(q => q.status === 'INTENTION')
  if (!mine.length) return ElMessage.warning('没有可填报的报名')
  commitForm.planText = mine.find(q => q.planText)?.planText || ''
  commitUnit.value = mine.find(q => q.unitPrice)?.unitPrice || null
  commitForm.confirm = false
  commitDialog.value = true
}
async function submitCommit() {
  if (!commitForm.planText.trim()) return ElMessage.warning('请填写实施方案')
  if (!commitUnit.value) return ElMessage.warning('请填写该品单价')
  await commit({
    demandId: Number(route.params.id),
    planText: commitForm.planText,
    items: [{ processNo: quotes.value[0]?.processNo || 1, unitPrice: commitUnit.value }],
  })
  ElMessage.success('方案已提交，保证金已冻结')
  commitDialog.value = false
  load()
}
async function pay() {
  const q = quotes.value.find(x => x.intentionStatus === 'PENDING_PAY')
  if (!q) return
  const data = await payIntention(q.id)
  if (data?.payUrl) {
    window.open(data.payUrl, '_blank')
    ElMessage.success('已打开支付宝沙箱，付完后刷新本页')
  }
}
async function cancelBid() {
  const q = quotes.value.find(x => x.status === 'INTENTION')
  if (!q) return
  await ElMessageBox.confirm('确认取消报名？意向金将退回。意向期内取消后，可重新填写承接量并再次报名。', '取消报名', { type: 'warning' })
  await api.post(`/bidding/${q.id}/cancel-intention`)
  ElMessage.success('已取消。请到「浏览需求」重新填报意向')
  load()
}
async function openSign() {
  if (!row.value.orderId) return ElMessage.warning('尚未生成订单')
  signRead.value = false
  signData.value = ''
  contract.value = await getContract(row.value.orderId)
  signOpen.value = true
}
async function openContractFile(id) {
  try { await downloadAttachment(id) } catch (e) { ElMessage.error(e.message || '无法打开合同') }
}
async function doSign() {
  await factorySign(row.value.orderId, true, signData.value)
  ElMessage.success('已签署合同')
  signOpen.value = false
  load()
}
async function cancelOrder() {
  if (!row.value.orderId) return ElMessage.warning('尚未生成订单')
  await ElMessageBox.confirm('取消后将扣除你缴纳的保证金赔偿买家，其他工厂合同继续。确定取消？', '取消订单', { type: 'warning', confirmButtonText: '再确认一次', cancelButtonText: '返回' })
  await ElMessageBox.confirm('请再次确认：此操作不可撤销，将扣除本厂保证金赔偿买家。', '二次确认', { type: 'warning', confirmButtonText: '确认取消订单', cancelButtonText: '返回' })
  await cancelByFactory(row.value.orderId)
  ElMessage.success('已取消本厂订单')
  load()
}

function jobStatusText(j) {
  if (!j.contractSigned) return '待签约'
  return label(STAGE_STATUS, j.status)
}
function periodStatusText(p) {
  return stageProgressLabel(p)
}
function canProduce(p) {
  return p.contractSigned && p.windowOpen && p.status === 'IN_PRODUCTION' && p.status !== 'CANCELLED'
}
function isReworking(p) {
  return p?.status === 'IN_PRODUCTION' && Number(p.reworkCount) > 0
}
function reasonFromInspect(ins, p) {
  if (p?.reworkReason) return p.reworkReason
  let r = {}
  try { r = ins?.reportJson ? JSON.parse(ins.reportJson) : {} } catch { r = {} }
  const parts = []
  if (r.quantityOk === false) parts.push('数量不达标')
  const dc = Number(r.criticalFailCount) || 0
  const dg = Number(r.generalFailCount) || 0
  if (dc > 0) parts.push('关键公差不合格 ' + dc + ' 件')
  if (r.toleranceOk === false && dc === 0) {
    if (r.aqlAc != null) parts.push('一般缺陷超过 Ac=' + r.aqlAc)
    else parts.push('抽检良率低于最低良率')
  } else if (dg > 0) parts.push('一般公差不合格 ' + dg + ' 件')
  if (r.actualYield != null && r.actualYield !== '') parts.push('抽检良率 ' + r.actualYield)
  if (r.remark) parts.push('质检备注：' + r.remark)
  if (r.keyDimensions) parts.push('关键尺寸：' + r.keyDimensions)
  return parts.length ? parts.join('；') : '买家要求返工'
}
async function openReworkReason(p) {
  reworkStage.value = p
  reworkReasonText.value = p.reworkReason || '加载中…'
  reworkOpen.value = true
  if (p.reworkReason) return
  try {
    const ins = await inspectionOf(p.id)
    reworkReasonText.value = reasonFromInspect(ins, p)
  } catch {
    reworkReasonText.value = '买家要求返工'
  }
}
function openProg(p) {
  progStage.value = p
  progForm.doneQty = Math.min((p.quantity || 1), Math.max(1, (p.actualProgress || 0) * (p.quantity || 1) / 100 + 1 | 0) || 1)
  progForm.remark = ''
  progFile.value = null
  progOpen.value = true
}
function onProgFile(f) { progFile.value = f.raw }
async function submitProg() {
  if (!progForm.remark.trim()) return ElMessage.warning('请填写说明')
  let attachmentId = null
  if (progFile.value) {
    const up = await uploadFile(progFile.value, 'PROGRESS')
    attachmentId = up.id
  }
  await reportProgress(progStage.value.id, { doneQty: progForm.doneQty, remark: progForm.remark.trim(), attachmentId })
  ElMessage.success('进度已上报')
  progOpen.value = false
  load()
}
async function openInsp(p) {
  if (!canShowInspectReport(p)) return ElMessage.warning('质检尚未完成，暂无质检报告')
  const ins = await inspectionOf(p.id)
  if (!ins) return ElMessage.warning('质检尚未完成，暂无质检报告')
  inspReport.value = ins
  inspOpen.value = true
}
function openDeliver(p) {
  delStage.value = p
  delQty.value = p.deliveredQty || 1
  delOpen.value = true
}
async function submitDeliver() {
  if (!delQty.value || delQty.value < 1) return ElMessage.warning('请填写实交件数')
  await deliver(delStage.value.id, { deliveredQty: delQty.value })
  ElMessage.success('已交付')
  delOpen.value = false
  load()
}
async function payFee(p) {
  await payInspectFee(p.id)
  ElMessage.success('质检费已支付，等待质检员检验')
  load()
}
function openSurvey(p) {
  surveyStage.value = p
  overall.value = 5
  surveyOpen.value = true
}
async function doSurvey() {
  await submitSurvey(surveyStage.value.id, { overall: overall.value })
  ElMessage.success('评价已提交')
  surveyOpen.value = false
  load()
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped>
.spec-tabs { margin-bottom: 16px; }
.phase { margin-top: 16px; }
.phase-actions { margin-top: 12px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; }
.dt-2 { line-height: 1.35; }
.dt-clock { color: #606266; font-size: 12px; }
.op-text { color: #909399; font-size: 13px; }
.summary { margin: 12px 0; font-size: 14px; }
h4 { margin: 16px 0 8px; }
.rework-status { color: #f56c6c; cursor: pointer; font-weight: 600; }
.rework-status:hover { text-decoration: underline; }
</style>
