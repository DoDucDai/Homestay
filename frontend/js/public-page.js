document.addEventListener('DOMContentLoaded',async()=>{
 const user=await checkLogin();
 renderPublicAccount(document.getElementById('public-account'),user);
});
