package com.example.multitenancy.resolver;

import jakarta.servlet.http.HttpServletRequest;

public interface TenantResolver {
    String resolveTenantId(HttpServletRequest request);
}
