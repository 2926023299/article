package com.heima.utils.thread;

import com.heima.model.user.pojos.ApUser;

public class AppThreadLocalUtil {
    private final static ThreadLocal<ApUser> AP_USER_THREAD_LOCAL = new ThreadLocal<>();

    public static void setApUser(ApUser apUser) {
        AP_USER_THREAD_LOCAL.set(apUser);
    }

    public static ApUser getApUser() {
        return AP_USER_THREAD_LOCAL.get();
    }

    public static void removeWmUser() {
        AP_USER_THREAD_LOCAL.remove();
    }
}
