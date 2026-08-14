package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "multitenancy.enabled=true",
        "multitenancy.tenants[0].id=tenant1",
        "multitenancy.tenants[0].url=jdbc:h2:mem:tenant1_db;DB_CLOSE_DELAY=-1",
        "multitenancy.tenants[0].username=sa",
        "multitenancy.tenants[0].password=",
        "multitenancy.tenants[0].driver-class-name=org.h2.Driver",
        "multitenancy.tenants[1].id=tenant2",
        "multitenancy.tenants[1].url=jdbc:h2:mem:tenant2_db;DB_CLOSE_DELAY=-1",
        "multitenancy.tenants[1].username=sa",
        "multitenancy.tenants[1].password=",
        "multitenancy.tenants[1].driver-class-name=org.h2.Driver",
        "multitenancy.tenants[2].id=tenant3",
        "multitenancy.tenants[2].url=jdbc:h2:mem:tenant3_db;DB_CLOSE_DELAY=-1",
        "multitenancy.tenants[2].username=sa",
        "multitenancy.tenants[2].password=",
        "multitenancy.tenants[2].driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
public class MultiTenantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testUserCreationAndDataIsolation() throws Exception {
        // Create User 1 in Tenant 1
        User user1 = new User("Alice Tenant1", "alice@tenant1.com");
        MvcResult result1 = mockMvc.perform(post("/api/users")
                        .header("X-Tenant-ID", "tenant1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("Alice Tenant1")))
                .andExpect(jsonPath("$.email", is("alice@tenant1.com")))
                .andReturn();

        User createdUser1 = objectMapper.readValue(result1.getResponse().getContentAsString(), User.class);
        Long user1Id = createdUser1.getId();

        // Create User 2 in Tenant 2
        User user2 = new User("Bob Tenant2", "bob@tenant2.com");
        mockMvc.perform(post("/api/users")
                        .header("X-Tenant-ID", "tenant2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Bob Tenant2")));

        // Query Tenant 1 users -> Should contain ONLY Tenant 1 user
        mockMvc.perform(get("/api/users")
                        .header("X-Tenant-ID", "tenant1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Alice Tenant1")));

        // Query Tenant 2 users -> Should contain ONLY Tenant 2 user
        mockMvc.perform(get("/api/users")
                        .header("X-Tenant-ID", "tenant2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Bob Tenant2")));

        // Query User by ID in Tenant 1 -> Should return 200 OK
        mockMvc.perform(get("/api/users/" + user1Id)
                        .header("X-Tenant-ID", "tenant1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Alice Tenant1")));

        // Query User by ID in Tenant 2 -> Should return 404 Not Found (Data Isolation)
        mockMvc.perform(get("/api/users/" + user1Id)
                        .header("X-Tenant-ID", "tenant2"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testMissingTenantHeader_Returns400() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("X-Tenant-ID header is missing")));
    }

    @Test
    public void testUnknownTenant_Returns404() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("X-Tenant-ID", "unknown_tenant"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Tenant not found: unknown_tenant")));
    }

    @Test
    public void testActuatorHealthDatasources() throws Exception {
        mockMvc.perform(get("/actuator/health/datasources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components.tenant1.status", is("UP")))
                .andExpect(jsonPath("$.components.tenant2.status", is("UP")))
                .andExpect(jsonPath("$.components.tenant3.status", is("UP")));
    }
}
