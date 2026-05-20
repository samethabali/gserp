/* ═══════════════════════════════════════════════════════════════
   GSERP — Products Page (products.js)
   ═══════════════════════════════════════════════════════════════ */

let allProducts = [];
let customerSearchTimeout = null;

document.addEventListener('DOMContentLoaded', () => {
    // Varsayılan tarihleri ayarla (Son 30 gün)
    const now = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(now.getDate() - 30);

    document.getElementById('saleDateFrom').value = thirtyDaysAgo.toISOString().split('T')[0];
    document.getElementById('saleDateTo').value = now.toISOString().split('T')[0];

    loadProducts();
    initCustomerAutocomplete();
});

function switchTab(tab) {
    document.getElementById('paneProducts').style.display = tab === 'products' ? 'block' : 'none';
    document.getElementById('paneSales').style.display    = tab === 'sales'    ? 'block' : 'none';
    document.getElementById('tabProducts').className = tab === 'products' ? 'btn btn-secondary' : 'btn btn-ghost';
    document.getElementById('tabSales').className    = tab === 'sales'    ? 'btn btn-secondary' : 'btn btn-ghost';
    if (tab === 'sales') loadSales();
}

async function loadProducts() {
    const json  = await api('GET', '/api/products');
    allProducts = json.data || [];
    populateCategories();
    filterProducts();

    const low = allProducts.filter(p => p.active && p.stockQuantity <= p.lowStockThreshold);
    const alert = document.getElementById('lowStockAlert');
    if (low.length) {
        alert.style.display = 'block';
        alert.innerHTML = `
            <div style="background:rgba(243,156,18,0.12);border:1px solid rgba(243,156,18,0.3);
                        border-radius:10px;padding:12px 16px;font-size:0.85rem;">
                ⚠️ <strong>${low.length} ürün</strong> düşük stokta: ${low.map(p => p.name).join(', ')}
            </div>`;
    } else {
        alert.style.display = 'none';
    }

    // Topbar kritik stok butonu
    const btn = document.getElementById('lowStockFilterBtn');
    const cnt = document.getElementById('lowStockCount');
    if (btn && cnt) {
        cnt.textContent = low.length;
        btn.style.display = low.length > 0 ? 'inline-flex' : 'none';
    }
}

let showingLowStock = false;
function toggleLowStockFilter() {
    showingLowStock = !showingLowStock;
    const btn = document.getElementById('lowStockFilterBtn');
    if (showingLowStock) {
        const low = allProducts.filter(p => p.active && p.stockQuantity <= p.lowStockThreshold);
        renderProducts(low);
        if (btn) btn.style.outline = '2px solid #e74c3c';
    } else {
        filterProducts();
        if (btn) btn.style.outline = 'none';
    }
}

function populateCategories() {
    const select = document.getElementById('prdCategoryFilter');
    if (!select) return;
    const currentVal = select.value;
    const categories = [...new Set(allProducts.map(p => p.category).filter(Boolean))].sort();
    select.innerHTML = '<option value="">📁 Tüm Kategoriler</option>' +
        categories.map(cat => `<option value="${cat.replace(/"/g, '&quot;')}">${cat}</option>`).join('');
    select.value = categories.includes(currentVal) ? currentVal : "";
}

function filterProducts() {
    const searchVal = document.getElementById('prdSearchInput').value.toLowerCase().trim();
    const catVal = document.getElementById('prdCategoryFilter').value;
    const stockVal = document.getElementById('prdStockFilter').value;

    const filtered = allProducts.filter(p => {
        if (searchVal && !p.name.toLowerCase().includes(searchVal) && !(p.category && p.category.toLowerCase().includes(searchVal))) {
            return false;
        }
        if (catVal && p.category !== catVal) {
            return false;
        }
        if (stockVal) {
            if (stockVal === 'out_of_stock' && p.stockQuantity > 0) return false;
            if (stockVal === 'low_stock' && (p.stockQuantity <= 0 || p.stockQuantity > p.lowStockThreshold)) return false;
            if (stockVal === 'in_stock' && p.stockQuantity <= p.lowStockThreshold) return false;
        }
        return true;
    });

    renderProducts(filtered);
}

function clearProductFilters() {
    document.getElementById('prdSearchInput').value = '';
    document.getElementById('prdCategoryFilter').value = '';
    document.getElementById('prdStockFilter').value = '';
    filterProducts();
}

function renderProducts(list) {
    const productsToRender = list || allProducts;
    const tbody = document.getElementById('productTableBody');
    if (!productsToRender.length) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--text-muted);">Ürün bulunamadı</td></tr>';
        return;
    }
    tbody.innerHTML = productsToRender.map(p => {
        const stockColor = p.stockQuantity <= p.lowStockThreshold
            ? 'color:var(--color-danger);font-weight:600;'
            : p.stockQuantity <= p.lowStockThreshold * 2 ? 'color:var(--color-warning);font-weight:600;' : '';
        return `
        <tr>
            <td><strong>${p.name}</strong></td>
            <td style="color:var(--text-secondary);">${p.category || '-'}</td>
            <td>${formatCurrency(p.costPrice)}</td>
            <td style="font-weight:600;">${formatCurrency(p.price)}</td>
            <td style="${stockColor}">${p.stockQuantity} adet</td>
            <td><span class="badge ${p.active ? 'badge-completed' : 'badge-cancelled'}">${p.active ? 'Aktif' : 'Pasif'}</span></td>
            <td style="display:flex;gap:4px;flex-wrap:wrap;">
                <button class="btn btn-xs btn-success" onclick="openSellModal(${p.id},'${p.name.replace(/'/g,'&#39;')}')">🛒 Sat</button>
                <button class="btn btn-xs btn-secondary" onclick="openRestockModal(${p.id},'${p.name.replace(/'/g,'&#39;')}')">📥 Stok</button>
                <button class="btn btn-xs btn-secondary" onclick="editProduct(${p.id})">✏️</button>
            </td>
        </tr>`;
    }).join('');
}

async function loadSales() {
    const from = document.getElementById('saleDateFrom').value;
    const to = document.getElementById('saleDateTo').value;

    const [salesJson, statsJson] = await Promise.all([
        api('GET', `/api/products/sales?from=${from}&to=${to}`),
        api('GET', `/api/products/sales/stats?from=${from}&to=${to}`)
    ]);

    renderSalesTable(salesJson.data || []);
    renderSalesStats(statsJson.data || {});
}

function renderSalesTable(sales) {
    const tbody = document.getElementById('saleTableBody');
    if (!sales.length) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:40px;color:var(--text-muted);">Bu tarihler arasında satış bulunamadı</td></tr>';
        return;
    }

    tbody.innerHTML = sales.map(s => {
        const product = allProducts.find(p => p.id === s.productId);
        const costPrice = product && product.costPrice ? product.costPrice : 0;
        const profit = Number(s.totalPrice) - (costPrice * s.quantity);
        const profitColor = profit >= 0 ? 'color:var(--color-success);' : 'color:var(--color-danger);';

        return `
        <tr>
            <td>${formatDate(s.soldAt.split('T')[0])} ${formatTime(s.soldAt)}</td>
            <td><strong>${product ? product.name : 'Ürün ID: ' + s.productId}</strong></td>
            <td><span class="badge badge-scheduled">${product && product.category ? product.category : 'Diğer'}</span></td>
            <td>${s.quantity} adet</td>
            <td>${formatCurrency(s.unitPrice)}</td>
            <td style="font-weight:600;">${formatCurrency(s.totalPrice)}</td>
            <td style="font-weight:600;${profitColor}">${formatCurrency(profit)}</td>
            <td style="color:var(--text-secondary);">${s.customerName || '-'}</td>
        </tr>`;
    }).join('');
}

function renderSalesStats(stats) {
    const container = document.getElementById('salesStatsContainer');
    if (!container) return;

    const totalRevenue = stats.totalRevenue || 0;
    const totalProfit = stats.totalProfit || 0;
    const totalSalesCount = stats.totalSalesCount || 0;

    const topQtyName = stats.topByQuantity && stats.topByQuantity.length > 0
        ? `${stats.topByQuantity[0].productName} (${stats.topByQuantity[0].quantitySold} adet)`
        : '-';

    const topProfName = stats.topByProfit && stats.topByProfit.length > 0
        ? `${stats.topByProfit[0].productName} (${formatCurrency(stats.topByProfit[0].totalProfit)})`
        : '-';

    container.innerHTML = `
        <div class="kpi-card purple">
            <div class="kpi-label">🛍️ Toplam Adet</div>
            <div class="kpi-value">${totalSalesCount}</div>
        </div>
        <div class="kpi-card blue">
            <div class="kpi-label">💰 Toplam Satış (Ciro)</div>
            <div class="kpi-value">${formatCurrency(totalRevenue)}</div>
        </div>
        <div class="kpi-card green">
            <div class="kpi-label">📈 Toplam Net Kâr</div>
            <div class="kpi-value">${formatCurrency(totalProfit)}</div>
        </div>
        <div class="kpi-card gold">
            <div class="kpi-label">🔥 En Çok Satan</div>
            <div class="kpi-value" style="font-size: 0.95rem; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${topQtyName}">${topQtyName}</div>
        </div>
        <div class="kpi-card red">
            <div class="kpi-label">💎 En Çok Kazandıran</div>
            <div class="kpi-value" style="font-size: 0.95rem; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${topProfName}">${topProfName}</div>
        </div>
    `;
}

// ─── Ürün CRUD ───
function openProductModal(p) {
    document.getElementById('editProductId').value  = p ? p.id : '';
    document.getElementById('prdName').value        = p ? p.name : '';
    document.getElementById('prdCategory').value    = p ? (p.category || '') : '';
    document.getElementById('prdCostPrice').value   = p ? (p.costPrice || 0) : '';
    document.getElementById('prdPrice').value       = p ? p.price : '';
    document.getElementById('prdStock').value       = p ? p.stockQuantity : 0;
    document.getElementById('prdThreshold').value   = p ? p.lowStockThreshold : 5;
    document.getElementById('productModalTitle').textContent = p ? 'Ürün Düzenle' : 'Yeni Ürün';
    openModal('productModal');
}

function editProduct(id) {
    const p = allProducts.find(x => x.id === id);
    if (p) openProductModal(p);
}

async function saveProduct() {
    const id = document.getElementById('editProductId').value;
    const body = {
        name:              document.getElementById('prdName').value.trim(),
        category:          document.getElementById('prdCategory').value.trim() || null,
        costPrice:         parseFloat(document.getElementById('prdCostPrice').value || 0),
        price:             parseFloat(document.getElementById('prdPrice').value),
        stockQuantity:     parseInt(document.getElementById('prdStock').value),
        lowStockThreshold: parseInt(document.getElementById('prdThreshold').value),
        active: true,
    };
    if (!body.name || isNaN(body.price)) { showToast('Ad ve satış fiyatı zorunludur', 'warning'); return; }
    const url    = id ? `/api/products/${id}` : '/api/products';
    const method = id ? 'PUT' : 'POST';
    const json   = await api(method, url, body);
    if (json.success) { showToast(json.message, 'success'); closeModal('productModal'); loadProducts(); }
    else showToast(json.message || 'Kayıt başarısız', 'error');
}

// ─── Müşteri Otomatik Tamamlama (Autocomplete) ───
function initCustomerAutocomplete() {
    const searchInput = document.getElementById('sellCustomerSearch');
    const dropdown = document.getElementById('sellCustomerSuggestions');
    if (!searchInput || !dropdown) return;

    searchInput.addEventListener('input', () => {
        clearTimeout(customerSearchTimeout);
        const query = searchInput.value.trim();
        if (query.length < 2) {
            dropdown.style.display = 'none';
            dropdown.innerHTML = '';
            return;
        }

        customerSearchTimeout = setTimeout(async () => {
            const json = await api('GET', `/api/customers?q=${encodeURIComponent(query)}`);
            const customers = json.data || [];
            if (!customers.length) {
                dropdown.style.display = 'none';
                return;
            }

            dropdown.innerHTML = customers.map(c => `
                <div class="autocomplete-item" style="padding:8px 12px; cursor:pointer; font-size:0.85rem; border-bottom:1px solid rgba(255,255,255,0.05); color:var(--text-secondary);"
                     onclick="selectCustomerForSell(${c.id}, '${c.fullName.replace(/'/g, "\\'")}', '${c.phone || ''}')">
                    <strong>${c.fullName}</strong> ${c.phone ? `<span style="font-size:0.75rem; color:var(--text-muted);">(${c.phone})</span>` : ''}
                </div>
            `).join('');
            dropdown.style.display = 'block';
        }, 300);
    });

    // Dropdown dışına tıklandığında kapat
    document.addEventListener('click', (e) => {
        if (!dropdown.contains(e.target) && e.target !== searchInput) {
            dropdown.style.display = 'none';
        }
    });
}

function selectCustomerForSell(id, name, phone) {
    document.getElementById('sellCustomerId').value = id;
    document.getElementById('sellCustomerSearch').value = name;
    document.getElementById('sellCustomerName').value = name;
    document.getElementById('sellCustomerPhone').value = phone;
    document.getElementById('sellCustomerSuggestions').style.display = 'none';
    showToast(`Müşteri seçildi: ${name}`, 'info');
}

// ─── Satış ───
function openSellModal(id, name) {
    document.getElementById('sellProductId').value  = id;
    document.getElementById('sellProductName').textContent = name;
    document.getElementById('sellQty').value        = 1;
    document.getElementById('sellCustomerId').value = '';
    document.getElementById('sellCustomerSearch').value = '';
    document.getElementById('sellCustomerName').value = '';
    document.getElementById('sellCustomerPhone').value = '';
    document.getElementById('sellCustomerSuggestions').style.display = 'none';
    openModal('sellModal');
}

async function confirmSell() {
    const productId    = parseInt(document.getElementById('sellProductId').value);
    const quantity     = parseInt(document.getElementById('sellQty').value);
    const customerId   = document.getElementById('sellCustomerId').value ? parseInt(document.getElementById('sellCustomerId').value) : null;
    const customerName = document.getElementById('sellCustomerName').value.trim() || null;
    const customerPhone= document.getElementById('sellCustomerPhone').value.trim() || null;

    if (!quantity || quantity < 1) { showToast('Geçerli adet girin', 'warning'); return; }
    if (!customerName) { showToast('Müşteri adı zorunludur', 'warning'); return; }

    const json = await api('POST', `/api/products/${productId}/sell`, { quantity, customerId, customerName, customerPhone });
    if (json.success) { showToast('Satış kaydedildi', 'success'); closeModal('sellModal'); loadProducts(); }
    else showToast(json.message || 'Satış başarısız', 'error');
}

// ─── Stok Güncelle ───
function openRestockModal(id, name) {
    document.getElementById('restockProductId').value = id;
    document.getElementById('restockProductName').textContent = name;
    document.getElementById('restockQty').value = 10;
    openModal('restockModal');
}

async function confirmRestock() {
    const productId = parseInt(document.getElementById('restockProductId').value);
    const quantity  = parseInt(document.getElementById('restockQty').value);
    if (!quantity || quantity < 1) { showToast('Geçerli adet girin', 'warning'); return; }
    const json = await api('PUT', `/api/products/${productId}/restock`, { quantity });
    if (json.success) { showToast('Stok güncellendi', 'success'); closeModal('restockModal'); loadProducts(); }
    else showToast(json.message || 'Güncelleme başarısız', 'error');
}

