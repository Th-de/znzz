package com.dsh.platform.security;

import com.dsh.platform.common.BizException;

public class UserContext {
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BizException(401, "未登录");
        }
        return u;
    }

    public static Long tenantId() {
        return get().getTenantId();
    }

    public static Long userId() {
        return get().getUserId();
    }

    public static String role() {
        return get().getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
