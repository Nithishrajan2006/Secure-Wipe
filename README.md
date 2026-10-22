# 🛡️ SecureWipe — Secure Data Wiping for Trustworthy IT Asset Recycling

<div align="center">

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)

**Enterprise-grade secure data destruction platform with auditable compliance certificates and real-time progress monitoring.**

[🚀 Quick Start (Docker)](#-quick-start-with-docker-recommended) • [💻 Local Dev Setup](#-local-development-setup) • [📡 API Docs](#-api-endpoints) • [🎬 Demo Flow](#-demo-flow) • [🏗️ Architecture](#-architecture)

</div>

---

## 📋 Problem Statement

**The Problem:** When IT assets (computers, servers, storage devices) are decommissioned or recycled, sensitive data on them must be completely and verifiably destroyed. Inadequate data wiping leads to data breaches, non-compliance with security policies, and potential misuse of confidential information.

**The Solution:** SecureWipe provides a robust, auditable, DoD-standards-compliant data destruction platform with real-time monitoring, dynamic PDF compliance certificates with QR verification, and complete audit trails.

---

## ✨ Features

### 🔍 Device Detection & Information
- ✔ Auto-detect all connected storage devices via OSHI hardware library
- ✔ Supports: Internal HDD, SSD, USB Flash Drives, External HDDs, NVMe Drives, Partitions
- ✔ Full device info: Model, Serial Number, Size, File System, Mount Point, Firmware Version
- ✔ One-click device refresh

### 🗑️ Secure Wipe Algorithms
| Method | Passes | Security Level | Best For |
|--------|--------|---------------|----------|
| Single Pass Zeros | 1 | ⭐ | Non-sensitive disposal |
| Random Data Overwrite | 1 | ⭐⭐ | General use |
| **DoD 5220.22-M (3-Pass)** | 3 | ⭐⭐⭐ | **Standard compliance ✓** |
| DoD 5220.22-M ECE (7-Pass) | 7 | ⭐⭐⭐⭐⭐ | Classified / high-security data |
| ATA Secure Erase | 1 | ⭐⭐⭐⭐⭐ | SSDs & NVMe |

- ✔ Real-time WebSocket progress: %, speed (MB/s), ETA, current pass
- ✔ 3-step safety confirmation with device name verification
- ✔ Cancel operation at any time

### 🔐 Security & Access Control
- ✔ JWT Authentication (24h access + 7-day refresh tokens)
- ✔ Role-Based Access Control: Admin / User
- ✔ BCrypt password hashing (cost factor 12)
- ✔ Complete audit trail with IP address logging
- ✔ SQL injection prevention via JPA parameterized queries

### 📜 Dynamic PDF Certificate Generation
- ✔ Every completed wipe generates a unique, print-ready PDF certificate
- ✔ Certificate includes: Unique Certificate ID, Device Model, Serial Number, Capacity, Wipe Algorithm, Administrator Name, Date & Time, QR Code, and Verification URL
- ✔ QR Code links to a public verification page to confirm certificate authenticity

### 🔎 QR Code Certificate Verification
- ✔ Each certificate carries an embedded QR code
- ✔ Scanning the QR opens the public certificate verification page
- ✔ Verifiers can confirm the certificate ID, device details, wipe method, and operator — without logging in

### 📊 Dashboard & Analytics
- ✔ Live stats: Total Wipes, Data Destroyed, Active Operations, Success Rate
- ✔ 7-day wipe trend chart (Recharts bar chart)
- ✔ Wipe breakdown by method
- ✔ Recent activity feed

### 🗂️ Audit Logs & Export
- ✔ Complete audit trail with timestamps and IP addresses
- ✔ Export audit logs as **CSV**
- ✔ Export audit logs as **PDF**

### 🛠️ Tech & Infrastructure
- ✔ MySQL Database
- ✔ Spring Boot Backend
- ✔ React Frontend
- ✔ Enterprise UI (dark/light theme, fully responsive)

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     BROWSER (Port 3000)                           │
│         React 18 + TypeScript + Vite + Tailwind CSS              │
│                                                                   │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌─────────┐ ┌─────────┐  │
│  │Dashboard│ │Devices  │ │  Wipe    │ │ Reports │ │  Audit  │  │
│  └─────────┘ └─────────┘ └──────────┘ └─────────┘ └─────────┘  │
│       │            │            │                                 │
│  Zustand (Auth/Theme State) + TanStack React Query (API State)   │
└─────────────────────┬────────────────────────────────────────────┘
                      │ REST API (HTTP/JSON) + WebSocket (STOMP)
┌─────────────────────▼────────────────────────────────────────────┐
│              SPRING BOOT BACKEND (Port 8080)                      │
│                                                                   │
│  JWT Auth Filter ──► Spring Security 6 ──► Controllers           │
│  ┌──────────┐ ┌──────────┐ ┌────────────┐ ┌────────────────┐   │
│  │AuthSvc   │ │DeviceSvc │ │WipeService │ │WipeEngineSvc   │   │
│  │          │ │(OSHI)    │ │+Executor   │ │(DoD/Zeros/Rand)│   │
│  └──────────┘ └──────────┘ └────────────┘ └────────────────┘   │
│  ┌──────────┐ ┌──────────┐ ┌────────────┐                       │
│  │ReportSvc │ │AuditSvc  │ │DashboardSvc│                       │
│  │(OpenPDF) │ │(Async)   │ │            │                       │
│  └──────────┘ └──────────┘ └────────────┘                       │
└─────────────────────┬────────────────────────────────────────────┘
                      │ JPA + Hibernate + Flyway Migrations
┌─────────────────────▼────────────────────────────────────────────┐
│                    MySQL 8.0 (Port 3306)                          │
│   users | wipe_jobs | audit_logs | device_snapshots              │
└──────────────────────────────────────────────────────────────────┘
```

### Real-time Wipe Progress Flow
```
WipeExecutorService (Thread Pool Executor)
  └─► WipeEngineService.executeWipe()
        └─► progress callback every 500ms
              └─► SimpMessagingTemplate
                    └─► /topic/wipe-progress/{jobId}   (WebSocket)
                          └─► React useWipeProgress hook
                                └─► Animated Progress Bar UI
```

---

## 🐳 Quick Start with Docker (Recommended)

**Prerequisites:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running.

### Step 1 — Clone the Repository
```bash
git clone https://github.com/your-org/securewipe.git
cd securewipe
```

### Step 2 — Start Everything with One Command
```bash
docker-compose up --build
```

> First build takes ~3-5 minutes as it downloads dependencies and compiles the Spring Boot app. Subsequent starts are instant.

### Step 3 — Open the Application
| Service | URL |
|---------|-----|
| 🌐 **Frontend App** | **http://localhost:3000** |
| 🔧 Backend API | http://localhost:8080 |
| 📚 Swagger UI | http://localhost:8080/swagger-ui.html |
| 🗄️ MySQL | localhost:3306 |

### Step 4 — First Run Admin Bootstrap
On the first backend startup, SecureWipe no longer ships with a baked-in default admin password.

You must configure the first admin account either by setting environment variables or by accepting the generated bootstrap password that is printed to the backend logs.

#### Option A — Environment variables
```bash
export ADMIN_USERNAME=securewipe-admin
export ADMIN_PASSWORD='ChangeMe!StrongPassword123'
docker-compose up --build
```

#### Option B — Generated one-time bootstrap secret
If `ADMIN_USERNAME` / `ADMIN_PASSWORD` are not provided, the backend will generate a random username and password at startup and print them to the logs once.

> The password is still BCrypt-hashed with strength 12 before being stored in the database.

After startup, log in with the bootstrap credentials shown in the backend logs.

### Useful Docker Commands
```bash
# Start in background (detached)
docker-compose up -d --build

# View live logs
docker-compose logs -f

# View backend logs only
docker-compose logs -f backend

# Stop all services
docker-compose down

# Stop and remove all data (fresh start)
docker-compose down -v

# Rebuild after code changes
docker-compose up --build --force-recreate
```

---

## 💻 Local Development Setup

### Prerequisites
| Tool | Version | Download |
|------|---------|----------|
| Java JDK | **17** (not 21, not 25) | [Eclipse Temurin 17](https://adoptium.net/temurin/releases/?version=17) |
| Maven | 3.8+ | Included via `mvnw` wrapper |
| Node.js | 20+ | [nodejs.org](https://nodejs.org) |
| MySQL | 8.0+ | [mysql.com](https://dev.mysql.com/downloads/) |

> ⚠️ **Important:** Java 17 is required. Java 21+ may cause Maven TLS issues with some dependency servers.

### Step 1 — MySQL Setup
```sql
CREATE DATABASE securewipe_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'securewipe_user'@'localhost' IDENTIFIED BY 'SecureWipe@2024';
GRANT ALL PRIVILEGES ON securewipe_db.* TO 'securewipe_user'@'localhost';
FLUSH PRIVILEGES;
```

### Step 2 — Start Backend
```bash
cd backend

# Windows
mvnw.cmd spring-boot:run

# Linux / Mac
chmod +x mvnw
./mvnw spring-boot:run
```

Wait for: `Started SecureWipeApplication in X seconds`

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### Step 3 — Start Frontend
```bash
cd frontend
npm install
npm run dev
```

- App: http://localhost:5173

### Step 4 — Login
Use the bootstrap credentials emitted by the backend startup log on first run. If you provide `ADMIN_USERNAME` and `ADMIN_PASSWORD`, those values are used directly.

---

## 🗄️ Database Schema

| Table | Purpose |
|-------|---------|
| `users` | User accounts, BCrypt passwords, roles (ADMIN/USER) |
| `wipe_jobs` | All wipe operations with status, progress, certificate IDs |
| `audit_logs` | Every system action with user, IP address, timestamp |
| `device_snapshots` | Hardware snapshot captured at wipe time |

Flyway automatically creates all tables on first startup via migrations `V1–V6`.

**Initial admin bootstrap behavior:**
- No hardcoded default admin password is shipped in the repository.
- The first admin account is created by the backend bootstrap service from `ADMIN_USERNAME` / `ADMIN_PASSWORD` if provided.
- Otherwise, SecureWipe generates a one-time random bootstrap password and logs it for the operator to use.

---

## 📡 API Endpoints

Full interactive documentation: **http://localhost:8080/swagger-ui.html**

### Authentication
```
POST  /api/auth/register     Register new user
POST  /api/auth/login        Login → returns JWT tokens
POST  /api/auth/refresh      Refresh access token
GET   /api/auth/me           Get current user info
```

### Devices
```
GET   /api/devices           List all detected storage devices
GET   /api/devices/refresh   Re-scan for devices
```

### Wipe Operations
```
POST  /api/wipe/start               Start a wipe (requires confirmation)
GET   /api/wipe/progress/{jobId}    Poll current progress
POST  /api/wipe/cancel/{jobId}      Cancel a running wipe
GET   /api/wipe/history             Paginated wipe history
GET   /api/wipe/{jobId}             Single job details
```

### Reports
```
GET   /api/reports/certificate/{jobId}   Download PDF wipe certificate
GET   /api/reports/audit/csv             Export audit log as CSV  (Admin)
GET   /api/reports/audit/pdf             Export audit log as PDF  (Admin)
```

### Dashboard & Admin
```
GET   /api/dashboard/stats           Statistics & chart data
GET   /api/audit/logs                Paginated audit logs     (Admin)
GET   /api/users                     List all users           (Admin)
PUT   /api/users/{id}/toggle-status  Enable / disable user    (Admin)
```

### WebSocket (Real-time Progress)
```
Endpoint : /ws  (with SockJS fallback)
Subscribe: /topic/wipe-progress/{jobId}
```

---

## 🎬 Demo Flow

> **Scenario:** Decommissioning a computer before IT asset disposal.

### 1️⃣ Login & Dashboard
- Open http://localhost:3000
- Log in using the bootstrap admin credentials printed by the backend startup logs on first run
- Dashboard shows: total wipes, data destroyed (formatted), active ops, success rate, 7-day bar chart

### 2️⃣ Device Detection
- Click **Devices** in sidebar
- Platform auto-detects all storage devices via OSHI library
- Each device card shows: model, type badge, size, filesystem, mount point, partitions
- Click **Refresh Devices** to re-scan

### 3️⃣ Start Secure Wipe (3-Step Safety)
- Click **Secure Wipe** on a device
- Select **DoD 5220.22-M (3-Pass)** method
- Click **Proceed to Confirmation**
  - **Step 1:** Warning about irreversible destruction → click "I Understand"
  - **Step 2:** Type exact device name to confirm (e.g. `/dev/sdb`)
  - **Step 3:** Check the acknowledgment checkbox
- Click **Start Secure Wipe**

### 4️⃣ Real-time Progress Monitoring
- Progress bar updates every 500ms via WebSocket
- Shows: **67%** | **148.3 MB/s** | **ETA: 2m 14s** | **Pass 2/3: Writing 0xFF**
- Cancel button available at any time

### Step 5 — PDF Wipe Certificate & QR Verification
- After completion → click **Download Wipe Certificate**
- PDF opens with:
  - SecureWipe branding
  - **Unique Certificate ID**
  - **Device Model, Serial Number, Capacity**
  - **Wipe Algorithm & number of passes**
  - **Administrator Name**
  - **Date & Time** of the wipe
  - **QR Code** — scan with any phone to open the public verification page
  - **Verification URL** — accessible without login

### 6️⃣ Certificate Verification (Public Page)
- Scan the QR code on the certificate **or** visit the verification URL directly
- The public verification page displays the full certificate details
- Any auditor, regulator, or stakeholder can confirm the certificate's authenticity without an account

### 7️⃣ Compliance Audit Trail
- Navigate to **Audit Logs** (Admin only)
- Every action logged: login, wipe start, wipe complete, certificate download
- Click **Export CSV** → downloadable compliance record
- Filter by user, action, or date range

---

## 📁 Project Structure

```
securewipe/                          ← Monorepo root
├── backend/                         ← Spring Boot 3.2 (Java 17)
│   ├── src/main/java/com/securewipe/backend/
│   │   ├── config/                  ← Security, WebSocket, Swagger, App config
│   │   ├── controller/              ← 7 REST controllers
│   │   ├── dto/request/             ← Validated request DTOs
│   │   ├── dto/response/            ← Response DTOs
│   │   ├── entity/                  ← JPA entities (User, WipeJob, AuditLog, ...)
│   │   ├── enums/                   ← WipeMethod, WipeStatus, DeviceType, UserRole
│   │   ├── exception/               ← Custom exceptions + GlobalExceptionHandler
│   │   ├── repository/              ← Spring Data JPA repositories
│   │   ├── security/                ← JWT provider, filter, UserDetailsService
│   │   └── service/
│   │       ├── WipeEngineService    ← Core DoD/zeros/random write algorithms
│   │       ├── WipeExecutorService  ← Async thread pool executor
│   │       ├── WipeService          ← Job management + cancellation
│   │       ├── DeviceService        ← OSHI hardware detection + simulation
│   │       ├── ReportService        ← OpenPDF certificates + CSV/PDF export
│   │       ├── AuthService          ← Register, login, JWT generation
│   │       ├── AuditService         ← Async audit logging
│   │       └── DashboardService     ← Stats aggregation
│   ├── src/main/resources/
│   │   ├── application.yml          ← Main configuration
│   │   ├── application-docker.yml   ← Docker profile overrides
│   │   └── db/migration/            ← Flyway SQL (V1–V5)
│   ├── Dockerfile                   ← Multi-stage Java build
│   └── pom.xml                      ← Maven dependencies
│
├── frontend/                        ← React 18 + TypeScript + Vite
│   ├── src/
│   │   ├── api/          ← 6 Axios API modules
│   │   ├── components/   ← Layout, Header, Sidebar, ProtectedRoute
│   │   ├── hooks/        ← useWipeProgress (WebSocket)
│   │   ├── pages/        ← 10 complete pages
│   │   ├── store/        ← Zustand (auth + theme)
│   │   └── types/        ← TypeScript interfaces + wipe method constants
│   ├── Dockerfile         ← Multi-stage Node + Nginx build
│   └── nginx.conf         ← SPA routing + API/WebSocket proxy
│
├── docker-compose.yml     ← MySQL + Backend + Frontend orchestration
├── start-dev.bat          ← Windows one-click dev start
├── start-dev.ps1          ← PowerShell one-click dev start
├── .gitignore
└── README.md
```

---

## 🔒 Security Highlights

| Feature | Implementation |
|---------|---------------|
| Authentication | JWT (JJWT 0.11.5) — access + refresh tokens |
| Password Storage | BCrypt strength 12 |
| Session | Stateless (no server-side sessions) |
| Authorization | Spring Security 6 `@PreAuthorize` + method security |
| CORS | Whitelist-based (configurable per environment) |
| SQL Injection | JPA parameterized queries (Hibernate) |
| Input Validation | Bean Validation (`@Valid`, `@NotBlank`, `@Pattern`) |
| Audit Trail | Every action logged with IP address |
| Wipe Safety | 3-step confirmation — warning → type device name → checkbox |

---

## 🛠️ Full Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend Framework | Spring Boot | 3.2.0 |
| Language | Java | 17 |
| Security | Spring Security + JJWT | 6.2 / 0.11.5 |
| Database | MySQL | 8.0 |
| ORM | Spring Data JPA + Hibernate | 3.2 / 6.3 |
| DB Migrations | Flyway | 9.x |
| PDF Generation | OpenPDF | 1.3.30 |
| API Docs | SpringDoc OpenAPI | 2.3.0 |
| Hardware Detection | OSHI Core | 6.4.10 |
| CSV Export | OpenCSV | 5.8 |
| Real-time | Spring WebSocket + STOMP | 6.1 |
| Frontend | React + TypeScript | 18 / 5.2 |
| Build Tool | Vite | 5.0 |
| Styling | Tailwind CSS | 3.3 |
| State Management | Zustand | 4.4 |
| Server State | TanStack React Query | 5.x |
| WebSocket Client | @stomp/stompjs + SockJS | 7.0 / 1.6 |
| Charts | Recharts | 2.9 |
| HTTP Client | Axios | 1.6 |
| Containerization | Docker + Docker Compose | Latest |

---

## 👨‍💻 Project Author

**Moulitharan I R**
B.Tech Information Technology — VSB Engineering College

| Responsibility | Details |
|---------------|---------|
| Project Designer | End-to-end system architecture and design |
| Full Stack Developer | Backend, frontend, database, and deployment |
| Backend Development | Spring Boot REST API, security, WebSocket, PDF engine |
| Frontend Development | React + TypeScript UI, state management, real-time hooks |
| Database Design | MySQL schema, Flyway migrations, JPA entities |
| Security Implementation | JWT auth, RBAC, BCrypt, audit logging |
| Certificate System | Dynamic PDF generation with unique certificate IDs |
| QR Verification System | QR code embedding and public verification page |

---

## 🏆 Why SecureWipe Stands Out

1. **Real DoD Wipe Implementation** — Actual multi-pass byte writing using Java NIO FileChannel, not simulated
2. **Live Progress** — WebSocket pushes updates every 500ms; watch real-time speed, ETA, and current pass
3. **Dynamic PDF Certificates** — Auto-generated on every wipe with unique Certificate ID, device details, and QR code
4. **QR Verification** — Scan to open the public verification page and confirm certificate authenticity instantly
5. **Enterprise Security** — JWT, RBAC, audit logs, BCrypt — production-grade from day one
6. **Docker Ready** — Single command to run everything, zero manual setup
7. **Complete Audit Trail** — Every action logged, filterable, exportable as CSV/PDF

---

## 🚀 Future Enhancements

| Enhancement | Description |
|-------------|-------------|
| ☁️ Cloud Certificate Verification | Host certificate records on a cloud endpoint for global accessibility |
| 🏢 Multi-Organization Support | Separate tenants, branding, and audit trails per organization |
| ⛓️ Certificate Blockchain Verification | Immutable on-chain certificate records for tamper-proof auditing |
| 🌐 Remote Device Management | Trigger and monitor wipes on networked devices remotely |
| 🔗 Secure Network Wiping | Wipe storage across LAN/WAN infrastructure without physical access |
| 📈 Enhanced Analytics Dashboard | Advanced reporting with date-range filters, export to Excel, and trend forecasting |
| 📧 Email Certificate Delivery | Automatically email the PDF certificate to administrators on wipe completion |

---

## 📄 License

This project is licensed under the MIT License.

---

<div align="center">

---

© 2026 Moulitharan I R

**SecureWipe** — Enterprise Secure Data Destruction Platform

*This repository showcases my software engineering, backend development, frontend development, database integration, security implementation, and enterprise application development skills.*

---

</div>
