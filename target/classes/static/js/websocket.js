/* ═══════════════════════════════════════════════════════════════
   GSERP — WebSocket Client (websocket.js)
   STOMP over SockJS for real-time calendar updates
   ═══════════════════════════════════════════════════════════════ */

let stompClient = null;
let wsConnected = false;
let wsReconnectTimer = null;

function connectWebSocket() {
    try {
        const socket = new SockJS('/ws-calendar');
        stompClient = Stomp.over(socket);
        stompClient.debug = null; // Disable noisy debug logs

        stompClient.connect({}, function (frame) {
            wsConnected = true;
            updateWsStatus(true);
            console.log('🔗 WebSocket connected');

            // Subscribe to appointment changes
            stompClient.subscribe('/topic/appointments', function (message) {
                const payload = JSON.parse(message.body);
                console.log('📡 WS received:', payload.action);
                handleWsAppointmentChange(payload);
            });

            // Subscribe to dashboard refresh signals
            stompClient.subscribe('/topic/dashboard', function (message) {
                const payload = JSON.parse(message.body);
                if (payload.action === 'REFRESH' && typeof refreshDashboard === 'function') {
                    refreshDashboard();
                }
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

// Auto-connect when page loads
document.addEventListener('DOMContentLoaded', () => {
    connectWebSocket();
});
