# ====================================================
# WHX Bill System - CI/CD Pipeline (Safe Delete Version)
# ====================================================

$BASE_DIR    = "C:\Users\Administrator\Desktop\whxbill"
$ADMIN_DIR   = "$BASE_DIR\frontend-admin"
$USER_DIR    = "$BASE_DIR\frontend-user"
$BACKEND_DIR = "$BASE_DIR\backend"
$DEPLOY_DIR  = "$BASE_DIR\deploy"
$SERVER_IP   = "39.106.168.139"

# --- Function: Error Catcher ---
function Check-Error($TaskName) {
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`n[X] Fatal Error: $TaskName failed! Pipeline aborted." -ForegroundColor Red
        Pause
        exit 1
    }
}

# --- Function: Safe Delete (The Core Improvement) ---
function Safe-Delete($TargetPath) {
    if (Test-Path $TargetPath) {
        Write-Host "  [-] Deleting existing: $TargetPath" -ForegroundColor DarkGray
        Remove-Item -Path $TargetPath -Recurse -Force -ErrorAction Stop
    }
}

# ==================== Step 1: Deep Clean & Prep ====================
Write-Host "[+] 1. Checking and cleaning deploy directory..." -ForegroundColor Cyan

# 1.1 Check if deploy folder exists, if not, create it
if (-Not (Test-Path $DEPLOY_DIR)) {
    Write-Host "  [!] Deploy folder not found. Creating $DEPLOY_DIR..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $DEPLOY_DIR | Out-Null
}

# 1.2 Deep clean target files BEFORE creating new ones
Safe-Delete "$DEPLOY_DIR\frontend-admin-dist"
Safe-Delete "$DEPLOY_DIR\frontend-user-dist"
Safe-Delete "$DEPLOY_DIR\backend-1.0.0.jar"
Write-Host "[OK] Clean completed! Environment is pure." -ForegroundColor Green


# ==================== Step 2: Fresh Build ====================
Write-Host "`n[+] 2. Building Frontend Admin..." -ForegroundColor Cyan
Set-Location $ADMIN_DIR
npm run build
Check-Error "Build Frontend Admin"
Copy-Item -Path "$ADMIN_DIR\dist" -Destination "$DEPLOY_DIR\frontend-admin-dist" -Recurse
Write-Host "[OK] Admin built successfully!" -ForegroundColor Green

Write-Host "`n[+] 3. Building Frontend User..." -ForegroundColor Cyan
Set-Location $USER_DIR
npm run build
Check-Error "Build Frontend User"
Copy-Item -Path "$USER_DIR\dist" -Destination "$DEPLOY_DIR\frontend-user-dist" -Recurse
Write-Host "[OK] User built successfully!" -ForegroundColor Green

Write-Host "`n[+] 4. Building Backend SpringBoot..." -ForegroundColor Cyan
Set-Location $BACKEND_DIR
mvn clean package
Check-Error "Build Backend Maven"
Copy-Item -Path "$BACKEND_DIR\target\backend-1.0.0.jar" -Destination "$DEPLOY_DIR\"
Write-Host "[OK] Backend built successfully!" -ForegroundColor Green


# ==================== Step 3: Server Sync ====================
Write-Host "`n[+] 5. Connecting to server and cleaning old environment..." -ForegroundColor Cyan
ssh root@$SERVER_IP "mkdir -p /opt/whx-bill-runtime/backend /opt/whx-bill-runtime/frontend-user /opt/whx-bill-runtime/frontend-admin /opt/whx-bill-runtime/docs/sql /opt/whx-bill-runtime/nginx && rm -rf /opt/whx-bill-runtime/frontend-user/dist /opt/whx-bill-runtime/frontend-admin/dist /opt/whx-bill-runtime/backend/backend-1.0.0.jar /opt/whx-bill-runtime/deploy_server.sh"

Write-Host "`n[+] 6. Uploading new files to server..." -ForegroundColor Cyan
Set-Location $DEPLOY_DIR

Write-Host "  -> Uploading Backend Jar..." -ForegroundColor DarkCyan
scp ".\backend-1.0.0.jar" root@${SERVER_IP}:/opt/whx-bill-runtime/backend/

Write-Host "  -> Uploading Frontend User..." -ForegroundColor DarkCyan
scp -r ".\frontend-user-dist" root@${SERVER_IP}:/opt/whx-bill-runtime/frontend-user/dist

Write-Host "  -> Uploading Frontend Admin..." -ForegroundColor DarkCyan
scp -r ".\frontend-admin-dist" root@${SERVER_IP}:/opt/whx-bill-runtime/frontend-admin/dist

Write-Host "  -> Uploading SQL Scripts..." -ForegroundColor DarkCyan
scp ".\schema.sql" root@${SERVER_IP}:/opt/whx-bill-runtime/docs/sql/
scp ".\seed.sql" root@${SERVER_IP}:/opt/whx-bill-runtime/docs/sql/

Write-Host "  -> Uploading Config and Scripts..." -ForegroundColor DarkCyan
scp ".\docker-compose.yml" root@${SERVER_IP}:/opt/whx-bill-runtime/
scp ".\.env" root@${SERVER_IP}:/opt/whx-bill-runtime/
scp ".\runtime.conf" root@${SERVER_IP}:/opt/whx-bill-runtime/nginx/
scp ".\deploy_server.sh" root@${SERVER_IP}:/opt/whx-bill-runtime/

Write-Host "`n[+] 7. Granting execution permissions on server..." -ForegroundColor Cyan
ssh root@$SERVER_IP "chmod +x /opt/whx-bill-runtime/deploy_server.sh"


# ==================== Finish ====================
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "SUCCESS: Pipeline executed perfectly with Safe Delete!" -ForegroundColor Green
Write-Host "Next Step: Login to server, cd to /opt/whx-bill-runtime/ and run ./deploy_server.sh" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Green

Pause