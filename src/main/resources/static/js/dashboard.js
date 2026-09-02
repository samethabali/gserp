/* ═══════════════════════════════════════════════════════════════
   GSCRM — Dashboard Page Logic (dashboard.js)
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
    loadTrendChart();
    checkLowStock();
});

async function refreshDashboard() {
    await Promise.all([loadKPIs(), loadPaymentSummary(), loadSessionProgress()]);
}

async function loadKPIs() {
    try {
        const res = await fetch(`/api/dashboard?date=${dashboardDate}`);
        const json = await res.json();
        const d = json.data;
        if (!d) return;

        // KPI kartları
        const el = id => document.getElementById(id);
        if (el('valTotal'))     el('valTotal').textContent     = d.totalAppointments;
        if (el('valCompleted')) el('valCompleted').textContent =
            `${d.completedAppointments} / ${d.scheduledAppointments + d.inProgressAppointments + d.completedAppointments}`;
        if (el('valRevenue'))   el('valRevenue').textContent   = formatCurrency(d.totalRevenue);
        if (el('valNoShow'))    el('valNoShow').textContent    = d.noShows;
        if (el('dashDateHint')) el('dashDateHint').textContent = `${formatDate(dashboardDate)} tarihli veriler`;

        // Personel performans kartları
        const container = el('staffPerfContainer');
        if (!container) return;

        if (d.staffPerformance && d.staffPerformance.length > 0) {
            container.innerHTML = d.staffPerformance.map(s => {
                const rate = s.totalAppointments ? Math.round((s.completed / s.totalAppointments) * 100) : 0;
                const detailRows = (s.appointments || []).map(a => `
                    <tr>
                        <td style="padding:6px 8px;font-size:0.8rem;">${formatTime(a.startTime)}</td>
                        <td style="padding:6px 8px;font-size:0.8rem;">${a.customerName || '-'}</td>
                        <td style="padding:6px 8px;font-size:0.8rem;">${a.serviceName || '-'}</td>
                        <td style="padding:6px 8px;font-size:0.8rem;">${formatCurrency(a.finalPrice)}</td>
                        <td style="padding:6px 8px;">${statusBadge(a.status)}</td>
                    </tr>
                `).join('');

                return `
                <div class="perf-card" style="--staff-color:${s.staffColor}">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                        <div style="font-weight:600;font-size:1rem;">${escapeHtml(s.staffName)}</div>
                        <button class="btn btn-ghost btn-xs" onclick="toggleStaffDetail(this)" style="font-size:0.8rem;">▼ Detay</button>
                    </div>
                    <div style="display:flex;justify-content:space-between;margin-bottom:4px;font-size:0.85rem;">
                        <span style="color:var(--text-secondary);">İşlem:</span>
                        <strong>${s.completed}/${s.totalAppointments}</strong>
                    </div>
                    <div style="display:flex;justify-content:space-between;margin-bottom:4px;font-size:0.85rem;">
                        <span style="color:var(--text-secondary);">Ciro:</span>
                        <strong>${formatCurrency(s.revenue)}</strong>
                    </div>
                    <div style="display:flex;justify-content:space-between;font-size:0.85rem;">
                        <span style="color:var(--text-secondary);">No-Show:</span>
                        <strong style="color:var(--color-danger)">${s.noShows}</strong>
                    </div>
                    <div style="margin-top:10px;height:5px;background:rgba(255,255,255,0.08);border-radius:3px;overflow:hidden;">
                        <div style="height:100%;width:${rate}%;background:${s.staffColor};transition:width 0.5s;"></div>
                    </div>
                    <div class="staff-detail-table" style="display:none;margin-top:12px;overflow-x:auto;">
                        ${detailRows ? `
                        <table style="width:100%;border-collapse:collapse;">
                            <thead>
                                <tr style="border-bottom:1px solid var(--border-glass);">
                                    <th style="padding:4px 8px;font-size:0.7rem;color:var(--text-muted);text-align:left;">SAAT</th>
                                    <th style="padding:4px 8px;font-size:0.7rem;color:var(--text-muted);text-align:left;">MÜŞTERİ</th>
                                    <th style="padding:4px 8px;font-size:0.7rem;color:var(--text-muted);text-align:left;">HİZMET</th>
                                    <th style="padding:4px 8px;font-size:0.7rem;color:var(--text-muted);text-align:left;">FİYAT</th>
                                    <th style="padding:4px 8px;font-size:0.7rem;color:var(--text-muted);text-align:left;">DURUM</th>
                                </tr>
                            </thead>
                            <tbody>${detailRows}</tbody>
                        </table>` : '<p style="font-size:0.8rem;color:var(--text-muted);">Randevu yok</p>'}
                    </div>
                </div>`;
            }).join('');
        } else {
            container.innerHTML = '<div style="color:var(--text-muted);font-size:0.9rem;">Kayıtlı veri bulunamadı.</div>';
        }

        // Personel bar grafiğini güncelle
        if (d.staffPerformance) renderStaffChart(d.staffPerformance);

    } catch (e) {
        console.error('Dashboard load error:', e);
    }
}

function toggleStaffDetail(btn) {
    const table = btn.closest('.perf-card').querySelector('.staff-detail-table');
    const open = table.style.display === 'none';
    table.style.display = open ? 'block' : 'none';
    btn.textContent = open ? '▲ Gizle' : '▼ Detay';
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
            const flags = (a.flags || []).map(f => `<span class="flag-badge" title="${escapeHtml(f.flagValue)}">${f.icon}</span>`).join('');

            return `
                <div class="appointment-item animate-fade-in">
                    <div class="appointment-time">
                        ${formatTime(a.startTime)}<br>
                        <span style="font-size:0.72rem;color:var(--text-muted);">${formatTime(a.endTime)}</span>
                    </div>
                    <span class="staff-dot" style="background:${a.staffColor}"></span>
                    <div class="appointment-info">
                        <div class="customer-name">${escapeHtml(a.customerName)} ${flags}</div>
                        <div class="service-name">${escapeHtml(a.serviceName)} • ${escapeHtml(a.staffName)} • ${formatCurrency(a.finalPrice)}</div>
                    </div>
                    <div>${statusBadge(a.status)}</div>
                    <div class="appointment-actions">
                        ${a.status === 'SCHEDULED' ? `
                            <button class="btn btn-xs btn-warning" onclick="quickStatus(${a.id}, 'IN_PROGRESS')" title="Başlat">▶️</button>
                            <button class="btn btn-xs btn-danger" onclick="quickStatus(${a.id}, 'NO_SHOW')" title="Gelmedi">👻</button>
                        ` : ''}
                        ${a.status === 'IN_PROGRESS' ? `
                            <button class="btn btn-xs btn-success" onclick="openPaymentModal(${a.id}, ${a.finalPrice})" title="Tamamla ve Tahsilat Al">✅</button>
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

// ─── Tahsilat Modal ───
function openPaymentModal(appointmentId, finalPrice) {
    document.getElementById('payAppointmentId').value = appointmentId;
    document.getElementById('payAmount').value = finalPrice || '';
    document.getElementById('payStatus').value = 'PAID';
    document.getElementById('payDeferredNote').value = '';
    document.getElementById('deferredNoteGroup').style.display = 'none';
    selectPayMethod('CASH');
    openModal('paymentModal');
}

function selectPayMethod(method) {
    document.getElementById('payMethod').value = method;
    document.getElementById('btnCash').classList.toggle('btn-primary', method === 'CASH');
    document.getElementById('btnCash').classList.toggle('btn-secondary', method !== 'CASH');
    document.getElementById('btnCard').classList.toggle('btn-primary', method === 'CARD');
    document.getElementById('btnCard').classList.toggle('btn-secondary', method !== 'CARD');
}

function toggleDeferredNote() {
    const status = document.getElementById('payStatus').value;
    document.getElementById('deferredNoteGroup').style.display =
        (status === 'DEFERRED' || status === 'PARTIAL') ? 'block' : 'none';
}

async function submitPayment() {
    const appointmentId = parseInt(document.getElementById('payAppointmentId').value);
    const amount = parseFloat(document.getElementById('payAmount').value);
    const method = document.getElementById('payMethod').value;
    const status = document.getElementById('payStatus').value;
    const deferredNote = document.getElementById('payDeferredNote').value;

    if (!amount || amount <= 0) {
        showToast('Geçerli bir tutar girin', 'warning');
        return;
    }

    const payRes = await api('POST', '/api/payments', { appointmentId, amount, method, status, deferredNote });
    if (!payRes.success) {
        showToast(payRes.message || 'Tahsilat kaydedilemedi', 'error');
        return;
    }

    const statusRes = await api('PATCH', `/api/appointments/${appointmentId}/status`, { status: 'COMPLETED' });
    if (statusRes.success) {
        closeModal('paymentModal');
        showToast('Tahsilat alındı ve randevu tamamlandı', 'success');
        refreshDashboard();
    } else {
        showToast(statusRes.message || 'Durum güncellenemedi', 'error');
    }
}

// ─── Günlük Tahsilat Özeti ───
async function loadPaymentSummary() {
    try {
        const res = await api('GET', `/api/payments/summary?date=${dashboardDate}`);
        if (!res.data) return;
        const s = res.data;
        document.getElementById('payCash').textContent     = formatCurrency(s.cashTotal);
        document.getElementById('payCard').textContent     = formatCurrency(s.cardTotal);
        document.getElementById('payDeferred').textContent = formatCurrency(s.deferredTotal);
        document.getElementById('payTotal').textContent    = formatCurrency(s.grandTotal);
    } catch (e) {
        console.error('Payment summary error:', e);
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

// ─── Seans İlerlemesi ───
async function loadSessionProgress() {
    try {
        const res = await api('GET', `/api/dashboard/sessions?date=${dashboardDate}`);
        const sessions = res.data || [];
        const container = document.getElementById('sessionProgressContainer');
        if (!container) return;

        if (!sessions.length) {
            container.innerHTML = '<div style="color:var(--text-muted);font-size:0.85rem;">Bu tarihte aktif seans randevusu yok</div>';
            return;
        }

        container.innerHTML = sessions.map(s => {
            const pct = Math.round((s.sessionNumber / s.totalSessions) * 100);
            const time = s.startTime ? formatTime(s.startTime) : '';
            return `
            <div style="display:flex;align-items:center;gap:12px;padding:10px 0;border-bottom:1px solid var(--border-glass);">
                <div style="flex:1;min-width:0;">
                    <div style="font-weight:600;font-size:0.88rem;">${s.customerName}
                        <span style="color:var(--text-muted);font-weight:400;font-size:0.78rem;margin-left:6px;">${time}</span>
                    </div>
                    <div style="color:var(--text-secondary);font-size:0.78rem;">${s.serviceName}</div>
                </div>
                <div style="text-align:right;flex-shrink:0;">
                    <div style="font-size:0.78rem;color:var(--color-primary-light);font-weight:600;margin-bottom:4px;">
                        Seans ${s.sessionNumber} / ${s.totalSessions}
                    </div>
                    <div style="width:100px;height:5px;background:rgba(255,255,255,0.08);border-radius:3px;overflow:hidden;">
                        <div style="height:100%;width:${pct}%;background:linear-gradient(90deg,var(--color-primary),var(--color-secondary));transition:width 0.4s;"></div>
                    </div>
                </div>
            </div>`;
        }).join('');
    } catch (e) {
        console.error('Session progress error:', e);
    }
}

// Auto-refresh every 60 seconds
setInterval(() => {
    if (document.visibilityState === 'visible') {
        refreshDashboard();
    }
}, 60000);

// ─── Trend Grafiği (Chart.js) ───
let trendChartInstance = null;
let staffChartInstance = null;

async function loadTrendChart() {
    try {
        const res = await api('GET', '/api/dashboard/trend?days=7');
        const trend = res.data || [];

        const labels = trend.map(d => {
            const dt = new Date(d.date);
            return dt.toLocaleDateString('tr-TR', { weekday: 'short', day: 'numeric', month: 'short' });
        });
        const revenues = trend.map(d => parseFloat(d.revenue) || 0);
        const totals = trend.map(d => d.totalAppointments);
        const completed = trend.map(d => d.completed);

        const ctx = document.getElementById('trendChart');
        if (!ctx) return;

        if (trendChartInstance) trendChartInstance.destroy();

        trendChartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels,
                datasets: [
                    {
                        label: 'Gelir (₺)',
                        data: revenues,
                        borderColor: '#9b59b6',
                        backgroundColor: 'rgba(155,89,182,0.1)',
                        fill: true,
                        tension: 0.4,
                        yAxisID: 'y'
                    },
                    {
                        label: 'Randevu',
                        data: totals,
                        borderColor: '#3498db',
                        backgroundColor: 'transparent',
                        tension: 0.4,
                        borderDash: [4, 4],
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { labels: { color: '#ccc', font: { size: 11 } } } },
                scales: {
                    x: { ticks: { color: '#999', font: { size: 10 } }, grid: { color: 'rgba(255,255,255,0.05)' } },
                    y: {
                        position: 'left',
                        ticks: { color: '#9b59b6', callback: v => '₺' + v.toLocaleString('tr') },
                        grid: { color: 'rgba(255,255,255,0.05)' }
                    },
                    y1: {
                        position: 'right',
                        ticks: { color: '#3498db' },
                        grid: { drawOnChartArea: false }
                    }
                }
            }
        });
    } catch (e) {
        console.error('Trend chart error:', e);
    }
}

function renderStaffChart(staffPerf) {
    const ctx = document.getElementById('staffChart');
    if (!ctx || !staffPerf || !staffPerf.length) return;

    if (staffChartInstance) staffChartInstance.destroy();

    staffChartInstance = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: staffPerf.map(s => s.staffName),
            datasets: [
                {
                    label: 'Gelir (₺)',
                    data: staffPerf.map(s => parseFloat(s.revenue) || 0),
                    backgroundColor: staffPerf.map(s => s.staffColor || '#9b59b6'),
                    borderRadius: 6
                }
            ]
        },
        options: {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: {
                x: { ticks: { color: '#ccc', font: { size: 10 } }, grid: { color: 'rgba(255,255,255,0.05)' } },
                y: {
                    ticks: { color: '#999', callback: v => '₺' + v.toLocaleString('tr') },
                    grid: { color: 'rgba(255,255,255,0.05)' }
                }
            }
        }
    });
}

// ─── Düşük Stok Uyarısı ───
async function checkLowStock() {
    try {
        const res = await api('GET', '/api/products/low-stock');
        const items = res.data || [];
        const badge = document.getElementById('stockAlertBadge');
        const count = document.getElementById('stockAlertCount');
        if (badge && items.length > 0) {
            count.textContent = items.length;
            badge.style.display = 'inline-block';
        }
    } catch (e) { /* sessiz hata */ }
}
