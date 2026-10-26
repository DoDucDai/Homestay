const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const {env,Element,payload,root} = require('./frontend-test-env.cjs');
const deferred = () => {let resolve;const promise=new Promise(r => {resolve=r;});return {resolve,promise};};
let passed=0;
function pass(name) {passed++;console.log('PASS '+name);}
const guest={customerId:1,fullName:payload};
const booking=(id,paid=true) => ({bookingId:id,roomCode:'P1',roomName:payload,checkinDate:'2026-10-26',checkoutDate:'2026-10-28',nights:2,status:paid?'CheckedOut':'Cancelled',paidTotal:paid?620000:0,paymentId:paid?7:null,paymentMethod:paid?'Tiền mặt':null});
const history=(id=1) => ({success:true,customer:{...guest,customerId:id},bookings:[booking(1),booking(2,false)],totalPaid:'620000'});
function accountEnv(handler) {
    class DomElement extends Element {
        constructor(tag) {super(tag);this.classes=new Set();this.classList={add:v=>this.classes.add(v),remove:v=>this.classes.delete(v)};}
        prepend(node) {this.children.unshift(node);}
        focus() {this.focused=true;}
        reset() {walk(this).filter(n=>n.tagName==='INPUT').forEach(n=>{n.value='';});}
        querySelector(selector) {return walk(this).find(n=>selector==='[type="submit"]' && n.type==='submit') || null;}
    }
    const walk=node=>[node,...node.children.flatMap(walk)];
    const body=new DomElement('body'),main=new DomElement('main');main.className='main-content';body.append(main);body.classList.add('session-pending');
    const add=(tag,id,parent=body)=>{const n=new DomElement(tag);n.id=id;parent.append(n);return n;};
    add('div','session-status');add('span','session-message');add('button','session-retry');add('a','logout-btn');add('div','alert-message');
    const admin=add('button','admin-only');admin.dataset.adminOnly='';admin.hidden=true;
    const events={},windowEvents={},calls=[],redirects=[];
    let reloads=0;
    const response=(status,data)=>({ok:status<400,status,json:async()=>data});
    const user={userId:1,role:'admin',fullName:payload};
    const context=vm.createContext({Node:DomElement,document:{body,createElement:tag=>new DomElement(tag),
        getElementById:id=>walk(body).find(n=>n.id===id)||null,querySelector:selector=>selector==='.main-content'?main:null,
        querySelectorAll:selector=>selector==='[data-admin-only]'?[admin]:[],addEventListener:(name,fn)=>{events[name]=fn;}},
        window:{location:{pathname:'/pages/rooms.html',search:'',replace:url=>redirects.push(url),reload:()=>{reloads++;}},addEventListener:(name,fn)=>{windowEvents[name]=fn;}},
        sessionStorage:{removeItem(){}},setTimeout:()=>0,URLSearchParams,console,
        fetch:async(url,options)=>{calls.push({url,options});return handler ? handler(url,options,response) : response(200,url.endsWith('/me')?{success:true,user}:url.endsWith('/csrf')?{token:'csrf',headerName:'X-CSRF-TOKEN'}:{success:true});}});
    vm.runInContext(fs.readFileSync(path.join(root,'api.js'),'utf8'),context);
    vm.runInContext(fs.readFileSync(path.join(root,'account.js'),'utf8'),context);
    return {context,body,main,events,windowEvents,calls,redirects,admin,el:id=>context.document.getElementById(id),get reloads(){return reloads;}};
}
(async()=>{
    let e=env('customers'),queries=[],pending=deferred();
    e.el('search-input').value="O'Neil & 09";e.context.apiGet=url=>{queries.push(url);return pending.promise;};
    const old=e.context.loadCustomers();assert.equal(queries[0],'/customers?search='+encodeURIComponent("O'Neil & 09"));
    e.context.apiGet=async()=>[];await e.context.loadCustomers();pending.resolve([guest]);await old;assert.ok(!e.el('customers-tbody').textContent.includes(payload));
    e.context.apiGet=async()=>{throw new Error('Offline');};await e.context.loadCustomers();assert.equal(e.el('customers-tbody').textContent,'Offline');
    pass('customer search goes to server with encoded input, ignores stale results and preserves loading errors');

    e=env('customers');e.context.apiGet=async()=>history();await e.context.openCustomerHistory(1);
    assert.ok(e.el('history-title').textContent.includes(payload));assert.equal(e.el('history-tbody').children.length,2);
    const paidLink=e.el('history-tbody').children[0].children[8].children[0];assert.equal(paidLink.href,'checkout.html?paymentId=7');
    assert.equal(e.el('history-tbody').children[1].children[6].textContent,'Chưa thanh toán');
    assert.equal(e.el('history-total').textContent,e.context.formatMoney(620000n));
    pass('history renders safe guest/room text, nights/status, paid totals and exact saved-invoice links');

    pending=deferred();e.context.apiGet=()=>pending.promise;const loading=e.context.openCustomerHistory(1);
    e.context.apiGet=async()=>({...history(2),bookings:[],totalPaid:'0'});await e.context.openCustomerHistory(2);pending.resolve(history());await loading;
    assert.match(e.el('history-title').textContent,/KH2/);assert.match(e.el('history-tbody').textContent,/chưa có lịch sử/);
    pending=deferred();e.context.apiGet=()=>pending.promise;const closing=e.context.openCustomerHistory(1);e.context.closeCustomerHistory();pending.resolve(history());await closing;
    assert.equal(e.el('history-total').textContent,'--');
    e.context.apiGet=async()=>{throw new Error('Not found');};await e.context.openCustomerHistory(999);assert.equal(e.el('history-status').textContent,'Not found');assert.equal(e.el('history-total').textContent,'--');
    pass('empty/missing history differs from loading failures; newer and closed modals ignore older responses');

    e.context.apiGet=async()=>({...history(),totalPaid:'999'});await e.context.openCustomerHistory(1);assert.equal(e.el('history-total').textContent,'--');
    e.context.apiGet=async()=>({success:true,customer:guest,totalPaid:'0',bookings:[{...booking(1),paidTotal:0}]});await e.context.openCustomerHistory(1);
    assert.equal(e.el('history-tbody').children[0].children[8].children[0].href,'checkout.html?paymentId=7');
    pass('history rejects inconsistent aggregates and zero-value paid invoices still retain their link');

    e=env('customers');vm.runInContext("currentUser={role:'staff'}",e.context);e.context.renderTable([{...guest,documentType:'PASSPORT',cccd:'AB123456',phone:'0901234567'}]);
    assert.equal(e.el('customers-tbody').children[0].children.at(-1).children.length,2);await e.context.deleteCustomer(1,'Guest');assert.equal(e.writes.length,0);
    e=env('services');vm.runInContext("currentUser={role:'staff'}",e.context);e.context.renderTable([{serviceId:1,serviceName:'Water',unitPrice:10000,status:'Hoạt động'}]);
    assert.equal(e.el('services-tbody').children[0].children.at(-1).children.length,0);await e.context.saveService();await e.context.deleteService(1,'Water');assert.equal(e.writes.length,0);
    pass('staff can edit/view customer history but has no customer delete or service catalog writes');

    let a=accountEnv();await a.events.DOMContentLoaded();
    assert.equal(a.el('account-name').textContent,payload);assert.equal(a.el('account-role').textContent,'Quản trị viên');assert.equal(a.admin.hidden,false);assert.equal(a.body.classes.has('session-pending'),false);
    a.context.addAccountControls({fullName:'Staff',role:'staff'});assert.equal(a.admin.hidden,true);assert.equal(a.el('account-name').textContent,'Staff');assert.equal(a.main.children.filter(n=>n.id==='account-controls').length,1);
    pass('shared header renders server identity safely, updates role permissions and never duplicates controls');

    a=accountEnv();await a.events.DOMContentLoaded();a.el('account-change-btn').events.click();
    a.el('account-old-password').value='old';a.el('account-new-password').value='short';a.el('account-confirm-password').value='short';
    const submit=a.el('account-password-form').events.submit,event={preventDefault(){}};await submit(event);assert.equal(a.calls.filter(c=>c.url.endsWith('/change-password')).length,0);
    a.el('account-new-password').value='  New Password 123!  ';a.el('account-confirm-password').value='Mismatch';await submit(event);assert.match(a.el('account-password-error').textContent,/chưa khớp/);
    a.el('account-confirm-password').value='  New Password 123!  ';pending=deferred();let writes=0,body;
    a.context.apiPut=(url,data)=>{writes++;body=data;return pending.promise;};const saving=submit(event);await submit(event);
    assert.equal(writes,1);assert.equal(a.el('account-password-cancel').disabled,true);assert.equal(body.newPassword,'  New Password 123!  ');
    pending.resolve({success:true});await saving;assert.equal(a.redirects[0],'login.html?changed=1');assert.equal(a.el('account-new-password').value,'');
    pass('password form validates length/match, preserves spaces, blocks duplicate writes and requires login after success');

    a=accountEnv();await a.events.DOMContentLoaded();a.el('account-old-password').value='old';a.el('account-new-password').value='New Password 123!';a.el('account-confirm-password').value='New Password 123!';
    a.context.apiPut=async()=>{throw new Error('Wrong old password');};await a.el('account-password-form').events.submit(event);
    assert.equal(a.el('account-password-error').textContent,'Wrong old password');assert.equal(a.el('account-password-save').disabled,false);assert.equal(a.redirects.length,0);
    pass('rejected password change keeps the server error and restores controls without a false redirect');

    a=accountEnv();await a.events.DOMContentLoaded();pending=deferred();writes=0;a.context.apiPost=()=>{writes++;return pending.promise;};
    const logout=a.context.logoutAccount();await a.context.logoutAccount();assert.equal(writes,1);pending.resolve({success:true});await logout;
    assert.equal(a.redirects[0],'login.html');assert.equal(a.body.classes.has('session-pending'),true);
    pass('header/sidebar share one logout lock and hide protected content after confirmed logout');

    let loggedOut=false;a=accountEnv((url,options,res)=>{
        if(url.endsWith('/me'))return res(loggedOut?401:200,loggedOut?{message:'Expired'}:{success:true,user:{userId:1,role:'staff',fullName:'Staff'}});
        if(url.endsWith('/csrf'))return res(200,{token:'csrf',headerName:'X-CSRF-TOKEN'});
        loggedOut=true;throw new Error('Lost logout response');
    });await a.events.DOMContentLoaded();await a.context.logoutAccount();assert.equal(a.calls.filter(c=>c.url.endsWith('/logout')).length,1);assert.equal(a.redirects[0],'login.html');
    pass('lost logout response rechecks the server with GET and recognizes a session already invalidated');

    let expired=false;a=accountEnv((url,options,res)=>res(expired?401:200,expired?{message:'Expired'}:{success:true,user:{userId:1,role:'staff',fullName:'Staff'}}));
    await a.events.DOMContentLoaded();a.el('account-old-password').value='secret';a.windowEvents.pagehide();assert.equal(a.el('account-old-password').value,'');expired=true;await a.windowEvents.pageshow({persisted:true});assert.equal(a.redirects[0],'login.html');assert.equal(a.body.classes.has('session-pending'),true);
    assert.equal(a.calls.filter(c=>c.url.endsWith('/me')).length,2);
    pass('restored pages revalidate /me and conceal stale content when the server session has expired');

    a=accountEnv((url,options,res)=>res(503,{message:'Auth offline'}));await a.events.DOMContentLoaded();assert.equal(a.body.classes.has('session-pending'),true);assert.equal(a.el('session-retry').hidden,false);a.el('session-retry').events.click();assert.equal(a.reloads,1);
    pass('unavailable session verification leaves protected content hidden and offers a working retry');

    const stale=deferred();let meCalls=0;a=accountEnv((url,options,res)=>++meCalls===1?stale.promise:res(200,{success:true,user:{userId:2,role:'staff',fullName:'New'}}));
    const first=a.context.checkLogin();await a.context.checkLogin(true);stale.resolve({ok:true,status:200,json:async()=>({success:true,user:{userId:1,role:'admin',fullName:'Old'}})});await first;
    assert.equal(vm.runInContext('currentUser.userId',a.context),2);
    vm.runInContext(fs.readFileSync(path.join(root,'auth.js'),'utf8'),a.context);await a.events.DOMContentLoaded();
    pass('late identity responses cannot overwrite a newer session and auth.js safely ignores pages without login form');

    for(const page of ['rooms','customers','bookings','checkin','checkout','services','statistics']) {
        const html=fs.readFileSync(path.join(root,'../pages',page+'.html'),'utf8');assert.ok(html.indexOf('api.js')<html.indexOf('account.js'));
        assert.match(html,/<body class="session-pending">/);assert.match(html,/data-admin-only hidden href="statistics.html"/);
        const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);assert.equal(ids.length,new Set(ids).size);
    }
    assert.ok(!fs.readFileSync(path.join(root,'../pages/login.html'),'utf8').includes('account.js'));
    pass('all protected pages load shared account controls after API helpers and start behind session verification');
    console.log('FE-04/05 frontend: '+passed+' groups passed (instrumented DOM/API).');
})().catch(error=>{console.error(error);process.exitCode=1;});
