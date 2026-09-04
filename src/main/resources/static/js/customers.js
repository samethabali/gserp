/* ═══════════════════════════════════════════════════════════════
   GSCRM — Customers Page (customers.js)
   ═══════════════════════════════════════════════════════════════ */

let allCustomers = [];
let selectedCustomerId = null;

document.addEventListener('DOMContentLoaded', async () => {
    await loadCustomers();
    loadDuplicateWarning();

    // URL'de ?phone= varsa otomatik arama yap ve müşteriyi seç
    const phoneParam = new URLSearchParams(window.location.search).get('phone');
    if (phoneParam) {
        document.getElementById('customerSearch').value = phoneParam;
        searchCustomers(phoneParam);
        // Eşleşen tek müşteri varsa otomatik aç
        const match = allCustomers.find(c => c.phone === phoneParam);
        if (match) selectCustomer(match.id);
    }
});

async function loadCustomers(query) {
    const url = query ? `/api/customers?q=${encodeURIComponent(query)}` : '/api/customers';
    const json = await api('GET', url);
    allCustomers = json.data || [];
    renderList(allCustomers);
}

function searchCustomers(q) {
    const filtered = allCustomers.filter(c => {
        const lq = q.toLowerCase();
        return c.fullName.toLowerCase().includes(lq)
            || (c.phone && c.phone.includes(lq))
            || (c.email && c.email.toLowerCase().includes(lq));
    });
    renderList(filtered);
}

function renderList(customers) {
    const el = document.getElementById('customerList');
    if (!customers.length) {
        el.innerHTML = '<div class="empty-state"><div class="empty-icon">👥</div><p>Müşteri bulunamadı</p></div>';
        return;
    }
    el.innerHTML = customers.map(c => {
        const balanceHtml = c.balance && parseFloat(c.balance) !== 0
            ? `<span class="badge ${parseFloat(c.balance) > 0 ? 'badge-completed' : 'badge-cancelled'}" style="font-size:0.65rem;">
                ${parseFloat(c.balance) > 0 ? '💰 +' : '⚠️ '}${formatCurrency(c.balance)}
               </span>`
            : '';
        const isSelected = c.id === selectedCustomerId;
        return `
        <div class="glass-card" style="padding:14px;cursor:pointer;${isSelected ? 'border-color:var(--color-primary);' : ''}"
             onclick="selectCustomer(${c.id})">
            <div style="display:flex;justify-content:space-between;align-items:flex-start;">
                <div>
                    <div style="font-weight:600;font-size:0.95rem;">${escapeHtml(c.fullName)}</div>
                    <div style="font-size:0.78rem;color:var(--text-secondary);">📞 ${escapeHtml(c.phone) || '-'}</div>
                </div>
                <div style="text-align:right;font-size:0.75rem;color:var(--text-muted);">
                    ${c.totalAppointments} randevu<br>${balanceHtml}
                </div>
            </div>
        </div>`;
    }).join('');
}

async function selectCustomer(id) {
    selectedCustomerId = id;
    renderList(allCustomers);
    const json = await api('GET', `/api/customers/${id}`);
    if (json.data) renderDetail(json.data);
}

function renderDetail(c) {
    const balance = parseFloat(c.balance || 0);
    const balanceColor = balance > 0 ? 'var(--color-success)' : balance < 0 ? 'var(--color-danger)' : 'var(--text-muted)';
    const balanceLabel = balance > 0 ? '💰 Kredi' : balance < 0 ? '⚠️ Borç' : 'Bakiye';

    const pastRows = (c.pastAppointments || []).slice(0, 10).map(a => `
        <tr>
            <td style="padding:6px 10px;font-size:0.8rem;">${formatDate(a.startTime)}</td>
            <td style="padding:6px 10px;font-size:0.8rem;">${formatTime(a.startTime)}</td>
            <td style="padding:6px 10px;font-size:0.8rem;">${escapeHtml(a.serviceName)}</td>
            <td style="padding:6px 10px;font-size:0.8rem;">${escapeHtml(a.staffName)}</td>
            <td style="padding:6px 10px;font-size:0.8rem;">${formatCurrency(a.finalPrice)}</td>
            <td style="padding:6px 10px;">${statusBadge(a.status)}</td>
        </tr>`).join('');

    const upcomingRows = (c.upcomingAppointments || []).slice(0, 5).map(a => `
        <tr>
            <td style="padding:6px 10px;font-size:0.8rem;">${formatDate(a.startTime)}</td>
            <td style="padding:6px 10px;font-size:0.8rem;">${formatTime(a.startTime)}</td>
            <td style="padding:6px 10px;font-size:0.8rem;">${escapeHtml(a.serviceName)}</td>
            <td style="padding:6px 10px;font-size:0.8rem;">${escapeHtml(a.staffName)}</td>
            <td style="padding:6px 10px;">${statusBadge(a.status)}</td>
        </tr>`).join('');

    const paymentRows = (c.payments || []).slice(0, 10).map(p => `
        <tr>
            <td style="padding:6px 10px;font-size:0.8rem;">${formatDate(p.collectedAt)}</td>
            <td style="padding:6px 10px;font-size:0.8rem;">${formatCurrency(p.amount)}</td>
            <td style="padding:6px 10px;">
                <span class="badge ${p.method === 'CASH' ? 'badge-completed' : 'badge-in-progress'}">${p.method === 'CASH' ? '💵 Nakit' : '💳 Kart'}</span>
            </td>
            <td style="padding:6px 10px;">
                <span class="badge ${p.status === 'PAID' ? 'badge-completed' : p.status === 'DEFERRED' ? 'badge-cancelled' : 'badge-in-progress'}">
                    ${p.status === 'PAID' ? 'Ödendi' : p.status === 'DEFERRED' ? 'Veresiye' : 'Kısmi'}
                </span>
            </td>
        </tr>`).join('');

    const productSaleRows = (c.productSales || []).slice(0, 10).map(s => `
        <tr>
            <td style="padding:6px 10px;font-size:0.8rem;">${formatDate(s.soldAt.split('T')[0])}</td>
            <td style="padding:6px 10px;font-size:0.8rem;"><strong>${escapeHtml(s.productName)}</strong></td>
            <td style="padding:6px 10px;font-size:0.8rem;">${s.quantity} adet</td>
            <td style="padding:6px 10px;font-size:0.8rem;">${formatCurrency(s.unitPrice)}</td>
            <td style="padding:6px 10px;font-size:0.8rem;font-weight:600;">${formatCurrency(s.totalPrice)}</td>
        </tr>`).join('');

    document.getElementById('detailPane').innerHTML = `
        <div style="display:flex;flex-direction:column;gap:16px;">
            <!-- Başlık -->
            <div class="glass-card" style="padding:20px;">
                <div style="display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:12px;">
                    <div>
                        <h2 style="font-size:1.4rem;font-weight:700;margin-bottom:4px;">${escapeHtml(c.fullName)}</h2>
                        <div style="color:var(--text-secondary);font-size:0.85rem;">📞 ${escapeHtml(c.phone) || '-'} &nbsp;|&nbsp; ✉️ ${c.email || '-'}</div>
                        ${c.notes ? `<div style="margin-top:8px;font-size:0.82rem;color:var(--text-muted);">📝 ${escapeHtml(c.notes)}</div>` : ''}
                    </div>
                    <div style="text-align:right;">
                        <div style="font-size:0.72rem;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">${balanceLabel}</div>
                        <div style="font-size:1.5rem;font-weight:700;color:${balanceColor};">${formatCurrency(Math.abs(balance))}</div>
                        <div style="margin-top:8px;display:flex;gap:6px;justify-content:flex-end;">
                            <button class="btn btn-secondary btn-sm" onclick="editCustomer(${c.id})">✏️ Düzenle</button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Yaklaşan randevular -->
            ${upcomingRows ? `
            <div class="glass-card" style="padding:0;overflow:hidden;">
                <div style="padding:16px 20px;border-bottom:1px solid var(--border-glass);font-weight:600;">
                    📅 Yaklaşan Randevular (${c.upcomingAppointments?.length || 0})
                </div>
                <div style="overflow-x:auto;">
                    <table style="width:100%;border-collapse:collapse;">
                        <thead><tr style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;">
                            <th style="padding:8px 10px;text-align:left;">Tarih</th>
                            <th style="padding:8px 10px;text-align:left;">Saat</th>
                            <th style="padding:8px 10px;text-align:left;">Hizmet</th>
                            <th style="padding:8px 10px;text-align:left;">Uzman</th>
                            <th style="padding:8px 10px;text-align:left;">Durum</th>
                        </tr></thead>
                        <tbody>${upcomingRows}</tbody>
                    </table>
                </div>
            </div>` : ''}

            <!-- Geçmiş işlemler -->
            <div class="glass-card" style="padding:0;overflow:hidden;">
                <div style="padding:16px 20px;border-bottom:1px solid var(--border-glass);font-weight:600;">
                    📜 Geçmiş İşlemler (${c.pastAppointments?.length || 0})
                </div>
                ${pastRows ? `<div style="overflow-x:auto;">
                    <table style="width:100%;border-collapse:collapse;">
                        <thead><tr style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;">
                            <th style="padding:8px 10px;text-align:left;">Tarih</th>
                            <th style="padding:8px 10px;text-align:left;">Saat</th>
                            <th style="padding:8px 10px;text-align:left;">Hizmet</th>
                            <th style="padding:8px 10px;text-align:left;">Uzman</th>
                            <th style="padding:8px 10px;text-align:left;">Ücret</th>
                            <th style="padding:8px 10px;text-align:left;">Durum</th>
                        </tr></thead>
                        <tbody>${pastRows}</tbody>
                    </table>
                </div>` : '<div style="padding:20px;color:var(--text-muted);font-size:0.85rem;">Geçmiş işlem yok</div>'}
            </div>

            <!-- Satın Alınan Ürünler -->
            ${productSaleRows ? `
            <div class="glass-card" style="padding:0;overflow:hidden;">
                <div style="padding:16px 20px;border-bottom:1px solid var(--border-glass);font-weight:600;">
                    📦 Satın Alınan Ürünler (${c.productSales?.length || 0})
                </div>
                <div style="overflow-x:auto;">
                    <table style="width:100%;border-collapse:collapse;">
                        <thead><tr style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;">
                            <th style="padding:8px 10px;text-align:left;">Tarih</th>
                            <th style="padding:8px 10px;text-align:left;">Ürün Adı</th>
                            <th style="padding:8px 10px;text-align:left;">Miktar</th>
                            <th style="padding:8px 10px;text-align:left;">Birim Fiyat</th>
                            <th style="padding:8px 10px;text-align:left;">Toplam Tutar</th>
                        </tr></thead>
                        <tbody>${productSaleRows}</tbody>
                    </table>
                </div>
            </div>` : ''}

            <!-- Ödeme geçmişi -->
            ${paymentRows ? `
            <div class="glass-card" style="padding:0;overflow:hidden;">
                <div style="padding:16px 20px;border-bottom:1px solid var(--border-glass);font-weight:600;">
                    💳 Ödeme Geçmişi (${c.payments?.length || 0})
                </div>
                <div style="overflow-x:auto;">
                    <table style="width:100%;border-collapse:collapse;">
                        <thead><tr style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;">
                            <th style="padding:8px 10px;text-align:left;">Tarih</th>
                            <th style="padding:8px 10px;text-align:left;">Tutar</th>
                            <th style="padding:8px 10px;text-align:left;">Yöntem</th>
                            <th style="padding:8px 10px;text-align:left;">Durum</th>
                        </tr></thead>
                        <tbody>${paymentRows}</tbody>
                    </table>
                </div>
            </div>` : ''}

            <div class="glass-card" style="padding:0;overflow:hidden;">
                <div style="padding:16px 20px;border-bottom:1px solid var(--border-glass);font-weight:600;">
                    📍 Hareketler
                </div>
                <div id="customerActivity" style="padding:16px 20px;color:var(--text-muted);font-size:0.85rem;">Yükleniyor…</div>
            </div>
        </div>`;
    loadCustomerActivity(c.id);
}

async function loadCustomerActivity(customerId) {
    const el = document.getElementById('customerActivity');
    if (!el) return;
    const json = await api('GET', `/api/customers/${customerId}/activity?limit=40`);
    if (!json.success) {
        el.textContent = json.message || 'Hareketler yüklenemedi';
        return;
    }
    const rows = json.data || [];
    if (!rows.length) {
        el.textContent = 'Henüz hareket yok';
        return;
    }
    el.innerHTML = '<ul style="margin:0;padding-left:18px;">' + rows.map(e =>
        `<li style="margin-bottom:8px;"><strong>${escapeHtml(e.action)}</strong> — ${escapeHtml(e.summary || '')} <span style="color:var(--text-muted);font-size:0.8rem;">(${e.createdAt ? new Date(e.createdAt).toLocaleString('tr-TR') : ''} · ${escapeHtml(e.actorUsername || '')})</span></li>`
    ).join('') + '</ul>';
}

// ─── Müşteri Ekle/Düzenle ───
function openCustomerModal(c) {
    document.getElementById('editCustomerId').value = c ? c.id : '';
    document.getElementById('cstFirstName').value  = c ? c.firstName : '';
    document.getElementById('cstLastName').value   = c ? c.lastName || '' : '';
    document.getElementById('cstPhone').value      = c ? c.phone || '' : '';
    document.getElementById('cstEmail').value      = c ? c.email || '' : '';
    document.getElementById('cstNotes').value      = c ? c.notes || '' : '';
    document.getElementById('customerModalTitle').textContent = c ? 'Müşteri Düzenle' : 'Yeni Müşteri';
    openModal('customerModal');
}

async function editCustomer(id) {
    const json = await api('GET', `/api/customers/${id}`);
    if (json.data) openCustomerModal(json.data);
}

async function saveCustomer() {
    const id = document.getElementById('editCustomerId').value;
    const body = {
        firstName: document.getElementById('cstFirstName').value.trim(),
        lastName:  document.getElementById('cstLastName').value.trim(),
        phone:     document.getElementById('cstPhone').value.trim(),
        email:     document.getElementById('cstEmail').value.trim(),
        notes:     document.getElementById('cstNotes').value.trim(),
    };

    if (!body.firstName) {
        showToast('Ad alanı zorunludur', 'warning');
        return;
    }

    // Aynı telefonla kayıtlı başka müşteri var mı? Sunucu da 409 ile korur; buradaki
    // kontrol kullanıcıya kimle çakıştığını söyleyip bilinçli devam imkânı verir.
    if (body.phone) {
        const existing = await api('GET', `/api/customers/lookup?phone=${encodeURIComponent(body.phone)}`);
        const match = existing && existing.success ? existing.data : null;
        if (match && String(match.id) !== String(id)) {
            const proceed = confirm(
                `Bu telefonla kayıtlı müşteri zaten var: ${match.fullName}.

` +
                `Yine de ayrı bir kayıt olarak devam etmek istiyor musunuz?`);
            if (!proceed) return;
            body.allowDuplicate = true;
        }
    }

    const url    = id ? `/api/customers/${id}` : '/api/customers';
    const method = id ? 'PUT' : 'POST';
    const json   = await api(method, url, body);

    if (json.success) {
        showToast(json.message, 'success');
        closeModal('customerModal');
        await loadCustomers();
        loadDuplicateWarning();
        if (id) selectCustomer(parseInt(id));
    } else {
        showToast(json.message || 'Kayıt başarısız', 'error');
    }
}


// ─── Olası yinelenen müşteriler ───
//
// Telefon normalizasyonu (V30) aynı numaranın farklı yazımlarını görünür kıldı.
// Yinelenenler bilinçli olarak otomatik birleştirilmiyor: gerçek bir birleştirme
// randevu, tahsilat, ürün satışı ve rıza kayıtlarını taşıyıp bakiyeyi mutabık
// kılmayı gerektirir. Burada yalnızca tespit var; kararı salon veriyor.

let duplicateGroups = [];

async function loadDuplicateWarning() {
    const banner = document.getElementById('duplicateBanner');
    if (!banner) return;

    const json = await api('GET', '/api/customers/duplicates');
    duplicateGroups = (json && json.success && json.data) ? json.data : [];

    if (!duplicateGroups.length) {
        banner.style.display = 'none';
        banner.innerHTML = '';
        return;
    }

    banner.style.display = 'block';
    banner.innerHTML = `⚠️ ${duplicateGroups.length} olası yinelenen müşteri
        <a href="#" onclick="showDuplicates(); return false;" style="margin-left:6px;">Görüntüle</a>`;
}

function showDuplicates() {
    const body = duplicateGroups.map(group => {
        const rows = (group.members || []).map(m => `
            <div style="display:flex;justify-content:space-between;gap:10px;padding:6px 0;border-bottom:1px solid var(--border-glass);">
                <span>${escapeHtml(m.firstName || '')} ${escapeHtml(m.lastName || '')}</span>
                <span style="color:var(--text-muted);">${escapeHtml(m.phone || '')}</span>
                <a href="#" onclick="selectCustomer(${m.id}); closeModal('duplicateModal'); return false;">Aç</a>
            </div>`).join('');
        return `<div style="margin-bottom:16px;">
                    <div style="font-weight:600;margin-bottom:4px;">${escapeHtml(group.normalizedPhone)}</div>
                    ${rows}
                </div>`;
    }).join('');

    const el = document.getElementById('duplicateModalBody');
    if (el) {
        el.innerHTML = body + `<p style="color:var(--text-muted);font-size:0.82rem;margin-top:8px;">
            Kayıtları tek tek açıp düzeltebilirsiniz. Otomatik birleştirme yakında.</p>`;
    }
    openModal('duplicateModal');
}
