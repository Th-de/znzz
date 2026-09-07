<template>
  <div v-loading="loading">
    <el-steps :active="stepActive" finish-status="success" align-center class="flow-steps">
      <el-step title="发布需求" />
      <el-step title="意向期" />
      <el-step title="方案确定" />
      <el-step title="合同签署" />
      <el-step title="生产与质检" />
      <el-step title="订单结算" />
    </el-steps>

    <el-form ref="formRef" :model="form" :rules="canEditDemand ? rules : {}" label-width="130px" @submit.prevent>
      <el-tabs type="border-card" stretch class="spec-tabs">
        <el-tab-pane label="基础">
          <template v-if="canEditDemand">
            <el-form-item label="需求标题" prop="title"><el-input v-model="form.title" /></el-form-item>
            <el-form-item label="产品名称" prop="productName"><el-input v-model="form.productName" /></el-form-item>
            <el-form-item label="产品类别" prop="category">
              <el-select v-model="form.category" style="width:100%">
                <el-option label="机加" value="机加" />
                <el-option label="齿轮" value="齿轮" />
                <el-option label="注塑" value="注塑" />
                <el-option label="钣金" value="钣金" />
                <el-option label="其他" value="其他" />
              </el-select>
            </el-form-item>
            <el-form-item label="图号/版本" prop="partRevision"><el-input v-model="form.partRevision" /></el-form-item>
            <el-form-item label="数量" prop="quantity"><el-input-number v-model="form.quantity" :min="1" /></el-form-item>
          </template>
          <el-descriptions v-else :column="2" border>
            <el-descriptions-item label="标题" :span="2">{{ demand.title }}</el-descriptions-item>
            <el-descriptions-item label="产品">{{ demand.productName }}</el-descriptions-item>
            <el-descriptions-item label="产品类别">{{ demand.category || '-' }}</el-descriptions-item>
            <el-descriptions-item label="图号/版本">{{ demand.partRevision || '-' }}</el-descriptions-item>
            <el-descriptions-item label="数量">{{ demand.quantity }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ fmtTime(demand.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="技术规格">
          <template v-if="canEditDemand">
            <el-form-item label="材料牌号" prop="material"><el-input v-model="form.material" /></el-form-item>
            <el-form-item label="一般公差" prop="generalTolerance">
              <el-select v-model="form.generalTolerance" style="width:100%">
                <el-option label="ISO 2768-m" value="ISO 2768-m" />
                <el-option label="ISO 2768-f" value="ISO 2768-f" />
                <el-option label="按图纸" value="按图纸" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键公差" prop="tolerance"><el-input v-model="form.tolerance" /></el-form-item>
            <el-form-item label="粗糙度 Ra" prop="roughness">
              <el-select v-model="form.roughness" style="width:100%">
                <el-option label="Ra 3.2" value="3.2" />
                <el-option label="Ra 1.6" value="1.6" />
                <el-option label="Ra 0.8" value="0.8" />
              </el-select>
            </el-form-item>
            <el-form-item label="表面处理" prop="surfaceTreatment"><el-input v-model="form.surfaceTreatment" /></el-form-item>
            <el-form-item label="热处理" prop="heatTreatment"><el-input v-model="form.heatTreatment" /></el-form-item>
          </template>
          <el-descriptions v-else :column="2" border>
            <el-descriptions-item label="材料">{{ demand.material }}</el-descriptions-item>
            <el-descriptions-item label="一般公差">{{ demand.generalTolerance || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关键公差">{{ demand.tolerance || '-' }}</el-descriptions-item>
            <el-descriptions-item label="粗糙度 Ra">{{ extra.roughness || '-' }}</el-descriptions-item>
            <el-descriptions-item label="表面处理">{{ demand.surfaceTreatment || '-' }}</el-descriptions-item>
            <el-descriptions-item label="热处理">{{ extra.heatTreatment || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="质量门槛">
          <template v-if="canEditDemand">
            <div class="card-head"><InspectDeliveryRules /></div>
            <el-form-item label="检验方式" prop="inspectMode">
              <el-radio-group v-model="form.inspectMode" class="inspect-modes" @change="onInspectMode">
                <el-radio value="AQL">AQL 抽样（{{ INSPECT_UNIT.AQL }} 元/件）</el-radio>
                <el-radio value="FULL">全检（{{ INSPECT_UNIT.FULL }} 元/件）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="needAql" label="AQL" prop="aql">
              <el-select v-model="form.aql" style="width:100%">
                <el-option label="0.65" value="0.65" />
                <el-option label="1.0" value="1.0" />
                <el-option label="1.5" value="1.5" />
                <el-option label="2.5" value="2.5" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="needFull" label="最低良率" prop="minYield">
              <el-input-number v-model="form.minYield" :min="0" :max="1" :step="0.01" />
            </el-form-item>
            <el-form-item label="认证要求" prop="certList">
              <el-select v-model="form.certList" multiple filterable allow-create default-first-option style="width:100%">
                <el-option v-for="c in CERT_OPTIONS" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item label="最低信用分"><el-input-number v-model="form.minCreditScore" :min="0" :max="100" /></el-form-item>
          </template>
          <el-descriptions v-else :column="2" border>
            <el-descriptions-item label="检验方式">{{ formatInspectMode(demand.inspectMode) }}</el-descriptions-item>
            <el-descriptions-item label="AQL">{{ demand.aql || '-' }}</el-descriptions-item>
            <el-descriptions-item label="认证">{{ demand.certification || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="demand.minYield != null" label="最低良率">{{ demand.minYield }}</el-descriptions-item>
            <el-descriptions-item label="最低信用分">{{ demand.minCreditScore }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="交期与交付">
          <template v-if="canEditDemand">
            <el-form-item label="硬交期" prop="deadlineHard"><el-date-picker v-model="form.deadlineHard" type="date" value-format="YYYY-MM-DD" /></el-form-item>
            <el-form-item label="交付地址" prop="deliveryAddress"><el-input v-model="form.deliveryAddress" /></el-form-item>
            <el-form-item label="包装要求" prop="packaging"><el-input v-model="form.packaging" /></el-form-item>
            <el-form-item label="分期交付次数">
              <div class="step">
                <button type="button" class="step-btn" @click="setTimes(deliveryTimes - 1)">−</button>
                <span class="step-num">{{ deliveryTimes }}</span>
                <button type="button" class="step-btn" @click="setTimes(deliveryTimes + 1)">+</button>
              </div>
            </el-form-item>
            <el-form-item v-for="(row, i) in deliveryPlan" :key="'plan-' + i" :label="`第${i + 1}期`">
              <div class="period-row">
                <div class="qty-wrap">
                  <div class="step">
                    <button type="button" class="step-btn" @click="bumpPercent(i, -1)">−</button>
                    <input class="step-input" type="number" min="1" max="100" v-model.number="row.percent" @blur="clampPercent(i)" />
                    <button type="button" class="step-btn" @click="bumpPercent(i, 1)">+</button>
                  </div>
                  <span class="qty-unit">%</span>
                </div>
                <el-date-picker v-model="row.startAt" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" :disabled="i > 0" />
                <el-date-picker v-model="row.endAt" type="date" value-format="YYYY-MM-DD" placeholder="截止日期" :disabled="i === deliveryPlan.length - 1" @change="onPeriodEndChange(i)" />
              </div>
            </el-form-item>
            <div class="hint" style="margin:-4px 0 12px 130px">已分配 {{ planPercentSum }}% / 100%</div>
          </template>
          <template v-else>
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
          </template>
        </el-tab-pane>
        <el-tab-pane label="工序意向图纸">
          <template v-if="canEditDemand">
            <el-form-item label="工序列表" prop="processes">
              <div class="row head">
                <span class="col-name">工序名</span>
                <span class="col-req">特殊要求</span>
                <span class="col-actions"></span>
              </div>
              <div v-for="(p, i) in form.processes" :key="i" class="row">
                <el-input v-model="p.processName" placeholder="如 粗车" class="col-name" />
                <el-input v-model="p.requirement" placeholder="可空" class="col-req" />
                <div class="proc-actions">
                  <el-button type="danger" :icon="Delete" circle plain @click="removeProcess(i)" />
                  <el-button v-if="i === form.processes.length - 1" type="primary" :icon="Plus" plain @click="addProcess">添加工序</el-button>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="意向期天数">
              <div class="step">
                <button type="button" class="step-btn" @click="form.intentionDays = Math.max(1, form.intentionDays - 1)">−</button>
                <span class="step-num">{{ form.intentionDays }}</span>
                <button type="button" class="step-btn" @click="form.intentionDays = Math.min(30, form.intentionDays + 1)">+</button>
              </div>
            </el-form-item>
            <el-form-item label="图纸/文档" prop="fileName">
              <input type="file" accept=".md,.pdf,.png,.jpg,.jpeg,.dwg,.dxf,.stp,.step,.zip" @change="onEditFile" />
              <span v-if="form.fileName" class="hint">已选：{{ form.fileName }}</span>
            </el-form-item>
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
          </template>
          <template v-else>
            <el-table :data="namedProcesses" border size="small">
              <el-table-column prop="processNo" label="序号" width="70" />
              <el-table-column prop="processName" label="工序" />
              <el-table-column prop="requirement" label="要求" />
            </el-table>
            <h4>附件</h4>
            <el-table :data="attachments" border size="small">
              <el-table-column prop="fileName" label="文件名" />
              <el-table-column prop="fileType" label="类型" width="80" />
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="viewFile(row)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-descriptions :column="2" border style="margin-top:12px">
              <el-descriptions-item label="意向天数">{{ demand.intentionDays }}</el-descriptions-item>
              <el-descriptions-item label="备注">{{ demand.remark || '-' }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-form>

    <el-card shadow="never" class="phase">
      <template #header>发布需求</template>
      <el-alert v-if="demand.status === 'PENDING_AUDIT'" type="info" :closable="false" title="等待运营审核。通过后自动进入意向期。" />
      <el-alert v-else-if="demand.status === 'RETURNED'" type="warning" :closable="false" :title="'审核不通过：' + (demand.returnReason || '未填写原因') + '。请在上方栏目修改后重新提交，或取消订单。'" />
      <el-alert v-else-if="demand.status === 'CANCELLED'" type="info" :closable="false" :title="'已取消' + (demand.cancelReason ? '：' + demand.cancelReason : '')" />
      <el-alert v-else type="success" :closable="false" title="已通过审核并发布。" />
      <div v-if="demand.status === 'RETURNED'" class="phase-actions">
        <el-button type="primary" :loading="saving" @click="submitRepublish">重新提交</el-button>
        <el-button type="danger" @click="cancelDraft">取消订单</el-button>
      </div>
    </el-card>

    <el-card v-if="reached(1)" shadow="never" class="phase">
      <template #header>意向期</template>
      <IntentionCountdown v-if="demand.status==='PUBLISHED'" :end-at="demand.intentionEndAt" />
      <IntentionCountdown v-else-if="demand.status==='FACTORY_THINKING'" :end-at="demand.factoryThinkingEndAt" />
      <IntentionCountdown v-else-if="demand.status==='BUYER_THINKING'" :end-at="demand.buyerThinkingEndAt" />
      <IntentionCountdown v-else-if="demand.status==='LOCKING'" :end-at="demand.lockingEndAt" />
      <div v-if="demand.status==='BUYER_THINKING'" class="deposit-box">
        根据报名工厂报价预估总金额：<b>￥{{ money(estimatedTotal) }}</b>。
        去掉最高、最低单价后，按各厂承接区间最高值加权的平均单件报价：<b>￥{{ money(weightedUnit) }}</b>/件。
        继续需冻结 5% 保证金 <b>￥{{ money(buyerDeposit) }}</b>（尾款抵扣）。思考期取消将全额退回各方意向金与保证金。
      </div>
      <div v-if="demand.status==='BUYER_THINKING'" class="phase-actions">
        <el-button type="success" @click="buyerContinue">继续并交保证金</el-button>
        <el-button type="danger" @click="buyerCancel">取消需求（全退）</el-button>
      </div>
      <div v-if="demand.status==='THINKING'" class="phase-actions">
        <el-button type="success" @click="decide('CONTINUE')">继续竞标</el-button>
        <el-button type="danger" @click="decide('CANCEL')">取消需求</el-button>
      </div>
      <h4>报名工厂</h4>
      <el-empty v-if="!factories.length" description="暂无工厂报名" :image-size="60" />
      <el-table v-else :data="factories" border size="small">
        <el-table-column label="工厂名称" min-width="160">
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column label="工厂信息" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFactory(row)">详情</el-button>
          </template>
        </el-table-column>
        <el-table-column v-if="showAllocQty" label="承接数量" width="120">
          <template #default="{ row }">{{ allocQtyText(row) }}</template>
        </el-table-column>
        <el-table-column v-else label="承接区间" width="160">
          <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
        </el-table-column>
        <el-table-column label="阶段" width="100">
          <template #default="{ row }">{{ bidStageText(row.bidStage) }}</template>
        </el-table-column>
        <el-table-column v-if="showFactoryQuotes" label="单价" width="110">
          <template #default="{ row }">{{ factoryUnitPrice(row) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="showCoverage" style="margin-top:12px">
        <div class="cov-label">零件覆盖</div>
        <CoverageBars :items="coverage" />
      </div>
    </el-card>

    <el-card v-if="reached(2)" shadow="never" class="phase">
      <template #header>方案确定</template>
      <div v-if="canCloseSolution" class="phase-actions">
        <el-button type="danger" @click="closeOrder">关闭订单</el-button>
      </div>
      <el-alert v-if="demand.status === 'SOLUTION_GENERATED' && !hasActiveSolution" type="info" :closable="false" title="等待平台确认推荐方案。" />
      <el-alert v-else-if="demand.status === 'SOLUTION_CONFIRMED'" type="success" :closable="false" title="方案已确认，正在进入合同签署。" />
      <template v-if="demand.status === 'SOLUTION_GENERATED' && hasActiveSolution">
        <el-alert type="info" :closable="false" title="上方是各厂报价。AI 推荐仅供参考且不可改；可在自选方案里按零件件数把整单分给工厂后确认。" style="margin-bottom:12px" />
        <el-card v-if="factoryQuoteList.length" shadow="never" style="margin-bottom:12px">
          <template #header>各工厂报价</template>
          <el-table :data="factoryQuoteList" size="small" border>
            <el-table-column label="工厂" min-width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="openFactoryById(row.factoryId)">{{ row.factoryName }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="单价" width="90" />
            <el-table-column label="承接区间" width="140">
              <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-row :gutter="16">
          <el-col :span="12" v-for="s in aiSolutions" :key="s.id">
            <el-card :header="'推荐方案 ' + (s.type || '') + '（仅供参考，不可改）'">
              <p class="meta">生成时间 {{ fmtTime(s.createdAt) }}</p>
              <p class="meta">{{ parseRationale(s.rationaleJson).rationale }}</p>
              <el-alert v-for="(r, i) in parseRationale(s.rationaleJson).risks" :key="i" type="warning" :closable="false" :title="r" style="margin-bottom:8px" />
              <el-table :data="factoryAllocRows(s.finalComboJson)" size="small" border>
                <el-table-column label="工厂" min-width="140">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openFactoryById(row.factoryId)">{{ row.factoryName || ('工厂-' + row.factoryId) }}</el-button>
                  </template>
                </el-table-column>
                <el-table-column label="分配(件)" width="88">
                  <template #default="{ row }">{{ row.quantity ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="单价" width="76">
                  <template #default="{ row }">{{ row.unitPrice ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="小计" width="90">
                  <template #default="{ row }">{{ moneyText(row.price) }}</template>
                </el-table-column>
              </el-table>
              <div class="total-bar">
                <span>总计金额</span>
                <strong>{{ moneyText(comboTotal(s.finalComboJson)) }} 元</strong>
              </div>
              <el-button type="primary" style="width:100%;margin-top:10px" @click="selectAi(s)">按此分配确认方案</el-button>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card header="自选方案">
              <p class="meta">各厂件数合计须等于需求量 {{ demandNeed }} 件，且落在该厂承接区间内。</p>
              <div class="proc-head">
                <span class="proc-title">需求 {{ demandNeed }} 件</span>
                <span class="alloc-status" :class="allocSum === demandNeed ? 'ok' : 'bad'">已分配 {{ allocSum }} / {{ demandNeed }}</span>
              </div>
              <div class="line head">
                <span class="col-del" />
                <span class="col-fac">报名工厂</span>
                <span class="col-range">承接区间</span>
                <span class="col-qty">分配(件)</span>
                <span class="col-amt">小计</span>
              </div>
              <div v-for="(line, i) in customLines" :key="'c-' + i" class="line">
                <span class="col-del">
                  <el-button type="danger" :icon="Delete" circle plain size="small" @click="removeLine(i)" />
                </span>
                <el-select v-model="line.factoryId" placeholder="选择工厂" class="col-fac" size="small" filterable clearable>
                  <el-option v-for="f in factoryQuoteList" :key="f.factoryId" :label="f.factoryName" :value="f.factoryId" :disabled="usedFactory(f.factoryId, i)" />
                </el-select>
                <span class="col-range">{{ rangeText(line.factoryId) }}</span>
                <el-input-number v-model="line.quantity" :min="1" class="col-qty" size="small" controls-position="right" />
                <span class="col-amt">{{ moneyText(customLineAmount(line)) }}</span>
              </div>
              <el-button type="primary" :icon="Plus" plain size="small" @click="addLine">添加工厂</el-button>
              <div class="total-bar">
                <span>总计金额</span>
                <strong>{{ moneyText(customTotal) }} 元</strong>
              </div>
              <el-button type="primary" style="width:100%;margin-top:10px" :loading="savingCustom" @click="confirmCustom">确认自选方案</el-button>
            </el-card>
          </el-col>
        </el-row>
      </template>
      <el-card v-if="confirmedPlan.groups.length" shadow="never" class="confirmed-plan">
        <template #header>
          <span>最终方案</span>
          <el-tag type="info" size="small" style="margin-left:8px">平台佣金 1%</el-tag>
        </template>
        <p class="hint" style="margin-top:0">{{ confirmedPlan.hint }} 平台抽取成交金额 1% 作为佣金（提现用户）。</p>
        <div class="line head">
          <span class="col-fac">工厂</span>
          <span class="col-qty">承接数量</span>
          <span class="col-amt">小计</span>
        </div>
        <div v-for="line in confirmedPlan.groups" :key="line.factoryId" class="line">
          <span class="col-fac">
            <el-button v-if="line.factoryId" link type="primary" @click="openFactoryById(line.factoryId)">{{ line.factoryName || ('工厂-' + line.factoryId) }}</el-button>
            <span v-else>{{ line.factoryName || '-' }}</span>
          </span>
          <span class="col-qty">{{ line.quantity ?? '-' }} 件</span>
          <span class="col-amt">{{ money(line.price) }}</span>
        </div>
        <div class="total-bar">
          <span>总计金额</span>
          <strong>{{ money(confirmedPlan.total) }} 元</strong>
          <span>佣金 1%</span>
          <strong>{{ money(confirmedPlan.total * 0.01) }} 元</strong>
        </div>
      </el-card>
    </el-card>

    <el-card v-if="showContracts" shadow="never" class="phase">
      <template #header>合同签署</template>
      <p v-if="fulfillHint" class="hint">{{ fulfillHint }}</p>
      <IntentionCountdown v-if="demand.status==='SOLUTION_SELECTED'" :end-at="order.contractSignEndAt || order.contractIssueEndAt" />
      <el-table :data="contractList" border size="small">
        <el-table-column prop="factoryName" label="工厂" />
        <el-table-column label="承接数量" width="120">
          <template #default="{ row }">{{ allocQtyOf(row) }}</template>
        </el-table-column>
        <el-table-column prop="processNames" label="承包工序" />
        <el-table-column label="合同文件" min-width="180">
          <template #default="{ row }">
            <span v-if="!row.attachmentId">未上传</span>
            <el-button v-else size="small" link type="primary" @click="openContractFile(row.attachmentId)">{{ row.fileName || ('附件 #' + row.attachmentId) }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="签署" min-width="220">
          <template #default="{ row }">
            <div>{{ contractStatusText(row.status) }}</div>
            <div class="hint">{{ row.signHint || '' }}</div>
            <img v-if="isSignImg(row.factorySign)" class="sign-img" :src="row.factorySign" alt="工厂签字" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button v-if="row.status!=='SIGNED' && !row.attachmentId" size="small" type="primary" @click="openUpload(row)">上传合同</el-button>
            <span v-else-if="row.attachmentId && row.status!=='SIGNED'" class="hint">已下发</span>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="success" style="margin-top:12px" :disabled="!canUnifySign" @click="openUnifySign">签名</el-button>
      <span v-if="!allContractsUploaded" class="hint" style="margin-left:10px">请先为每个工厂分别上传合同，下发后不可更换</span>
      <span v-else-if="buyerAllSigned && !canConfirmDispatch" class="hint" style="margin-left:10px">买家已签名，等待各厂签署；工厂签字将显示在本页</span>
      <el-button v-if="canConfirmDispatch" type="primary" style="margin-top:12px;margin-left:8px" @click="confirmAllSigned">确认全部签署并开始派单</el-button>
      <div v-if="order.canBuyerCancel" class="phase-actions">
        <el-button type="danger" @click="cancelFulfillment">取消订单</el-button>
      </div>
    </el-card>

    <el-card v-if="showStages" shadow="never" class="phase">
      <template #header>生产与质检 {{ progressPercent }}%</template>
      <el-progress :percentage="progressPercent" style="margin-bottom:10px" />
      <el-table :data="factoryStageGroups" border size="small" row-key="key">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.children" border size="small">
              <el-table-column label="期数" width="90">
                <template #default="{ row: p }">{{ p.periodLabel || ('第' + (p.periodNo || '-') + '期') }}</template>
              </el-table-column>
              <el-table-column label="数量" width="90">
                <template #default="{ row: p }">{{ p.quantity != null ? p.quantity + ' 件' : '-' }}</template>
              </el-table-column>
              <el-table-column label="进度" width="150">
                <template #default="{ row: p }">
                  <el-progress :percentage="p.actualProgress || 0" :stroke-width="10" />
                  <el-tag v-if="overdueDays(p)" type="danger" size="small">逾期 {{ overdueDays(p) }} 天</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="本段工费" width="100">
                <template #default="{ row: p }">{{ p.amount ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="应托管" width="100">
                <template #default="{ row: p }">{{ p.payAmount != null ? p.payAmount : '-' }}</template>
              </el-table-column>
              <el-table-column label="托管" width="90">
                <template #default="{ row: p }">{{ label(ESCROW_STATUS, p.escrowStatus) }}</template>
              </el-table-column>
              <el-table-column label="操作" min-width="280">
                <template #default="{ row: p }">
                  <el-button size="small" @click="openProgress(p)">进度记录</el-button>
                  <el-button v-if="p.status==='PENDING_INSPECT_PAY' && p.inspectFeePayer!=='FACTORY'" size="small" type="warning" @click="payFee(p)">支付质检费 ¥{{ p.inspectFeeAmount }}</el-button>
                  <el-button v-if="['PASS','FAIL','CLOSED'].includes(p.status)" size="small" @click="openInsp(p)">质检报告</el-button>
                  <el-button v-if="p.status==='FAIL'" size="small" type="warning" @click="openDecide(p)">处理结果</el-button>
                  <el-button v-if="p.status==='PASS' && (p.escrowStatus==='NONE' || p.escrowStatus==='PENDING_PAY')" size="small" type="primary" @click="pay(p)">{{ p.escrowStatus==='PENDING_PAY' ? '继续支付' : ('支付 ¥' + (p.payAmount ?? p.amount)) }}</el-button>
                  <el-button v-if="p.status==='PASS' && !p.surveyed" size="small" @click="openSurvey(p)">评价</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="factoryName" label="工厂" min-width="160" />
        <el-table-column label="承接总数" width="110">
          <template #default="{ row }">{{ row.totalQty }} 件</template>
        </el-table-column>
        <el-table-column label="进度" width="150">
          <template #default="{ row }"><el-progress :percentage="row.progress" :stroke-width="10" /></template>
        </el-table-column>
        <el-table-column label="总工费" width="110">
          <template #default="{ row }">{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="已托管工费" width="120">
          <template #default="{ row }">{{ money(row.escrowHeld) }}</template>
        </el-table-column>
      </el-table>
      <el-button v-if="order.status==='IN_PRODUCTION'" type="success" style="margin-top:12px" @click="doAccept">完工确认</el-button>
    </el-card>

    <el-card v-if="demand.status === 'COMPLETED'" shadow="never" class="phase">
      <template #header>订单结算</template>
      <el-alert type="success" :closable="false" title="订单已完成。" />
    </el-card>

    <el-dialog v-model="fileOpen" :title="fileTitle" width="720px">
      <pre class="file-preview">{{ fileText }}</pre>
    </el-dialog>
    <el-dialog v-model="factoryOpen" title="工厂详情" width="640px" :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="名称" :span="2">{{ factoryInfo.name }}</el-descriptions-item>
        <el-descriptions-item label="信用分">{{ factoryInfo.creditScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="认证">{{ factoryInfo.authStatus === 'APPROVED' ? '已认证' : '未认证' }}</el-descriptions-item>
        <el-descriptions-item label="质检合格率">{{ factoryInfo.inspectionPassRate != null ? factoryInfo.inspectionPassRate + '%' : '暂无记录' }}</el-descriptions-item>
        <el-descriptions-item label="已结算工单">{{ factoryInfo.settledStages ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="买家评分">{{ factoryInfo.surveyAvg ?? '暂无' }}</el-descriptions-item>
        <el-descriptions-item label="日产能合计">{{ factoryInfo.dailyCapacitySum ?? 0 }} 件/天</el-descriptions-item>
        <el-descriptions-item label="体系认证" :span="2">{{ (factoryInfo.certs || []).join('、') || '-' }}</el-descriptions-item>
        <el-descriptions-item label="企业介绍" :span="2">{{ factoryInfo.introduction || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin:12px 0 8px">设备与状态</h4>
      <el-table :data="factoryInfo.devices || []" border size="small">
        <el-table-column prop="name" label="设备" min-width="120" />
        <el-table-column prop="processNames" label="适用工序" min-width="120" />
        <el-table-column prop="dailyCapacity" label="日产能" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="deviceStatusType(row.status)" size="small">{{ label(DEVICE_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <el-dialog v-model="upOpen" :title="'上传合同 · ' + (current?.factoryName || '')" width="520px" destroy-on-close :close-on-click-modal="false" @closed="resetUpload">
      <p>请上传与该厂约定的合同文件。</p>
      <p v-if="current?.fileName" class="hint">该厂当前合同：{{ current.fileName }}</p>
      <el-upload
        :key="uploadKey"
        v-model:file-list="uploadList"
        :auto-upload="false"
        :limit="1"
        :on-change="onFile"
        :on-remove="onUploadRemove"
        :on-exceed="onUploadExceed"
      >
        <el-button>选择文件</el-button>
      </el-upload>
      <template #footer>
        <el-button @click="upOpen=false">取消</el-button>
        <el-button type="primary" :disabled="!upFile" @click="doUpload">上传该厂合同</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="signOpen" title="签名" width="520px" :close-on-click-modal="false">
      <ul class="hint" style="padding-left:18px">
        <li v-for="c in contractList" :key="c.id">{{ c.factoryName }}：{{ c.fileName || (c.attachmentId ? ('附件 #' + c.attachmentId) : '未上传') }}</li>
      </ul>
      <el-checkbox v-model="signRead">我已阅读各厂合同全文</el-checkbox>
      <p>手写签名</p>
      <SignPad @change="signData = $event" />
      <template #footer>
        <el-button @click="signOpen=false">取消</el-button>
        <el-button type="success" :disabled="!signRead || !signData" @click="doSign">提交签名</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="progOpen" title="进度记录" width="560px">
      <el-timeline>
        <el-timeline-item v-for="p in progLogs" :key="p.id" :timestamp="p.createdAt">
          {{ p.doneQty }} / {{ progStage?.quantity }} 件 · {{ p.progress }}% · {{ p.remark }}
          <div v-if="p.attachmentId">
            <el-button size="small" link type="primary" @click="openContractFile(p.attachmentId)">{{ p.fileName || '现场照片' }}</el-button>
          </div>
        </el-timeline-item>
      </el-timeline>
      <p v-if="!progLogs.length">暂无上报</p>
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
    <el-dialog v-model="decideOpen" title="处理质检结果" width="560px" :close-on-click-modal="false">
      <p>分支 {{ decideStage?.branchCode || '-' }}。让步：B 不齐但抽检/全检过关（托管已交工费 + 本阶段工费 5%）；C1 齐但轻微不良（托管全款。抽检再赔本阶段工费 5%；全检赔工费×(最低良率−实际良率)）。C2/D/E 不能让步。</p>
      <p class="hint">关闭将取消该厂后续期，并按「本期+后续工费」5% 从工厂保证金赔你；保证金不足则无法关闭。返工全程一次，期限由你填写（12～72 小时），与原分期截止无关。</p>
      <el-form label-width="120px" style="margin-top:12px">
        <el-form-item label="返工期限(小时)">
          <el-input-number v-model="reworkHours" :min="12" :max="72" :disabled="!decideStage?.canRework" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decideOpen=false">取消</el-button>
        <el-button type="success" :disabled="!decideStage?.canConcede" @click="doDecide('CONCESSION')">让步交款</el-button>
        <el-button type="warning" :disabled="!decideStage?.canRework" @click="doDecide('REWORK')">要求返工</el-button>
        <el-button type="danger" @click="doDecide('CLOSE')">关闭本段</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="surveyOpen" title="总体评价" width="420px">
      <el-form label-width="100px">
        <el-form-item label="总体评价"><el-rate v-model="overall" /></el-form-item>
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
import { getDetail, getCoverage, getCancelStats, decide as decideDemand, buyerDecide as buyerDecideApi, closeSolution, listBidFactories, republish, cancelDemand } from '../../api/demand'
import { listOrders, getOrder, listContracts, uploadContract, buyerSign, stages as listStages, payStage, payInspectFee, accept, submitSurvey, uploadFile, downloadAttachment, progressLog, inspectionOf, decideInspect, cancelByBuyer, confirmDispatch } from '../../api/order'
import { listByDemand, selectSolution, saveCustom } from '../../api/solution'
import { fetchAttachment, saveBlob } from '../../api/file'
import api from '../../api'
import CoverageBars from '../../components/CoverageBars.vue'
import IntentionCountdown from '../../components/IntentionCountdown.vue'
import SignPad from '../../components/SignPad.vue'
import InspectDeliveryRules from '../../components/InspectDeliveryRules.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { DEVICE_STATUS, ESCROW_STATUS, label, fmtTime, deviceStatusType, formatInspectMode } from '../../utils/labels'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const savingCustom = ref(false)
const demand = ref({})
const quoteStats = ref({})
const processes = ref([])
const attachments = ref([])
const coverage = ref([])
const cancelStats = ref({ last90Days: 0, warn: false })
const fileOpen = ref(false)
const fileTitle = ref('')
const fileText = ref('')
const hasActiveSolution = ref(false)
const solutions = ref([])
const factories = ref([])
const factoryOpen = ref(false)
const factoryInfo = ref({})
const customLines = ref([{ factoryId: null, quantity: null }])
const formRef = ref()
const editFile = ref(null)
const existingAttachmentId = ref(null)
const CERT_OPTIONS = ['ISO9001', 'IATF16949', 'CE', 'ISO14001', 'AS9100', 'ISO13485']
const INSPECT_UNIT = { AQL: 5, FULL: 3 }
const requireMsg = (msg) => [{ required: true, message: msg, trigger: 'blur' }]
const rules = {
  title: requireMsg('请填写需求标题'),
  productName: requireMsg('请填写产品名称'),
  category: requireMsg('请选择产品类别'),
  partRevision: requireMsg('请填写图号/版本'),
  quantity: requireMsg('请填写数量'),
  material: requireMsg('请填写材料牌号'),
  generalTolerance: requireMsg('请选择一般公差'),
  tolerance: requireMsg('请填写关键公差'),
  roughness: requireMsg('请选择粗糙度'),
  surfaceTreatment: requireMsg('请填写表面处理'),
  heatTreatment: requireMsg('请填写热处理'),
  inspectMode: requireMsg('请选择检验方式'),
  certList: [{ type: 'array', required: true, min: 1, message: '请选择认证要求', trigger: 'change' }],
  deadlineHard: requireMsg('请选择硬交期'),
  deliveryAddress: requireMsg('请填写交付地址'),
  packaging: requireMsg('请填写包装要求'),
}
const form = reactive({
  title: '', productName: '', category: '机加', partRevision: '',
  quantity: null, material: '', generalTolerance: 'ISO 2768-m', tolerance: '',
  roughness: '3.2', surfaceTreatment: '', heatTreatment: '',
  aql: '1.0', inspectMode: 'AQL', certList: [],
  minYield: null, minCreditScore: 60, deadlineHard: '',
  deliveryAddress: '', packaging: '', intentionDays: 5, remark: '', fileName: '',
  processes: [{ processNo: 1, processName: '', requirement: '' }],
})
const deliveryTimes = ref(1)
const deliveryPlan = ref([])

const canEditDemand = computed(() => demand.value.status === 'RETURNED')
const needAql = computed(() => form.inspectMode === 'AQL')
const needFull = computed(() => form.inspectMode === 'FULL')
const namedProcesses = computed(() => (processes.value || []).filter(p => p.processName && p.processName !== '整单'))
const showFactoryQuotes = computed(() => ['SOLUTION_GENERATED', 'SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(demand.value.status))
const showCoverage = computed(() => ['PUBLISHED', 'FACTORY_THINKING', 'BUYER_THINKING', 'THINKING', 'LOCKING'].includes(demand.value.status))
const canCloseSolution = computed(() => ['SOLUTION_GENERATED', 'SOLUTION_CONFIRMED'].includes(demand.value.status))
const showFulfillment = computed(() => ['SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(demand.value.status))
const showContracts = computed(() => showFulfillment.value)
const showAllocQty = computed(() => ['SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(demand.value.status))
const order = ref({})
const contractList = ref([])
const upFile = ref(null)
const uploadList = ref([])
const uploadKey = ref(0)
const signRead = ref(false)
const signData = ref('')
const upOpen = ref(false)
const signOpen = ref(false)
const current = ref(null)
const surveyOpen = ref(false)
const surveyStage = ref(null)
const overall = ref(5)
const progOpen = ref(false)
const progLogs = ref([])
const progStage = ref(null)
const inspOpen = ref(false)
const inspReport = ref({})
const decideOpen = ref(false)
const decideStage = ref(null)
const reworkHours = ref(24)
const stageList = ref([])

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
const flowStep = computed(() => statusIndex(demand.value.status))
const stepActive = computed(() => demand.value.status === 'COMPLETED' ? 6 : flowStep.value)
function reached(n) {
  return flowStep.value >= n
}
const factoryCombo = computed(() => {
  const map = new Map()
  for (const row of order.value.combo || []) {
    const key = row.factoryId ?? row.factoryName
    const cur = map.get(key)
    if (!cur) {
      map.set(key, {
        factoryId: row.factoryId,
        factoryName: row.factoryName,
        minQty: row.minQty,
        maxQty: row.maxQty,
        quantity: Number(row.quantity) || 0,
        price: Number(row.price) || 0,
      })
      continue
    }
    cur.quantity += Number(row.quantity) || 0
    cur.price += Number(row.price) || 0
    if (row.minQty != null) cur.minQty = cur.minQty == null ? row.minQty : Math.min(cur.minQty, row.minQty)
    if (row.maxQty != null) cur.maxQty = cur.maxQty == null ? row.maxQty : Math.max(cur.maxQty, row.maxQty)
  }
  return [...map.values()]
})
function allocQtyOf(row) {
  if (row.allocQty != null) return row.allocQty + ' 件'
  const hit = factoryCombo.value.find(c =>
    (row.tenantId != null && c.factoryId === row.tenantId) || c.factoryName === row.factoryName)
  return hit?.quantity != null ? hit.quantity + ' 件' : '-'
}
function allocQtyText(row) {
  const g = confirmedPlan.value.groups.find(x => x.factoryId === row.id)
  if (g?.quantity != null) return g.quantity + ' 件'
  return '-'
}
function bidStageText(s) {
  return ({ INTENTION: '已报名', THINKING: '思考中', QUOTED: '已报价', CANCELLED: '已取消' })[s] || s || '-'
}
const inspJson = computed(() => {
  try { return inspReport.value.reportJson ? JSON.parse(inspReport.value.reportJson) : {} } catch { return {} }
})
const allContractsUploaded = computed(() => {
  const list = contractList.value || []
  return list.length > 0 && list.every(c => c.status === 'SIGNED' || c.attachmentId)
})
const buyerAllSigned = computed(() => {
  const list = (contractList.value || []).filter(c => c.status !== 'SIGNED')
  return list.length > 0 && list.every(c => c.buyerSign && String(c.buyerSign).trim())
})
const canUnifySign = computed(() => allContractsUploaded.value && (contractList.value || []).some(c => c.status !== 'SIGNED' && !(c.buyerSign && String(c.buyerSign).trim())))
const canConfirmDispatch = computed(() => {
  const list = contractList.value || []
  return list.length > 0 && list.every(c => c.status === 'SIGNED' || (c.buyerSign && c.factorySign))
    && list.some(c => c.status !== 'SIGNED')
})
const fulfillHint = computed(() => {
  if (!showFulfillment.value) return ''
  const list = contractList.value || []
  const needFile = list.filter(c => c.status !== 'SIGNED' && !c.attachmentId).length
  if (needFile) return '请先为每个工厂分别上传合同（下发后不可更换），再统一签名'
  const needSign = list.filter(c => c.status !== 'SIGNED' && !(c.buyerSign && String(c.buyerSign).trim())).length
  if (needSign) return '各厂合同已下发，请点「签名」一次签完'
  if (canConfirmDispatch.value) return '各厂已签字提交到本页，请确认全部签署后开始派单'
  const need = list.filter(c => c.status !== 'SIGNED').length
  if (need) return '有 ' + need + ' 份合同待工厂签署，签完后将提交到本页'
  const pay = (stageList.value || []).filter(s => s.status === 'PASS' && (s.escrowStatus === 'NONE' || s.escrowStatus === 'PENDING_PAY'))
  if (pay.length) return '有 ' + pay.length + ' 笔阶段款待支付托管'
  const wait = (stageList.value || []).filter(s => s.status === 'FAIL')
  if (wait.length) return '有 ' + wait.length + ' 个工单待处理质检结果（让步 / 返工 / 关闭）'
  if (order.value.status === 'IN_PRODUCTION') return '生产进行中，可在下方查看各厂进度、质检与托管'
  if (order.value.status === 'COMPLETED') return '订单已完成'
  return ''
})
function contractStatusText(s) {
  return ({ DRAFT: '待上传/签署', PENDING_REVIEW: '待你确认派单', SIGNED: '已确认派单' })[s] || s || '-'
}
function isSignImg(v) {
  return typeof v === 'string' && v.startsWith('data:image')
}
function overdueDays(row) {
  if (!row?.promisedDate) return 0
  if (['PASS', 'FAIL', 'CLOSED', 'COMPLETED'].includes(row.status) || row.escrowStatus === 'SETTLED') return 0
  const d = Math.ceil((Date.now() - new Date(row.promisedDate).getTime()) / 86400000)
  return d > 0 ? d : 0
}
const estimatedTotal = computed(() => Number(quoteStats.value.estimatedTotal ?? demand.value.estimatedTotal) || 0)
const weightedUnit = computed(() => Number(quoteStats.value.weightedUnit) || 0)
const deliveryRows = computed(() => {
  try {
    const arr = JSON.parse(demand.value.deliveryPlanJson || '[]')
    if (!Array.isArray(arr)) return []
    return arr.map((x) => {
      if (x == null) return { percent: '-', startAt: '-', endAt: '-' }
      let percent = '-'
      if (x.percent != null && Number(x.percent) > 0) percent = Number(x.percent) + '%'
      else if (String(x.text || '').includes('%')) percent = x.text
      else if (x.qty != null && Number(x.qty) > 0 && Number(x.qty) <= 100) percent = Number(x.qty) + '%'
      return {
        percent,
        startAt: String(x.startAt || '').slice(0, 10) || '-',
        endAt: String(x.endAt || '').slice(0, 10) || '-',
      }
    })
  } catch {
    return []
  }
})
const extra = computed(() => parseJson(demand.value.extraJson))
const showStages = computed(() => ['CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(demand.value.status) && (stageList.value || []).length > 0)
const progressPercent = computed(() => {
  const list = stageList.value || []
  if (!list.length) return 0
  const sum = list.reduce((s, r) => s + (r.actualProgress || 0), 0)
  return Math.round(sum / list.length)
})
const factoryStageGroups = computed(() => {
  const map = new Map()
  for (const s of stageList.value || []) {
    const key = s.tenantId ?? s.factoryName
    if (!map.has(key)) {
      map.set(key, { key, factoryName: s.factoryName, tenantId: s.tenantId, children: [], totalQty: 0, totalAmount: 0, escrowHeld: 0, progress: 0 })
    }
    const g = map.get(key)
    g.children.push(s)
    g.totalQty += Number(s.quantity) || 0
    g.totalAmount += Number(s.amount) || 0
    if (s.escrowStatus === 'HELD' || s.escrowStatus === 'SETTLED') {
      g.escrowHeld += Number(s.payAmount ?? s.amount) || 0
    }
  }
  for (const g of map.values()) {
    const n = g.children.length || 1
    g.progress = Math.round(g.children.reduce((s, r) => s + (r.actualProgress || 0), 0) / n)
  }
  return [...map.values()]
})
const buyerDeposit = computed(() => {
  const t = estimatedTotal.value
  return t > 0 ? (t * 0.05) : 0
})
const demandNeed = computed(() => Number(demand.value.quantity) || 0)
const aiSolutions = computed(() => (solutions.value || []).filter(s => (s.type || '').startsWith('AI')))
const factoryQuoteList = computed(() => {
  const out = []
  const seen = new Set()
  for (const f of factories.value) {
    if (seen.has(f.id)) continue
    seen.add(f.id)
    const q = (f.quotes || []).find(x => x.unitPrice != null) || (f.quotes || [])[0] || {}
    out.push({ factoryId: f.id, factoryName: f.name, unitPrice: q.unitPrice, minQty: f.minQty ?? q.minQty, maxQty: f.maxQty ?? q.maxQty })
  }
  return out
})
const allocSum = computed(() => customLines.value.reduce((s, l) => s + (Number(l.quantity) || 0), 0))
const customTotal = computed(() => {
  let total = 0
  let any = false
  for (const line of customLines.value) {
    const amt = customLineAmount(line)
    if (amt != null) { total += amt; any = true }
  }
  return any ? Math.round(total * 100) / 100 : null
})
const planPercentSum = computed(() => deliveryPlan.value.reduce((s, r) => s + (Number(r.percent) || 0), 0))

function money(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}
function moneyText(n) {
  if (n == null || !Number.isFinite(n)) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function factoryUnitPrice(row) {
  const q = (row.quotes || []).find(x => x.unitPrice != null) || (row.quotes || [])[0]
  if (q?.unitPrice == null) return '-'
  return '¥' + q.unitPrice + '/件'
}
function parseCombo(json) {
  try {
    const v = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(v) ? v : []
  } catch { return [] }
}
function factoryAllocRows(json) {
  const map = new Map()
  for (const it of parseCombo(json)) {
    const fid = it.factoryId
    if (fid == null) continue
    const qty = Number(it.quantity) || 0
    const unit = Number(it.unitPrice)
    if (!map.has(fid)) {
      map.set(fid, { factoryId: fid, factoryName: it.factoryName, unitPrice: Number.isFinite(unit) ? unit : null, quantity: qty, minQty: it.minQty, maxQty: it.maxQty })
    } else {
      const row = map.get(fid)
      if (qty > row.quantity) row.quantity = qty
    }
  }
  return [...map.values()].map(r => ({
    ...r,
    price: r.unitPrice != null && r.quantity ? Math.round(r.unitPrice * r.quantity * 100) / 100 : null,
  }))
}
function comboTotal(json) {
  const rows = factoryAllocRows(json)
  if (!rows.length) return null
  return Math.round(rows.reduce((s, r) => s + (Number(r.price) || 0), 0) * 100) / 100
}
function parseRationale(raw) {
  if (!raw) return { rationale: '', risks: [], milestones: [] }
  try {
    const o = JSON.parse(raw)
    if (o && typeof o === 'object' && (o.rationale != null || o.risks)) {
      return { rationale: o.rationale || '', risks: Array.isArray(o.risks) ? o.risks : [], milestones: Array.isArray(o.milestones) ? o.milestones : [] }
    }
  } catch { /* 旧数据 */ }
  return { rationale: raw, risks: [], milestones: [] }
}
function quoteOf(factoryId) {
  return factoryQuoteList.value.find(f => f.factoryId === factoryId)
}
function usedFactory(factoryId, idx) {
  return customLines.value.some((l, i) => i !== idx && l.factoryId === factoryId)
}
function rangeText(factoryId) {
  const q = quoteOf(factoryId)
  if (!q) return '-'
  return (q.minQty ?? '-') + ' ~ ' + (q.maxQty ?? '-')
}
function customLineAmount(line) {
  const q = quoteOf(line.factoryId)
  const price = Number(q?.unitPrice)
  const qty = Number(line.quantity)
  if (!Number.isFinite(price) || !Number.isFinite(qty) || qty <= 0) return null
  return Math.round(price * qty * 100) / 100
}
function addLine() {
  customLines.value.push({ factoryId: null, quantity: null })
}
function removeLine(i) {
  if (customLines.value.length <= 1) {
    ElMessage.warning('至少保留一行')
    return
  }
  customLines.value.splice(i, 1)
}

const confirmedPlan = computed(() => {
  const st = demand.value.status
  if (!['SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(st)) {
    return { groups: [], total: 0, hint: '' }
  }
  const sols = solutions.value || []
  const picked = sols.find(s => Number(s.isFinal) === 1)
    || sols.find(s => Number(s.final) === 1)
    || sols.find(s => s.source === 'FINAL')
    || sols.find(s => (s.type || '') === 'CUSTOM')
  if (!picked) return { groups: [], total: 0, hint: '' }
  const groups = factoryAllocRows(picked.finalComboJson)
  const total = groups.reduce((s, g) => s + (Number(g.price) || 0), 0)
  const kind = (picked.type || '').startsWith('AI') ? '已按推荐方案确认' : '已按自选分配确认'
  return { groups, total, hint: kind + '。确认后不可再改。' }
})

function parseJson(raw) {
  try { return raw ? JSON.parse(raw) : {} } catch { return {} }
}

function todayStr() {
  const today = new Date()
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
}
function evenPercents(times) {
  const n = Math.max(1, times)
  const base = Math.floor(100 / n)
  const rest = 100 - base * n
  return Array.from({ length: n }, (_, i) => base + (i === n - 1 ? rest : 0))
}
function blankPeriod(prev = {}, percent = 100) {
  return {
    percent: Math.min(100, Math.max(1, Number(percent) || Number(prev.percent) || 1)),
    startAt: prev.startAt || '',
    endAt: prev.endAt || '',
  }
}
function chainPeriodStarts() {
  const rows = deliveryPlan.value
  for (let i = 1; i < rows.length; i++) rows[i].startAt = rows[i - 1].endAt || ''
}
function applyHardOnLast() {
  const rows = deliveryPlan.value
  const hard = form.deadlineHard || ''
  if (!rows.length || !hard) return
  rows[rows.length - 1].endAt = hard
}
function fillEmptyPeriodDates() {
  const rows = deliveryPlan.value
  if (!rows.length) return
  if (!rows[0].startAt) rows[0].startAt = todayStr()
  chainPeriodStarts()
  applyHardOnLast()
}
function onPeriodEndChange(i) {
  if (i === deliveryPlan.value.length - 1) { applyHardOnLast(); return }
  chainPeriodStarts()
}
function setTimes(n) {
  const oldTimes = deliveryTimes.value
  const times = Math.min(10, Math.max(1, Number(n) || 1))
  deliveryTimes.value = times
  const kept = deliveryPlan.value || []
  const percents = evenPercents(times)
  deliveryPlan.value = Array.from({ length: times }, (_, i) => blankPeriod(kept[i], percents[i]))
  if (times > oldTimes) {
    const formerLast = deliveryPlan.value[oldTimes - 1]
    if (formerLast && formerLast.endAt && formerLast.endAt === form.deadlineHard) formerLast.endAt = ''
  }
  fillEmptyPeriodDates()
}
function bumpPercent(i, delta) {
  const row = deliveryPlan.value[i]
  if (!row) return
  row.percent = Math.min(100, Math.max(1, (Number(row.percent) || 1) + delta))
}
function clampPercent(i) {
  const row = deliveryPlan.value[i]
  if (!row) return
  const n = Number(row.percent)
  row.percent = Number.isFinite(n) ? Math.min(100, Math.max(1, Math.round(n))) : 1
}
function onInspectMode(mode) {
  if (mode !== 'AQL') {
    form.aql = ''
    if (form.minYield == null) form.minYield = 0.97
  } else {
    form.aql = form.aql || '1.0'
    form.minYield = null
  }
}
function addProcess() {
  form.processes.push({ processNo: form.processes.length + 1, processName: '', requirement: '' })
}
async function removeProcess(i) {
  if (form.processes.length <= 1) {
    ElMessage.warning('至少保留 1 道工序')
    return
  }
  form.processes.splice(i, 1)
}
function onEditFile(e) {
  editFile.value = e.target.files?.[0] || null
  form.fileName = editFile.value ? editFile.value.name : (form.fileName || '')
}
function dateOnly(v) {
  if (!v) return ''
  return String(v).slice(0, 10)
}
function periodPercentOf(x, fallback = 1) {
  if (x == null) return fallback
  if (typeof x === 'number') {
    const n = Math.round(x)
    return n >= 1 && n <= 100 ? n : fallback
  }
  if (x.percent != null && Number(x.percent) > 0) return Math.min(100, Math.max(1, Number(x.percent)))
  return fallback
}
function fillEditForm(view) {
  const d = view.demand || {}
  const extraObj = parseJson(d.extraJson)
  form.title = d.title || ''
  form.productName = d.productName || ''
  form.category = d.category || '机加'
  form.partRevision = d.partRevision || ''
  form.quantity = d.quantity
  form.material = d.material || ''
  form.generalTolerance = d.generalTolerance || 'ISO 2768-m'
  form.tolerance = d.tolerance || ''
  form.roughness = extraObj.roughness || '3.2'
  form.surfaceTreatment = d.surfaceTreatment || ''
  form.heatTreatment = extraObj.heatTreatment || ''
  form.inspectMode = String(d.inspectMode || 'AQL').toUpperCase().includes('FULL') ? 'FULL' : 'AQL'
  form.aql = form.inspectMode === 'AQL' ? (d.aql || '1.0') : ''
  form.certList = (d.certification || '').split(',').filter(Boolean)
  form.minYield = form.inspectMode === 'FULL' ? (d.minYield ?? 0.97) : null
  form.minCreditScore = d.minCreditScore
  form.deadlineHard = dateOnly(d.deadlineHard)
  form.deliveryAddress = d.deliveryAddress || ''
  form.packaging = d.packaging || ''
  form.intentionDays = d.intentionDays || 5
  form.remark = d.remark || ''
  try {
    const plan = JSON.parse(d.deliveryPlanJson || '[]')
    const items = Array.isArray(plan) ? plan : []
    setTimes(d.deliveryTimes || 1)
    deliveryPlan.value = deliveryPlan.value.map((row, i) => {
      const x = items[i]
      if (x == null) return row
      return { percent: periodPercentOf(x, row.percent), startAt: dateOnly(x.startAt) || row.startAt, endAt: dateOnly(x.endAt) || row.endAt }
    })
    fillEmptyPeriodDates()
  } catch { setTimes(d.deliveryTimes || 1) }
  const named = (view.processes || []).filter(p => p.processName && p.processName !== '整单')
  form.processes = named.length >= 1 ? named.map(p => ({ processNo: p.processNo, processName: p.processName, requirement: p.requirement || '' })) : form.processes
  const att = (view.attachments || [])[0]
  existingAttachmentId.value = att?.id || null
  form.fileName = att?.fileName || ''
  editFile.value = null
}
function firstError() {
  if (!form.title?.trim()) return '请填写需求标题'
  if (!form.productName?.trim()) return '请填写产品名称'
  if (!form.partRevision?.trim()) return '请填写图号/版本'
  if (!form.quantity || form.quantity < 1) return '请填写数量'
  if (!form.material?.trim()) return '请填写材料牌号'
  if (!form.tolerance?.trim()) return '请填写关键公差'
  if (!form.surfaceTreatment?.trim()) return '请填写表面处理'
  if (!form.heatTreatment?.trim()) return '请填写热处理'
  if (!form.certList?.length) return '请选择认证要求'
  if (form.inspectMode === 'AQL' && !form.aql) return '请填写 AQL'
  if (form.inspectMode === 'FULL' && (form.minYield == null || form.minYield === '')) return '全检请填写最低良率'
  if (!form.deadlineHard) return '请选择硬交期'
  if (!form.deliveryAddress?.trim()) return '请填写交付地址'
  if (!form.packaging?.trim()) return '请填写包装要求'
  if (planPercentSum.value !== 100) return '各期交付比例之和必须为 100%'
  if (!(form.processes || []).filter(p => p.processName?.trim()).length) return '请至少填写 1 道工序'
  if (!editFile.value && !existingAttachmentId.value) return '请上传图纸或文档'
  return ''
}
function buildBody(attachmentId) {
  return {
    title: form.title, productName: form.productName, category: form.category, quantity: form.quantity,
    material: form.material, tolerance: form.tolerance, surfaceTreatment: form.surfaceTreatment,
    inspectMode: form.inspectMode === 'FULL' ? 'FULL' : 'AQL',
    aql: form.inspectMode === 'AQL' ? form.aql : null,
    certification: (form.certList || []).join(','),
    minYield: form.inspectMode === 'FULL' ? form.minYield : null,
    minCreditScore: form.minCreditScore, deadlineHard: form.deadlineHard, deadlineFlexible: null,
    deliveryAddress: form.deliveryAddress, packaging: form.packaging, multiProcess: 1,
    intentionDays: form.intentionDays, remark: form.remark, generalTolerance: form.generalTolerance,
    partRevision: form.partRevision,
    extraJson: JSON.stringify({ roughness: form.roughness, heatTreatment: form.heatTreatment }),
    attachmentId, processes: form.processes, sourceDemandId: demand.value.sourceDemandId,
    deliveryTimes: deliveryTimes.value,
    deliveryPlan: deliveryPlan.value.map(r => ({ percent: Number(r.percent) || 0, text: (Number(r.percent) || 0) + '%', startAt: r.startAt, endAt: r.endAt })),
  }
}
async function submitRepublish() {
  const err = firstError()
  if (err) { ElMessage.warning(err); return }
  saving.value = true
  try {
    let attachmentId = existingAttachmentId.value
    if (editFile.value) {
      const fd = new FormData()
      fd.append('file', editFile.value)
      fd.append('bizType', 'DEMAND')
      const up = await api.post('/file/upload', fd)
      attachmentId = up?.id ?? up
    }
    await republish(demand.value.id, buildBody(attachmentId))
    ElMessage.success('已提交审核，通过后进入意向期')
    load()
  } finally { saving.value = false }
}
async function cancelDraft() {
  const box = await ElMessageBox.prompt('取消后需求作废。请填写原因。', '取消订单', {
    confirmButtonText: '确认取消', cancelButtonText: '再想想',
    inputPlaceholder: '必填', inputValidator: (v) => !!String(v || '').trim() || '请填写理由',
  })
  await cancelDemand(demand.value.id, String(box.value).trim())
  ElMessage.success('已取消')
  load()
}

async function decide(action) {
  let reason
  if (action === 'CANCEL') {
    const box = await ElMessageBox.prompt('请填写取消理由', '思考期取消', {
      confirmButtonText: '提交', cancelButtonText: '再想想',
      inputPlaceholder: '必填', inputValidator: (v) => !!String(v || '').trim() || '请填写理由',
    })
    reason = String(box.value).trim()
  }
  await decideDemand(demand.value.id, action, reason)
  ElMessage.success('操作成功')
  load()
}
async function buyerContinue() {
  await ElMessageBox.confirm(
    `继续将冻结保证金 ￥${money(buyerDeposit.value)}（预估总价 ￥${money(estimatedTotal.value)} 的 5%），该保证金在尾款支付时抵扣。确定继续？`,
    '继续并交保证金', { type: 'warning', confirmButtonText: '确认冻结并继续', cancelButtonText: '再想想' },
  )
  await buyerDecideApi(demand.value.id, 'CONTINUE')
  ElMessage.success('保证金已冻结，AI 正在生成方案')
  load()
}
async function buyerCancel() {
  const box = await ElMessageBox.prompt('取消后所有工厂的意向金/保证金全额退回。请填写取消理由。', '取消需求', {
    confirmButtonText: '确认取消', cancelButtonText: '再想想',
    inputPlaceholder: '必填', inputValidator: (v) => !!String(v || '').trim() || '请填写理由',
  })
  await buyerDecideApi(demand.value.id, 'CANCEL', String(box.value).trim())
  ElMessage.success('已取消，相关资金已退回')
  load()
}
async function closeOrder() {
  await ElMessageBox.confirm(
    '关闭后将扣除买家保证金 50%，按各厂承接区间最高值比重赔偿工厂，剩余保证金退回。确定关闭？',
    '关闭订单', { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '再想想' },
  )
  await closeSolution(demand.value.id)
  ElMessage.success('订单已关闭')
  load()
}
async function selectAi(s) {
  await ElMessageBox.confirm('将按该推荐方案进入合同签署，此后不可再改。', '确认方案', { type: 'warning' })
  await selectSolution(demand.value.id, s.id)
  ElMessage.success('已确认，请按厂上传并签署合同')
  load()
}
async function confirmCustom() {
  const lines = customLines.value.filter(l => l.factoryId && Number(l.quantity) > 0)
  if (!lines.length) { ElMessage.warning('请选择工厂并填写分配件数'); return }
  if (allocSum.value !== demandNeed.value) {
    ElMessage.warning(`已分配 ${allocSum.value} 件，须等于需求 ${demandNeed.value} 件`)
    return
  }
  const items = []
  for (const l of lines) {
    const q = quoteOf(l.factoryId)
    const qty = Number(l.quantity)
    if (q?.minQty != null && qty < q.minQty) { ElMessage.warning(`「${q.factoryName}」分配低于最小承接量 ${q.minQty}`); return }
    if (q?.maxQty != null && qty > q.maxQty) { ElMessage.warning(`「${q.factoryName}」分配超过最大承接量 ${q.maxQty}`); return }
    items.push({ factoryId: l.factoryId, quantity: qty })
  }
  await ElMessageBox.confirm('确认后将按自选分配进入合同签署，此后不可再改。', '确认自选方案', { type: 'warning' })
  savingCustom.value = true
  try {
    const s = await saveCustom(demand.value.id, items)
    await selectSolution(demand.value.id, s.id)
    ElMessage.success('已确认，请按厂上传并签署合同')
    load()
  } finally { savingCustom.value = false }
}

async function openProgress(row) {
  progStage.value = row
  progLogs.value = await progressLog(row.id)
  progOpen.value = true
}
async function openInsp(row) {
  inspReport.value = (await inspectionOf(row.id)) || {}
  inspOpen.value = true
}
function openDecide(row) {
  decideStage.value = row
  reworkHours.value = 24
  decideOpen.value = true
}
async function doDecide(action) {
  if (action === 'REWORK' && (reworkHours.value < 12 || reworkHours.value > 72)) {
    return ElMessage.warning('返工期限须为 12～72 小时')
  }
  await decideInspect(decideStage.value.id, { action, reworkHours: reworkHours.value })
  ElMessage.success(action === 'CONCESSION' ? '已让步，请支付本阶段托管款' : (action === 'REWORK' ? '已通知工厂返工' : '本段已关闭'))
  decideOpen.value = false
  load()
}
async function cancelFulfillment() {
  await ElMessageBox.confirm('签署期内取消将扣除全部保证金，按方案零件件数比重赔偿参与工厂。确定取消？', '取消订单', { type: 'warning', confirmButtonText: '再确认一次', cancelButtonText: '返回' })
  await ElMessageBox.confirm('请再次确认：此操作不可撤销，将扣除全部保证金并赔偿工厂。', '二次确认', { type: 'warning', confirmButtonText: '确认取消订单', cancelButtonText: '返回' })
  await cancelByBuyer(order.value.id)
  ElMessage.success('订单已取消')
  load()
}
async function openContractFile(id) {
  try { await downloadAttachment(id) } catch (e) { ElMessage.error(e.message || '无法打开合同') }
}
function resetUpload() {
  upFile.value = null
  uploadList.value = []
}
function openUpload(row) {
  resetUpload()
  current.value = row
  uploadKey.value += 1
  upOpen.value = true
}
function openUnifySign() {
  if (!allContractsUploaded.value) { ElMessage.warning('请先为每个工厂分别上传合同'); return }
  signRead.value = false
  signData.value = ''
  signOpen.value = true
}
function onFile(f, list) {
  upFile.value = f?.raw || null
  uploadList.value = list ? list.slice(-1) : []
}
function onUploadRemove() {
  upFile.value = null
  uploadList.value = []
}
function onUploadExceed(files) {
  const f = files?.[0]
  upFile.value = f || null
  uploadList.value = f ? [{ name: f.name, uid: Date.now(), raw: f, status: 'ready' }] : []
}
async function doUpload() {
  const up = await uploadFile(upFile.value, 'CONTRACT')
  await uploadContract(order.value.id, up.id, current.value.tenantId)
  ElMessage.success('已上传「' + (current.value.factoryName || '该厂') + '」合同')
  resetUpload()
  upOpen.value = false
  await load()
}
async function confirmAllSigned() {
  await ElMessageBox.confirm('确认各厂均已完成合同签署后开始派单生产。此操作不可撤销。', '确认签署并派单', { type: 'warning' })
  await confirmDispatch(order.value.id)
  ElMessage.success('已确认签署，开始派单生产')
  load()
}
async function doSign() {
  await buyerSign(order.value.id, true, signData.value)
  ElMessage.success('已成功签名')
  signOpen.value = false
  load()
}
async function pay(row) {
  const res = await payStage(row.id)
  const data = res?.data || res
  if (data?.payUrl) {
    window.open(data.payUrl, '_blank')
    ElMessage.success(data.message || '已打开支付宝沙箱，付完后刷新本页')
  } else {
    ElMessage.success(data?.message || '已托管到平台，工厂余额不变')
  }
  load()
}
async function payFee(row) {
  await payInspectFee(row.id)
  ElMessage.success('质检费已支付，等待质检')
  load()
}
async function doAccept() {
  await accept(order.value.id)
  ElMessage.success('完工确认完成，已结算；佣金已从托管工钱扣除')
  load()
}
function openSurvey(row) {
  surveyStage.value = row
  overall.value = 5
  surveyOpen.value = true
}
async function doSurvey() {
  await submitSurvey(surveyStage.value.id, { overall: overall.value })
  ElMessage.success('评价已提交')
  surveyOpen.value = false
  load()
}
function isTextFile(row, fileName) {
  const t = (row.fileType || '').toLowerCase()
  const n = (fileName || row.fileName || '').toLowerCase()
  return t === 'md' || n.endsWith('.md') || t === 'txt' || n.endsWith('.txt')
}
async function viewFile(row) {
  try {
    const { blob, fileName } = await fetchAttachment(row.id)
    if (isTextFile(row, fileName)) {
      fileTitle.value = fileName || row.fileName
      fileText.value = await blob.text()
      fileOpen.value = true
    } else {
      saveBlob(blob, fileName || row.fileName)
    }
  } catch (e) {
    ElMessage.error(e.message || '无法打开文件')
  }
}

async function load() {
  loading.value = true
  try {
    const view = await getDetail(route.params.id)
    demand.value = view.demand || {}
    quoteStats.value = view.quoteStats || {}
    processes.value = view.processes || []
    attachments.value = view.attachments || []
    if (demand.value.status === 'SOLUTION_CONFIRMED') {
      await api.post(`/order/dispatch/${route.params.id}`)
      const again = await getDetail(route.params.id)
      demand.value = again.demand || {}
      quoteStats.value = again.quoteStats || {}
      processes.value = again.processes || []
      attachments.value = again.attachments || []
    }
    if (demand.value.status === 'RETURNED') fillEditForm(view)
    if (showCoverage.value) {
      const cov = await getCoverage(route.params.id)
      coverage.value = cov.processes || []
    } else {
      coverage.value = []
    }
    try { cancelStats.value = await getCancelStats() } catch { /* ignore */ }
    try {
      const sols = await listByDemand(route.params.id)
      solutions.value = sols || []
      hasActiveSolution.value = (sols || []).length > 0
    } catch {
      solutions.value = []
      hasActiveSolution.value = false
    }
    try {
      order.value = {}
      contractList.value = []
      stageList.value = []
      if (showFulfillment.value || ['CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(demand.value.status)) {
        const orders = await listOrders()
        const hit = (orders || []).find(o => String(o.demandId) === String(route.params.id))
        if (hit?.id) {
          order.value = await getOrder(hit.id)
          try { contractList.value = await listContracts(hit.id) } catch { contractList.value = [] }
          try { stageList.value = await listStages(hit.id) } catch { stageList.value = [] }
        }
      }
    } catch {
      order.value = {}
      contractList.value = []
      stageList.value = []
    }
    try { factories.value = await listBidFactories(route.params.id) } catch { factories.value = [] }
    customLines.value = [{ factoryId: null, quantity: null }]
  } finally {
    loading.value = false
  }
}

function openFactory(row) {
  factoryInfo.value = row || {}
  factoryOpen.value = true
}
async function openFactoryById(id) {
  const hit = (factories.value || []).find(f => f.id === id)
  if (hit) {
    factoryInfo.value = hit
    factoryOpen.value = true
    return
  }
  factoryInfo.value = await api.get(`/enterprise/${id}/public-profile`)
  factoryOpen.value = true
}

watch(() => form.deadlineHard, () => { if (canEditDemand.value) fillEmptyPeriodDates() })
watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped>
.sign-img { display: block; max-height: 48px; margin-top: 6px; border: 1px solid #ebeef5; background: #fff; }
.spec-tabs { margin-bottom: 16px; }
.phase { margin-top: 16px; }
.phase-actions { margin-top: 12px; }
.deposit-box { margin-top: 10px; padding: 10px 12px; background: #fdf6ec; border-radius: 6px; color: #b88230; font-size: 13px; }
.cov-label { font-size: 13px; color: #303133; margin-bottom: 6px; }
h4 { margin: 16px 0 8px; }
.file-preview { white-space: pre-wrap; word-break: break-word; margin: 0; font-size: 13px; line-height: 1.6; }
.hint { color: #909399; font-size: 12px; }
.meta { color: #909399; font-size: 12px; }
.confirmed-plan { margin-top: 16px; }
.proc-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.proc-title { font-weight: 600; font-size: 13px; color: #303133; }
.alloc-status { font-size: 12px; }
.alloc-status.ok { color: #67c23a; }
.alloc-status.bad { color: #f56c6c; }
.line { display: flex; align-items: center; gap: 8px; flex-wrap: nowrap; margin-bottom: 8px; }
.line.head { color: #909399; font-size: 12px; margin-bottom: 4px; }
.col-del { width: 28px; flex: 0 0 28px; }
.col-fac { flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-range { flex: 0 0 100px; width: 100px; color: #909399; font-size: 12px; white-space: nowrap; }
.col-qty { flex: 0 0 110px; width: 110px; text-align: right; }
.col-amt { flex: 0 0 100px; width: 100px; text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
.line :deep(.el-select) { width: 100%; }
.line :deep(.el-input-number) { width: 110px; }
.total-bar {
  display: flex; align-items: center; justify-content: flex-end; gap: 12px;
  margin: 8px 0 0; padding: 10px 12px; background: #f5f7fa; border-radius: 6px;
  font-size: 13px; color: #606266;
}
.total-bar strong { font-size: 16px; color: #303133; font-variant-numeric: tabular-nums; }
.card-head { display: flex; justify-content: flex-end; margin-bottom: 8px; }
.row { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
.row.head { color: #606266; font-size: 12px; margin-bottom: 8px; }
.col-name { width: 160px; flex-shrink: 0; }
.col-req { flex: 1; min-width: 180px; }
.proc-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.step {
  display: inline-flex; align-items: stretch; border: 1px solid #dcdfe6; border-radius: 4px; overflow: hidden; width: 150px; height: 32px;
}
.step-btn { width: 32px; border: 0; background: #f5f7fa; cursor: pointer; font-size: 16px; line-height: 32px; }
.step-num { flex: 1; text-align: center; line-height: 32px; border-left: 1px solid #dcdfe6; border-right: 1px solid #dcdfe6; background: #fff; }
.step-input { flex: 1; width: 0; min-width: 56px; height: 32px; border: 0; border-left: 1px solid #dcdfe6; border-right: 1px solid #dcdfe6; text-align: center; outline: none; background: #fff; }
.period-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; width: 100%; }
.qty-wrap { display: inline-flex; align-items: center; gap: 8px; }
.qty-unit { color: #606266; font-size: 14px; }
.inspect-modes { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
</style>
