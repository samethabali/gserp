/* ═══════════════════════════════════════════════════════════════
   GSERP — Calendar Page Logic (calendar.js)
   FullCalendar 6 + drag-drop + appointment CRUD + sessions
   ═══════════════════════════════════════════════════════════════ */

let calendar;
let staffData = [];
let serviceData = [];
let activeStaffFilters = new Set();

// ─── Initialize ───
document.addEventListener('DOMContentLoaded', async () => {
    await Promise.all([loadStaffData(), loadServiceData()]);
    initCalendar();
    renderStaffLegend();
    document.getElementById('dateInput').value = todayISO();

    // Status reason select change
    document.getElementById('statusReasonSelect').addEventListener('change', function() {
        const customGroup = document.getElementById('statusReasonCustomGroup');
        customGroup.style.display = this.value === 'Diğer' ? 'block' : 'none';
    });
});

async function loadStaffData() {
    const res = await fetch('/api/staff/specialists');
    const json = await res.json();
    staffData = json.data || [];
    const sel = document.getElementById('staffSelect');
    sel.innerHTML = '<option value="">Uzman seçin...</option>';
    staffData.forEach(s => {
        sel.innerHTML += `<option value="${s.id}">${s.name}</option>`;
    });
    staffData.forEach(s => activeStaffFilters.add(s.id));
}

async function loadServiceData() {
    const res = await fetch('/api/services/active');
    const json = await res.json();
    serviceData = json.data || [];
    const sel = document.getElementById('serviceSelect');
    sel.innerHTML = '<option value="">Hizmet seçin...</option>';
    serviceData.forEach(s => {
        sel.innerHTML += `<option value="${s.id}">${s.name} (${s.durationMinutes} dk — ${Number(s.basePrice).toLocaleString('tr-TR')} ₺)</option>`;
    });
}

// ─── Staff Legend / Filter ───
function renderStaffLegend() {
    const legend = document.getElementById('staffLegend');
    legend.innerHTML = staffData.map(s => `
        <div class="staff-chip ${activeStaffFilters.has(s.id) ? 'active' : ''}"
             onclick="toggleStaffFilter(${s.id})" data-staff-id="${s.id}">
            <span class="chip-dot" style="background:${s.colorHex}"></span>
            ${s.name}
        </div>
    `).join('');
}

function toggleStaffFilter(staffId) {
    if (activeStaffFilters.has(staffId)) {
        activeStaffFilters.delete(staffId);
    } else {
        activeStaffFilters.add(staffId);
    }
    renderStaffLegend();
    calendar.refetchEvents();
}

// ─── FullCalendar Init ───
function initCalendar() {
    const calendarEl = document.getElementById('calendar');

    calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'timeGridDay',
        locale: 'tr',
        firstDay: 1,
        slotMinTime: '08:00:00',
        slotMaxTime: '20:00:00',
        slotDuration: '00:15:00',
        slotLabelInterval: '01:00:00',
        allDaySlot: false,
        nowIndicator: true,
        editable: true,
        selectable: true,
        selectMirror: true,
        height: 'auto',
        expandRows: true,
        dayMaxEvents: 4,       // for month view: show max 4 then "+X more"

        headerToolbar: {
            left: 'prev,next today',
            center: 'title',
            right: 'timeGridDay,timeGridWeek,dayGridMonth'
        },

        buttonText: {
            today: 'Bugün',
            day: 'Gün',
            week: 'Hafta',
            month: 'Ay'
        },

        // Fetch events from API — iterate over each day in the visible range
        events: async function (info, successCallback, failureCallback) {
            try {
                let allEvents = [];
                const startDate = new Date(info.start);
                const endDate = new Date(info.end);

                for (let d = new Date(startDate); d < endDate; d.setDate(d.getDate() + 1)) {
                    const dateStr = localDateStr(d);

                    const res = await fetch(`/api/appointments?date=${dateStr}`);
                    const json = await res.json();
                    const appointments = json.data || [];

                    const events = appointments
                        .filter(a => activeStaffFilters.has(a.staffId))
                        .map(a => ({
                            id: a.id,
                            title: a.customerName,
                            start: a.startTime,
                            end: a.endTime,
                            backgroundColor: getEventBgColor(a),
                            borderColor: 'transparent',
                            textColor: '#fff',
                            extendedProps: { ...a }
                        }));

                    allEvents = allEvents.concat(events);
                }

                successCallback(allEvents);
            } catch (e) {
                console.error('Failed to load events:', e);
                failureCallback(e);
            }
        },

        // Custom event rendering
        eventContent: function (arg) {
            const a = arg.event.extendedProps;
            const flags = (a.flags || []).map(f => `<span title="${f.flagValue}">${f.icon}</span>`).join('');
            const sessionBadge = a.sessionNumber ? `<span class="session-badge">⚡${a.sessionNumber}/${a.totalSessions}</span>` : '';

            // Month view: compact rendering
            if (arg.view.type === 'dayGridMonth') {
                return {
                    html: `<div class="event-month">
                        <span class="event-month-time">${formatTime(a.startTime)}</span>
                        <span>${a.customerName || '?'}</span>
                        ${sessionBadge}
                    </div>`
                };
            }

            return {
                html: `
                    <div class="event-content">
                        <div class="event-customer">${a.customerName || '?'} ${sessionBadge}</div>
                        <div class="event-service">${a.serviceName || '?'} • ${formatTime(a.startTime)}-${formatTime(a.endTime)}</div>
                        ${flags ? `<div class="event-flags">${flags}</div>` : ''}
                    </div>
                `
            };
        },

        // Click to view appointment
        eventClick: function (info) {
            showAppointmentDetail(info.event.extendedProps);
        },

        // Drag-drop to move appointment — FIX: use local date format, not ISO with timezone
        eventDrop: async function (info) {
            const a = info.event.extendedProps;
            const d = info.event.start;
            const newStartTime = `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;

            const moveData = {
                appointmentId: a.id,
                newStartTime: newStartTime,
                version: a.version
            };

            try {
                const res = await fetch(`/api/appointments/${a.id}/move`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(moveData)
                });
                const json = await res.json();

                if (!json.success) {
                    info.revert();
                    showToast(json.message || 'Taşıma başarısız', 'error');
                } else {
                    showToast('Randevu taşındı', 'success');
                }
            } catch (e) {
                info.revert();
                showToast('Bağlantı hatası', 'error');
            }
        },

        // Click on empty slot to create
        select: function (info) {
            const d = info.start;
            document.getElementById('dateInput').value = localDateStr(d);
            document.getElementById('timeInput').value = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
            openCreateModal();
            calendar.unselect();
        },

        // Prevent resize
        eventResize: async function (info) {
            info.revert();
            showToast('Randevu süresi hizmete göre sabitlenmiştir', 'info');
        }
    });

    calendar.render();
}

function localDateStr(d) {
    return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;
}

function getEventBgColor(appointment) {
    if (appointment.status === 'COMPLETED') return 'rgba(46, 204, 113, 0.35)';
    if (appointment.status === 'CANCELLED') return 'rgba(231, 76, 60, 0.25)';
    if (appointment.status === 'NO_SHOW') return 'rgba(155, 89, 182, 0.25)';
    return appointment.staffColor || '#9b59b6';
}

function refreshCalendar() {
    if (calendar) calendar.refetchEvents();
}

// ─── Service Selection Handler ───
function onServiceChange() {
    const serviceId = parseInt(document.getElementById('serviceSelect').value);
    const service = serviceData.find(s => s.id === serviceId);
    if (service) {
        document.getElementById('durationDisplay').value = service.durationMinutes;
        document.getElementById('basePriceDisplay').value = service.basePrice;
        document.getElementById('adjustmentInput').value = 0;
        document.getElementById('finalPriceInput').value = service.basePrice;
        // Show session row for laser/multi-session services
        const showSession = service.category === 'LASER' || service.category === 'SKIN';
        document.getElementById('sessionRow').style.display = showSession ? 'flex' : 'none';
        if (!showSession) {
            document.getElementById('sessionCountInput').value = 1;
            document.getElementById('sessionInfo').style.display = 'none';
        }
    } else {
        document.getElementById('durationDisplay').value = '';
        document.getElementById('basePriceDisplay').value = '';
        document.getElementById('finalPriceInput').value = '';
        document.getElementById('sessionRow').style.display = 'none';
    }
}

function updateFinalPrice() {
    const base = parseFloat(document.getElementById('basePriceDisplay').value) || 0;
    const adj = parseFloat(document.getElementById('adjustmentInput').value) || 0;
    document.getElementById('finalPriceInput').value = base + adj;
}

function updateSessionInfo() {
    const count = parseInt(document.getElementById('sessionCountInput').value) || 1;
    const infoEl = document.getElementById('sessionInfo');
    const textEl = document.getElementById('sessionInfoText');
    if (count > 1) {
        const dayNames = { MONDAY:'Pazartesi', TUESDAY:'Salı', WEDNESDAY:'Çarşamba', THURSDAY:'Perşembe', FRIDAY:'Cuma', SATURDAY:'Cumartesi' };
        const day = document.getElementById('preferredDaySelect').value;
        const dayName = day ? dayNames[day] : 'aynı gün';
        const time = document.getElementById('timeInput').value || '--:--';
        textEl.textContent = `${count} hafta boyunca her ${dayName} ${time}'de randevu oluşturulacak`;
        infoEl.style.display = 'block';
    } else {
        infoEl.style.display = 'none';
    }
}

// ─── Flag Toggles ───
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.flag-toggle').forEach(btn => {
        btn.addEventListener('click', () => {
            btn.classList.toggle('btn-primary');
            btn.classList.toggle('btn-secondary');
        });
    });
});

function getSelectedFlags() {
    const flags = [];
    const flagValue = document.getElementById('flagValueInput').value.trim();
    document.querySelectorAll('.flag-toggle.btn-primary').forEach(btn => {
        flags.push({
            flagType: btn.dataset.flag,
            flagValue: flagValue || btn.textContent.trim(),
            icon: btn.dataset.icon
        });
    });
    return flags;
}

// ─── Create Modal ───
function openCreateModal() {
    document.getElementById('editAppointmentId').value = '';
    document.getElementById('editVersion').value = '';
    document.getElementById('modalTitle').textContent = 'Yeni Randevu';
    document.getElementById('customerNameInput').value = '';
    document.getElementById('customerPhoneInput').value = '';
    document.getElementById('staffSelect').value = '';
    document.getElementById('serviceSelect').value = '';
    document.getElementById('durationDisplay').value = '';
    document.getElementById('basePriceDisplay').value = '';
    document.getElementById('adjustmentInput').value = '0';
    document.getElementById('adjustmentNoteInput').value = '';
    document.getElementById('finalPriceInput').value = '';
    document.getElementById('noteInput').value = '';
    document.getElementById('flagValueInput').value = '';
    document.getElementById('sessionCountInput').value = '1';
    document.getElementById('preferredDaySelect').value = '';
    document.getElementById('sessionRow').style.display = 'none';
    document.getElementById('sessionInfo').style.display = 'none';
    document.querySelectorAll('.flag-toggle').forEach(btn => {
        btn.classList.remove('btn-primary');
        btn.classList.add('btn-secondary');
    });
    openModal('appointmentModal');
}

// ─── Edit Modal ───
function openEditModal(a) {
    closeModal('viewModal');
    document.getElementById('editAppointmentId').value = a.id;
    document.getElementById('editVersion').value = a.version;
    document.getElementById('modalTitle').textContent = '✏️ Randevu Düzenle';
    document.getElementById('customerNameInput').value = a.customerName || '';
    document.getElementById('customerPhoneInput').value = a.customerPhone || '';
    document.getElementById('staffSelect').value = a.staffId;
    document.getElementById('serviceSelect').value = a.serviceId;
    document.getElementById('durationDisplay').value = a.durationMinutes;
    document.getElementById('basePriceDisplay').value = a.basePrice || '';
    document.getElementById('adjustmentInput').value = a.adjustment || 0;
    document.getElementById('adjustmentNoteInput').value = a.adjustmentNote || '';
    document.getElementById('finalPriceInput').value = a.finalPrice || '';
    document.getElementById('dateInput').value = a.startTime.split('T')[0];
    document.getElementById('timeInput').value = a.startTime.split('T')[1]?.substring(0, 5) || '';
    document.getElementById('noteInput').value = a.internalNote || '';
    document.getElementById('flagValueInput').value = '';
    document.getElementById('sessionRow').style.display = 'none';
    document.getElementById('sessionInfo').style.display = 'none';
    document.getElementById('sessionCountInput').value = '1';

    // Set flags
    document.querySelectorAll('.flag-toggle').forEach(btn => {
        const isActive = (a.flags || []).some(f => f.flagType === btn.dataset.flag);
        btn.classList.toggle('btn-primary', isActive);
        btn.classList.toggle('btn-secondary', !isActive);
        if (isActive) {
            const flag = a.flags.find(f => f.flagType === btn.dataset.flag);
            if (flag) document.getElementById('flagValueInput').value = flag.flagValue || '';
        }
    });

    openModal('appointmentModal');
}

// ─── Save Appointment ───
async function saveAppointment() {
    const editId = document.getElementById('editAppointmentId').value;
    const customerName = document.getElementById('customerNameInput').value.trim();
    const customerPhone = document.getElementById('customerPhoneInput').value.trim();
    const staffId = document.getElementById('staffSelect').value;
    const serviceId = document.getElementById('serviceSelect').value;
    const date = document.getElementById('dateInput').value;
    const time = document.getElementById('timeInput').value;
    const finalPrice = document.getElementById('finalPriceInput').value;
    const adjustment = document.getElementById('adjustmentInput').value;
    const adjustmentNote = document.getElementById('adjustmentNoteInput').value.trim();
    const note = document.getElementById('noteInput').value;

    if (!customerName || !staffId || !serviceId || !date || !time) {
        showToast('Lütfen tüm zorunlu alanları doldurun', 'warning');
        return;
    }

    const startTime = `${date}T${time}:00`;
    const flags = getSelectedFlags();

    const body = {
        customerName: customerName,
        customerPhone: customerPhone,
        staffId: parseInt(staffId),
        serviceId: parseInt(serviceId),
        startTime: startTime,
        finalPrice: finalPrice ? parseFloat(finalPrice) : null,
        adjustment: adjustment ? parseFloat(adjustment) : null,
        adjustmentNote: adjustmentNote || null,
        internalNote: note,
        flags: flags.length > 0 ? flags : null
    };

    // Add session fields if creating new
    if (!editId) {
        const sessionCount = parseInt(document.getElementById('sessionCountInput').value) || 1;
        const preferredDay = document.getElementById('preferredDaySelect').value;
        if (sessionCount > 1) {
            body.numberOfSessions = sessionCount;
            if (preferredDay) body.preferredDayOfWeek = preferredDay;
        }
    }

    try {
        let url, method;
        if (editId) {
            url = `/api/appointments/${editId}`;
            method = 'PUT';
        } else {
            url = '/api/appointments';
            method = 'POST';
        }

        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        const json = await res.json();

        if (json.success) {
            showToast(json.message || 'Randevu kaydedildi', 'success');
            closeModal('appointmentModal');
            calendar.refetchEvents();
        } else {
            showToast(json.message || 'Hata oluştu', 'error');
        }
    } catch (e) {
        showToast('Bağlantı hatası: ' + e.message, 'error');
    }
}

// ─── View Appointment Detail ───
function showAppointmentDetail(a) {
    const flags = (a.flags || []).map(f => `<span class="flag-badge" title="${f.flagValue}">${f.icon} ${f.flagValue}</span>`).join(' ');
    const sessionInfo = a.sessionNumber ? `<div><span class="detail-label">SEANS</span><br><strong>⚡ ${a.sessionNumber} / ${a.totalSessions}</strong></div>` : '';

    // Price display
    let priceHtml = formatCurrency(a.finalPrice);
    if (a.adjustment && parseFloat(a.adjustment) !== 0) {
        const sign = parseFloat(a.adjustment) > 0 ? '+' : '';
        priceHtml = `${formatCurrency(a.basePrice)} ${sign}${formatCurrency(a.adjustment)}<br><strong>${formatCurrency(a.finalPrice)}</strong>`;
        if (a.adjustmentNote) priceHtml += `<br><small style="color:var(--text-muted)">${a.adjustmentNote}</small>`;
    }

    // Cancellation reason
    const reasonHtml = a.cancellationReason ? `<div><span class="detail-label">NEDEN</span><br>${a.cancellationReason}</div>` : '';

    document.getElementById('viewModalBody').innerHTML = `
        <div style="display:flex;flex-direction:column;gap:16px;">
            <div style="display:flex;align-items:center;gap:12px;">
                <span class="staff-dot" style="background:${a.staffColor};width:14px;height:14px;"></span>
                <div>
                    <div style="font-weight:700;font-size:1.1rem;">${a.customerName}</div>
                    <div style="color:var(--text-secondary);font-size:0.85rem;">📞 ${a.customerPhone || '-'}</div>
                </div>
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
                <div><span class="detail-label">UZMAN</span><br><strong>${a.staffName}</strong></div>
                <div><span class="detail-label">HİZMET</span><br><strong>${a.serviceName}</strong></div>
                <div><span class="detail-label">SAAT</span><br><strong>${formatTime(a.startTime)} - ${formatTime(a.endTime)}</strong></div>
                <div><span class="detail-label">FİYAT</span><br>${priceHtml}</div>
            </div>
            ${sessionInfo}
            <div><span class="detail-label">DURUM</span><br>${statusBadge(a.status)}</div>
            ${reasonHtml}
            ${a.internalNote ? `<div><span class="detail-label">NOT</span><br>${a.internalNote}</div>` : ''}
            ${flags ? `<div><span class="detail-label">NOTLAR</span><br><div class="flag-badges">${flags}</div></div>` : ''}
            <div id="customerHistorySection"></div>
        </div>
    `;

    // Status action buttons
    let actions = '';
    if (a.status === 'SCHEDULED') {
        actions = `
            <button class="btn btn-sm btn-warning" onclick="changeAppointmentStatus(${a.id}, 'IN_PROGRESS')">▶️ Başlat</button>
            <button class="btn btn-sm btn-danger" onclick="openStatusModal(${a.id}, 'NO_SHOW', '👻 Gelmedi')">👻 Gelmedi</button>
            <button class="btn btn-sm btn-ghost" onclick="openStatusModal(${a.id}, 'CANCELLED', '❌ İptal')">❌ İptal</button>
        `;
    } else if (a.status === 'IN_PROGRESS') {
        actions = `
            <button class="btn btn-sm btn-success" onclick="changeAppointmentStatus(${a.id}, 'COMPLETED')">✅ Tamamla</button>
        `;
    }
    actions += `<button class="btn btn-sm btn-primary" onclick='openEditModal(${JSON.stringify(a).replace(/'/g, "&#39;")})'>✏️ Düzenle</button>`;
    actions += `<button class="btn btn-sm btn-danger" onclick="deleteAppointment(${a.id})">🗑️ Sil</button>`;

    document.getElementById('viewModalFooter').innerHTML = actions;
    openModal('viewModal');

    // Load customer history
    if (a.customerPhone) {
        loadCustomerHistory(a.customerPhone, a.id);
    }
}

async function loadCustomerHistory(phone, currentId) {
    try {
        const res = await fetch(`/api/appointments/history?phone=${encodeURIComponent(phone)}`);
        const json = await res.json();
        const history = (json.data || []).filter(h => h.id !== currentId);
        if (history.length > 0) {
            const rows = history.slice(0, 5).map(h => `
                <div style="display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.05);">
                    <div>
                        <span style="font-size:0.75rem;color:var(--text-muted);">${formatDate(h.startTime)}</span>
                        <span style="margin-left:8px;">${h.serviceName}</span>
                    </div>
                    <div>${statusBadge(h.status)}</div>
                </div>
            `).join('');
            document.getElementById('customerHistorySection').innerHTML = `
                <div style="margin-top:8px;">
                    <span class="detail-label">📜 GEÇMİŞ İŞLEMLER (son ${Math.min(5, history.length)})</span>
                    <div style="margin-top:6px;">${rows}</div>
                </div>
            `;
        }
    } catch (e) { /* silent */ }
}

// Status change with reason
function openStatusModal(id, status, title) {
    document.getElementById('statusAppointmentId').value = id;
    document.getElementById('statusNewValue').value = status;
    document.getElementById('statusModalTitle').textContent = title;
    document.getElementById('statusReasonSelect').value = '';
    document.getElementById('statusReasonCustom').value = '';
    document.getElementById('statusReasonCustomGroup').style.display = 'none';
    closeModal('viewModal');
    openModal('statusModal');
}

async function confirmStatusChange() {
    const id = document.getElementById('statusAppointmentId').value;
    const status = document.getElementById('statusNewValue').value;
    let reason = document.getElementById('statusReasonSelect').value;
    if (reason === 'Diğer') {
        reason = document.getElementById('statusReasonCustom').value.trim() || 'Diğer';
    }

    const res = await fetch(`/api/appointments/${id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status, reason })
    });
    const json = await res.json();
    if (json.success) {
        showToast('Durum güncellendi', 'success');
        closeModal('statusModal');
        calendar.refetchEvents();
    } else {
        showToast(json.message, 'error');
    }
}

async function changeAppointmentStatus(id, status) {
    // For simple transitions (no reason needed: start, complete)
    const res = await fetch(`/api/appointments/${id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status })
    });
    const json = await res.json();
    if (json.success) {
        showToast('Durum güncellendi', 'success');
        closeModal('viewModal');
        calendar.refetchEvents();
    } else {
        showToast(json.message, 'error');
    }
}

async function deleteAppointment(id) {
    if (!confirm('Bu randevuyu silmek istediğinize emin misiniz?')) return;
    const res = await fetch(`/api/appointments/${id}`, { method: 'DELETE' });
    const json = await res.json();
    showToast(json.message || 'Silindi', json.success ? 'success' : 'error');
    closeModal('viewModal');
    calendar.refetchEvents();
}
