# Backend Deployment Guide - ITOps SaaS Platform

## Prerequisites

### Required Software
- **Java Development Kit (JDK)**: 17 or higher
- **PostgreSQL**: 15.x
- **Maven**: 3.8 or higher
- **Git**: Latest version

## Installation Steps

### 1. Install Java 17 (Ubuntu/Debian)

```bash
# Update package list
sudo apt update

# Install OpenJDK 17
sudo apt install -y openjdk-17-jdk

# Verify installation
java -version
javac -version
```

### 2. Install PostgreSQL 15

```bash
# Add PostgreSQL repository
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -

# Update and install
sudo apt update
sudo apt install -y postgresql-15 postgresql-contrib-15

# Start PostgreSQL service
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Verify installation
sudo -u postgres psql --version
```

### 3. Setup Database

```bash
# Switch to postgres user
sudo -u postgres psql

# Create database and user (in PostgreSQL shell)
CREATE DATABASE itops;
CREATE USER itops_user WITH ENCRYPTED PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE itops TO itops_user;
\q
```

### 4. Install Maven

```bash
# Install Maven
sudo apt install -y maven

# Verify installation
mvn -version
```

### 5. Clone and Configure Application

```bash
# Clone repository
git clone <your-repository-url>
cd itops-saas-backend

# Create application properties
nano src/main/resources/application.yml
```

**Configure application.yml:**

```yaml
server:
  port: 8081
  servlet:
    context-path: /api/v1

spring:
  application:
    name: itops-saas-backend
  
  datasource:
    url: jdbc:postgresql://localhost:5432/itops
    username: itops_user
    password: your_secure_password
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

jwt:
  secret: your-super-secure-jwt-secret-key-min-256-bits-change-this-in-production
  access-token-expiration: 86400000      # 24 hours
  refresh-token-expiration: 2592000000   # 30 days

logging:
  level:
    com.itops: INFO
    org.springframework: INFO
```

### 6. Build Application

```bash
# Clean and build
mvn clean package -DskipTests

# Or with tests
mvn clean package
```

### 7. Run Application

**Development Mode:**
```bash
mvn spring-boot:run
```

**Production Mode (JAR):**
```bash
# Run the JAR file
java -jar target/itops-saas-backend-1.0.0.jar
```

## Deployment with Systemd Service

### Create Service File

```bash
sudo nano /etc/systemd/system/itops-backend.service
```

**Service Configuration:**

```ini
[Unit]
Description=ITOps SaaS Backend
After=syslog.target network.target postgresql.service

[Service]
User=ubuntu
Group=ubuntu
WorkingDirectory=/home/ubuntu/itops-saas-backend
ExecStart=/usr/bin/java -jar /home/ubuntu/itops-saas-backend/target/itops-saas-backend-1.0.0.jar
SuccessExitStatus=143
StandardOutput=journal
StandardError=journal
Restart=always
RestartSec=10

Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="JAVA_OPTS=-Xms512m -Xmx1024m"

[Install]
WantedBy=multi-user.target
```

### Start Service

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service on boot
sudo systemctl enable itops-backend

# Start service
sudo systemctl start itops-backend

# Check status
sudo systemctl status itops-backend

# View logs
sudo journalctl -u itops-backend -f
```

## Nginx Reverse Proxy

### Install Nginx

```bash
sudo apt install -y nginx
```

### Configure Nginx

```bash
sudo nano /etc/nginx/sites-available/itops-backend
```

**Nginx Configuration:**

```nginx
server {
    listen 80;
    server_name api.yourdomain.com;

    location /api/v1/ {
        proxy_pass http://localhost:8081/api/v1/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

### Enable Site

```bash
# Create symbolic link
sudo ln -s /etc/nginx/sites-available/itops-backend /etc/nginx/sites-enabled/

# Test configuration
sudo nginx -t

# Restart Nginx
sudo systemctl restart nginx
```

## SSL/TLS with Let's Encrypt

```bash
# Install Certbot
sudo apt install -y certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d api.yourdomain.com

# Auto-renewal is configured automatically
# Test renewal
sudo certbot renew --dry-run
```

## Environment Variables (Production)

Create `.env` file or set in systemd service:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/itops
SPRING_DATASOURCE_USERNAME=itops_user
SPRING_DATASOURCE_PASSWORD=your_secure_password
JWT_SECRET=your-production-jwt-secret-min-256-bits
JWT_ACCESS_TOKEN_EXPIRATION=86400000
JWT_REFRESH_TOKEN_EXPIRATION=2592000000
SPRING_PROFILES_ACTIVE=prod
```

## Database Backup

```bash
# Backup database
pg_dump -U itops_user itops > backup_$(date +%Y%m%d_%H%M%S).sql

# Restore database
psql -U itops_user itops < backup_20260104_120000.sql
```

## Monitoring & Logs

```bash
# View application logs
sudo journalctl -u itops-backend -f

# View Nginx logs
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log

# Check application health
curl http://localhost:8081/api/v1/actuator/health
```

## Package Dependencies

### Spring Boot (Version 3.2.1)
- `spring-boot-starter-web` - REST API
- `spring-boot-starter-data-jpa` - Database ORM
- `spring-boot-starter-security` - Authentication
- `spring-boot-starter-validation` - Input validation

### Database
- `postgresql` (driver 42.7.1) - PostgreSQL connection
- `flyway-core` (9.22.3) - Database migrations

### Security & JWT
- `jjwt-api` (0.12.3) - JWT API
- `jjwt-impl` (0.12.3) - JWT implementation
- `jjwt-jackson` (0.12.3) - JWT JSON processing

### PDF Generation
- `openpdf` (1.3.30) - Invoice & report PDFs

### Utilities
- `lombok` (1.18.30) - Code generation

## Troubleshooting

### Application won't start
```bash
# Check if port 8081 is already in use
sudo lsof -i :8081

# Check PostgreSQL is running
sudo systemctl status postgresql

# Check application logs
sudo journalctl -u itops-backend --no-pager -n 100
```

### Database connection issues
```bash
# Test PostgreSQL connection
psql -U itops_user -d itops -h localhost -p 5432

# Check PostgreSQL logs
sudo tail -f /var/log/postgresql/postgresql-15-main.log
```

### Out of memory
```bash
# Increase JVM memory in systemd service
Environment="JAVA_OPTS=-Xms1024m -Xmx2048m"

# Restart service
sudo systemctl restart itops-backend
```

## Security Checklist

- [ ] Change default JWT secret
- [ ] Use strong database password
- [ ] Enable SSL/TLS with Let's Encrypt
- [ ] Configure firewall (ufw)
- [ ] Disable unnecessary ports
- [ ] Set up regular database backups
- [ ] Configure log rotation
- [ ] Enable fail2ban for SSH
- [ ] Keep system and packages updated
- [ ] Use environment variables for secrets

## Performance Optimization

### JVM Tuning
```bash
# In systemd service file
Environment="JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### PostgreSQL Tuning
```bash
# Edit postgresql.conf
sudo nano /etc/postgresql/15/main/postgresql.conf

# Recommended settings for 2GB RAM
shared_buffers = 512MB
effective_cache_size = 1536MB
maintenance_work_mem = 128MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
work_mem = 2621kB
min_wal_size = 1GB
max_wal_size = 4GB
```

## Update Deployment

```bash
# Stop service
sudo systemctl stop itops-backend

# Pull latest changes
git pull origin main

# Rebuild
mvn clean package -DskipTests

# Start service
sudo systemctl start itops-backend

# Check logs
sudo journalctl -u itops-backend -f
```

## Quick Commands Reference

```bash
# Build
mvn clean package -DskipTests

# Run locally
mvn spring-boot:run

# Start service
sudo systemctl start itops-backend

# Stop service
sudo systemctl stop itops-backend

# Restart service
sudo systemctl restart itops-backend

# View logs
sudo journalctl -u itops-backend -f

# Check status
sudo systemctl status itops-backend

# Database backup
pg_dump -U itops_user itops > backup.sql
```
