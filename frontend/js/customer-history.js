let customerRows=[],customerListVersion=0,customerDetailVersion=0,customerActionBusy=false,customerExpiryTimer=null,customerRefreshTimer=null;
const customerHistoryEl=id=>document.getElementById(id);
const customerInvoicesView=()=>document.body.dataset.customerView==='invoices';
function customerHistoryError(error){customerHistoryEl('customer-detail').replaceChildren(textNode('p',error.message || 'Chưa tải được chi tiết. Hãy thử lại.','alert alert-error'));}
function customerRenderList(){
    const host=customerHistoryEl('customer-list'),filter=customerHistoryEl('customer-status-filter')?.value || '';
    const rows=customerRows.filter(row=>!filter || row.status===filter);host.replaceChildren();
    customerHistoryEl('customer-list-status').textContent=rows.length+(customerInvoicesView()?' hóa đơn đã thanh toán.':' đơn đặt phòng.');
    if(!rows.length){host.append(textNode('p',customerInvoicesView()?'Chưa có hóa đơn đã thanh toán. Bạn vẫn có thể xem tất cả đơn tại Đặt phòng của tôi.':'Chưa có đơn phù hợp. Chọn Đặt phòng mới để bắt đầu.','customer-empty'));return;}
    for(const row of rows){
        const card=textNode('article','','card customer-order'),top=textNode('div','','customer-order-top');
        top.append(textNode('h2',customerInvoicesView()?'HĐ'+row.paymentId+' · BK'+row.bookingId:'BK'+row.bookingId),customerInvoicesView()?textNode('span','Đã thanh toán','badge badge-info'):customerStatus(row));card.append(top);
        card.append(textNode('p',(row.roomCode || '')+' · '+(row.roomName || '')));
        if(customerInvoicesView())card.append(textNode('p',formatMoney(row.grandTotal)+' · '+(row.paymentMethod==='Tiền mặt'?'Thanh toán tại quầy':row.paymentMethod)),textNode('p','Thời điểm thu tiền: '+String(row.paidAt || '').slice(0,19).replace('T',' ')+' (giờ VN)'));
        else card.append(textNode('p',row.propertyName || ''),textNode('p',bookingTime(row,'checkin')+' → '+bookingTime(row,'checkout')));
        const link=customerLink(customerInvoicesView()?'Xem hóa đơn':'Xem chi tiết',customerInvoicesView()?'?invoice='+row.paymentId:'?booking='+row.bookingId);
        link.addEventListener('click',event=>{event.preventDefault();if(customerActionBusy)return;history.replaceState(null,'',link.href);customerOpenDetail(customerInvoicesView()?row.paymentId:row.bookingId,true);});card.append(link);host.append(card);
    }
}
async function customerLoadList(){
    if(customerActionBusy)return;const version=++customerListVersion;
    customerRows=[];customerHistoryEl('customer-list').replaceChildren();customerHistoryEl('customer-list-status').textContent='Đang tải dữ liệu của bạn…';customerHistoryEl('customer-refresh').disabled=true;
    try{const rows=expectList(await apiGet(customerInvoicesView()?'/customer/invoices':'/customer/bookings'));if(version!==customerListVersion)return;customerRows=rows;customerRenderList();}
    catch(error){if(version===customerListVersion){customerRows=[];customerHistoryEl('customer-list-status').textContent=error.message || 'Không tải được dữ liệu. Bấm Làm mới để thử lại.';}}
    finally{if(version===customerListVersion)customerHistoryEl('customer-refresh').disabled=false;}
}
function customerRenderInvoice(invoice){
    const card=textNode('article','','customer-invoice');
    card.append(textNode('p','HOMESTAY','customer-eyebrow'),textNode('h2','Hóa đơn HĐ'+invoice.paymentId),textNode('p','Đã thanh toán · BK'+invoice.bookingId));
    const meta=textNode('div','','customer-invoice-meta');
    for(const line of ['Người đặt: '+invoice.customerName,'Điện thoại: '+invoice.customerPhone,'Phòng: '+invoice.roomCode+' · '+invoice.roomName,'Lưu trú: '+bookingTime(invoice,'checkin')+' → '+bookingTime(invoice,'checkout'),'Thu tiền: '+String(invoice.paidAt || '').replace('T',' ')+' (giờ VN)','Phương thức: '+(invoice.paymentMethod==='Tiền mặt'?'Thanh toán tại quầy':invoice.paymentMethod)])meta.append(textNode('p',line));
    card.append(meta,customerServiceTable(invoice.services),customerTotals(invoice));
    if(invoice.snapshotEstimated)card.append(textNode('p','Hóa đơn cũ có giá được phục dựng. Liên hệ nhân viên nếu cần đối chiếu.'));
    if(invoice.reconciliationWarning)card.append(textNode('p',invoice.reconciliationWarning));
    const print=actionButton('In / lưu PDF','btn btn-primary no-print',()=>window.print());card.append(print);return card;
}
function customerRenderExpiry(payment,host,version){
    clearInterval(customerExpiryTimer);
    if(payment.paymentStatus!=='Unpaid' || payment.status!=='Confirmed' || !payment.reservationExpiresAt)return;
    const expires=Date.parse(payment.reservationExpiresAt),server=Date.parse(payment.serverTime),started=performance.now();
    if(!Number.isFinite(expires) || !Number.isFinite(server))return;
    const timer=textNode('p','','customer-countdown');host.append(timer);
    let requested=false;
    const tick=()=>{if(version!==customerDetailVersion){clearInterval(customerExpiryTimer);return;}
        const remaining=Math.max(0,Math.ceil((expires-server-(performance.now()-started))/1000));
        timer.textContent=remaining?'Thời gian giữ phòng còn '+Math.floor(remaining/60)+':'+String(remaining%60).padStart(2,'0'):'Đang kiểm tra hạn giữ phòng với hệ thống…';
        if(!remaining && !requested){requested=true;clearInterval(customerExpiryTimer);if(!customerActionBusy)customerOpenDetail(payment.bookingId,false);}
    };tick();if(!requested)customerExpiryTimer=setInterval(tick,1000);
}
function customerRenderTransfer(payment,host,version){
    const transfer=textNode('section','','customer-transfer');transfer.append(textNode('h3','Thông tin thanh toán'));
    if(payment.paymentStatus==='Paid'){transfer.append(textNode('p','Số tiền đã thanh toán: '+formatMoney(payment.paidTotal ?? payment.grandTotal),'customer-payment-amount'),textNode('p','Hệ thống đã ghi nhận thanh toán. Hóa đơn có trong Hóa đơn của tôi.'));host.append(transfer);return;}
    if(['Cancelled','Expired'].includes(payment.status)){transfer.append(textNode('p','Đơn không còn hiệu lực. Vui lòng tìm phòng và tạo đơn mới nếu cần.'));host.append(transfer);return;}
    transfer.append(textNode('p','Số tiền cần thanh toán: '+formatMoney(payment.grandTotal),'customer-payment-amount'));
    if(payment.paymentMethod!=='Chuyển khoản'){transfer.append(textNode('p','Thanh toán tại quầy · Chưa thanh toán. Nhân viên xác nhận thu tiền; hóa đơn xuất hiện sau khi xác nhận.'));host.append(transfer);return;}
    if(payment.paymentStatus==='PendingReview'){transfer.append(textNode('p','Chờ xác nhận: yêu cầu đối soát đã được ghi nhận. Đơn được giữ trong khi nhân viên kiểm tra.'));host.append(transfer);return;}
    customerRenderExpiry(payment,transfer,version);
    if(!payment.transferConfigured || !payment.transferAccount){transfer.append(textNode('p','Chưa có thông tin nhận chuyển khoản. Vui lòng liên hệ nhân viên trước khi thanh toán.'));host.append(transfer);return;}
    const account=payment.transferAccount,dl=document.createElement('dl');
    for(const [label,value] of [['Ngân hàng',account.bankName],['Số tài khoản',account.accountNumber],['Chủ tài khoản',account.accountName],['Số tiền',formatMoney(payment.grandTotal)],['Nội dung',payment.reference]]){const item=document.createElement('div');item.append(textNode('dt',label),textNode('dd',value));dl.append(item);}transfer.append(dl);
    // Encodes configured instructions only. No third-party QR service receives booking/account data.
    // A bank-app-compatible VietQR requires an acquiring bank BIN and is a separate integration.
    try{const qr=qrcode(0,'M');const payload=JSON.stringify({bank:account.bankName,account:account.accountNumber,name:account.accountName,amount:payment.grandTotal,reference:payment.reference}).replace(/[\u007f-\uffff]/g,char=>'\\u'+char.charCodeAt(0).toString(16).padStart(4,'0'));qr.addData(payload,'Byte');qr.make();const count=qr.getModuleCount(),scale=5,canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');canvas.width=canvas.height=(count+8)*scale;ctx.fillStyle='#fff';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.fillStyle='#000';for(let y=0;y<count;y++)for(let x=0;x<count;x++)if(qr.isDark(y,x))ctx.fillRect((x+4)*scale,(y+4)*scale,scale,scale);canvas.setAttribute('role','img');canvas.setAttribute('aria-label','QR thông tin thanh toán BK'+payment.bookingId);transfer.append(canvas,textNode('p','QR chứa thông tin nhận tiền để đọc bằng trình quét QR. Dùng các thông tin bên trên để chuyển khoản; QR này chưa liên kết ứng dụng ngân hàng.'));}
    catch(_){transfer.append(textNode('p','Không hiển thị được QR. Sử dụng thông tin nhận tiền bên trên.'));}
    if(payment.canReportTransfer)transfer.append(actionButton('Tôi đã chuyển khoản','btn btn-primary',()=>customerBookingAction(payment.bookingId,'report')));
    transfer.append(textNode('p','Mở hoặc quét QR không xác nhận tiền đã về. Nhân viên kiểm tra trước khi xuất hóa đơn.'));host.append(transfer);
}
async function customerOpenDetail(id,focus=false){
    if(customerActionBusy)return;const version=++customerDetailVersion;clearInterval(customerExpiryTimer);
    const host=customerHistoryEl('customer-detail');host.hidden=false;host.replaceChildren(textNode('p','Đang tải chi tiết…'));if(focus)host.focus();
    try{
        if(customerInvoicesView()){
            const response=await apiGet('/customer/invoices/'+id),invoice=response.invoice;if(version!==customerDetailVersion)return;
            if(!invoice?.paymentId || invoice.paymentStatus!=='Đã thanh toán')throw new Error('Hóa đơn đã lưu chưa đầy đủ. Liên hệ nhân viên để kiểm tra.');
            host.replaceChildren(customerRenderInvoice(invoice));return;
        }
        const results=await Promise.allSettled([apiGet('/customer/bookings/'+id),apiGet('/customer/bookings/'+id+'/payment')]);if(version!==customerDetailVersion)return;
        if(results[0].status==='rejected')throw results[0].reason;
        const row=results[0].value,card=textNode('article','','card'),preview=row.preview;
        card.append(textNode('h2','Đơn BK'+row.bookingId),customerStatus(row),textNode('p',row.roomCode+' · '+row.roomName+' · '+row.propertyName),textNode('p',bookingTime(row,'checkin')+' → '+bookingTime(row,'checkout')),textNode('p','Người đặt: '+preview.customerName),customerServiceTable(preview.services),customerTotals(preview));
        if(row.note)card.append(textNode('p','Ghi chú: '+row.note));
        const actions=textNode('div','','customer-actions');
        if(row.status==='Confirmed' && row.paymentStatus==='Unpaid')actions.append(actionButton('Hủy đơn','btn btn-danger',()=>customerBookingAction(id,'cancel')));
        else if(!['Cancelled','Expired','CheckedOut'].includes(row.status))actions.append(customerLink('Liên hệ hỗ trợ','support.html'));
        if(row.paymentStatus==='Paid' && row.paymentId)actions.append(customerLink('Xem hóa đơn','my-invoices.html?invoice='+row.paymentId));
        card.append(actions);host.replaceChildren(card);
        if(results[1].status==='fulfilled')customerRenderTransfer(results[1].value,card,version);
        else card.append(textNode('p','Chưa tải được thông tin thanh toán. Bấm Làm mới để thử lại. '+results[1].reason.message));
    }catch(error){if(version===customerDetailVersion)customerHistoryError(error);}
}
async function customerBookingAction(id,action){
    if(customerActionBusy)return;
    if(!await askConfirmation(action==='cancel'?'Hủy đơn BK'+id+'? Phòng sẽ được giải phóng.':'Bạn đã chuyển khoản đúng số tiền và nội dung của BK'+id+'? Yêu cầu sẽ được gửi cho nhân viên kiểm tra.'))return;
    if(customerActionBusy)return;customerActionBusy=true;
    customerHistoryEl('customer-detail').querySelectorAll('button').forEach(button=>button.disabled=true);
    try{const result=action==='cancel'?await apiPut('/customer/bookings/'+id+'/cancel',{}):await apiPost('/customer/bookings/'+id+'/payment-report',{});showAlert(result.message || 'Đã ghi nhận yêu cầu.');}
    catch(error){showAlert((error.message || 'Chưa xác nhận được kết quả.')+' Hãy kiểm tra lại trạng thái đơn trước khi thử tiếp.','error');}
    finally{customerActionBusy=false;await Promise.allSettled([customerLoadList(),customerOpenDetail(id,false)]);}
}
async function customerRefresh(){
    if(customerActionBusy)return;const params=new URLSearchParams(location.search),id=Number(params.get(customerInvoicesView()?'invoice':'booking') || params.get(customerInvoicesView()?'paymentId':'bookingId'));
    await Promise.allSettled([customerLoadList(),Number.isSafeInteger(id)&&id>0?customerOpenDetail(id,false):Promise.resolve()]);
}
document.addEventListener('DOMContentLoaded',async()=>{
    if(!await customerSession())return;
    customerHistoryEl('customer-refresh').addEventListener('click',customerRefresh);customerHistoryEl('customer-status-filter')?.addEventListener('change',customerRenderList);
    await customerRefresh();
    if(!customerInvoicesView())customerRefreshTimer=setInterval(()=>{if(!document.hidden && !customerActionBusy && !document.querySelector('.confirmation-overlay'))customerRefresh();},30000);
});
window.addEventListener('pagehide',()=>{clearInterval(customerExpiryTimer);clearInterval(customerRefreshTimer);customerListVersion++;customerDetailVersion++;customerRows=[];customerHistoryEl('customer-list')?.replaceChildren();customerHistoryEl('customer-detail')?.replaceChildren();});
