/* ═══════════════════════════════════════════════════════════════
   GSCRM — WebSocket Client (websocket.js)
   STOMP over SockJS for real-time calendar updates
   ═══════════════════════════════════════════════════════════════ */

let stompClient = null;
let wsConnected = false;
let wsReconnectTimer = null;

// Yedek değer '1' idi: meta boş kaldığında sayfa sessizce 1 numaralı salonun
// kanalına bağlanıyordu. Kiracı bilinmiyorsa hiç bağlanmamak doğrusu.
function getSalonTopicPrefix() {
    const meta = document.querySelector('meta[name="salon-id"]');
    const salonId = meta && meta.content ? meta.content.trim() : '';
    return salonId ? '/topic/salon.' + salonId : null;
}

function connectWebSocket() {
    if (!getSalonTopicPrefix()) {
        console.warn('Salon bilinmiyor — canlı güncelleme devre dışı.');
        updateWsStatus(false);
        return;
    }
    try {
        const socket = new SockJS('/ws-calendar');
        stompClient = Stomp.over(socket);
        stompClient.debug = null; // Disable noisy debug logs

        stompClient.connect({}, function (frame) {
            wsConnected = true;
            updateWsStatus(true);
            console.log('🔗 WebSocket connected');

            const topicPrefix = getSalonTopicPrefix();

            // Subscribe to appointment changes
            stompClient.subscribe(topicPrefix + '.appointments', function (message) {
                const payload = JSON.parse(message.body);
                console.log('📡 WS received:', payload.action);
                handleWsAppointmentChange(payload);
            });

            // Subscribe to dashboard refresh signals
            stompClient.subscribe(topicPrefix + '.dashboard', function (message) {
                const payload = JSON.parse(message.body);
                if (payload.action === 'REFRESH' && typeof refreshDashboard === 'function') {
                    refreshDashboard();
                }
            });

            // Subscribe to general notifications (session reminders, etc.)
            stompClient.subscribe(topicPrefix + '.notifications', function (message) {
                const payload = JSON.parse(message.body);
                handleNotification(payload);
            });

        }, function (error) {
            wsConnected = false;
            updateWsStatus(false);
            console.warn('🔌 WebSocket disconnected, retrying in 5s...', error);
            scheduleReconnect();
        });
    } catch (e) {
        console.error('WebSocket connection error:', e);
        scheduleReconnect();
    }
}

function scheduleReconnect() {
    if (wsReconnectTimer) clearTimeout(wsReconnectTimer);
    wsReconnectTimer = setTimeout(() => connectWebSocket(), 5000);
}

function updateWsStatus(connected) {
    const el = document.getElementById('wsStatus');
    if (!el) return;
    if (connected) {
        el.classList.add('connected');
        el.querySelector('.ws-label').textContent = 'Bağlı';
    } else {
        el.classList.remove('connected');
        el.querySelector('.ws-label').textContent = 'Bağlantı kesildi';
    }
}

/**
 * Handle incoming WebSocket appointment change.
 * This function is called by the subscription callback
 * and delegates to calendar.js or dashboard.js depending on the page.
 */
function handleWsAppointmentChange(payload) {
    const { action, appointment } = payload;

    // If on calendar page, refresh calendar
    if (typeof refreshCalendar === 'function') {
        refreshCalendar();
    }

    // Show toast
    const actionLabels = {
        CREATE: '📅 Yeni randevu oluşturuldu',
        MOVE: '↔️ Randevu taşındı',
        UPDATE: '✏️ Randevu güncellendi',
        DELETE: '🗑️ Randevu silindi',
        STATUS_CHANGE: '🔄 Randevu durumu değişti'
    };

    if (appointment) {
        showToast(
            `${actionLabels[action] || action}: ${appointment.customerName || ''} — ${appointment.serviceName || ''}`,
            'info'
        );
    }
}

function handleNotification(payload) {
    const { type, message } = payload;

    if (type === 'SESSION_REMINDER') {
        showToast(message, 'warning');
        // Dashboard'daki seans panelini güncelle
        if (typeof loadSessionProgress === 'function') {
            loadSessionProgress();
        }
    }
}

// Auto-connect when page loads
document.addEventListener('DOMContentLoaded', () => {
    connectWebSocket();
});
