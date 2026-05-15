/* ═══════════════════════════════════════════════════════════════
   GSERP — Common Utilities (app.js)
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
    SCHEDULED: 'Bekliyor',
    IN_PROGRESS: 'Devam Ediyor',
    COMPLETED: 'Tamamlandı',
    CANCELLED: 'İptal',
    NO_SHOW: 'Gelmedi'
};

const statusBadgeClass = {
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
async function api(method, url, body) {
    const opts = {
        method,
        headers: { 'Content-Type': 'application/json' }
    };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);
    return res.json();
}

console.log('%c💅 GSERP — Güzellik Salonu ERP', 'font-size:16px;font-weight:bold;color:#9b59b6;');
