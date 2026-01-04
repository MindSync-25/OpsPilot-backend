# Amazon Linux Deployment Guide

## Quick Start

### Backend Deployment
```bash
# Download the script
wget https://raw.githubusercontent.com/your-repo/deploy-backend-amazonlinux.sh
# Or copy the script to your server

# Make it executable
chmod +x deploy-backend-amazonlinux.sh

# Run the script
sudo ./deploy-backend-amazonlinux.sh
```

### Frontend Deployment
```bash
# Download the script
wget https://raw.githubusercontent.com/your-repo/deploy-frontend-amazonlinux.sh
# Or copy the script to your server

# Make it executable
chmod +x deploy-frontend-amazonlinux.sh

# Run the script
./deploy-frontend-amazonlinux.sh
```

## What the Scripts Do

### Backend Script (`deploy-backend-amazonlinux.sh`)

**Installs and Configures:**
1. Java 17 (Amazon Corretto)
2. PostgreSQL 15
3. Maven 3.8+
4. Git
5. Creates database and user
6. Clones your repository
7. Builds the application
8. Creates systemd service
9. Installs and configures Nginx reverse proxy
10. Configures firewall
11. Installs Certbot for SSL

**Expected Output Snippets:**

```
==========================================
ITOps SaaS Backend Deployment - Amazon Linux
==========================================

Step 1: System Update
Running: sudo dnf update -y
Last metadata expiration check: 0:00:05 ago
Dependencies resolved.
Complete!
✓ System updated

Step 2: Install Java 17
Running: sudo dnf install -y java-17-amazon-corretto-devel
openjdk version "17.0.9" 2023-10-17 LTS
✓ Java 17 installed successfully

Step 3: Install PostgreSQL 15
Initializing database ... OK
Created symlink /etc/systemd/system/multi-user.target.wants/postgresql.service
✓ PostgreSQL 15 installed and running

Step 5: Create Database and User
CREATE DATABASE
CREATE ROLE
GRANT
✓ Database created and configured

Step 11: Create Systemd Service
Created symlink /etc/systemd/system/multi-user.target.wants/itops-backend.service
● itops-backend.service - ITOps SaaS Backend Service
   Active: active (running)
✓ Service created and started

==========================================
Installation Complete!
==========================================

Service Status:
Backend Service: Running
PostgreSQL: Running
Nginx: Running

Important Information:
----------------------
Application URL: http://api.yourdomain.com/api/v1
Database Name: itops
Database User: itops_user
```

### Frontend Script (`deploy-frontend-amazonlinux.sh`)

**Installs and Configures:**
1. Node.js 20 (via nvm)
2. Git
3. Clones your repository
4. Creates environment configuration
5. Installs npm dependencies
6. Builds production bundle
7. Installs and configures Nginx
8. Sets up static file serving
9. Configures firewall
10. Creates update script
11. Optionally sets up PM2

**Expected Output Snippets:**

```
==========================================
ITOps SaaS Frontend Deployment - Amazon Linux
==========================================

Step 2: Install Node.js 20
Installing nvm (Node Version Manager)...
=> Downloading nvm from git to '/home/ec2-user/.nvm'
Now using node v20.11.0 (npm v10.2.4)
✓ Node.js 20 installed successfully

Step 6: Install Dependencies
added 567 packages, and audited 568 packages in 45s
found 0 vulnerabilities
✓ Dependencies installed

Step 7: Build Application
vite v7.2.4 building for production...
✓ 1234 modules transformed.
✓ built in 23.45s
✓ Application built successfully

Step 8: Install and Configure Nginx
nginx: configuration file /etc/nginx/nginx.conf test is successful
✓ Nginx installed and configured

==========================================
Installation Complete!
==========================================

Service Status:
Nginx: Running

Important Information:
----------------------
Application URL: http://yourdomain.com
Deployment Method: Nginx Static Files
```

## Manual Configuration After Running Scripts

### 1. Update Backend Configuration

Edit the application.yml if needed:
```bash
sudo nano /home/ec2-user/itops-saas-backend/src/main/resources/application.yml
```

Change database password and JWT secret:
```yaml
spring:
  datasource:
    password: YOUR_STRONG_PASSWORD

jwt:
  secret: YOUR_256_BIT_SECRET_KEY
```

Rebuild and restart:
```bash
cd /home/ec2-user/itops-saas-backend
mvn clean package -DskipTests
sudo systemctl restart itops-backend
```

### 2. Update Frontend API URL

Edit environment file:
```bash
nano /home/ec2-user/itops-saas-frontend/.env.production
```

Update API URL:
```
VITE_API_URL=https://api.yourdomain.com/api/v1
```

Rebuild:
```bash
cd /home/ec2-user/itops-saas-frontend
npm run build
sudo cp -r dist/* /var/www/itops-frontend/
```

### 3. Configure SSL Certificates

**Backend:**
```bash
sudo certbot --nginx -d api.yourdomain.com
```

**Frontend:**
```bash
sudo certbot --nginx -d yourdomain.com -d www.yourdomain.com
```

### 4. Update Domain Names

**Backend Nginx:**
```bash
sudo nano /etc/nginx/conf.d/itops-backend.conf
```
Change `server_name api.yourdomain.com;`

**Frontend Nginx:**
```bash
sudo nano /etc/nginx/conf.d/itops-frontend.conf
```
Change `server_name yourdomain.com www.yourdomain.com;`

Restart Nginx:
```bash
sudo systemctl restart nginx
```

## Common Commands

### Backend Management
```bash
# View logs
sudo journalctl -u itops-backend -f

# Restart service
sudo systemctl restart itops-backend

# Check status
sudo systemctl status itops-backend

# Stop service
sudo systemctl stop itops-backend

# Start service
sudo systemctl start itops-backend

# Database backup
pg_dump -U itops_user itops > backup_$(date +%Y%m%d).sql

# Database restore
psql -U itops_user itops < backup_20260104.sql
```

### Frontend Management
```bash
# Update deployment
cd /home/ec2-user/itops-saas-frontend
./update-frontend.sh

# Manual update
git pull origin main
npm install
npm run build
sudo cp -r dist/* /var/www/itops-frontend/

# View Nginx logs
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log

# Restart Nginx
sudo systemctl restart nginx

# Test Nginx config
sudo nginx -t
```

## Troubleshooting

### Backend Issues

**Service won't start:**
```bash
# Check logs
sudo journalctl -u itops-backend --no-pager -n 100

# Check if port is in use
sudo ss -tulpn | grep 8081

# Check PostgreSQL
sudo systemctl status postgresql
```

**Database connection failed:**
```bash
# Test connection
psql -U itops_user -d itops -h localhost

# Check PostgreSQL logs
sudo tail -f /var/lib/pgsql/data/log/postgresql-*.log
```

### Frontend Issues

**404 errors:**
```bash
# Check files exist
ls -la /var/www/itops-frontend/

# Check Nginx config
sudo nginx -t

# Check permissions
sudo chown -R nginx:nginx /var/www/itops-frontend
```

**API not connecting:**
```bash
# Check environment
cat /home/ec2-user/itops-saas-frontend/.env.production

# Rebuild with correct API URL
cd /home/ec2-user/itops-saas-frontend
npm run build
sudo cp -r dist/* /var/www/itops-frontend/
```

## Security Hardening

### Firewall Configuration
```bash
# View current rules
sudo firewall-cmd --list-all

# Add specific rules
sudo firewall-cmd --permanent --add-port=8081/tcp
sudo firewall-cmd --reload
```

### SELinux Configuration
```bash
# Check SELinux status
getenforce

# If enforcing, allow Nginx to connect
sudo setsebool -P httpd_can_network_connect 1
```

### Fail2Ban Setup
```bash
# Install fail2ban
sudo dnf install -y fail2ban

# Enable and start
sudo systemctl enable fail2ban
sudo systemctl start fail2ban
```

## Performance Tuning

### Backend JVM Options
Edit systemd service:
```bash
sudo nano /etc/systemd/system/itops-backend.service
```

Update JAVA_OPTS:
```
Environment="JAVA_OPTS=-Xms1024m -Xmx2048m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"
```

Reload and restart:
```bash
sudo systemctl daemon-reload
sudo systemctl restart itops-backend
```

### PostgreSQL Tuning
```bash
sudo nano /var/lib/pgsql/data/postgresql.conf
```

For 4GB RAM EC2 instance:
```
shared_buffers = 1GB
effective_cache_size = 3GB
maintenance_work_mem = 256MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
work_mem = 5242kB
min_wal_size = 1GB
max_wal_size = 4GB
max_worker_processes = 4
max_parallel_workers_per_gather = 2
max_parallel_workers = 4
```

Restart PostgreSQL:
```bash
sudo systemctl restart postgresql
```

## Monitoring Setup

### Install CloudWatch Agent
```bash
wget https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm
sudo rpm -U ./amazon-cloudwatch-agent.rpm

# Configure CloudWatch
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-config-wizard
```

### System Monitoring Commands
```bash
# CPU and Memory
htop  # Install with: sudo dnf install -y htop

# Disk usage
df -h

# Network connections
sudo ss -tulpn

# Process list
ps aux | grep java
```

## Backup Strategy

### Automated Database Backup
```bash
# Create backup script
sudo nano /usr/local/bin/backup-db.sh
```

```bash
#!/bin/bash
BACKUP_DIR="/home/ec2-user/backups"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

pg_dump -U itops_user itops | gzip > $BACKUP_DIR/itops_backup_$DATE.sql.gz

# Keep only last 7 days
find $BACKUP_DIR -name "itops_backup_*.sql.gz" -mtime +7 -delete
```

Make executable:
```bash
sudo chmod +x /usr/local/bin/backup-db.sh
```

Add to crontab:
```bash
sudo crontab -e
# Add: 0 2 * * * /usr/local/bin/backup-db.sh
```

## Additional Resources

- Amazon Linux Documentation: https://docs.aws.amazon.com/linux/
- PostgreSQL Documentation: https://www.postgresql.org/docs/
- Nginx Documentation: https://nginx.org/en/docs/
- Let's Encrypt: https://letsencrypt.org/
- PM2 Documentation: https://pm2.keymetrics.io/

## Support Checklist

Before deploying to production:
- [ ] Changed default database password
- [ ] Changed JWT secret key
- [ ] Configured domain names
- [ ] Obtained SSL certificates
- [ ] Configured DNS records
- [ ] Set up automated backups
- [ ] Configured monitoring
- [ ] Tested application functionality
- [ ] Reviewed security settings
- [ ] Configured firewall rules
- [ ] Set up log rotation
- [ ] Documented credentials securely
