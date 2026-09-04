package com.dsh.platform.domain.inspect;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 检验方式：AQL 抽样或全检（二选一）。首件由工厂内部完成，平台不做 FAI。 */
public final class InspectPrices {

    public static final BigDecimal AQL = new BigDecimal("5.00");
    public static final BigDecimal FULL = new BigDecimal("3.00");

    private InspectPrices() {}

    public static List<String> modes(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        for (String p : raw.split("[,，;；/、\\s]+")) {
            String m = p.trim().toUpperCase();
            if ("AQL".equals(m) || "FULL".equals(m)) {
                out.add(m);
            }
        }
        return new ArrayList<>(out);
    }

    /** 旧数据若含 FAI，去掉后只保留 AQL/全检；都没有则视为 AQL。 */
    public static String normalize(String raw) {
        List<String> list = modes(raw);
        if (list.contains("FULL")) {
            return "FULL";
        }
        return "AQL";
    }

    public static boolean includesAql(String raw) {
        return "AQL".equals(normalize(raw));
    }

    public static BigDecimal of(String mode) {
        return "FULL".equals(normalize(mode)) ? FULL : AQL;
    }

    public static BigDecimal fee(String modes, int qty) {
        int n = Math.max(0, qty);
        return of(modes).multiply(BigDecimal.valueOf(n)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal unitOf(String mode) {
        return of(mode);
    }

    public static String label(String mode) {
        return "FULL".equals(normalize(mode)) ? "全检" : "AQL 抽样";
    }

    public static String oneLabel(String mode) {
        return label(mode);
    }
}
