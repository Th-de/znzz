package com.dsh.platform.domain.status;

import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Demand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemandStateMachineTest {

    private final DemandStateMachine sm = new DemandStateMachine();

    private static Demand demandIn(DemandStatus status) {
        Demand d = new Demand();
        d.setStatus(status.name());
        return d;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // 主流程：发布 -> 工厂思考 -> 买家思考 -> 方案 -> 签约 -> 生产 -> 完成
            "DRAFT, PUBLISHED",
            "DRAFT, PENDING_AUDIT",
            "PENDING_AUDIT, PUBLISHED",
            "PENDING_AUDIT, RETURNED",
            "RETURNED, PENDING_AUDIT",
            "PUBLISHED, FACTORY_THINKING",
            "FACTORY_THINKING, BUYER_THINKING",
            "BUYER_THINKING, SOLUTION_GENERATED",
            "SOLUTION_GENERATED, SOLUTION_CONFIRMED",
            "SOLUTION_CONFIRMED, SOLUTION_SELECTED",
            "SOLUTION_SELECTED, CONTRACTED",
            "CONTRACTED, IN_PRODUCTION",
            "IN_PRODUCTION, COMPLETED",
            // 流单分支
            "PUBLISHED, FLOW_FAILED",
            "LOCKING, FLOW_FAILED",
            "SOLUTION_SELECTED, FLOW_FAILED",
            // 旧流程兼容
            "PUBLISHED, THINKING",
            "THINKING, LOCKING",
            "THINKING, REVIEWING",
            "REVIEWING, LOCKING",
            "LOCKING, SOLUTION_GENERATED",
    })
    @DisplayName("允许的流转应写回目标状态")
    void allowedTransitions(DemandStatus from, DemandStatus to) {
        Demand d = demandIn(from);
        sm.transit(d, to);
        assertThat(d.getStatus()).isEqualTo(to.name());
    }

    @ParameterizedTest(name = "{0} -> {1} 应被拒绝")
    @CsvSource({
            "DRAFT, CONTRACTED",             // 不能跳过竞标直接签约
            "PUBLISHED, SOLUTION_GENERATED", // 不能跳过思考期
            "FACTORY_THINKING, PUBLISHED",   // 不能回退
            "BUYER_THINKING, FACTORY_THINKING",
            "IN_PRODUCTION, CANCELLED",      // 生产中不可取消，只能完成
            "IN_PRODUCTION, CONTRACTED",
            "SOLUTION_GENERATED, SOLUTION_SELECTED", // 必须先经运营确认
    })
    @DisplayName("非法流转应抛 BizException 且不改状态")
    void rejectedTransitions(DemandStatus from, DemandStatus to) {
        Demand d = demandIn(from);
        assertThatThrownBy(() -> sm.transit(d, to))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
        assertThat(d.getStatus()).isEqualTo(from.name());
    }

    @ParameterizedTest
    @EnumSource(value = DemandStatus.class, names = {"COMPLETED", "CANCELLED", "FLOW_FAILED"})
    @DisplayName("终态不可再流转到任何状态")
    void terminalStatesAreFinal(DemandStatus terminal) {
        for (DemandStatus target : DemandStatus.values()) {
            Demand d = demandIn(terminal);
            assertThatThrownBy(() -> sm.transit(d, target)).isInstanceOf(BizException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(value = DemandStatus.class,
            names = {"COMPLETED", "CANCELLED", "FLOW_FAILED", "IN_PRODUCTION"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("除生产中与终态外，任何状态都可以取消")
    void everyActiveStateCanBeCancelled(DemandStatus from) {
        Demand d = demandIn(from);
        sm.transit(d, DemandStatus.CANCELLED);
        assertThat(d.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("空状态视为 DRAFT")
    void blankStatusIsDraft() {
        Demand d = new Demand();
        d.setStatus(null);
        sm.transit(d, DemandStatus.PUBLISHED);
        assertThat(d.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("未知状态字符串应抛 BizException")
    void unknownStatusRejected() {
        Demand d = new Demand();
        d.setStatus("NOT_A_STATUS");
        assertThatThrownBy(() -> sm.transit(d, DemandStatus.PUBLISHED))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知需求状态");
    }
}
