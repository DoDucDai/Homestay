const assert = require('node:assert/strict');
const {env, payload} = require('./frontend-test-env.cjs');
let passed = 0;
const pass = name => { passed++; console.log('PASS ' + name); };
function noHtml(element) {
    assert.ok(!['IMG', 'SCRIPT'].includes(element.tagName));
    assert.equal(element.getAttribute('onclick'), null);
    element.children.forEach(noHtml);
}
(async () => {
    for (const [module, id, record, key, method] of [
        ['rooms', 'rooms-tbody', { roomId: 1, roomCode: payload, roomName: payload, roomType: 'Single', price: 1, status: 'Trống' }, 'roomName', 'openRoomStay'],
        ['customers', 'customers-tbody', { customerId: 1, fullName: payload, cccd: 'AB123456', documentType: 'PASSPORT', phone: '0901234567', email: payload, validationWarnings: [payload] }, 'fullName', 'openEditModal'],
        ['services', 'services-tbody', { serviceId: 1, serviceName: payload, unitPrice: 1, status: 'Hoạt động' }, 'serviceName', 'openEditModal'],
        ['bookings', 'bookings-tbody', { bookingId: 1, customerName: payload, roomCode: payload, checkinDate: '2026-10-26', checkoutDate: '2026-10-28', note: payload, status: 'Confirmed' }],
        ['checkin', 'checkin-tbody', { bookingId: 1, customerName: payload, roomCode: payload, checkinDate: '2026-10-26', note: payload }, 'customerName', 'doCheckin'],
        ['checkout', 'checkout-tbody', { bookingId: 1, customerName: payload, roomCode: payload, checkinDate: '2026-10-26', checkoutDate: '2026-10-28' }, 'bookingId', 'openCheckoutModal']
    ]) {
        const current = env(module); let captured;
        if (method) current.context[method] = (...args) => { captured = args; };
        current.context.renderTable([record]);
        const body = current.el(id);
        assert.ok(body.textContent.includes(payload)); noHtml(body); assert.equal(current.context.pwned, undefined);
        if (method) {
            const actionCell = body.children[0].children.at(-1);
            const button = module === 'rooms' ? body.children[0] : actionCell.children[0];
            button.events.click();
            if (method === 'openEditModal' || method === 'openRoomStay') assert.equal(captured[0][key], record[key]);
            else if (method === 'doCheckin') assert.equal(captured[2], payload);
            else assert.equal(captured[0], 1);
        }
    }
    pass('six tables render HTML/quotes as text and action callbacks preserve original data without inline code');

    let current = env('bookings');
    current.context.apiGet = async endpoint => endpoint === '/customers'
        ? [{ customerId: 1, fullName: payload, cccd: 'AB123456' }]
        : [{ roomId: 1, roomCode: payload, roomName: payload, price: 300000, status: 'Trống' }];
    await current.context.openAddModal();
    for (const id of ['booking-room', 'booking-customer']) { assert.ok(current.el(id).textContent.includes(payload)); noHtml(current.el(id)); }
    pass('booking dropdown labels use text and cannot turn customer/room names into markup');

    current = env('customers');current.context.openAddModal();
    current.el('customer-name').value = 'Guest'; current.el('customer-cccd').value = '12345678901';
    current.el('customer-phone').value = '0901234567';
    await current.context.saveCustomer(); assert.equal(current.writes.length, 0);
    current.el('customer-document-type').value = 'PASSPORT'; current.el('customer-cccd').value = 'AB123456';
    await current.context.saveCustomer(); assert.equal(current.writes.length, 1);assert.equal(current.writes[0].body.documentType, 'PASSPORT');
    pass('frontend distinguishes CCCD/passport and blocks invalid document before sending');

    current = env('customers');
    const legacy = { customerId: 3, fullName: 'Legacy', cccd: 'OLD 01', documentType: 'LEGACY', phone: ' 123', email: 'old bad', address: 'Old', validationWarnings: ['Review document', 'Review phone'] };
    current.context.openEditModal(legacy); current.el('customer-address').value = 'Changed';
    await current.context.saveCustomer(); assert.equal(current.writes.length, 1);
    assert.equal(current.writes[0].body.phone, ' 123');assert.equal(current.writes[0].body.cccd, 'OLD 01');
    assert.ok(current.el('customer-validation-warning').textContent.includes('Review phone'));
    current.el('customer-phone').value = '987';await current.context.saveCustomer();assert.equal(current.writes.length, 1);
    pass('legacy warnings are visible, unchanged invalid values retained and newly invalid edits blocked');

    for (const [module, load, table] of [['rooms', 'loadRooms', 'rooms-tbody'], ['customers', 'loadCustomers', 'customers-tbody'], ['services', 'loadServices', 'services-tbody'],
        ['bookings', 'loadBookings', 'bookings-tbody'], ['checkin', 'loadPendingBookings', 'checkin-tbody'], ['checkout', 'loadCheckedInBookings', 'checkout-tbody']]) {
        current = env(module);current.el(table).textContent = 'Old data';
        current.context.apiGet = async () => { const error = new Error('Server unavailable');error.status = 503;throw error; };
        await current.context[load]();assert.equal(current.el(table).textContent, 'Server unavailable');
        assert.equal(current.el('alert-message').textContent, 'Server unavailable');
    }
    pass('list failures clear stale data and show the actual error instead of empty success');

    current = env('statistics');
    current.context.apiGet = async () => { const error = new Error('No permission');error.status = 403;throw error; };
    await current.context.loadOverview();
    assert.equal(current.el('stat-total-rooms').textContent, '--');assert.equal(current.el('room-status-tbody').textContent, 'No permission');
    current.el('stat-month').value = '10'; current.el('stat-year').value = '2026';
    await current.context.loadRevenue();assert.equal(current.el('revenue-tbody').textContent, 'No permission');
    current.context.apiGet = async () => [{ paymentId: 1, customerName: payload, roomCode: payload, paidAt: '2026-10-26', roomTotal: 1, serviceTotal: 2, grandTotal: 3 }];
    await current.context.loadRevenue();assert.ok(current.el('revenue-tbody').textContent.includes(payload));noHtml(current.el('revenue-tbody'));
    pass('statistics errors are visible with unknown KPIs and invoice names render safely');

    current = env('rooms');
    for (const [id, value] of Object.entries({ 'room-code': 'P1', 'room-name': 'Room', 'room-type': 'Single', 'room-price': '-1', 'room-status': 'Trống' })) current.el(id).value = value;
    await current.context.saveRoom();assert.equal(current.writes.length, 0);
    current.el('room-price').value = '300000';current.el('room-code').value = 'x'.repeat(21);
    await current.context.saveRoom();assert.equal(current.writes.length, 0);
    assert.throws(() => current.context.validBookingDates('2026-02-30', '2026-03-02'));
    assert.throws(() => current.context.validEnum('Alien', ['Single'], 'Type'));
    pass('frontend length/money/date/enum validation rejects invalid edits');

    current = env('checkout');
    const invoiceIds = ['inv-id','inv-customer','inv-room','inv-checkin','inv-checkout','inv-nights','inv-price-per-night','inv-room-total','inv-service-total','inv-grand-total'];
    invoiceIds.forEach(id => { current.el(id).textContent = 'Old'; });
    current.context.apiGet = async () => { const error = new Error('Invoice missing');error.status = 404;throw error; };
    await current.context.openCheckoutModal(1);
    assert.equal(current.el('checkout-confirm-btn').disabled, true);assert.equal(current.el('inv-grand-total').textContent, '--');
    assert.equal(current.el('checkout-status').textContent, 'Invoice missing');
    pass('checkout 404 clears old invoice and keeps payment disabled');
    for (const status of [400, 401, 403, 404, 409, 500, 503]) {
        await assert.rejects(current.context.responseJson({ ok: false, status, json: async () => ({ success: false, code: 'TEST_ERROR', message: 'Readable error' }) }),
            error => error.status === status && error.code === 'TEST_ERROR' && error.message === 'Readable error');
    }
    await assert.rejects(current.context.responseJson({ ok: true, status: 200, json: async () => { throw new SyntaxError('invalid'); } }), error => error.status === 502);
    await assert.rejects(current.context.responseJson({ ok: true, status: 200, json: async () => ({ success: false, message: 'Legacy error' }) }), /Legacy error/);
    pass('HTTP 400/401/403/404/409/500/503, malformed JSON and legacy false responses preserve error semantics');
    console.log('FIX-03 frontend: ' + passed + ' groups passed (instrumented DOM/fetch).');
})().catch(error => { console.error(error);process.exitCode = 1; });
