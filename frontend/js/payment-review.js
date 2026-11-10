let reviewVersion=0,reviewDetailVersion=0,reviewBusy=false,reviewTimer=null;
async function loadPaymentReview(){
    if(reviewBusy)return;const version=++reviewVersion,host=document.getElementById('review-list');host.replaceChildren();document.getElementById('review-status').textContent='Đang tải đơn chưa thu tiền…';
    try{
        const property=selectedPropertyId(),rows=expectList(await apiGet('/admin/booking-payments'+(property?'?propertyId='+property:'')));if(version!==reviewVersion)return;
        document.getElementById('review-status').textContent=rows.length+' đơn chưa thu tiền.';
        for(const row of rows){const card=textNode('article','','card customer-order');card.append(textNode('h2','BK'+row.bookingId+' · '+row.roomCode),textNode('p',row.customerName),textNode('p',formatMoney(row.grandTotal)+' · '+(row.paymentMethod==='Tiền mặt'?'Thanh toán tại quầy':row.paymentMethod || 'Chưa chọn phương thức')));
            if(row.reviewStatus==='Pending')card.append(textNode('p','Chờ đối soát '+Math.floor(row.waitingSeconds/60)+' phút'+(row.reviewOverdue?' · Quá thời gian xử lý, cần kiểm tra ngay.':''),row.reviewOverdue?'alert alert-error':''));
            card.append(actionButton('Kiểm tra và thu tiền','btn btn-primary',()=>openPaymentReview(row)));host.append(card);
        }
        const requested=Number(new URLSearchParams(location.search).get('booking'));
        if(requested && document.getElementById('review-detail').hidden){const row=rows.find(row=>row.bookingId===requested);if(row)openPaymentReview(row);}
    }catch(error){if(version===reviewVersion)document.getElementById('review-status').textContent=error.message;}
}
async function openPaymentReview(row){
    if(reviewBusy)return;const version=++reviewDetailVersion,host=document.getElementById('review-detail');host.hidden=false;host.replaceChildren(textNode('p','Đang tải bảng kê…'));host.focus();
    try{
        const response=await apiGet('/admin/booking-payments/'+row.bookingId),invoice=response.invoice || response;if(version!==reviewDetailVersion)return;
        host.replaceChildren(textNode('h2','Kiểm tra BK'+row.bookingId),textNode('p',invoice.customerName+' · '+invoice.roomCode),customerServiceTable(invoice.services),customerTotals(invoice));
        if(invoice.paymentId){host.append(customerLink('Đơn đã có hóa đơn · Xem / in','checkout.html?paymentId='+invoice.paymentId));return;}
        const label=textNode('label','Phương thức thực nhận'),method=document.createElement('select');method.id='review-method';label.htmlFor=method.id;method.append(selectOption('Tiền mặt','Thanh toán tại quầy'),selectOption('Chuyển khoản','Chuyển khoản'));if(row.paymentMethod){method.value=row.paymentMethod;method.disabled=true;}
        const actions=textNode('div','','customer-actions');
        actions.append(actionButton('Xác nhận đã nhận đủ tiền','btn btn-primary',()=>submitPaymentReview(row,invoice,method.value,false)));
        if(row.reviewStatus==='Pending')actions.append(actionButton('Từ chối đối soát','btn btn-danger',()=>submitPaymentReview(row,invoice,method.value,true)));
        host.append(label,method,actions);
    }catch(error){if(version===reviewDetailVersion)host.replaceChildren(textNode('p',error.message));}
}
async function submitPaymentReview(row,invoice,method,reject){
    if(reviewBusy)return;
    if(!await askConfirmation(reject?'Từ chối yêu cầu đối soát BK'+row.bookingId+'?':'Xác nhận đã thực nhận đủ '+formatMoney(invoice.grandTotal)+' cho BK'+row.bookingId+'?'))return;
    if(reviewBusy)return;reviewBusy=true;document.getElementById('review-detail').querySelectorAll('button,select').forEach(el=>el.disabled=true);
    try{
        const result=await apiPost('/admin/booking-payments/'+row.bookingId+(reject?'/reject':'/confirm'),reject?{}:{paymentMethod:method,expectedTotal:invoice.grandTotal});
        document.getElementById('review-detail').replaceChildren(textNode('p',result.message));if(result.paymentId)document.getElementById('review-detail').append(customerLink('Xem hóa đơn','checkout.html?paymentId='+result.paymentId));
    }catch(error){document.getElementById('review-detail').replaceChildren(textNode('p',error.message+' Làm mới danh sách và kiểm tra trạng thái trước khi thử lại.'));}
    finally{reviewBusy=false;await loadPaymentReview();}
}
document.addEventListener('DOMContentLoaded',async()=>{const user=await checkLogin();if(user?.role!=='admin')return;document.getElementById('review-refresh').addEventListener('click',()=>{reviewDetailVersion++;document.getElementById('review-detail').hidden=true;loadPaymentReview();});await loadPaymentReview();reviewTimer=setInterval(()=>{if(!document.hidden && !reviewBusy)loadPaymentReview();},30000);});
window.addEventListener('pagehide',()=>{clearInterval(reviewTimer);reviewVersion++;reviewDetailVersion++;});
