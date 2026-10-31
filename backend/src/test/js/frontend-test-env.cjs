const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../../../frontend/js');
const payload = `O'Neil <img src=x onerror="globalThis.pwned=1"><script>globalThis.pwned=1</script>`;
class Element {
    constructor(tag = 'div') { this.tagName = tag.toUpperCase(); this.children = []; this.style = {}; this.dataset = {}; this.events = {}; this.attributes = {}; this.value = ''; this._text = ''; this.className = ''; this.disabled = false; this.selectedIndex = 0; this.classList = { add() {}, remove() {} }; }
    set textContent(value) { this._text = String(value); this.children = []; }
    get textContent() { return this._text + this.children.map(child => child.textContent).join(''); }
    set innerHTML(value) { throw new Error('Unsafe innerHTML reached: ' + value); }
    get firstElementChild() { return this.children[0]; }
    get options() { return this.children; }
    appendChild(child) { child.parentNode=this;this.children.push(child); return child; }
    remove() { if(this.parentNode) this.parentNode.children=this.parentNode.children.filter(n=>n!==this); }
    focus() { this.focused=true; }
    append(...children) { children.forEach(child => this.appendChild(child)); }
    replaceChildren(...children) { this._text = ''; this.children = []; this.append(...children); }
    addEventListener(type, fn) { this.events[type] = fn; }
    getAttribute(name) { return name === 'data-price' ? this.dataset.price : this.attributes[name] ?? null; }
    setAttribute(name, value) { this.attributes[name] = value; }
}
function env(module, options = {}) {
    const elements = new Map(), events = {}, writes = [], confirmations = [];
    const el = id => { if (!elements.has(id)) elements.set(id, new Element(id.includes('tbody') ? 'tbody' : 'div')); return elements.get(id); };
    const context = vm.createContext({
        Node: Element, document: { body:new Element('body'),createElementNS:(_,tag)=>new Element(tag),createElement: tag => new Element(tag), getElementById: el,
            addEventListener: (name, fn) => { events[name] = fn; }, querySelectorAll: selector => selector.startsWith('#invoice-details') ? [...elements].filter(([id]) => id.startsWith('inv-')).map(([, node]) => node) : [], querySelector: () => null },
        window: { location: { pathname: '/pages/rooms.html', replace() {} }, addEventListener() {} },
        sessionStorage: { removeItem() {} }, setTimeout: () => 0, clearTimeout() {}, setInterval: () => 0, URLSearchParams,
        fetch: async () => ({ ok: true, status: 200, json: async () => ({ success: true, user: { role: 'admin' } }) }),
        confirm: message => { confirmations.push(message); return false; }, console
    });
    vm.runInContext(fs.readFileSync(path.join(root, 'api.js'), 'utf8'), context);
    if(module === 'rooms') vm.runInContext(fs.readFileSync(path.join(root, 'room-stay.js'), 'utf8'), context);
    vm.runInContext(fs.readFileSync(path.join(root, module + '.js'), 'utf8'), context);
    if (!options.realConfirmation) context.askConfirmation = async message => context.confirm(message);
    // Rendering protected admin actions requires a verified identity in the fixture.
    vm.runInContext("currentUser = {role:'admin'}", context);
    context.apiGet = async () => [];
    context.apiPost = async (endpoint, body) => { writes.push({ endpoint, body }); return { success: true }; };
    context.apiPut = context.apiPost;
    context.apiDelete = async endpoint => { writes.push({endpoint}); return {success:true}; };
    context.confirm = () => true;
    context.window.print = () => { context.printCount = (context.printCount || 0) + 1; };
    return { context, el, elements, events, writes, confirmations };
}

module.exports = {env, Element, payload, root};
