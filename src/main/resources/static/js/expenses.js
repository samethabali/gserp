/* ═══════════════════════════════════════════════════════════════
   GSERP — Expenses Page (expenses.js)
   ═══════════════════════════════════════════════════════════════ */

const CAT_LABELS = {
    RENT: '🏠 Kira', UTILITIES: '⚡ Faturalar', SUPPLIES: '🧴 Malzeme',
    SALARY: '💼 Maaş', MAINTENANCE: '🔧 Bakım', OTHER: '📌 Diğer'
};
const CAT_COLORS = {
    RENT: 'purple', UTILITIES: 'blue', SUPPLIES: 'orange',
    SALARY: 'red', MAINTENANCE: 'gold', OTHER: 'green'
};

document.addEventListener('DOMContentLoaded', () => {
    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
    const today = now.toISOString().split('T')[0];
    document.getElementById('dateFrom').value = firstDay;
    document.getElementById('dateTo').value   = today;
    loadExpenses();
});

async function loadExpenses() {
    const from = document.getElementById('dateFrom').value;
    const to   = document.getElementById('dateTo').value;
    const [listRes, summaryRes] = await Promise.all([
        api('GET', `/api/expenses?from=${from}&to=${to}`),
        api('GET', `/api/expenses/summary?from=${from}&to=${to}`)
    ]);

    renderSummary(summaryRes.data || {});
    renderTable(listRes.data || []);
}

function renderSummary(summary) {
    const total = summary['TOTAL'] || 0;
    const container = document.getElementById('expenseSummary');
    const items = [
        { key: 'TOTAL', label: 'Toplam Gider', color: 'red', value: total },
        ...Object.entries(CAT_LABELS).map(([k, l]) => ({ key: k, label: l, color: CAT_COLORS[k], value: summary[k] || 0 }))
    ];
    container.innerHTML = items
        .filter(i => parseFloat(i.value) > 0 || i.key === 'TOTAL')
        .map(i => `
        <div class="kpi-card ${i.color}">
            <div class="kpi-label">${i.label}</div>
            <div class="kpi-value">${formatCurrency(i.value)}</div>
        </div>`).join('');
}

function renderTable(expenses) {
    const tbody = document.getElementById('expenseTableBody');
    if (!expenses.length) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:40px;color:var(--text-muted);">Bu dönemde gider kaydı yok</td></tr>';
        return;
    }
    tbody.innerHTML = expenses.map(e => `
        <tr>
            <td>${formatDate(e.expenseDate + 'T00:00:00')}</td>
            <td><strong>${e.description}</strong></td>
            <td><span class="badge badge-scheduled">${CAT_LABELS[e.category] || e.category}</span></td>
            <td style="font-weight:600;color:var(--color-danger);">${formatCurrency(e.amount)}</td>
            <td style="color:var(--text-muted);font-size:0.82rem;">${e.notes || '-'}</td>
            <td>
                <button class="btn btn-xs btn-secondary" onclick="editExpense(${e.id})">✏️</button>
                <button class="btn btn-xs btn-danger" onclick="deleteExpense(${e.id})">🗑️</button>
            </td>
        </tr>`).join('');
}

let expenseCache = [];
async function loadExpensesForEdit() {
    const from = document.getElementById('dateFrom').value;
    const to   = document.getElementById('dateTo').value;
    const res  = await api('GET', `/api/expenses?from=${from}&to=${to}`);
    expenseCache = res.data || [];
}

function openExpenseModal(e) {
    document.getElementById('editExpenseId').value = e ? e.id : '';
    document.getElementById('expDesc').value       = e ? e.description : '';
    document.getElementById('expAmount').value     = e ? e.amount : '';
    document.getElementById('expDate').value       = e ? e.expenseDate : todayISO();
    document.getElementById('expCategory').value   = e ? e.category : 'OTHER';
    document.getElementById('expNotes').value      = e ? (e.notes || '') : '';
    document.getElementById('expenseModalTitle').textContent = e ? 'Gider Düzenle' : 'Yeni Gider';
    openModal('expenseModal');
}

async function editExpense(id) {
    const res = await api('GET', `/api/expenses?from=2000-01-01&to=2099-12-31`);
    const e   = (res.data || []).find(x => x.id === id);
    if (e) openExpenseModal(e);
}

async function deleteExpense(id) {
    if (!confirm('Bu gideri silmek istediğinizden emin misiniz?')) return;
    const json = await api('DELETE', `/api/expenses/${id}`);
    if (json.success) { showToast('Gider silindi', 'success'); loadExpenses(); }
    else showToast(json.message || 'Silinemedi', 'error');
}

async function saveExpense() {
    const id = document.getElementById('editExpenseId').value;
    const body = {
        description:  document.getElementById('expDesc').value.trim(),
        amount:       parseFloat(document.getElementById('expAmount').value),
        expenseDate:  document.getElementById('expDate').value,
        category:     document.getElementById('expCategory').value,
        notes:        document.getElementById('expNotes').value.trim() || null,
    };
    if (!body.description || !body.amount || !body.expenseDate) {
        showToast('Açıklama, tutar ve tarih zorunludur', 'warning'); return;
    }
    const url    = id ? `/api/expenses/${id}` : '/api/expenses';
    const method = id ? 'PUT' : 'POST';
    const json   = await api(method, url, body);
    if (json.success) {
        showToast(json.message, 'success');
        closeModal('expenseModal');
        loadExpenses();
    } else {
        showToast(json.message || 'Kayıt başarısız', 'error');
    }
}
