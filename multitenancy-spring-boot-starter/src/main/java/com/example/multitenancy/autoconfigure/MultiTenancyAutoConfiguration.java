package com.example.multitenancy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.multitenancy.datasource.TenantAwareRoutingDataSource;
import com.example.multitenancy.health.TenantDataSourcesHealthContributor;
import com.example.multitenancy.interceptor.TenantInterceptor;
import com.example.multitenancy.properties.MultiTenancyProperties;
import com.example.multitenancy.resolver.HeaderTenantResolver;
import com.example.multitenancy.resolver.TenantResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties(MultiTenancyProperties.class)
@ConditionalOnProperty(prefix = "multitenancy", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass({ DataSource.class, AbstractRoutingDataSource.class })
public class MultiTenancyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantResolver tenantResolver() {
        return new HeaderTenantResolver();
    }

    @Bean
    public Map<String, DataSource> tenantDataSources(MultiTenancyProperties properties) {
        Map<String, DataSource> dataSources = new LinkedHashMap<>();
        for (MultiTenancyProperties.TenantConfig config : properties.getTenants()) {
            DataSourceBuilder<?> builder = DataSourceBuilder.create()
                    .url(config.getUrl())
                    .username(config.getUsername())
                    .password(config.getPassword());
            if (config.getDriverClassName() != null && !config.getDriverClassName().isBlank()) {
                builder.driverClassName(config.getDriverClassName());
            }
            dataSources.put(config.getId(), builder.build());
        }
        return dataSources;
    }

    @Bean
    @Primary
    public DataSource dataSource(MultiTenancyProperties properties, Map<String, DataSource> tenantDataSources) {
        TenantAwareRoutingDataSource routingDataSource = new TenantAwareRoutingDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>(tenantDataSources);
        routingDataSource.setTargetDataSources(targetDataSources);

        if (!tenantDataSources.isEmpty()) {
            DataSource defaultDs = tenantDataSources.values().iterator().next();
            routingDataSource.setDefaultTargetDataSource(defaultDs);
        }

        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantInterceptor tenantInterceptor(TenantResolver tenantResolver,
                                               MultiTenancyProperties properties,
                                               ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new TenantInterceptor(tenantResolver, properties, objectMapperProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnClass(WebMvcConfigurer.class)
    public WebMvcConfigurer tenantWebMvcConfigurer(TenantInterceptor tenantInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(tenantInterceptor)
                        .addPathPatterns("/api/**")
                        .excludePathPatterns("/actuator/**", "/error");
            }
        };
    }

    @Bean("datasources")
    @ConditionalOnClass(CompositeHealthContributor.class)
    @ConditionalOnMissingBean(name = "datasources")
    public CompositeHealthContributor datasourcesHealthContributor(Map<String, DataSource> tenantDataSources) {
        return new TenantDataSourcesHealthContributor(tenantDataSources);
    }
}
