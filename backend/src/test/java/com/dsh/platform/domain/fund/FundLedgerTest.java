package com.dsh.platform.domain.fund;

import com.dsh.platform.entity.FundFlow;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.FundFlowMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资金台账规则测试：只验证「冻结/解冻/罚没」的金额与流水方向，不接数据库。
 */
@ExtendWith(MockitoExtension.class)
class FundLedgerTest {

    private static final BigDecimal INTENTION = new BigDecimal("1000");

    @Mock FundFlowMapper fundFlowMapper;
    @Mock QuotationMapper quotationMapper;
    @Mock EnterpriseMapper enterpriseMapper;
    @Mock AccountService accountService;

    @InjectMocks FundLedger ledger;

    @Captor ArgumentCaptor<FundFlow> flowCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ledger, "intentionFixed", INTENTION);
    }

    private static Quotation quotation(long id, long demandId, long tenantId, String status, String intentionStatus) {
        Quotation q = new Quotation();
        q.setId(id);
        q.setDemandId(demandId);
        q.setTenantId(tenantId);
        q.setStatus(status);
        q.setIntentionStatus(intentionStatus);
        return q;
    }

    @Test
    @DisplayName("报名冻结意向金：冻结固定金额并写一条 INTENTION/FREEZE 流水")
    void freezeIntentionWritesFreezeFlow() {
        Quotation q = quotation(7L, 100L, 20L, "INTENTION", null);

        ledger.freezeIntention(q);

        verify(accountService).freeze(20L, INTENTION);
        verify(fundFlowMapper).insert(flowCaptor.capture());
        FundFlow f = flowCaptor.getValue();
        assertThat(f.getType()).isEqualTo("INTENTION");
        assertThat(f.getDirection()).isEqualTo("FREEZE");
        assertThat(f.getAmount()).isEqualByComparingTo(INTENTION);
        assertThat(f.getDemandId()).isEqualTo(100L);
        assertThat(f.getTenantId()).isEqualTo(20L);
        assertThat(f.getIdempotentNo()).isEqualTo("INTENTION-FREEZE-7");
        assertThat(q.getIntentionStatus()).isEqualTo("FROZEN");
        verify(quotationMapper).updateById(q);
    }

    @Test
    @DisplayName("同一幂等号已存在时不重复写流水")
    void writeIsIdempotent() {
        when(fundFlowMapper.selectCount(any())).thenReturn(1L);
        Quotation q = quotation(7L, 100L, 20L, "INTENTION", null);

        ledger.freezeIntention(q);

        verify(accountService).freeze(20L, INTENTION);
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("解冻已冻结的意向金：释放并作废报价")
    void unfreezeFrozenIntention() {
        Quotation q = quotation(8L, 100L, 21L, "INTENTION", "FROZEN");

        ledger.unfreezeIntention(q);

        verify(accountService).unfreeze(21L, INTENTION);
        verify(fundFlowMapper).insert(flowCaptor.capture());
        assertThat(flowCaptor.getValue().getDirection()).isEqualTo("UNFREEZE");
        assertThat(q.getIntentionStatus()).isEqualTo("RELEASED");
        assertThat(q.getStatus()).isEqualTo("INVALID");
    }

    @Test
    @DisplayName("并入报名(COVERED)的意向金没有冻结资金，只改状态不动账户")
    void unfreezeCoveredIntentionTouchesNoMoney() {
        Quotation q = quotation(9L, 100L, 22L, "INTENTION", "COVERED");

        ledger.unfreezeIntention(q);

        verify(accountService, never()).unfreeze(any(), any());
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
        assertThat(q.getIntentionStatus()).isEqualTo("RELEASED");
        assertThat(q.getStatus()).isEqualTo("INVALID");
    }

    @Test
    @DisplayName("空报价对象直接忽略")
    void nullQuotationIgnored() {
        ledger.freezeIntention(null);
        ledger.unfreezeIntention(new Quotation());
        verify(accountService, never()).freeze(any(), any());
        verify(accountService, never()).unfreeze(any(), any());
    }

    @Test
    @DisplayName("买家审核不通过：扣 50% 意向金，均分给报名厂，零头给最后一家")
    void forfeitIntentionSplitsPenaltyWithoutLosingCents() {
        when(quotationMapper.selectList(any())).thenReturn(List.of(
                quotation(1L, 100L, 31L, "INTENTION", "FROZEN"),
                quotation(2L, 100L, 32L, "LOCKED", "FROZEN"),
                quotation(3L, 100L, 33L, "INTENTION", "FROZEN")));

        ledger.forfeitIntention(100L, 10L);

        BigDecimal penalty = new BigDecimal("500.0");
        verify(accountService).debit(10L, penalty);
        // 500 / 3 = 166.66 向下取整，最后一家拿 166.68 补齐
        verify(accountService).credit(31L, new BigDecimal("166.66"));
        verify(accountService).credit(32L, new BigDecimal("166.66"));
        verify(accountService).credit(33L, new BigDecimal("166.68"));

        ArgumentCaptor<FundFlow> flows = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper, org.mockito.Mockito.times(4)).insert(flows.capture());
        BigDecimal in = flows.getAllValues().stream()
                .filter(f -> "IN".equals(f.getDirection()))
                .map(FundFlow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal out = flows.getAllValues().stream()
                .filter(f -> "OUT".equals(f.getDirection()))
                .map(FundFlow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(in).isEqualByComparingTo(out);
    }

    @Test
    @DisplayName("审核不通过但无人报名：只扣买家，不分账")
    void forfeitIntentionWithNoFactories() {
        when(quotationMapper.selectList(any())).thenReturn(List.of());

        ledger.forfeitIntention(100L, 10L);

        verify(accountService).debit(eq(10L), any());
        verify(accountService, never()).credit(any(), any());
    }

    @Test
    @DisplayName("意向金金额来自配置")
    void intentionAmountComesFromConfig() {
        assertThat(ledger.intentionAmount()).isEqualByComparingTo("1000");
    }
}
