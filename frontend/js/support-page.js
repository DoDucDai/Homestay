// NOTE FRONTEND: FAQ có tìm kiếm không dấu/lọc nhóm, kết nối khu gửi và lịch sử yêu cầu hỗ trợ sau đăng nhập.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
const helpTopics=[
 {id:'cancel',group:'booking',icon:'↶',title:'Hủy & thay đổi đặt phòng',items:[['Tôi muốn thay đổi hoặc hủy lượt đặt thì làm thế nào?','Gửi yêu cầu hỗ trợ kèm mã đặt phòng và nội dung muốn thay đổi để admin kiểm tra. Hiện chưa có chính sách hủy hoặc hoàn tiền được công bố; không có mức phí hay cam kết hoàn tiền mặc định.']]},
 {id:'payment',group:'booking',icon:'▤',title:'Thanh toán & đặt cọc',items:[['Tôi có phải đặt cọc trước không?','Hiện Homestay chưa công bố quy định đặt cọc. Nếu cần làm rõ thông tin cho lượt đặt cụ thể, hãy gửi yêu cầu cho admin trước khi thanh toán.'],['Giá phòng đã bao gồm các dịch vụ chưa?','Tiền phòng tạm tính chưa bao gồm dịch vụ phát sinh. Kiểm tra thông tin lượt đặt và trao đổi với admin nếu có thắc mắc về khoản thanh toán.']]},
 {id:'details',group:'booking',icon:'▣',title:'Chi tiết đặt phòng',items:[['Tôi xem thông tin đặt phòng ở đâu?','Sau khi đăng nhập, mở Đặt phòng trên thanh điều hướng để xem lịch và thông tin lượt đặt trong hệ thống. Nếu không tìm thấy, gửi mã đặt phòng cho admin kiểm tra.'],['Làm sao biết đặt phòng đã thành công?','Chỉ coi lượt đặt là thành công khi hệ thống trả thông báo xác nhận. Nếu bị mất mạng trong lúc gửi, hãy kiểm tra lại lịch hoặc liên hệ admin trước khi đặt lại.']]},
 {id:'checkin',group:'stay',icon:'↗',title:'Nhận & trả phòng',items:[['Tôi có thể nhận phòng sớm hoặc trả phòng muộn không?','Khả năng điều chỉnh phụ thuộc tình trạng phòng và lượt đặt. Gửi yêu cầu kèm mã đặt phòng cùng thời gian mong muốn để admin kiểm tra và xác nhận.']]},
 {id:'rooms',group:'stay',icon:'⌂',title:'Phòng & tiện nghi',items:[['Tôi tìm phòng và xem tiện nghi ở đâu?','Mở Khám phá phòng để xem danh mục, mô tả và giá. Để kiểm tra phòng trống trong khoảng lưu trú, hãy dùng lịch Đặt phòng hoặc hỏi Mochi. Các yêu cầu đặc biệt cần được admin xác nhận.']]},
 {id:'security',group:'account',icon:'♧',title:'Tài khoản & bảo mật',items:[['Tôi cần làm gì khi gặp vấn đề đăng nhập?','Kiểm tra tên tài khoản và thử đăng nhập lại. Nếu vẫn không được, liên hệ đơn vị vận hành qua kênh bạn đã sử dụng khi đặt phòng. Không gửi mật khẩu hoặc mã xác thực trong tin nhắn.'],['Ai có thể xem yêu cầu hỗ trợ của tôi?','Bạn xem được các yêu cầu do tài khoản của mình gửi. Admin có quyền xem và phản hồi để xử lý hỗ trợ. Không ghi thông tin thẻ hoặc giấy tờ cá nhân trong yêu cầu.']]}
];
// NOTE FRONTEND: Bỏ dấu, viết thường và đổi đ thành d để tìm FAQ nhất quán với từ khóa không dấu.
function normalizeHelp(value){return value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d');}
document.addEventListener('DOMContentLoaded',async()=>{
 let filter='all',topic=null;const topics=document.getElementById('help-topics'),questions=document.getElementById('help-questions'),search=document.getElementById('help-search');
 const make=(tag,text,cls)=>{const n=document.createElement(tag);n.textContent=text;if(cls)n.className=cls;return n;};
 // NOTE FRONTEND: Dựng lại chủ đề/câu hỏi khớp bộ lọc và từ khóa, mở nhóm đang chọn và thông báo khi không có kết quả.
 function render(){topics.replaceChildren();questions.replaceChildren();const term=normalizeHelp(search.value.trim());let count=0;
  for(const entry of helpTopics){if(filter!=='all'&&entry.group!==filter)continue;const matches=entry.items.filter(([q,a])=>!term||normalizeHelp(entry.title+' '+q+' '+a).includes(term));if(!matches.length)continue;
   const button=make('button','','help-topic');button.type='button';button.setAttribute('aria-pressed',String(topic===entry.id));button.append(make('span',entry.icon,'help-topic-icon'),make('span',entry.title),make('span','›','help-topic-arrow'));button.onclick=()=>{topic=topic===entry.id?null:entry.id;render();questions.scrollIntoView({behavior:'smooth',block:'nearest'});};topics.append(button);
   if(topic&&topic!==entry.id)continue;
   for(const [q,a]of matches){const details=make('details','','help-question');details.append(make('summary',q),make('p',a));if(term||topic)details.open=true;questions.append(details);count++;}
  }
  document.getElementById('help-empty').hidden=count>0;
 }
 for(const tab of document.querySelectorAll('[data-help-filter]'))tab.onclick=()=>{filter=tab.dataset.helpFilter;topic=null;document.querySelectorAll('[data-help-filter]').forEach(t=>t.setAttribute('aria-pressed',String(t===tab)));render();};
 search.oninput=()=>{topic=null;render();};render();
 const user=await checkLogin();if(!user)return;
 if(['#contact-request','#request-history'].includes(location.hash)){const target=document.getElementById(location.hash.slice(1));target.open=true;requestAnimationFrame(()=>target.scrollIntoView({block:'start'}));}
 for(const [linkId,target,label]of[['help-contact-link','contact-request','Gửi yêu cầu hỗ trợ'],['help-requests-link','request-history','Xem yêu cầu & phản hồi']]){const link=document.getElementById(linkId);link.textContent=label;link.href='#'+target;link.onclick=()=>{document.getElementById(target).open=true;};}
});
