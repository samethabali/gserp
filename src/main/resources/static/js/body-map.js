/* ═══════════════════════════════════════════════════════════════
   GSCRM — İnsan Vücudu Şablonu (body-map.js)

   Epilasyon randevularında işlem yapılacak bölge, listeden değil şablon
   üzerinden seçilir: "ön kol" gibi bir etiketin nereye denk geldiği
   müşteri için de uzman için de tartışmasız olsun diye.

   Bölge kodları com.gscrm.model.enums.BodyRegion ile birebir aynıdır;
   BodyRegionCatalogTest iki listeyi karşılaştırır.
   ═══════════════════════════════════════════════════════════════ */

/** Baştan ayağa — sunucudaki enum sırasıyla aynı. */
const BODY_REGIONS = [
    { code: 'UPPER_LIP',  label: 'Üst Dudak' },
    { code: 'CHIN',       label: 'Çene' },
    { code: 'FACE',       label: 'Yüz' },
    { code: 'NECK',       label: 'Boyun' },
    { code: 'NAPE',       label: 'Ense' },
    { code: 'UNDERARM',   label: 'Koltuk Altı' },
    { code: 'UPPER_ARM',  label: 'Üst Kol' },
    { code: 'FOREARM',    label: 'Ön Kol' },
    { code: 'HAND',       label: 'El' },
    { code: 'CHEST',      label: 'Göğüs' },
    { code: 'ABDOMEN',    label: 'Karın' },
    { code: 'UPPER_BACK', label: 'Sırt' },
    { code: 'LOWER_BACK', label: 'Bel' },
    { code: 'BIKINI',     label: 'Bikini' },
    { code: 'BUTTOCKS',   label: 'Kalça' },
    { code: 'THIGH',      label: 'Üst Bacak' },
    { code: 'LOWER_LEG',  label: 'Alt Bacak' },
    { code: 'FOOT',       label: 'Ayak' },
];

const BODY_REGION_LABELS = BODY_REGIONS.reduce((acc, r) => { acc[r.code] = r.label; return acc; }, {});

/**
 * Bölge çizimleri. Kol ve bacak iki görünümde de aynı kodu taşır — tek bir
 * "üst kol" bölgesi vardır, ön ve arka onun iki yüzüdür; birinden seçmek
 * diğerinde de işaretler.
 */
const BODY_SHAPES = {
    front: {
        FACE:      '<ellipse cx="90" cy="32" rx="19" ry="23"/>',
        UPPER_LIP: '<rect x="83" y="36" width="14" height="5" rx="2.5"/>',
        CHIN:      '<ellipse cx="90" cy="48" rx="9" ry="6"/>',
        NECK:      '<rect x="81" y="54" width="18" height="18" rx="5"/>',
        CHEST:     '<path d="M58,72 L122,72 C131,72 138,78 137,86 L128,124 L52,124 L43,86 C42,78 49,72 58,72 Z"/>',
        ABDOMEN:   '<path d="M52,124 L128,124 L122,172 L58,172 Z"/>',
        BIKINI:    '<path d="M58,172 L122,172 L117,200 L90,206 L63,200 Z"/>',
        UPPER_ARM: '<rect x="41" y="78" width="17" height="64" rx="8.5"/>'
                 + '<rect x="122" y="78" width="17" height="64" rx="8.5"/>',
        FOREARM:   '<rect x="42.5" y="140" width="14" height="58" rx="7"/>'
                 + '<rect x="123.5" y="140" width="14" height="58" rx="7"/>',
        HAND:      '<ellipse cx="49.5" cy="206" rx="8.5" ry="11"/><ellipse cx="130.5" cy="206" rx="8.5" ry="11"/>',
        UNDERARM:  '<ellipse cx="54" cy="84" rx="7" ry="8"/><ellipse cx="126" cy="84" rx="7" ry="8"/>',
        THIGH:     '<rect x="64" y="204" width="24" height="90" rx="12"/><rect x="92" y="204" width="24" height="90" rx="12"/>',
        LOWER_LEG: '<rect x="67" y="292" width="20" height="76" rx="10"/><rect x="93" y="292" width="20" height="76" rx="10"/>',
        FOOT:      '<ellipse cx="77" cy="376" rx="11" ry="10"/><ellipse cx="103" cy="376" rx="11" ry="10"/>',
    },
    back: {
        NAPE:       '<rect x="80" y="46" width="20" height="26" rx="5"/>',
        UPPER_BACK: '<path d="M58,72 L122,72 C131,72 138,78 137,86 L128,124 L52,124 L43,86 C42,78 49,72 58,72 Z"/>',
        LOWER_BACK: '<path d="M52,124 L128,124 L122,172 L58,172 Z"/>',
        BUTTOCKS:   '<path d="M58,172 L122,172 L117,202 L90,208 L63,202 Z"/>',
        UPPER_ARM:  '<rect x="41" y="78" width="17" height="64" rx="8.5"/>'
                  + '<rect x="122" y="78" width="17" height="64" rx="8.5"/>',
        FOREARM:    '<rect x="42.5" y="140" width="14" height="58" rx="7"/>'
                  + '<rect x="123.5" y="140" width="14" height="58" rx="7"/>',
        HAND:       '<ellipse cx="49.5" cy="206" rx="8.5" ry="11"/><ellipse cx="130.5" cy="206" rx="8.5" ry="11"/>',
        THIGH:      '<rect x="64" y="204" width="24" height="90" rx="12"/><rect x="92" y="204" width="24" height="90" rx="12"/>',
        LOWER_LEG:  '<rect x="67" y="292" width="20" height="76" rx="10"/><rect x="93" y="292" width="20" height="76" rx="10"/>',
        FOOT:       '<ellipse cx="77" cy="376" rx="11" ry="10"/><ellipse cx="103" cy="376" rx="11" ry="10"/>',
    },
};

/** Çizim sırası = tıklama önceliği: sonra çizilen üstte kalır. */
const BODY_DRAW_ORDER = {
    front: ['FACE', 'UPPER_LIP', 'CHIN', 'NECK', 'CHEST', 'ABDOMEN', 'BIKINI',
            'UPPER_ARM', 'FOREARM', 'HAND', 'UNDERARM', 'THIGH', 'LOWER_LEG', 'FOOT'],
    back:  ['UPPER_BACK', 'LOWER_BACK', 'BUTTOCKS', 'NAPE',
            'UPPER_ARM', 'FOREARM', 'HAND', 'THIGH', 'LOWER_LEG', 'FOOT'],
};

/** Arka görünümde yüz yok; kafa yalnızca siluetin tamamlanması için çizilir. */
const BACK_HEAD = '<ellipse class="bm-static" cx="90" cy="32" rx="19" ry="23"/>';

function bodyRegionLabel(code) {
    return BODY_REGION_LABELS[code] || code;
}

/** "Üst Dudak, Koltuk Altı" — özet satırları için. */
function bodyRegionLabels(codes) {
    return (codes || []).map(bodyRegionLabel).join(', ');
}

/**
 * Hizmet epilasyon mu?
 *
 * Öncelik kategoridedir; ad kontrolü, epilasyonu LASER dışında bir kategoriye
 * koymuş salonlarda şablonun yine de çıkması içindir.
 */
function isEpilationService(service) {
    if (!service) return false;
    if (service.category === 'LASER') return true;
    return /epilasyon|epilas|ağda|agda|lazer/i.test(service.name || '');
}

function buildBodySvg(view, caption) {
    const shapes = BODY_SHAPES[view];
    const groups = BODY_DRAW_ORDER[view].map(code => `
        <g class="bm-region" data-region="${code}" tabindex="0" role="button"
           aria-label="${bodyRegionLabel(code)}"><title>${bodyRegionLabel(code)}</title>${shapes[code]}</g>`).join('');

    return `<div class="bm-view">
        <svg viewBox="0 0 180 400" class="bm-figure" xmlns="http://www.w3.org/2000/svg"
             role="img" aria-label="Vücut şablonu — ${caption}">
            ${view === 'back' ? BACK_HEAD : ''}
            ${groups}
        </svg>
        <div class="bm-caption">${caption}</div>
    </div>`;
}

/**
 * Şablonu verilen kaba çizer.
 *
 * @param {HTMLElement} container
 * @param {{selected?: string[], readonly?: boolean, onChange?: (codes: string[]) => void}} options
 */
function renderBodyMap(container, options) {
    if (!container) return;
    const opts     = options || {};
    const readonly = !!opts.readonly;
    const selected = new Set((opts.selected || []).filter(c => BODY_REGION_LABELS[c]));

    container.classList.add('body-map');
    container.classList.toggle('body-map--readonly', readonly);
    // Dinleyiciler her çizimde yenilenen bu iç düğüme bağlanır; kaba bağlansaydı
    // ikinci çizimden sonra tıklama iki kez işlenir, yani hiç işlenmezdi.
    container.innerHTML = `
        <div class="bm-root">
            <div class="bm-figures">
                ${buildBodySvg('front', 'Ön')}
                ${buildBodySvg('back', 'Arka')}
            </div>
            <div class="bm-legend"></div>
        </div>`;

    const root   = container.querySelector('.bm-root');
    const legend = container.querySelector('.bm-legend');

    function paint() {
        container.querySelectorAll('.bm-region').forEach(g => {
            g.classList.toggle('is-selected', selected.has(g.dataset.region));
        });
        // Rozetler enum sırasında dizilir: liste her açılışta aynı okunur.
        const codes = BODY_REGIONS.filter(r => selected.has(r.code)).map(r => r.code);
        if (!codes.length) {
            legend.innerHTML = readonly
                ? '<span class="bm-empty">Bölge seçilmemiş</span>'
                : '<span class="bm-empty">Şablon üzerinden bölge seçin</span>';
        } else {
            legend.innerHTML = codes.map(code =>
                `<span class="bm-chip" data-region="${code}">${bodyRegionLabel(code)}${
                    readonly ? '' : '<button type="button" class="bm-chip-x" aria-label="Kaldır">✕</button>'}</span>`
            ).join('');
        }
        return codes;
    }

    function toggle(code) {
        if (selected.has(code)) selected.delete(code); else selected.add(code);
        const codes = paint();
        if (typeof opts.onChange === 'function') opts.onChange(codes);
    }

    if (!readonly) {
        root.addEventListener('click', e => {
            const chipX = e.target.closest('.bm-chip-x');
            if (chipX) { toggle(chipX.closest('.bm-chip').dataset.region); return; }
            const region = e.target.closest('.bm-region');
            if (region) toggle(region.dataset.region);
        });
        // Klavye: şablon salt fare ile kullanılabilir kalmasın.
        root.addEventListener('keydown', e => {
            if (e.key !== 'Enter' && e.key !== ' ') return;
            const region = e.target.closest('.bm-region');
            if (!region) return;
            e.preventDefault();
            toggle(region.dataset.region);
        });
    }

    paint();
    container.__bodyMapSelected = selected;
}

/** Kaptaki güncel seçim — enum sırasında. */
function getBodyMapSelection(container) {
    const selected = container && container.__bodyMapSelected;
    if (!selected) return [];
    return BODY_REGIONS.filter(r => selected.has(r.code)).map(r => r.code);
}
