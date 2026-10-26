const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const frontend = path.resolve(__dirname, '../../../../frontend/js');
const apiCode = fs.readFileSync(path.join(frontend, 'api.js'), 'utf8');
const response = (status, data) => ({ ok: status >= 200 && status < 300, status, json: async () => data });
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const user = { userId: 2, fullName: 'Staff', role: 'staff' };
let passed = 0;
function environment(handler, login = false) {
    const calls = [], redirects = [], events = {}, elements = new Map();
    const formEvents = {};
    const submit = { disabled: false };
    const form = { addEventListener: (name, fn) => { formEvents[name] = fn; }, querySelector: () => submit };
    for (const id of ['username', 'password', 'alert-message']) elements.set(id, { value: '', style: {}, textContent: '' });
    const context = vm.createContext({
        fetch: async (url, options) => { calls.push({ url, options }); return handler(url, options); },
        window: { location: { pathname: login ? '/pages/login.html' : '/pages/rooms.html', search: '', replace: url => redirects.push(url) }, addEventListener() {} },
        sessionStorage: { getItem: () => '{"role":"admin"}', removeItem() {}, setItem() { throw new Error('Must not store auth in sessionStorage'); } },
        document: { getElementById: id => id === 'login-form' ? (login ? form : null) : elements.get(id), addEventListener: (name, fn) => { events[name] = fn; } },
        setTimeout: () => 0, URLSearchParams, console
    });
    vm.runInContext(apiCode, context);
    return { context, calls, redirects, events, elements, formEvents, submit };
}
function pass(name) { passed++; console.log('PASS ' + name); }
(async () => {
    let env = environment(async () => response(401, { message: 'Please log in' }));
    await assert.rejects(env.context.apiGet('/rooms'));
    assert.equal(env.calls.length, 1); assert.equal(env.calls[0].url, '/api/auth/me');
    assert.equal(env.redirects[0], 'login.html');
    pass('forged browser storage cannot authorize a protected request');

    const pending = deferred();
    env = environment(async url => url.endsWith('/me') ? pending.promise : response(200, []));
    const loading = env.context.apiGet('/rooms');
    await Promise.resolve(); assert.equal(env.calls.length, 1);
    pending.resolve(response(200, { success: true, user })); await loading;
    assert.equal(env.calls[1].url, '/api/rooms');
    assert.ok(env.calls.every(call => call.options.credentials === 'include'));
    pass('protected loading waits for server me and sends cookie credentials');

    let writes = 0, tokens = 0;
    env = environment(async (url, options) => {
        if (url.endsWith('/me')) return response(200, { success: true, user });
        if (url.endsWith('/csrf')) return response(200, { success: true, token: 'token-' + ++tokens, headerName: 'X-CSRF-TOKEN' });
        writes++;
        assert.equal(options.headers['X-CSRF-TOKEN'], 'token-' + tokens);
        return writes === 1 ? response(403, { code: 'CSRF_INVALID', message: 'Reload token' }) : response(200, { success: true });
    });
    await assert.rejects(env.context.apiPost('/bookings', { customerId: 1 }), /Reload token/);
    assert.equal(writes, 1);
    await env.context.apiPost('/bookings', { customerId: 1 });
    assert.equal(tokens, 2); assert.equal(writes, 2);
    pass('writes carry CSRF; explicit retry refreshes rejected token without automatic duplicate writes');

    env = environment(async url => url.endsWith('/me') ? response(200, { success: true, user })
        : url.endsWith('/csrf') ? response(200, { token: 'token', headerName: 'X-CSRF-TOKEN' })
        : response(403, { code: 'FORBIDDEN', message: 'No permission' }));
    await assert.rejects(env.context.apiDelete('/rooms/1'), error => error.status === 403 && error.message === 'No permission');
    assert.equal(env.redirects.length, 0);
    pass('403 preserves the server permission message without a login loop');

    env = environment(async url => url.endsWith('/me') ? response(200, { success: true, user })
        : response(401, { code: 'UNAUTHENTICATED', message: 'Expired' }));
    await assert.rejects(env.context.apiPost('/bookings', {}));
    assert.equal(env.redirects[0], 'login.html');
    assert.equal(env.calls.filter(call => call.url === '/api/bookings').length, 0);
    pass('expiry while obtaining a CSRF token redirects before submitting a mutation');

    writes = 0;
    env = environment(async url => {
        if (url.endsWith('/me')) return response(200, { success: true, user });
        if (url.endsWith('/csrf')) return response(200, { token: 'token', headerName: 'X-CSRF-TOKEN' });
        writes++; throw new Error('Connection lost');
    });
    await assert.rejects(env.context.apiPost('/checkout/1', {}), /Connection lost/);
    assert.equal(writes, 1);
    pass('a lost checkout response is not retried automatically');

    let loggedOut = false;
    env = environment(async url => {
        if (url.endsWith('/me')) return loggedOut ? response(401, { message: 'Expired' }) : response(200, { success: true, user });
        if (url.endsWith('/csrf')) return response(200, { token: 'token', headerName: 'X-CSRF-TOKEN' });
        assert.equal(url, '/api/auth/logout'); loggedOut = true; return response(200, { success: true });
    });
    await env.context.apiPost('/auth/logout');
    await assert.rejects(env.context.apiGet('/rooms'));
    assert.equal(env.calls.filter(call => call.url.endsWith('/me')).length, 2);
    assert.equal(env.redirects[0], 'login.html');
    pass('logout calls the server then clears cached identity and rechecks me');

    const loginPending = deferred(); writes = 0;
    env = environment(async url => {
        if (url.endsWith('/me')) return response(401, { message: 'Anonymous' });
        if (url.endsWith('/csrf')) return response(200, { token: 'token', headerName: 'X-CSRF-TOKEN' });
        writes++; return loginPending.promise;
    }, true);
    vm.runInContext(fs.readFileSync(path.join(frontend, 'auth.js'), 'utf8'), env.context);
    env.elements.get('username').value = ' staff ';
    env.elements.get('password').value = '  keep password spaces  ';
    const submitEvent = { preventDefault() {} };
    const first = env.formEvents.submit(submitEvent);
    await env.formEvents.submit(submitEvent);
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(writes, 1);
    const loginCall = env.calls.find(call => call.url.endsWith('/auth/login'));
    assert.equal(JSON.parse(loginCall.options.body).password, '  keep password spaces  ');
    assert.equal(JSON.parse(loginCall.options.body).username, 'staff');
    loginPending.resolve(response(200, { success: true, user })); await first;
    assert.equal(env.elements.get('password').value, ''); assert.equal(env.redirects[0], 'rooms.html');
    pass('login preserves password spaces, prevents double submit and does not store auth in browser storage');
    env = environment(async () => response(200, {success:true,user}), true);
    vm.runInContext(fs.readFileSync(path.join(frontend,'auth.js'),'utf8'),env.context);
    await env.events.DOMContentLoaded();
    assert.equal(env.redirects[0],'rooms.html');assert.equal(env.calls.filter(c=>c.url.endsWith('/login')).length,0);
    pass('login with an existing server session redirects to rooms without resubmitting credentials');

    const oldResponse=deferred();
    env=environment(async url=>url.endsWith('/me')?response(200,{success:true,user}):oldResponse.promise);
    const oldRequest=env.context.apiGet('/rooms');await new Promise(resolve=>setImmediate(resolve));
    await env.context.checkLogin(true);oldResponse.resolve(response(401,{message:'Old session expired'}));
    await assert.rejects(oldRequest,/Phiên đã thay đổi/);assert.equal(env.redirects.length,0);
    assert.equal(vm.runInContext('currentUser.role',env.context),'staff');
    pass('late 401 from an older request cannot invalidate a newly verified server session');

    const oldToken=deferred();
    env=environment(async url=>url.endsWith('/me')?response(200,{success:true,user}):url.endsWith('/csrf')?oldToken.promise:response(200,{success:true}));
    const oldWrite=env.context.apiPost('/bookings',{});await new Promise(resolve=>setImmediate(resolve));await env.context.checkLogin(true);
    oldToken.resolve(response(200,{token:'old-token',headerName:'X-CSRF-TOKEN'}));
    await assert.rejects(oldWrite,/Phiên đã thay đổi/);assert.equal(env.calls.filter(c=>c.url==='/api/bookings').length,0);
    await env.context.apiPost('/bookings',{});assert.equal(env.calls.filter(c=>c.url==='/api/bookings').length,1);
    pass('session revalidation cancels a queued write waiting on an older CSRF request');
    console.log('SEC-01/FE-05 frontend: ' + passed + ' groups passed (mock fetch/DOM).');
})().catch(error => { console.error(error); process.exitCode = 1; });
