package com.example.multitenancy.resolver;

import jakarta.servlet.http.HttpServletRequest;

public class HeaderTenantResolver implements TenantResolver {
    public static final String DEFAULT_HEADER_NAME = "X-Tenant-ID";
    private final String headerName;

    public HeaderTenantResolver() {
        this(DEFAULT_HEADER_NAME);
    }

    public HeaderTenantResolver(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public String resolveTenantId(HttpServletRequest request) {
        return request.getHeader(headerName);
    }
}
