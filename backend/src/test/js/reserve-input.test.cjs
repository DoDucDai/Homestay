const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {env,root}=require('./frontend-test-env.cjs');

(async()=>{
    const e=env('reserve');
    vm.runInContext(fs.readFileSync(path.join(root,'customer-common.js'),'utf8'),e.context);
    e.context.vietnamNow=()=> '2026-11-10T11:00:00';
    e.context.RoomPhotos={create:()=>null};
    const room={roomId:1,roomCode:'P101',roomName:'Phòng 101',roomType:'Double',propertyName:'Cơ sở 1',price:400000,hourlyPrice:100000};
    const setStay=(endDate='10/11/2026',startTime='12:00',endTime='13:54')=>{
        for(const [id,value] of Object.entries({'stay-unit':'Hour','stay-start-date':'10/11/2026','stay-end-date':endDate,'stay-start-time':startTime,'stay-end-time':endTime}))e.el(id).value=value;
    };
    setStay();
    let stay=e.context.reserveValidateStay();
    assert.equal(stay.checkinAt,'2026-11-10T12:00');
    assert.equal(stay.checkoutAt,'2026-11-10T13:54');
    assert.match(e.el('stay-summary').textContent,/1 giờ 54 phút/);
    assert.match(e.el('stay-summary').textContent,/tính 2 giờ/);
    assert.equal(e.context.reserveTime('00:00','Giờ'),'00:00');
    assert.equal(e.context.reserveTime('13:54','Giờ'),'13:54');
    assert.throws(()=>e.context.reserveTime('13:54 PM','Giờ'),/24h/);
    assert.throws(()=>e.context.reserveDate('31/11/2026','Ngày'),/không hợp lệ/);
    assert.equal(e.context.reserveDate('29/02/2028','Ngày'),'2028-02-29');

    // A reversed date must not prevent loading or selecting the public room catalog.
    setStay('11/10/2026');
    const reads=[];
    e.context.apiGet=async endpoint=>{reads.push(endpoint);return [room];};
    await e.context.reserveLoadRooms();
    assert.deepEqual(reads,['/public/rooms']);
    assert.equal(e.el('stay-room').disabled,false);
    assert.equal(e.el('stay-room').options.length,2);
    e.el('stay-room').value='1';e.context.reserveShowRoom();
    assert.match(e.el('room-copy').textContent,/P101/);
    assert.equal(e.context.reserveValidateStay(),null);
    assert.match(e.el('stay-error').textContent,/trả phải sau/);
    assert.equal(e.el('stay-room').value,'1');
    setStay('10/11/2026','00:00');
    assert.equal(e.context.reserveValidateStay(),null);
    assert.match(e.el('stay-error').textContent,/quá khứ/);

    // An older availability response cannot replace the current room/time result.
    setStay();
    const pending=[];
    e.context.apiGet=()=>new Promise(resolve=>pending.push(resolve));
    e.context.apiPost=async(_endpoint,body)=>({room,services:[],roomTotal:200000,serviceTotal:0,grandTotal:200000,billedHours:2,quoteFingerprint:'current'});
    const first=e.context.reserveFindRooms();
    e.el('stay-end-time').value='14:00';
    const second=e.context.reserveFindRooms();
    pending[1]([{...room,estimatedRoomTotal:200000}]);await second;
    pending[0]([]);await first;
    assert.equal(e.el('stay-room').value,'1');
    assert.doesNotMatch(e.el('rooms-message').textContent,/không phù hợp/);
    assert.match(e.el('reserve-summary').textContent,/14:00/);
    assert.match(e.el('room-copy').textContent,/P101/);
    assert.equal(e.el('reserve-room-amount').textContent,'200.000 VNĐ');
    assert.equal(e.el('reserve-service-amount').textContent,'0 VNĐ');
    assert.equal(e.el('reserve-total-amount').textContent,'200.000 VNĐ');

    // Missing hourly rates are explicit; existing nightly rates remain visible and bookable.
    const priced=env('reserve');
    vm.runInContext(fs.readFileSync(path.join(root,'customer-common.js'),'utf8'),priced.context);
    priced.context.vietnamNow=()=> '2026-11-10T11:00:00';
    const nightlyOnly={roomId:9,roomCode:'Room 101',roomName:'Phòng 101',roomType:'Double',propertyName:'Cơ sở 1',price:400000,hourlyPrice:null};
    priced.context.catalogRoom=nightlyOnly;
    vm.runInContext('reserveRooms=[catalogRoom];',priced.context);
    for(const [id,value] of Object.entries({'stay-unit':'Hour','stay-room':'9','stay-start-date':'11/11/2026','stay-end-date':'11/11/2026','stay-start-time':'12:00','stay-end-time':'13:54'}))priced.el(id).value=value;
    await priced.context.reserveLoadQuote();
    assert.equal(priced.writes.length,0,'No hourly quote request for a room without hourly pricing');
    assert.match(priced.el('reserve-price-rate').textContent,/400.000 VNĐ/);
    assert.match(priced.el('reserve-price-state').textContent,/chưa có giá theo giờ/);
    assert.equal(priced.el('reserve-total-amount').textContent,'Chưa tính','Never display a missing rate as a free booking');
    priced.el('stay-unit').value='Night';priced.el('stay-end-date').value='12/11/2026';
    vm.runInContext('reserveServices=[{serviceId:7,serviceName:"Nước",unit:"chai",unitPrice:20000}];',priced.context);
    priced.el('service-pick-7').checked=true;priced.el('service-qty-7').value='2';
    priced.context.reserveInvalidateQuote();
    assert.equal(priced.el('reserve-room-amount').textContent,'400.000 VNĐ');
    assert.equal(priced.el('reserve-service-amount').textContent,'40.000 VNĐ');
    assert.equal(priced.el('reserve-total-amount').textContent,'440.000 VNĐ');
    priced.context.apiPost=async(endpoint,body)=>{assert.equal(endpoint,'/customer/quotes');assert.equal(body.services[0].quantity,2);return {room:nightlyOnly,services:[{serviceName:'Nước',unit:'chai',quantity:2,unitPrice:20000,totalPrice:40000}],roomTotal:400000,serviceTotal:40000,grandTotal:440000,nights:1,quoteFingerprint:'reviewed'};};
    await priced.context.reserveLoadQuote();
    assert.equal(priced.el('reserve-total-amount').textContent,'440.000 VNĐ');
    assert.match(priced.el('reserve-price-state').textContent,/hệ thống xác nhận/);
    priced.context.apiPost=async()=>({room:{...nightlyOnly,price:450000},services:[],roomTotal:450000,serviceTotal:40000,grandTotal:490000,nights:1,quoteFingerprint:'updated'});
    await priced.context.reserveLoadQuote();
    assert.match(priced.el('reserve-price-rate').textContent,/450.000 VNĐ/,'Displayed unit price must use the server-confirmed quote');
    assert.equal(priced.el('reserve-total-amount').textContent,'490.000 VNĐ');
    // Submit must explain every missing field even when there is no quote yet.
    const v=env('reserve');
    vm.runInContext(fs.readFileSync(path.join(root,'customer-common.js'),'utf8'),v.context);
    v.context.vietnamNow=()=> '2026-11-10T11:00:00';
    for(const id of ['guest-name','guest-phone','guest-document','guest-address','reserve-note','stay-start-date','stay-end-date'])v.el(id).tagName='INPUT';
    v.el('stay-room').tagName='SELECT';v.el('guest-document-type').tagName='SELECT';
    v.el('stay-unit').value='Night';v.el('stay-start-date').value='11/11/2026';v.el('stay-end-date').value='12/11/2026';v.el('guest-document-type').value='CCCD';v.el('payment-method').value='Tiền mặt';
    v.context.reserveInvalidateQuote();assert.equal(v.el('reserve-submit').disabled,false,'The button must remain clickable to explain missing information');
    await v.context.reserveSubmit({preventDefault(){}});
    const message=v.el('reserve-error').textContent;
    for(const expected of [/chọn phòng/,/họ và tên/,/số điện thoại/,/căn cước/])assert.match(message,expected);
    assert.equal(v.el('guest-phone').getAttribute('aria-invalid'),'true');
    assert.equal(v.el('guest-phone-error').hidden,false);
    assert.equal(v.writes.length,0);

    vm.runInContext('reserveRooms=[{roomId:1}];reserveReady=true;reserveServicesReady=true;',v.context);
    v.el('stay-room').value='1';v.el('guest-name').value='Khách local';v.el('guest-phone').value='123';v.el('guest-document').value='123';
    await v.context.reserveSubmit({preventDefault(){}});
    assert.match(v.el('reserve-error').textContent,/10 chữ số/);
    assert.match(v.el('reserve-error').textContent,/12 chữ số/);
    assert.match(v.el('reserve-error').textContent,/Chưa có báo giá/);
    assert.equal(v.writes.length,0);

    v.el('guest-phone').value='0900000001';v.el('guest-document').value='123456789012';
    vm.runInContext('reserveQuotePending=true;',v.context);
    await v.context.reserveSubmit({preventDefault(){}});
    assert.match(v.el('reserve-error').textContent,/Báo giá đang được kiểm tra/);
    vm.runInContext('reserveQuotePending=false;reserveQuoteError="Phòng không còn trống trong thời gian đã chọn.";',v.context);
    await v.context.reserveSubmit({preventDefault(){}});
    assert.match(v.el('reserve-error').textContent,/Phòng không còn trống/);
    assert.equal(v.writes.length,0);

    // A server document conflict must identify the document field and preserve the form.
    vm.runInContext('reserveQuote={grandTotal:400000,quoteFingerprint:"reviewed"};',v.context);
    v.context.apiPost=async()=>{const error=new Error('Giấy tờ đã có hồ sơ. Vui lòng liên hệ quản trị viên để kiểm tra.');error.status=409;error.code='DOCUMENT_IN_USE';throw error;};
    await v.context.reserveSubmit({preventDefault(){}});
    assert.match(v.el('reserve-error').textContent,/Giấy tờ đã có hồ sơ/);
    assert.equal(v.el('guest-document').getAttribute('aria-invalid'),'true');
    assert.equal(v.el('guest-name').value,'Khách local');
    assert.equal(v.el('reserve-submit').disabled,false);

    // A valid submission still sends exactly once while its response is pending.
    let complete,requests=0;
    v.context.apiPost=()=>{requests++;return new Promise(resolve=>{complete=resolve;});};
    const submitting=v.context.reserveSubmit({preventDefault(){}});
    assert.equal(v.el('reserve-submit').disabled,true);
    await v.context.reserveSubmit({preventDefault(){}});assert.equal(requests,1);
    complete({success:true,bookingId:99});await submitting;
    assert.equal(v.el('reserve-success').hidden,false);
    assert.match(v.el('reserve-result').textContent,/BK99/);
    console.log('Room selection, dates/times, stale responses and actionable submit feedback passed (mock APIs only).');
})().catch(error=>{console.error(error);process.exitCode=1;});
