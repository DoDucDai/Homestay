const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../../../frontend');
const defer = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a;reject=b; });return { promise,resolve,reject }; };
const flush = () => new Promise(resolve => setImmediate(resolve));
const clone = value => JSON.parse(JSON.stringify(value));
class Element {
    constructor(tag='div') {
        this.tagName=tag.toUpperCase();this.children=[];this.style={};this.dataset={};this.events={};this.classes=new Set();
        this.classList={add:value=>this.classes.add(value),remove:value=>this.classes.delete(value)};
        this._value='';this._text='';this.selectedIndex=-1;this.disabled=false;this.hidden=false;this.attributes={};
    }
    get value() { return this.tagName==='SELECT' ? this.children[this.selectedIndex]?.value || '' : this._value; }
    set value(value) { this._value=String(value);if(this.tagName==='SELECT') this.selectedIndex=this.children.findIndex(option=>option.value===String(value)); }
    set textContent(value) { this._text=String(value);this.children=[]; }
    get textContent() { return this._text+this.children.map(node=>node.textContent).join(''); }
    set innerHTML(value) { throw new Error('Unsafe HTML: '+value); }
    get options() { return this.children; }
    get firstElementChild() { return this.children[0]; }
    appendChild(node) { this.children.push(node);if(this.tagName==='SELECT' && this.selectedIndex===-1) this.selectedIndex=0;return node; }
    append(...nodes) { nodes.forEach(node=>this.appendChild(node)); }
    replaceChildren(...nodes) { this.children=[];this._text='';this.selectedIndex=-1;this.append(...nodes); }
    addEventListener(name,fn) { (this.events[name]??=[]).push(fn); }
    getAttribute(name) { return name==='data-price' ? this.dataset.price : this.attributes[name]??null; }
    setAttribute(name,value) { this.attributes[name]=String(value); }
    click() { if(!this.disabled) return Promise.all((this.events.click||[]).map(fn=>fn({preventDefault(){}}))); }
}
const today=new Date(Date.now()+7*3600000).toISOString().slice(0,10);
const day=offset=>new Date(Date.parse(today+'T00:00:00Z')+offset*86400000).toISOString().slice(0,10);
const guest={customerId:1,fullName:`O'Neil <img src=x onerror=alert(1)>`,cccd:'AB123456'};
const room={roomId:1,roomCode:'P1',roomName:'Room',price:300000,status:'Đang sử dụng'};
const booking=(id=1,status='Confirmed',start=0,end=2)=>({bookingId:id,customerName:guest.fullName,roomCode:'P1',phone:'0901234567',
    checkinDate:day(start),checkoutDate:day(end),note:'<script>bad()</script>',status});
function env(module,handlers={}) {
    const nodes=new Map(),events=[],calls=[],redirects=[];
    const server={customers:[guest],rooms:[room],bookings:[]};
    const el=id=>{if(!nodes.has(id)) nodes.set(id,new Element(['booking-customer','booking-room'].includes(id)?'select':id.includes('tbody')?'tbody':'div'));return nodes.get(id);};
    const context=vm.createContext({Node:Element,console,URLSearchParams,setTimeout:()=>0,
        sessionStorage:{removeItem(){}},window:{location:{pathname:'/pages/'+module+'.html',replace:value=>redirects.push(value)},addEventListener(){}},
        document:{getElementById:el,createElement:tag=>new Element(tag),querySelectorAll:()=>[],querySelector:()=>null,addEventListener:(name,fn)=>events.push({name,fn})},
        fetch:async()=>({ok:true,status:200,json:async()=>({success:true,user:{role:'admin'}})}),confirm:()=>true});
    vm.runInContext(fs.readFileSync(path.join(root,'js/api.js'),'utf8'),context);
    vm.runInContext(fs.readFileSync(path.join(root,'js/'+module+'.js'),'utf8'),context);
    context.askConfirmation = async message => context.confirm(message);
    const defaultGet=endpoint=>endpoint==='/customers'?clone(server.customers):endpoint.startsWith('/bookings/available-rooms?')?clone(server.rooms)
        :endpoint==='/bookings?status=Confirmed'?clone(server.bookings.filter(b=>b.status==='Confirmed')):clone(server.bookings);
    context.apiGet=async endpoint=>{calls.push({method:'GET',endpoint});return handlers.get?handlers.get(endpoint,server,defaultGet):defaultGet(endpoint);};
    context.apiPost=async(endpoint,body)=>{calls.push({method:'POST',endpoint,body});if(handlers.post)return handlers.post(endpoint,body,server);
        server.bookings.push({...booking(10),...body});return {success:true,bookingId:10};};
    context.apiPut=async(endpoint,body)=>{calls.push({method:'PUT',endpoint,body});if(handlers.put)return handlers.put(endpoint,body,server);
        const id=Number(endpoint.split('/').find((part,i,parts)=>parts[i-1]==='checkin'||parts[i-1]==='bookings'));
        server.bookings=server.bookings.map(b=>b.bookingId===id?{...b,status:endpoint.endsWith('/cancel')?'Cancelled':'CheckedIn'}:b);
        return {success:true};};
    el('checkin-success').hidden=true;
    const select=(customer=1,roomId=1)=>{el('booking-customer').value=customer;el('booking-room').value=roomId;context.calculateEstimate();context.updateBookingControls();};
    const dates=(start,end)=>{el('booking-checkin-date').value=day(start);el('booking-checkout-date').value=day(end);};
    return {context,el,calls,server,handlers,events,select,dates,redirects};
}
let passed=0;
const pass=name=>{passed++;console.log('PASS '+name);};
const availableCalls=e=>e.calls.filter(call=>call.endpoint.startsWith('/bookings/available-rooms?'));
(async()=>{
    const customers=defer(),rooms=defer();
    let e=env('bookings',{get:(endpoint,server,fallback)=>endpoint==='/customers'?customers.promise:endpoint.startsWith('/bookings/available-rooms?')?rooms.promise:fallback(endpoint)});
    const opening=e.context.openAddModal();
    assert.equal(e.el('booking-save-btn').disabled,true);assert.equal(e.el('booking-room').disabled,true);
    customers.resolve([guest]);await flush();assert.equal(e.el('booking-save-btn').disabled,true);
    rooms.resolve([room]);await opening;e.select();
    assert.equal(e.el('booking-save-btn').disabled,false);assert.equal(e.el('booking-room').children.length,2);
    assert.ok(availableCalls(e)[0].endpoint.includes('checkin='+today));assert.equal(e.calls.some(call=>call.endpoint==='/rooms'),false);
    assert.equal(e.el('total-price-display').textContent,e.context.formatMoney(300000));
    pass('initial loading gates submission and uses date availability without filtering current room status');

    const first=defer(),last=defer();e=env('bookings');await e.context.openAddModal();e.select();
    e.handlers.get=(endpoint,server,fallback)=>endpoint.includes('checkin='+day(3))?first.promise:endpoint.includes('checkin='+day(8))?last.promise:fallback(endpoint);
    e.dates(3,5);const a=e.context.refreshAvailableRooms();
    assert.equal(e.el('booking-room').value,'');assert.equal(e.el('booking-save-btn').disabled,true);assert.equal(e.el('price-preview').style.display,'none');
    e.dates(8,10);const b=e.context.refreshAvailableRooms();last.resolve([{...room,roomId:2,roomCode:'Latest',price:500000}]);await b;e.select(1,2);
    first.resolve([{...room,roomCode:'Stale'}]);await a;
    assert.equal(e.el('booking-room').value,'2');assert.ok(e.el('booking-room').textContent.includes('Latest'));assert.ok(!e.el('booking-room').textContent.includes('Stale'));
    assert.equal(e.el('total-price-display').textContent,e.context.formatMoney(1000000));
    pass('rapid date changes ignore stale responses, clear invalid selection and compute nights for latest dates');

    const lateError=defer();e=env('bookings');await e.context.openAddModal();
    e.handlers.get=(endpoint,server,fallback)=>endpoint.includes('checkin='+day(3))?lateError.promise:fallback(endpoint);
    e.dates(3,5);const stale=e.context.refreshAvailableRooms();e.dates(8,10);await e.context.refreshAvailableRooms();
    const message=e.el('booking-room-status').textContent;lateError.reject(new Error('old error'));await stale;
    assert.equal(e.el('booking-room-status').textContent,message);
    pass('a late failure from older dates cannot overwrite newer successful availability');

    e=env('bookings');await e.context.openAddModal();e.select();const before=availableCalls(e).length;
    for(const [start,end] of [[1,1],[2,1],[-1,1]]) {e.dates(start,end);await e.context.refreshAvailableRooms();assert.equal(e.el('booking-save-btn').disabled,true);}
    e.el('booking-checkin-date').value='2026-02-30';await e.context.refreshAvailableRooms();
    assert.equal(availableCalls(e).length,before);assert.equal(e.el('price-preview').style.display,'none');
    pass('past/equal/reversed/nonexistent dates do not send availability requests or show a valid estimate');

    e=env('bookings');e.server.rooms=[];await e.context.openAddModal();assert.ok(e.el('booking-room-status').textContent.includes('Không có phòng trống'));
    e.handlers.get=(endpoint,server,fallback)=>{if(endpoint.startsWith('/bookings/available-rooms?'))throw new Error('Server unavailable');return fallback(endpoint);};
    await e.context.reloadBookingData();assert.equal(e.el('booking-room-status').textContent,'Server unavailable');assert.equal(e.el('booking-save-btn').disabled,true);
    e.handlers.get=null;e.server.rooms=[room];await e.context.reloadBookingData();e.select();assert.equal(e.el('booking-save-btn').disabled,false);
    pass('empty and failed availability are distinct and explicit reload recovers the form');

    const closedRooms=defer();e=env('bookings',{get:(endpoint,server,fallback)=>endpoint.startsWith('/bookings/available-rooms?')?closedRooms.promise:fallback(endpoint)});
    const closing=e.context.openAddModal();e.context.closeBookingModal();closedRooms.resolve([room]);await closing;
    assert.equal(e.el('booking-modal').classes.has('show'),false);assert.equal(e.el('booking-save-btn').disabled,true);
    assert.equal(e.el('booking-room').children.length,1);
    pass('closing the form invalidates pending loads instead of filling a closed modal');

    const pending=defer();e=env('bookings');await e.context.openAddModal();
    e.handlers.get=(endpoint,server,fallback)=>endpoint.startsWith('/bookings/available-rooms?')?pending.promise:fallback(endpoint);
    e.dates(3,5);const same=e.context.refreshAvailableRooms();const sameAgain=e.context.refreshAvailableRooms();
    assert.equal(same,sameAgain);const count=availableCalls(e).length;pending.resolve([room]);await same;assert.equal(availableCalls(e).length,count);
    pass('input/change events for one pending range share a single availability request');

    const posting=defer();e=env('bookings',{post:async(endpoint,body,server)=>{const result=await posting.promise;server.bookings=[booking(10)];return result;}});
    await e.context.openAddModal();e.select();const saving=e.context.saveBooking();await e.context.saveBooking();
    e.context.closeBookingModal();assert.equal(e.el('booking-modal').classes.has('show'),true);assert.equal(e.el('booking-close-btn').disabled,true);
    assert.equal(e.calls.filter(c=>c.method==='POST').length,1);
    assert.equal(e.el('booking-save-btn').getAttribute('aria-busy'),'true');
    posting.resolve({success:true,bookingId:10});await saving;
    assert.equal(e.el('booking-modal').classes.has('show'),false);assert.equal(e.el('booking-save-btn').disabled,true);assert.ok(e.el('bookings-tbody').textContent.includes('BK10'));
    assert.equal(e.el('booking-save-btn').getAttribute('aria-busy'),'false');
    pass('double submission is blocked and a successful create closes/reset form and reloads bookings');

    e=env('bookings',{post:async(endpoint,body,server)=>{server.rooms=[];server.bookings=[booking(20)];const error=new Error('Room just booked');error.status=409;throw error;}});
    await e.context.openAddModal();e.select();await e.context.saveBooking();
    assert.equal(e.calls.filter(c=>c.method==='POST').length,1);assert.equal(e.el('booking-room').value,'');assert.equal(e.el('booking-save-btn').disabled,true);
    assert.equal(e.el('booking-status').textContent,'Room just booked');assert.ok(e.el('bookings-tbody').textContent.includes('BK20'));
    pass('a booking conflict refreshes calendar/list, clears unavailable selection and never retries the write');

    e=env('bookings',{post:async(endpoint,body,server)=>{server.rooms=[];server.bookings=[booking(30)];throw new Error('Response lost');}});
    await e.context.openAddModal();e.select();await e.context.saveBooking();
    assert.equal(e.calls.filter(c=>c.method==='POST').length,1);assert.equal(e.el('booking-save-btn').disabled,true);assert.ok(e.el('bookings-tbody').textContent.includes('BK30'));
    pass('an uncertain POST result is reconciled with GETs without sending a duplicate reservation');

    e=env('bookings');await e.context.openAddModal();e.select();e.el('booking-note').value='x'.repeat(501);await e.context.saveBooking();assert.equal(e.calls.filter(c=>c.method==='POST').length,0);
    e.el('booking-note').value='';e.server.rooms=[{...room,price:999999999999}];e.dates(0,2);await e.context.refreshAvailableRooms(true);e.select();
    assert.equal(e.el('price-preview').style.display,'none');assert.equal(e.el('booking-save-btn').disabled,true);
    pass('oversized notes and unrepresentable estimated totals are rejected before posting');

    const cancelling=defer();e=env('bookings',{put:async(endpoint,body,server)=>{const result=await cancelling.promise;server.bookings=server.bookings.map(row=>({...row,status:'Cancelled'}));return result;}});
    e.server.bookings=['Confirmed','CheckedIn','CheckedOut','Cancelled'].map((state,i)=>booking(i+1,state));e.context.renderTable(clone(e.server.bookings));
    assert.deepEqual(e.el('bookings-tbody').children.map(row=>row.children[5].textContent),['Đã xác nhận','Đã nhận phòng','Đã trả phòng','Đã hủy']);
    assert.deepEqual(e.server.bookings.map(row=>row.status),['Confirmed','CheckedIn','CheckedOut','Cancelled']);
    assert.equal(e.el('bookings-tbody').children[0].children.at(-1).children.length,1);
    assert.ok(e.el('bookings-tbody').children.slice(1).every(row=>row.children.at(-1).children.length===0));
    const cancel=e.context.cancelBooking(1);await e.context.cancelBooking(1);assert.equal(e.calls.filter(c=>c.method==='PUT').length,1);
    cancelling.resolve({success:true});await cancel;assert.equal(e.el('bookings-tbody').children[0].children.at(-1).children.length,0);
    pass('only Confirmed shows cancel, double cancel sends once, and completion refreshes current status');

    e=env('bookings',{put:async(endpoint,body,server)=>{server.bookings=[booking(1,'CheckedIn')];const error=new Error('Already checked in');error.status=409;throw error;}});
    e.server.bookings=[booking()];e.context.renderTable(clone(e.server.bookings));await e.context.cancelBooking(1);
    assert.equal(e.el('bookings-tbody').children[0].children.at(-1).children.length,0);assert.equal(e.el('alert-message').textContent,'Already checked in');
    pass('cancel conflicts preserve the server message and replace stale cancellable rows');

    const oldList=defer(),newList=defer();let sequence=0;e=env('bookings',{get:()=>++sequence===1?oldList.promise:newList.promise});
    const older=e.context.loadBookings(),newer=e.context.loadBookings();newList.resolve([booking(1,'Cancelled')]);await newer;oldList.resolve([booking(1)]);await older;
    assert.equal(e.el('bookings-tbody').children[0].children.at(-1).children.length,0);
    pass('out-of-order list reloads cannot restore stale cancel actions');

    e=env('checkin');e.context.renderTable([booking(1),booking(2,'Confirmed',1,3),booking(3,'Confirmed',-2,0)]);
    const table=e.el('checkin-tbody');assert.equal(table.children[0].children.length,8);
    assert.equal(table.children[0].children.at(-1).children[0].disabled,false);assert.equal(table.children[1].children.at(-1).children[0].disabled,true);
    assert.equal(table.children[2].children.at(-1).children[0].disabled,true);
    const futureActions=table.children[1].children.at(-1);
    assert.equal(futureActions.children[0].getAttribute('aria-describedby'),futureActions.children[1].id);
    assert.equal(futureActions.children[1].textContent,'Chưa đến ngày nhận phòng.');
    e.context.doCheckin(2);assert.equal(e.el('checkin-modal').classes.has('show'),false);
    e.context.doCheckin(1);assert.ok(e.el('checkin-info').textContent.includes(guest.fullName));assert.equal(e.el('checkin-success').hidden,true);
    assert.ok(e.el('checkin-info').textContent.includes('0901234567'));
    pass('check-in date eligibility is visible and valid selections open a safely rendered confirmation modal');

    const receiving=defer();e=env('checkin',{put:async(endpoint,body,server)=>{const result=await receiving.promise;server.bookings=[];return result;}});
    e.server.bookings=[booking()];e.context.renderTable(clone(e.server.bookings));e.context.doCheckin(1);
    const receivingPromise=e.context.confirmCheckin();await e.context.confirmCheckin();e.context.closeCheckinModal();
    assert.equal(e.calls.filter(c=>c.method==='PUT').length,1);assert.equal(e.el('checkin-close-btn').disabled,true);assert.equal(e.el('checkin-success').hidden,true);
    assert.equal(e.el('checkin-confirm-btn').getAttribute('aria-busy'),'true');
    receiving.resolve({success:true});await receivingPromise;
    assert.equal(e.el('checkin-modal').classes.has('show'),false);assert.equal(e.el('checkin-success').hidden,false);assert.equal(e.el('checkin-confirm-btn').disabled,true);
    assert.equal(e.el('checkin-confirm-btn').getAttribute('aria-busy'),'false');
    pass('double check-in is blocked and the services-navigation card appears only after confirmed success');

    e=env('checkin',{put:async()=>{const error=new Error('Room still occupied');error.status=409;throw error;}});
    e.server.bookings=[booking()];e.context.renderTable(clone(e.server.bookings));e.context.doCheckin(1);await e.context.confirmCheckin();await e.context.confirmCheckin();
    assert.equal(e.calls.filter(c=>c.method==='PUT').length,1);assert.equal(e.el('checkin-confirm-btn').disabled,true);
    assert.ok(e.el('checkin-status').textContent.includes('Room still occupied'));assert.equal(e.el('checkin-success').hidden,true);
    e.context.closeCheckinModal();e.context.doCheckin(1);assert.equal(e.el('checkin-confirm-btn').disabled,false);
    pass('server check-in rejection reloads data and requires explicit reselection rather than automatic retry');

    e=env('checkin');e.context.renderTable([booking()]);e.context.doCheckin(1);
    e.context.vietnamToday=()=>day(2);await e.context.confirmCheckin();assert.equal(e.calls.filter(c=>c.method==='PUT').length,0);assert.equal(e.el('checkin-confirm-btn').disabled,true);
    pass('check-in rechecks the day when confirming, including midnight/departure-day rollover');

    for(const module of ['bookings','checkin']) {
        const source=fs.readFileSync(path.join(root,'js/'+module+'.js'),'utf8');const html=fs.readFileSync(path.join(root,'pages/'+module+'.html'),'utf8');
        for(const match of source.matchAll(/getElementById\('([^']+)'\)/g)) assert.ok(html.includes('id="'+match[1]+'"'),'Missing HTML ID '+match[1]);
        const header=html.match(/<thead>([\s\S]*?)<\/thead>/)[1];assert.equal((header.match(/<th\b[^>]*>/g)||[]).length,8);
        for(const match of html.matchAll(/onclick="([A-Za-z]+)\(/g)) {
            const current=env(module);
            if(module==='bookings')vm.runInContext(fs.readFileSync(path.join(root,'js/booking-calendar.js'),'utf8'),current.context);
            assert.equal(typeof current.context[match[1]],'function','Missing click handler '+match[1]);
        }
    }
    pass('HTML IDs, eight-column tables and static click handlers match the JavaScript');
    console.log('FE-01 frontend: '+passed+' groups passed (instrumented DOM/API).');
})().catch(error=>{console.error(error);process.exitCode=1;});
