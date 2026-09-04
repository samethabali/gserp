/* ═══════════════════════════════════════════════════════════════
   GSCRM — Public Booking Page (booking.js)
   ═══════════════════════════════════════════════════════════════ */

const state = {
    serviceId:   null,
    serviceName: '',
    servicePrice: null,
    // Epilasyon hizmetlerinde araya bölge adımı girer.
    needsBodyRegion: false,
    bodyRegions: [],
    staffId:     null,
    staffName:   '',
    date:        null,
    time:        null,
    // Telefon doğrulama (salon ayarı kapalıyken bu alanların hiçbiri kullanılmaz)
    smsVerification:   false,
    verificationToken: null,
    verifiedPhone:     '',
};

let salonBranding = { name: 'Online Randevu', logoUrl: '', primaryColor: '#e91e8c' };

/** Hizmetin kategorisi seçimden sonra da gerekiyor (bölge adımı kararı). */
let allServices = [];

/** Form ne zaman açıldı — anında gönderim bot işaretidir. */
let formLoadedAt = Date.now();

/**
 * Akıştaki adımlar. Doğrulama açıksa araya bir adım girdiği için nokta
 * göstergesi de bu diziden üretilir — sabit sayıda nokta iki modda da doğru olamaz.
 */
let steps = ['step1', 'step2', 'step3', 'step4', 'step5'];
let currentStep = 0;

const CAT_LABELS = { HAIR: '💇 Saç', NAIL: '💅 Tırnak', SKIN: '🧖 Cilt', LASER: '⚡ Lazer', OTHER: '✨ Diğer' };

// ─── Boot ───
document.addEventListener('DOMContentLoaded', async () => {
    const dateInput = document.getElementById('bookDate');
    dateInput.min = todayISO();
    dateInput.value = todayISO();
    formLoadedAt = Date.now();

    await Promise.all([loadServices(), loadStaff(), loadSalonBranding()]);
    rebuildSteps();
});

async function loadSalonBranding() {
    try {
        const json = await fetch('/api/settings/public').then(r => r.json());
        const s = json.data || {};
        salonBranding = { name: s.name || salonBranding.name, logoUrl: s.logoUrl || '', primaryColor: s.primaryColor || salonBranding.primaryColor };
        state.smsVerification = s.smsVerificationEnabled === 'true';
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
        logoEl.innerHTML = `<img src="${encodeURI(salonBranding.logoUrl)}" alt="" style="max-height:64px;max-width:120px;border-radius:8px;">`;
    }
}

// ─── Adım navigasyonu ───

/**
 * Adım listesini ve nokta göstergesini kurar.
 *
 * Akış iki yerde dallanıyor — epilasyonda bölge adımı, salon ayarı açıkken
 * doğrulama adımı — bu yüzden başlıktaki sıra numarası da buradan yazılır;
 * gömülü sabit numaralar dört akışın hepsinde birden doğru olamaz.
 */
function rebuildSteps() {
    steps = ['step1'];
    if (state.needsBodyRegion) steps.push('stepRegion');
    steps.push('step2', 'step3');
    if (state.smsVerification) steps.push('stepVerify');
    steps.push('step4', 'step5');

    const indicator = document.getElementById('stepIndicator');
    if (indicator) {
        indicator.innerHTML = steps.map((_, i) =>
            `<div class="step-dot${i === currentStep ? ' active' : ''}${i < currentStep ? ' done' : ''}"></div>`
        ).join('');
    }

    steps.forEach((id, i) => {
        const heading = document.querySelector(`#${id} .step-title`);
        if (heading) heading.textContent = `${i + 1}. ${heading.dataset.stepTitle}`;
    });

    // Doğrulama açıkken telefon doğrulama adımında alınır; son adımda tekrar sorulmaz.
    const phoneGroup = document.getElementById('bookPhone')?.closest('.form-group');
    if (phoneGroup) phoneGroup.style.display = state.smsVerification ? 'none' : '';
}

function showStep(index) {
    currentStep = index;
    steps.forEach((id, i) => {
        document.getElementById(id)?.classList.toggle('active', i === index);
    });
    // Görünürdeki diğer adımları da kapat (mod değişiminde artık kalmasın)
    document.querySelectorAll('.step').forEach(el => {
        if (!steps.includes(el.id)) el.classList.remove('active');
    });
    rebuildSteps();
}

/** Bu adımdan ileri gitmek için gereken şart sağlandı mı? */
function canLeave(stepId) {
    if (stepId === 'step1' && !state.serviceId) { showToast('Lütfen bir hizmet seçin', 'warning'); return false; }
    if (stepId === 'step2' && !state.staffId)   { showToast('Lütfen bir uzman seçin', 'warning'); return false; }
    if (stepId === 'stepRegion' && !state.bodyRegions.length) {
        showToast('Lütfen en az bir bölge seçin', 'warning');
        return false;
    }
    if (stepId === 'step3' && !state.time)      { showToast('Lütfen bir saat seçin', 'warning'); return false; }
    if (stepId === 'stepVerify' && !state.verificationToken) {
        showToast('Devam etmek için numaranızı doğrulayın', 'warning');
        return false;
    }
    return true;
}

function goNext() {
    if (!canLeave(steps[currentStep])) return;
    if (currentStep >= steps.length - 1) return;
    enterStep(currentStep + 1);
}

function goBack() {
    if (currentStep <= 0) return;
    showStep(currentStep - 1);
}

/** Bir adıma girerken o adımın hazırlık işini yapar. */
function enterStep(index) {
    const id = steps[index];
    if (id === 'stepRegion') renderBookingBodyMap();
    if (id === 'step3') loadSlots();
    if (id === 'step4') buildSummary();
    showStep(index);
}

/** Doğrudan bir adıma atla (onay ekranı için). */
function goStepId(id) {
    const index = steps.indexOf(id);
    if (index >= 0) showStep(index);
}

// ─── Adım 1: Hizmetler ───
async function loadServices() {
    const json = await fetch('/api/booking/services').then(r => r.json());
    const services = json.data || [];
    allServices = services;
    const grid = document.getElementById('serviceGrid');
    if (!services.length) { grid.innerHTML = '<p style="color:var(--text-muted);">Hizmet bulunamadı</p>'; return; }

    grid.innerHTML = services.map(s => `
        <div class="service-option" onclick="selectService(${s.id})" id="srv_${s.id}">
            <div style="font-size:1rem;margin-bottom:2px;">${CAT_LABELS[s.category] || '✨'}</div>
            <div style="font-weight:600;font-size:0.9rem;">${escapeHtml(s.name)}</div>
            <div style="color:var(--text-muted);font-size:0.78rem;margin-top:4px;">⏱ ${s.durationMinutes} dk &nbsp;|&nbsp; ${formatCurrency(s.basePrice)}</div>
        </div>`).join('');
}

function selectService(id) {
    const service = allServices.find(s => s.id === id);
    if (!service) return;

    state.serviceId    = id;
    state.serviceName  = service.name;
    state.servicePrice = service.basePrice;

    // Epilasyon dışı bir hizmete geçilirse bölge seçimi taşınmamalı: müşteri
    // manikür randevusunun özetinde "koltuk altı" görmemeli.
    const needsRegion = isEpilationService(service);
    if (!needsRegion) state.bodyRegions = [];
    state.needsBodyRegion = needsRegion;

    document.querySelectorAll('.service-option').forEach(el => el.classList.remove('selected'));
    document.getElementById('srv_' + id)?.classList.add('selected');
    rebuildSteps();
}

// ─── Bölge adımı ───
function renderBookingBodyMap() {
    renderBodyMap(document.getElementById('bookingBodyMap'), {
        selected: state.bodyRegions,
        onChange: codes => { state.bodyRegions = codes; },
    });
}

// ─── Adım 2: Uzmanlar ───
async function loadStaff() {
    const json = await fetch('/api/booking/staff').then(r => r.json());
    // Uzman filtresi sunucuda: /api/booking/staff yalnizca aktif SPECIALIST doner.
    // Burada role'e bakmak bir donem her uzmani eliyordu — payload role tasimiyor.
    const staff = json.data || [];
    const grid = document.getElementById('staffGrid');
    if (!staff.length) { grid.innerHTML = '<p style="color:var(--text-muted);">Uzman bulunamadı</p>'; return; }

    grid.innerHTML = staff.map(s => `
        <div class="staff-option" onclick="selectStaff(${s.id},'${esc(s.name)}')" id="stf_${s.id}">
            <div style="width:36px;height:36px;border-radius:50%;background:${s.colorHex};display:flex;align-items:center;justify-content:center;font-size:1.1rem;flex-shrink:0;">👩‍💼</div>
            <div>
                <div style="font-weight:600;font-size:0.9rem;">${escapeHtml(s.name)}</div>
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

// ─── Telefon doğrulama (yalnızca salon ayarı açıkken) ───

async function startVerification() {
    const phone = document.getElementById('verifyPhone').value.trim();
    if (!phone) { showToast('Telefon numarası zorunludur', 'warning'); return; }

    const btn    = document.getElementById('btnSendCode');
    const status = document.getElementById('verifyStatus');
    btn.disabled = true;

    try {
        const res  = await fetch('/api/booking/verify/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ phone }),
        });
        const json = await res.json();
        const data = json.data || {};

        if (!res.ok || !json.success) {
            status.textContent = json.message || 'Kod gönderilemedi';
            btn.disabled = false;
            return;
        }

        if (data.sent) {
            document.getElementById('codeBox').style.display = 'block';
            status.textContent = 'Kod gönderildi';
            startResendCountdown(Number(data.resendAfterSeconds) || 60);
        } else {
            status.textContent = json.message || 'Kod gönderilemedi';
            btn.disabled = false;
        }
    } catch (_) {
        status.textContent = 'Bağlantı hatası';
        btn.disabled = false;
    }
}

function startResendCountdown(seconds) {
    const btn = document.getElementById('btnSendCode');
    let left = seconds;
    btn.disabled = true;
    btn.textContent = `Tekrar gönder (${left}sn)`;
    const timer = setInterval(() => {
        left -= 1;
        if (left <= 0) {
            clearInterval(timer);
            btn.disabled = false;
            btn.textContent = '📩 Tekrar Gönder';
            return;
        }
        btn.textContent = `Tekrar gönder (${left}sn)`;
    }, 1000);
}

async function confirmVerification() {
    const phone = document.getElementById('verifyPhone').value.trim();
    const code  = document.getElementById('verifyCode').value.trim();
    if (!code) { showToast('Doğrulama kodunu girin', 'warning'); return; }

    const res  = await fetch('/api/booking/verify/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, code }),
    });
    const json = await res.json();

    if (!res.ok || !json.success) {
        showToast(json.message || 'Kod hatalı', 'error');
        return;
    }

    const data = json.data || {};
    state.verificationToken = data.verificationToken;
    state.verifiedPhone     = phone;

    renderRecognition(data);
    goNext();
}

/** "Hoş geldin" bandı, ad prefill, geçmiş ve sadakat — yalnızca doğrulamadan sonra. */
function renderRecognition(data) {
    const banner = document.getElementById('recognizedBanner');
    if (!banner) return;

    if (!data.recognized) {
        banner.style.display = 'none';
        banner.innerHTML = '';
        return;
    }

    const nameInput = document.getElementById('bookName');
    const fullName  = [data.firstName, data.lastName].filter(Boolean).join(' ').trim();
    if (nameInput && !nameInput.value.trim() && fullName) nameInput.value = fullName;

    const past = data.pastAppointments || [];
    const historyHtml = past.length
        ? `<details style="margin-top:8px;">
             <summary style="cursor:pointer;">Önceki randevularınız (${past.length})</summary>
             <div style="margin-top:6px;line-height:1.7;">${
                past.map(a => `• ${formatDate(a.startTime)} — ${escapeHtml(a.serviceName || '')}`).join('<br>')
             }</div>
           </details>`
        : '';

    const loyalty = data.loyalty || {};
    const discount = Number(loyalty.discountPercentage || 0);
    const loyaltyHtml = discount > 0
        ? `<div style="margin-top:6px;">🎁 Sadakat indiriminiz: <strong>%${discount}</strong>${
              loyalty.tierName ? ` (${escapeHtml(loyalty.tierName)})` : ''}</div>`
        : '';

    banner.innerHTML = `<div>👋 Hoş geldin, <strong>${escapeHtml(data.firstName || '')}</strong>!</div>${loyaltyHtml}${historyHtml}`;
    banner.style.display = 'block';
}

// ─── Adım 4: Özet ───
function buildSummary() {
    const regionLine = state.bodyRegions.length
        ? `<div>🎯 <strong>${escapeHtml(bodyRegionLabels(state.bodyRegions))}</strong></div>`
        : '';
    document.getElementById('bookSummary').innerHTML = `
        <div>💇 <strong>${escapeHtml(state.serviceName)}</strong></div>
        ${regionLine}
        <div>👩‍💼 <strong>${escapeHtml(state.staffName)}</strong></div>
        <div>📅 <strong>${formatDate(state.date + 'T00:00:00')}</strong> saat <strong>${state.time}</strong></div>
        <div>💰 <strong>${formatCurrency(state.servicePrice)}</strong></div>`;
}

// ─── Randevu Gönder ───
async function submitBooking() {
    const name  = document.getElementById('bookName').value.trim();
    // Doğrulama açıkken numara doğrulama adımında alındı; tekrar sorulmuyor.
    const phone = state.smsVerification
        ? state.verifiedPhone
        : document.getElementById('bookPhone').value.trim();

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
        bodyRegions:   state.bodyRegions,
        consentTypes:  ['PRIVACY'],
        website:       document.getElementById('bookWebsite')?.value || '',
        elapsedMs:     Date.now() - formLoadedAt,
    };
    if (state.verificationToken) {
        body.verificationToken = state.verificationToken;
    }

    const res  = await fetch('/api/booking/request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    const json = await res.json();

    if (json.success) {
        document.getElementById('confirmDetails').innerHTML = `
            <div>💇 ${escapeHtml(state.serviceName)}</div>
            ${state.bodyRegions.length ? `<div>🎯 ${escapeHtml(bodyRegionLabels(state.bodyRegions))}</div>` : ''}
            <div>👩‍💼 ${escapeHtml(state.staffName)}</div>
            <div>📅 ${formatDate(state.date + 'T00:00:00')} — ${state.time}</div>
            <div style="margin-top:12px;padding:10px;background:rgba(241,196,15,0.15);border-radius:8px;font-size:0.85rem;">
                ⏳ Randevu isteğiniz alındı. Salon onayından sonra kesinleşecektir.
            </div>
            <div style="margin-top:8px;color:var(--text-muted);font-size:0.8rem;">Referans: <strong>#${json.data?.id || '-'}</strong></div>`;
        goStepId('step5');
    } else {
        showToast(json.message || 'Randevu oluşturulamadı', 'error');
    }
}

// ─── Sıfırla ───
function resetBooking() {
    state.serviceId = null; state.serviceName = '';
    state.needsBodyRegion = false; state.bodyRegions = [];
    state.staffId   = null; state.staffName   = '';
    state.date      = null; state.time        = null;
    state.verificationToken = null;
    state.verifiedPhone     = '';

    document.getElementById('bookName').value  = '';
    document.getElementById('bookPhone').value = '';
    document.getElementById('bookDate').value  = todayISO();

    const verifyPhone = document.getElementById('verifyPhone');
    const verifyCode  = document.getElementById('verifyCode');
    if (verifyPhone) verifyPhone.value = '';
    if (verifyCode)  verifyCode.value  = '';
    const codeBox = document.getElementById('codeBox');
    if (codeBox) codeBox.style.display = 'none';
    const banner = document.getElementById('recognizedBanner');
    if (banner) { banner.style.display = 'none'; banner.innerHTML = ''; }

    formLoadedAt = Date.now();
    showStep(0);
}

function esc(s) { return s.replace(/'/g, "\\'").replace(/"/g, '&quot;'); }
