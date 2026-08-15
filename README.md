# Header-Based Multi-Tenant Data Source Routing Spring Boot Starter & Demo Application

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Docker](https://img.shields.io/badge/Docker-Supported-blue.svg)](https://www.docker.com/)

An industrial-grade, reusable Spring Boot starter (`multitenancy-spring-boot-starter`) providing auto-configured, header-based multi-tenant data source routing. It encapsulates tenant context management using `ThreadLocal`, dynamic data source lookup using `AbstractRoutingDataSource`, HTTP request interception via `HandlerInterceptor`, configuration mapping via `@ConfigurationProperties`, and custom health monitoring via Spring Boot Actuator.

---

## Architecture & Design Overview

In a Software-as-a-Service (SaaS) architecture, data isolation is a critical requirement. This project implements **database-per-tenant** multi-tenancy: each tenant operates against its own dedicated PostgreSQL database (`tenant1_db`, `tenant2_db`, `tenant3_db`).

```
                              ┌───────────────────────────────────┐
                              │          HTTP Request             │
                              │    Header: X-Tenant-ID: tenant1   │
                              └─────────────────┬─────────────────┘
                                                │
                                                ▼
                              ┌───────────────────────────────────┐
                              │        TenantInterceptor          │
                              │ 1. Validate X-Tenant-ID header    │
                              │ 2. Set TenantContext (ThreadLocal)│
                              └─────────────────┬─────────────────┘
                                                │
                                                ▼
                              ┌───────────────────────────────────┐
                              │      Spring Data Repository       │
                              │   (Triggers DB Connection fetch)  │
                              └─────────────────┬─────────────────┘
                                                │
                                                ▼
                              ┌───────────────────────────────────┐
                              │  TenantAwareRoutingDataSource     │
                              │ (Extends AbstractRoutingDataSource)│
                              │  Reads TenantContext.getTenant()  │
                              └──────┬──────────┬──────────┬──────┘
                                     │          │          │
                     ┌───────────────┘          │          └───────────────┐
                     ▼                          ▼                          ▼
          ┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
          │     Tenant 1 DB    │    │     Tenant 2 DB    │    │     Tenant 3 DB    │
          │    (tenant1_db)    │    │    (tenant2_db)    │    │    (tenant3_db)    │
          └────────────────────┘    └────────────────────┘    └────────────────────┘
```

### Core Architecture Components

1. **`TenantContext`**:
   Uses `ThreadLocal<String>` to store the current tenant ID safely per HTTP request thread. Ensures `clear()` is invoked in an `afterCompletion` interceptor callback to prevent context bleeding and memory leaks in web thread pools.

2. **`TenantResolver` & `HeaderTenantResolver`**:
   Extensible strategy interface for resolving tenant identity. Defaults to extracting the tenant ID from the `X-Tenant-ID` HTTP header.

3. **`TenantInterceptor`**:
   Implements `HandlerInterceptor`. Inspects incoming requests to `/api/**`:
   - Returns **400 Bad Request** (`{"error": "Bad Request", "message": "X-Tenant-ID header is missing"}`) if the header is missing.
   - Returns **404 Not Found** (`{"error": "Not Found", "message": "Tenant not found: <tenant_id>"}`) if the tenant ID is not configured in `application.yml`.
   - Clears `TenantContext` upon request completion.

4. **`TenantAwareRoutingDataSource`**:
   Extends Spring's `AbstractRoutingDataSource`. Dynamically routes JDBC connections to the target tenant database based on the lookup key retrieved from `TenantContext.getCurrentTenant()`.

5. **`MultiTenancyAutoConfiguration`**:
   Spring Boot auto-configuration registered in `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Instantiates and wires tenant data sources, interceptors, routing data sources, and Actuator health indicators automatically when `multitenancy.enabled=true`.

6. **`TenantDataSourcesHealthContributor`**:
   Exposes composite health metrics for all configured tenant data sources at `/actuator/health/datasources`.

---

## Project Structure

```
Multi-Tenant_Data_Source_Routing/
├── pom.xml                                    # Parent Multi-Module POM
├── docker-compose.yml                         # Container Orchestration
├── .env.example                               # Environment Variables Documentation
├── db-init/
│   └── init-tenant-dbs.sh                     # Automated DB Creation Script
├── multitenancy-spring-boot-starter/           # Starter Module Library
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/multitenancy/
│       │   ├── TenantContext.java
│       │   ├── resolver/
│       │   │   ├── TenantResolver.java
│       │   │   └── HeaderTenantResolver.java
│       │   ├── datasource/
│       │   │   └── TenantAwareRoutingDataSource.java
│       │   ├── interceptor/
│       │   │   └── TenantInterceptor.java
│       │   ├── properties/
│       │   │   └── MultiTenancyProperties.java
│       │   ├── health/
│       │   │   └── TenantDataSourcesHealthContributor.java
│       │   └── autoconfigure/
│       │       └── MultiTenancyAutoConfiguration.java
│       └── resources/META-INF/
│           ├── spring/
│           │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│           └── spring.factories
└── demo-application/                          # Consumer Demo Application
    ├── pom.xml
    ├── Dockerfile                             # Multi-stage Container Build
    └── src/
        ├── main/
        │   ├── java/com/example/demo/
        │   │   ├── DemoApplication.java
        │   │   ├── entity/User.java
        │   │   ├── repository/UserRepository.java
        │   │   ├── controller/UserController.java
        │   │   └── config/TenantSchemaInitializer.java
        │   └── resources/
        │       └── application.yml
        └── test/java/com/example/demo/
            └── MultiTenantIntegrationTest.java
```

---

## Quick Start & Local Setup

### Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Docker & Docker Compose**

### Building the Starter & Demo App Locally

To compile and install the starter JAR into your local Maven repository (`~/.m2/repository`), run:

```bash
mvn clean install
```

This compiles both `multitenancy-spring-boot-starter` and `demo-application`, running all automated unit and integration tests.

---

## Running with Docker Compose

The application and database services are fully containerized and automated using Docker Compose.

### Start Services

From the project root directory, run:

```bash
docker-compose up --build -d
```

This single command:
1. Starts the `postgres_db` container running PostgreSQL 14.
2. Runs `db-init/init-tenant-dbs.sh` on PostgreSQL startup to create `tenant1_db`, `tenant2_db`, and `tenant3_db`.
3. Waits until PostgreSQL passes its healthcheck (`pg_isready`).
4. Builds and starts the `demo_app` container running on port `8080`.
5. Performs container health checks via `/actuator/health`.

### Verify Container Status

```bash
docker-compose ps
```

You should see both containers in `healthy` status:

```
NAME          IMAGE                                COMMAND                  SERVICE   CREATED          STATUS                    PORTS
demo_app      multi-tenant_data_source_routing-app   "java -jar app.jar"      app       30 seconds ago   Up 30 seconds (healthy)   0.0.0.0:8080->8080/tcp
postgres_db   postgres:14                          "docker-entrypoint.s…"   db        30 seconds ago   Up 30 seconds (healthy)   0.0.0.0:5432->5432/tcp
```

### Stop Services

```bash
docker-compose down -v
```

---

## Configuration Guide (`application.yml`)

The demo application configures tenant databases in `demo-application/src/main/resources/application.yml`:

```yaml
server:
  port: 8080

multitenancy:
  enabled: true
  tenants:
    - id: tenant1
      url: ${TENANT1_URL:jdbc:postgresql://db:5432/tenant1_db}
      username: ${POSTGRES_USER:user}
      password: ${POSTGRES_PASSWORD:password}
      driver-class-name: org.postgresql.Driver
    - id: tenant2
      url: ${TENANT2_URL:jdbc:postgresql://db:5432/tenant2_db}
      username: ${POSTGRES_USER:user}
      password: ${POSTGRES_PASSWORD:password}
      driver-class-name: org.postgresql.Driver
    - id: tenant3
      url: ${TENANT3_URL:jdbc:postgresql://db:5432/tenant3_db}
      username: ${POSTGRES_USER:user}
      password: ${POSTGRES_PASSWORD:password}
      driver-class-name: org.postgresql.Driver

spring:
  jpa:
    hibernate:
      ddl-auto: update

management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
```

---

## Postman Collection

A pre-configured Postman Collection ([postman_collection.json](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/postman_collection.json)) is included in the project root directory for interactive testing and automated API verification.

### Included Request Suites:
1. **Actuator & Health**:
   - `GET Tenant Data Sources Health`: Checks `/actuator/health/datasources` to confirm tenant DB connections (`tenant1`, `tenant2`, `tenant3`) are UP.
   - `GET General Application Health`: Checks `/actuator/health`.
2. **Tenant 1 Operations**:
   - `POST Create User - Tenant 1`: Creates user record in `tenant1_db`.
   - `GET List Users - Tenant 1`: Retrieves users isolated in `tenant1_db`.
   - `GET User by ID - Tenant 1`: Retrieves single user by ID in `tenant1_db`.
3. **Tenant 2 Operations**:
   - `POST Create User - Tenant 2`: Creates user record in `tenant2_db`.
   - `GET List Users - Tenant 2`: Verifies strict isolation (Tenant 1 users are not visible).
   - `GET User by ID - Tenant 2`: Retrieves user in `tenant2_db`.
4. **Error Handling & Interceptors**:
   - `GET Missing X-Tenant-ID Header`: Verifies HTTP `400 Bad Request` handling when header is absent.
   - `GET Unknown Tenant`: Verifies HTTP `404 Not Found` handling for unconfigured tenant IDs.

### Quick Start with Postman:
1. Open **Postman** and click **Import**.
2. Select the `postman_collection.json` file from the repository root.
3. Select **Multi-Tenant Data Source Routing API** collection and click **Run Collection**.
4. All requests include built-in JavaScript tests (`pm.test(...)`) validating HTTP status codes and response bodies automatically!

---

## API Endpoints & Verification Examples

### 1. Create User in Tenant 1 (`POST /api/users`)

```bash
curl -X POST http://localhost:8080/api/users \
  -H "X-Tenant-ID: tenant1" \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Smith", "email": "alice@tenant1.com"}'
```

**Response (201 Created):**
```json
{
  "id": 1,
  "name": "Alice Smith",
  "email": "alice@tenant1.com"
}
```

---

### 2. Create User in Tenant 2 (`POST /api/users`)

```bash
curl -X POST http://localhost:8080/api/users \
  -H "X-Tenant-ID: tenant2" \
  -H "Content-Type: application/json" \
  -d '{"name": "Bob Jones", "email": "bob@tenant2.com"}'
```

**Response (201 Created):**
```json
{
  "id": 1,
  "name": "Bob Jones",
  "email": "bob@tenant2.com"
}
```

---

### 3. List Users for Tenant 1 (`GET /api/users`)

```bash
curl -X GET http://localhost:8080/api/users \
  -H "X-Tenant-ID: tenant1"
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "Alice Smith",
    "email": "alice@tenant1.com"
  }
]
```

---

### 4. List Users for Tenant 2 (`GET /api/users`)

```bash
curl -X GET http://localhost:8080/api/users \
  -H "X-Tenant-ID: tenant2"
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "Bob Jones",
    "email": "bob@tenant2.com"
  }
]
```

> **Data Isolation Verification**: Querying `tenant1` returns only Alice. Querying `tenant2` returns only Bob. Each record exists strictly within its respective database (`tenant1_db` vs `tenant2_db`).

---

### 5. Fetch Single User by ID (`GET /api/users/{id}`)

- **Querying Tenant 1 for User ID 1:**
```bash
curl -i -X GET http://localhost:8080/api/users/1 \
  -H "X-Tenant-ID: tenant1"
```
*Returns `HTTP/1.1 200 OK` with Alice Smith's user payload.*

- **Querying Tenant 2 for User ID 1:**
```bash
curl -i -X GET http://localhost:8080/api/users/1 \
  -H "X-Tenant-ID: tenant2"
```
*Returns `HTTP/1.1 404 Not Found` (proving user lookup is strictly tenant-scoped).*

---

### 6. Missing Header Error Handling (`400 Bad Request`)

```bash
curl -i -X GET http://localhost:8080/api/users
```

**Response (400 Bad Request):**
```json
{
  "error": "Bad Request",
  "message": "X-Tenant-ID header is missing"
}
```

---

### 7. Unconfigured Tenant Error Handling (`404 Not Found`)

```bash
curl -i -X GET http://localhost:8080/api/users \
  -H "X-Tenant-ID: unknown_tenant"
```

**Response (404 Not Found):**
```json
{
  "error": "Not Found",
  "message": "Tenant not found: unknown_tenant"
}
```

---

### 8. Tenant Data Sources Health Indicator (`GET /actuator/health/datasources`)

```bash
curl -X GET http://localhost:8080/actuator/health/datasources
```

**Response (200 OK):**
```json
{
  "status": "UP",
  "components": {
    "tenant1": {
      "status": "UP",
      "details": {
        "tenantId": "tenant1",
        "database": "PostgreSQL",
        "url": "jdbc:postgresql://db:5432/tenant1_db"
      }
    },
    "tenant2": {
      "status": "UP",
      "details": {
        "tenantId": "tenant2",
        "database": "PostgreSQL",
        "url": "jdbc:postgresql://db:5432/tenant2_db"
      }
    },
    "tenant3": {
      "status": "UP",
      "details": {
        "tenantId": "tenant3",
        "database": "PostgreSQL",
        "url": "jdbc:postgresql://db:5432/tenant3_db"
      }
    }
  }
}
```

---

## Direct Database Verification

To inspect PostgreSQL tables directly inside the running database container:

```bash
# Connect to PostgreSQL shell
docker exec -it postgres_db psql -U user -d tenant1_db

# Query tenant1_db
SELECT * FROM users;

# Connect to tenant2_db
\c tenant2_db
SELECT * FROM users;
```

---

## Submission Checklist Verification

| Requirement | Implementation Artifact | Status |
| :--- | :--- | :---: |
| **Containerization & Healthchecks** | [docker-compose.yml](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/docker-compose.yml), [db-init/init-tenant-dbs.sh](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/db-init/init-tenant-dbs.sh) | ✅ Verified |
| **Starter Auto-Configuration** | [AutoConfiguration.imports](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/multitenancy-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | ✅ Verified |
| **YAML Tenant Configuration** | [application.yml](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/demo-application/src/main/resources/application.yml) | ✅ Verified |
| **POST /api/users & Tenant Routing** | [UserController.java](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/demo-application/src/main/java/com/example/demo/controller/UserController.java) | ✅ Verified |
| **GET /api/users Tenant 1 Isolation** | [MultiTenantIntegrationTest.java](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/demo-application/src/test/java/com/example/demo/MultiTenantIntegrationTest.java) | ✅ Verified |
| **GET /api/users Tenant 2 Isolation** | [MultiTenantIntegrationTest.java](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/demo-application/src/test/java/com/example/demo/MultiTenantIntegrationTest.java) | ✅ Verified |
| **GET /api/users/{id} Tenant Lookup** | [UserController.java](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/demo-application/src/main/java/com/example/demo/controller/UserController.java) | ✅ Verified |
| **Missing Header (400 Bad Request)** | [TenantInterceptor.java](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/multitenancy-spring-boot-starter/src/main/java/com/example/multitenancy/interceptor/TenantInterceptor.java) | ✅ Verified |
| **Unknown Tenant (404 Not Found)** | [TenantInterceptor.java](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/multitenancy-spring-boot-starter/src/main/java/com/example/multitenancy/interceptor/TenantInterceptor.java) | ✅ Verified |
| **Custom Health Endpoint** | [TenantDataSourcesHealthContributor.java](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/multitenancy-spring-boot-starter/src/main/java/com/example/multitenancy/health/TenantDataSourcesHealthContributor.java) | ✅ Verified |
| **Postman Collection Verification** | [postman_collection.json](file:///d:/Partnr/Main/week31/Multi-Tenant_Data_Source_Routing/postman_collection.json) | ✅ Verified |

---

## License

MIT License. See LICENSE file for details.
