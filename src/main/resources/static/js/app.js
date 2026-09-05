/* ═══════════════════════════════════════════════════════════════
   GSCRM — Common Utilities (app.js)
   ═══════════════════════════════════════════════════════════════ */

// ─── Modal Management ───
function openModal(id) {
    document.getElementById(id).classList.add('active');
}

function closeModal(id) {
    document.getElementById(id).classList.remove('active');
}

// Close modal on overlay click
document.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal-overlay')) {
        e.target.classList.remove('active');
    }
});

// Close modal on Escape
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        document.querySelectorAll('.modal-overlay.active').forEach(m => m.classList.remove('active'));
    }
});

// ─── Toast Notifications ───
function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const icons = { success: '✅', error: '❌', info: 'ℹ️', warning: '⚠️' };
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `<span>${icons[type] || 'ℹ️'}</span><span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => toast.remove(), 4000);
}

// ─── Debounce ───
function debounce(fn, delay) {
    let timer;
    return function (...args) {
        clearTimeout(timer);
        timer = setTimeout(() => fn.apply(this, args), delay);
    };
}

// ─── Date Formatting ───
function formatDate(dateStr) {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function formatTime(dateStr) {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
}

function formatDateTime(dateStr) {
    if (!dateStr) return '-';
    return formatDate(dateStr) + ' ' + formatTime(dateStr);
}

function formatCurrency(amount) {
    if (amount == null) return '0 ₺';
    return Number(amount).toLocaleString('tr-TR', { minimumFractionDigits: 0 }) + ' ₺';
}

function todayISO() {
    return new Date().toISOString().split('T')[0];
}

function pad(n) {
    return n < 10 ? '0' + n : n;
}

// ─── Status Helpers ───
const statusLabels = {
    PENDING_APPROVAL: 'Onay Bekliyor',
    SCHEDULED: 'Bekliyor',
    IN_PROGRESS: 'Devam Ediyor',
    COMPLETED: 'Tamamlandı',
    CANCELLED: 'İptal',
    NO_SHOW: 'Gelmedi'
};

const statusBadgeClass = {
    PENDING_APPROVAL: 'badge-pending-approval',
    SCHEDULED: 'badge-scheduled',
    IN_PROGRESS: 'badge-in-progress',
    COMPLETED: 'badge-completed',
    CANCELLED: 'badge-cancelled',
    NO_SHOW: 'badge-no-show'
};

function statusBadge(status) {
    return `<span class="badge ${statusBadgeClass[status] || ''}">${statusLabels[status] || status}</span>`;
}

// ─── API Helper ───
const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

async function api(method, url, body) {
    const headers = { 'Content-Type': 'application/json' };
    if (csrfToken && csrfHeader && method !== 'GET' && method !== 'HEAD') {
        headers[csrfHeader] = csrfToken;
    }
    const opts = {
        method,
        headers,
        credentials: 'same-origin',
        redirect: 'manual'   // 302 -> /login akışını otomatik takip etme
    };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);

    // 401/302 (form-login chain'inden redirect) -> oturum gerçekten yok
    if (res.status === 401 || res.type === 'opaqueredirect') {
        if (!window.location.pathname.startsWith('/login')) {
            window.location.href = '/login';
        }
        return { success: false, message: 'Oturum sona erdi' };
    }
    // 403 oturumun bittiği değil, bu rolün o uca yetkisi olmadığı anlamına gelir.
    // Eskiden burada da /login'e atılıyordu; uzman (SPECIALIST) hesabı dashboard'da
    // yetkisi olmayan tek bir ucu çağırdığında sayfadan atılıyordu.
    if (res.status === 403) {
        return { success: false, forbidden: true, message: 'Bu işlem için yetkiniz yok' };
    }
    // Boş 204 cevapları
    if (res.status === 204) return { success: true };
    try {
        return await res.json();
    } catch (e) {
        return { success: false, message: 'Sunucudan beklenmeyen yanıt' };
    }
}

// ─── Sidebar Toggle (Mobile) ───
function initSidebar() {
    const sidebar  = document.getElementById('sidebar');
    const overlay  = document.getElementById('sidebarOverlay');
    const toggle   = document.getElementById('menuToggle');
    if (!sidebar || !overlay || !toggle) return;

    toggle.addEventListener('click', () => {
        sidebar.classList.toggle('open');
        overlay.classList.toggle('active');
    });

    overlay.addEventListener('click', () => {
        sidebar.classList.remove('open');
        overlay.classList.remove('active');
    });

    // Nav linke tıklandığında mobilde sidebar'ı kapat
    sidebar.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', () => {
            if (window.innerWidth <= 1024) {
                sidebar.classList.remove('open');
                overlay.classList.remove('active');
            }
        });
    });
}

document.addEventListener('DOMContentLoaded', () => {
    initSidebar();
    initShowcaseAndBilling();
    initSalonSwitcher();
});

async function initSalonSwitcher() {
    const wrap = document.getElementById('salonSwitcherWrap');
    const select = document.getElementById('salonSwitcher');
    if (!wrap || !select) return;
    const json = await api('GET', '/api/org/salons');
    if (!json.success || !json.data || json.data.length < 2) return;
    wrap.style.display = 'block';
    const currentSlug = document.querySelector('meta[name="current-salon-slug"]')?.content || '';
    select.innerHTML = json.data.map(s =>
        `<option value="${s.slug}"${s.slug === currentSlug ? ' selected' : ''}>${s.name}</option>`
    ).join('');
    select.addEventListener('change', async () => {
        const slug = select.value;
        const res = await api('POST', '/api/org/switch-salon', { slug });
        if (res.success) {
            if (res.data && res.data.token) {
                try { localStorage.setItem('accessToken', res.data.token); } catch (_) {}
            }
            window.location.href = res.data.redirectUrl || '/';
        } else {
            showToast(res.message || 'Şube değiştirilemedi', 'error');
        }
    });
}

async function initShowcaseAndBilling() {
    if (!document.querySelector('meta[name="_csrf"]')) return;
    if (window.location.pathname.startsWith('/login') || window.location.pathname.startsWith('/onboarding/wizard')) return;

    const pub = await api('GET', '/api/settings/public');
    if (pub.success && pub.data && pub.data.showcase === 'true') {
        window.GSCRM_SHOWCASE = true;
        const main = document.querySelector('.main-content');
        if (main && !document.getElementById('subscriptionBanner')) {
            const banner = document.createElement('div');
            banner.id = 'subscriptionBanner';
            banner.className = 'subscription-banner subscription-banner--warn';
            banner.innerHTML = 'Tanıtım sürümü — veriler örnektir. Tüm özellikler bu ortamda açık değildir.';
            main.insertBefore(banner, main.firstChild);
        }
        return;
    }
    await initSubscriptionBanner();
    await initBookingReadinessHint();
}

/**
 * "Randevu sayfan bos" uyarisi.
 *
 * Provisioning hizmet menusunu ekiyor ama personel eklemiyor; uzman yokken
 * /api/booking/staff bos donuyor ve isletmenin randevu linki ziyaretciye bos
 * gorunuyor. Uyari bilerek yalnizca panelde: ziyaretciye gosterilen mesaj notr
 * kalmali, eylem cagrisi isletme sahibine ait. Kontrol booking sayfasinin
 * kullandigi ucun aynisiyla yapiliyor ki iki taraf ayni gercegi gorsun.
 */
async function initBookingReadinessHint() {
    if (window.location.pathname !== '/dashboard') return;
    const main = document.querySelector('.main-content');
    if (!main || document.getElementById('bookingReadinessBanner')) return;

    const json = await api('GET', '/api/booking/staff');
    if (!json.success || !Array.isArray(json.data) || json.data.length) return;

    const banner = document.createElement('div');
    banner.id = 'bookingReadinessBanner';
    banner.className = 'subscription-banner subscription-banner--warn';
    banner.innerHTML = 'Randevu sayfanız henüz boş görünüyor: online randevu '
        + 'alabilmek için en az bir uzman eklemelisiniz. '
        + '<a href="/staff">Personel ekle</a>';
    main.insertBefore(banner, main.firstChild);
}

async function initSubscriptionBanner() {
    if (!document.querySelector('meta[name="_csrf"]')) return;
    if (window.location.pathname.startsWith('/login') || window.location.pathname.startsWith('/onboarding/wizard')) return;

    const json = await api('GET', '/api/billing/status');
    if (!json.success || !json.data) return;

    const d = json.data;
    window.GSCRM_READ_ONLY = !!d.readOnly;

    let message = null;
    let cls = 'subscription-banner--warn';
    if (d.readOnly) {
        message = 'Deneme süreniz doldu. Kesintisiz kullanım için bizimle iletişime geçin.';
        cls = 'subscription-banner--danger';
    } else if (d.status === 'TRIAL' && d.trialDaysRemaining != null && d.trialDaysRemaining <= 7) {
        message = `Deneme süreniz ${d.trialDaysRemaining} gün içinde bitiyor. Devam etmek için bizimle iletişime geçin.`;
    }
    if (!message) return;

    const main = document.querySelector('.main-content');
    if (!main || document.getElementById('subscriptionBanner')) return;

    const banner = document.createElement('div');
    banner.id = 'subscriptionBanner';
    banner.className = 'subscription-banner ' + cls;
    banner.innerHTML = message;
    main.insertBefore(banner, main.firstChild);
}

// ─── XSS koruması ───
// innerHTML'e giden her kullanıcı kaynaklı değer buradan geçmelidir. Müşteri adı
// gibi alanlar herkese açık randevu formundan gelir; kaçışsız gömülürse personel
// panelinde çalışan betiğe dönüşür.
function escapeHtml(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

console.log('%c💅 GSCRM — Güzellik Salonu CRM', 'font-size:16px;font-weight:bold;color:#9b59b6;');
