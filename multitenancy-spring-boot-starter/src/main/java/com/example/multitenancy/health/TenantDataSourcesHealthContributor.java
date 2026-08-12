package com.example.multitenancy.health;

import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.NamedContributor;

import javax.sql.DataSource;
import java.sql.Connection;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class TenantDataSourcesHealthContributor implements CompositeHealthContributor {

    private final Map<String, HealthContributor> contributors = new LinkedHashMap<>();

    public TenantDataSourcesHealthContributor(Map<String, DataSource> tenantDataSources) {
        tenantDataSources.forEach((tenantId, dataSource) -> {
            contributors.put(tenantId, (HealthIndicator) () -> checkDataSourceHealth(tenantId, dataSource));
        });
    }

    private Health checkDataSourceHealth(String tenantId, DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return Health.up()
                        .withDetail("tenantId", tenantId)
                        .withDetail("database", connection.getMetaData().getDatabaseProductName())
                        .withDetail("url", connection.getMetaData().getURL())
                        .build();
            } else {
                return Health.down()
                        .withDetail("tenantId", tenantId)
                        .withDetail("error", "Connection is invalid")
                        .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("tenantId", tenantId)
                    .build();
        }
    }

    @Override
    public HealthContributor getContributor(String name) {
        return contributors.get(name);
    }

    @Override
    public Iterator<NamedContributor<HealthContributor>> iterator() {
        return contributors.entrySet().stream()
                .map(entry -> NamedContributor.of(entry.getKey(), entry.getValue()))
                .iterator();
    }
}
