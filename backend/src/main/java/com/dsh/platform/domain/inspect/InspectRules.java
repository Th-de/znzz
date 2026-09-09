package com.dsh.platform.domain.inspect;

import com.dsh.platform.entity.WorkStage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class InspectRules {

    public static final BigDecimal CONCEDE_BAND = new BigDecimal("0.05");
    public static final BigDecimal CLOSE_RATE = new BigDecimal("0.05");

    public enum Branch {
        A, B, C1, C2, D, E
    }

    private InspectRules() {}

    public static int requiredSample(int agreedQty) {
        if (agreedQty <= 0) {
            return 0;
        }
        return agreedQty;
    }

    public static int requiredSample(int agreedQty, boolean quantityOk, Integer deliveredQty) {
        int delivered = deliveredQty == null ? 0 : deliveredQty;
        int lot = delivered > 0 ? delivered : Math.max(0, agreedQty);
        return lot;
    }

    public static int requiredSample(String inspectMode, String aql, int agreedQty, Integer deliveredQty) {
        int delivered = deliveredQty == null ? 0 : deliveredQty;
        int lot = delivered > 0 ? delivered : Math.max(0, agreedQty);
        if (InspectPrices.includesAql(inspectMode)) {
            return AqlPlans.plan(lot, aql).n();
        }
        return lot;
    }

    public static boolean aqlGeneralPass(int generalFail, AqlPlans.Plan plan) {
        int ac = plan == null ? 0 : plan.ac();
        return generalFail <= ac;
    }

    public static boolean tolerancePass(String inspectMode, int criticalFail, int generalFail,
                                       BigDecimal yield, BigDecimal minYield, AqlPlans.Plan plan) {
        if (criticalFail > 0) {
            return false;
        }
        if (InspectPrices.includesAql(inspectMode)) {
            return aqlGeneralPass(generalFail, plan);
        }
        return tolerancePass(0, yield, minYield);
    }

    public static BigDecimal yield(int sampleCount, int criticalFail, int generalFail) {
        if (sampleCount <= 0) {
            return BigDecimal.ZERO;
        }
        int fail = Math.max(0, criticalFail) + Math.max(0, generalFail);
        int ok = Math.max(0, sampleCount - fail);
        return BigDecimal.valueOf(ok).divide(BigDecimal.valueOf(sampleCount), 4, RoundingMode.HALF_UP);
    }

    /**
     * 关键公差不合格数不为 0 则公差不合格；
     * 关键为 0 但抽检良率低于需求最低良率则公差不合格；其余为公差合格。
     */
    public static boolean tolerancePass(int criticalFail, BigDecimal yield, BigDecimal minYield) {
        if (criticalFail > 0) {
            return false;
        }
        BigDecimal min = minYield == null ? BigDecimal.ZERO : minYield;
        BigDecimal y = yield == null ? BigDecimal.ZERO : yield;
        return y.compareTo(min) >= 0;
    }

    public static boolean isPass(boolean quantityOk, boolean toleranceOk) {
        return quantityOk && toleranceOk;
    }

    public static Branch classify(boolean quantityOk, int criticalFail, BigDecimal yield, BigDecimal minYield) {
        return classify(quantityOk, criticalFail, 0, yield, minYield, null);
    }

    public static Branch classify(boolean quantityOk, int criticalFail, int generalFail,
                                  BigDecimal yield, BigDecimal minYield, AqlPlans.Plan aqlPlan) {
        if (criticalFail > 0) {
            return Branch.E;
        }
        if (aqlPlan != null) {
            boolean aqlOk = aqlGeneralPass(generalFail, aqlPlan);
            if (quantityOk && aqlOk) {
                return Branch.A;
            }
            if (!quantityOk && aqlOk) {
                return Branch.B;
            }
            if (quantityOk && generalFail == aqlPlan.re()) {
                return Branch.C1;
            }
            if (quantityOk) {
                return Branch.C2;
            }
            return Branch.D;
        }
        boolean yieldOk = tolerancePass(0, yield, minYield);
        if (quantityOk && yieldOk) {
            return Branch.A;
        }
        if (!quantityOk && yieldOk) {
            return Branch.B;
        }
        BigDecimal y = yield == null ? BigDecimal.ZERO : yield;
        BigDecimal m = minYield == null ? BigDecimal.ZERO : minYield;
        boolean mild = y.compareTo(m.subtract(CONCEDE_BAND)) >= 0;
        if (quantityOk && mild) {
            return Branch.C1;
        }
        if (quantityOk) {
            return Branch.C2;
        }
        return Branch.D;
    }

    public static boolean canConcede(Branch branch, int reworkCount) {
        return branch == Branch.B || branch == Branch.C1;
    }

    public static boolean canConcede(boolean quantityOk, int criticalFail, BigDecimal yield, BigDecimal minYield) {
        return canConcede(classify(quantityOk, criticalFail, yield, minYield), 0);
    }

    public static boolean canRework(Branch branch, int reworkCount) {
        if (reworkCount >= 1) {
            return false;
        }
        return branch != null && branch != Branch.A;
    }

    /** 数量不足且质量合格时只能让步或补交，不能关闭本阶段。 */
    public static boolean canClose(Branch branch) {
        return branch != null && branch != Branch.B;
    }

    public static boolean isActiveWork(String status) {
        if (status == null) {
            return false;
        }
        return "IN_PRODUCTION".equals(status)
                || "FAIL".equals(status)
                || "WAITING_OPEN".equals(status)
                || "PENDING".equals(status)
                || "PENDING_INSPECT_PAY".equals(status)
                || "PENDING_INSPECTION".equals(status)
                || "PENDING_REVIEW".equals(status)
                || "INSPECTING".equals(status);
    }

    /** 本厂已被买家关闭本阶段及后续期，且没有仍需生产/质检的工单。 */
    public static boolean factoryWorkClosed(List<WorkStage> periods) {
        if (periods == null || periods.isEmpty()) {
            return false;
        }
        boolean anyClosed = periods.stream().anyMatch(ws -> "CLOSED".equals(ws.getStatus()));
        if (!anyClosed) {
            return false;
        }
        return periods.stream().noneMatch(ws -> isActiveWork(ws.getStatus()));
    }

    /** 让步托管：B 按已交比例；C1 托管本阶段全款。 */
    public static BigDecimal concessionPay(Branch branch, BigDecimal amount, int agreed, int delivered, BigDecimal yield) {
        if (branch == Branch.B) {
            if (agreed <= 0) {
                return BigDecimal.ZERO;
            }
            return scale(amount, BigDecimal.valueOf(Math.max(0, delivered)).divide(BigDecimal.valueOf(agreed), 6, RoundingMode.HALF_UP));
        }
        if (branch == Branch.C1) {
            return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /** 让步赔付：B 与抽检 C1 为本阶段工费 5%；全检 C1 为 A×(最低良率−实际良率)。 */
    public static BigDecimal concessionPenalty(Branch branch, BigDecimal amount, BigDecimal minYield, BigDecimal yield) {
        return concessionPenalty(branch, amount, minYield, yield, 0, null);
    }

    public static BigDecimal concessionPenalty(Branch branch, BigDecimal amount, BigDecimal minYield, BigDecimal yield,
                                              int generalFail, AqlPlans.Plan aqlPlan) {
        if (branch == Branch.B || (branch == Branch.C1 && aqlPlan != null)) {
            return scale(amount, CLOSE_RATE);
        }
        if (branch == Branch.C1) {
            return concessionPenalty(amount, minYield, yield);
        }
        return BigDecimal.ZERO;
    }

    /** 让步赔付：本段工钱 ×（最低良率 − 实际良率）。 */
    public static BigDecimal concessionPenalty(BigDecimal amount, BigDecimal minYield, BigDecimal yield) {
        BigDecimal m = minYield == null ? BigDecimal.ZERO : minYield;
        BigDecimal y = yield == null ? BigDecimal.ZERO : yield;
        BigDecimal gap = m.subtract(y);
        if (gap.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return scale(amount, gap);
    }

    public static BigDecimal closePenalty(BigDecimal remainingWages) {
        return scale(remainingWages, CLOSE_RATE);
    }

    public static BigDecimal scale(BigDecimal amount, BigDecimal rate) {
        BigDecimal a = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal r = rate == null ? BigDecimal.ZERO : rate;
        return a.multiply(r).setScale(2, RoundingMode.HALF_UP);
    }
}
