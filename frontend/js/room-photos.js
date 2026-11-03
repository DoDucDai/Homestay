/* Preserve the supplied AI poster design; room scenes are optional extra views. */
const RoomPhotos = (() => {
    const landscape = [[8,590,220,155],[241,590,219,155],[466,590,209,155],[685,590,211,155],[910,590,230,155]];
    const cream = [[7,660,172,374],[187,660,169,374],[365,660,167,374],[542,660,164,374],[720,660,166,374]];
    const blue = [[14,817,164,261],[193,817,158,261],[370,817,158,261],[547,817,160,261],[722,817,158,261]];
    const posters = {
        '101': [1152,925,[8,210,895,310],landscape],
        '102': [1152,925,[8,210,895,310],landscape],
        '103': [1152,925,[8,210,895,310],landscape],
        '201': [1152,925,[8,210,895,310],landscape],
        '202': [896,1200,[8,165,680,423],cream],
        '301': [896,1200,[8,145,680,443],cream],
        '401': [896,1200,[8,164,680,424],[[6,660,211,374],[231,660,212,374],[454,660,210,374],[680,660,210,374]]],
        '402': [896,1200,[8,185,680,460],[[8,733,279,303],[310,733,130,303],[451,733,135,303],[609,733,132,303],[750,733,138,303]]],
        '403': [1200,896,[12,215,986,280],[[14,575,221,220],[255,575,214,220],[491,575,218,220],[730,575,219,220],[969,575,220,220]]],
        '404': [896,1200,[12,175,695,557],blue],
        '405': [896,1200,[12,175,695,557],blue],
        '406': [768,1376,[12,315,740,620],[[14,1020,135,230],[163,1020,136,230],[313,1020,137,230],[462,1020,141,230],[620,1020,133,230]]],
        '407': [896,1200,[14,345,866,430],[[16,859,157,205],[194,859,151,205],[371,859,154,205],[546,859,155,205],[721,859,156,205]]]
    };
    // New posters share the same reviewed 896 x 1200 layout.
    for(const number of [104, 105, 106, 107, 108, 109, 110, 203, 204, 205, 206, 207, 208, 209, 210, 303, 304, 305, 306, 307, 308, 309, 310, 414, 415, 416, 417, 418, 419, 420, 421, 422, 423, 424, 425, 426, 427, 501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 601, 602, 603, 604, 605, 606, 607, 608, 609, 610, 701, 702, 703, 704, 705, 706, 707, 708, 709, 710, 801, 802, 803, 804, 805, 806, 807])posters[String(number)]=[896,1200,[10,173,704,562],blue];
    function lookup(room) {
        const match = /^(?:(?:DEMO-)?P|ROOM\s*)(\d{3})$/.exec(String(room?.roomCode || '').trim().toUpperCase());
        const number = match?.[1], poster = posters[number];
        return poster ? {number, width:poster[0], height:poster[1], scenes:[[0,0,poster[0],poster[1]],poster[2],...poster[3]]} : null;
    }
    function view(photo, scene, label) {
        const ns = 'http://www.w3.org/2000/svg';
        const svg = document.createElementNS(ns,'svg');
        svg.setAttribute('viewBox',scene.join(' '));
        svg.setAttribute('preserveAspectRatio','xMidYMid slice');
        svg.setAttribute('role','img');svg.setAttribute('aria-label',label);
        svg.classList.add('room-photo-visual');
        const image = document.createElementNS(ns,'image');
        image.setAttribute('href','../css/images/rooms/room-'+photo.number+'.webp');
        image.setAttribute('width',String(photo.width));image.setAttribute('height',String(photo.height));
        svg.append(image);return svg;
    }
    function create(room, {gallery=false}={}) {
        const photo = lookup(room);if(!photo)return null;
        const figure = document.createElement(gallery?'figure':'span');figure.className='room-photo';
        const label='Ảnh minh họa AI · '+room.roomCode;
        const main=gallery?view(photo,photo.scenes[0],label):document.createElement('img');
        if(!gallery){main.src='../css/images/rooms/room-'+photo.number+'.webp';main.alt=label;main.className='room-photo-visual';main.loading='lazy';main.decoding='async';main.style.objectFit='contain';}
        main.setAttribute('preserveAspectRatio','xMidYMid meet');
        main.style.aspectRatio=gallery?photo.width+' / '+photo.height:'4 / 5';
        if(gallery) {
            const full=document.createElement('a');full.className='room-photo-open';
            full.href='../css/images/rooms/room-'+photo.number+'.webp';full.target='_blank';full.rel='noopener';
            full.setAttribute('aria-label','Mở ảnh thiết kế đầy đủ của phòng '+room.roomCode+' ở kích thước lớn');
            full.append(main);figure.append(full);
        } else figure.append(main);
        const credit=document.createElement(gallery?'figcaption':'span');credit.className='room-photo-credit';
        credit.textContent=gallery?'Ảnh minh họa AI · Bấm ảnh để xem lớn':'Ảnh minh họa AI';
        if(gallery) {
            figure.classList.add('room-photo-gallery');
            const choices=document.createElement('div');choices.className='room-photo-choices';
            choices.setAttribute('role','group');choices.setAttribute('aria-label','Các góc nhìn phòng '+room.roomCode);
            photo.scenes.forEach((scene,index)=>{
                const button=document.createElement('button');button.type='button';
                button.className='room-photo-choice';button.setAttribute('aria-label',index===0?'Xem thiết kế đầy đủ của phòng '+room.roomCode:'Xem góc nhìn '+index+' của phòng '+room.roomCode);
                button.setAttribute('aria-pressed',String(index===0));
                const thumb=view(photo,scene,'');thumb.setAttribute('aria-hidden','true');thumb.removeAttribute('role');
                if(index===0)thumb.setAttribute('preserveAspectRatio','xMidYMid meet');
                button.append(thumb);button.addEventListener('click',()=>{
                    main.setAttribute('viewBox',scene.join(' '));
                    main.setAttribute('aria-label',label+(index===0?' · thiết kế đầy đủ':' · góc nhìn '+index));
                    main.style.aspectRatio=index===0?photo.width+' / '+photo.height:'4 / 3';
                    for(const choice of choices.children)choice.setAttribute('aria-pressed',String(choice===button));
                    credit.textContent=index===0?'Ảnh minh họa AI · Bấm ảnh để xem lớn':'Ảnh minh họa AI · Góc nhìn '+index+' / '+(photo.scenes.length-1)+' · Bấm ảnh để mở thiết kế đầy đủ';
                });choices.append(button);
            });figure.append(choices);
        }
        figure.append(credit);return figure;
    }
    return {lookup,create};
})();
