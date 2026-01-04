#!/bin/bash

################################################################################
# Backend Deployment Script for Amazon Linux 2023
# ITOps SaaS Platform - Backend Installation
################################################################################

set -e  # Exit on any error

echo "=========================================="
echo "ITOps SaaS Backend Deployment - Amazon Linux"
echo "=========================================="
echo ""

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
DB_NAME="itops"
DB_USER="itops_user"
DB_PASSWORD="Change_This_Password_123!"
JWT_SECRET="Change_This_JWT_Secret_Min_256_Bits_Production_Key_123456789"
APP_DIR="/home/ec2-user/itops-saas-backend"
DOMAIN="api.yourdomain.com"

echo -e "${YELLOW}Step 1: System Update${NC}"
echo "Running: sudo dnf update -y"
sudo dnf update -y
# Expected output:
# Last metadata expiration check: 0:00:05 ago
# Dependencies resolved.
# Nothing to do.
# Complete!

echo ""
echo -e "${GREEN}✓ System updated${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 2: Install Java 17${NC}"
echo "Running: sudo dnf install -y java-17-amazon-corretto-devel"
sudo dnf install -y java-17-amazon-corretto-devel

echo "Verifying Java installation..."
java -version
# Expected output:
# openjdk version "17.0.9" 2023-10-17 LTS
# OpenJDK Runtime Environment Corretto-17.0.9.8.1 (build 17.0.9+8-LTS)
# OpenJDK 64-Bit Server VM Corretto-17.0.9.8.1 (build 17.0.9+8-LTS, mixed mode, sharing)

javac -version
# Expected output:
# javac 17.0.9

echo ""
echo -e "${GREEN}✓ Java 17 installed successfully${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 3: Install PostgreSQL 15${NC}"
echo "Installing PostgreSQL repository..."
sudo dnf install -y postgresql15-server postgresql15-contrib

echo "Initializing PostgreSQL database..."
sudo postgresql-setup --initdb
# Expected output:
# Initializing database ... OK

echo "Starting PostgreSQL service..."
sudo systemctl start postgresql
sudo systemctl enable postgresql
# Expected output:
# Created symlink /etc/systemd/system/multi-user.target.wants/postgresql.service

echo "Checking PostgreSQL status..."
sudo systemctl status postgresql --no-pager -l
# Expected output:
# ● postgresql.service - PostgreSQL database server
#    Loaded: loaded (/usr/lib/systemd/system/postgresql.service; enabled)
#    Active: active (running)

echo ""
echo -e "${GREEN}✓ PostgreSQL 15 installed and running${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 4: Configure PostgreSQL${NC}"
echo "Configuring PostgreSQL for network access..."

# Backup original config
sudo cp /var/lib/pgsql/data/postgresql.conf /var/lib/pgsql/data/postgresql.conf.backup
sudo cp /var/lib/pgsql/data/pg_hba.conf /var/lib/pgsql/data/pg_hba.conf.backup

# Update postgresql.conf
sudo sed -i "s/#listen_addresses = 'localhost'/listen_addresses = 'localhost'/" /var/lib/pgsql/data/postgresql.conf

# Update pg_hba.conf for local connections
echo "local   all             all                                     peer" | sudo tee -a /var/lib/pgsql/data/pg_hba.conf
echo "host    all             all             127.0.0.1/32            md5" | sudo tee -a /var/lib/pgsql/data/pg_hba.conf

echo "Restarting PostgreSQL..."
sudo systemctl restart postgresql

echo ""
echo -e "${GREEN}✓ PostgreSQL configured${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 5: Create Database and User${NC}"
echo "Creating database: ${DB_NAME}"
sudo -u postgres psql -c "CREATE DATABASE ${DB_NAME};"
# Expected output:
# CREATE DATABASE

echo "Creating user: ${DB_USER}"
sudo -u postgres psql -c "CREATE USER ${DB_USER} WITH ENCRYPTED PASSWORD '${DB_PASSWORD}';"
# Expected output:
# CREATE ROLE

echo "Granting privileges..."
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};"
# Expected output:
# GRANT

sudo -u postgres psql -d ${DB_NAME} -c "GRANT ALL ON SCHEMA public TO ${DB_USER};"
# Expected output:
# GRANT

echo "Verifying database creation..."
sudo -u postgres psql -c "\l" | grep ${DB_NAME}
# Expected output:
# itops          | postgres | UTF8     | en_US.UTF-8 | en_US.UTF-8 |

echo ""
echo -e "${GREEN}✓ Database created and configured${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 6: Install Maven${NC}"
echo "Installing Maven..."
sudo dnf install -y maven

echo "Verifying Maven installation..."
mvn -version
# Expected output:
# Apache Maven 3.8.4 (Amazon Linux)
# Maven home: /usr/share/maven
# Java version: 17.0.9, vendor: Amazon.com Inc.

echo ""
echo -e "${GREEN}✓ Maven installed successfully${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 7: Install Git${NC}"
echo "Installing Git..."
sudo dnf install -y git

echo "Verifying Git installation..."
git --version
# Expected output:
# git version 2.40.1

echo ""
echo -e "${GREEN}✓ Git installed successfully${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 8: Clone Application Repository${NC}"
echo "Note: Replace with your actual repository URL"
echo "Example: git clone https://github.com/yourusername/itops-saas-backend.git"
echo ""
read -p "Enter your Git repository URL (or press Enter to skip): " REPO_URL

if [ -n "$REPO_URL" ]; then
    echo "Cloning repository..."
    cd /home/ec2-user
    git clone $REPO_URL
    cd itops-saas-backend
    echo -e "${GREEN}✓ Repository cloned${NC}"
else
    echo -e "${YELLOW}⚠ Skipping repository clone. Manual clone required.${NC}"
    mkdir -p $APP_DIR
    cd $APP_DIR
fi

echo ""

################################################################################
echo -e "${YELLOW}Step 9: Create Application Configuration${NC}"
echo "Creating application.yml..."

mkdir -p src/main/resources

cat > src/main/resources/application.yml << EOF
server:
  port: 8081
  servlet:
    context-path: /api/v1

spring:
  application:
    name: itops-saas-backend
  
  datasource:
    url: jdbc:postgresql://localhost:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
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
  secret: ${JWT_SECRET}
  access-token-expiration: 86400000
  refresh-token-expiration: 2592000000

logging:
  level:
    com.itops: INFO
    org.springframework: INFO
EOF

echo -e "${GREEN}✓ Application configuration created${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 10: Build Application${NC}"
echo "Building with Maven (this may take several minutes)..."
mvn clean package -DskipTests
# Expected output:
# [INFO] Building ITOps SaaS Backend 1.0.0
# [INFO] ------------------------------------------------------------------------
# [INFO] BUILD SUCCESS
# [INFO] ------------------------------------------------------------------------

echo ""
echo -e "${GREEN}✓ Application built successfully${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 11: Create Systemd Service${NC}"
echo "Creating systemd service file..."

sudo tee /etc/systemd/system/itops-backend.service > /dev/null << EOF
[Unit]
Description=ITOps SaaS Backend Service
After=syslog.target network.target postgresql.service

[Service]
User=ec2-user
Group=ec2-user
WorkingDirectory=${APP_DIR}
ExecStart=/usr/bin/java -jar ${APP_DIR}/target/itops-saas-backend-1.0.0.jar
SuccessExitStatus=143
StandardOutput=journal
StandardError=journal
Restart=always
RestartSec=10

Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC"

[Install]
WantedBy=multi-user.target
EOF

echo "Reloading systemd daemon..."
sudo systemctl daemon-reload

echo "Enabling service on boot..."
sudo systemctl enable itops-backend
# Expected output:
# Created symlink /etc/systemd/system/multi-user.target.wants/itops-backend.service

echo "Starting service..."
sudo systemctl start itops-backend

sleep 5

echo "Checking service status..."
sudo systemctl status itops-backend --no-pager -l
# Expected output:
# ● itops-backend.service - ITOps SaaS Backend Service
#    Loaded: loaded (/etc/systemd/system/itops-backend.service; enabled)
#    Active: active (running)

echo ""
echo -e "${GREEN}✓ Service created and started${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 12: Install and Configure Nginx${NC}"
echo "Installing Nginx..."
sudo dnf install -y nginx

echo "Starting and enabling Nginx..."
sudo systemctl start nginx
sudo systemctl enable nginx
# Expected output:
# Created symlink /etc/systemd/system/multi-user.target.wants/nginx.service

echo "Creating Nginx configuration..."
sudo tee /etc/nginx/conf.d/itops-backend.conf > /dev/null << EOF
server {
    listen 80;
    server_name ${DOMAIN};

    location /api/v1/ {
        proxy_pass http://localhost:8081/api/v1/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
EOF

echo "Testing Nginx configuration..."
sudo nginx -t
# Expected output:
# nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
# nginx: configuration file /etc/nginx/nginx.conf test is successful

echo "Restarting Nginx..."
sudo systemctl restart nginx

echo ""
echo -e "${GREEN}✓ Nginx installed and configured${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 13: Configure Firewall${NC}"
echo "Opening HTTP and HTTPS ports..."
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
# Expected output:
# success
# success
# success

echo ""
echo -e "${GREEN}✓ Firewall configured${NC}"
echo ""

################################################################################
echo -e "${YELLOW}Step 14: Install SSL Certificate (Optional)${NC}"
echo "Installing Certbot for Let's Encrypt..."
sudo dnf install -y certbot python3-certbot-nginx

echo ""
echo "To obtain SSL certificate, run:"
echo "sudo certbot --nginx -d ${DOMAIN}"
echo ""

################################################################################
echo ""
echo "=========================================="
echo -e "${GREEN}Installation Complete!${NC}"
echo "=========================================="
echo ""
echo "Service Status:"
sudo systemctl is-active itops-backend && echo -e "Backend Service: ${GREEN}Running${NC}" || echo -e "Backend Service: ${RED}Stopped${NC}"
sudo systemctl is-active postgresql && echo -e "PostgreSQL: ${GREEN}Running${NC}" || echo -e "PostgreSQL: ${RED}Stopped${NC}"
sudo systemctl is-active nginx && echo -e "Nginx: ${GREEN}Running${NC}" || echo -e "Nginx: ${RED}Stopped${NC}"

echo ""
echo "Important Information:"
echo "----------------------"
echo "Application URL: http://${DOMAIN}/api/v1"
echo "Database Name: ${DB_NAME}"
echo "Database User: ${DB_USER}"
echo "Database Password: ${DB_PASSWORD}"
echo "JWT Secret: ${JWT_SECRET}"
echo ""
echo -e "${RED}⚠ IMPORTANT: Change default passwords and secrets!${NC}"
echo ""
echo "Useful Commands:"
echo "----------------"
echo "View logs: sudo journalctl -u itops-backend -f"
echo "Restart service: sudo systemctl restart itops-backend"
echo "Check status: sudo systemctl status itops-backend"
echo "Database backup: pg_dump -U ${DB_USER} ${DB_NAME} > backup.sql"
echo ""
echo "Next Steps:"
echo "-----------"
echo "1. Update domain in /etc/nginx/conf.d/itops-backend.conf"
echo "2. Obtain SSL certificate: sudo certbot --nginx -d ${DOMAIN}"
echo "3. Change database password and JWT secret"
echo "4. Configure your DNS to point to this server"
echo "5. Test the API: curl http://localhost:8081/api/v1/auth/health"
echo ""
echo "=========================================="
