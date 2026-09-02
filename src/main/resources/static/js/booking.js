/* ═══════════════════════════════════════════════════════════════
   GSCRM — Public Booking Page (booking.js)
   ═══════════════════════════════════════════════════════════════ */

const state = {
    serviceId:   null,
    serviceName: '',
    servicePrice: null,
    staffId:     null,
    staffName:   '',
    date:        null,
    time:        null,
};

let salonBranding = { name: 'Online Randevu', logoUrl: '', primaryColor: '#e91e8c' };

const CAT_LABELS = { HAIR: '💇 Saç', NAIL: '💅 Tırnak', SKIN: '🧖 Cilt', LASER: '⚡ Lazer', OTHER: '✨ Diğer' };

// ─── Boot ───
document.addEventListener('DOMContentLoaded', async () => {
    const dateInput = document.getElementById('bookDate');
    dateInput.min = todayISO();
    dateInput.value = todayISO();

    await Promise.all([loadServices(), loadStaff(), loadSalonBranding()]);
});

async function loadSalonBranding() {
    try {
        const json = await fetch('/api/settings/public').then(r => r.json());
        const s = json.data || {};
        salonBranding = { name: s.name || salonBranding.name, logoUrl: s.logoUrl || '', primaryColor: s.primaryColor || salonBranding.primaryColor };
        applySalonBranding();
    } catch (_) { /* defaults */ }
}

function applySalonBranding() {
    const titleEl = document.getElementById('bookingTitle');
    if (titleEl) titleEl.textContent = salonBranding.name;
    if (salonBranding.primaryColor) {
        document.documentElement.style.setProperty('--color-primary', salonBranding.primaryColor);
    }
    const logoEl = document.getElementById('bookingLogo');
    if (logoEl && salonBranding.logoUrl) {
        logoEl.innerHTML = `<img src="${salonBranding.logoUrl}" alt="" style="max-height:64px;max-width:120px;border-radius:8px;">`;
    }
}

// ─── Adım navigasyonu ───
function goStep(n) {
    // Validasyon
    if (n === 2 && !state.serviceId) { showToast('Lütfen bir hizmet seçin', 'warning'); return; }
    if (n === 3 && !state.staffId)   { showToast('Lütfen bir uzman seçin', 'warning'); return; }
    if (n === 4 && !state.time)      { showToast('Lütfen bir saat seçin', 'warning'); return; }

    if (n === 4) buildSummary();
    if (n === 3) loadSlots();

    document.querySelectorAll('.step').forEach((s, i) => s.classList.toggle('active', i + 1 === n));
    document.querySelectorAll('.step-dot').forEach((d, i) => {
        d.classList.toggle('active', i + 1 === n);
        d.classList.toggle('done',   i + 1 < n);
    });
}

// ─── Adım 1: Hizmetler ───
async function loadServices() {
    const json = await fetch('/api/booking/services').then(r => r.json());
    const services = json.data || [];
    const grid = document.getElementById('serviceGrid');
    if (!services.length) { grid.innerHTML = '<p style="color:var(--text-muted);">Hizmet bulunamadı</p>'; return; }

    grid.innerHTML = services.map(s => `
        <div class="service-option" onclick="selectService(${s.id},'${esc(s.name)}',${s.basePrice})" id="srv_${s.id}">
            <div style="font-size:1rem;margin-bottom:2px;">${CAT_LABELS[s.category] || '✨'}</div>
            <div style="font-weight:600;font-size:0.9rem;">${s.name}</div>
            <div style="color:var(--text-muted);font-size:0.78rem;margin-top:4px;">⏱ ${s.durationMinutes} dk &nbsp;|&nbsp; ${formatCurrency(s.basePrice)}</div>
        </div>`).join('');
}

function selectService(id, name, price) {
    state.serviceId    = id;
    state.serviceName  = name;
    state.servicePrice = price;
    document.querySelectorAll('.service-option').forEach(el => el.classList.remove('selected'));
    document.getElementById('srv_' + id)?.classList.add('selected');
}

// ─── Adım 2: Uzmanlar ───
async function loadStaff() {
    const json = await fetch('/api/booking/staff').then(r => r.json());
    const staff = (json.data || []).filter(s => s.role === 'SPECIALIST');
    const grid = document.getElementById('staffGrid');
    if (!staff.length) { grid.innerHTML = '<p style="color:var(--text-muted);">Uzman bulunamadı</p>'; return; }

    grid.innerHTML = staff.map(s => `
        <div class="staff-option" onclick="selectStaff(${s.id},'${esc(s.name)}')" id="stf_${s.id}">
            <div style="width:36px;height:36px;border-radius:50%;background:${s.colorHex};display:flex;align-items:center;justify-content:center;font-size:1.1rem;flex-shrink:0;">👩‍💼</div>
            <div>
                <div style="font-weight:600;font-size:0.9rem;">${s.name}</div>
            </div>
        </div>`).join('');
}

function selectStaff(id, name) {
    state.staffId   = id;
    state.staffName = name;
    document.querySelectorAll('.staff-option').forEach(el => el.classList.remove('selected'));
    document.getElementById('stf_' + id)?.classList.add('selected');
}

// ─── Adım 3: Slotlar ───
async function loadSlots() {
    state.time = null;
    const date = document.getElementById('bookDate').value;
    state.date = date;
    if (!state.staffId || !state.serviceId || !date) return;

    const container = document.getElementById('slotContainer');
    container.innerHTML = '<p style="color:var(--text-muted);font-size:0.85rem;">Müsait saatler yükleniyor...</p>';

    const json = await fetch(`/api/booking/availability?staffId=${state.staffId}&serviceId=${state.serviceId}&date=${date}`)
                      .then(r => r.json());
    const slots = json.data || [];

    if (!slots.length) { container.innerHTML = '<p style="color:var(--text-muted);">Bu gün için müsait saat bulunamadı</p>'; return; }

    container.innerHTML = `<div class="slot-grid">${
        slots.map(s => `
            <button class="slot-btn" id="slot_${s.time.replace(':','')}"
                    ${!s.available ? 'disabled' : ''}
                    onclick="selectSlot('${s.time}')">
                ${s.time}
            </button>`).join('')
    }</div>`;
}

function selectSlot(time) {
    state.time = time;
    document.querySelectorAll('.slot-btn').forEach(b => b.classList.remove('selected'));
    document.getElementById('slot_' + time.replace(':', ''))?.classList.add('selected');
}

// ─── Adım 4: Özet ───
function buildSummary() {
    document.getElementById('bookSummary').innerHTML = `
        <div>💇 <strong>${state.serviceName}</strong></div>
        <div>👩‍💼 <strong>${state.staffName}</strong></div>
        <div>📅 <strong>${formatDate(state.date + 'T00:00:00')}</strong> saat <strong>${state.time}</strong></div>
        <div>💰 <strong>${formatCurrency(state.servicePrice)}</strong></div>`;
}

// ─── Randevu Gönder ───
async function submitBooking() {
    const name  = document.getElementById('bookName').value.trim();
    const phone = document.getElementById('bookPhone').value.trim();

    if (!name)  { showToast('Ad Soyad zorunludur', 'warning'); return; }
    if (!phone) { showToast('Telefon numarası zorunludur', 'warning'); return; }
    if (!document.getElementById('bookKvkk')?.checked) {
        showToast('KVKK onayı zorunludur', 'warning'); return;
    }

    const startTime = `${state.date}T${state.time}:00`;

    const body = {
        customerName:  name,
        customerPhone: phone,
        staffId:       state.staffId,
        serviceId:     state.serviceId,
        startTime:     startTime,
        consentTypes:  ['PRIVACY'],
    };

    const res  = await fetch('/api/booking/request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    const json = await res.json();

    if (json.success) {
        document.getElementById('confirmDetails').innerHTML = `
            <div>💇 ${state.serviceName}</div>
            <div>👩‍💼 ${state.staffName}</div>
            <div>📅 ${formatDate(state.date + 'T00:00:00')} — ${state.time}</div>
            <div style="margin-top:12px;padding:10px;background:rgba(241,196,15,0.15);border-radius:8px;font-size:0.85rem;">
                ⏳ Randevu isteğiniz alındı. Salon onayından sonra kesinleşecektir.
            </div>
            <div style="margin-top:8px;color:var(--text-muted);font-size:0.8rem;">Referans: <strong>#${json.data?.id || '-'}</strong></div>`;
        goStep(5);
    } else {
        showToast(json.message || 'Randevu oluşturulamadı', 'error');
    }
}

// ─── Sıfırla ───
function resetBooking() {
    state.serviceId = null; state.serviceName = '';
    state.staffId   = null; state.staffName   = '';
    state.date      = null; state.time        = null;
    document.getElementById('bookName').value  = '';
    document.getElementById('bookPhone').value = '';
    document.getElementById('bookDate').value  = todayISO();
    goStep(1);
}

function esc(s) { return s.replace(/'/g, "\\'").replace(/"/g, '&quot;'); }
