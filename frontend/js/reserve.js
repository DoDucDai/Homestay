let reserveRooms=[],reserveAvailableRooms=null,reserveServices=[],reserveQuote=null,reserveVersion=0,reserveAvailabilityVersion=0,reserveCatalogVersion=0,reserveRefreshTimer=null,reserveSaving=false,reserveReady=false,reserveServicesReady=false,reserveUncertain=false;
let reserveQuotePending=false,reserveQuoteError='',reserveStayProblem=null,reserveAttempted=false,reserveServerError=false,reserveProfilePending=false;
const reserveErrorFields=new Set();
const reserveEl=id=>document.getElementById(id);
function reserveHasHourlyPrice(room){return Number.isSafeInteger(Number(room?.hourlyPrice)) && Number(room.hourlyPrice)>0;}
function reservePriceEstimate(){
    const room=reserveRooms.find(row=>row.roomId===Number(reserveEl('stay-room').value));
    let serviceTotal=0;
    for(const service of reserveServices){if(!reserveEl('service-pick-'+service.serviceId)?.checked)continue;const quantity=Number(reserveEl('service-qty-'+service.serviceId).value);if(!Number.isInteger(quantity) || quantity<1 || quantity>10000)return {room,roomTotal:null,serviceTotal:null,grandTotal:null};serviceTotal+=Number(service.unitPrice)*quantity;}
    if(!room)return {room,roomTotal:null,serviceTotal,grandTotal:null};
    try{
        const stay=reserveStay();let total;
        if(stay.billingUnit==='Hour'){
            if(!reserveHasHourlyPrice(room))return {room,roomTotal:null,serviceTotal,grandTotal:null};
            if(stay.checkinAt<vietnamNow().slice(0,16))throw new Error('Past stay');
            const hours=Math.ceil((Date.parse(stay.checkoutAt+'+07:00')-Date.parse(stay.checkinAt+'+07:00'))/3600000);
            total=quoteHourly(room.hourlyPrice,room.hourlyPackages,hours).total;
        }else{
            if(stay.checkinDate<vietnamNow().slice(0,10))throw new Error('Past stay');
            total=quoteNightly(room.price,room.nightlyPricing,stay.checkinDate,stay.checkoutDate).total;
        }
        return {room,roomTotal:total,serviceTotal,grandTotal:total+serviceTotal};
    }catch(_){return {room,roomTotal:null,serviceTotal,grandTotal:null};}
}
function reserveUpdatePricePanel(){
    const preview=reservePriceEstimate(),money=value=>value==null?'Chưa tính':formatMoney(value),room=reserveQuote?.room || preview.room;
    reserveEl('reserve-price-rate').textContent=room?room.roomCode+' · Theo đêm: '+nightlyPriceLabel(room)+' · Theo giờ: '+hourlyPriceLabel(room):'Chọn phòng để xem đơn giá theo giờ và theo đêm.';
    const amounts=reserveQuote || preview;
    reserveEl('reserve-room-amount').textContent=money(amounts.roomTotal);reserveEl('reserve-service-amount').textContent=money(amounts.serviceTotal);reserveEl('reserve-total-amount').textContent=money(amounts.grandTotal);
    let state;
    if(room && reserveEl('stay-unit').value==='Hour' && !reserveHasHourlyPrice(room))state='Phòng này chưa có giá theo giờ. Chọn Theo đêm hoặc chọn phòng khác có giá giờ để đặt. Tổng thanh toán theo giờ chưa thể tính.';
    else if(reserveQuote)state='Giá đã được hệ thống xác nhận cho phòng, thời gian và dịch vụ đang chọn. Kiểm tra tổng tiền trước khi đặt.';
    else if(reserveQuoteError)state=reserveQuoteError+(preview.grandTotal!=null?' Số tiền trên là tạm tính từ bảng giá, chưa xác nhận đặt được phòng.':'');
    else if(reserveQuotePending)state='Đang xác nhận phòng trống và tổng tiền với hệ thống. Số tiền trên là tạm tính.';
    else if(preview.grandTotal!=null)state='Tạm tính từ bảng giá phòng và dịch vụ. Hệ thống kiểm tra lại phòng trống và giá trước khi xác nhận đặt.';
    else state=room?'Chưa tính được tiền phòng. Nhập ngày/giờ hợp lệ; xem lỗi ở mục Thời gian lưu trú.':'Chọn phòng và thời gian để tính tiền phòng, dịch vụ và tổng thanh toán.';
    reserveEl('reserve-price-state').textContent=state;
}
const reserveDisplayDate=value=>value.split('-').reverse().join('/');
function reserveDate(value,label){
    const match=/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(value.trim());
    if(!match)throw new Error(label+' cần nhập theo ngày/tháng/năm, ví dụ 10/11/2026.');
    const iso=match[3]+'-'+match[2].padStart(2,'0')+'-'+match[1].padStart(2,'0'),time=Date.parse(iso+'T00:00:00Z');
    if(Number(match[3])<1 || Number(match[3])>9998 || !Number.isFinite(time) || new Date(time).toISOString().slice(0,10)!==iso)throw new Error(label+' không hợp lệ. Hãy kiểm tra ngày, tháng và năm.');
    return iso;
}
function reserveTime(value,label){
    const match=/^([01]?\d|2[0-3]):([0-5]\d)$/.exec(value.trim());
    if(!match)throw new Error(label+' cần dùng giờ 24h, ví dụ 12:00 hoặc 13:54 (không nhập AM/PM).');
    return match[1].padStart(2,'0')+':'+match[2];
}
function reserveStay(){
    const read=(id,label,parse)=>{try{return parse(reserveEl(id).value,label);}catch(error){error.field=id;throw error;}};
    const start=read('stay-start-date','Ngày nhận',reserveDate),end=read('stay-end-date','Ngày trả',reserveDate);
    return reserveEl('stay-unit').value==='Hour'?{billingUnit:'Hour',checkinAt:start+'T'+read('stay-start-time','Giờ nhận',reserveTime),checkoutAt:end+'T'+read('stay-end-time','Giờ trả',reserveTime)}:{billingUnit:'Night',checkinDate:start,checkoutDate:end};
}
function reserveValidateStay(){
    const error=reserveEl('stay-error'),summary=reserveEl('stay-summary');error.hidden=true;summary.textContent='';reserveStayProblem=null;
    try{
        const stay=reserveStay(),hour=stay.billingUnit==='Hour',start=hour?stay.checkinAt:stay.checkinDate,end=hour?stay.checkoutAt:stay.checkoutDate;
        if(end<=start){const problem=new Error(hour?'Ngày và giờ trả phải sau ngày và giờ nhận. Kiểm tra thứ tự ngày/tháng/năm.':'Ngày trả phải sau ngày nhận. Kiểm tra thứ tự ngày/tháng/năm.');problem.field=hour && end.slice(0,10)===start.slice(0,10)?'stay-end-time':'stay-end-date';throw problem;}
        if(start<(hour?vietnamNow().slice(0,16):vietnamNow().slice(0,10))){const problem=new Error(hour?'Giờ nhận đã ở trong quá khứ theo giờ Việt Nam. Chọn ngày và giờ nhận từ hiện tại trở đi.':'Ngày nhận không được trước ngày hiện tại theo giờ Việt Nam.');problem.field=hour && start.slice(0,10)===vietnamNow().slice(0,10)?'stay-start-time':'stay-start-date';throw problem;}
        if(hour){const minutes=(Date.parse(end+'+07:00')-Date.parse(start+'+07:00'))/60000;summary.textContent='Nhận '+reserveDisplayDate(start.slice(0,10))+' lúc '+start.slice(11)+' → Trả '+reserveDisplayDate(end.slice(0,10))+' lúc '+end.slice(11)+' · '+Math.floor(minutes/60)+' giờ '+minutes%60+' phút · Dự kiến tính '+Math.ceil(minutes/60)+' giờ.';}
        else summary.textContent='Nhận '+reserveDisplayDate(start)+' → Trả '+reserveDisplayDate(end)+'.';
        return stay;
    }catch(problem){reserveStayProblem={id:problem.field || 'stay-start-date',message:problem.message};error.textContent=problem.message;error.hidden=false;return null;}
}
function reserveUpdateSubmitControls(){
    reserveUpdatePricePanel();
    reserveEl('reserve-submit').disabled=reserveSaving;
    reserveEl('reserve-submit').textContent=reserveSaving?'Đang xác nhận…':'Xác nhận đặt phòng';
    reserveEl('reserve-submit-help').textContent=reserveSaving?'Đang gửi yêu cầu đặt phòng. Vui lòng chờ kết quả.':reserveUncertain?'Chưa xác định được kết quả yêu cầu trước. Kiểm tra Đặt phòng của tôi trước khi đặt lại.':reserveQuotePending?'Đang kiểm tra báo giá. Bạn vẫn có thể bấm xác nhận để xem thông tin còn thiếu.':reserveQuote?'Bấm xác nhận để kiểm tra thông tin người đặt và gửi yêu cầu.':reserveQuoteError || 'Chọn phòng và thời gian để lấy báo giá. Bấm xác nhận để xem thông tin còn thiếu.';
    reserveEl('reserve-requote').hidden=!!reserveQuote || !reserveEl('stay-room').value;
    reserveEl('reserve-requote').disabled=reserveSaving || reserveQuotePending;
}
function reserveCollectIssues(){
    const issues=[],add=(id,message)=>issues.push({id,message}),value=id=>reserveEl(id).value.trim();
    const roomId=Number(reserveEl('stay-room').value);
    if(!roomId)add('stay-room','Vui lòng chọn phòng bạn muốn đặt.');
    else if(!reserveRooms.some(room=>room.roomId===roomId))add('stay-room','Phòng đã chọn không còn trong danh mục. Tải lại danh sách phòng và chọn lại.');
    const stay=reserveValidateStay();if(!stay)issues.push(reserveStayProblem);
    if(!value('guest-name'))add('guest-name','Vui lòng nhập họ và tên người đặt phòng.');
    else if(value('guest-name').length>100)add('guest-name','Họ và tên không được vượt quá 100 ký tự.');
    if(!value('guest-phone'))add('guest-phone','Vui lòng nhập số điện thoại liên hệ.');
    else if(!/^0[0-9]{9}$/.test(value('guest-phone')))add('guest-phone','Số điện thoại cần đúng 10 chữ số và bắt đầu bằng 0.');
    const type=value('guest-document-type'),document=value('guest-document');
    if(!['CCCD','PASSPORT'].includes(type))add('guest-document-type','Vui lòng chọn loại giấy tờ: căn cước công dân hoặc hộ chiếu.');
    if(!document)add('guest-document',type==='PASSPORT'?'Vui lòng nhập số hộ chiếu.':'Vui lòng nhập số căn cước công dân.');
    else if(type==='CCCD' && !/^[0-9]{12}$/.test(document))add('guest-document','Số căn cước công dân cần đúng 12 chữ số.');
    else if(type==='PASSPORT' && !/^[A-Za-z0-9]{6,20}$/.test(document))add('guest-document','Số hộ chiếu cần từ 6–20 chữ cái hoặc chữ số.');
    if(value('guest-address').length>200)add('guest-address','Địa chỉ không được vượt quá 200 ký tự.');
    if(value('reserve-note').length>500)add('reserve-note','Ghi chú không được vượt quá 500 ký tự.');
    if(!['Tiền mặt','Chuyển khoản'].includes(value('payment-method')))add('payment-method','Vui lòng chọn thanh toán tại quầy hoặc chuyển khoản.');
    let servicesValid=true;
    for(const service of reserveServices){if(!reserveEl('service-pick-'+service.serviceId)?.checked)continue;const id='service-qty-'+service.serviceId,quantity=Number(reserveEl(id).value);if(!Number.isInteger(quantity) || quantity<1 || quantity>10000){servicesValid=false;add(id,'Số lượng dịch vụ '+service.serviceName+' phải là số nguyên từ 1 đến 10.000.');}}
    if(!reserveReady)add(null,reserveProfilePending?'Hồ sơ tài khoản đang tải. Vui lòng chờ tải xong rồi bấm xác nhận lại.':'Chưa tải được hồ sơ tài khoản. Vui lòng tải lại trang để tiếp tục đặt phòng.');
    if(!reserveServicesReady)add(reserveEl('reload-services').hidden?null:'reload-services','Danh mục dịch vụ chưa tải xong hoặc đang lỗi. Chờ tải xong hoặc bấm Tải lại dịch vụ.');
    if(reserveUncertain)add(null,'Chưa xác định được kết quả đặt phòng trước đó. Hãy kiểm tra Đặt phòng của tôi để tránh tạo đơn lặp.');
    if(!reserveQuote && roomId && stay && servicesValid)add(reserveQuotePending?null:'reserve-requote',reserveQuotePending?'Báo giá đang được kiểm tra. Vui lòng chờ vài giây rồi bấm xác nhận lại.':reserveQuoteError || 'Chưa có báo giá hợp lệ cho phòng và thời gian đã chọn. Bấm Tải lại báo giá trước khi xác nhận.');
    return issues;
}
function reserveShowIssues(issues,focus=false){
    for(const id of reserveErrorFields){reserveEl(id)?.setAttribute('aria-invalid','false');const error=reserveEl(id+'-error');if(error)error.hidden=true;}reserveErrorFields.clear();
    const box=reserveEl('reserve-error');box.replaceChildren();box.hidden=!issues.length;if(!issues.length)return;
    box.append(textNode('p','Chưa thể đặt phòng. Vui lòng kiểm tra:'));
    const list=document.createElement('ul');
    for(const issue of issues){
        const item=document.createElement('li'),field=issue.id?reserveEl(issue.id):null;
        if(field){const link=textNode('a',issue.message);link.href='#'+issue.id;link.addEventListener('click',event=>{event.preventDefault();field.focus();});item.append(link);
            if(['INPUT','SELECT','TEXTAREA'].includes(field.tagName)){reserveErrorFields.add(issue.id);field.setAttribute('aria-invalid','true');let error=reserveEl(issue.id+'-error');if(!error){error=textNode('p','','form-error');error.id=issue.id+'-error';field.after(error);}error.id=issue.id+'-error';error.textContent=issue.message;error.hidden=false;const described=new Set((field.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean));described.add(error.id);field.setAttribute('aria-describedby',[...described].join(' '));}
        }else item.append(textNode('span',issue.message));list.append(item);
    }
    box.append(list);if(reserveUncertain)box.append(customerLink('Kiểm tra Đặt phòng của tôi','my-bookings.html'));
    if(focus){const first=issues.find(issue=>issue.id && ['INPUT','SELECT','TEXTAREA'].includes(reserveEl(issue.id)?.tagName));(first?reserveEl(first.id):box).focus();}
}
function reserveRefreshIssues(){if(reserveAttempted && !reserveSaving && !reserveServerError)reserveShowIssues(reserveCollectIssues());}
function reserveSelectedServices(){return reserveServices.flatMap(service=>{const selected=reserveEl('service-pick-'+service.serviceId);return selected?.checked?[{serviceId:service.serviceId,quantity:Number(reserveEl('service-qty-'+service.serviceId).value)}]:[];});}
function reservePayload(stay=reserveStay()){return {...stay,roomId:Number(reserveEl('stay-room').value),services:reserveSelectedServices()};}
function reserveInvalidateQuote(){reserveVersion++;reserveQuote=null;reserveQuotePending=false;reserveQuoteError='';reserveUpdateSubmitControls();reserveEl('reserve-summary').textContent='Chọn phòng và kiểm tra báo giá trước khi xác nhận.';}
function reserveRenderRooms(){
    const select=reserveEl('stay-room'),chosen=select.value;
    select.replaceChildren(selectOption('',reserveRooms.length?'Chọn phòng để xem ảnh':'Chưa có phòng trong danh mục'));
    for(const room of reserveRooms){
        const available=reserveAvailableRooms?.find(row=>row.roomId===room.roomId);
        const suffix=reserveEl('stay-unit').value==='Hour' && !reserveHasHourlyPrice(room)?' · Chưa có giá theo giờ':reserveAvailableRooms===null?'':available?' · '+formatMoney(available.estimatedRoomTotal):' · Không phù hợp thời gian đã chọn';
        select.append(selectOption(String(room.roomId),room.roomCode+' · '+room.roomName+' · '+room.propertyName+suffix));
    }
    select.value=reserveRooms.some(room=>String(room.roomId)===chosen)?chosen:'';
}
function reserveAvailabilityMessage(){
    const id=Number(reserveEl('stay-room').value);
    const room=reserveRooms.find(row=>row.roomId===id);
    if(room && reserveEl('stay-unit').value==='Hour' && !reserveHasHourlyPrice(room)){reserveEl('rooms-message').textContent='Phòng '+room.roomCode+' chưa có giá theo giờ. Chọn Theo đêm hoặc chọn phòng khác có giá giờ.';return;}
    reserveEl('rooms-message').textContent=reserveAvailableRooms===null?'Chọn phòng để xem ảnh. Hệ thống sẽ kiểm tra phòng theo thời gian bạn nhập.':id && !reserveAvailableRooms.some(room=>room.roomId===id)?'Phòng đã chọn không phù hợp thời gian này. Bạn vẫn có thể xem ảnh; hãy đổi thời gian hoặc chọn phòng khác.':reserveAvailableRooms.length+' phòng phù hợp thời gian đã chọn. Bạn có thể chọn phòng bất kỳ để xem ảnh.';
}
function reserveRefreshStay(){
    clearTimeout(reserveRefreshTimer);reserveAvailabilityVersion++;reserveAvailableRooms=null;reserveInvalidateQuote();reserveRenderRooms();reserveShowRoom();reserveAvailabilityMessage();reserveEl('find-rooms').disabled=false;
    reserveValidateStay();reserveRefreshTimer=setTimeout(reserveFindRooms,350);
}
async function reserveLoadRooms(){
    const version=++reserveCatalogVersion;reserveEl('reload-rooms').hidden=true;
    try{
        const rooms=expectList(await apiGet('/public/rooms'));if(version!==reserveCatalogVersion)return;
        reserveRooms=propertyRooms(rooms);reserveRenderRooms();reserveShowRoom();reserveAvailabilityMessage();
        await reserveFindRooms();
    }catch(error){if(version===reserveCatalogVersion){reserveEl('rooms-message').textContent=error.message || 'Chưa tải được danh sách phòng.';reserveEl('reload-rooms').hidden=false;}}
}
function reserveShowRoom(){
    const room=reserveRooms.find(row=>row.roomId===Number(reserveEl('stay-room').value));
    const photo=reserveEl('room-photo'),copy=reserveEl('room-copy');photo.replaceChildren();copy.replaceChildren();
    if(!room){photo.className='customer-photo-empty';photo.textContent='Chọn phòng để xem hình ảnh';return;}
    const picture=RoomPhotos.create(room,{gallery:true});photo.className=picture?'':'customer-photo-empty';photo.append(picture || textNode('p','Phòng này chưa có ảnh'));
    copy.append(textNode('h2',room.roomCode+' · '+room.roomName),textNode('p',room.roomType+' · '+room.propertyName),textNode('p',room.propertyAddress || ''),textNode('p',room.description || ''),textNode('p',reserveEl('stay-unit').value==='Hour'?hourlyPriceLabel(room):nightlyPriceLabel(room)));
}
async function reserveFindRooms(){
    if(reserveSaving)return;clearTimeout(reserveRefreshTimer);const version=++reserveAvailabilityVersion;reserveAvailableRooms=null;reserveInvalidateQuote();reserveRenderRooms();
    const stay=reserveValidateStay();if(!stay){reserveAvailabilityMessage();return;}
    const query=new URLSearchParams(stay);if(selectedPropertyId())query.set('propertyId',selectedPropertyId());
    reserveEl('rooms-message').textContent='Đang kiểm tra phòng theo thời gian. Bạn vẫn có thể chọn phòng và xem ảnh…';reserveEl('find-rooms').disabled=true;
    try{
        const rooms=expectList(await apiGet('/customer/availability?'+query));if(version!==reserveAvailabilityVersion)return;
        reserveAvailableRooms=rooms;reserveRenderRooms();reserveAvailabilityMessage();await reserveLoadQuote();
    }catch(error){if(version===reserveAvailabilityVersion)reserveEl('rooms-message').textContent=error.message || 'Chưa tìm được phòng. Hãy thử lại.';}
    finally{if(version===reserveAvailabilityVersion)reserveEl('find-rooms').disabled=false;}
}
async function reserveLoadQuote(){
    reserveInvalidateQuote();if(!reserveEl('stay-room').value)return;
    const stay=reserveValidateStay();if(!stay)return;
    const selected=reserveRooms.find(room=>room.roomId===Number(reserveEl('stay-room').value));
    if(stay.billingUnit==='Hour' && !reserveHasHourlyPrice(selected)){reserveQuoteError='Phòng đã chọn chưa có giá theo giờ. Chọn Theo đêm hoặc chọn phòng khác có giá giờ.';reserveEl('reserve-summary').textContent=reserveQuoteError;reserveUpdateSubmitControls();reserveRefreshIssues();return;}
    const version=reserveVersion;reserveQuotePending=true;reserveUpdateSubmitControls();reserveEl('reserve-summary').textContent='Đang kiểm tra giá phòng và dịch vụ…';
    try{
        const quote=await apiPost('/customer/quotes',reservePayload(stay));if(version!==reserveVersion)return;reserveQuote=quote;
        const room=quote.room,summary=reserveEl('reserve-summary');
        const guest=textNode('p','Người đặt: '+reserveEl('guest-name').value);guest.id='reserve-summary-guest';
        summary.replaceChildren(textNode('p',room.roomCode+' · '+room.roomName+' · '+room.propertyName),textNode('p',bookingTime(stay,'checkin')+' → '+bookingTime(stay,'checkout')),textNode('p',stay.billingUnit==='Hour'?quote.billedHours+' giờ tính tiền · Làm tròn lên mỗi giờ':quote.nights+' đêm · Không tính ngày trả'),guest,customerServiceTable(quote.services));
    }catch(error){if(version===reserveVersion){reserveQuote=null;reserveQuoteError=error.message || 'Không tải được báo giá. Bấm Tải lại báo giá để thử lại.';reserveEl('reserve-summary').textContent=reserveQuoteError;}}
    finally{if(version===reserveVersion){reserveQuotePending=false;reserveUpdateSubmitControls();reserveRefreshIssues();}}
}
async function reserveLoadServices(){
    reserveServicesReady=false;reserveInvalidateQuote();reserveEl('reload-services').hidden=true;reserveEl('reserve-services').textContent='Đang tải dịch vụ…';
    try{
        reserveServices=expectList(await apiGet('/customer/services'));const host=reserveEl('reserve-services');host.replaceChildren();
        for(const service of reserveServices){
            const row=textNode('div','','customer-service'),pick=document.createElement('input'),label=textNode('label',service.serviceName),qty=document.createElement('input');
            pick.type='checkbox';pick.id='service-pick-'+service.serviceId;label.htmlFor=pick.id;label.append(textNode('small',formatMoney(service.unitPrice)+' / '+service.unit));
            qty.type='number';qty.min='1';qty.max='10000';qty.step='1';qty.value='1';qty.disabled=true;qty.id='service-qty-'+service.serviceId;qty.setAttribute('aria-label','Số lượng '+service.serviceName+' ('+service.unit+')');
            const amount=textNode('span','Không sử dụng','service-amount');
            const changed=()=>{qty.disabled=!pick.checked;qty.required=pick.checked;if(!pick.checked)qty.value='1';amount.textContent=pick.checked && qty.validity.valid?formatMoney(Number(qty.value)*Number(service.unitPrice)):'Không sử dụng';reserveLoadQuote();};
            pick.addEventListener('change',changed);qty.addEventListener('input',changed);row.append(pick,label,qty,amount);host.append(row);
        }
        if(!reserveServices.length)host.textContent='Hiện chưa có dịch vụ đi kèm.';reserveServicesReady=true;await reserveLoadQuote();
    }catch(error){reserveEl('reserve-services').textContent=error.message;reserveEl('reload-services').hidden=false;}
}
function reserveLock(locked){
    reserveSaving=locked;reserveEl('reserve-form').setAttribute('aria-busy',String(locked));
    if(locked)reserveEl('reserve-form').querySelectorAll('input,select,textarea,button').forEach(el=>{el.dataset.beforeDisabled=String(el.disabled);el.disabled=true;});
    else reserveEl('reserve-form').querySelectorAll('[data-before-disabled]').forEach(el=>{el.disabled=el.dataset.beforeDisabled==='true';delete el.dataset.beforeDisabled;});
    reserveUpdateSubmitControls();
}
async function reserveSubmit(event){
    event.preventDefault();if(reserveSaving)return;
    reserveAttempted=true;reserveServerError=false;
    const issues=reserveCollectIssues();reserveShowIssues(issues,true);if(issues.length)return;
    const body={...reservePayload(),expectedTotal:reserveQuote.grandTotal,expectedQuote:reserveQuote.quoteFingerprint,paymentMethod:reserveEl('payment-method').value,note:reserveEl('reserve-note').value.trim(),profile:{fullName:reserveEl('guest-name').value.trim(),phone:reserveEl('guest-phone').value.trim(),documentType:reserveEl('guest-document-type').value,documentNumber:reserveEl('guest-document').value.trim(),address:reserveEl('guest-address').value.trim()}};
    const reviewedQuote=reserveQuote;
    reserveLock(true);
    try{
        const result=await apiPost('/customer/bookings',body);reserveEl('reserve-form').hidden=true;
        reserveEl('reserve-result').textContent='Mã đơn BK'+result.bookingId+'. '+(body.paymentMethod==='Tiền mặt'?'Đơn đã xác nhận, chưa thanh toán.':'Đơn đang chờ thanh toán. Mở chi tiết để xem hạn giữ phòng và thông tin chuyển khoản.');
        reserveEl('reserve-result-link').href='my-bookings.html?booking='+result.bookingId;reserveEl('reserve-success').hidden=false;reserveEl('reserve-success').focus();
        reserveEl('reserve-result-totals').replaceChildren(customerTotals(reviewedQuote));
    }catch(error){
        reserveServerError=true;let message=error.message || 'Chưa xác nhận được kết quả đặt phòng.';
        if(!error.status || error.status>=500){reserveUncertain=true;message+=' Kiểm tra Đặt phòng của tôi trước khi đặt lại để tránh tạo đơn lặp.';}
        else if(error.code==='PRICE_CHANGED'){await reserveLoadQuote();message='Giá đã thay đổi. Hãy kiểm tra tổng mới và bấm xác nhận lại.';}
        else if(error.status===409 && error.code!=='DOCUMENT_IN_USE'){reserveRefreshStay();message+=' Kiểm tra lại thời gian hoặc chọn phòng khác.';}
        reserveShowIssues([{id:error.code==='DOCUMENT_IN_USE'?'guest-document':null,message}],true);
    }finally{reserveLock(false);}
}
document.addEventListener('DOMContentLoaded',async()=>{
    const user=await customerSession();if(!user)return;
    reserveProfilePending=true;
    const today=vietnamNow().slice(0,10),tomorrow=new Date(Date.parse(today+'T00:00:00Z')+86400000).toISOString().slice(0,10);
    reserveEl('stay-start-date').value=reserveDisplayDate(today);reserveEl('stay-end-date').value=reserveDisplayDate(tomorrow);
    const start=new Date(Math.ceil((Date.now()+7*3600000)/60000)*60000).toISOString().slice(0,16),end=new Date(Date.parse(start+'Z')+2*3600000).toISOString().slice(0,16);
    reserveEl('stay-start-time').value=start.slice(11);reserveEl('stay-end-time').value=end.slice(11);
    let datesEdited=false;
    reserveEl('stay-unit').addEventListener('change',()=>{
        const hour=reserveEl('stay-unit').value==='Hour';reserveEl('hour-fields').hidden=!hour;
        reserveEl('hour-fields').querySelectorAll('input').forEach(el=>{el.disabled=!hour;el.required=hour;});
        if(!datesEdited){reserveEl('stay-start-date').value=reserveDisplayDate(hour?start.slice(0,10):today);reserveEl('stay-end-date').value=reserveDisplayDate(hour?end.slice(0,10):tomorrow);}
        reserveEl('stay-help').textContent=hour?'Ngày dd/mm/yyyy · Giờ 24h: 00:00 là nửa đêm, 12:00 là buổi trưa, 13:54 là 1 giờ 54 chiều. Thời lượng làm tròn lên mỗi giờ. Giờ Việt Nam.':'Nhập ngày theo dd/mm/yyyy. Ngày trả không tính thêm đêm. Thời gian theo giờ Việt Nam.';
        reserveRefreshStay();
    });
    const requested=new URLSearchParams(location.search),date=requested.get('date'),hour=requested.get('hour');
    if(date && /^\d{4}-\d{2}-\d{2}$/.test(date) && date>=today && Number.isFinite(Date.parse(date+'T00:00:00Z'))){
        datesEdited=true;reserveEl('stay-start-date').value=reserveDisplayDate(date);reserveEl('stay-end-date').value=reserveDisplayDate(new Date(Date.parse(date+'T00:00:00Z')+86400000).toISOString().slice(0,10));
        if(requested.get('mode')==='hours' && /^\d{2}$/.test(hour || '') && Number(hour)<24 && date+'T'+hour+':00'>=vietnamNow().slice(0,16)){
            const at=date+'T'+hour+':00',until=new Date(Date.parse(at+'Z')+2*3600000).toISOString().slice(0,16);reserveEl('stay-start-time').value=at.slice(11);reserveEl('stay-end-time').value=until.slice(11);reserveEl('stay-end-date').value=reserveDisplayDate(until.slice(0,10));reserveEl('stay-unit').value='Hour';reserveEl('stay-unit').dispatchEvent(new Event('change'));
        }
    }
    for(const id of ['stay-start-date','stay-end-date','stay-start-time','stay-end-time']){
        reserveEl(id).addEventListener('input',()=>{if(id.endsWith('-date'))datesEdited=true;reserveRefreshStay();});
        reserveEl(id).addEventListener('blur',()=>{try{reserveEl(id).value=id.endsWith('-date')?reserveDisplayDate(reserveDate(reserveEl(id).value,'Ngày')):reserveTime(reserveEl(id).value,'Giờ');}catch(_){/* Keep incomplete input for correction. */}});
    }
    reserveEl('find-rooms').addEventListener('click',reserveFindRooms);reserveEl('stay-room').addEventListener('change',()=>{reserveShowRoom();reserveAvailabilityMessage();reserveLoadQuote();});reserveEl('reload-services').addEventListener('click',reserveLoadServices);reserveEl('reload-rooms').addEventListener('click',reserveLoadRooms);reserveEl('reserve-requote').addEventListener('click',reserveLoadQuote);
    reserveEl('guest-name').addEventListener('input',()=>{const guest=reserveEl('reserve-summary-guest');if(guest)guest.textContent='Người đặt: '+reserveEl('guest-name').value;});
    reserveEl('payment-method').addEventListener('change',()=>{reserveEl('payment-help').textContent=reserveEl('payment-method').value==='Tiền mặt'?'Đơn được xác nhận, chưa thanh toán. Nhân viên xác nhận thu tiền tại quầy.':'Thời hạn giữ phòng được ghi khi tạo đơn. Báo đã chuyển khoản chỉ gửi yêu cầu đối soát; nhân viên xác nhận nhận tiền trước khi xuất hóa đơn.';});
    reserveEl('reserve-form').addEventListener('submit',reserveSubmit);
    for(const event of ['input','change'])reserveEl('reserve-form').addEventListener(event,()=>{reserveServerError=false;reserveRefreshIssues();});
    // Fetch independently, inspect both outcomes, and never book using a failed profile/catalog load.
    const results=await Promise.allSettled([apiGet('/customer/profile'),reserveLoadServices(),reserveLoadRooms()]);
    reserveProfilePending=false;
    if(results[0].status==='fulfilled'){
        const p=results[0].value.profile;for(const [id,key] of [['guest-name','fullName'],['guest-phone','phone'],['guest-document-type','documentType'],['guest-document','documentNumber'],['guest-address','address']])if(p[key])reserveEl(id).value=p[key];reserveEl('guest-email').textContent=p.email?'Email: '+p.email:'Cập nhật email trong Tài khoản nếu cần nhận thông báo.';reserveReady=true;reserveEl('reserve-message').textContent='Thông tin người đặt chỉ dùng cho đơn của bạn.';
    }else{reserveEl('reserve-message').textContent='Chưa tải được hồ sơ. Tải lại trang để tiếp tục. '+results[0].reason.message;}
    if(results[1].status==='rejected')reserveEl('reserve-message').textContent='Chưa tải được dịch vụ. Hãy tải lại dịch vụ để tiếp tục.';
    reserveUpdateSubmitControls();reserveRefreshIssues();
});
window.addEventListener('pagehide',()=>{clearTimeout(reserveRefreshTimer);reserveVersion++;reserveAvailabilityVersion++;reserveCatalogVersion++;});
