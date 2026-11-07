// NOTE MOCHI: Lịch sử chat cục bộ được tách khóa theo userId, tối đa 30 cuộc và 100 tin/cuộc; không phải cơ sở dữ liệu đặt phòng.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
/* Local conversation archive, separated by the authenticated account. */
window.HomestayHistory={create(userId,storage=window.localStorage){
 const key='homestay-mochi-history-v1:'+userId;
 // NOTE MOCHI: Đọc JSON từ localStorage, kiểm tra cấu trúc cuộc/tin nhắn và chỉ giữ số lượng đã giới hạn.
 function read(){
  const raw=storage.getItem(key);if(!raw)return [];
  const rows=JSON.parse(raw);if(!Array.isArray(rows))throw new Error('Lịch sử chat không hợp lệ.');
  return rows.filter(row=>row&&typeof row.id==='string'&&typeof row.title==='string'&&Array.isArray(row.messages)&&row.messages.every(m=>m&&['user','assistant'].includes(m.role)&&typeof m.text==='string')).slice(0,30);
 }
 return {read,save(id,snapshot){
  if(!snapshot.messages.length)return null;
  const rows=read();
  const row={id:id||crypto.randomUUID(),title:snapshot.messages.find(m=>m.role==='user')?.text.slice(0,70)||'Cuộc trò chuyện',updatedAt:new Date().toISOString(),checkin:snapshot.checkin||'',checkout:snapshot.checkout||'',messages:snapshot.messages.slice(-100)};
  storage.setItem(key,JSON.stringify([row,...rows.filter(item=>item.id!==row.id)].slice(0,30)));
  return row.id;
 }};
}};
