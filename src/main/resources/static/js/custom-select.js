/* GSCRM — Custom select (dark dropdown). Inline styles: CSS dosyası eski olsa da çalışır. */
(function () {
    const MENU_STYLE = [
        'display:none',
        'max-height:260px',
        'overflow-y:auto',
        'background:#12121e',
        'color:#e8e8f0',
        'border:1px solid rgba(155,89,182,0.5)',
        'border-radius:12px',
        'box-shadow:0 12px 40px rgba(0,0,0,0.6)',
        'padding:4px',
        'z-index:2000'
    ].join(';');

    const OPTION_BASE = {
        display: 'block',
        width: '100%',
        padding: '10px 12px',
        textAlign: 'left',
        background: 'transparent',
        border: 'none',
        borderRadius: '8px',
        color: '#e8e8f0',
        fontFamily: 'Inter, sans-serif',
        fontSize: '14px',
        lineHeight: '1.35',
        cursor: 'pointer'
    };

    let globalListenersBound = false;

    function bindGlobalListeners() {
        if (globalListenersBound) return;
        globalListenersBound = true;
        document.addEventListener('click', () => closeAllMenus());
        document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeAllMenus(); });
        window.addEventListener('scroll', closeAllMenus, true);
        window.addEventListener('resize', closeAllMenus);
    }

    function closeAllMenus() {
        document.querySelectorAll('.custom-select.is-open').forEach((w) => w._closeMenu?.());
    }

    function positionMenu(trigger, menu) {
        const rect = trigger.getBoundingClientRect();
        const menuHeight = Math.min(menu.scrollHeight || 260, 260);
        const spaceBelow = window.innerHeight - rect.bottom;
        const openUp = spaceBelow < menuHeight + 12 && rect.top > menuHeight + 12;
        menu.style.position = 'fixed';
        menu.style.left = Math.max(8, rect.left) + 'px';
        menu.style.width = rect.width + 'px';
        if (openUp) {
            menu.style.top = 'auto';
            menu.style.bottom = (window.innerHeight - rect.top + 4) + 'px';
        } else {
            menu.style.top = (rect.bottom + 4) + 'px';
            menu.style.bottom = 'auto';
        }
    }

    function styleTrigger(trigger) {
        trigger.style.backgroundImage = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' fill='%239a9ab0' viewBox='0 0 16 16'%3E%3Cpath d='M8 11L3 6h10z'/%3E%3C/svg%3E\")";
        trigger.style.backgroundRepeat = 'no-repeat';
        trigger.style.backgroundPosition = 'right 12px center';
        trigger.style.paddingRight = '32px';
        trigger.style.textAlign = 'left';
        trigger.style.cursor = 'pointer';
        trigger.style.width = '100%';
    }

    function enhanceSelect(select) {
        if (!select || select.dataset.nativeSelect !== undefined) return;
        if (select.closest('.custom-select')) return;

        bindGlobalListeners();

        select.style.setProperty('display', 'none', 'important');
        select.classList.add('custom-select-native');
        select.tabIndex = -1;
        select.setAttribute('aria-hidden', 'true');

        const wrapper = document.createElement('div');
        wrapper.className = 'custom-select';
        if (select.style.width) wrapper.style.width = select.style.width;

        const parent = select.parentNode;
        parent.insertBefore(wrapper, select);
        wrapper.appendChild(select);

        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'custom-select-trigger';
        if (select.classList.contains('form-input')) trigger.classList.add('form-input');
        if (select.classList.contains('form-select')) trigger.classList.add('form-select');
        styleTrigger(trigger);

        const menu = document.createElement('div');
        menu.className = 'custom-select-menu';
        menu.setAttribute('role', 'listbox');
        menu.style.cssText = MENU_STYLE;

        wrapper.appendChild(trigger);
        wrapper.appendChild(menu);

        ['mousedown', 'pointerdown', 'click', 'focus', 'keydown'].forEach((evt) => {
            select.addEventListener(evt, (e) => {
                e.preventDefault();
                e.stopPropagation();
                trigger.focus();
            }, true);
        });

        function buildMenu() {
            menu.innerHTML = '';
            [...select.options].forEach((opt) => {
                const item = document.createElement('button');
                item.type = 'button';
                item.className = 'custom-select-option';
                item.setAttribute('role', 'option');
                item.textContent = opt.text;
                item.disabled = opt.disabled;
                Object.assign(item.style, OPTION_BASE);
                if (opt.value === select.value) {
                    item.classList.add('is-selected');
                    item.style.background = 'rgba(155, 89, 182, 0.38)';
                    item.style.color = '#c39bd3';
                    item.style.fontWeight = '600';
                }
                item.addEventListener('mouseenter', () => {
                    if (!item.disabled) {
                        item.style.background = 'rgba(155, 89, 182, 0.28)';
                        item.style.color = '#fff';
                    }
                });
                item.addEventListener('mouseleave', () => {
                    if (opt.value === select.value) {
                        item.style.background = 'rgba(155, 89, 182, 0.38)';
                        item.style.color = '#c39bd3';
                    } else {
                        item.style.background = 'transparent';
                        item.style.color = '#e8e8f0';
                    }
                });
                item.addEventListener('mousedown', (e) => e.preventDefault());
                item.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (opt.disabled) return;
                    select.value = opt.value;
                    select.dispatchEvent(new Event('change', { bubbles: true }));
                    updateTrigger();
                    closeMenu();
                });
                menu.appendChild(item);
            });
        }

        function updateTrigger() {
            const selected = select.options[select.selectedIndex];
            trigger.textContent = selected ? selected.text : 'Seçin…';
            trigger.disabled = select.disabled;
            wrapper.classList.toggle('is-disabled', select.disabled);
        }

        function closeMenu() {
            wrapper.classList.remove('is-open');
            trigger.setAttribute('aria-expanded', 'false');
            menu.style.display = 'none';
            menu.classList.remove('is-open');
            if (menu.parentNode === document.body) wrapper.appendChild(menu);
        }

        function openMenu() {
            if (select.disabled) return;
            closeAllMenus();
            buildMenu();
            document.body.appendChild(menu);
            menu.style.display = 'block';
            menu.classList.add('is-open');
            wrapper.classList.add('is-open');
            trigger.setAttribute('aria-expanded', 'true');
            positionMenu(trigger, menu);
            menu.querySelector('.is-selected')?.scrollIntoView({ block: 'nearest' });
        }

        wrapper._closeMenu = closeMenu;
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');

        trigger.addEventListener('mousedown', (e) => { e.preventDefault(); e.stopPropagation(); });
        trigger.addEventListener('click', (e) => {
            e.stopPropagation();
            if (wrapper.classList.contains('is-open')) closeMenu();
            else openMenu();
        });

        new MutationObserver(() => {
            updateTrigger();
            if (wrapper.classList.contains('is-open')) {
                buildMenu();
                positionMenu(trigger, menu);
            }
        }).observe(select, { childList: true, subtree: true, attributes: true, attributeFilter: ['disabled'] });

        select.addEventListener('change', updateTrigger);
        select._customSelectRefresh = updateTrigger;
        updateTrigger();
    }

    function initCustomSelects(root) {
        (root || document).querySelectorAll('select:not([data-native-select])').forEach((sel) => {
            if (!sel.closest('.custom-select')) enhanceSelect(sel);
        });
    }

    window.initCustomSelects = initCustomSelects;
    window.refreshCustomSelect = function (selectOrId) {
        const el = typeof selectOrId === 'string' ? document.getElementById(selectOrId) : selectOrId;
        if (el && el._customSelectRefresh) el._customSelectRefresh();
        else if (el && !el.closest('.custom-select')) enhanceSelect(el);
    };

    function boot() { initCustomSelects(); }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
    else boot();
    window.addEventListener('load', boot);

    new MutationObserver((mutations) => {
        mutations.forEach((m) => m.addedNodes.forEach((node) => {
            if (node.nodeType !== 1) return;
            if (node.matches && node.matches('select:not([data-native-select])')) enhanceSelect(node);
            node.querySelectorAll && node.querySelectorAll('select:not([data-native-select])').forEach(enhanceSelect);
        }));
    }).observe(document.documentElement, { childList: true, subtree: true });
})();
