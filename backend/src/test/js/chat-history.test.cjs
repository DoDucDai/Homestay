// NOTE KIỂM THỬ: Kiểm tra lưu/đọc lịch sử chat theo tài khoản, tiếp tục hội thoại và lỗi bộ nhớ trình duyệt.
// Code chạy bằng Node trong môi trường giả để kiểm tra frontend; các assert so kết quả thực tế với kết quả mong đợi.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {randomUUID}=require('node:crypto');
const data=new Map();
const storage={getItem:key=>data.get(key)||null,setItem:(key,value)=>data.set(key,value)};
const context=vm.createContext({window:{localStorage:storage},crypto:{randomUUID}});
vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../../../ai-rag/web/chat-history.js'),'utf8'),context);
const create=context.window.HomestayHistory.create;
const a=create(1),b=create(2);
const snapshot={messages:[{role:'user',text:'Đặt phòng như thế nào?'},{role:'assistant',text:'Chọn ngày và phòng.'}],checkin:'2026-12-01',checkout:'2026-12-02'};
const id=a.save(null,snapshot);
assert.equal(create(1).read()[0].messages[1].text,snapshot.messages[1].text,'History survives opening a new store');
assert.equal(b.read().length,0,'Another account cannot see the archive');
a.save(id,{...snapshot,messages:[...snapshot.messages,{role:'user',text:'Còn phòng nào?'}]});
assert.equal(a.read().length,1,'Continuing an existing conversation does not duplicate it');
assert.equal(a.read()[0].messages.length,3);
assert.equal(a.save(null,{messages:[]}),null,'Empty conversations do not fill history');
assert.equal(a.read().length,1);
const broken=create(3,{getItem:()=>'{invalid',setItem(){}});
assert.throws(()=>broken.read(),error=>error.name==='SyntaxError','Corrupt storage is surfaced to the page');
const full=create(4,{getItem:()=>null,setItem(){throw new Error('QuotaExceeded');}});
assert.throws(()=>full.save(null,snapshot),/QuotaExceeded/,'The page can report a storage failure without treating it as an AI error');
console.log('Chat history persistence, ownership, continuation, empty state and storage failures: OK');
