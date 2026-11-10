// Shared presentation for private customer pages. All data comes from /customer APIs.
const customerStayLabels={Confirmed:'Chờ nhận phòng',CheckedIn:'Đang lưu trú',CheckedOut:'Đã hoàn tất',Cancelled:'Đã hủy',Expired:'Hết hạn giữ phòng'};
const customerPaymentLabels={Unpaid:'Chưa thanh toán',PendingReview:'Chờ xác nhận',Paid:'Đã thanh toán'};
function customerStayLabel(row){return row.status==='Confirmed' && row.paymentMethod==='Chuyển khoản' && row.paymentStatus==='Unpaid'?'Chờ thanh toán':customerStayLabels[row.status] || 'Cần kiểm tra';}
function customerLink(label,href,style='btn btn-secondary'){const link=textNode('a',label,style);link.href=href;return link;}
function customerTotals(invoice){
    const dl=textNode('dl','','customer-totals');
    for(const [label,value,total] of [['Tiền phòng',invoice.roomTotal],['Tiền dịch vụ',invoice.serviceTotal],['Tổng thanh toán',invoice.grandTotal,true]]){
        const row=textNode('div','',total?'customer-total':'');row.append(textNode('dt',label),textNode('dd',formatMoney(value)));dl.append(row);
    }return dl;
}
function customerServiceTable(services){
    if(!services?.length)return textNode('p','Không sử dụng dịch vụ đi kèm.');
    const wrap=textNode('div','','customer-service-table'),table=document.createElement('table');
    const caption=textNode('caption','Dịch vụ đã chọn');table.append(caption);
    const head=document.createElement('thead'),tr=document.createElement('tr');
    for(const label of ['Dịch vụ','Số lượng','Đơn giá','Thành tiền']){const th=textNode('th',label);th.scope='col';tr.append(th);}head.append(tr);table.append(head);
    const body=document.createElement('tbody');
    for(const line of services){const row=document.createElement('tr');for(const value of [line.serviceName,line.quantity+(line.unit?' '+line.unit:''),formatMoney(line.unitPrice),formatMoney(line.totalPrice)])row.append(textNode('td',String(value)));body.append(row);}table.append(body);wrap.append(table);return wrap;
}
function customerStatus(row){const host=textNode('div','','customer-status');host.append(textNode('span',customerStayLabel(row),'badge badge-neutral'),textNode('span',customerPaymentLabels[row.paymentStatus] || 'Cần kiểm tra thanh toán','badge badge-info'));return host;}
async function customerSession(){const user=await checkLogin();if(!user)return null;if(user.role!=='customer'){window.location.replace('bookings.html');return null;}return user;}
// Bfcache restores must not display another session's private content.
window.addEventListener('pageshow',event=>{if(event.persisted)window.location.reload();});
