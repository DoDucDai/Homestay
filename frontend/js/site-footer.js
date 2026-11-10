// NOTE FRONTEND: Dựng footer cố định, cập nhật năm hiện tại và nút lên đầu trang; không có thao tác tài khoản/booking.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
/* Footer for the public homestay pages. No account or booking operations here. */
(()=>{
 const footer=document.querySelector('[data-site-footer]');if(!footer)return;
 footer.className='site-footer';
 footer.innerHTML=`
 <div class="site-footer-container">
  <div class="footer-invitation">
   <div><p class="footer-eyebrow">A LITTLE TIME FOR YOURSELF</p><h2>Một khoảng nghỉ nhỏ.<br><em>Một ngày dễ chịu hơn.</em></h2></div>
   <a class="footer-book" href="bookings.html">Tìm khoảng nghỉ của bạn <span aria-hidden="true">↗</span></a>
  </div>
  <div class="footer-grid">
   <div class="footer-brand-column"><a class="footer-brand" href="landing.html" aria-label="Homestay, trang chủ"><img src="../css/images/homestay-logo.svg" width="42" height="44" alt=""><span>Homestay<small>MAKE ROOM FOR YOURSELF</small></span></a><p>Một không gian ấm áp, một nhịp sống chậm hơn. Chọn khoảng thời gian dành riêng cho bạn.</p><span class="footer-brand-signature">stay a little. <em>feel at home.</em></span></div>
   <nav class="footer-links" aria-label="Khám phá Homestay"><h3>KHÁM PHÁ</h3><a href="explore.html">Không gian & phòng</a><a href="bookings.html">Đặt phòng</a><a href="services.html">Dịch vụ lưu trú</a><a href="landing.html#cam-hung">Cảm hứng nghỉ ngơi</a></nav>
   <nav class="footer-links" aria-label="Trợ giúp tại Homestay"><h3>CHÚNG TÔI Ở ĐÂY</h3><a href="support.html">Trung tâm trợ giúp</a><a href="support.html#contact-request">Gửi yêu cầu hỗ trợ</a><a href="assistant.html">Trò chuyện với Mochi</a><a href="account.html">Tài khoản của bạn</a></nav>
   <aside class="footer-care"><span class="footer-care-symbol" aria-hidden="true">♡</span><h3>An tâm cho mỗi lần ở.</h3><p>Thông tin rõ ràng để bạn thoải mái chọn phòng và tận hưởng kỳ nghỉ.</p><a href="policies.html">Bảo mật & điều khoản <span aria-hidden="true">↗</span></a></aside>
  </div>
  <div class="footer-bottom"><small>© <span id="landing-year"></span> Homestay. Một nơi để thấy bình yên.</small><nav aria-label="Chính sách cuối trang"><a href="policies.html#bao-mat">Chính sách bảo mật</a><span aria-hidden="true">·</span><a href="policies.html#dieu-khoan">Điều khoản dịch vụ</a></nav><button class="footer-top" type="button">Về đầu trang <span aria-hidden="true">↑</span></button></div>
 </div>`;
 footer.querySelector('#landing-year').textContent=new Date().getFullYear();
 footer.querySelector('.footer-top').onclick=()=>window.scrollTo({top:0,behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'});
 if(typeof routeCustomerLinks==='function' && currentUser)routeCustomerLinks(currentUser);
})();
