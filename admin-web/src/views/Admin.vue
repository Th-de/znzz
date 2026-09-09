<template>
  <div class="shell">
    <AppHeader
      :items="menuItems"
      :active="active"
      :router-mode="false"
      :role-label="isInspector ? '质检端' : '运营端'"
      show-password
      upload-avatar
      @select="active = $event"
      @logout="logout"
      @password="pwdOpen = true"
    />
    <div class="app-body">
      <div class="page-shell">
      <!-- 工作台：原需求管理分阶段处理 -->
      <div v-if="active==='overview'">
        <div class="toolbar">
          <span class="title">工作台</span>
          <div>
            <el-button @click="loadDemands">刷新</el-button>
            <el-button @click="logout">退出</el-button>
          </div>
        </div>
        <el-tabs v-model="demandTab">
          <el-tab-pane :label="'申请发布 (' + countGroup('audit') + ')'" name="audit" />
          <el-tab-pane :label="'意向与思考 (' + countGroup('match') + ')'" name="match" />
          <el-tab-pane :label="'方案与合同 (' + countGroup('quote') + ')'" name="quote" />
          <el-tab-pane :label="'生产与质检 (' + countGroup('fulfill') + ')'" name="fulfill" />
          <el-tab-pane :label="'结算 (' + countGroup('close') + ')'" name="close" />
        </el-tabs>
        <PagedBox :data="filteredDemands" v-slot="{ rows }">
        <el-table :data="rows" border>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="标题" min-width="160" />
          <template v-if="demandTab==='quote'">
            <el-table-column label="开始时间" width="170">
              <template #default="{row}">{{ stageStart(row) }}</template>
            </el-table-column>
            <el-table-column label="当前阶段" width="150">
              <template #default="{row}"><el-tag :type="tagType(row.status)">{{ statusText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{row}">{{ fmtTime(row.updatedAt) }}</template>
            </el-table-column>
          </template>
          <template v-else>
            <el-table-column prop="quantity" label="数量" width="100" />
            <el-table-column prop="status" label="当前状态" width="170">
              <template #default="{row}">
                <el-tag v-if="demandTab==='fulfill' && row.pendingReview" type="danger">待审核</el-tag>
                <el-tag v-else :type="tagType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="提交时间" width="160">
              <template #default="{row}">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="阶段开始" width="160">
              <template #default="{row}">{{ stageStart(row) }}</template>
            </el-table-column>
            <el-table-column label="阶段截止" width="160">
              <template #default="{row}">{{ stageEnd(row) }}</template>
            </el-table-column>
          </template>
          <el-table-column label="可执行操作" :min-width="demandTab==='quote' ? 240 : 420">
            <template #default="{ row }">
              <div class="op-cell">
              <el-button size="small" @click="openDetail(row)">详情</el-button>
              <el-button v-if="demandTab==='quote' && row.status==='SOLUTION_GENERATED'" size="small" type="success" :loading="aiLoading && currentSolDemand?.id===row.id" @click="reviewAi(row)">审核AI方案</el-button>
              <el-button v-if="demandTab==='quote' && ['SOLUTION_CONFIRMED','SOLUTION_SELECTED'].includes(row.status)" size="small" @click="viewSolutions(row)">查看方案</el-button>
              <el-button v-if="row.status==='PENDING_AUDIT'" size="small" type="primary" @click="audit(row,'PASS')">审核通过</el-button>
              <el-button v-if="row.status==='PENDING_AUDIT'" size="small" type="danger" @click="openReturn(row)">退回修改</el-button>
              <el-button v-if="row.status==='PUBLISHED'" size="small" type="warning" @click="endIntention(row)">结束意向期</el-button>
              <el-button v-if="row.status==='FACTORY_THINKING'" size="small" type="warning" @click="endFactoryThinking(row)">结束工厂思考期</el-button>
              <el-button v-if="row.status==='REVIEWING'" size="small" type="primary" @click="review(row,true)">同意取消</el-button>
              <el-button v-if="row.status==='REVIEWING'" size="small" type="danger" @click="review(row,false)">驳回取消</el-button>
              <el-button v-if="row.status==='LOCKING'" size="small" type="warning" @click="endLocking(row)">结束保证金期</el-button>
              <el-tag v-if="['THINKING','BUYER_THINKING','SOLUTION_CONFIRMED','SOLUTION_SELECTED','CONTRACTED','IN_PRODUCTION','COMPLETED','CANCELLED','FLOW_FAILED','RETURNED'].includes(row.status) && !row.pendingReview" type="info" size="small">{{ row.status==='RETURNED' ? '待买家修改' : (row.status==='BUYER_THINKING' ? '等待买家决定' : (row.status==='SOLUTION_SELECTED' || row.status==='SOLUTION_CONFIRMED' ? '等待买家签署合同' : '等待其他角色操作')) }}</el-tag>
              </div>
            </template>
          </el-table-column>
        </el-table>
        </PagedBox>
        <el-alert style="margin-top:10px" type="info" :closable="false"
          title="每次发布先申请发布，运营通过后工厂才能看到。待审核可退回修改，改完须再审。意向期不可退回；买家不能改字段，只能取消后发新单。" />
      </div>

      <div v-if="active==='all'">
        <div class="toolbar">
          <span class="title">全部需求</span>
          <el-button @click="loadDemands">刷新</el-button>
        </div>
        <PagedBox :data="demands" v-slot="{ rows }">
        <el-table :data="rows" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="productName" label="产品" />
          <el-table-column prop="quantity" label="数量" width="100" />
          <el-table-column label="状态" width="140">
            <template #default="{ row }">{{ label(DEMAND_STATUS, row.status) }}</template>
          </el-table-column>
          <el-table-column label="提交时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="发布/意向开始" width="160">
            <template #default="{ row }">{{ fmtTime(row.publishedAt) }}</template>
          </el-table-column>
          <el-table-column label="倒计时" min-width="180">
            <template #default="{ row }">
              <IntentionCountdown v-if="row.status==='PUBLISHED'" :end-at="row.intentionEndAt" />
              <IntentionCountdown v-else-if="row.status==='FACTORY_THINKING'" :end-at="row.factoryThinkingEndAt" />
              <IntentionCountdown v-else-if="row.status==='BUYER_THINKING'" :end-at="row.buyerThinkingEndAt" />
              <IntentionCountdown v-else-if="row.status==='LOCKING'" :end-at="row.lockingEndAt" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        </PagedBox>
      </div>

        <el-drawer v-model="detailOpen" title="需求详情" size="70%" append-to-body>
          <el-alert v-if="detail.demand?.returnReason" type="warning" :closable="false" :title="'退回原因：' + detail.demand.returnReason" style="margin-bottom:12px" />
          <el-steps :active="detailStep" finish-status="success" align-center style="margin-bottom:16px">
            <el-step title="申请发布" />
            <el-step title="意向与思考" />
            <el-step title="方案与合同" />
            <el-step title="生产与质检" />
            <el-step title="结算" />
          </el-steps>
          <div v-if="detailReached(3)" style="margin-bottom:16px">
            <h4>生产与质检 {{ detailProgress }}%</h4>
            <el-progress :percentage="detailProgress" style="margin-bottom:10px" />
            <p class="hint">工单状态含待交质检费、质检中、结算中。返工工单显示在对应工期下一行。运营审核质检员已提交的结果，不代替买家付款。</p>
            <el-table :data="detailStageGroups" border size="small" row-key="key" :expand-row-keys="detailExpandKeys" @expand-change="onDetailExpand">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <el-table :data="row.children" border size="small">
                    <el-table-column label="期数" width="90">
                      <template #default="{ row: p }">{{ p.periodLabel || ('第' + (p.periodNo || '-') + '期') }}</template>
                    </el-table-column>
                    <el-table-column label="数量" width="90">
                      <template #default="{ row: p }">{{ p.quantity != null ? p.quantity + ' 件' : '-' }}</template>
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
                    <el-table-column label="进度" width="150">
                      <template #default="{ row: p }">
                        <el-progress :percentage="p.actualProgress || 0" :stroke-width="10" />
                      </template>
                    </el-table-column>
                    <el-table-column label="本段工费" width="100">
                      <template #default="{ row: p }">{{ p.amount ?? '-' }}</template>
                    </el-table-column>
                    <el-table-column label="托管" width="90">
                      <template #default="{ row: p }">{{ label(ESCROW_STATUS, p.escrowStatus) }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="120">
                      <template #default="{ row: p }">{{ stageProgressLabel(p) }}</template>
                    </el-table-column>
                    <el-table-column label="操作" min-width="200">
                      <template #default="{ row: p }">
                        <el-button size="small" @click="openProgress(p)">进度记录</el-button>
                        <el-button v-if="p.status==='PENDING_REVIEW'" size="small" type="warning" @click="openInspect(p)">审核质检</el-button>
                        <el-button v-else-if="canShowInspectReport(p)" size="small" @click="openInspect(p)">质检结果</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </template>
              </el-table-column>
              <el-table-column prop="factoryName" label="工厂" min-width="160" />
              <el-table-column label="开始" width="108">
                <template #default="{ row }">
                  <div class="dt-2">
                    <div>{{ fmtDateLine(row.spanStart) }}</div>
                    <div v-if="fmtTimeLine(row.spanStart)" class="dt-clock">{{ fmtTimeLine(row.spanStart) }}</div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="截止" width="108">
                <template #default="{ row }">
                  <div class="dt-2">
                    <div>{{ fmtDateLine(row.spanEnd) }}</div>
                    <div v-if="fmtTimeLine(row.spanEnd)" class="dt-clock">{{ fmtTimeLine(row.spanEnd) }}</div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="承接总数" width="110">
                <template #default="{ row }">{{ row.totalQty }} 件</template>
              </el-table-column>
              <el-table-column label="进度" width="150">
                <template #default="{ row }"><el-progress :percentage="row.progress" :stroke-width="10" /></template>
              </el-table-column>
              <el-table-column label="总工费" width="110">
                <template #default="{ row }">{{ money(row.totalAmount) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="140" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="canOpenNextPeriod(row)" size="small" type="warning" @click="openNextPeriod(row)">进入下一阶段</el-button>
                  <span v-else class="hint">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="状态">{{ label(DEMAND_STATUS, detail.demand?.status) }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ detail.demand?.title }}</el-descriptions-item>
            <el-descriptions-item label="产品">{{ detail.demand?.productName }}</el-descriptions-item>
            <el-descriptions-item label="数量">{{ detail.demand?.quantity }}</el-descriptions-item>
            <el-descriptions-item label="硬交期">{{ detail.demand?.deadlineHard }}</el-descriptions-item>
            <el-descriptions-item label="交付地址">{{ detail.demand?.deliveryAddress }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ fmtTime(detail.demand?.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="发布/意向开始">{{ fmtTime(detail.demand?.publishedAt) }}</el-descriptions-item>
            <el-descriptions-item label="意向截止">{{ fmtTime(detail.demand?.intentionEndAt) }}</el-descriptions-item>
            <el-descriptions-item label="工厂思考开始">{{ fmtTime(detail.demand?.factoryThinkingAt) }}</el-descriptions-item>
            <el-descriptions-item label="工厂思考截止">{{ fmtTime(detail.demand?.factoryThinkingEndAt) }}</el-descriptions-item>
            <el-descriptions-item label="买家思考开始">{{ fmtTime(detail.demand?.buyerThinkingAt) }}</el-descriptions-item>
            <el-descriptions-item label="买家思考截止">{{ fmtTime(detail.demand?.buyerThinkingEndAt) }}</el-descriptions-item>
            <el-descriptions-item label="取消原因">{{ detail.demand?.cancelReason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ detail.demand?.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
          <h4>买家信息</h4>
          <BuyerInfoBlock :buyer="detail.buyer" staff />
          <h4>分期交付计划</h4>
          <el-table v-if="adminDeliveryRows.length" :data="adminDeliveryRows" border size="small" style="margin:8px 0 12px">
            <el-table-column label="期次" width="90">
              <template #default="{ $index }">第{{ $index + 1 }}期</template>
            </el-table-column>
            <el-table-column prop="percent" label="交付比例" width="120" />
            <el-table-column prop="startAt" label="开始日期" width="140" />
            <el-table-column prop="endAt" label="截止日期" min-width="140" />
          </el-table>
          <p v-else class="hint">未填写分期计划</p>
          <el-collapse style="margin-top:12px">
            <el-collapse-item title="技术要求（材料/公差/认证等）" name="tech">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="材料">{{ detail.demand?.material }}</el-descriptions-item>
                <el-descriptions-item label="公差">{{ detail.demand?.tolerance || '-' }}</el-descriptions-item>
                <el-descriptions-item label="AQL">{{ detail.demand?.aql || '-' }}</el-descriptions-item>
                <el-descriptions-item label="检验方式">{{ formatInspectMode(detail.demand?.inspectMode) }}</el-descriptions-item>
                <el-descriptions-item label="认证">{{ detail.demand?.certification || '-' }}</el-descriptions-item>
                <el-descriptions-item v-if="detail.demand?.minYield != null" label="最低良率">{{ detail.demand?.minYield }}</el-descriptions-item>
                <el-descriptions-item label="最低信用">{{ detail.demand?.minCreditScore }}</el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
          </el-collapse>
          <div v-if="detailReached(1)">
          <template v-if="showCoverage">
          <h4>零件覆盖</h4>
          <CoverageBars :items="coverage" />
          </template>
          <h4>报名与报价（工厂）</h4>
          <el-empty v-if="!(detail.factories || []).length" description="暂无工厂报名" :image-size="56" />
          <el-table v-else :data="detail.factories" border size="small">
            <el-table-column prop="name" label="工厂" min-width="160" />
            <el-table-column label="报价" width="130">
              <template #default="{ row }">{{ factoryQuoteLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="承接区间" width="160">
              <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
            </el-table-column>
            <el-table-column prop="creditScore" label="信用分" width="80" />
            <el-table-column label="阶段" width="90">
              <template #default="{ row }">{{ row.bidStage === 'QUOTED' ? '已报价' : '已报名' }}</template>
            </el-table-column>
          </el-table>
          <el-descriptions v-if="detail.quoteStats && (detail.quoteStats.estimatedTotal != null)" :column="2" border size="small" style="margin-top:12px">
            <el-descriptions-item label="预估总价">{{ money(detail.quoteStats.estimatedTotal) }}</el-descriptions-item>
            <el-descriptions-item label="加权均价">{{ money(detail.quoteStats.weightedUnit) }}</el-descriptions-item>
          </el-descriptions>
          </div>
          <h4>工序</h4>
          <el-table :data="detail.processes" border size="small">
            <el-table-column prop="processNo" label="#" width="50" />
            <el-table-column prop="processName" label="工序" />
            <el-table-column prop="requirement" label="要求" />
          </el-table>
          <h4>附件</h4>
          <el-table :data="detail.attachments" border size="small">
            <el-table-column prop="fileName" label="文件名" />
            <el-table-column prop="fileType" label="类型" width="80" />
            <el-table-column prop="fileSize" label="大小" width="100" />
            <el-table-column label="上传时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="viewFile(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="detail.confirmedPlan.groups.length" class="confirmed-plan">
            <h4>买家确认方案 <el-tag type="info" size="small">不可修改</el-tag></h4>
            <p class="hint">{{ detail.confirmedPlan.hint }}</p>
            <div class="line head">
              <span class="col-fac">工厂</span>
              <span class="col-range">承接区间</span>
              <span class="col-qty">分配(件)</span>
              <span class="col-amt">小计</span>
            </div>
            <div v-for="line in detail.confirmedPlan.groups" :key="line.factoryId" class="line">
              <span class="col-fac">{{ line.factoryName || ('工厂-' + line.factoryId) }}</span>
              <span class="col-range">{{ (line.minQty ?? '-') + ' ~ ' + (line.maxQty ?? '-') }}</span>
              <span class="col-qty">{{ line.quantity ?? '-' }}</span>
              <span class="col-amt">{{ money(line.price) }}</span>
            </div>
            <div class="total-bar">
              <span>总计金额</span>
              <strong>{{ money(detail.confirmedPlan.total) }} 元</strong>
            </div>
          </div>
          <el-alert
            v-else-if="['SOLUTION_CONFIRMED','SOLUTION_SELECTED','CONTRACTED','IN_PRODUCTION','COMPLETED'].includes(detail.demand?.status)"
            type="warning"
            :closable="false"
            title="未找到买家确认的方案"
            style="margin-top:16px"
          />
          <div v-if="detailReached(2)" style="margin-top:16px">
            <h4>合同签署</h4>
            <el-empty v-if="!(detail.contracts || []).length" description="尚未生成合同" :image-size="48" />
            <el-table v-else :data="detail.contracts" border size="small">
              <el-table-column prop="factoryName" label="工厂" min-width="140" />
              <el-table-column label="文件" min-width="160">
                <template #default="{ row }">
                  <el-button v-if="row.attachmentId" size="small" link type="primary" @click="downloadPhoto(row.attachmentId)">{{ row.fileName || ('附件 #' + row.attachmentId) }}</el-button>
                  <span v-else>未上传</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">{{ contractStatusText(row.status) }}</template>
              </el-table-column>
              <el-table-column label="签署时间" width="160">
                <template #default="{ row }">{{ fmtTime(row.signedAt) }}</template>
              </el-table-column>
            </el-table>
          </div>
          <div v-if="detailReached(4)" style="margin-top:16px">
            <h4>结算</h4>
            <el-alert v-if="detail.demand?.status==='COMPLETED'" type="success" :closable="false" title="订单已完成结算。" />
            <el-alert v-else-if="detail.demand?.status==='CANCELLED'" type="info" :closable="false" :title="'已取消' + (detail.demand?.cancelReason ? '：' + detail.demand.cancelReason : '')" />
            <el-alert v-else-if="detail.demand?.status==='FLOW_FAILED'" type="warning" :closable="false" :title="'已流单' + (detail.demand?.cancelReason ? '：' + detail.demand.cancelReason : '')" />
          </div>
          <div style="margin-top:16px" v-if="detail.demand?.status==='PENDING_AUDIT'">
            <el-button type="danger" @click="openReturn(detail.demand)">退回修改</el-button>
          </div>
        </el-drawer>
        <el-dialog v-model="fileOpen" :title="fileTitle" width="720px">
          <pre class="file-preview">{{ fileText }}</pre>
        </el-dialog>
        <el-dialog v-model="returnOpen" title="退回买家修改" width="480px" :close-on-click-modal="false">
          <el-form label-width="80px">
            <el-form-item label="原因" required>
              <el-input v-model="returnReason" type="textarea" :rows="3" placeholder="必填，买家会看到原文" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="returnOpen=false">取消</el-button>
            <el-button type="danger" @click="submitReturn">确认退回</el-button>
          </template>
        </el-dialog>

      <!-- 操作日志 -->
      <div v-if="active==='audit'">
        <div class="toolbar">
          <div>
            <span class="title">操作日志</span>
            <div class="hint" style="margin:4px 0 0">记录买家、工厂与运营的关键操作。「对象」是这次动作针对的业务单据。</div>
          </div>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="auditAction" clearable placeholder="全部动作" style="width:220px" @change="loadAudit">
              <el-option v-for="a in AUDIT_ACTIONS" :key="a" :label="a" :value="a" />
            </el-select>
            <el-button @click="loadAudit">刷新</el-button>
          </div>
        </div>
        <PagedBox :data="auditLogs" v-slot="{ rows }">
        <el-table :data="rows" border size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="时间" width="170">
            <template #default="{row}">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="actorName" label="操作人" width="160" />
          <el-table-column prop="action" label="动作" width="170" />
          <el-table-column width="168">
            <template #header>
              <el-tooltip content="这次操作针对的业务单据：需求 / 订单 / 方案 / 工单 / 合同，数字为单据编号" placement="top">
                <span>对象</span>
              </el-tooltip>
            </template>
            <template #default="{row}">
              <div class="audit-obj">
                <el-tag size="small" effect="light" :type="auditTargetTag(row.targetType)">{{ row.targetLabel || auditTargetName(row.targetType) }}</el-tag>
                <span class="audit-obj-id">#{{ row.targetId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="详情" min-width="240" show-overflow-tooltip>
            <template #default="{row}">{{ auditDetail(row) }}</template>
          </el-table-column>
        </el-table>
        </PagedBox>
      </div>

      <!-- 工单质检（仅质检员） -->
      <div v-if="isInspector && active==='inspect'">
        <div class="toolbar"><span class="title">工单质检</span><el-button @click="loadStages">刷新</el-button></div>
        <PagedBox :data="inspectStages" v-slot="{ rows }">
        <el-table :data="rows" border>
          <el-table-column prop="id" label="工单ID" width="90" />
          <el-table-column prop="factoryName" label="工厂" min-width="160" />
          <el-table-column label="承接区间" width="140">
            <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
          </el-table-column>
          <el-table-column label="约定/实交" width="110">
            <template #default="{row}">{{ row.quantity ?? '-' }} / {{ row.deliveredQty ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{row}">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{row}">{{ fmtTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{row}">
              <el-tag :type="inspectStatusType(row.status)" size="small">{{ inspectStatusText(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="isInspector && row.status==='PENDING_INSPECTION'" size="small" type="primary" @click="openInspect(row)">填写质检</el-button>
              <el-button v-else-if="canShowInspectReport(row)" size="small" type="primary" @click="openInspect(row)">质检结果</el-button>
            </template>
          </el-table-column>
        </el-table>
        </PagedBox>
      </div>
        <el-dialog v-model="inspOpen" :title="inspDialogTitle" width="680px" :close-on-click-modal="false" append-to-body>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="产品名称">{{ inspTarget?.productName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="类别">{{ inspTarget?.category || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工厂">{{ inspTarget?.factoryName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="承接区间">{{ (inspTarget?.minQty ?? '-') }} ~ {{ (inspTarget?.maxQty ?? '-') }} 件</el-descriptions-item>
            <el-descriptions-item label="该厂承担数量">{{ inspTarget?.quantity ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="工序">{{ inspTarget?.processName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="技术规格" :span="2">{{ inspTarget?.techSpecs || '-' }}</el-descriptions-item>
            <el-descriptions-item label="质量门槛" :span="2">{{ inspTarget?.qualityThreshold || '-' }}</el-descriptions-item>
            <el-descriptions-item label="买家备注" :span="2">{{ inspTarget?.buyerRemark || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="!isInspector">
            <h4 style="margin:16px 0 8px">买家信息</h4>
            <BuyerInfoBlock :buyer="inspBuyer" staff />
          </div>
          <h4 style="margin:16px 0 8px">{{ inspCanApprove ? '审核' : '质检填写' }}</h4>
          <p class="hint" style="margin:0 0 8px">{{ inspRuleHint }} 规定抽检数 {{ inspRequiredSample }} 件。</p>
          <el-form label-width="130px">
            <el-form-item label="实交数量">
              <el-input-number v-model="inspForm.deliveredQty" :min="0" :disabled="!inspQtyEditable" />
              <span class="hint">约定 {{ inspTarget?.quantity ?? '-' }} 件，质检员/运营可按现场清点修改</span>
            </el-form-item>
            <el-form-item label="抽检数"><el-input-number v-model="inspForm.sampleCount" :min="inspRequiredSample" :disabled="true" /></el-form-item>
            <el-form-item label="关键公差不合格"><el-input-number v-model="inspForm.criticalFailCount" :min="0" :max="inspForm.sampleCount || 0" :disabled="inspFillReadonly" /></el-form-item>
            <el-form-item label="一般公差不合格"><el-input-number v-model="inspForm.generalFailCount" :min="0" :max="inspRemainGeneral" :disabled="inspFillReadonly" /></el-form-item>
            <el-form-item label="关键尺寸结论"><el-input v-model="inspForm.keyDimensions" :disabled="inspFillReadonly" /></el-form-item>
            <el-form-item label="实际良率">
              <el-input :model-value="inspActualYield" disabled />
              <span class="hint">{{ inspIsAql ? ('AQL ' + (inspTarget?.aql || '') + '　Ac=' + (inspTarget?.aqlAc ?? '-') + ' / Re=' + (inspTarget?.aqlRe ?? '-')) : ('最低良率 ' + inspMinYield) }}</span>
            </el-form-item>
            <el-form-item label="公差是否合格">
              <el-radio-group :model-value="inspToleranceOk" disabled>
                <el-radio :value="true">合格</el-radio>
                <el-radio :value="false">不合格</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="数量是否达标">
              <el-radio-group v-model="inspForm.quantityOk" :disabled="!inspQtyEditable">
                <el-radio :value="true">达标</el-radio>
                <el-radio :value="false">不达标</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="是否合格">
              <el-radio-group v-model="inspForm.result" disabled>
                <el-radio value="PASS">合格</el-radio>
                <el-radio value="FAIL">不合格</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="备注"><el-input v-model="inspForm.remark" type="textarea" :disabled="inspFillReadonly" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="inspOpen=false">{{ (inspCanSubmit || inspCanApprove) ? '取消' : '关闭' }}</el-button>
            <el-button v-if="inspCanSubmit" :type="inspForm.result==='PASS'?'success':'danger'" @click="submitInspect">提交</el-button>
            <el-button v-if="inspCanApprove" type="primary" @click="approveInspectSheet">提交</el-button>
          </template>
        </el-dialog>
        <el-dialog v-model="progOpen" title="进度记录" width="560px" append-to-body>
          <el-timeline>
            <el-timeline-item v-for="p in progLogs" :key="p.id" :timestamp="p.createdAt">
              {{ p.doneQty }} / {{ progStage?.quantity }} 件 · {{ p.progress }}% · {{ p.remark }}
            </el-timeline-item>
          </el-timeline>
          <p v-if="!progLogs.length" class="hint">暂无上报</p>
        </el-dialog>

      <!-- 资金流水 -->
      <div v-if="active==='funds'">
        <div v-if="!fundEnterprise">
        <div class="toolbar"><span class="title">资金总览</span><el-button @click="loadFundViews">刷新</el-button></div>
        <el-card shadow="never" class="trend-card">
          <div class="trend-head">
            <div>
              <span>近 30 天资金流水</span>
              <small>按日汇总 · 人民币</small>
            </div>
            <strong>¥ {{ fmtAmt(fundTrendTotal) }}</strong>
          </div>
          <div class="trend-plot" v-if="fundTrendPoints.hasActivity">
            <div class="trend-y">
              <span v-for="(t, i) in fundTrendPoints.yTicks.slice().reverse()" :key="i">{{ t }}</span>
            </div>
            <div class="trend-plot-main">
              <div class="trend-svg-wrap" @mousemove="onTrendMove" @mouseleave="fundTrendHover = null">
                <svg class="trend-svg" viewBox="0 0 640 220" preserveAspectRatio="none">
                  <defs>
                    <linearGradient id="fundArea" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stop-color="#533afd" stop-opacity="0.28" />
                      <stop offset="100%" stop-color="#7c6ffe" stop-opacity="0.02" />
                    </linearGradient>
                    <filter id="fundGlow" x="-20%" y="-20%" width="140%" height="140%">
                      <feGaussianBlur stdDeviation="2.4" result="b" />
                      <feMerge>
                        <feMergeNode in="b" />
                        <feMergeNode in="SourceGraphic" />
                      </feMerge>
                    </filter>
                  </defs>
                  <line v-for="(y, i) in fundTrendPoints.gridYs" :key="'g'+i" x1="0" :y1="y" x2="640" :y2="y" stroke="#e6e8f5" stroke-width="1" />
                  <polygon v-if="fundTrendPoints.area" :points="fundTrendPoints.area" fill="url(#fundArea)" ref="trendArea" />
                  <line v-if="fundTrendHover" :x1="fundTrendHover.x" :x2="fundTrendHover.x" y1="0" y2="220" stroke="#7c6ffe" stroke-width="1" stroke-dasharray="3 3" />
                  <polyline ref="trendLine" :points="fundTrendPoints.line" fill="none" stroke="#533afd" stroke-width="2.6" stroke-linejoin="round" stroke-linecap="round" filter="url(#fundGlow)" />
                  <circle v-if="fundTrendHover" :cx="fundTrendHover.x" :cy="fundTrendHover.y" r="5.5" fill="#fff" stroke="#533afd" stroke-width="2" />
                </svg>
                <div
                  v-if="fundTrendHover"
                  class="trend-tip"
                  :style="{ left: fundTrendHover.xPct + '%', transform: fundTrendHover.xPct > 85 ? 'translateX(-100%)' : (fundTrendHover.xPct < 15 ? 'translateX(0)' : 'translateX(-50%)') }"
                >
                  <div>{{ fundTrendHover.dateLabel }}</div>
                  <div class="trend-tip-amt">¥ {{ fmtAmt(fundTrendHover.amount) }}</div>
                </div>
              </div>
              <div class="trend-axis">
                <span v-for="(t, i) in fundTrendAxis" :key="i">{{ t }}</span>
              </div>
            </div>
          </div>
          <div v-else class="trend-empty">
            <div class="trend-empty-mark">
              <i></i><i></i><i></i><i></i><i></i>
            </div>
            <strong>近 30 天暂无资金变动</strong>
            <span>产生托管、佣金或结算流水后，将在此显示趋势</span>
          </div>
        </el-card>
        <el-row :gutter="16" class="fund-kpis">
          <el-col :span="5"><el-card shadow="never" class="fund-kpi-card" tabindex="0" @click="openFundDetails('INTENTION')" @keyup.enter="openFundDetails('INTENTION')"><div class="kpi">意向冻结 <span>查看明细 →</span></div><div class="kpi-v"><CountUp prefix="¥ " :value="fundOverview.intentionFrozenNet || 0" /></div></el-card></el-col>
          <el-col :span="5"><el-card shadow="never" class="fund-kpi-card" tabindex="0" @click="openFundDetails('DEPOSIT')" @keyup.enter="openFundDetails('DEPOSIT')"><div class="kpi">保证金冻结 <span>查看明细 →</span></div><div class="kpi-v"><CountUp prefix="¥ " :value="fundOverview.depositFrozenNet || 0" /></div></el-card></el-col>
          <el-col :span="5"><el-card shadow="never" class="fund-kpi-card" tabindex="0" @click="openFundDetails('ESCROW')" @keyup.enter="openFundDetails('ESCROW')"><div class="kpi">平台托管 <span>查看明细 →</span></div><div class="kpi-v"><CountUp prefix="¥ " :value="fundOverview.platformEscrow || 0" /></div></el-card></el-col>
          <el-col :span="5"><el-card shadow="never" class="fund-kpi-card" tabindex="0" @click="openFundDetails('COMMISSION')" @keyup.enter="openFundDetails('COMMISSION')"><div class="kpi">佣金累计 <span>查看明细 →</span></div><div class="kpi-v"><CountUp prefix="¥ " :value="fundOverview.commissionTotal || 0" /></div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="kpi">平台余额</div><div class="kpi-v"><CountUp prefix="¥ " :value="fundOverview.platformBalance || 0" /></div></el-card></el-col>
        </el-row>
        <el-tabs v-model="fundTab">
          <el-tab-pane label="需求流水" name="byDemand" />
          <el-tab-pane label="买家流水" name="buyers" />
          <el-tab-pane label="工厂流水" name="factories" />
        </el-tabs>
        <div v-if="fundTab==='byDemand'">
          <PagedBox v-if="fundByDemand.length" :data="fundByDemand" v-slot="{ rows }">
            <el-collapse>
              <el-collapse-item v-for="g in rows" :key="g.demandId" :title="'#' + g.demandId + ' ' + (g.demandTitle || '') + ' · 小计 ¥' + g.subtotal">
                <el-table :data="g.flows" border size="small">
                  <el-table-column label="时间" width="160">
                    <template #default="{row}">{{ fmtTime(row.createdAt) }}</template>
                  </el-table-column>
                  <el-table-column prop="enterpriseName" label="企业" min-width="120" />
                  <el-table-column label="类型" width="100"><template #default="{row}">{{ label(FUND_TYPE, row.type) }}</template></el-table-column>
                  <el-table-column label="方向" width="90"><template #default="{row}">{{ label(FUND_DIR, row.direction) }}</template></el-table-column>
                  <el-table-column prop="amount" label="金额" width="100" />
                </el-table>
              </el-collapse-item>
            </el-collapse>
          </PagedBox>
          <el-empty v-else description="暂无需求流水" :image-size="60" />
        </div>
        <div v-else-if="fundTab==='buyers'">
          <PagedBox :data="fundBuyers" v-slot="{ rows }">
            <el-table :key="'buyers-'+rows.length" :data="rows" border>
              <el-table-column prop="tenantId" label="企业ID" width="80" />
              <el-table-column prop="enterpriseName" label="企业" min-width="160" />
              <el-table-column prop="balance" label="可用余额" width="120" />
              <el-table-column prop="frozen" label="冻结" width="120" />
              <el-table-column label="总流水" width="140">
                <template #default="{ row }">{{ fmtAmt(enterpriseTotalFlow(row)) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" @click="openFundEnterprise(row)">流水详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </PagedBox>
        </div>
        <div v-else>
          <PagedBox :data="fundFactories" v-slot="{ rows }">
            <el-table :key="'factories-'+rows.length" :data="rows" border>
              <el-table-column prop="tenantId" label="企业ID" width="80" />
              <el-table-column prop="enterpriseName" label="企业" min-width="160" />
              <el-table-column prop="balance" label="可用余额" width="120" />
              <el-table-column prop="frozen" label="冻结" width="120" />
              <el-table-column label="总流水" width="140">
                <template #default="{ row }">{{ fmtAmt(enterpriseTotalFlow(row)) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" @click="openFundEnterprise(row)">流水详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </PagedBox>
        </div>
        </div>
        <div v-else>
          <div class="toolbar">
            <span class="title">{{ fundEnterprise.enterpriseName || '企业流水' }}</span>
            <div style="display:flex;gap:8px">
              <el-button @click="fundEnterprise=null">返回</el-button>
            </div>
          </div>
          <el-descriptions :column="3" border size="small" style="margin-bottom:12px">
            <el-descriptions-item label="企业">{{ fundEnterprise.enterpriseName }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ fundEnterprise.enterpriseType === 'BUYER' ? '买家' : (fundEnterprise.enterpriseType === 'FACTORY' ? '工厂' : fundEnterprise.enterpriseType) }}</el-descriptions-item>
            <el-descriptions-item label="可用余额">¥ {{ fundEnterprise.account?.balance ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="冻结">¥ {{ fundEnterprise.account?.frozen ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="总流水">¥ {{ fmtAmt(fundEnterprise.totalFlow ?? enterpriseFlowSum(fundEnterprise.flows)) }}</el-descriptions-item>
          </el-descriptions>
          <p class="hint">含意向金、保证金、托管工费、质检费等全部流水。</p>
          <PagedBox :data="fundEnterprise.flows || []" v-slot="{ rows }">
          <el-table :data="rows" border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="时间" width="170">
              <template #default="{row}">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="需求" min-width="160">
              <template #default="{row}">{{ row.demandTitle || (row.demandId ? '#' + row.demandId : '-') }}</template>
            </el-table-column>
            <el-table-column label="类型" width="110"><template #default="{row}">{{ label(FUND_TYPE, row.type) }}</template></el-table-column>
            <el-table-column label="方向" width="90"><template #default="{row}">{{ label(FUND_DIR, row.direction) }}</template></el-table-column>
            <el-table-column prop="amount" label="金额" width="120" />
          </el-table>
          </PagedBox>
        </div>
      </div>
      <el-dialog v-model="fundDetailOpen" :title="fundDetailTitle" width="860px">
        <PagedBox v-if="fundDetailRows.length" :data="fundDetailRows" :page-size="10" v-slot="{ rows }">
          <el-table :data="rows" border>
            <el-table-column label="时间" width="165"><template #default="{ row }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
            <el-table-column label="需求" min-width="160"><template #default="{ row }">{{ row.demandTitle || (row.demandId ? '#' + row.demandId : '-') }}</template></el-table-column>
            <el-table-column prop="enterpriseName" label="资金主体" min-width="150" />
            <el-table-column label="类型" width="110"><template #default="{ row }">{{ label(FUND_TYPE, row.type) }}</template></el-table-column>
            <el-table-column label="方向" width="90"><template #default="{ row }">{{ label(FUND_DIR, row.direction) }}</template></el-table-column>
            <el-table-column label="金额" width="120" align="right"><template #default="{ row }">¥ {{ fmtAmt(row.amount) }}</template></el-table-column>
          </el-table>
        </PagedBox>
        <el-empty v-else description="暂无相关资金明细" :image-size="64" />
      </el-dialog>

      <!-- 用户信息管理 -->
      <div v-if="active==='enterprises'">
        <div class="toolbar">
          <span class="title">用户信息管理</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-input v-model="entKeyword" clearable placeholder="搜企业名/信用代码/联系人/手机号" style="width:280px" @keyup.enter="loadEnterprises" />
            <el-button type="primary" @click="loadEnterprises">搜索</el-button>
            <el-button @click="entKeyword=''; loadEnterprises()">刷新</el-button>
          </div>
        </div>
        <el-tabs v-model="entTab" @tab-change="loadEnterprises">
          <el-tab-pane label="需求方" name="BUYER" />
          <el-tab-pane label="工厂方" name="FACTORY" />
          <el-tab-pane label="质检方" name="INSPECTION" />
        </el-tabs>
        <PagedBox :data="enterprises" v-slot="{ rows }">
        <el-table :data="rows" border class="enterprise-table">
          <el-table-column prop="name" label="企业" min-width="150" show-overflow-tooltip />
          <el-table-column prop="creditCode" label="统一信用代码" width="150" show-overflow-tooltip />
          <el-table-column prop="contactName" label="联系人" width="90" align="center" />
          <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
          <el-table-column label="企业状态" width="110" align="center">
            <template #default="{ row }">
              <div class="credit-cell">
                <strong>{{ row.creditScore ?? '-' }}</strong>
                <span>{{ row.authStatus === 'APPROVED' ? '已认证' : (row.authStatus === 'REJECTED' ? '未通过' : '待认证') }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="150" align="center">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="登录账号" width="135">
            <template #default="{ row }">
              <div class="account-cell">
                <strong>{{ row.accountPhone || '-' }}</strong>
                <span>{{ row.realName || '未设置姓名' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="密码" width="120" align="center">
            <template #default="{ row }">
              <span v-if="row.accountPassword" class="pwd-cell">
                <span class="pwd-text" :class="{ 'pwd-mask': !pwdShown[row.id] }">{{ pwdShown[row.id] ? row.accountPassword : '••••••••' }}</span>
                <el-icon class="pwd-eye" :title="pwdShown[row.id] ? '隐藏密码' : '显示密码'" @click.stop="togglePwd(row.id)">
                  <Hide v-if="pwdShown[row.id]" />
                  <View v-else />
                </el-icon>
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.userStatus === 'DISABLED'" type="danger" size="small">停用</el-tag>
              <el-tag v-else-if="row.userStatus" type="success" size="small">启用</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" :width="entTab==='FACTORY' ? 210 : 150" align="center">
            <template #default="{ row }">
              <el-button v-if="entTab==='FACTORY'" size="small" type="primary" plain @click="openFactoryProfile(row.id)">详情</el-button>
              <el-button size="small" @click="openEntEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeEnterprise(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        </PagedBox>

        <el-dialog v-model="entEditOpen" title="编辑用户信息" width="560px" :close-on-click-modal="false">
          <h4 style="margin:0 0 8px">企业信息</h4>
          <el-form label-width="100px">
            <el-form-item label="企业名称" required><el-input v-model="entEdit.name" /></el-form-item>
            <el-form-item label="信用代码"><el-input v-model="entEdit.creditCode" /></el-form-item>
            <el-form-item label="联系人"><el-input v-model="entEdit.contactName" /></el-form-item>
            <el-form-item label="地址"><el-input v-model="entEdit.address" /></el-form-item>
            <el-form-item label="信用分"><el-input-number v-model="entEdit.creditScore" :min="0" :max="100" /></el-form-item>
            <el-form-item label="认证状态">
              <el-select v-model="entEdit.authStatus" style="width:100%">
                <el-option label="待审" value="PENDING" />
                <el-option label="已通过" value="APPROVED" />
                <el-option label="已拒绝" value="REJECTED" />
              </el-select>
            </el-form-item>
          </el-form>
          <h4 style="margin:16px 0 8px">登录账号</h4>
          <el-form label-width="100px">
            <el-form-item label="手机号"><el-input v-model="entEdit.accountPhone" maxlength="11" :disabled="!entEdit.userId" @input="onEntPhone" /></el-form-item>
            <el-form-item label="姓名"><el-input v-model="entEdit.realName" :disabled="!entEdit.userId" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="entEdit.userStatus" style="width:100%" :disabled="!entEdit.userId">
                <el-option label="启用" value="ENABLED" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="entEdit.password" type="password" show-password placeholder="不改请留空" :disabled="!entEdit.userId" />
            </el-form-item>
          </el-form>
          <el-alert v-if="!entEdit.userId" type="warning" :closable="false" title="该企业暂无关联登录账号，仅可改企业信息" style="margin-top:8px" />
          <template #footer>
            <el-button @click="entEditOpen=false">取消</el-button>
            <el-button type="primary" @click="saveEntEdit">保存</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- 账号管理 -->
      <div v-if="active==='users' && isSuper">
        <div class="toolbar"><span class="title">创建账号（发放权限）</span></div>
        <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="90px" style="max-width:480px">
          <el-form-item label="手机号" prop="phone"><el-input v-model="userForm.phone" maxlength="11" placeholder="11位数字" @input="onUserPhone" /></el-form-item>
          <el-form-item label="密码" prop="password"><el-input v-model="userForm.password" type="password" show-password /></el-form-item>
          <el-form-item label="姓名" prop="realName"><el-input v-model="userForm.realName" /></el-form-item>
          <el-form-item label="角色">
            <el-radio-group v-model="userForm.role">
              <el-radio value="OPERATOR">运营</el-radio>
              <el-radio value="INSPECTION">质检</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="userForm.role==='INSPECTION'" label="质检机构名">
            <el-input v-model="userForm.orgName" placeholder="如 华检质量检测（上海）有限公司，不填按姓名生成" />
          </el-form-item>
          <el-button type="primary" @click="createUser">创建账号</el-button>
        </el-form>
      </div>
      <el-dialog v-model="pwdOpen" title="修改登录密码" width="420px" :close-on-click-modal="false">
        <el-form label-width="90px">
          <el-form-item label="原密码"><el-input v-model="pwd.oldPassword" type="password" show-password /></el-form-item>
          <el-form-item label="新密码"><el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" /></el-form-item>
          <el-form-item label="确认新密码"><el-input v-model="pwd.confirm" type="password" show-password /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="pwdOpen=false">取消</el-button>
          <el-button type="primary" @click="changePwd">修改</el-button>
        </template>
      </el-dialog>
      <el-dialog v-model="solDialog" title="审核 AI 推荐方案" width="960px" :close-on-click-modal="false">
        <div
          v-loading="aiLoading"
          class="ai-sol-body"
          element-loading-text="AI 正在生成推荐方案，通常需要十几秒到一分钟，请稍候…"
          element-loading-background="rgba(255,255,255,0.88)"
        >
        <el-alert type="info" :closable="false" style="margin-bottom:10px"
          title="勾选审核通过的方案后，点底部「下发选中方案」一次发给买家。分次下发会导致买家端方案列表不同步。点工厂名称可查看该厂公开信息。" />
        <div v-if="solBuyer && solBuyer.name" class="sol-buyer">
          <h4 style="margin:0 0 8px">买家信息</h4>
          <BuyerInfoBlock :buyer="solBuyer" staff />
        </div>
        <el-button v-if="currentSolDemand && currentSolDemand.status==='SOLUTION_GENERATED'" type="warning" :loading="aiLoading" :disabled="aiLoading" style="margin-bottom:10px" @click="retryAi">重新生成 AI 方案</el-button>
        <div v-if="aiLoading && !solutions.length" class="ai-skel">
          <div v-for="n in 2" :key="n" class="ai-skel-card">
            <el-skeleton animated :rows="5" />
          </div>
          <p class="hint ai-skel-hint">正在调用模型编排工厂与件数，页面会一直等到生成完成。</p>
        </div>
        <el-empty v-else-if="!solutions.length" description="暂无方案，可点重新生成" />
        <div v-for="s in solutions" :key="s.id" style="margin-bottom:20px">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;flex-wrap:wrap">
            <el-checkbox v-if="canPickSolution(s)" v-model="s._picked">选中下发</el-checkbox>
            <el-tag type="primary">方案 {{ s.type }}</el-tag>
            <span class="hint">生成 {{ fmtTime(s.createdAt) }}</span>
            <el-tag size="small" :type="solStatusType(s.status)">{{ solStatusText(s.status) }}</el-tag>
          </div>
          <el-input v-if="canPickSolution(s)" v-model="s._rationale" type="textarea" :rows="2" placeholder="推荐说明（可改）" style="margin-bottom:8px" />
          <p v-else class="hint">{{ parseRationale(s.rationaleJson).rationale }}</p>
          <el-alert
            v-for="(r, i) in parseRationale(s.rationaleJson).risks"
            :key="s.id + '-r-' + i"
            type="warning"
            :closable="false"
            :title="r"
            style="margin-bottom:8px"
          />
          <el-table :data="s._alloc || []" size="small" border>
            <el-table-column label="工厂" width="180">
              <template #default="{ row }">
                <el-button v-if="row.factoryId" link type="primary" @click="openFactoryProfile(row.factoryId)">{{ row.factoryName || ('工厂-' + row.factoryId) }}</el-button>
                <span v-else>{{ row.factoryName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分配(件)" width="150">
              <template #default="{ row }">
                <el-input-number v-if="canPickSolution(s)" v-model="row.quantity" :min="row.minQty || 1" :max="row.maxQty || 999999" size="small" @change="syncAlloc(s, row)" />
                <span v-else>{{ row.quantity ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="承接区间" width="120">
              <template #default="{ row }">{{ (row.minQty ?? '-') + ' ~ ' + (row.maxQty ?? '-') }}</template>
            </el-table-column>
            <el-table-column label="单价" width="70">
              <template #default="{ row }">{{ row.unitPrice ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="选厂理由" min-width="140" />
            <el-table-column prop="capacityCheck" label="产能核算" min-width="120" />
            <el-table-column label="小计" width="90">
              <template #default="{ row }">{{ money(allocSubtotal(row)) }}</template>
            </el-table-column>
            <el-table-column prop="days" label="工期" width="60" />
            <el-table-column v-if="canPickSolution(s)" label="修改" width="80">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openReplace(s, row)">换厂</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="total-bar">
            <span>总计金额</span>
            <strong>{{ money(allocTotal(s._alloc)) }} 元</strong>
          </div>
          <div v-if="canPickSolution(s)" style="margin-top:8px;display:flex;gap:8px">
            <el-button size="small" :disabled="aiLoading" @click="saveAiEdits(s)">保存修改</el-button>
          </div>
        </div>
        <div v-if="pendingReviewSolutions.length && currentSolDemand?.status==='SOLUTION_GENERATED'" class="publish-bar">
          <span>已选 {{ pickedSolutionCount }} / {{ pendingReviewSolutions.length }} 套待审方案</span>
          <el-button type="success" :disabled="aiLoading || pickedSolutionCount < 1" @click="publishPicked">下发选中方案</el-button>
        </div>
        </div>
      </el-dialog>
      <el-drawer v-model="factoryProfileOpen" title="工厂详情" size="600px" append-to-body :z-index="4100">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="名称">{{ factoryProfile.name }}</el-descriptions-item>
          <el-descriptions-item label="信用分">{{ factoryProfile.creditScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="认证">{{ factoryProfile.authStatus === 'APPROVED' ? '已认证' : (factoryProfile.authStatus || '-') }}</el-descriptions-item>
          <el-descriptions-item label="质检合格率(平台计算)">{{ factoryProfile.inspectionPassRate != null ? factoryProfile.inspectionPassRate + '%（已结算且已出结论 ' + factoryProfile.inspectionCount + ' 单）' : '暂无已结算质检' }}</el-descriptions-item>
          <el-descriptions-item label="已结算工单">{{ factoryProfile.settledStages ?? 0 }} 单</el-descriptions-item>
          <el-descriptions-item label="买家评分">{{ factoryProfile.surveyAvg != null ? factoryProfile.surveyAvg + ' 分' : '暂无评价' }}</el-descriptions-item>
          <el-descriptions-item label="报名后未填报次数">{{ factoryProfile.noLockCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="违约/处罚次数">{{ factoryProfile.penaltyCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="体系认证">{{ factoryProfile.certs || '-' }}</el-descriptions-item>
          <el-descriptions-item label="企业介绍">{{ factoryProfile.introduction || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4>设备与产能（日产能合计 {{ factoryProfile.dailyCapacitySum ?? 0 }} 件/天）</h4>
        <PagedBox :data="factoryProfile.devices || []" :page-size="8" v-slot="{ rows }">
        <el-table :data="rows" border size="small">
          <el-table-column prop="name" label="设备" min-width="120" />
          <el-table-column prop="processNames" label="适用工序" min-width="120" />
          <el-table-column prop="dailyCapacity" label="日产能" width="80" />
          <el-table-column label="添加时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="deviceStatusType(row.status)" size="small">{{ label(DEVICE_STATUS, row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        </PagedBox>
      </el-drawer>
      <el-dialog v-model="repOpen" title="换厂（价格不变）" width="640px" :close-on-click-modal="false">
        <p>将替换该厂在方案中的全部工序分配，单价按新厂报价更新后以件数小计。</p>
        <PagedBox :data="alts" :page-size="8" v-slot="{ rows }">
        <el-table :data="rows" size="small" border>
          <el-table-column label="工厂">
            <template #default="{ row }">
              <el-button v-if="row.factoryId" link type="primary" @click="openFactoryProfile(row.factoryId)">{{ row.factoryName || ('工厂-' + row.factoryId) }}</el-button>
              <span v-else>{{ row.factoryName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="creditScore" label="信用" width="70" />
          <el-table-column prop="maxQty" label="承接量" width="80" />
          <el-table-column prop="dailyCapacity" label="日产能合计" width="110" />
          <el-table-column label="" width="80">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="doReplace(row)">选用</el-button>
            </template>
          </el-table-column>
        </el-table>
        </PagedBox>
      </el-dialog>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { listAll, getDetail, getCoverage, listBidFactories, returnToBuyer, audit as auditDemand } from '../api/demand'
import { inspect as inspectStage, approveInspect, inspectionOf, inspectQueue, listOrders, listContracts, stages as listStages, progressLog, openNextPeriod as openNextPeriodApi } from '../api/order'
import { fetchAttachment, saveBlob } from '../api/file'
import CoverageBars from '../components/CoverageBars.vue'
import BuyerInfoBlock from '../components/BuyerInfoBlock.vue'
import IntentionCountdown from '../components/IntentionCountdown.vue'
import PagedBox from '../components/PagedBox.vue'
import CountUp from '../components/CountUp.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, Hide } from '@element-plus/icons-vue'
import AppHeader from '../components/AppHeader.vue'
import { DEMAND_STATUS, FUND_TYPE, FUND_DIR, STAGE_STATUS, DEVICE_STATUS, ESCROW_STATUS, label, fmtTime, fmtDateLine, fmtTimeLine, deviceStatusType, formatInspectMode, stageProgressLabel, nestReworkPeriods, canShowInspectReport, factoryWorkSpan } from '../utils/labels'
import { getRole, clearAuth } from '../utils/auth'
import { drawStroke, fillArea } from '../motion/chart'

const router = useRouter()
const role = getRole()
const isInspector = role === 'INSPECTION'
const isSuper = role === 'SUPER_ADMIN'
const menuItems = computed(() => {
  if (isInspector) return [{ index: 'inspect', label: '工单质检' }]
  const items = [
    { index: 'overview', label: '工作台' },
    { index: 'all', label: '全部需求' },
    { index: 'funds', label: '资金流水' },
    { index: 'enterprises', label: '用户信息管理' },
    { index: 'audit', label: '操作日志' },
  ]
  if (isSuper) items.push({ index: 'users', label: '账号管理' })
  return items
})
const active = ref(isInspector ? 'inspect' : 'overview')
const demandTab = ref('audit')
const DEMAND_GROUPS = {
  audit: ['PENDING_AUDIT', 'RETURNED'],
  match: ['PUBLISHED', 'FACTORY_THINKING', 'BUYER_THINKING', 'THINKING', 'REVIEWING'],
  quote: ['LOCKING', 'SOLUTION_GENERATED', 'SOLUTION_CONFIRMED', 'SOLUTION_SELECTED'],
  fulfill: ['CONTRACTED', 'IN_PRODUCTION'],
  close: ['COMPLETED', 'CANCELLED', 'FLOW_FAILED'],
}
const demands = ref([])
const filteredDemands = computed(() => demands.value.filter(d => (DEMAND_GROUPS[demandTab.value] || []).includes(d.status)))
const solutionDemands = computed(() => demands.value.filter(d =>
  ['SOLUTION_GENERATED', 'SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(d.status)))
function countGroup(key) {
  return demands.value.filter(d => (DEMAND_GROUPS[key] || []).includes(d.status)).length
}
const stages = ref([])
const inspectStages = computed(() => [...stages.value].sort((a, b) => {
  const rank = s => {
    if (isInspector) return s === 'PENDING_INSPECTION' ? 0 : (s === 'PENDING_REVIEW' ? 1 : 2)
    return s === 'PENDING_REVIEW' ? 0 : (s === 'PENDING_INSPECTION' ? 1 : 2)
  }
  const d = rank(a.status) - rank(b.status)
  if (d !== 0) return d
  return String(b.updatedAt || '').localeCompare(String(a.updatedAt || ''))
}))
const funds = ref([])
const fundOverview = ref({})
const fundByDemand = ref([])
const fundAccounts = ref([])
const fundTrend = ref([])
const fundTab = ref('byDemand')
const fundEnterprise = ref(null)
const fundDetailOpen = ref(false)
const fundDetailTitle = ref('')
const fundDetailRows = ref([])
const fundTrendHover = ref(null)
const trendLine = ref(null)
const trendArea = ref(null)
const fundBuyers = computed(() => (fundAccounts.value || []).filter(a => a.enterpriseType === 'BUYER'))
const fundFactories = computed(() => (fundAccounts.value || []).filter(a => a.enterpriseType === 'FACTORY'))
const fundTrendTotal = computed(() => (fundTrend.value || []).reduce((sum, item) => sum + (Number(item.amount) || 0), 0))
const fundTrendPoints = computed(() => {
  const list = fundTrend.value || []
  if (!list.length) return { line: '', area: '', yTicks: [], gridYs: [], dots: [], hasActivity: false }
  const vals = list.map(d => Number(d.amount) || 0)
  const hasActivity = vals.some(value => value !== 0)
  if (!hasActivity) return { line: '', area: '', yTicks: [], gridYs: [], dots: [], hasActivity: false }
  const max = niceTrendMax(Math.max(...vals, 0))
  const w = 640
  const h = 220
  const padTop = 16
  const padBot = 12
  const n = list.length
  const innerH = h - padTop - padBot
  const dots = list.map((d, i) => {
    const amount = Number(d.amount) || 0
    const x = n === 1 ? w / 2 : (i * w / (n - 1))
    const y = h - padBot - (amount / max) * innerH
    return {
      x: x.toFixed(1),
      y: y.toFixed(1),
      xPct: n === 1 ? 50 : (i / (n - 1)) * 100,
      amount,
      date: d.date,
      dateLabel: fmtTrendDate(d.date),
    }
  })
  const steps = 4
  const yTicks = []
  const gridYs = []
  for (let i = 0; i <= steps; i++) {
    yTicks.push(formatTrendY((max * i) / steps))
    gridYs.push((h - padBot - (innerH * i) / steps).toFixed(1))
  }
  const line = dots.map(p => `${p.x},${p.y}`).join(' ')
  const area = dots.length
    ? `${dots[0].x},${h} ${line} ${dots[dots.length - 1].x},${h}`
    : ''
  return { line, area, yTicks, gridYs, dots, hasActivity: true }
})
const fundTrendAxis = computed(() => {
  const list = fundTrend.value || []
  if (list.length < 2) return list.map(d => fmtTrendDate(d.date))
  const pick = [list[0], list[Math.floor(list.length / 3)], list[Math.floor((list.length * 2) / 3)], list[list.length - 1]]
  return pick.map(d => fmtTrendDate(d?.date))
})
function fmtTrendDate(iso) {
  if (!iso) return ''
  const p = String(iso).split('-')
  if (p.length < 3) return iso
  return Number(p[1]) + '/' + Number(p[2])
}
function niceTrendMax(v) {
  if (v <= 0) return 100
  const mag = 10 ** Math.floor(Math.log10(v))
  const n = v / mag
  const nice = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10
  return nice * mag
}
function formatTrendY(v) {
  if (v >= 10000) return (v / 10000).toFixed(v % 10000 === 0 ? 0 : 1) + '万'
  if (v >= 1000) return String(Math.round(v))
  if (Number.isInteger(v)) return String(v)
  return v.toFixed(1)
}
function onTrendMove(e) {
  const dots = fundTrendPoints.value.dots || []
  if (!dots.length) return
  const rect = e.currentTarget.getBoundingClientRect()
  if (rect.width <= 0) return
  const pct = (e.clientX - rect.left) / rect.width
  const i = Math.round(pct * (dots.length - 1))
  fundTrendHover.value = dots[Math.min(dots.length - 1, Math.max(0, i))]
}
function fmtAmt(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}
function enterpriseFlowSum(flows) {
  return (flows || []).reduce((s, f) => s + Math.abs(Number(f.amount) || 0), 0)
}
function enterpriseTotalFlow(row) {
  if (row?.totalFlow != null && row.totalFlow !== '') return row.totalFlow
  const id = row?.tenantId
  return (funds.value || [])
    .filter(f => f.tenantId === id)
    .reduce((s, f) => s + Math.abs(Number(f.amount) || 0), 0)
}
const enterprises = ref([])
const entTab = ref('BUYER')
const entKeyword = ref('')
const pwdShown = reactive({})
function togglePwd(id) {
  pwdShown[id] = !pwdShown[id]
}
const entEditOpen = ref(false)
const entEdit = reactive({
  id: null, name: '', creditCode: '', contactName: '', address: '', creditScore: 60, authStatus: 'APPROVED',
  userId: null, accountPhone: '', realName: '', userStatus: 'ENABLED', password: '',
})
const userFormRef = ref()
const userForm = reactive({ phone: '', password: '123456', realName: '', role: 'OPERATOR', orgName: '' })
const userRules = {
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^\d{11}$/, message: '手机号必须为11位数字', trigger: ['blur', 'change'] },
  ],
  password: [{ required: true, message: '请填写密码', trigger: 'blur' }, { min: 6, message: '至少 6 位', trigger: 'blur' }],
  realName: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
}
const solutions = ref([])
const solDialog = ref(false)
const currentSolDemand = ref(null)
const solBuyer = ref({})
const aiLoading = ref(false)
const repOpen = ref(false)
const alts = ref([])
const currentSol = ref(null)
const currentProcess = ref(null)

const overview = ref({})
const overviewKpis = [
  { key: 'pendingAudit', label: '待审核发布', warn: true },
  { key: 'published', label: '意向期' },
  { key: 'factoryThinking', label: '工厂思考期' },
  { key: 'buyerThinking', label: '买家思考期' },
  { key: 'solutionGenerated', label: '方案待审/待选', warn: true },
  { key: 'solutionSelected', label: '合同签署中' },
  { key: 'inProduction', label: '履约中' },
  { key: 'completed', label: '已完成' },
  { key: 'contractsPending', label: '待买家确认签署' },
  { key: 'stagesPendingInspection', label: '待质检', warn: true },
  { key: 'stagesOverdue', label: '逾期工单', warn: true },
]
const allContracts = ref([])
const contractStatus = ref('')
const auditLogs = ref([])
const auditAction = ref('')
const AUDIT_ACTIONS = [
  '买家申请发布', '买家再次提交审核', '买家取消需求', '买家思考期继续', '买家思考期取消',
  '买家确认方案', '买家上传合同', '买家确认签署并派单', '买家完工确认', '方案期关闭订单',
  '工厂报名', '工厂取消报名', '工厂提交报价', '工厂思考期退出', '工厂签署合同', '工厂开工', '工厂交付',
  '需求审核通过', '需求退回', '结束意向期', '结束工厂思考期', '下发方案', '进入合同签署',
  '工单质检', '提交质检单', '审核质检单', '开启下一期工单', '支付质检费',
]
const factoryProfileOpen = ref(false)
const factoryProfile = ref({})

const fileOpen = ref(false)
const fileTitle = ref('')
const fileText = ref('')
const detailOpen = ref(false)
const detail = reactive({
  demand: null, processes: [], attachments: [], factories: [], buyer: {}, quoteStats: {},
  contracts: [], stages: [], orderId: null,
  confirmedPlan: { groups: [], total: 0, hint: '' },
})
function detailStatusIndex(st) {
  if (['PENDING_AUDIT', 'RETURNED', 'DRAFT'].includes(st)) return 0
  if (['PUBLISHED', 'FACTORY_THINKING', 'BUYER_THINKING', 'THINKING', 'REVIEWING'].includes(st)) return 1
  if (['LOCKING', 'SOLUTION_GENERATED', 'SOLUTION_CONFIRMED', 'SOLUTION_SELECTED'].includes(st)) return 2
  if (['CONTRACTED', 'IN_PRODUCTION'].includes(st)) return 3
  if (['COMPLETED', 'CANCELLED', 'FLOW_FAILED'].includes(st)) return 4
  return 0
}
const detailStep = computed(() => {
  const st = detail.demand?.status
  return st === 'COMPLETED' ? 5 : detailStatusIndex(st)
})
function detailReached(n) {
  return detailStatusIndex(detail.demand?.status) >= n
}
const showCoverage = computed(() => {
  const st = detail.demand?.status
  return detailReached(1) && !['SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED', 'CANCELLED', 'FLOW_FAILED'].includes(st)
})
const detailStageGroups = computed(() => {
  const map = new Map()
  for (const p of detail.stages || []) {
    const key = String(p.tenantId ?? p.factoryName ?? p.id)
    if (!map.has(key)) {
      map.set(key, { key, tenantId: p.tenantId, factoryName: p.factoryName || ('工厂-' + p.tenantId), children: [], totalQty: 0, totalAmount: 0 })
    }
    const g = map.get(key)
    g.children.push(p)
    g.totalQty += Number(p.quantity) || 0
    g.totalAmount += Number(p.amount) || 0
  }
  for (const g of map.values()) {
    g.children = nestReworkPeriods(g.children)
    const span = factoryWorkSpan(g.children)
    g.spanStart = span.start
    g.spanEnd = span.end
    g.progress = g.children.length
      ? Math.round(g.children.reduce((s, p) => s + (Number(p.actualProgress) || 0), 0) / g.children.length)
      : 0
  }
  return [...map.values()]
})
const detailExpandKeys = ref([])
function onDetailExpand(row, expandedRows) {
  detailExpandKeys.value = (expandedRows || []).map(r => r.key)
}
function syncDetailExpand() {
  detailExpandKeys.value = detailStageGroups.value
    .filter(g => (g.children || []).some(p => p.status === 'PENDING_REVIEW'))
    .map(g => g.key)
}
const detailProgress = computed(() => {
  const list = detail.stages || []
  if (!list.length) return 0
  return Math.round(list.reduce((s, p) => s + (Number(p.actualProgress) || 0), 0) / list.length)
})
function canOpenNextPeriod(group) {
  return (group.children || []).some(p => p.status === 'WAITING_OPEN' || p.status === 'PENDING')
}
async function openNextPeriod(group) {
  if (!detail.orderId || !group.tenantId) return ElMessage.warning('缺少订单或工厂信息')
  await ElMessageBox.confirm('确认开启该厂下一期提交入口？工厂可立即上报进度与交付，不等待分期开始时间。', '进入下一阶段', { type: 'warning' })
  await openNextPeriodApi(detail.orderId, group.tenantId)
  ElMessage.success('已开启下一期')
  try { detail.stages = (await listStages(detail.orderId)) || [] } catch { /* 保持原列表 */ }
}
const progOpen = ref(false)
const progLogs = ref([])
const progStage = ref(null)
async function openProgress(p) {
  progStage.value = p
  try { progLogs.value = await progressLog(p.id) } catch { progLogs.value = [] }
  progOpen.value = true
}
const adminDeliveryRows = computed(() => {
  try {
    const arr = JSON.parse(detail.demand?.deliveryPlanJson || '[]')
    if (!Array.isArray(arr)) return []
    return arr.map((x) => {
      if (x == null) return { percent: '-', startAt: '-', endAt: '-' }
      let percent = '-'
      if (x.percent != null && Number(x.percent) > 0) {
        percent = Number(x.percent) + '%'
      } else if (String(x.text || '').includes('%')) {
        percent = x.text
      } else if (x.qty != null && Number(x.qty) > 0 && Number(x.qty) <= 100) {
        percent = Number(x.qty) + '%'
      }
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
const returnOpen = ref(false)
const returnReason = ref('')
const returnTarget = ref(null)
const coverage = ref([])
const inspOpen = ref(false)
const inspTarget = ref(null)
const inspBuyer = ref({})
const inspForm = reactive({
  result: 'PASS', sampleCount: 10, failCount: 0, criticalFailCount: 0, generalFailCount: 0,
  deliveredQty: 0, keyDimensions: '',
  meetsRequirement: true, remark: '', quantityOk: true,
})
function requiredSampleOf(n) {
  const q = Number(n) || 0
  if (q <= 0) return 0
  return q
}
const inspIsAql = computed(() => String(inspTarget.value?.inspectMode || '').toUpperCase().includes('AQL'))
const inspRequiredSample = computed(() => {
  const n = Number(inspTarget.value?.requiredSampleCount)
  if (Number.isFinite(n) && n > 0) return n
  const delivered = Number(inspForm.deliveredQty) || 0
  const agreed = Number(inspTarget.value?.quantity) || 0
  return delivered > 0 ? delivered : agreed
})
const inspRuleHint = computed(() => {
  if (inspIsAql.value) {
    return `AQL ${inspTarget.value?.aql || ''}，水平II一次正常。抽 ${inspRequiredSample.value} 件，一般缺陷 Ac=${inspTarget.value?.aqlAc ?? '-'} / Re=${inspTarget.value?.aqlRe ?? '-'}，关键超差 0 件。`
  }
  return `全检实交件数。无关键超差且良率不低于最低良率 ${inspMinYield.value}。`
})
const inspRemainGeneral = computed(() => Math.max(0, (Number(inspForm.sampleCount) || 0) - (Number(inspForm.criticalFailCount) || 0)))
const inspMinYield = computed(() => {
  const v = Number(inspTarget.value?.minYield)
  return Number.isFinite(v) ? v : 0
})
const inspActualYield = computed(() => {
  const n = Number(inspForm.sampleCount) || 0
  const f = (Number(inspForm.criticalFailCount) || 0) + (Number(inspForm.generalFailCount) || 0)
  if (n <= 0) return null
  const y = (n - Math.min(f, n)) / n
  return Math.round(Math.max(0, Math.min(1, y)) * 10000) / 10000
})
const inspToleranceOk = computed(() => {
  const dc = Number(inspForm.criticalFailCount) || 0
  if (dc !== 0) return false
  if (inspIsAql.value) {
    const ac = Number(inspTarget.value?.aqlAc)
    const dg = Number(inspForm.generalFailCount) || 0
    return dg <= (Number.isFinite(ac) ? ac : 0)
  }
  const y = inspActualYield.value
  if (y == null) return false
  return y >= inspMinYield.value
})
watch(() => inspForm.deliveredQty, () => {
  const agreed = Number(inspTarget.value?.quantity) || 0
  const delivered = Number(inspForm.deliveredQty) || 0
  inspForm.quantityOk = agreed > 0 && delivered >= agreed
  inspForm.sampleCount = inspRequiredSample.value
})
watch(() => [inspForm.criticalFailCount, inspForm.generalFailCount, inspForm.quantityOk, inspForm.sampleCount, inspMinYield.value], () => {
  const dc = Number(inspForm.criticalFailCount) || 0
  const dg = Number(inspForm.generalFailCount) || 0
  inspForm.failCount = dc + dg
  inspForm.result = (inspForm.quantityOk && inspToleranceOk.value) ? 'PASS' : 'FAIL'
})
const inspFillReadonly = computed(() => !isInspector || inspTarget.value?.status !== 'PENDING_INSPECTION')
const inspQtyEditable = computed(() => {
  const st = inspTarget.value?.status
  if (isInspector && st === 'PENDING_INSPECTION') return true
  if (!isInspector && st === 'PENDING_REVIEW') return true
  return false
})
const inspCanSubmit = computed(() => isInspector && inspTarget.value?.status === 'PENDING_INSPECTION')
const inspCanApprove = computed(() => !isInspector && inspTarget.value?.status === 'PENDING_REVIEW')
const inspDialogTitle = computed(() => {
  const st = inspTarget.value?.status
  if (st === 'PENDING_REVIEW') return '审核质检单'
  if (['PASS', 'FAIL', 'CLOSED', 'COMPLETED'].includes(st)) return '质检结果'
  return '质检单'
})
function inspectStatusText(row) {
  if (row && typeof row === 'object') return stageProgressLabel(row)
  const s = row
  if (s === 'PENDING_INSPECT_PAY') return '待交质检费'
  if (s === 'PENDING_INSPECTION' || s === 'PENDING_REVIEW' || s === 'INSPECTING') return '质检中'
  if (s === 'PASS') return '结算中'
  if (s === 'FAIL') return '待买家处理'
  if (s === 'CLOSED') return '已关闭'
  if (s === 'COMPLETED') return '已完成'
  return statusText(s)
}
function inspectStatusType(s) {
  if (s === 'PENDING_INSPECT_PAY' || s === 'PENDING_REVIEW' || s === 'FAIL') return 'warning'
  if (s === 'CLOSED') return 'info'
  if (['PASS', 'COMPLETED'].includes(s)) return 'success'
  return ''
}
function statusText(s) { return label(DEMAND_STATUS, s) === s ? label(STAGE_STATUS, s) : label(DEMAND_STATUS, s) }
function stageStart(row) {
  if (['CONTRACTED', 'IN_PRODUCTION'].includes(row.status)) {
    try {
      const arr = JSON.parse(row.deliveryPlanJson || '[]')
      const first = (Array.isArray(arr) ? arr : []).find(x => x && x.startAt)?.startAt
      if (first) return fmtTime(first)
    } catch { /* 无分期计划 */ }
  }
  const v = ({
    PENDING_AUDIT: row.createdAt,
    PUBLISHED: row.publishedAt,
    FACTORY_THINKING: row.factoryThinkingAt,
    BUYER_THINKING: row.buyerThinkingAt,
    SOLUTION_GENERATED: row.buyerThinkingEndAt || row.updatedAt,
    SOLUTION_CONFIRMED: row.updatedAt,
    LOCKING: row.createdAt,
  })[row.status]
  return fmtTime(v)
}
function stageEnd(row) {
  if (['CONTRACTED', 'IN_PRODUCTION'].includes(row.status)) {
    return row.deadlineHard ? String(row.deadlineHard) : '-'
  }
  const v = ({
    PUBLISHED: row.intentionEndAt,
    FACTORY_THINKING: row.factoryThinkingEndAt,
    BUYER_THINKING: row.buyerThinkingEndAt,
    LOCKING: row.lockingEndAt,
  })[row.status]
  return fmtTime(v)
}
function buyerDepositText(s) {
  return ({ NONE: '未收', FROZEN: '已冻结(5%)', DEDUCTED: '已抵扣尾款', RELEASED: '已退回' })[s] || '-'
}
function contractStatusText(s) {
  return ({ PENDING_UPLOAD: '待上传', PENDING_SIGN: '签署中', PENDING_REVIEW: '待买家确认', SIGNED: '已签署' })[s] || s
}
function tagType(s) {
  if (['PENDING_AUDIT','PUBLISHED','LOCKING','RETURNED'].includes(s)) return 'warning'
  if (['THINKING','REVIEWING','FACTORY_THINKING','BUYER_THINKING'].includes(s)) return 'primary'
  if (['SOLUTION_GENERATED','SOLUTION_CONFIRMED','SOLUTION_SELECTED'].includes(s)) return 'success'
  if (['COMPLETED'].includes(s)) return 'success'
  if (['CANCELLED','FLOW_FAILED','FAIL'].includes(s)) return 'danger'
  return 'info'
}
function parse(json) {
  if (Array.isArray(json)) return json
  try { return JSON.parse(json) } catch { return [] }
}
function money(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}
function factoryQuoteLabel(row) {
  const u = row.unitPrice ?? (row.quotes || []).find(q => q.unitPrice != null)?.unitPrice
  if (u == null || u === '') {
    return row.bidStage === 'QUOTED' ? '-' : '未报价'
  }
  return money(u) + ' 元/件'
}
function lineAmount(line) {
  const p = Number(line?.price)
  if (Number.isFinite(p)) return p
  const unit = Number(line?.unitPrice)
  const qty = Number(line?.quantity)
  if (Number.isFinite(unit) && Number.isFinite(qty)) return Math.round(unit * qty * 100) / 100
  return 0
}
function buildConfirmedPlan(list, processes) {
  const empty = { groups: [], total: 0, hint: '' }
  const sols = list || []
  const picked = sols.find(s => Number(s.isFinal) === 1)
    || sols.find(s => Number(s.final) === 1)
    || sols.find(s => s.source === 'FINAL')
    || sols.find(s => (s.type || '') === 'CUSTOM')
  if (!picked) return empty
  const items = parse(picked.finalComboJson || picked.suggestedComboJson)
  const map = new Map()
  for (const it of items) {
    const fid = it.factoryId
    if (fid == null) continue
    const qty = Number(it.quantity) || 0
    const unit = Number(it.unitPrice)
    if (!map.has(fid)) {
      map.set(fid, {
        factoryId: fid,
        factoryName: it.factoryName,
        minQty: it.minQty,
        maxQty: it.maxQty,
        unitPrice: Number.isFinite(unit) ? unit : null,
        quantity: qty,
      })
    } else {
      const g = map.get(fid)
      if (qty > g.quantity) g.quantity = qty
    }
  }
  const groups = [...map.values()].map(g => ({
    ...g,
    price: g.unitPrice != null && g.quantity
      ? Math.round(g.unitPrice * g.quantity * 100) / 100
      : 0,
  }))
  const total = groups.reduce((s, g) => s + (Number(g.price) || 0), 0)
  const kind = (picked.type || '').startsWith('AI') ? '买家已按推荐方案确认' : '买家已按自选分配确认'
  return { groups, total, hint: kind + '，派单将按此分配通知中标厂。' }
}
function parseRationale(raw) {
  if (!raw) return { rationale: '', risks: [] }
  try {
    const o = JSON.parse(raw)
    if (o && typeof o === 'object' && (o.rationale != null || o.risks)) {
      return { rationale: o.rationale || '', risks: Array.isArray(o.risks) ? o.risks : [] }
    }
  } catch { /* 旧数据是纯文本 */ }
  return { rationale: raw, risks: [] }
}

async function loadDemands() {
  demands.value = await listAll()
  try {
    const qs = await inspectQueue()
    const pending = new Set((qs || []).filter(s => s.status === 'PENDING_REVIEW').map(s => Number(s.demandId)))
    for (const d of demands.value) {
      d.pendingReview = pending.has(Number(d.id))
    }
  } catch { /* 质检队列失败时不影响需求列表 */ }
}
async function loadStages() {
  stages.value = await inspectQueue()
}
async function loadFunds() { funds.value = await api.get('/common/all-funds') }
async function loadFundViews() {
  fundOverview.value = await api.get('/fund/overview')
  fundByDemand.value = await api.get('/fund/by-demand')
  fundAccounts.value = await api.get('/fund/accounts')
  fundTrend.value = await api.get('/fund/trend')
  await loadFunds()
}
async function openFundEnterprise(row) {
  fundEnterprise.value = await api.get(`/fund/tenant/${row.tenantId}`)
}
async function openFundDetails(category) {
  const titles = {
    INTENTION: '意向冻结明细',
    DEPOSIT: '保证金冻结明细',
    ESCROW: '平台托管明细',
    COMMISSION: '佣金累计明细',
  }
  fundDetailTitle.value = titles[category] || '资金明细'
  fundDetailRows.value = await api.get(`/fund/details/${category}`)
  fundDetailOpen.value = true
}
async function loadEnterprises() {
  enterprises.value = await api.get('/enterprise/manage', {
    params: { type: entTab.value, keyword: entKeyword.value || undefined },
  })
}
function openEntEdit(row) {
  Object.assign(entEdit, {
    id: row.id,
    name: row.name || '',
    creditCode: row.creditCode || '',
    contactName: row.contactName || '',
    address: row.address || '',
    creditScore: row.creditScore ?? 60,
    authStatus: row.authStatus || 'APPROVED',
    userId: row.userId || null,
    accountPhone: row.accountPhone || '',
    realName: row.realName || '',
    userStatus: row.userStatus || 'ENABLED',
    password: '',
  })
  entEditOpen.value = true
}
async function saveEntEdit() {
  if (!entEdit.name?.trim()) return ElMessage.warning('请填写企业名称')
  if (entEdit.userId && !/^\d{11}$/.test(String(entEdit.accountPhone || '').trim()) && entEdit.accountPhone !== 'admin') {
    return ElMessage.warning('手机号必须为11位数字')
  }
  await api.put(`/enterprise/${entEdit.id}`, {
    name: entEdit.name,
    creditCode: entEdit.creditCode,
    contactName: entEdit.contactName,
    address: entEdit.address,
    creditScore: entEdit.creditScore,
    authStatus: entEdit.authStatus,
  })
  if (entEdit.userId) {
    await api.put(`/enterprise/${entEdit.id}/account`, {
      phone: entEdit.accountPhone,
      realName: entEdit.realName,
      status: entEdit.userStatus,
      password: entEdit.password || null,
    })
  }
  ElMessage.success('已保存')
  entEditOpen.value = false
  loadEnterprises()
}
async function removeEnterprise(row) {
  await ElMessageBox.confirm(
    `确认删除「${row.name}」？将软删企业并停用其登录账号，历史单据保留。`,
    '删除确认',
    { type: 'warning' },
  )
  await api.delete(`/enterprise/${row.id}`)
  ElMessage.success('已删除')
  loadEnterprises()
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

async function openDetail(row) {
  const view = await getDetail(row.id)
  detail.demand = view.demand
  detail.processes = view.processes || []
  detail.attachments = view.attachments || []
  detail.buyer = view.buyer || {}
  detail.quoteStats = view.quoteStats || {}
  detail.factories = []
  detail.contracts = []
  detail.stages = []
  detail.orderId = null
  detail.confirmedPlan = { groups: [], total: 0, hint: '' }
  const st0 = view.demand?.status
  if (['SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED', 'CANCELLED', 'FLOW_FAILED'].includes(st0)) {
    coverage.value = []
  } else {
    const cov = await getCoverage(row.id)
    coverage.value = cov.processes || []
  }
  try {
    detail.factories = (await listBidFactories(row.id)) || []
  } catch { /* 无报名时仍可看详情 */ }
  try {
    const list = await api.get(`/solution/${row.id}`)
    const st = view.demand?.status
    if (['SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(st)) {
      detail.confirmedPlan = buildConfirmedPlan(list, detail.processes)
    }
  } catch { /* 无方案时详情仍可看 */ }
  try {
    const orders = (await listOrders()) || []
    const hit = orders.find(o => Number(o.demandId) === Number(row.id))
    if (hit?.id) {
      detail.orderId = hit.id
      try { detail.contracts = (await listContracts(hit.id)) || [] } catch { detail.contracts = [] }
      try { detail.stages = (await listStages(hit.id)) || [] } catch { detail.stages = [] }
    }
  } catch { /* 无订单时详情仍可看 */ }
  syncDetailExpand()
  detailOpen.value = true
}

let factoryPoll = null
async function refreshBidFactories() {
  const id = detail.demand?.id
  if (!id || !detailOpen.value) return
  try {
    const view = await getDetail(id)
    detail.quoteStats = view.quoteStats || {}
    detail.factories = (await listBidFactories(id)) || []
  } catch { /* 轮询失败不打断详情 */ }
}
watch(detailOpen, (open) => {
  if (factoryPoll) {
    clearInterval(factoryPoll)
    factoryPoll = null
  }
  if (open && !isInspector) {
    factoryPoll = setInterval(refreshBidFactories, 4000)
  }
})
function openReturn(row) {
  returnTarget.value = row
  returnReason.value = ''
  returnOpen.value = true
}
async function submitReturn() {
  if (!returnReason.value.trim()) return ElMessage.warning('退回必须填写原因')
  await returnToBuyer(returnTarget.value.id, returnReason.value.trim())
  ElMessage.success('已退回买家')
  returnOpen.value = false
  detailOpen.value = false
  loadDemands()
}
async function audit(row, result) {
  await ElMessageBox.confirm(result === 'PASS' ? '确认审核通过该需求？通过后将进入意向期。' : '确认驳回该审核？', '审核确认', { type: 'warning' })
  await auditDemand(row.id, result)
  ElMessage.success('操作成功')
  loadDemands()
}
async function endIntention(row) {
  await ElMessageBox.confirm('确认结束意向期并进入工厂思考期？', '结束意向期', { type: 'warning' })
  await api.post(`/flow/${row.id}/end-intention`)
  ElMessage.success('已进入工厂思考期')
  loadDemands()
}
async function endFactoryThinking(row) {
  await ElMessageBox.confirm('是否确认结束工厂思考期', '结束工厂思考期', { type: 'warning' })
  await api.post(`/flow/${row.id}/end-factory-thinking`)
  ElMessage.success('已进入买家思考期')
  loadDemands()
}
async function review(row, pass) {
  await ElMessageBox.confirm(pass ? '确认同意买家取消该需求？' : '确认驳回买家的取消申请？', pass ? '同意取消' : '驳回取消', { type: 'warning' })
  await api.post(`/flow/${row.id}/review?pass=${pass}`)
  ElMessage.success('操作成功')
  loadDemands()
}
async function endLocking(row) {
  await ElMessageBox.confirm('确认结束保证金期？结束后将进入方案阶段。', '结束保证金期', { type: 'warning' })
  await api.post(`/flow/${row.id}/end-locking`)
  ElMessage.success('保证金期已结束，请在本页「方案与合同」审核 AI 方案并下发')
  loadDemands()
}
async function loadSolBuyer(demandId) {
  solBuyer.value = {}
  try {
    const view = await getDetail(demandId)
    solBuyer.value = view.buyer || {}
  } catch { solBuyer.value = {} }
}
async function reviewAi(row) {
  currentSolDemand.value = row
  solutions.value = []
  solDialog.value = true
  aiLoading.value = true
  loadSolBuyer(row.id)
  try {
    let list = await api.get(`/solution/${row.id}`)
    if (!list?.length) {
      list = await api.post(`/solution/${row.id}/generate-ai`, null, { timeout: 180000 })
    }
    decorateSolutions(list)
  } catch (e) {
    ElMessage.error(e.message || '生成方案失败，请稍后重试')
  } finally { aiLoading.value = false }
}
function decorateSolutions(list) {
  const prevPick = Object.fromEntries((solutions.value || []).map(s => [s.id, s._picked]))
  solutions.value = (list || []).map(s => {
    const items = parse(s.finalComboJson || s.suggestedComboJson)
    const pending = s.status !== 'ACTIVE' && s.status !== 'REJECTED' && s.type !== 'CUSTOM'
    return {
      ...s,
      _rationale: parseRationale(s.rationaleJson).rationale,
      _items: items,
      _alloc: factoryAllocFromItems(items),
      _picked: pending ? (prevPick[s.id] ?? true) : false,
    }
  })
}
function canPickSolution(s) {
  return s && s.status !== 'ACTIVE' && s.status !== 'REJECTED' && s.type !== 'CUSTOM'
    && currentSolDemand.value?.status === 'SOLUTION_GENERATED'
}
function solStatusText(st) {
  if (st === 'ACTIVE') return '已推荐给买家'
  if (st === 'REJECTED') return '未下发'
  return '待审核'
}
function solStatusType(st) {
  if (st === 'ACTIVE') return 'success'
  if (st === 'REJECTED') return 'info'
  return 'warning'
}
const pendingReviewSolutions = computed(() => (solutions.value || []).filter(s => canPickSolution(s)))
const pickedSolutionCount = computed(() => pendingReviewSolutions.value.filter(s => s._picked).length)
function factoryAllocFromItems(items) {
  const map = new Map()
  for (const it of items || []) {
    const fid = it.factoryId
    if (fid == null) continue
    const qty = Number(it.quantity) || 0
    const unit = Number(it.unitPrice)
    if (!map.has(fid)) {
      map.set(fid, {
        factoryId: fid,
        factoryName: it.factoryName,
        unitPrice: Number.isFinite(unit) ? unit : null,
        quantity: qty,
        days: Number(it.days) || 0,
        minQty: it.minQty,
        maxQty: it.maxQty,
        reason: it.reason,
        capacityCheck: it.capacityCheck,
        processNo: it.processNo,
      })
    } else {
      const row = map.get(fid)
      if (qty > row.quantity) row.quantity = qty
      if (!row.reason && it.reason) row.reason = it.reason
      if (!row.capacityCheck && it.capacityCheck) row.capacityCheck = it.capacityCheck
    }
  }
  return [...map.values()]
}
function allocSubtotal(row) {
  const unit = Number(row?.unitPrice)
  const qty = Number(row?.quantity)
  if (!Number.isFinite(unit) || !Number.isFinite(qty) || qty <= 0) return 0
  return Math.round(unit * qty * 100) / 100
}
function allocTotal(rows) {
  return (rows || []).reduce((s, r) => s + allocSubtotal(r), 0)
}
function syncAlloc(s, row) {
  for (const it of s._items || []) {
    if (it.factoryId === row.factoryId) {
      it.quantity = row.quantity
      const unit = Number(it.unitPrice)
      if (Number.isFinite(unit)) it.price = Math.round(unit * Number(row.quantity) * 100) / 100
    }
  }
}
async function retryAi() {
  if (!currentSolDemand.value) return
  aiLoading.value = true
  try {
    const list = await api.post(`/solution/${currentSolDemand.value.id}/generate-ai?force=true`, null, { timeout: 180000 })
    decorateSolutions(list)
    ElMessage.success('已重新生成，请审阅')
  } catch (e) {
    ElMessage.error(e.message || '重新生成失败，请稍后重试')
  } finally { aiLoading.value = false }
}
async function saveAiEdits(s, quiet = false) {
  for (const row of s._alloc || []) {
    syncAlloc(s, row)
  }
  const list = await api.post(`/solution/item/${s.id}/save-review`, {
    rationale: s._rationale,
    items: (s._items || []).map(it => ({ factoryId: it.factoryId, processNo: it.processNo, quantity: it.quantity })),
  })
  decorateSolutions(list)
  if (!quiet) ElMessage.success('已保存修改')
}
async function publishPicked() {
  const picked = pendingReviewSolutions.value.filter(s => s._picked)
  if (!picked.length) return ElMessage.warning('请勾选要下发给买家的方案')
  const names = picked.map(s => s.type || ('#' + s.id)).join('、')
  await ElMessageBox.confirm(
    `将一次性下发 ${names} 共 ${picked.length} 套方案给买家。未勾选的待审方案不会出现在买家端，避免分次下发导致两端看到的方案不一致。`,
    '下发选中方案',
    { type: 'warning' }
  )
  for (const s of picked) {
    await saveAiEdits(s, true)
  }
  const demandId = currentSolDemand.value.id
  await api.post(`/solution/${demandId}/publish-batch`, picked.map(s => s.id))
  ElMessage.success('已一并下发 ' + names)
  decorateSolutions(await api.get(`/solution/${demandId}`))
  loadDemands()
}
async function genAiFromDialog() {
  await retryAi()
}
async function openReplace(s, row) {
  currentSol.value = s
  currentProcess.value = row
  const item = (s._items || []).find(i => i.factoryId === row.factoryId)
  alts.value = await api.get(`/solution/item/${s.id}/alternatives`, { params: { processNo: item?.processNo || row.processNo } })
  repOpen.value = true
}
async function viewSolutions(row) {
  currentSolDemand.value = row
  solutions.value = []
  solDialog.value = true
  aiLoading.value = true
  loadSolBuyer(row.id)
  try {
    decorateSolutions(await api.get(`/solution/${row.id}`))
  } catch (e) {
    ElMessage.error(e.message || '加载方案失败')
  } finally { aiLoading.value = false }
}
async function doReplace(row) {
  const fromId = currentProcess.value.factoryId
  const processNos = [...new Set((currentSol.value._items || [])
    .filter(i => i.factoryId === fromId)
    .map(i => i.processNo)
    .filter(n => n != null))]
  for (const processNo of processNos) {
    await api.post(`/solution/item/${currentSol.value.id}/replace-factory`, {
      processNo,
      factoryId: row.factoryId,
      fromFactoryId: fromId,
    })
  }
  ElMessage.success('已换厂')
  repOpen.value = false
  decorateSolutions(await api.get(`/solution/${currentSol.value.demandId}`))
}
async function loadAllContracts() {
  allContracts.value = await api.get('/order/contracts/all', { params: { status: contractStatus.value || undefined } })
}
async function loadOverview() { overview.value = await api.get('/audit/overview') }
function goHandle(row) {
  if (['PENDING_AUDIT', 'RETURNED'].includes(row.status)) demandTab.value = 'audit'
  else if (['LOCKING', 'SOLUTION_GENERATED', 'SOLUTION_CONFIRMED', 'SOLUTION_SELECTED'].includes(row.status)) demandTab.value = 'quote'
  else if (['CONTRACTED', 'IN_PRODUCTION'].includes(row.status)) demandTab.value = 'fulfill'
  else if (['COMPLETED', 'CANCELLED', 'FLOW_FAILED'].includes(row.status)) demandTab.value = 'close'
  else demandTab.value = 'match'
  active.value = 'overview'
  loadDemands()
}
async function loadAudit() {
  auditLogs.value = await api.get('/audit/logs', { params: { action: auditAction.value || undefined, limit: 200 } })
}
function auditTargetName(type) {
  return ({ DEMAND: '需求', ORDER: '订单', SOLUTION: '方案', STAGE: '工单', CONTRACT: '合同' })[type] || type || '—'
}
function auditTargetTag(type) {
  return ({ DEMAND: '', ORDER: 'warning', SOLUTION: 'success', STAGE: 'info', CONTRACT: 'danger' })[type] || 'info'
}
function auditDetail(row) {
  const raw = row?.afterJson
  if (raw == null || raw === '') return ''
  const s = String(raw).trim()
  if (s.startsWith('{')) {
    try {
      const obj = JSON.parse(s)
      if (obj && obj.detail != null && obj.detail !== '') return String(obj.detail)
    } catch { /* 非 JSON 则原样 */ }
  }
  return s
}
async function openFactoryProfile(id) {
  factoryProfile.value = await api.get(`/enterprise/${id}/public-profile`)
  factoryProfileOpen.value = true
}
async function openInspect(row) {
  const filling = isInspector && row.status === 'PENDING_INSPECTION'
  const reviewing = !isInspector && row.status === 'PENDING_REVIEW'
  if (!filling && !reviewing && !canShowInspectReport(row)) {
    return ElMessage.warning('质检尚未完成，暂无质检报告')
  }
  inspTarget.value = row
  inspBuyer.value = {}
  if (!isInspector && row.demandId) {
    try {
      const view = await getDetail(row.demandId)
      inspBuyer.value = view.buyer || {}
    } catch { inspBuyer.value = {} }
  }
  const agreed = Number(row.quantity) || 0
  const reported = row.deliveredQty != null ? Number(row.deliveredQty) : agreed
  inspForm.result = ''
  inspForm.deliveredQty = reported
  inspForm.quantityOk = reported >= agreed
  inspForm.sampleCount = Number(row.requiredSampleCount) > 0
    ? Number(row.requiredSampleCount)
    : requiredSampleOf(reported || agreed)
  inspForm.failCount = 0
  inspForm.criticalFailCount = 0
  inspForm.generalFailCount = 0
  inspForm.keyDimensions = ''
  inspForm.meetsRequirement = true
  inspForm.remark = ''
  if (!filling) {
    try {
      const ins = await inspectionOf(row.id)
      if (!ins) {
        return ElMessage.warning('质检尚未完成，暂无质检报告')
      }
      const r = parseInspectReport(ins.reportJson)
      inspForm.result = ins.result || inspForm.result
      inspForm.sampleCount = r.sampleCount ?? inspForm.sampleCount
      inspForm.failCount = r.failCount ?? inspForm.failCount
      inspForm.criticalFailCount = r.criticalFailCount ?? 0
      inspForm.generalFailCount = r.generalFailCount ?? 0
      inspForm.deliveredQty = r.deliveredQty ?? reported
      inspForm.keyDimensions = r.keyDimensions || ''
      inspForm.meetsRequirement = r.meetsRequirement !== false
      inspForm.remark = r.remark || ''
      inspForm.quantityOk = r.quantityOk != null ? !!r.quantityOk : ((Number(inspForm.deliveredQty) || 0) >= agreed)
    } catch {
      return ElMessage.warning('质检尚未完成，暂无质检报告')
    }
  }
  inspOpen.value = true
}
function parseInspectReport(raw) {
  try { return raw ? JSON.parse(raw) : {} } catch { return {} }
}
async function submitInspect() {
  if (!inspForm.result) return ElMessage.warning('请选择是否合格')
  if (inspForm.quantityOk == null) return ElMessage.warning('请选择数量是否达标')
  if (inspActualYield.value == null) return ElMessage.warning('请按规则填写抽检数据')
  const dc = Number(inspForm.criticalFailCount) || 0
  const dg = Number(inspForm.generalFailCount) || 0
  if (dc + dg > (Number(inspForm.sampleCount) || 0)) return ElMessage.warning('不合格件数不能超过抽检数')
  await inspectStage(inspTarget.value.id, {
    ...inspForm,
    failCount: dc + dg,
    actualYield: inspActualYield.value,
    meetsRequirement: inspForm.result === 'PASS',
  })
  ElMessage.success('质检单已提交，等待运营审核后发给买家')
  inspOpen.value = false
  loadStages().catch(ignore)
  refreshDetailStages()
}
async function approveInspectSheet() {
  if (!inspForm.result) return ElMessage.warning('请选择是否合格')
  if (inspForm.quantityOk == null) return ElMessage.warning('请选择数量是否达标')
  await approveInspect(inspTarget.value.id, {
    ...inspForm,
    failCount: (Number(inspForm.criticalFailCount) || 0) + (Number(inspForm.generalFailCount) || 0),
    actualYield: inspActualYield.value,
    meetsRequirement: inspForm.result === 'PASS',
  })
  ElMessage.success(inspForm.result === 'PASS' ? '已审核可收款并通知买家支付本阶段费用' : '已审核为不合格，等待买家选择让步、返工或关闭')
  inspOpen.value = false
  loadStages().catch(ignore)
  refreshDetailStages()
}
async function refreshDetailStages() {
  if (!detail.orderId) return
  try { detail.stages = (await listStages(detail.orderId)) || [] } catch { /* 保持原列表 */ }
}
async function createUser() {
  await userFormRef.value.validate()
  await api.post('/auth/create-user', { ...userForm })
  ElMessage.success('账号已创建')
}
function onUserPhone(v) {
  userForm.phone = String(v ?? '').replace(/\D/g, '').slice(0, 11)
}
function onEntPhone(v) {
  entEdit.accountPhone = String(v ?? '').replace(/\D/g, '').slice(0, 11)
}

async function downloadPhoto(id) {
  try {
    const { blob, fileName } = await fetchAttachment(id)
    saveBlob(blob, fileName)
  } catch (e) {
    ElMessage.error(e.message || '无法下载照片')
  }
}

const pwdOpen = ref(false)
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
async function changePwd() {
  if (!pwd.oldPassword) return ElMessage.warning('请填写原密码')
  if (!pwd.newPassword || pwd.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (pwd.newPassword !== pwd.confirm) return ElMessage.warning('两次输入的新密码不一致')
  await api.post('/auth/change-password', { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
  ElMessage.success('密码已修改，下次登录请用新密码')
  pwdOpen.value = false
  pwd.oldPassword = pwd.newPassword = pwd.confirm = ''
}

function logout() { clearAuth(); router.push('/login') }

function ignore() { /* 接口错误已由 axios 拦截器提示 */ }

watch(() => fundTrendPoints.value.line, async (line) => {
  if (!line) return
  await nextTick()
  drawStroke(trendLine.value, 1.1)
  fillArea(trendArea.value, 0.35)
})

watch(active, (v) => {
  if (v !== 'funds') {
    fundEnterprise.value = null
    fundTrendHover.value = null
  }
})

onMounted(() => {
  if (isInspector) {
    loadStages().catch(ignore)
  } else {
    loadDemands().catch(ignore)
    loadFundViews().catch(ignore)
    loadEnterprises().catch(ignore)
    loadAudit().catch(ignore)
  }
})
onUnmounted(() => {
  if (factoryPoll) {
    clearInterval(factoryPoll)
    factoryPoll = null
  }
})
</script>

<style scoped>
.op-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.op-cell :deep(.el-button + .el-button) {
  margin-left: 0;
}
.aside { background:#304156; }
.logo { color:#fff; text-align:center; padding:16px 0 2px; font-weight:bold; }
.sub-logo { color:#bfcbd9; text-align:center; font-size:12px; padding-bottom:12px; }
.clickable { cursor: pointer; }
.plan-text { display:inline-block; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; vertical-align:bottom; }
.ai-sol-body { min-height: 280px; position: relative; }
.ai-skel { padding: 8px 0 16px; }
.ai-skel-card { padding: 12px; margin-bottom: 12px; border: 1px solid #ebeef5; border-radius: 8px; background: #fafafa; }
.ai-skel-hint { text-align: center; margin-top: 8px; }
.sol-buyer { margin: 0 0 12px; padding: 10px 12px; background: #f5f7fa; border-radius: 6px; }
.publish-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  padding: 12px;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  position: sticky;
  bottom: 0;
}
.toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:18px; }
.title { font-size:16px; font-weight:bold; }
.audit-obj { display: inline-flex; align-items: center; gap: 8px; }
.audit-obj-id { font-variant-numeric: tabular-nums; color: #303133; font-weight: 600; }
.credit-cell,
.account-cell { display: flex; flex-direction: column; justify-content: center; gap: 4px; min-height: 38px; line-height: 1.2; }
.credit-cell strong,
.account-cell strong { color: #0d253d; font-size: 13px; font-weight: 600; font-variant-numeric: tabular-nums; }
.credit-cell span,
.account-cell span { color: #7b8798; font-size: 11px; }
.enterprise-table :deep(.el-table__body-wrapper) { overflow-x: auto; }
.enterprise-table :deep(td.el-table__cell) {
  height: 58px;
  padding-top: 0;
  padding-bottom: 0;
  vertical-align: middle;
}
.enterprise-table :deep(td.el-table__cell > .cell) {
  min-height: 58px;
  display: flex;
  align-items: center;
}
.enterprise-table :deep(td.is-center > .cell) {
  justify-content: center;
}
.enterprise-table :deep(.pwd-cell) {
  min-height: 32px;
  align-items: center;
}
.file-preview { white-space: pre-wrap; word-break: break-word; margin: 0; font-size: 13px; line-height: 1.6; }
.hint { color: #909399; font-size: 12px; margin: 4px 0 8px; }
.dt-2 { line-height: 1.35; }
.dt-clock { color: #606266; font-size: 12px; }
.pwd-cell { display: inline-flex; align-items: center; gap: 6px; }
.pwd-text { font-variant-numeric: tabular-nums; }
.pwd-mask { letter-spacing: -3px; }
.pwd-eye { cursor: pointer; color: #909399; font-size: 16px; }
.pwd-eye:hover { color: #533afd; }
.confirmed-plan { margin-top: 16px; }
.proc-block { margin-bottom: 14px; padding-bottom: 4px; border-bottom: 1px solid #ebeef5; }
.proc-block:last-of-type { border-bottom: none; }
.proc-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.proc-title { font-weight: 600; font-size: 13px; color: #303133; }
.alloc-ok { font-size: 12px; color: #67c23a; }
.line { display: flex; align-items: center; gap: 8px; flex-wrap: nowrap; margin-bottom: 6px; }
.line.head { color: #909399; font-size: 12px; margin-bottom: 4px; }
.col-fac { flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-range { flex: 0 0 100px; width: 100px; color: #909399; font-size: 12px; white-space: nowrap; }
.col-qty { flex: 0 0 72px; width: 72px; text-align: right; }
.col-amt { flex: 0 0 96px; width: 96px; text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
.total-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
}
.total-bar strong { font-size: 16px; color: #303133; font-variant-numeric: tabular-nums; }
.kpi { color:#64748d; font-size:12px; }
.kpi span { float: right; color: #6d5df5; font-size: 10px; opacity: 0; transition: opacity 160ms ease; }
.kpi-v { font-size:20px; font-weight:600; margin-top:8px; color:#0d253d; }
.fund-kpi-card { cursor: pointer; transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease; }
.fund-kpi-card:hover,
.fund-kpi-card:focus-visible {
  transform: translateY(-2px);
  border-color: rgba(83, 58, 253, 0.22) !important;
  box-shadow: 0 12px 28px rgba(48, 41, 120, 0.1) !important;
  outline: none;
}
.fund-kpi-card:hover .kpi span,
.fund-kpi-card:focus-visible .kpi span { opacity: 1; }
.trend-card {
  margin-bottom: 20px;
  padding-bottom: 4px;
  background: rgba(255, 255, 255, 0.72) !important;
  border: 1px solid rgba(255, 255, 255, 0.8) !important;
}
.trend-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
  color: #0d253d;
}
.trend-head span { display: block; font-size: 15px; font-weight: 600; }
.trend-head small { display: block; margin-top: 5px; color: #64748d; font-size: 11px; font-weight: 400; }
.trend-head strong { font-size: 24px; font-weight: 500; letter-spacing: -0.025em; font-variant-numeric: tabular-nums; }
.trend-plot { display: flex; align-items: stretch; gap: 12px; }
.trend-y {
  width: 52px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  color: #64748d;
  font-size: 12px;
  padding: 12px 0;
  font-variant-numeric: tabular-nums;
}
.trend-plot-main { flex: 1; min-width: 0; }
.trend-svg-wrap { position: relative; cursor: crosshair; }
.trend-svg {
  width: 100%;
  height: 220px;
  background: linear-gradient(180deg, #f4f2ff, #f8faff);
  border-radius: 12px;
  display: block;
}
.trend-tip {
  position: absolute;
  top: 10px;
  z-index: 2;
  pointer-events: none;
  background: rgba(28, 30, 84, 0.88);
  color: #fff;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  box-shadow: 0 8px 22px rgba(28, 30, 84, 0.22);
}
.trend-tip-amt { font-weight: 600; font-variant-numeric: tabular-nums; margin-top: 2px; }
.trend-axis { display: flex; justify-content: space-between; color: #64748d; font-size: 12px; margin-top: 10px; }
.trend-empty {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(83, 58, 253, 0.08);
  border-radius: 14px;
  background:
    radial-gradient(circle at 50% 20%, rgba(126, 177, 255, 0.18), transparent 48%),
    linear-gradient(180deg, #f7f6ff, #fbfcff);
}
.trend-empty-mark { height: 42px; display: flex; align-items: flex-end; gap: 5px; margin-bottom: 16px; }
.trend-empty-mark i { width: 5px; border-radius: 8px; background: linear-gradient(180deg, #7c6ffe, #533afd); opacity: 0.72; }
.trend-empty-mark i:nth-child(1) { height: 13px; }
.trend-empty-mark i:nth-child(2) { height: 25px; }
.trend-empty-mark i:nth-child(3) { height: 18px; }
.trend-empty-mark i:nth-child(4) { height: 36px; }
.trend-empty-mark i:nth-child(5) { height: 28px; }
.trend-empty strong { color: #273951; font-size: 14px; font-weight: 600; }
.trend-empty span { margin-top: 7px; color: #7b8798; font-size: 12px; }
.fund-kpis { margin-bottom: 20px; }
</style>
