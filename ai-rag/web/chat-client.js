/* Shared by the dedicated assistant page and the floating quick chat. */
window.HomestayChat={mount(host,{compact=false,onChange=()=>{},onBusy=()=>{}}={}){
 const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text)n.textContent=text;if(cls)n.className=cls;return n;};
 let busy=false,disposed=false,messages=[];
 const snapshot=()=>({messages:messages.map(m=>({...m})),checkin:fields.checkin.value,checkout:fields.checkout.value});
 const log=el('div','','ai-thread');log.setAttribute('role','log');log.setAttribute('aria-live','polite');log.setAttribute('aria-label','Cuộc trò chuyện với Homestay AI');
 const dates=el('div','','ai-dates');const fields={};
 for(const [key,title]of[['checkin','Ngày nhận'],['checkout','Ngày trả']]){const label=el('label',title);const input=document.createElement('input');input.type='date';input.min=new Date(Date.now()+7*3600000).toISOString().slice(0,10);label.append(input);dates.append(label);fields[key]=input;}
 const form=el('form','','ai-composer');const input=el('textarea');input.required=true;input.maxLength=1800;input.placeholder='Hỏi về phòng và kỳ nghỉ của bạn…';input.setAttribute('aria-label','Tin nhắn cho trợ lý');input.rows=compact?2:1;
 const send=el('button','Gửi ↗','ai-send');send.type='submit';form.append(input,send);
 function resizeInput(){
  if(!input.clientWidth)return;
  input.style.height='auto';input.style.height=input.scrollHeight+'px';
 }
 input.addEventListener('input',()=>{
  resizeInput();
  if(input.selectionStart===input.selectionEnd&&input.selectionEnd===input.value.length)send.scrollIntoView({block:'nearest'});
 });
 let inputWidth=0;
 const inputObserver=new ResizeObserver(()=>{if(input.clientWidth!==inputWidth){inputWidth=input.clientWidth;resizeInput();}});
 inputObserver.observe(form);
 const status=el('p','','ai-chat-status');status.setAttribute('role','status');
 const suggestions=el('div','','ai-prompts');
 for(const question of ['Tìm phòng trống cho tôi','Đặt phòng như thế nào?','Có quy định đặt cọc chưa?']){const button=el('button',question);button.type='button';button.onclick=()=>{input.value=question;resizeInput();input.focus();};suggestions.append(button);}
 function append(role,text){const row=el('article','', 'ai-message ai-message-'+role);row.append(el('small',role==='user'?'Bạn':'Homestay AI'),el('p',text));log.append(row);log.scrollTop=log.scrollHeight;return row;}
 function addSources(row,sources){if(Array.isArray(sources)&&sources.length){const details=el('details');details.append(el('summary','Nguồn tham khảo · '+sources.length),el('p',sources.map(s=>s.title+' · v'+s.version).join('\n')));row.append(details);}}
 const welcome=append('assistant','Tôi là Mochi, trợ lý Homestay. Chọn ngày để tìm phòng trống, hoặc hỏi tôi về cách đặt phòng nhé.');
 if(!compact){welcome.classList.add('ai-welcome');const portrait=el('img');portrait.src='/ai/mascot.svg';portrait.alt='Mochi chào bạn';portrait.width=90;portrait.height=106;welcome.prepend(portrait,el('h2','Một khoảng nghỉ, bắt đầu từ đây.'));}
 const dateControl=el('details','',compact?'ai-quick-dates':'ai-stay-picker');
 const dateSummary=el('summary','Ngày lưu trú · chọn khi tìm phòng');dateControl.append(dateSummary,dates);
 dates.addEventListener('change',()=>{dateSummary.textContent=fields.checkin.value&&fields.checkout.value?'Ngày lưu trú · '+fields.checkin.value+' → '+fields.checkout.value:'Ngày lưu trú · chọn khi tìm phòng';});
 host.append(dateControl,log,suggestions,form,status);
 resizeInput();
 async function ask(){
  const message=input.value.trim();if(!message||busy)return;
  busy=true;onBusy(true);send.disabled=true;suggestions.querySelectorAll('button').forEach(b=>b.disabled=true);status.textContent='Đang tìm thông tin…';
  try{
   const response=await fetch('/api/auth/me',{credentials:'include',cache:'no-store'});
   if(response.status===401){status.replaceChildren(el('span','Đăng nhập để trò chuyện và đặt phòng. '));const login=el('a','Đăng nhập');login.href='login.html?next='+encodeURIComponent('/pages/assistant.html');status.append(login);return;}
   if(!response.ok)throw new Error('Chưa kiểm tra được phiên đăng nhập. Vui lòng thử lại.');
   host.classList.add('is-conversation');dateControl.open=false;append('user',message);input.value='';resizeInput();messages.push({role:'user',text:message});onChange(snapshot());
   const csrfResponse=await fetch('/api/auth/csrf',{credentials:'include',cache:'no-store'});if(!csrfResponse.ok)throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');const csrf=await csrfResponse.json();
   const result=await fetch('/api/assistant/chat',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':csrf.token},body:JSON.stringify({message,checkin:fields.checkin.value,checkout:fields.checkout.value})});
   const data=await result.json();if(!result.ok||data.success===false)throw new Error(data.message||'Chưa nhận được phản hồi.');if(disposed)return;
   const row=append('assistant',data.answer);
   addSources(row,data.sources);messages.push({role:'assistant',text:data.answer,sources:data.sources||[]});onChange(snapshot());
   for(const room of data.rooms||[]){const card=el('div','','ai-room');const amount=Number(room.estimatedRoomTotal),nights=Number(room.nights);const price=Number.isSafeInteger(amount)&&amount>0&&Number.isInteger(nights)&&nights>0?amount.toLocaleString('vi-VN')+' đ / '+nights+' đêm · Chưa gồm dịch vụ':room.nightlyPricing?'Ngày thường '+Number(room.price).toLocaleString('vi-VN')+' đ/đêm · Cuối tuần '+Number(room.nightlyPricing.weekendPrice).toLocaleString('vi-VN')+' đ/đêm':Number(room.price).toLocaleString('vi-VN')+' đ/đêm';card.append(el('strong',room.roomCode+' · '+room.roomName),el('p',price));const link=el('a','Chọn phòng này ↗');link.href='bookings.html?'+new URLSearchParams({room:room.roomCode,date:fields.checkin.value});card.append(link);row.append(card);}
   status.textContent='Thông tin cần được kiểm tra lại trước khi xác nhận đặt phòng.';log.scrollTop=log.scrollHeight;
  }catch(error){status.textContent=error.message||'Chưa kết nối được trợ lý.';}finally{busy=false;onBusy(false);send.disabled=false;suggestions.querySelectorAll('button').forEach(b=>b.disabled=false);}
 }
 form.addEventListener('submit',event=>{event.preventDefault();ask();});
 input.addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();ask();}});
 return {newChat(){if(busy)return false;messages=[];host.classList.remove('is-conversation');log.replaceChildren(welcome);status.textContent='';input.value='';resizeInput();fields.checkin.value='';fields.checkout.value='';dateSummary.textContent='Ngày lưu trú · chọn khi tìm phòng';return true;},restore(saved){if(busy)return false;messages=saved.messages.map(m=>({...m}));log.replaceChildren();host.classList.toggle('is-conversation',messages.length>0);for(const m of messages){const row=append(m.role,m.text);addSources(row,m.sources);}fields.checkin.value=saved.checkin||'';fields.checkout.value=saved.checkout||'';dates.dispatchEvent(new Event('change'));dateControl.open=false;input.value='';resizeInput();status.textContent='Đang xem lịch sử. Chọn lại ngày để kiểm tra phòng và giá hiện tại.';log.scrollTop=log.scrollHeight;return true;},focus(){input.focus();},dispose(){disposed=true;inputObserver.disconnect();}};
}};
