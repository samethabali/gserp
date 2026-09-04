# GSCRM Dev Başlatma Scripti
# Kullanım: .\start-dev.ps1

Write-Host "GSCRM Dev Ortamı Başlatılıyor..." -ForegroundColor Cyan

# DB container durumunu kontrol et
$dbRunning = docker ps --filter "name=gscrm-db-dev" --format "{{.Names}}" 2>$null

if (-not $dbRunning) {
    Write-Host "Veritabanı başlatılıyor (port 5800)..." -ForegroundColor Yellow
    docker compose -f docker-compose.dev.yml up -d
    if ($LASTEXITCODE -ne 0) {
        Write-Host "HATA: docker-compose başlatılamadı." -ForegroundColor Red
        exit 1
    }
    Write-Host "Veritabanı hazır olana kadar bekleniyor..." -ForegroundColor Yellow
    Start-Sleep -Seconds 5
} else {
    Write-Host "Veritabanı zaten çalışıyor." -ForegroundColor Green
}

# DB bağlantısını doğrula
$dbReady = docker exec gscrm-db-dev pg_isready -U gscrm -d gscrm_dev 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Veritabanı yanıt vermiyor, 5 saniye daha bekleniyor..." -ForegroundColor Yellow
    Start-Sleep -Seconds 5
}

# Spring Boot başlat
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5800/gscrm_dev"
Write-Host ""
Write-Host "Uygulama başlatılıyor..." -ForegroundColor Green
Write-Host "URL: http://localhost:8989" -ForegroundColor Cyan
Write-Host "Giriş: admin / admin123" -ForegroundColor Cyan
Write-Host "Booking: http://localhost:8989/booking" -ForegroundColor Cyan
Write-Host ""
mvn spring-boot:run
