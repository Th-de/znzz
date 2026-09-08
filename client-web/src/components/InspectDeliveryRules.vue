<template>
  <span class="wrap">
    <a class="rules-link" href="javascript:;" @click.prevent="open = true">查看规则</a>
    <el-dialog
      v-model="open"
      title="质检与交付规则"
      width="720px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="rules">
        <p class="lead">一单一品。工厂做完全部工序后按期交货。交货后先付质检费，质检员才能检。合格才托管工钱。</p>

        <h4>工序与分期</h4>
        <ul>
          <li>工序只描述工艺路线，不拆数量；件数等于该厂该期承接件数。</li>
          <li>后一期开始日必须等于前一期截止日，最后一期截止日必须等于硬交期。未到开始日工厂端为「待开启」，不能上报、不能交付。</li>
          <li>到期未完成不延长交期；由买家确定返工期限后开启返工工单，与原分期截止无关。</li>
        </ul>

        <h4>质检费（平台标价，发布时不填单价）</h4>
        <ul>
          <li>二选一：AQL 抽样 5 元/件，或全检 3 元/件，按本期实交件数计。</li>
          <li>选 AQL 须填 0.65/1.0/1.5/2.5，不填最低良率。选全检须填最低良率，不填 AQL。</li>
          <li>首次：买家付；质量返工再检：工厂付。数量返工补件：买家为剩余件数付质检费。</li>
        </ul>

        <h4>怎么检</h4>
        <ul>
          <li>AQL：按 GB/T 2828.1 一般检验水平 II、一次正常抽样，用本批实交查 n 和一般缺陷 Ac/Re。关键超差仍为 0 件。方案 n 大于实交时检完全部实交且 Ac=0。</li>
          <li>全检：实交件数全部检验。无关键超差且良率不低于最低良率，才算公差合格。</li>
          <li>数量齐且公差合格才整单合格。质检员和运营可按现场清点修改实交数量，并判定数量是否达标。</li>
        </ul>

        <h4>第一次质检之后</h4>
        <ul>
          <li><b>A</b> 齐、无关键、抽检/全检过关：全额托管本阶段工费。</li>
          <li><b>B</b> 不齐、无关键、抽检/全检过关：返工补齐或让步。让步托管已交工费，并扣本阶段全部约定工费 5% 赔买家。</li>
          <li><b>C1</b> 齐、无关键、刚超标（AQL 一般缺陷刚好到 Re；全检良率在最低减 5 个百分点以上）：返工或让步。让步均托管本阶段全款。抽检再从工厂保证金划本阶段工费 5% 给买家；全检划工费×（最低良率−实际良率）。</li>
          <li><b>C2</b> 齐、无关键、超标更多：返工或关闭。</li>
          <li><b>D</b> 不齐且抽检/全检不过：一次返工，再用尽则关闭。</li>
          <li><b>E</b> 有关键超差：返工或关闭。返工后再不合格只能关闭。</li>
        </ul>

        <h4>返工</h4>
        <ul>
          <li>全程 1 次。B 的补件工单再检不合格只能关闭或让步，不能二次返工。</li>
          <li>数量返工：已交部分先托管工费；剩余件数生成补件工单。</li>
        </ul>

        <h4>关闭本阶段</h4>
        <p>扣该厂「本期 + 后续未完成期」工费合计的 5% 从保证金赔买家，后续期全部取消，本期不交托管。保证金不足则不能关闭。</p>

        <h4>保证金与佣金</h4>
        <ul>
          <li>工厂按该品总报价冻结 5% 履约保证金；买家按预估总价冻结 5%。</li>
          <li>平台佣金为结算金额的 1%，从托管工钱扣，不占用保证金。</li>
        </ul>
      </div>
      <template #footer>
        <el-button type="primary" @click="open = false">我知道了</el-button>
      </template>
    </el-dialog>
  </span>
</template>

<script setup>
import { ref } from 'vue'
const open = ref(false)
</script>

<style scoped>
.wrap { display: inline; }
.rules-link {
  color: #f56c6c;
  font-size: 13px;
  cursor: pointer;
  text-decoration: underline;
  font-weight: 500;
}
.rules-link:hover { color: #dd6161; }
.rules { color: #303133; font-size: 14px; line-height: 1.7; }
.lead { margin: 0 0 12px; color: #606266; }
.rules h4 { margin: 14px 0 6px; font-size: 15px; }
.rules ul { margin: 0; padding-left: 20px; }
.rules li { margin: 4px 0; }
.rules p { margin: 0; }
</style>
