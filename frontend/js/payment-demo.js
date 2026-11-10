// Thanh toán mô phỏng cho bài tập. QR chỉ chứa dữ liệu mẫu, không có lệnh chuyển tiền ngân hàng.
// QR encoder: qrcode-generator 2.0.4 (MIT), xem vendor/qrcode.LICENSE.
const DEMO_TRANSFER_ACCOUNT = Object.freeze({bank:'DEMO BANK',account:'0000123456789',name:'NGUYEN AN DEMO'});

function demoPaymentPayload(invoice) {
    if(!invoice || !Number.isSafeInteger(invoice.bookingId) || invoice.bookingId<=0
        || !Number.isSafeInteger(invoice.grandTotal) || invoice.grandTotal<0 || invoice.grandTotal>999999999999) return null;
    return {demo:true,type:'HOMESTAY_DEMO_TRANSFER',bank:DEMO_TRANSFER_ACCOUNT.bank,
        account:DEMO_TRANSFER_ACCOUNT.account,name:DEMO_TRANSFER_ACCOUNT.name,
        amount:invoice.grandTotal,currency:'VND',reference:'HOMESTAY BK'+invoice.bookingId};
}

function renderDemoPayment(host, invoice, method) {
    if(!host) return;
    const payment=demoPaymentPayload(invoice);
    if(!payment || !['Tiền mặt','Chuyển khoản'].includes(method)) {
        host.replaceChildren();host.hidden=true;delete host.dataset.paymentKey;return;
    }
    const key=method+':'+payment.reference+':'+payment.amount;
    host.hidden=false;
    if(host.dataset.paymentKey===key) return;
    host.dataset.paymentKey=key;host.replaceChildren();
    const card=textNode('section','','demo-payment-card');
    card.append(textNode('span','BÀI TẬP · THANH TOÁN MÔ PHỎNG','demo-payment-badge'),
        textNode('h4',method==='Chuyển khoản'?'Chuyển khoản bằng QR demo':'Thanh toán tiền mặt','demo-payment-title'));
    const content=textNode('div','','demo-payment-content');
    if(method==='Chuyển khoản') {
        const visual=textNode('div','','demo-payment-qr');
        try {
            const code=qrcode(0,'M');code.addData(JSON.stringify(payment),'Byte');code.make();
            const image=document.createElement('img');
            image.src=code.createDataURL(5,20);
            image.alt='QR mô phỏng chuyển khoản '+payment.reference+', '+formatMoney(payment.amount);
            image.width=224;image.height=224;
            visual.append(image,textNode('span','QR DEMO · KHÔNG CHUYỂN TIỀN THẬT'));
        } catch(_) {
            visual.append(textNode('p','Chưa tạo được QR. Hãy tải lại trang; thông tin mẫu vẫn hiển thị bên cạnh.','form-error'));
        }
        content.appendChild(visual);
    }
    const details=textNode('div','','demo-payment-info'),list=document.createElement('dl');
    const rows=method==='Chuyển khoản'
        ? [['Ngân hàng mô phỏng',payment.bank],['Số tài khoản mẫu',payment.account],['Chủ tài khoản mẫu',payment.name]] : [];
    rows.push(['Số tiền',formatMoney(payment.amount)],['Nội dung','HOMESTAY BK'+invoice.bookingId]);
    rows.forEach(([label,value])=>{
        const row=document.createElement('div');row.append(textNode('dt',label),textNode('dd',value));list.appendChild(row);
    });
    details.appendChild(list);
    details.appendChild(textNode('p',method==='Chuyển khoản'
        ? 'QR chứa thông tin mẫu để xem bằng trình quét QR thông thường. Không dùng trong ứng dụng ngân hàng. Chọn xác nhận bên dưới để hoàn tất mô phỏng.'
        : 'Mô phỏng khách trả đủ tiền mặt tại quầy. Chọn xác nhận bên dưới để lưu hóa đơn và trả phòng.','demo-payment-help'));
    content.appendChild(details);card.appendChild(content);host.appendChild(card);
}
