# OpsPilot Backend

Production-grade Spring Boot backend for OpsPilot IT Operations Management platform.

## Tech Stack

- **Spring Boot 3.2.1**
- **Java 17**
- **PostgreSQL**
- **Flyway** (Database migrations)
- **JWT** (Authentication)
- **Spring Security**
- **Maven**

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker (for PostgreSQL)

## Database Setup

Run PostgreSQL using Docker:

```bash
docker run --name itops-postgres \
  -e POSTGRES_DB=itops \
  -e POSTGRES_USER=itops_user \
  -e POSTGRES_PASSWORD=itops_pass \
  -p 5432:5432 \
  -d postgres:15
```

## Build & Run

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run
```

The API will be available at: `http://localhost:8080/api/v1`

## API Endpoints

### Authentication
- `POST /api/v1/auth/login` - Login

### Projects
- `GET /api/v1/projects` - List all projects
- `GET /api/v1/projects/{id}` - Get project by ID
- `POST /api/v1/projects` - Create project
- `PUT /api/v1/projects/{id}` - Update project
- `DELETE /api/v1/projects/{id}` - Delete project

### Tasks
- `GET /api/v1/tasks` - List all tasks
- `GET /api/v1/tasks/{id}` - Get task by ID
- `POST /api/v1/tasks` - Create task
- `PUT /api/v1/tasks/{id}` - Update task
- `DELETE /api/v1/tasks/{id}` - Delete task

### Clients
- `GET /api/v1/clients` - List all clients
- `GET /api/v1/clients/{id}` - Get client by ID

### Time Entries
- `GET /api/v1/time-entries` - List all time entries

### Invoices
- `GET /api/v1/invoices` - List all invoices

## Authentication

Use Bearer token in Authorization header:
```
Authorization: Bearer <access_token>
```

## Multi-tenancy

All entities are isolated by `companyId`. The JWT token contains the company ID, and all queries automatically filter by this ID.

## Database Schema

Database schema is managed by Flyway. Initial migration creates:
- companies
- users
- clients
- projects
- tasks
- time_entries
- invoices
- invoice_items

All tables use UUID as primary keys and include soft delete support.
