// Shared account UI. All session checks and requests go through api.js.
let accountBusy = false;
let accountOperation = '';
function resetAccountPasswordForm() {
    const form = document.getElementById('account-password-form');
    form?.reset(); form?.setAttribute('aria-busy', 'false');
    for (const key of ['old', 'new', 'confirm']) {
        const input = document.getElementById('account-'+key+'-password');
        if (input) input.setAttribute('aria-invalid', 'false');
        setPasswordVisibility(input, document.getElementById('account-'+key+'-toggle'));
    }
    const error = document.getElementById('account-password-error');
    if (error) { error.textContent = ''; error.hidden = true; }
    const status = document.getElementById('account-form-status'); if (status) status.textContent = '';
}
function accountPasswordError(message, field) {
    const error = document.getElementById('account-password-error');
    error.textContent = message; error.hidden = !message;
    if (field) { document.getElementById(field).setAttribute('aria-invalid','true'); document.getElementById(field).focus(); }
}
function setSessionState(pending, message = '') {
    if (pending && typeof closeShellMenu === 'function') closeShellMenu();
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
        document.getElementById('account-role').className = 'account-role badge ' + (user.role === 'admin' ? 'badge-info' : 'badge-neutral');
        document.getElementById('account-admin-link').hidden=user.role!=='admin';
        return;
    }
    const main = document.querySelector('.main-content');
    if (!main) return;
    bar = textNode('header','','account-controls');bar.id = 'account-controls';
    const profile = textNode('a','','account-profile');profile.id='account-profile-link';profile.href='account.html';profile.setAttribute('aria-label','Xem thông tin tài khoản');
    const identity = textNode('div','','account-identity');
    const name = textNode('strong',user.fullName || user.username || '','account-name-link');name.id = 'account-name';
    const role = textNode('span',user.role === 'admin' ? 'Quản trị viên' : 'Nhân viên','account-role badge ' + (user.role === 'admin' ? 'badge-info' : 'badge-neutral'));role.id = 'account-role';
    identity.append(name,role);
    profile.append(identity);
    const actions = textNode('div','','account-actions');
    const change = actionButton('Đổi mật khẩu','btn btn-secondary',() => {
        if (accountBusy) return;
        resetAccountPasswordForm();openModal('account-password-modal');
        document.getElementById('account-old-password').focus();
    });change.id = 'account-change-btn';
    const logout = actionButton('Đăng xuất','btn btn-danger',logoutAccount);logout.id = 'account-logout-btn';
    const info=textNode('a','Thông tin tài khoản','account-menu-link');info.href='account.html';info.id='account-info-link';
    const admin=textNode('a','Khu quản trị','account-menu-link');admin.href='account.html#quan-tri';admin.id='account-admin-link';admin.setAttribute('data-admin-only','');admin.hidden=user.role!=='admin';
    [profile,info,admin].forEach(link=>link.addEventListener('click',event=>{
        event.preventDefault();if(!accountBusy)window.location.href=link.href;
    }));
    actions.append(info,admin,change,logout);bar.append(profile,actions);main.prepend(bar);
    if(!window.location.pathname.endsWith('/landing.html')){
        const menu=document.createElement('details');menu.className='workspace-account-menu';
        const summary=document.createElement('summary');summary.className='account-profile workspace-account-toggle';summary.id='workspace-account-toggle';
        summary.setAttribute('aria-label','Mở menu tài khoản');summary.append(identity);
        menu.append(summary,actions);bar.replaceChildren(menu);
    }

    const overlay = textNode('div','','modal-overlay');overlay.id = 'account-password-modal';
    overlay.setAttribute('role','dialog');overlay.setAttribute('aria-modal','true');overlay.setAttribute('aria-labelledby','account-password-title');
    const box = textNode('div','','modal-box account-password-box');
    const title = textNode('h3','Đổi mật khẩu');title.id = 'account-password-title';box.append(title);
    box.append(textNode('p','Cập nhật mật khẩu cho tài khoản đang đăng nhập.','account-password-intro'));
    const form = document.createElement('form');form.id = 'account-password-form';form.noValidate = true;
    form.setAttribute('aria-busy','false');
    const help = textNode('p','Mật khẩu mới cần 12–128 ký tự, khác mật khẩu hiện tại. Sau khi đổi, bạn cần đăng nhập lại.','form-help');help.id='account-password-help';form.append(help);
    for (const [key,label,autocomplete,max,min] of [
        ['old','Mật khẩu hiện tại','current-password',256,1],
        ['new','Mật khẩu mới (12–128 ký tự)','new-password',128,12],
        ['confirm','Nhập lại mật khẩu mới','new-password',128,12]
    ]) {
        const group = textNode('div','','form-group');
        const caption = textNode('label',label);caption.htmlFor = 'account-'+key+'-password';
        const input = document.createElement('input');input.id = caption.htmlFor;input.type = 'password';
        input.autocomplete = autocomplete;input.maxLength = max;input.minLength = min;input.required = true;
        input.setAttribute('aria-describedby','account-password-help account-password-error');
        const field = textNode('div','','password-field');
        const toggle = textNode('button','Hiện','password-toggle');toggle.type='button';toggle.id='account-'+key+'-toggle';
        bindPasswordVisibility(input,toggle,label.toLocaleLowerCase('vi'));
        input.addEventListener('input',()=>{input.setAttribute('aria-invalid','false');accountPasswordError('');});
        field.append(input,toggle);group.append(caption,field);form.append(group);
    }
    const errorLabel = textNode('p','','alert alert-error');errorLabel.id = 'account-password-error';errorLabel.hidden=true;errorLabel.setAttribute('role','alert');form.append(errorLabel);
    const footer = textNode('div','','modal-footer');
    const cancel = actionButton('Hủy','btn btn-secondary',() => {
        if (accountBusy) return;
        resetAccountPasswordForm();overlay.classList.remove('show');change.focus();
    });cancel.id = 'account-password-cancel';cancel.setAttribute('data-modal-close','');
    const save = textNode('button','Lưu mật khẩu','btn btn-primary');save.type = 'submit';save.id = 'account-password-save';
    const status = textNode('p','','account-form-status');status.id='account-form-status';status.setAttribute('role','status');status.setAttribute('aria-live','polite');
    footer.append(cancel,save);form.append(footer,status);box.append(form);overlay.append(box);document.body.append(overlay);
    form.addEventListener('submit',async event => {
        event.preventDefault();if (accountBusy) return;
        const oldPassword = document.getElementById('account-old-password').value;
        const newPassword = document.getElementById('account-new-password').value;
        const confirmation = document.getElementById('account-confirm-password').value;
        ['old','new','confirm'].forEach(key=>document.getElementById('account-'+key+'-password').setAttribute('aria-invalid','false'));
        if (!oldPassword || oldPassword.length > 256 || newPassword.length < 12 || newPassword.length > 128 || !newPassword.trim() || newPassword === oldPassword) {
            accountPasswordError('Nhập mật khẩu hiện tại; mật khẩu mới cần 12–128 ký tự, không chỉ khoảng trắng và khác mật khẩu cũ.',!oldPassword || oldPassword.length>256 ? 'account-old-password':'account-new-password');return;
        }
        if (newPassword !== confirmation) { accountPasswordError('Hai mật khẩu mới chưa khớp.','account-confirm-password');return; }
        accountOperation='password';accountBusy = true;accountPasswordError('');setAccountBusy(true);
        try {
            await apiPut('/auth/change-password',{oldPassword,newPassword});
            resetAccountPasswordForm();window.location.replace('login.html?changed=1');
        } catch (error) { accountPasswordError(error.message || 'Không thể đổi mật khẩu.'); }
        finally { accountBusy = false;setAccountBusy(false); }
    });
}
function setAccountBusy(busy) {
    ['account-change-btn','account-logout-btn','account-password-cancel','account-password-save','account-old-password','account-new-password','account-confirm-password','account-old-toggle','account-new-toggle','account-confirm-toggle'].forEach(id => {
        const el = document.getElementById(id);if (el) el.disabled = busy;
    });
    const logout = document.getElementById('logout-btn');if (logout) logout.setAttribute('aria-disabled',String(busy));
    for(const id of ['account-profile-link','account-info-link','account-admin-link'])document.getElementById(id)?.setAttribute('aria-disabled',String(busy));
    const form = document.getElementById('account-password-form');form?.setAttribute('aria-busy',String(busy && accountOperation==='password'));
    const save = document.getElementById('account-password-save');if(save)save.textContent=busy && accountOperation==='password'?'Đang lưu...':'Lưu mật khẩu';
    const status = document.getElementById('account-form-status');if(status)status.textContent=busy && accountOperation==='password'?'Đang cập nhật mật khẩu...':'';
    const headerLogout = document.getElementById('account-logout-btn');if(headerLogout)headerLogout.textContent=busy && accountOperation==='logout'?'Đang đăng xuất...':'Đăng xuất';
    if (!busy) accountOperation='';
}
async function logoutAccount(event) {
    if (event) event.preventDefault();if (accountBusy) return;
    accountOperation='logout';accountBusy = true;setAccountBusy(true);
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
    addAccountControls(user);
    if (typeof initializeShell === 'function') initializeShell(user);
    setSessionState(false);
}
document.addEventListener('DOMContentLoaded',async () => {
    document.getElementById('session-retry')?.addEventListener('click',() => window.location.reload());
    const user = await checkLogin();
    if (!user) { setSessionState(true,'Không xác nhận được phiên đăng nhập. Vui lòng thử lại.');return; }
    addAccountControls(user);
    if (typeof initializeShell === 'function') initializeShell(user);
    setSessionState(false);
    const logout = document.getElementById('logout-btn');if (logout) logout.addEventListener('click',logoutAccount);
});
window.addEventListener('pageshow',event => { if (event.persisted) return refreshAccountSession(); });
window.addEventListener('pagehide',() => {
    setSessionState(true);
    resetAccountPasswordForm();
    document.getElementById('account-password-modal')?.classList.remove('show');
});
