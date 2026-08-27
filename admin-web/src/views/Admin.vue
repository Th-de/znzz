<template>
  <el-container style="height:100vh">
    <el-aside width="200px" class="aside">
      <div class="logo">平台运营端</div>
      <div style="padding:0 16px 12px"><el-button size="small" @click="logout">退出</el-button></div>
      <el-menu :default-active="active" @select="active=$event" background-color="#304156" text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item index="demands">📋 需求管理</el-menu-item>
        <el-menu-item index="solutions">📊 方案查看</el-menu-item>
        <el-menu-item index="contracts">📝 合同审核</el-menu-item>
        <el-menu-item index="inspect">🔍 工单质检</el-menu-item>
        <el-menu-item index="funds">💰 资金流水</el-menu-item>
        <el-menu-item index="enterprises">👥 用户信息管理</el-menu-item>
        <el-menu-item index="users" v-if="isSuper">👤 账号管理</el-menu-item>
      </el-menu>
    </el-aside>

    <el-main>
      <!-- 需求管理 -->
      <div v-if="active==='demands'">
        <div class="toolbar">
          <span class="title">需求管理</span>
          <div>
            <el-button @click="loadDemands">刷新</el-button>
            <el-button @click="logout">退出</el-button>
          </div>
        </div>
        <el-tabs v-model="demandTab">
          <el-tab-pane :label="'申请发布 (' + countGroup('audit') + ')'" name="audit" />
          <el-tab-pane :label="'退回修改 (' + countGroup('returned') + ')'" name="returned" />
          <el-tab-pane :label="'意向匹配 (' + countGroup('match') + ')'" name="match" />
          <el-tab-pane :label="'报价与方案 (' + countGroup('quote') + ')'" name="quote" />
          <el-tab-pane :label="'履约 (' + countGroup('fulfill') + ')'" name="fulfill" />
          <el-tab-pane :label="'收口 (' + countGroup('close') + ')'" name="close" />
        </el-tabs>
        <el-table :data="filteredDemands" border>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="标题" width="200" />
          <el-table-column prop="quantity" label="数量" width="100" />
          <el-table-column prop="status" label="当前状态" width="170">
            <template #default="{row}"><el-tag :type="tagType(row.status)">{{ statusText(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="锁价截止" width="160">
            <template #default="{row}">{{ row.status==='LOCKING' ? (row.lockingEndAt || '-') : '-' }}</template>
          </el-table-column>
          <el-table-column label="可执行操作" min-width="420">
            <template #default="{ row }">
              <el-button size="small" @click="openDetail(row)">详情</el-button>
              <el-button size="small" type="primary" plain @click="openPanorama(row)">全景</el-button>
              <el-button v-if="row.status==='PENDING_AUDIT'" size="small" type="primary" @click="audit(row,'PASS')">审核通过</el-button>
              <el-button v-if="['PENDING_AUDIT','PUBLISHED'].includes(row.status)" size="small" type="danger" @click="openReturn(row)">退回修改</el-button>
              <el-button v-if="row.status==='PUBLISHED'" size="small" type="warning" @click="endIntention(row)">结束意向期</el-button>
              <el-button v-if="row.status==='REVIEWING'" size="small" type="primary" @click="review(row,true)">同意取消</el-button>
              <el-button v-if="row.status==='REVIEWING'" size="small" type="danger" @click="review(row,false)">驳回取消</el-button>
              <el-button v-if="row.status==='LOCKING'" size="small" type="warning" @click="endLocking(row)">结束保证金期</el-button>
              <el-button v-if="row.status==='SOLUTION_GENERATED'" size="small" type="success" @click="genAi(row)">生成AI综合方案</el-button>
              <el-button v-if="row.status==='SOLUTION_CONFIRMED'" size="small" type="primary" @click="dispatchOrder(row)">派单</el-button>
              <el-tag v-if="['THINKING','CONTRACTED','IN_PRODUCTION','COMPLETED','CANCELLED','FLOW_FAILED','RETURNED'].includes(row.status)" type="info" size="small">{{ row.status==='RETURNED' ? '待买家修改' : '等待其他角色操作' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-alert style="margin-top:10px" type="info" :closable="false"
          title="每次发布先申请发布，运营通过后工厂才能看到。退回改完须再审。已发布买家不能改字段，只能取消后发新单。" />
        <el-drawer v-model="detailOpen" title="需求详情" size="640px">
          <el-alert v-if="detail.demand?.returnReason" type="warning" :closable="false" :title="'退回原因：' + detail.demand.returnReason" style="margin-bottom:12px" />
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="状态">{{ label(DEMAND_STATUS, detail.demand?.status) }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ detail.demand?.title }}</el-descriptions-item>
            <el-descriptions-item label="产品">{{ detail.demand?.productName }}</el-descriptions-item>
            <el-descriptions-item label="数量">{{ detail.demand?.quantity }}</el-descriptions-item>
            <el-descriptions-item label="硬交期">{{ detail.demand?.deadlineHard }}</el-descriptions-item>
            <el-descriptions-item label="交付地址">{{ detail.demand?.deliveryAddress }}</el-descriptions-item>
            <el-descriptions-item label="意向截止">{{ detail.demand?.intentionEndAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源需求">{{ detail.demand?.sourceDemandId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="取消原因">{{ detail.demand?.cancelReason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ detail.demand?.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-collapse style="margin-top:12px">
            <el-collapse-item title="技术要求（材料/公差/认证等）" name="tech">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="材料">{{ detail.demand?.material }}</el-descriptions-item>
                <el-descriptions-item label="公差">{{ detail.demand?.tolerance || '-' }}</el-descriptions-item>
                <el-descriptions-item label="AQL">{{ detail.demand?.aql }}</el-descriptions-item>
                <el-descriptions-item label="认证">{{ detail.demand?.certification || '-' }}</el-descriptions-item>
                <el-descriptions-item label="最低良率">{{ detail.demand?.minYield }}</el-descriptions-item>
                <el-descriptions-item label="最低信用">{{ detail.demand?.minCreditScore }}</el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
          </el-collapse>
          <h4>工序覆盖</h4>
          <CoverageBars :items="coverage" />
          <h4>工序</h4>
          <el-table :data="detail.processes" border size="small">
            <el-table-column prop="processNo" label="#" width="50" />
            <el-table-column prop="processName" label="工序" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column prop="requirement" label="要求" />
          </el-table>
          <h4>附件</h4>
          <el-table :data="detail.attachments" border size="small">
            <el-table-column prop="fileName" label="文件名" />
            <el-table-column prop="fileType" label="类型" width="80" />
            <el-table-column prop="fileSize" label="大小" width="100" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="viewFile(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top:16px" v-if="['PENDING_AUDIT','PUBLISHED'].includes(detail.demand?.status)">
            <el-button type="danger" @click="openReturn(detail.demand)">退回修改</el-button>
          </div>
        </el-drawer>
        <el-drawer v-model="panoOpen" title="需求全景" size="720px">
          <el-descriptions :column="1" border size="small" title="阶段与买家">
            <el-descriptions-item label="阶段">{{ label(DEMAND_STATUS, pano.stage) }}</el-descriptions-item>
            <el-descriptions-item label="买家">{{ pano.buyer?.name }}</el-descriptions-item>
            <el-descriptions-item label="买家余额">¥ {{ pano.buyer?.balance }} / 冻结 ¥ {{ pano.buyer?.frozen }}</el-descriptions-item>
            <el-descriptions-item label="订单">{{ pano.orderId || '-' }} {{ pano.orderStatus || '' }}</el-descriptions-item>
            <el-descriptions-item label="整体进度">{{ pano.progressPercent ?? 0 }}%</el-descriptions-item>
          </el-descriptions>
          <h4 v-if="(pano.stages || []).length">工单进度</h4>
          <el-table v-if="(pano.stages || []).length" :data="pano.stages" border size="small" style="margin-bottom:12px">
            <el-table-column prop="processName" label="工序" />
            <el-table-column prop="factoryName" label="工厂" />
            <el-table-column label="完成" width="110">
              <template #default="{ row }">{{ row.doneQty || 0 }} / {{ row.quantity || 0 }}</template>
            </el-table-column>
            <el-table-column label="进度" width="140">
              <template #default="{ row }">
                <el-progress :percentage="row.progress || 0" :stroke-width="10" />
              </template>
            </el-table-column>
            <el-table-column prop="promisedDate" label="承诺交期" width="120" />
            <el-table-column prop="status" label="状态" width="110" />
          </el-table>
          <h4>报名 / 工厂 / 设备</h4>
          <el-table :data="pano.bids || []" border size="small">
            <el-table-column prop="processNo" label="工序#" width="70" />
            <el-table-column prop="factoryName" label="工厂" min-width="120" />
            <el-table-column prop="status" label="报名状态" width="100" />
            <el-table-column prop="intentionStatus" label="意向金" width="100" />
            <el-table-column prop="price" label="锁价" width="90" />
            <el-table-column label="设备" min-width="160">
              <template #default="{ row }">
                {{ (row.devices || []).map(d => d.name).join('、') || '-' }}
              </template>
            </el-table-column>
          </el-table>
          <h4>资金小计 ¥ {{ pano.fundSubtotal || 0 }}</h4>
          <el-table :data="pano.funds || []" border size="small">
            <el-table-column prop="enterpriseName" label="企业" min-width="120" />
            <el-table-column label="类型" width="100">
              <template #default="{row}">{{ label(FUND_TYPE, row.type) }}</template>
            </el-table-column>
            <el-table-column label="方向" width="90">
              <template #default="{row}">{{ label(FUND_DIR, row.direction) }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="100" />
          </el-table>
        </el-drawer>
        <el-dialog v-model="fileOpen" :title="fileTitle" width="720px">
          <pre class="file-preview">{{ fileText }}</pre>
        </el-dialog>
        <el-dialog v-model="returnOpen" title="退回买家修改" width="480px">
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
      </div>

      <!-- 方案查看 -->
      <div v-if="active==='solutions'">
        <div class="toolbar"><span class="title">方案查看</span><el-button @click="loadDemands">刷新</el-button></div>
        <el-table :data="demands.filter(d=>['SOLUTION_GENERATED','SOLUTION_CONFIRMED','SOLUTION_SELECTED','CONTRACTED','IN_PRODUCTION','COMPLETED'].includes(d.status))" border @row-click="viewSolutions">
          <el-table-column prop="id" label="需求ID" width="80" />
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="status" label="状态" width="150" />
          <el-table-column label="操作" width="120">
            <template #default="{row}"><el-button size="small" @click="viewSolutions(row)">查看方案</el-button></template>
          </el-table-column>
        </el-table>
        <el-dialog v-model="solDialog" title="方案详情（全部先审再下发）" width="920px">
          <el-button type="warning" :loading="aiLoading" style="margin-bottom:10px" @click="genAiFromDialog">生成 AI 综合方案</el-button>
          <div v-for="s in solutions" :key="s.id" style="margin-bottom:16px">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;flex-wrap:wrap">
              <el-tag type="primary">方案 {{ s.type }}</el-tag>
              <el-tag size="small" :type="s.status==='ACTIVE'?'success':'warning'">{{ s.status==='ACTIVE' ? '已下发买家' : '待下发' }}</el-tag>
              <el-button v-if="s.status!=='ACTIVE'" size="small" type="success" @click="publish(s)">下发给买家</el-button>
            </div>
            <p class="hint">{{ parseRationale(s.rationaleJson).rationale }}</p>
            <el-alert
              v-for="(r, i) in parseRationale(s.rationaleJson).risks"
              :key="s.id + '-r-' + i"
              type="warning"
              :closable="false"
              :title="r"
              style="margin-bottom:8px"
            />
            <el-table :data="parse(s.finalComboJson || s.suggestedComboJson)" size="small" border>
              <el-table-column prop="processName" label="工序" width="80" />
              <el-table-column prop="factoryName" label="工厂" width="140" />
              <el-table-column prop="reason" label="选厂理由" min-width="160" />
              <el-table-column prop="capacityCheck" label="产能核算" min-width="160" />
              <el-table-column prop="price" label="价格" width="90" />
              <el-table-column prop="days" label="工期" width="60" />
              <el-table-column prop="yieldRate" label="良率" width="70" />
              <el-table-column v-if="currentSolDemand && currentSolDemand.status==='SOLUTION_GENERATED'" label="修改" width="80">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="openReplace(s, row)">换厂</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-dialog>
        <el-dialog v-model="repOpen" title="换厂（价格不变）" width="640px">
          <p>工序 {{ currentProcess?.processName }}，原价 {{ currentProcess?.price }} 不可改。</p>
          <el-table :data="alts" size="small" border>
            <el-table-column prop="factoryName" label="工厂" />
            <el-table-column prop="creditScore" label="信用" width="70" />
            <el-table-column prop="maxQty" label="承接量" width="80" />
            <el-table-column prop="dailyCapacity" label="日产能合计" width="110" />
            <el-table-column label="" width="80">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="doReplace(row)">选用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-dialog>
      </div>

      <!-- 合同审核 -->
      <div v-if="active==='contracts'">
        <div class="toolbar"><span class="title">待确认合同（只确认该厂这份已双签，不审正文）</span><el-button @click="loadContracts">刷新</el-button></div>
        <el-table :data="contracts" border>
          <el-table-column prop="orderId" label="订单" width="80" />
          <el-table-column prop="factoryName" label="工厂" />
          <el-table-column prop="processNames" label="工序" />
          <el-table-column prop="attachmentId" label="附件" width="80" />
          <el-table-column prop="status" label="状态" width="140" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button v-if="row.status==='PENDING_REVIEW'" size="small" type="success" @click="approve(row)">确认该厂已签</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 工单质检 -->
      <div v-if="active==='inspect'">
        <div class="toolbar"><span class="title">工单质检</span><el-button @click="loadStages">刷新</el-button></div>
        <el-table :data="sortedStages" border>
          <el-table-column prop="id" label="工单ID" width="70" />
          <el-table-column prop="orderId" label="订单ID" width="70" />
          <el-table-column prop="factoryName" label="工厂" min-width="120" />
          <el-table-column prop="processName" label="工序" />
          <el-table-column label="进度" width="140">
            <template #default="{row}">
              <el-progress :percentage="row.actualProgress || 0" :stroke-width="10" />
              <el-tag v-if="overdueDays(row)" type="danger" size="small">逾期 {{ overdueDays(row) }} 天</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="工钱" width="90" />
          <el-table-column label="托管" width="110">
            <template #default="{row}">{{ label(ESCROW_STATUS, row.escrowStatus) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="140">
            <template #default="{row}"><el-tag :type="row.status==='PASS'?'success':row.status==='FAIL'?'danger':'info'">{{ statusText(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button size="small" @click="openProgress(row)">进度记录</el-button>
              <el-button v-if="row.status==='PENDING_INSPECTION'" size="small" type="success" @click="openInspect(row,'PASS')">合格</el-button>
              <el-button v-if="row.status==='PENDING_INSPECTION'" size="small" type="danger" @click="openInspect(row,'FAIL')">不合格</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-dialog v-model="progOpen" title="进度记录" width="560px">
          <el-timeline>
            <el-timeline-item v-for="p in progLogs" :key="p.id" :timestamp="p.createdAt">
              {{ p.doneQty }} 件 · {{ p.progress }}% · {{ p.remark }}
              <div v-if="p.attachmentId">
                <el-button size="small" link type="primary" @click="downloadPhoto(p.attachmentId)">{{ p.fileName || '下载照片' }}</el-button>
              </div>
            </el-timeline-item>
          </el-timeline>
          <p v-if="!progLogs.length">暂无上报</p>
        </el-dialog>
        <el-dialog v-model="inspOpen" :title="inspForm.result==='PASS' ? '合格报告' : '不合格报告'" width="520px">
          <el-form label-width="120px">
            <el-form-item label="抽检数"><el-input-number v-model="inspForm.sampleCount" :min="0" /></el-form-item>
            <el-form-item label="不合格数"><el-input-number v-model="inspForm.failCount" :min="0" /></el-form-item>
            <el-form-item label="关键尺寸结论"><el-input v-model="inspForm.keyDimensions" /></el-form-item>
            <el-form-item label="是否满足需求"><el-switch v-model="inspForm.meetsRequirement" /></el-form-item>
            <el-form-item label="备注"><el-input v-model="inspForm.remark" type="textarea" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="inspOpen=false">取消</el-button>
            <el-button :type="inspForm.result==='PASS'?'success':'danger'" @click="submitInspect">提交</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- 资金流水 -->
      <div v-if="active==='funds'">
        <div class="toolbar"><span class="title">资金总览</span><el-button @click="loadFundViews">刷新</el-button></div>
        <el-row :gutter="12" style="margin-bottom:16px">
          <el-col :span="4"><el-card shadow="never"><div class="kpi">意向冻结</div><div class="kpi-v">¥ {{ fundOverview.intentionFrozenNet || 0 }}</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="kpi">保证金冻结</div><div class="kpi-v">¥ {{ fundOverview.depositFrozenNet || 0 }}</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="kpi">托管在途</div><div class="kpi-v">¥ {{ fundOverview.escrowHeld || 0 }}</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="kpi">平台暂存</div><div class="kpi-v">¥ {{ fundOverview.impoundBalance || 0 }}</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="kpi">佣金累计</div><div class="kpi-v">¥ {{ fundOverview.commissionTotal || 0 }}</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="kpi">平台余额</div><div class="kpi-v">¥ {{ fundOverview.platformBalance || 0 }}</div></el-card></el-col>
        </el-row>
        <el-tabs v-model="fundTab">
          <el-tab-pane label="按需求分组" name="byDemand">
            <el-collapse>
              <el-collapse-item v-for="g in fundByDemand" :key="g.demandId" :title="'#' + g.demandId + ' ' + (g.demandTitle || '') + ' · 小计 ¥' + g.subtotal">
                <el-table :data="g.flows" border size="small">
                  <el-table-column prop="enterpriseName" label="企业" min-width="120" />
                  <el-table-column label="类型" width="100"><template #default="{row}">{{ label(FUND_TYPE, row.type) }}</template></el-table-column>
                  <el-table-column label="方向" width="90"><template #default="{row}">{{ label(FUND_DIR, row.direction) }}</template></el-table-column>
                  <el-table-column prop="amount" label="金额" width="100" />
                </el-table>
              </el-collapse-item>
            </el-collapse>
          </el-tab-pane>
          <el-tab-pane label="企业账户" name="accounts">
            <el-table :data="fundAccounts" border>
              <el-table-column prop="tenantId" label="企业ID" width="80" />
              <el-table-column prop="enterpriseName" label="企业" min-width="140" />
              <el-table-column prop="enterpriseType" label="类型" width="90" />
              <el-table-column prop="balance" label="可用余额" width="120" />
              <el-table-column prop="frozen" label="冻结" width="120" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="全部流水" name="all">
            <el-table :data="funds" border>
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column prop="tenantId" label="企业ID" width="80" />
              <el-table-column prop="enterpriseName" label="企业" min-width="140" />
              <el-table-column label="类型" width="120"><template #default="{row}">{{ label(FUND_TYPE, row.type) }}</template></el-table-column>
              <el-table-column label="方向" width="100"><template #default="{row}">{{ label(FUND_DIR, row.direction) }}</template></el-table-column>
              <el-table-column prop="amount" label="金额" width="120" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>

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
        <el-table :data="enterprises" border>
          <el-table-column prop="id" label="企业ID" width="80" />
          <el-table-column prop="name" label="企业名称" min-width="140" />
          <el-table-column prop="creditCode" label="信用代码" min-width="150" />
          <el-table-column prop="contactName" label="联系人" width="100" />
          <el-table-column prop="address" label="地址" min-width="160" />
          <el-table-column prop="creditScore" label="信用分" width="80" />
          <el-table-column prop="authStatus" label="认证" width="90" />
          <el-table-column prop="accountPhone" label="登录手机" width="120" />
          <el-table-column prop="realName" label="账号姓名" width="100" />
          <el-table-column label="账号状态" width="90">
            <template #default="{ row }">{{ row.userStatus === 'DISABLED' ? '停用' : (row.userStatus || '-') }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEntEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeEnterprise(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-dialog v-model="entEditOpen" title="编辑用户信息" width="560px">
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
            <el-form-item label="手机号"><el-input v-model="entEdit.accountPhone" :disabled="!entEdit.userId" /></el-form-item>
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
          <el-form-item label="手机号" prop="phone"><el-input v-model="userForm.phone" /></el-form-item>
          <el-form-item label="密码" prop="password"><el-input v-model="userForm.password" type="password" show-password /></el-form-item>
          <el-form-item label="姓名" prop="realName"><el-input v-model="userForm.realName" /></el-form-item>
          <el-form-item label="角色">
            <el-radio-group v-model="userForm.role">
              <el-radio value="OPERATOR">运营</el-radio>
              <el-radio value="INSPECTION">质检</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-button type="primary" @click="createUser">创建账号</el-button>
        </el-form>
      </div>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { listAll, getDetail, getCoverage, returnToBuyer, audit as auditDemand } from '../api/demand'
import { pendingContracts, approveContract, inspect as inspectStage, progressLog } from '../api/order'
import { fetchAttachment, saveBlob } from '../api/file'
import CoverageBars from '../components/CoverageBars.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DEMAND_STATUS, FUND_TYPE, FUND_DIR, STAGE_STATUS, ESCROW_STATUS, label } from '../utils/labels'

const router = useRouter()
const active = ref('demands')
const demandTab = ref('audit')
const DEMAND_GROUPS = {
  audit: ['PENDING_AUDIT'],
  returned: ['RETURNED'],
  match: ['PUBLISHED', 'THINKING', 'REVIEWING'],
  quote: ['LOCKING', 'SOLUTION_GENERATED', 'SOLUTION_CONFIRMED'],
  fulfill: ['SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION'],
  close: ['COMPLETED', 'CANCELLED', 'FLOW_FAILED'],
}
const demands = ref([])
const filteredDemands = computed(() => demands.value.filter(d => (DEMAND_GROUPS[demandTab.value] || []).includes(d.status)))
function countGroup(key) {
  return demands.value.filter(d => (DEMAND_GROUPS[key] || []).includes(d.status)).length
}
const stages = ref([])
const sortedStages = computed(() => {
  const rank = s => (s === 'PENDING_INSPECTION' ? 0 : s === 'IN_PRODUCTION' ? 1 : 2)
  return [...stages.value].sort((a, b) => rank(a.status) - rank(b.status))
})
const progOpen = ref(false)
const progLogs = ref([])
const funds = ref([])
const fundOverview = ref({})
const fundByDemand = ref([])
const fundAccounts = ref([])
const fundTab = ref('byDemand')
const panoOpen = ref(false)
const pano = ref({})
const enterprises = ref([])
const entTab = ref('BUYER')
const entKeyword = ref('')
const entEditOpen = ref(false)
const entEdit = reactive({
  id: null, name: '', creditCode: '', contactName: '', address: '', creditScore: 60, authStatus: 'APPROVED',
  userId: null, accountPhone: '', realName: '', userStatus: 'ENABLED', password: '',
})
const userFormRef = ref()
const userForm = reactive({ phone: '', password: '123456', realName: '', role: 'OPERATOR' })
const userRules = {
  phone: [{ required: true, message: '请填写手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请填写密码', trigger: 'blur' }, { min: 6, message: '至少 6 位', trigger: 'blur' }],
  realName: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
}
const solutions = ref([])
const solDialog = ref(false)
const currentSolDemand = ref(null)
const aiLoading = ref(false)
const repOpen = ref(false)
const alts = ref([])
const currentSol = ref(null)
const currentProcess = ref(null)
const isSuper = localStorage.getItem('role') === 'SUPER_ADMIN'

const fileOpen = ref(false)
const fileTitle = ref('')
const fileText = ref('')
const detailOpen = ref(false)
const detail = reactive({ demand: null, processes: [], attachments: [] })
const returnOpen = ref(false)
const returnReason = ref('')
const returnTarget = ref(null)
const coverage = ref([])
const contracts = ref([])
const inspOpen = ref(false)
const inspTarget = ref(null)
const inspForm = reactive({ result: 'PASS', sampleCount: 10, failCount: 0, keyDimensions: '', meetsRequirement: true, remark: '' })

function statusText(s) { return label(DEMAND_STATUS, s) === s ? label(STAGE_STATUS, s) : label(DEMAND_STATUS, s) }
function tagType(s) {
  if (['PENDING_AUDIT','PUBLISHED','LOCKING','RETURNED'].includes(s)) return 'warning'
  if (['THINKING','REVIEWING'].includes(s)) return 'primary'
  if (['SOLUTION_GENERATED','SOLUTION_CONFIRMED','SOLUTION_SELECTED'].includes(s)) return 'success'
  if (['COMPLETED'].includes(s)) return 'success'
  if (['CANCELLED','FLOW_FAILED','FAIL'].includes(s)) return 'danger'
  return 'info'
}
function parse(json) { try { return JSON.parse(json) } catch { return [] } }
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

async function loadDemands() { demands.value = await listAll() }
async function loadStages() {
  const orders = await api.get('/order/all'); const all = []
  for (const o of orders) { all.push(...await api.get(`/order/${o.id}/stages`)) }
  stages.value = all
}
async function loadFunds() { funds.value = await api.get('/common/all-funds') }
async function loadFundViews() {
  fundOverview.value = await api.get('/fund/overview')
  fundByDemand.value = await api.get('/fund/by-demand')
  fundAccounts.value = await api.get('/fund/accounts')
  await loadFunds()
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

async function openPanorama(row) {
  pano.value = await api.get(`/demand/${row.id}/panorama`)
  panoOpen.value = true
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
  const cov = await getCoverage(row.id)
  coverage.value = cov.processes || []
  detailOpen.value = true
}
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
async function audit(row, result) { await auditDemand(row.id, result); ElMessage.success('操作成功'); loadDemands() }
async function endIntention(row) { await api.post(`/flow/${row.id}/end-intention`); ElMessage.success('已进入思考期'); loadDemands() }
async function review(row, pass) { await api.post(`/flow/${row.id}/review?pass=${pass}`); ElMessage.success('操作成功'); loadDemands() }
async function endLocking(row) { await api.post(`/flow/${row.id}/end-locking`); ElMessage.success('保证金期已结束，请到方案查看里审阅 AI 方案并下发'); loadDemands() }
async function dispatchOrder(row) {
  await ElMessageBox.confirm('派单后将通知中标厂签合同，并退回未中标厂的冻结资金。', '确认派单', { type: 'warning' })
  await api.post(`/order/dispatch/${row.id}`)
  ElMessage.success('已派单，落选厂资金已退回')
  loadDemands()
  loadFundViews()
}
async function genAi(row) {
  currentSolDemand.value = row
  aiLoading.value = true
  try {
    solutions.value = await api.post(`/solution/${row.id}/generate-ai`, null, { timeout: 90000 })
    ElMessage.success('AI 已生成，买家还看不到，请审阅后下发')
    solDialog.value = true
  } finally { aiLoading.value = false }
}
async function genAiFromDialog() {
  if (currentSolDemand.value) await genAi(currentSolDemand.value)
}
async function viewSolutions(row) {
  currentSolDemand.value = row
  solutions.value = await api.get(`/solution/${row.id}`)
  solDialog.value = true
}
async function publish(s) {
  await api.post(`/solution/item/${s.id}/publish`)
  ElMessage.success('已下发给买家')
  solutions.value = await api.get(`/solution/${s.demandId}`)
}
async function openReplace(s, row) {
  currentSol.value = s
  currentProcess.value = row
  alts.value = await api.get(`/solution/item/${s.id}/alternatives`, { params: { processNo: row.processNo } })
  repOpen.value = true
}
async function doReplace(row) {
  await api.post(`/solution/item/${currentSol.value.id}/replace-factory`, { processNo: currentProcess.value.processNo, factoryId: row.factoryId })
  ElMessage.success('已换厂，价格未改')
  repOpen.value = false
  solutions.value = await api.get(`/solution/${currentSol.value.demandId}`)
}
async function loadContracts() { contracts.value = await pendingContracts() }
async function approve(row) { await approveContract(row.orderId, row.tenantId); ElMessage.success('已确认该厂合同，已拆该厂工单'); loadContracts(); loadStages(); loadDemands() }
function openInspect(row, result) {
  inspTarget.value = row
  inspForm.result = result
  inspForm.sampleCount = 10
  inspForm.failCount = result === 'PASS' ? 0 : 1
  inspForm.keyDimensions = ''
  inspForm.meetsRequirement = result === 'PASS'
  inspForm.remark = ''
  inspOpen.value = true
}
async function submitInspect() {
  await inspectStage(inspTarget.value.id, { ...inspForm })
  ElMessage.success(inspForm.result === 'PASS' ? '合格，等待买家付托管（不直接给工厂）' : '已判定不合格，无托管')
  inspOpen.value = false
  loadStages()
}
async function createUser() {
  await userFormRef.value.validate()
  await api.post('/auth/create-user', { ...userForm })
  ElMessage.success('账号已创建')
}

function overdueDays(row) {
  if (!row?.promisedDate) return 0
  if (['PASS', 'FAIL', 'COMPLETED'].includes(row.status) || row.escrowStatus === 'SETTLED') return 0
  const d = Math.ceil((Date.now() - new Date(row.promisedDate).getTime()) / 86400000)
  return d > 0 ? d : 0
}
async function openProgress(row) {
  progLogs.value = await progressLog(row.id)
  progOpen.value = true
}
async function downloadPhoto(id) {
  try {
    const { blob, fileName } = await fetchAttachment(id)
    saveBlob(blob, fileName)
  } catch (e) {
    ElMessage.error(e.message || '无法下载照片')
  }
}

function logout() { localStorage.clear(); router.push('/login') }

onMounted(() => {
  loadDemands()
  loadStages()
  loadFundViews()
  loadContracts()
  loadEnterprises()
})
</script>

<style scoped>
.aside { background:#304156; }
.logo { color:#fff; text-align:center; padding:16px 0; font-weight:bold; }
.toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.title { font-size:16px; font-weight:bold; }
.file-preview { white-space: pre-wrap; word-break: break-word; margin: 0; font-size: 13px; line-height: 1.6; }
.hint { color: #909399; font-size: 12px; margin: 4px 0 8px; }
.kpi { color:#909399; font-size:12px; }
.kpi-v { font-size:18px; font-weight:600; margin-top:6px; }
</style>
