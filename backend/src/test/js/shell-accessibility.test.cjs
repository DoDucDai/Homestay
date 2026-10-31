const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
let passed=0;const pass=name=>{passed++;console.log('PASS '+name);};
function fixture() {
 const doc={activeElement:null};
 class Node {
  constructor(id='',classes='',tag='DIV'){this.id=id;this.tagName=tag;this.children=[];this.parentNode=null;this.attrs={};this.inert=false;this.hidden=false;this.disabled=false;this.visible=true;this.tabIndex=tag==='BUTTON'?0:-1;this.isConnected=true;const set=new Set(classes.split(' ').filter(Boolean));this.classList={contains:v=>set.has(v),add:v=>set.add(v),remove:v=>set.delete(v),toggle:(v,on)=>on?set.add(v):set.delete(v)};}
  append(...nodes){for(const n of nodes){n.parentNode=this;this.children.push(n);}}
  contains(n){return n===this||this.children.some(c=>c.contains(n));}
  setAttribute(n,v){this.attrs[n]=String(v);}
  removeAttribute(n){delete this.attrs[n];}
  getAttribute(n){return this.attrs[n]??null;}
  closest(selector){let n=this;while(n){if(selector==='[inert]'&&n.inert)return n;n=n.parentNode;}return null;}
  getClientRects(){let n=this;while(n){if(n.hidden||!n.visible)return [];n=n.parentNode;}return [{}];}
  focus(){if(!this.disabled&&!this.closest('[inert]')&&this.getClientRects().length)doc.activeElement=this;}
  click(){if(!this.disabled)this.onClick?.();}
  querySelectorAll(){return walk(this).filter(n=>n!==this&&(n.tagName==='BUTTON'||n.tagName==='INPUT'||n.attrs.href||n.tabIndex>=0));}
  querySelector(s){return walk(this).find(n=>s==='.modal-box'?n.classList.contains('modal-box'):s==='h3, h2'?['H3','H2'].includes(n.tagName):s==='[autofocus]'?n.attrs.autofocus!==undefined:s==='[data-modal-close]'?n.attrs['data-modal-close']!==undefined:false)||null;}
 }
 const walk=n=>[n,...n.children.flatMap(walk)];
 const body=new Node('body'),main=new Node('main-content'),sidebar=new Node('app-sidebar'),skip=new Node('skip'),print=new Node('print-preview-controls'),back=new Node('back','','BUTTON');print.hidden=true;print.append(back);body.append(main,sidebar,skip,print);doc.body=body;doc.activeElement=main;
 doc.getElementById=id=>walk(body).find(n=>n.id===id)||null;
 doc.querySelector=s=>s==='.skip-link'?skip:s==='#print-preview-controls button'?back:null;
 doc.querySelectorAll=s=>walk(body).filter(n=>n.classList.contains('modal-overlay')&&(s!=='.modal-overlay.show'||n.classList.contains('show')));
 const context=vm.createContext({document:doc,console});vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../../../frontend/js/shell.js'),'utf8'),context);vm.runInContext('shellInitialized=true;shellMedia={matches:false}',context);
 function dialog(id){const overlay=new Node(id,'modal-overlay'),box=new Node(id+'-box','modal-box'),title=new Node(id+'-title','','H3'),close=new Node(id+'-close','','BUTTON'),input=new Node(id+'-input','','INPUT');input.tabIndex=0;close.setAttribute('data-modal-close','');close.onClick=()=>overlay.classList.remove('show');box.append(title,input,close);overlay.append(box);body.append(overlay);return {overlay,box,close,input};}
 const open=(d,opener)=>{context.prepareModalAccessibility(d.overlay,opener);d.overlay.classList.add('show');context.syncModalAccessibility();};
 return {context,doc,Node,main,sidebar,print,back,dialog,open};
}
let f=fixture(),d=f.dialog('parent'),opener=new f.Node('opener','','BUTTON');f.main.append(opener);opener.focus();f.open(d,opener);
assert.equal(f.main.inert,true);assert.equal(f.sidebar.inert,true);assert.equal(f.doc.activeElement,d.input);assert.equal(d.overlay.getAttribute('aria-modal'),'true');
f.context.requestModalDismissal(d.overlay);f.context.syncModalAccessibility();assert.equal(f.main.inert,false);assert.equal(f.doc.activeElement,opener);
pass('opening a modal isolates the app; dismissing it restores the exact opener');

f=fixture();d=f.dialog('locked');f.open(d,f.main);d.close.disabled=true;let clicked=0;d.close.onClick=()=>{clicked++;};
assert.equal(f.context.requestModalDismissal(d.overlay),false);assert.equal(clicked,0);assert.ok(d.overlay.classList.contains('show'));
d.close.disabled=false;d.close.onClick=()=>{clicked++;/* business guard refuses closing */};f.context.requestModalDismissal(d.overlay);f.context.syncModalAccessibility();assert.equal(clicked,1);assert.ok(d.overlay.classList.contains('show'));assert.equal(f.main.inert,true);
pass('Escape dismissal cannot bypass disabled buttons or a page-owned business guard');

f=fixture();d=f.dialog('parent');const child=f.dialog('child');opener=new f.Node('open-child','','BUTTON');d.box.append(opener);f.open(d,f.main);opener.focus();f.open(child,opener);
assert.equal(d.overlay.inert,true);assert.equal(child.overlay.inert,false);child.close.click();f.context.syncModalAccessibility();assert.equal(d.overlay.inert,false);assert.equal(f.doc.activeElement,opener);assert.equal(f.main.inert,true);
pass('nested dialogs isolate their parent and return focus to the parent action');

f=fixture();d=f.dialog('tab');f.open(d,f.main);let prevented=0;d.close.focus();f.context.trapShellFocus(d.overlay,{key:'Tab',shiftKey:false,preventDefault(){prevented++;}});assert.equal(f.doc.activeElement,d.input);
d.input.focus();f.context.trapShellFocus(d.overlay,{key:'Tab',shiftKey:true,preventDefault(){prevented++;}});assert.equal(f.doc.activeElement,d.close);assert.equal(prevented,2);
f.context.trapShellFocus(d.overlay,{key:'Tab',ctrlKey:true,preventDefault(){throw Error('Browser shortcut trapped');}});assert.equal(f.doc.activeElement,d.close);
d.input.disabled=true;d.close.disabled=true;f.context.trapShellFocus(d.overlay,{key:'Tab',preventDefault(){}});assert.equal(f.doc.activeElement,d.box);
pass('Tab/Shift+Tab wrap and all-disabled dialogs retain a focusable container');

f=fixture();d=f.dialog('checkout-modal');opener=new f.Node('open-invoice','','BUTTON');f.main.append(opener);f.open(d,opener);f.print.hidden=false;f.context.syncModalAccessibility();assert.equal(f.main.inert,false);assert.equal(f.doc.activeElement,f.back);assert.equal(d.overlay.getAttribute('aria-modal'),'false');
f.print.hidden=true;f.context.syncModalAccessibility();assert.equal(f.main.inert,true);d.close.click();f.context.syncModalAccessibility();assert.equal(f.doc.activeElement,opener);
pass('print preview leaves modal isolation and returns without losing the invoice opener');

f=fixture();d=f.dialog('pending');f.open(d,f.main);f.doc.body.classList.add('session-pending');const oldFocus=f.doc.activeElement;f.context.syncModalAccessibility();assert.equal(f.doc.activeElement,oldFocus);assert.equal(d.overlay.getAttribute('aria-modal'),'false');assert.equal(f.doc.body.classList.contains('modal-open'),false);
pass('pending-session transitions do not refocus protected content or retain a modal scroll lock');

f=fixture();const toggle=new f.Node('navigation-toggle','','BUTTON'),navClose=new f.Node('navigation-close','','BUTTON'),backdrop=new f.Node('navigation-backdrop','','BUTTON');f.main.append(toggle);f.sidebar.append(navClose);f.doc.body.append(backdrop);vm.runInContext('shellMedia.matches=true',f.context);
f.context.setShellMenu(true);assert.equal(f.main.inert,true);assert.equal(f.sidebar.inert,false);assert.equal(toggle.getAttribute('aria-expanded'),'true');assert.equal(f.doc.activeElement,navClose);assert.equal(backdrop.hidden,false);
f.context.setShellMenu(false);assert.equal(f.main.inert,false);assert.equal(f.sidebar.inert,true);assert.equal(backdrop.hidden,true);assert.equal(f.doc.activeElement,toggle);
pass('mobile navigation isolates the content, exposes its state and returns focus on close');
f.context.setShellMenu(true);vm.runInContext('shellMedia.matches=false',f.context);f.context.setShellMenu(false,false);assert.equal(f.main.inert,false);assert.equal(f.sidebar.inert,false);assert.equal(f.doc.body.classList.contains('menu-open'),false);assert.equal(toggle.getAttribute('aria-expanded'),'false');assert.equal(f.sidebar.getAttribute('aria-modal'),null);
pass('returning to desktop clears the mobile overlay, focus isolation and scroll lock');
console.log('SHELL accessibility: '+passed+' groups passed.');
