package com.smarthome.support;

import com.smarthome.security.SessionPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextTestHelper {

    private SecurityContextTestHelper() {}

    public static void setSession(String userId, String orgId, String orgRole, String platformRole) {
        SessionPrincipal sp = new SessionPrincipal(userId, orgId, orgRole, platformRole);
        var auth = new UsernamePasswordAuthenticationToken(userId, null);
        auth.setDetails(sp);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void setOrgMember(String userId, String orgId) {
        setSession(userId, orgId, "MANAGER", null);
    }

    public static void setPlatformOwner(String userId) {
        setSession(userId, null, null, "PLATFORM_OWNER");
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
