// Cookie sessions and CSRF. Set HOMESTAY_API_BASE before this file only for explicit dev CORS.
// NOTE API: Tiền tố URL backend; ví dụ apiGet("/rooms") sẽ gọi /api/rooms.
// Chỉ đặt HOMESTAY_API_BASE trước file này khi cấu hình frontend/backend khác địa chỉ trong môi trường dev.
const API_BASE = window.HOMESTAY_API_BASE || '/api';
let activePropertyId=(()=>{
    const requested=new URLSearchParams(window.location.search).get('property');
    let saved='';try{saved=localStorage.getItem('homestay-property') || '';}catch(_){}
    const raw=requested===null?saved:requested;
    return /^[1-9]\d*$/.test(raw) && Number.isSafeInteger(Number(raw))?Number(raw):null;
})();
function selectedPropertyId(){return activePropertyId;}
function setSelectedProperty(id){
    activePropertyId=id?Number(id):null;
    try{localStorage.setItem('homestay-property',activePropertyId?String(activePropertyId):'all');}catch(_){}
}
// NOTE API: Gắn propertyId đang chọn vào API đọc phòng, booking, hóa đơn và thống kê.
// Không sửa các endpoint khác, tránh gửi tham số cơ sở vào API tài khoản.
function propertyScopedEndpoint(endpoint){
    if(!activePropertyId || !/^\/(?:rooms(?:\?|$)|bookings(?:\?|$)|payments(?:\?|$)|statistics\/)/.test(endpoint))return endpoint;
    const [path,query='']=endpoint.split('?'),params=new URLSearchParams(query);params.set('propertyId',activePropertyId);
    return path+'?'+params;
}
function propertyRooms(rows){return activePropertyId?rows.filter(room=>room.propertyId===activePropertyId):rows;}
if(new URLSearchParams(window.location.search).has('property'))setSelectedProperty(activePropertyId);
function quoteNightly(base,pricing,start,end) {
    const money=n=>Number.isSafeInteger(n) && n>0 && n<=999999999999;
    const date=value=>{
        if(!/^\d{4}-\d{2}-\d{2}$/.test(value))throw new Error('Ngày lưu trú không hợp lệ.');
        const time=Date.parse(value+'T00:00:00Z');
        if(!Number.isFinite(time) || new Date(time).toISOString().slice(0,10)!==value)throw new Error('Ngày lưu trú không hợp lệ.');
        return time;
    };
    const first=date(start),last=date(end),nights=(last-first)/86400000;
    base=Number(base);
    if(!money(base) || !Number.isSafeInteger(nights) || nights<=0)throw new Error('Giá hoặc ngày lưu trú không hợp lệ.');
    let weekendNights=0,weekendPrice=base;
    if(pricing!=null) {
        weekendPrice=Number(pricing.weekendPrice);
        const days=pricing.weekendDays;
        if(!money(weekendPrice) || !Array.isArray(days) || !days.length || days.length>7 || new Set(days).size!==days.length || !days.every(d=>Number.isInteger(d)&&d>=1&&d<=7))throw new Error('Bảng giá cuối tuần không hợp lệ.');
        weekendNights=Math.floor(nights/7)*days.length;
        for(let i=0;i<nights%7;i++)if(days.includes((new Date(first+i*86400000).getUTCDay()+6)%7+1))weekendNights++;
    }
    const weekdayNights=nights-weekendNights,total=weekdayNights*base+weekendNights*weekendPrice;
    if(!money(total))throw new Error('Tiền phòng vượt giới hạn lưu trữ.');
    return {nights,weekdayNights,weekendNights,weekdayPrice:base,weekendPrice,total};
}
function weekendDaysLabel(days) {
    return days.map(d=>d===7?'Chủ nhật':'thứ '+(d+1)).join(', ');
}
function nightlyPriceLabel(room) {
    return room.nightlyPricing?'Ngày thường '+formatMoney(room.price)+' · Cuối tuần '+formatMoney(room.nightlyPricing.weekendPrice)+' / đêm ('+weekendDaysLabel(room.nightlyPricing.weekendDays)+')':formatMoney(room.price)+' / đêm';
}
function nightlyChargeLabel(quote,pricing) {
    if(!pricing)return quote.nights+' đêm × '+formatMoney(quote.weekdayPrice)+'/đêm';
    return [quote.weekdayNights?quote.weekdayNights+' đêm ngày thường × '+formatMoney(quote.weekdayPrice):'',quote.weekendNights?quote.weekendNights+' đêm cuối tuần × '+formatMoney(quote.weekendPrice):''].filter(Boolean).join(' + ');
}
function validNightlyInvoice(p) {
    try{const q=quoteNightly(p.pricePerNight,p.nightlyPricing,p.checkinDate,p.checkoutDate);return q.nights===p.nights && q.total===p.roomTotal;}catch{return false;}
}
function quoteHourly(rate, packages, hours) {
    const money=value=>Number.isSafeInteger(value) && value>0 && value<=999999999999;
    rate=Number(rate);
    if(!money(rate) || !Number.isSafeInteger(hours) || hours<=0)throw new Error('Giá giờ hoặc số giờ không hợp lệ.');
    let best={packageHours:0,packagePrice:0,extraHours:hours,rate,total:rate*hours};
    if(packages!=null) {
        const values=['twoHours','fourHours','eightHours'].map(key=>Number(packages[key]));
        if(!values.every(money) || values[0]>values[1] || values[1]>values[2])throw new Error('Bảng giá gói không hợp lệ.');
        best=null;
        [2,4,8].forEach((included,index)=>{
            const extra=Math.max(0,hours-included),total=values[index]+extra*rate;
            if(!best || total<best.total)best={packageHours:included,packagePrice:values[index],extraHours:extra,rate,total};
        });
    }
    if(!money(best.total))throw new Error('Tiền phòng vượt giới hạn lưu trữ.');
    return best;
}
function hourlyPriceLabel(room) {
    const p=room.hourlyPackages;
    if(p) return '2 giờ '+formatMoney(p.twoHours)+' · 4 giờ '+formatMoney(p.fourHours)+' · 8 giờ '+formatMoney(p.eightHours)+' · Giờ lẻ '+formatMoney(room.hourlyPrice)+'/giờ';
    return Number(room.hourlyPrice)>0?formatMoney(room.hourlyPrice)+' / giờ':'Chưa có giá theo giờ';
}
function hourlyChargeLabel(quote,hours) {
    return quote.packageHours ? hours+' giờ · Gói '+quote.packageHours+' giờ '+formatMoney(quote.packagePrice)+(quote.extraHours?' + '+quote.extraHours+' giờ lẻ × '+formatMoney(quote.rate):'')
        : hours+' giờ × '+formatMoney(quote.rate);
}
function validHourlyInvoice(p) {
    try {return quoteHourly(p.pricePerHour,p.hourlyPackages,p.billedHours).total===p.roomTotal;}
    catch {return false;}
}
// NOTE API: Lưu trạng thái phiên trong bộ nhớ và dùng chung yêu cầu đang chạy để tránh gọi xác thực nhiều lần.
// Cookie do server quản lý; authVersion giúp bỏ kết quả cũ nếu người dùng vừa đăng nhập/đăng xuất.
let authPromise = null;
let currentUser = null;
let csrfPromise = null;
let redirectingToLogin = false;
let authVersion = 0;
function validAccountPhone(value) { return /^0[0-9]{9}$/.test(value); }
function validAccountEmail(value) { return value.length<=254 && /^[a-z0-9](?:[a-z0-9._%+!-]*[a-z0-9])?@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/i.test(value) && !value.split('@')[0].includes('..'); }
function validAccountUsername(value) { return /^[A-Za-z0-9_.-]{6,50}$/.test(value); }
function validAccountPassword(value) {
    return value.length>=6 && value.length<=128 && /[A-Z]/.test(value) && /[a-z]/.test(value)
        && /[0-9]/.test(value) && /[\p{P}\p{S}]/u.test(value);
}
try { sessionStorage.removeItem('currentUser'); } catch { /* Storage is optional; the server owns the session. */ }

// NOTE API: Xóa dữ liệu xác thực và mã CSRF đang nhớ sau khi phiên thay đổi; lần gọi sau phải kiểm tra lại server.
function resetAuth() {
    authVersion++;
    currentUser = null;
    authPromise = null;
    csrfPromise = null;
}
// NOTE API: Chuyển về trang đăng nhập khi hết phiên, giữ URL hiện tại trong next để có thể quay lại.
function goToLogin() {
    if (!window.location.pathname.endsWith('/login.html') && !redirectingToLogin) {
        redirectingToLogin = true;
        const next=window.location.pathname+(window.location.search||'')+(window.location.hash||'');
        window.location.replace('login.html?next='+encodeURIComponent(next));
    }
}
// NOTE API: Đọc JSON phản hồi và chuyển HTTP lỗi hoặc success=false thành Error cho trang gọi xử lý.
// error.status và error.code giúp phân biệt hết phiên, thiếu quyền, sai CSRF hoặc xung đột dữ liệu.
async function responseJson(response) {
    let data;
    try { data = await response.json(); }
    catch {
        const error = new Error('Phản hồi không hợp lệ từ máy chủ.');
        error.status = response.ok ? 502 : response.status;
        throw error;
    }
    if (!response.ok) {
        const error = new Error(data.message || 'Không thể thực hiện yêu cầu.');
        error.status = response.status;
        error.code = data.code;
        throw error;
    }
    if (data && data.success === false) {
        const error = new Error(data.message || 'Không thể thực hiện yêu cầu.');
        error.status = 409; error.code = data.code; throw error;
    }
    return data;
}
// NOTE API: Gọi GET /auth/me kèm cookie để biết tài khoản và quyền thực sự từ server.
// force=true buộc tải lại; nếu hết phiên thì chuyển trang bảo vệ về đăng nhập, còn trang công khai vẫn xem được.
async function checkLogin(force = false) {
    if (force) { authVersion++; authPromise = null; csrfPromise = null; }
    if (authPromise && !force) return authPromise;
    const version = authVersion;
    authPromise = (async () => {
        try {
            const data = await responseJson(await fetch(API_BASE + '/auth/me', {
                credentials: 'include', cache: 'no-store'
            }));
            if (version !== authVersion) return null;
            if (!data.success || !data.user || !['admin','customer'].includes(data.user.role)) throw new Error('Không thể xác nhận tài khoản.');
            currentUser = data.user;
            return currentUser;
        } catch (error) {
            if (version !== authVersion) return null;
            currentUser = null;
            authPromise = null;
            if (error.status === 401) {
                if (!['/pages/landing.html','/pages/explore.html','/pages/policies.html','/pages/support.html','/pages/assistant.html'].includes(window.location.pathname)) goToLogin();
            }
            else showAlert(error.message || 'Không thể kiểm tra đăng nhập.', 'error');
            return null;
        }
    })();
    return authPromise;
}
// NOTE API: Gọi GET /auth/csrf để lấy token/headerName; dùng chung kết quả trong cùng phiên.
// Token này được gửi lại ở header X-CSRF-TOKEN của yêu cầu ghi dữ liệu nhằm chống giả mạo yêu cầu.
async function getCsrfToken() {
    if (!csrfPromise) {
        const version = authVersion;
        csrfPromise = (async () => {
            const data = await responseJson(await fetch(API_BASE + '/auth/csrf', {
                credentials: 'include', cache: 'no-store'
            }));
            if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
            if (!data.token || data.headerName !== 'X-CSRF-TOKEN') throw new Error('Không thể xác nhận phiên thao tác.');
            return data;
        })().catch(error => {
            if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
            csrfPromise = null;
            if (error.status === 401) { resetAuth(); goToLogin(); }
            throw error;
        });
    }
    return csrfPromise;
}
// NOTE API: Hàm gửi yêu cầu chung: kiểm tra phiên, gửi cookie, gắn CSRF cho phương thức khác GET và mã hóa JSON.
// Các API đăng nhập/đăng ký/khôi phục được phép gọi khi chưa đăng nhập nhưng yêu cầu ghi vẫn gửi CSRF.
// Kết quả đi qua responseJson; khi đổi tài khoản/mật khẩu thì làm mới trạng thái phiên trong bộ nhớ.
async function apiRequest(endpoint, method = 'GET', data) {
    const isLogin = ['/auth/login','/auth/register','/auth/forgot-password','/auth/reset-password'].includes(endpoint);
    if (!isLogin && !await checkLogin()) throw new Error('Vui lòng đăng nhập để tiếp tục.');
    const version = authVersion;
    // NOTE API: credentials=include gửi cookie phiên; no-store tránh dùng dữ liệu tài khoản cũ từ bộ nhớ đệm.
    const options = { method, credentials: 'include', cache: 'no-store', headers: {} };
    if (method !== 'GET') {
        const csrf = await getCsrfToken();
        if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
        options.headers[csrf.headerName] = csrf.token;
        if (data !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(data);
        }
    }
    try {
        const result = await responseJson(await fetch(API_BASE + endpoint, options));
        if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
        if (result.success && ['/auth/login','/auth/register','/auth/reset-password', '/auth/logout', '/auth/change-password'].includes(endpoint)) resetAuth();
        if (result.success && endpoint === '/auth/profile' && result.user) {
            currentUser = result.user;
            authPromise = Promise.resolve(currentUser);
        }
        return result;
    } catch (error) {
        if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
        // NOTE API: CSRF hết hiệu lực thì bỏ token cũ; HTTP 401 ở API được bảo vệ thì xóa trạng thái và yêu cầu đăng nhập lại.
        if (error.code === 'CSRF_INVALID') csrfPromise = null;
        if (error.status === 401 && !isLogin) { resetAuth(); goToLogin(); }
        // Do not retry writes automatically: a response can be lost after commit.
        throw error;
    }
}
// NOTE API: GET đọc dữ liệu; tự thêm bộ lọc cơ sở vào các danh sách có hỗ trợ.
function apiGet(endpoint) { return apiRequest(propertyScopedEndpoint(endpoint)); }
// NOTE API: POST gửi JSON để tạo dữ liệu hoặc thực hiện hành động như đăng nhập/thanh toán.
function apiPost(endpoint, data) { return apiRequest(endpoint, 'POST', data); }
// NOTE API: PUT gửi JSON để cập nhật dữ liệu hoặc chuyển trạng thái như nhận/hủy phòng.
function apiPut(endpoint, data) { return apiRequest(endpoint, 'PUT', data); }
// NOTE API: DELETE xóa bản ghi theo endpoint; server vẫn kiểm tra quyền và ràng buộc dữ liệu.
function apiDelete(endpoint) { return apiRequest(endpoint, 'DELETE'); }

let alertMessageVersion = 0;
function showAlert(message, type = 'success') {
    const el = document.getElementById('alert-message');
    if (!el) return;
    el.textContent = message;
    el.className = `alert alert-${type}`;
    el.style.display = 'block';
    const version = ++alertMessageVersion;
    setTimeout(() => { if (version === alertMessageVersion) el.style.display = 'none'; }, 5000);
}
function setPasswordVisibility(input, toggle, visible = false) {
    if (!input || !toggle) return;
    input.type = visible ? 'text' : 'password';
    toggle.textContent = visible ? 'Ẩn' : 'Hiện';
    toggle.setAttribute('aria-pressed', String(visible));
    toggle.setAttribute('aria-label', (visible ? 'Ẩn ' : 'Hiện ') + (toggle.dataset.passwordLabel || 'mật khẩu'));
}
function bindPasswordVisibility(input, toggle, label = 'mật khẩu') {
    if (!input || !toggle) return;
    toggle.dataset.passwordLabel = label;
    toggle.setAttribute('aria-controls', input.id);
    setPasswordVisibility(input, toggle);
    toggle.addEventListener('click', () => {
        if (!input.disabled && !toggle.disabled) setPasswordVisibility(input, toggle, input.type === 'password');
    });
}
function openModal(modalId) {
    const overlay = document.getElementById(modalId);
    if (typeof prepareModalAccessibility === 'function') prepareModalAccessibility(overlay);
    overlay.classList.add('show');
    if (typeof syncModalAccessibility === 'function') syncModalAccessibility();
}
function closeModal() {
    document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('show'));
    if (typeof syncModalAccessibility === 'function') syncModalAccessibility();
}
function formatMoney(amount) { return (typeof amount === 'bigint' ? amount : Number(amount)).toLocaleString('vi-VN') + ' VNĐ'; }
function vietnamNow() { return new Date(Date.now()+7*3600000).toISOString().slice(0,19); }
function bookingTime(booking,which) {
    const at=booking[which+'At'];
    if(booking.billingUnit==='Hour' && at) return at.slice(0,16).replace('T',' ')+' (giờ VN)';
    return formatDate(booking[which+'Date']);
}
function formatDate(dateStr) { return dateStr ? new Date(dateStr).toLocaleDateString('vi-VN') : '--'; }

// Dynamic data is text; action handlers close over data rather than inline code.
function textNode(tag, value, className) {
    const node = document.createElement(tag);
    node.textContent = String(value ?? '');
    if (className) node.className = className;
    return node;
}
function actionButton(label, className, handler) {
    const button = textNode('button', label, className);
    button.type = 'button'; button.addEventListener('click', handler); return button;
}
function appendTableRow(body, values, actions = null) {
    const row = document.createElement('tr');
    for (const value of values) {
        const cell = document.createElement('td');
        if (value instanceof Node) cell.appendChild(value);
        else cell.textContent = String(value ?? '');
        row.appendChild(cell);
    }
    if (actions !== null) {
        const cell = document.createElement('td');
        actions.forEach(button => cell.appendChild(button)); row.appendChild(cell);
    }
    body.appendChild(row); return row;
}
function tableState(id, columns, message) {
    const body = document.getElementById(id); if (!body) return;
    const pager = document.getElementById('pager-'+(id === 'checkout-tbody' ? 'stays' : id.replace(/-tbody$/,'')));
    if (pager) {pager.replaceChildren();pager.hidden = true;}
    body.replaceChildren();
    const row = appendTableRow(body, [message]);
    row.firstElementChild.colSpan = columns;
    row.firstElementChild.style.textAlign = 'center';
}
function expectList(data) {
    if (!Array.isArray(data)) throw new Error('Danh sách nhận được không hợp lệ.'); return data;
}
function selectOption(value, label, price) {
    const option = textNode('option', label); option.value = String(value);
    if (price !== undefined) option.dataset.price = String(price); return option;
}
function showRequestError(error, fallback) { showAlert(error.message || fallback, 'error'); }
function validText(value, label, max, required = true) {
    if (value.length > max || required && !value.trim()) throw new Error(`${label} trống hoặc vượt ${max} ký tự.`);
    return value;
}
function validMoney(value, label, minimum = 0) {
    const money = Number(value);
    if (String(value).trim() === '' || !Number.isSafeInteger(money) || money < minimum || money > 999999999999) {
        throw new Error(`${label} phải là số nguyên trong giới hạn cho phép.`);
    }
    return money;
}
function validEnum(value, allowed, label) {
    if (!allowed.includes(value)) throw new Error(`${label} không hợp lệ.`); return value;
}
function validBookingDates(checkin, checkout) {
    for (const value of [checkin, checkout]) {
        const date = new Date(value + 'T00:00:00Z');
        if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value) || value.startsWith('0000')
            || !Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== value) throw new Error('Ngày không hợp lệ.');
    }
    if (checkin < vietnamToday() || checkout <= checkin) throw new Error('Ngày nhận không trong quá khứ; ngày trả phải sau ngày nhận.');
}
function vietnamToday(offset = 0) {
    return new Date(Date.now() + 7 * 3600000 + offset * 86400000).toISOString().slice(0, 10);
}

let confirmationPending = false;
function askConfirmation(message, title = 'Xác nhận thao tác') {
    if (confirmationPending) return Promise.resolve(false);
    confirmationPending = true;
    const previousFocus = document.activeElement;
    const overlay = textNode('div','','modal-overlay show confirmation-overlay');
    overlay.setAttribute('role','dialog');overlay.setAttribute('aria-modal','true');
    overlay.setAttribute('aria-labelledby','confirmation-title');overlay.setAttribute('aria-describedby','confirmation-message');
    const box = textNode('div','','modal-box');
    const heading = textNode('h3',title);heading.id = 'confirmation-title';
    const description = textNode('p',message);description.id = 'confirmation-message';
    const footer = textNode('div','','modal-footer');
    return new Promise(resolve => {
        const finish = accepted => {
            overlay.remove();confirmationPending = false;
            if (previousFocus?.isConnected) previousFocus.focus();
            resolve(accepted);
        };
        const cancel = actionButton('Quay lại','btn btn-secondary',() => finish(false));
        const accept = actionButton('Đồng ý','btn btn-primary',() => finish(true));
        footer.append(cancel,accept);box.append(heading,description,footer);overlay.append(box);
        if (typeof prepareModalAccessibility === 'function') prepareModalAccessibility(overlay, previousFocus);
        document.body.append(overlay);
        if (typeof syncModalAccessibility === 'function') syncModalAccessibility();
        overlay.addEventListener('keydown',event => {
            if (event.key === 'Escape') {event.preventDefault();finish(false);}
            if (event.key === 'Tab') {event.preventDefault();(document.activeElement === cancel ? accept : cancel).focus();}
        });
        cancel.focus();
    });
}

const paginationStates = new Map();
function pageRows(key, rows, redraw, size = 20) {
    let state = paginationStates.get(key);
    if (!state || state.rows !== rows) { state = {rows,page:1};paginationStates.set(key,state); }
    const pages = Math.max(1,Math.ceil(rows.length/size));state.page = Math.min(state.page,pages);
    const nav = document.getElementById('pager-'+key);
    if (nav) {
        nav.replaceChildren();nav.hidden = rows.length <= size;
        const previous = actionButton('Trang trước','btn btn-secondary',() => {state.page--;redraw(rows);});previous.disabled = state.page <= 1;
        const next = actionButton('Trang sau','btn btn-secondary',() => {state.page++;redraw(rows);});next.disabled = state.page >= pages;
        nav.append(previous,textNode('span',`Trang ${state.page}/${pages} · ${rows.length} dòng`),next);
    }
    return rows.slice((state.page-1)*size,state.page*size);
}
function makeCsv(columns, rows) {
    const cell = (value, text = false) => {
        let content = String(value ?? '').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g,'');
        if (text || /^[\s\ufeff]*[=+\-@]/.test(content) || /^[\t\r\n]/.test(content)) content = "'"+content;
        return '"'+content.replace(/"/g,'""')+'"';
    };
    return '\ufeff'+[columns.map(c => cell(c.label)).join(','),...rows.map(row => columns.map(c => cell(c.value(row),c.text)).join(','))].join('\r\n')+'\r\n';
}
function downloadCsv(filename, columns, rows, ready = true) {
    if (!ready) {showAlert('Dữ liệu chưa tải xong hoặc đang lỗi. Hãy tải lại trước khi xuất CSV.','error');return false;}
    if (!rows.length) {showAlert('Không có dòng dữ liệu để xuất CSV.','info');return false;}
    const blob = new Blob([makeCsv(columns,rows)],{type:'text/csv;charset=utf-8;'});
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url;link.download = filename;document.body.append(link);link.click();link.remove();
    setTimeout(() => URL.revokeObjectURL(url),1000);return true;
}
