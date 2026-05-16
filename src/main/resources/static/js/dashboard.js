/* ═══════════════════════════════════════════════════════════════
   GSERP — Dashboard Page Logic (dashboard.js)
   ═══════════════════════════════════════════════════════════════ */

let dashboardDate = todayISO();

document.addEventListener('DOMContentLoaded', () => {
    const dateInput = document.getElementById('dashDate');
    dateInput.value = dashboardDate;
    dateInput.addEventListener('change', (e) => {
        dashboardDate = e.target.value;
        refreshDashboard();
    });

    refreshDashboard();
});

async function refreshDashboard() {
    await Promise.all([loadKPIs(), loadAppointments()]);
}

async function loadKPIs() {
    try {
        const res = await fetch(`/api/dashboard?date=${dashboardDate}`);
        const json = await res.json();
        const d = json.data;
        if (!d) return;

        animateValue('kpiTotal', d.totalAppointments);
        animateValue('kpiCompleted', d.completedAppointments);
        animateValue('kpiInProgress', d.inProgressAppointments);
        animateValue('kpiScheduled', d.scheduledAppointments);
        animateValue('kpiNoShow', d.noShows);
        document.getElementById('kpiRevenue').textContent = formatCurrency(d.totalRevenue);
        document.getElementById('kpiExpected').textContent = 'Beklenen: ' + formatCurrency(d.expectedRevenue);
    } catch (e) {
        console.error('Dashboard load error:', e);
    }
}

async function loadAppointments() {
    try {
        const res = await fetch(`/api/appointments?date=${dashboardDate}`);
        const json = await res.json();
        const appointments = json.data || [];

        const list = document.getElementById('appointmentList');

        if (!appointments.length) {
            list.innerHTML = '<div class="empty-state"><div class="empty-icon">📅</div><p>Bu tarihte randevu yok</p></div>';
            return;
        }

        list.innerHTML = appointments.map(a => {
            const flags = (a.flags || []).map(f => `<span class="flag-badge" title="${f.flagValue}">${f.icon}</span>`).join('');

            return `
                <div class="appointment-item animate-fade-in">
                    <div class="appointment-time">
                        ${formatTime(a.startTime)}<br>
                        <span style="font-size:0.72rem;color:var(--text-muted);">${formatTime(a.endTime)}</span>
                    </div>
                    <span class="staff-dot" style="background:${a.staffColor}"></span>
                    <div class="appointment-info">
                        <div class="customer-name">${a.customerName} ${flags}</div>
                        <div class="service-name">${a.serviceName} • ${a.staffName} • ${formatCurrency(a.finalPrice)}</div>
                    </div>
                    <div>${statusBadge(a.status)}</div>
                    <div class="appointment-actions">
                        ${a.status === 'SCHEDULED' ? `
                            <button class="btn btn-xs btn-warning" onclick="quickStatus(${a.id}, 'IN_PROGRESS')" title="Başlat">▶️</button>
                            <button class="btn btn-xs btn-danger" onclick="quickStatus(${a.id}, 'NO_SHOW')" title="Gelmedi">👻</button>
                        ` : ''}
                        ${a.status === 'IN_PROGRESS' ? `
                            <button class="btn btn-xs btn-success" onclick="quickStatus(${a.id}, 'COMPLETED')" title="Tamamla">✅</button>
                        ` : ''}
                    </div>
                </div>
            `;
        }).join('');
    } catch (e) {
        console.error('Appointments load error:', e);
    }
}

async function quickStatus(id, status) {
    const json = await api('PATCH', `/api/appointments/${id}/status`, { status });
    if (json.success) {
        showToast(statusLabels[status] || 'Güncellendi', 'success');
        refreshDashboard();
    } else {
        showToast(json.message, 'error');
    }
}

// ─── Animate KPI value ───
function animateValue(elementId, targetValue) {
    const el = document.getElementById(elementId);
    if (!el) return;
    const start = parseInt(el.textContent) || 0;
    const diff = targetValue - start;
    if (diff === 0) { el.textContent = targetValue; return; }
    const duration = 500;
    const steps = 20;
    const increment = diff / steps;
    let current = start;
    let step = 0;

    const timer = setInterval(() => {
        step++;
        current += increment;
        el.textContent = Math.round(current);
        if (step >= steps) {
            clearInterval(timer);
            el.textContent = targetValue;
        }
    }, duration / steps);
}

// Auto-refresh every 60 seconds
setInterval(() => {
    if (document.visibilityState === 'visible') {
        refreshDashboard();
    }
}, 60000);
