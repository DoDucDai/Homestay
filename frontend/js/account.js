// Shared account UI. All session checks and requests go through api.js.
let accountBusy = false;
function setSessionState(pending, message = '') {
    document.body.classList[pending ? 'add' : 'remove']('session-pending');
    const status = document.getElementById('session-status');
    if (status) {
        status.hidden = !pending;
        document.getElementById('session-message').textContent = message || 'Đang kiểm tra phiên đăng nhập...';
        document.getElementById('session-retry').hidden = !message;
    }
}
function applyAccountPermissions(user) {
    document.querySelectorAll('[data-admin-only]').forEach(el => { el.hidden = user.role !== 'admin'; });
}
function addAccountControls(user) {
    applyAccountPermissions(user);
    let bar = document.getElementById('account-controls');
    if (bar) {
        document.getElementById('account-name').textContent = user.fullName || user.username || '';
        document.getElementById('account-role').textContent = user.role === 'admin' ? 'Quản trị viên' : 'Nhân viên';
        return;
    }
    const main = document.querySelector('.main-content');
    if (!main) return;
    bar = textNode('header','','account-controls');bar.id = 'account-controls';
    const identity = textNode('div','','account-identity');
    const name = textNode('strong',user.fullName || user.username || '');name.id = 'account-name';
    const role = textNode('span',user.role === 'admin' ? 'Quản trị viên' : 'Nhân viên','account-role');role.id = 'account-role';
    identity.append(name,role);
    const actions = textNode('div','','account-actions');
    const change = actionButton('Đổi mật khẩu','btn btn-secondary',() => {
        if (accountBusy) return;
        form.reset();errorLabel.textContent = '';openModal('account-password-modal');
        document.getElementById('account-old-password').focus();
    });change.id = 'account-change-btn';
    const logout = actionButton('Đăng xuất','btn btn-danger',logoutAccount);logout.id = 'account-logout-btn';
    actions.append(change,logout);bar.append(identity,actions);main.prepend(bar);

    const overlay = textNode('div','','modal-overlay');overlay.id = 'account-password-modal';
    overlay.setAttribute('role','dialog');overlay.setAttribute('aria-modal','true');overlay.setAttribute('aria-labelledby','account-password-title');
    const box = textNode('div','','modal-box');
    const title = textNode('h3','Đổi mật khẩu');title.id = 'account-password-title';box.append(title);
    const form = document.createElement('form');form.id = 'account-password-form';
    for (const [key,label,autocomplete,max,min] of [
        ['old','Mật khẩu hiện tại','current-password',256,1],
        ['new','Mật khẩu mới (12–128 ký tự)','new-password',128,12],
        ['confirm','Nhập lại mật khẩu mới','new-password',128,12]
    ]) {
        const group = textNode('div','','form-group');
        const caption = textNode('label',label);caption.htmlFor = 'account-'+key+'-password';
        const input = document.createElement('input');input.id = caption.htmlFor;input.type = 'password';
        input.autocomplete = autocomplete;input.maxLength = max;input.minLength = min;input.required = true;
        group.append(caption,input);form.append(group);
    }
    const errorLabel = textNode('p','');errorLabel.id = 'account-password-error';errorLabel.setAttribute('role','alert');form.append(errorLabel);
    const footer = textNode('div','','modal-footer');
    const cancel = actionButton('Hủy','btn btn-secondary',() => {
        if (accountBusy) return;
        form.reset();overlay.classList.remove('show');change.focus();
    });cancel.id = 'account-password-cancel';
    const save = textNode('button','Lưu mật khẩu','btn btn-primary');save.type = 'submit';save.id = 'account-password-save';
    footer.append(cancel,save);form.append(footer);box.append(form);overlay.append(box);document.body.append(overlay);
    form.addEventListener('submit',async event => {
        event.preventDefault();if (accountBusy) return;
        const oldPassword = document.getElementById('account-old-password').value;
        const newPassword = document.getElementById('account-new-password').value;
        const confirmation = document.getElementById('account-confirm-password').value;
        if (!oldPassword || oldPassword.length > 256 || newPassword.length < 12 || newPassword.length > 128 || !newPassword.trim() || newPassword === oldPassword) {
            errorLabel.textContent = 'Nhập mật khẩu hiện tại; mật khẩu mới cần 12–128 ký tự, không chỉ khoảng trắng và khác mật khẩu cũ.';return;
        }
        if (newPassword !== confirmation) { errorLabel.textContent = 'Hai mật khẩu mới chưa khớp.';return; }
        accountBusy = true;setAccountBusy(true);errorLabel.textContent = '';
        try {
            await apiPut('/auth/change-password',{oldPassword,newPassword});
            form.reset();window.location.replace('login.html?changed=1');
        } catch (error) { errorLabel.textContent = error.message || 'Không thể đổi mật khẩu.'; }
        finally { accountBusy = false;setAccountBusy(false); }
    });
}
function setAccountBusy(busy) {
    ['account-change-btn','account-logout-btn','account-password-cancel','account-password-save','account-old-password','account-new-password','account-confirm-password'].forEach(id => {
        const el = document.getElementById(id);if (el) el.disabled = busy;
    });
    const logout = document.getElementById('logout-btn');if (logout) logout.setAttribute('aria-disabled',String(busy));
}
async function logoutAccount(event) {
    if (event) event.preventDefault();if (accountBusy) return;
    accountBusy = true;setAccountBusy(true);
    try {
        await apiPost('/auth/logout');
        setSessionState(true,'Đã đăng xuất. Đang chuyển về trang đăng nhập...');
        window.location.replace('login.html');
    } catch (error) {
        // A lost logout response can follow successful invalidation; only recheck with GET.
        await refreshAccountSession();
        showRequestError(error,'Chưa xác nhận được đăng xuất.');
    } finally { accountBusy = false;setAccountBusy(false); }
}
async function refreshAccountSession() {
    const previous = currentUser;
    setSessionState(true);
    const user = await checkLogin(true);
    if (!user) { setSessionState(true,'Không xác nhận được phiên đăng nhập. Vui lòng thử lại.');return; }
    if (previous && (previous.userId !== user.userId || previous.role !== user.role)) { window.location.reload();return; }
    addAccountControls(user);setSessionState(false);
}
document.addEventListener('DOMContentLoaded',async () => {
    document.getElementById('session-retry')?.addEventListener('click',() => window.location.reload());
    const user = await checkLogin();
    if (!user) { setSessionState(true,'Không xác nhận được phiên đăng nhập. Vui lòng thử lại.');return; }
    addAccountControls(user);setSessionState(false);
    const logout = document.getElementById('logout-btn');if (logout) logout.addEventListener('click',logoutAccount);
});
window.addEventListener('pageshow',event => { if (event.persisted) return refreshAccountSession(); });
window.addEventListener('pagehide',() => {
    setSessionState(true);
    document.getElementById('account-password-form')?.reset();
    document.getElementById('account-password-modal')?.classList.remove('show');
});
