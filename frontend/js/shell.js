// Shared authenticated shell. Page modules continue to own all business actions.
const shellNavigation = [
    ['landing.html', 'Trang chủ'],
    ['bookings.html', 'Đặt phòng'], ['customers.html', 'Khách hàng'],
    ['services.html', 'Dịch vụ'], ['checkin.html', 'Nhận phòng'],
    ['checkout.html', 'Hóa đơn & trả phòng']
];
const adminNavigation=[['account.html','Tài khoản & quản trị'],['statistics.html','Thống kê'],['rooms.html','Quản lý phòng'],['customers.html?workspace=admin','Quản lý khách hàng'],['bookings.html','Quay lại vận hành']];
function isAdminWorkspace(user,page,search=''){
    return user.role==='admin' && (['account.html','statistics.html','rooms.html'].includes(page) || page==='customers.html' && new URLSearchParams(search).get('workspace')==='admin');
}
function sidebarIcon(symbol){
    const icon=document.createElementNS('http://www.w3.org/2000/svg','svg');
    icon.setAttribute('viewBox','0 0 24 24');icon.setAttribute('aria-hidden','true');
    const use=document.createElementNS('http://www.w3.org/2000/svg','use');use.setAttribute('href','../css/icons.svg#'+symbol);icon.append(use);return icon;
}
let shellInitialized = false, shellMenuOpen = false, shellMedia = null;
let modalStack = [];
const modalOrigins = new Map();

function focusableElements(root) {
    return Array.from(root.querySelectorAll('a[href],button,input:not([type="hidden"]),select,textarea,[tabindex]'))
        .filter(el => !el.disabled && !el.hidden && el.tabIndex >= 0 &&
            el.getAttribute('aria-disabled') !== 'true' && !el.closest('[inert]') && el.getClientRects().length);
}
function trapShellFocus(root, event) {
    if (event.key !== 'Tab' || event.ctrlKey || event.altKey || event.metaKey) return;
    const items = focusableElements(root), first = items[0], last = items.at(-1);
    if (!first) { event.preventDefault(); (root.querySelector('.modal-box') || root).focus(); return; }
    if (!root.contains(document.activeElement) || event.shiftKey && document.activeElement === first) {
        event.preventDefault(); (event.shiftKey ? last : first).focus();
    } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
}
function setShellMenu(open, restoreFocus = true) {
    const sidebar = document.getElementById('app-sidebar'), toggle = document.getElementById('navigation-toggle');
    if (!sidebar || !toggle) return;
    shellMenuOpen = !!open && !!shellMedia?.matches;
    document.body.classList.toggle('menu-open', shellMenuOpen);
    toggle.setAttribute('aria-expanded', String(shellMenuOpen));
    toggle.setAttribute('aria-label', shellMenuOpen ? 'Đóng menu' : 'Mở menu');
    toggle.textContent = shellMenuOpen ? 'Đóng menu' : 'Menu';
    sidebar.inert = !!shellMedia?.matches && !shellMenuOpen;
    sidebar.setAttribute('aria-hidden', String(sidebar.inert));
    sidebar.setAttribute('role', shellMenuOpen ? 'dialog' : 'navigation');
    if (shellMenuOpen) sidebar.setAttribute('aria-modal', 'true'); else sidebar.removeAttribute('aria-modal');
    document.getElementById('navigation-backdrop').hidden = !shellMenuOpen;
    document.getElementById('main-content').inert = shellMenuOpen;
    syncModalAccessibility();
    if (shellMenuOpen) document.getElementById('navigation-close').focus();
    else if (restoreFocus && shellMedia?.matches) toggle.focus();
}
function closeShellMenu() { if (shellMenuOpen) setShellMenu(false, false); }
function initializeShell(user) {
    if (!user || !['admin', 'staff'].includes(user.role)) return;
    const sidebar = document.getElementById('app-sidebar'), main = document.getElementById('main-content');
    if (!sidebar || !main) return;
    if (!shellInitialized) {
        shellInitialized = true; shellMedia = window.matchMedia('(max-width: 1199px)');
        const brand = textNode('div', '', 'sidebar-brand');
        const logo = textNode('div','','logo');
        const logoMark=textNode('span','','sidebar-logo-mark');logoMark.append(sidebarIcon('house'));
        logo.append(logoMark,textNode('span','Homestay','sidebar-logo-text'));
        const subtitle = textNode('p', 'Quản lý lưu trú', 'sidebar-subtitle');
        const close = actionButton('Đóng menu', 'btn btn-ghost navigation-close', () => setShellMenu(false));
        close.replaceChildren(sidebarIcon('close'));
        close.id = 'navigation-close'; close.setAttribute('aria-label', 'Đóng menu điều hướng');
        const brandDetails=document.createElement('details');brandDetails.className='sidebar-brand-details';
        const brandSummary=document.createElement('summary');brandSummary.append(logo);brandSummary.setAttribute('aria-label','Mở thông tin Homestay');
        const home=document.createElement('a');home.href='landing.html';home.className='sidebar-home-link';home.textContent='Về trang chủ ↗';
        brandDetails.append(brandSummary,subtitle,home);brand.append(brandDetails,close);
        const list = document.createElement('ul'); list.className = 'sidebar-links';
        const currentPage = window.location.pathname.split('/').pop();
        const adminWorkspace=isAdminWorkspace(user,currentPage,window.location.search);
        document.body.classList.toggle('admin-workspace',adminWorkspace);
        for (const [route, label, admin] of (adminWorkspace?adminNavigation:shellNavigation)) {
            const item = document.createElement('li'), link = document.createElement('a');
            link.href = route;
            const icon = document.createElementNS('http://www.w3.org/2000/svg','svg');
            icon.setAttribute('class','ui-icon'); icon.setAttribute('aria-hidden','true');
            const use = document.createElementNS('http://www.w3.org/2000/svg','use');
            const routePage=route.split('?')[0];
            use.setAttribute('href','../css/icons.svg#'+({'account.html':'users','landing.html':'house','statistics.html':'chart','bookings.html':'calendar','rooms.html':'bed','customers.html':'users','services.html':'bell','checkin.html':'arrow-right','checkout.html':'print'}[routePage]));
            icon.append(use); link.append(icon,textNode('span', label));
            if (admin) { link.setAttribute('data-admin-only', ''); link.hidden = user.role !== 'admin'; }
            if (routePage === currentPage) { link.className = 'active'; link.setAttribute('aria-current', 'page'); }
            link.addEventListener('click', closeShellMenu); item.append(link); list.append(item);
        }
        const footer = textNode('div', '', 'sidebar-footer');
        const logout = document.createElement('a'); logout.id = 'logout-btn'; logout.href = '#';
        logout.append(textNode('span', 'Đăng xuất')); footer.append(logout);
        const property = document.createElement('details');property.className='property-switcher';
        const propertySummary=document.createElement('summary');propertySummary.className='property-summary';
        const propertyCurrent=textNode('span','Tất cả cơ sở','property-current');propertyCurrent.id='property-current';
        const propertyCopy=textNode('span','','property-summary-copy');
        propertyCopy.append(textNode('span','Cơ sở','property-summary-label'),propertyCurrent);
        const propertyIcon=textNode('span','','property-summary-icon');propertyIcon.append(sidebarIcon('house'));
        propertySummary.append(propertyIcon,propertyCopy);
        const propertyLabel = textNode('label','Chọn cơ sở','sidebar-section-label');propertyLabel.htmlFor='property-select';
        const propertySelect = document.createElement('select');propertySelect.id='property-select';
        // The current database has one room inventory and no branch assignment.
        propertySelect.append(selectOption('all','Tất cả cơ sở'),selectOption('current','Cơ sở hiện tại'));
        try { propertySelect.value=localStorage.getItem('homestay-property') || 'all'; } catch (_) {}
        propertyCurrent.textContent=propertySelect.value==='current'?'Cơ sở hiện tại':'Tất cả cơ sở';
        propertySelect.addEventListener('change',()=>{
            propertyCurrent.textContent=propertySelect.value==='current'?'Cơ sở hiện tại':'Tất cả cơ sở';
            try {localStorage.setItem('homestay-property',propertySelect.value);} catch (_) {}
            document.querySelectorAll('[data-property-label]').forEach(el=>{el.textContent=propertySelect.value==='current'?'Cơ sở hiện tại':'Tất cả cơ sở';});
            document.dispatchEvent(new Event('propertychange'));
        });
        property.append(propertySummary,propertyLabel,propertySelect,textNode('p','Một cơ sở · Danh mục phòng hiện tại','property-caption'));
        sidebar.replaceChildren(brand, property, textNode('p',adminWorkspace?'KHU QUẢN TRỊ':'VẬN HÀNH LƯU TRÚ','sidebar-section-label'), list, footer);
        document.querySelectorAll('[data-property-label]').forEach(el=>{el.textContent=propertySelect.value==='current'?'Cơ sở hiện tại':'Tất cả cơ sở';});
        const toggle = actionButton('Menu', 'btn btn-secondary navigation-toggle', () => setShellMenu(!shellMenuOpen));
        toggle.id = 'navigation-toggle'; toggle.setAttribute('aria-label', 'Mở menu');
        toggle.setAttribute('aria-controls', 'app-sidebar');
        document.getElementById('account-controls').prepend(toggle);
        const backdrop = document.createElement('button'); backdrop.type = 'button';
        backdrop.id = 'navigation-backdrop'; backdrop.className = 'navigation-backdrop'; backdrop.hidden = true;
        backdrop.tabIndex = -1; backdrop.setAttribute('aria-label', 'Đóng menu điều hướng');
        backdrop.addEventListener('click', () => setShellMenu(false)); sidebar.after(backdrop);
        shellMedia.addEventListener('change', () => setShellMenu(false, false));
        document.addEventListener('keydown', event => {
            if (modalStack.length) {
                const top = modalStack.at(-1);
                // The existing confirmation already owns its Tab/Escape handling.
                if (top.classList.contains('confirmation-overlay')) return;
                if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); requestModalDismissal(top); }
                else trapShellFocus(top, event);
            } else if (shellMenuOpen) {
                if (event.key === 'Escape') { event.preventDefault(); setShellMenu(false); }
                else trapShellFocus(sidebar, event);
            }
        }, true);
        document.addEventListener('focusin', event => {
            const top = modalStack.at(-1);
            if (top && !top.contains(event.target)) focusDialog(top);
        });
        const observer = new MutationObserver(records => {
            if (records.some(r => r.type === 'attributes' && (r.target.classList?.contains('modal-overlay') ||
                    r.target === document.body || r.target.id === 'print-preview-controls') ||
                r.type === 'childList' && [...r.addedNodes, ...r.removedNodes].some(n => n.nodeType === 1 &&
                    (n.matches('.modal-overlay') || n.querySelector('.modal-overlay'))))) syncModalAccessibility();
        });
        observer.observe(document.body, {subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'hidden']});
        setShellMenu(false, false);
    }
    sidebar.querySelectorAll('[data-admin-only]').forEach(link => { link.hidden = user.role !== 'admin'; });
}

function prepareModalAccessibility(overlay, opener = document.activeElement) {
    if (!shellInitialized) return;
    closeShellMenu();
    if (!modalOrigins.has(overlay)) modalOrigins.set(overlay, {opener});
}
function focusDialog(overlay) {
    const box = overlay.querySelector('.modal-box') || overlay;
    const target = overlay.querySelector('[autofocus]') || focusableElements(overlay)[0] || box;
    target.focus({preventScroll: true});
}
function requestModalDismissal(overlay) {
    const close = overlay.querySelector('[data-modal-close]');
    if (!close || close.disabled || close.getAttribute('aria-disabled') === 'true') return false;
    close.click(); // The page's own close function retains its submission/business guards.
    return true;
}
function syncModalAccessibility() {
    if (!shellInitialized) return;
    const pending = document.body.classList.contains('session-pending');
    const printPreview = document.getElementById('print-preview-controls')?.hidden === false;
    const visible = pending ? [] : Array.from(document.querySelectorAll('.modal-overlay.show'))
        .filter(el => !(printPreview && el.id === 'checkout-modal'));
    const priorTop = modalStack.at(-1), closed = modalStack.filter(el => !visible.includes(el));
    modalStack = modalStack.filter(el => visible.includes(el));
    for (const overlay of visible) {
        if (!modalOrigins.has(overlay)) modalOrigins.set(overlay, {opener: document.activeElement});
        const title = overlay.querySelector('h3, h2');
        if (title && !title.id) title.id = (overlay.id || 'modal') + '-title';
        overlay.setAttribute('role', 'dialog');
        if (title) overlay.setAttribute('aria-labelledby', title.id);
        const box = overlay.querySelector('.modal-box'); if (box) box.tabIndex = -1;
        if (!modalStack.includes(overlay)) modalStack.push(overlay);
    }
    const top = modalStack.at(-1);
    document.querySelectorAll('.modal-overlay').forEach(el => {
        el.inert = !!top && el !== top;
        el.setAttribute('aria-hidden', String(el.inert));
        el.setAttribute('aria-modal', String(el === top));
    });
    const main = document.getElementById('main-content'), sidebar = document.getElementById('app-sidebar');
    main.inert = !!top || shellMenuOpen;
    main.setAttribute('aria-hidden', String(main.inert));
    sidebar.inert = !!top || !!shellMedia?.matches && !shellMenuOpen;
    sidebar.setAttribute('aria-hidden', String(sidebar.inert));
    const skip = document.querySelector('.skip-link');
    if (skip) { skip.inert = !!top || shellMenuOpen; skip.setAttribute('aria-hidden', String(skip.inert)); }
    document.body.classList.toggle('modal-open', !!top);
    if (top && (top !== priorTop || !top.contains(document.activeElement))) {
        const origin = closed.includes(priorTop) ? modalOrigins.get(priorTop)?.opener : null;
        if (origin && top.contains(origin) && !origin.disabled && origin.getClientRects().length) origin.focus({preventScroll: true});
        else focusDialog(top);
    }
    else if (!top && closed.length && !pending) {
        if (printPreview) document.querySelector('#print-preview-controls button')?.focus();
        else {
            const origin = modalOrigins.get(priorTop)?.opener;
            if (origin?.isConnected && !origin.closest('[inert]') && origin.getClientRects().length && !origin.disabled) origin.focus({preventScroll: true});
            else document.getElementById('main-content').focus({preventScroll: true});
        }
    }
    for (const overlay of closed) {
        if (!overlay.classList.contains('show') || !overlay.isConnected) modalOrigins.delete(overlay);
    }
}
