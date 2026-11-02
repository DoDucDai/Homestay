document.addEventListener('DOMContentLoaded',async()=>{
 const chat=HomestayChat.mount(document.getElementById('assistant-chat'));
 document.getElementById('ai-new-chat').onclick=()=>chat.newChat();
 let user;
 try{const response=await fetch('/api/auth/me',{credentials:'include',cache:'no-store'});if(response.ok)user=(await response.json()).user;}catch{}
 const session=document.getElementById('ai-session');
 if(!user){session.textContent='Đăng nhập để trò chuyện và đặt phòng';session.href='login.html?next='+encodeURIComponent('/pages/assistant.html');}
 else{session.textContent=user.fullName||user.username;session.href='account.html';}
 if(user?.role==='admin'){
  document.getElementById('ai-admin').hidden=false;
  document.getElementById('document-form').addEventListener('reset',()=>{document.getElementById('document-id').value='';});
  document.getElementById('document-form').addEventListener('submit',event=>{event.preventDefault();const form=event.currentTarget;submitForm(form,async()=>{await apiPost('/assistant/documents',{id:Number(byId('document-id').value)||null,title:byId('document-title').value,content:byId('document-content').value});form.reset();await loadDocuments();report('Đã cập nhật tài liệu.');});});
  try{await loadDocuments();}catch(error){report(error.message);}
 }
});
