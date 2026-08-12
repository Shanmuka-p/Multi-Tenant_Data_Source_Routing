package com.example.multitenancy.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.multitenancy.TenantContext;
import com.example.multitenancy.properties.MultiTenancyProperties;
import com.example.multitenancy.resolver.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TenantInterceptor implements HandlerInterceptor {

    private final TenantResolver tenantResolver;
    private final MultiTenancyProperties properties;
    private final ObjectMapper objectMapper;

    public TenantInterceptor(TenantResolver tenantResolver,
                             MultiTenancyProperties properties,
                             ObjectMapper objectMapper) {
        this.tenantResolver = tenantResolver;
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantId = tenantResolver.resolveTenantId(request);

        if (tenantId == null || tenantId.isBlank()) {
            writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Bad Request", "X-Tenant-ID header is missing");
            return false;
        }

        if (!properties.isValidTenant(tenantId)) {
            writeErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Not Found", "Tenant not found: " + tenantId);
            return false;
        }

        TenantContext.setCurrentTenant(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            // Guarantee context clearance to avoid thread local leaks
        } finally {
            TenantContext.clear();
        }
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, String> errorDetails = new HashMap<>();
        errorDetails.put("error", error);
        errorDetails.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
    }
}
