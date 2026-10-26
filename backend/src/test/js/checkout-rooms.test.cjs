const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const {env,payload,root} = require('./frontend-test-env.cjs');
let passed = 0;
function pass(name) { passed++; console.log('PASS '+name); }
function deferred() { let resolve,reject; const promise = new Promise((a,b) => {resolve=a;reject=b;}); return {promise,resolve,reject}; }
function preview(id=1) { return {success:true,bookingId:id,customerName:payload,roomCode:'P1',roomName:'Phòng',checkinDate:'2026-10-26',checkoutDate:'2026-10-28',nights:2,pricePerNight:300000,roomTotal:600000,serviceTotal:120000,grandTotal:720000,
    services:[{bookingServiceId:11,serviceName:payload,quantity:2,unitPrice:10000,totalPrice:20000},{bookingServiceId:12,serviceName:'Thuê xe',quantity:1,unitPrice:100000,totalPrice:100000}]}; }
function paid() { return {...preview(),paymentId:7,paymentMethod:'Tiền mặt',paymentStatus:'Đã thanh toán',paidAt:'2026-10-28T09:00:00'}; }
(async () => {
    let e=env('checkout'), pending=deferred();
    e.context.apiGet=() => pending.promise;
    const loading=e.context.openCheckoutModal(1);
    assert.equal(e.el('checkout-confirm-btn').disabled,true); assert.equal(e.el('add-service-btn').disabled,true);
    pending.resolve(preview()); await loading;
    assert.equal(e.el('checkout-confirm-btn').disabled,false); assert.equal(e.el('inv-grand-total').textContent,e.context.formatMoney(720000));
    assert.ok(e.el('invoice-services').textContent.includes(payload)); assert.match(e.el('invoice-title').textContent,/Tạm tính BK1/);
    pass('preview gates controls, shows captured prices, two waters plus rental and safe service names');

    const old=deferred(); e.context.apiGet=() => old.promise; const first=e.context.openCheckoutModal(1);
    e.context.apiGet=async () => preview(2); await e.context.openCheckoutModal(2); old.resolve(preview()); await first;
    assert.equal(e.el('inv-id').textContent,'BK2');
    const closed=deferred();e.context.apiGet=() => closed.promise;const late=e.context.openCheckoutModal(1);e.context.closeCheckoutModal();closed.resolve(preview());await late;
    assert.equal(e.el('checkout-confirm-btn').disabled,true);assert.equal(e.el('inv-id').textContent,'--');
    pass('out-of-order and closed-modal preview responses cannot restore stale billing actions');

    for (const broken of [{...preview(),grandTotal:1},{...preview(),services:[]},{...preview(),nights:0}]) {
        e.context.apiGet=async () => broken; await e.context.openCheckoutModal(1); assert.equal(e.el('checkout-confirm-btn').disabled,true);
    }
    e.context.apiGet=async () => {throw new Error('Database offline');};await e.context.openCheckoutModal(1);
    assert.equal(e.el('checkout-status').textContent,'Database offline'); assert.equal(e.el('inv-grand-total').textContent,'--');
    pass('invalid totals, incomplete details and server errors disable collection and clear the old invoice');

    e=env('checkout');let state=preview(),gets=[];
    e.context.apiGet=async url => { gets.push(url);return url==='/services' ? [{serviceId:1,serviceName:payload,unitPrice:10000,status:'Hoạt động'},{serviceId:2,serviceName:'Stopped',unitPrice:1,status:'Ngừng'}] : state; };
    await e.context.openCheckoutModal(1); await e.context.openServiceModal();
    assert.ok(e.el('service-select').textContent.includes(payload));assert.ok(!e.el('service-select').textContent.includes('Stopped'));
    e.el('service-select').value='1';e.el('service-quantity').value='1.5';await e.context.addBookingService();assert.equal(e.writes.length,0);
    e.el('service-quantity').value='2';pending=deferred();e.context.apiPost=(url,body) => {e.writes.push({url,body});return pending.promise;};
    const adding=e.context.addBookingService();await e.context.addBookingService();await e.context.confirmCheckout();
    assert.equal(e.writes.length,1);assert.equal(e.el('checkout-confirm-btn').disabled,true);assert.equal(e.el('service-cancel-btn').disabled,true);
    pending.resolve({success:true});await adding;assert.equal(e.writes[0].body.quantity,2);assert.equal(e.el('checkout-confirm-btn').disabled,false);
    state={...preview(),services:[preview().services[1]],serviceTotal:100000,grandTotal:700000};
    await e.context.removeBookingService(11);assert.equal(e.writes.at(-1).endpoint,'/bookings/services/11');
    assert.equal(e.el('inv-grand-total').textContent,e.context.formatMoney(700000));
    pass('active service selection, integer quantity, duplicate-write blocking and exact line removal refresh totals');

    e.context.apiDelete=async () => {throw new Error('Conflict');}; await e.context.removeBookingService(12);
    assert.match(e.el('checkout-status').textContent,/Conflict/);assert.ok(gets.filter(x => x==='/checkout/preview/1').length>=3);
    pass('service conflict reloads the authoritative preview without retrying the mutation');

    e=env('checkout');let postCount=0;pending=deferred();
    e.context.apiGet=async url => url.startsWith('/checkout/preview') ? preview() : url==='/payments/7' ? {success:true,invoice:paid()} : [];
    await e.context.openCheckoutModal(1);e.context.apiPost=() => {postCount++;return pending.promise;};
    const checkout=e.context.confirmCheckout();await e.context.confirmCheckout();e.context.closeCheckoutModal();
    assert.equal(postCount,1);assert.equal(e.el('checkout-cancel-btn').disabled,true);
    pending.resolve({success:true,paymentId:7});await checkout;
    assert.equal(e.el('invoice-title').textContent,'Hóa đơn HD7');assert.equal(e.el('invoice-print-btn').disabled,false);assert.equal(e.el('checkout-confirm-btn').hidden,true);
    e.context.printInvoice();e.context.printInvoice();assert.equal(e.context.printCount,2);assert.equal(postCount,1);
    e.context.closeCheckoutModal();await e.context.openPayment(7);assert.equal(e.el('inv-method').textContent,'Tiền mặt');
    pass('checkout submits once, fetches official snapshot, prints repeatedly without writes and reopens saved invoice');

    e=env('checkout');postCount=0;const reads=[];
    e.context.apiGet=async url => {reads.push(url);return url.startsWith('/checkout/preview') ? preview() : url.startsWith('/payments?') ? [{bookingId:1,paymentId:7}] : url==='/payments/7' ? {success:true,invoice:paid()} : [];};
    await e.context.openCheckoutModal(1);e.context.apiPost=async () => {postCount++;throw new Error('Lost response');};await e.context.confirmCheckout();
    assert.equal(postCount,1);assert.ok(reads.includes('/payments?bookingId=1'));assert.equal(e.el('invoice-title').textContent,'Hóa đơn HD7');
    pass('lost checkout response reconciles committed payment by GET and never posts twice');

    e=env('checkout');e.context.apiGet=async url => url.startsWith('/checkout/preview') ? preview() : [];
    await e.context.openCheckoutModal(1);e.context.apiPost=async () => {throw new Error('Rejected');};await e.context.confirmCheckout();
    assert.equal(e.el('checkout-confirm-btn').disabled,true);assert.match(e.el('checkout-status').textContent,/Rejected/);
    e.context.printInvoice();assert.equal(e.context.printCount,undefined);
    pass('unresolved checkout stays locked and preview cannot invoke invoice printing');

    e=env('rooms');const urls=[];pending=deferred();e.context.apiGet=url => {urls.push(url);return pending.promise;};
    e.el('filter-type').value='VIP';e.el('filter-status').value='Trống';e.el('search-input').value="O'Neil & P";
    const roomLoad=e.context.loadRooms();const params=new URLSearchParams(urls[0].split('?')[1]);
    assert.equal(params.get('type'),'VIP');assert.equal(params.get('status'),'Trống');assert.equal(params.get('search'),"O'Neil & P");
    e.context.apiGet=async () => [];await e.context.loadRooms();pending.resolve([{roomId:1,roomCode:'Stale',roomName:'Old',price:1,status:'Trống'}]);await roomLoad;
    assert.ok(!e.el('rooms-tbody').textContent.includes('Stale'));
    pass('room filters combine in encoded server query and old requests cannot overwrite newer results');

    const room={roomId:1,roomCode:payload,roomName:payload,roomType:'Single',price:300000,status:'Trống'};
    vm.runInContext("currentUser={role:'staff'}",e.context);e.context.renderTable([room]);
    assert.equal(e.el('rooms-tbody').children[0].children.at(-1).children.length,0);await e.context.changeRoomStatus(room);assert.equal(e.writes.length,0);
    vm.runInContext("currentUser={role:'admin'}",e.context);e.context.apiGet=async () => [room];
    pending=deferred();e.context.apiPut=(url,body) => {e.writes.push({url,body});return pending.promise;};
    const maintenance=e.context.changeRoomStatus(room);await e.context.changeRoomStatus(room);assert.equal(e.writes.length,1);
    assert.equal(e.writes[0].url,'/rooms/1/status');assert.equal(e.writes[0].body.status,'Bảo trì');pending.resolve({success:true});await maintenance;
    pass('staff has no catalog actions, admin maintenance submits once through dedicated status API');

    e=env('rooms');e.context.openEditModal({...room,roomCode:'P1',roomName:'Phòng đơn'});await e.context.saveRoom();
    assert.equal(e.writes.length,1);assert.equal(e.writes[0].body.status,undefined);assert.equal(e.writes[0].body.price,300000);
    pass('room catalog edits omit derived status and retain money/text validation');

    for (const page of ['checkout','rooms']) {
        const html=fs.readFileSync(path.join(root,'../pages',page+'.html'),'utf8');
        const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);assert.equal(ids.length,new Set(ids).size);
        const js=fs.readFileSync(path.join(root,page+'.js'),'utf8');
        for (const [,id] of js.matchAll(/getElementById\('([^']+)'\)/g)) if (!id.endsWith('-')) assert.ok(ids.includes(id),page+' missing '+id);
        for (const [,handler] of html.matchAll(/on(?:click|change|input)="(\w+)\(/g)) assert.equal(typeof e.context[handler]==='function' || typeof env(page).context[handler]==='function',true,handler);
        assert.ok(!html.includes('??'));
    }
    const css=fs.readFileSync(path.join(root,'../css/style.css'),'utf8');assert.match(css,/@media print/);assert.match(css,/:not\(#invoice-details\)/);
    pass('HTML handlers/IDs match, Vietnamese labels survive and print CSS isolates invoice content');
    console.log('FE-02/03 frontend: '+passed+' groups passed (instrumented DOM/API).');
})().catch(error => {console.error(error);process.exitCode=1;});
