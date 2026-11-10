const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../../main/resources/static');
const mediaSource = fs.readFileSync(path.join(root, 'movie-media.js'), 'utf8');
const movieSource = fs.readFileSync(path.join(root, 'movies.js'), 'utf8');
const pure = vm.createContext({URL});
vm.runInContext(mediaSource, pure);
const media = pure.BbcinemaMovieMedia;

test('uploaded poster paths work but traversal, script and arbitrary local paths do not', () => {
  const local = '/uploads/posters/0123456789abcdef0123456789abcdef.png';
  assert.equal(media.posterUrl(local), local);
  assert.equal(media.posterUrl('/demo/posters/summer-train.svg'), '/demo/posters/summer-train.svg');
  for (const url of ['/uploads/posters/../secret.png', '/uploads/posters/%2e%2e/secret.png', '/uploads/posters/0123456789abcdef0123456789abcdef.svg', '//evil.example/poster.jpg', '/admin.html', 'javascript:alert(1)', 'https://user:pass@example.com/a.jpg']) assert.equal(media.posterUrl(url), null, url);
});

test('YouTube watch, shortened, embed, shorts and live links resolve to one video', () => {
  for (const url of ['https://www.youtube.com/watch?v=M7lc1UVf-VE&feature=share', 'https://youtu.be/M7lc1UVf-VE?si=abc', 'https://www.youtube-nocookie.com/embed/M7lc1UVf-VE', 'https://m.youtube.com/shorts/M7lc1UVf-VE', 'https://youtube.com/live/M7lc1UVf-VE']) {
    const result = media.trailer(url);
    assert.equal(result.kind, 'youtube');
    assert.equal(result.url, 'https://www.youtube.com/watch?v=M7lc1UVf-VE');
    assert.equal(new URL(result.embedUrl).pathname, '/embed/M7lc1UVf-VE');
  }
  for (const url of ['https://youtube.com/playlist?list=abc', 'https://youtube.com/watch?v=bad', 'https://youtu.be/', 'javascript:alert(1)', 'file:///C:/video.mp4']) assert.equal(media.trailer(url), null);
});

test('only direct MP4/WebM URLs become native video; other sites are external links', () => {
  assert.equal(media.trailer('https://cdn.example.com/trailer.MP4?token=abc').kind, 'video');
  assert.equal(media.trailer('https://cdn.example.com/trailer.webm').kind, 'video');
  assert.equal(media.trailer('https://youtube.com.evil.example/embed/M7lc1UVf-VE').kind, 'link');
  assert.equal(media.trailer('https://example.com/watch').kind, 'link');
});

test('poster limits are enforced before a request is sent', () => {
  assert.equal(media.validatePoster({type:'image/png', size:100}).size, 100);
  assert.throws(() => media.validatePoster({type:'image/svg+xml', size:100}));
  assert.throws(() => media.validatePoster({type:'image/jpeg', size:0}));
  assert.throws(() => media.validatePoster({type:'image/jpeg', size:media.MAX_POSTER_BYTES + 1}));
});

class Node {
  get value() { return this._value || ''; }
  set value(value) { this._value = String(value); }
  constructor(tag='div') { this.tagName=tag.toUpperCase(); this.children=[]; this.events={}; this.attrs={}; this.dataset={}; this.style={}; this.hidden=false; this.disabled=false; this.checked=false; this.value=''; this.textContent=''; this.className=''; this.elements={}; this.isConnected=true; this.files=[];
    this.classList={add:()=>{},remove:()=>{},contains:()=>false}; }
  append(...nodes) { for (const node of nodes) { if (node.tagName==='#FRAGMENT') this.append(...node.children); else { this.children.push(node); if (typeof node==='object') node.parent=this; } } }
  replaceChildren(...nodes) { this.children=[]; this.append(...nodes); }
  setAttribute(key,value) { this.attrs[key]=String(value); }
  removeAttribute(key) { delete this.attrs[key]; delete this[key]; }
  addEventListener(name,fn) { this.events[name]=fn; }
  closest() { return this.card ||= new Node(); }
  querySelectorAll(selector) { return descendants(this).slice(1).filter(node => selector.split(',').some(part => part.startsWith('input') ? node.tagName==='INPUT' && (!part.includes(':checked') || node.checked) : part.toUpperCase()===node.tagName)); }
  reset() { for (const node of Object.values(this.elements)) { node.value=node.defaultValue || ''; node.checked=false; } this.querySelectorAll('input').forEach(node=>{node.checked=false;}); }
  focus() {} scrollIntoView() {}
  async fire(event) { this.events[event]?.({currentTarget:this,preventDefault(){},submitter:new Node('button')}); await flush(); }
}
function descendants(node) { return [node,...node.children.filter(child=>typeof child==='object').flatMap(descendants)]; }
const flush = () => new Promise(resolve=>setTimeout(resolve, 20));
const persisted = '/uploads/posters/0123456789abcdef0123456789abcdef.jpg';
const film = {id:7,title:'Test movie',description:'Description',durationMinutes:120,ageRating:'T13',castList:'Cast',director:'Director',posterUrl:persisted,trailerUrl:'https://youtu.be/M7lc1UVf-VE',releaseDate:'2027-01-01',status:'NOW_SHOWING',genres:[{id:2,genreName:'Action'}]};

async function boot(options={}) {
  const html=fs.readFileSync(path.join(root, 'movie-management.html'), 'utf8');
  const ids={}, calls=[], revoked=[], windows={};
  for (const match of html.matchAll(/<([a-z0-9]+)\b([^>]*\bid="([^"]+)"[^>]*)>/g)) { const node=new Node(match[1]); node.hidden=/\bhidden\b/.test(match[2]); ids[match[3]]=node; }
  for (const match of html.matchAll(/<form id="([^"]+)"[^>]*>([\s\S]*?)<\/form>/g)) {
    const form=ids[match[1]];
    for (const tag of match[2].matchAll(/<(input|select|textarea|button)\b([^>]*)>/g)) {
      const name=/\bname="([^"]+)"/.exec(tag[2])?.[1], id=/\bid="([^"]+)"/.exec(tag[2])?.[1];
      const node=ids[id] || new Node(tag[1]); node.value=/\bvalue="([^"]*)"/.exec(tag[2])?.[1] || ''; node.defaultValue=node.value;
      if (name) form.elements[name]=node;
      form.append(node);
    }
  }
  if (ids['movie-form']) ids['movie-form'].append(ids['genre-checkboxes']);
  class BrowserFormData extends FormData { constructor(form) { super(); if (form) for (const [key,node] of Object.entries(form.elements)) if (!node.disabled) this.append(key,String(node.value)); } }
  class BrowserURL extends URL {}
  BrowserURL.createObjectURL=()=> 'blob:local-poster'; BrowserURL.revokeObjectURL=value=>revoked.push(value);
  const document={body:{dataset:{page:'management'},classList:{contains:()=>false}},getElementById:id=>ids[id],createElement:tag=>new Node(tag),createDocumentFragment:()=>new Node('#fragment')};
  const window={location:{origin:'http://localhost:8080'},addEventListener:(name,fn)=>{windows[name]=fn;}};
  const fetch=async (url,opt={})=>{
    calls.push({url,opt}); let data, status=200;
    if (url==='/api/users/me') { status=options.unauthorized?401:200; data={role:'ADMIN',fullName:'Admin'}; }
    else if (url==='/api/auth/csrf') data={headerName:'X-CSRF-TOKEN',token:'fresh-token'};
    else if (url==='/api/genres') data=[{id:2,genreName:'Action'}];
    else if (url.includes('movies?')) data={items:[film],totalElements:1,totalPages:1,page:0};
    else if (opt.method==='POST' || opt.method==='PUT') {
      if (options.failSave) { status=413; data={message:'Poster too large'}; }
      else { const body=opt.body instanceof BrowserFormData ? JSON.parse(await opt.body.get('movie').text()) : JSON.parse(opt.body); data={...film,...body,posterUrl:opt.body instanceof BrowserFormData ? persisted : body.posterUrl}; }
    } else if (url.endsWith('/movies/7')) data={...film};
    else throw new Error(`Unexpected request ${url}`);
    return {status,ok:status<400,json:async()=>data};
  };
  const context=vm.createContext({document,window,fetch,URL:BrowserURL,URLSearchParams,FormData:BrowserFormData,Blob,Intl,console,location:{search:'?id=7'},setTimeout});
  vm.runInContext(mediaSource,context); vm.runInContext(movieSource,context); await flush();
  return {ids,calls,revoked,document,window,media:window.BbcinemaMovieMedia,FormData:BrowserFormData};
}

async function openEdit(app) {
  const edit=descendants(app.ids['movie-list']).find(node=>node.tagName==='BUTTON'); await edit.fire('click');
}

test('choosing an image previews locally and uploads only on save with CSRF and a browser boundary', async () => {
  const app=await boot(); await openEdit(app);
  const file=new File(['png data'], 'my-poster.png', {type:'image/png'});
  app.ids['poster-file'].files=[file]; await app.ids['poster-file'].fire('change');
  assert.equal(app.ids['poster-preview'].src,'blob:local-poster');
  assert.equal(app.calls.filter(call=>call.opt.method==='PUT').length,0);
  await app.ids['movie-form'].fire('submit');
  const save=app.calls.find(call=>call.opt.method==='PUT'); assert(save);
  assert(save.opt.body instanceof app.FormData); assert.equal(save.opt.headers['Content-Type'],undefined);
  assert.equal(save.opt.headers['X-CSRF-TOKEN'],'fresh-token');
  assert.equal(save.opt.body.get('poster').name,'my-poster.png');
  const body=JSON.parse(await save.opt.body.get('movie').text()); assert.deepEqual(body.genreIds,[2]);
  assert.equal(body.trailerUrl,'https://www.youtube.com/watch?v=M7lc1UVf-VE');
  assert.equal(app.ids['poster-url'].value,persisted); assert.equal(app.ids['poster-url'].disabled,false);
  assert(app.revoked.includes('blob:local-poster')); assert.equal(app.ids['poster-cancel'].hidden,true);
});

test('cancel restores existing poster; metadata-only edits keep the path and removing saves null', async () => {
  const app=await boot(); await openEdit(app);
  app.ids['poster-file'].files=[new File(['jpeg'], 'next.jpg', {type:'image/jpeg'})]; await app.ids['poster-file'].fire('change');
  await app.ids['poster-cancel'].fire('click'); assert.equal(app.ids['poster-preview'].src,persisted);
  await app.ids['movie-form'].fire('submit');
  const first=app.calls.find(call=>call.opt.method==='PUT'); assert.equal(JSON.parse(first.opt.body).posterUrl,persisted);
  await app.ids['poster-remove'].fire('click'); await app.ids['movie-form'].fire('submit');
  assert.equal(JSON.parse(app.calls.filter(call=>call.opt.method==='PUT').at(-1).opt.body).posterUrl,null);
});

test('failed upload preserves the chosen file and form for retry', async () => {
  const app=await boot({failSave:true}); await openEdit(app);
  app.ids['poster-file'].files=[new File(['png'], 'retry.png', {type:'image/png'})]; await app.ids['poster-file'].fire('change');
  await app.ids['movie-form'].fire('submit'); assert.equal(app.ids['poster-preview'].src,'blob:local-poster');
  assert.equal(app.ids['poster-cancel'].hidden,false); assert.equal(app.ids['poster-url'].disabled,true);
  assert.equal(app.ids['save-movie'].disabled,false); assert.equal(app.ids.message.textContent,'Poster too large');
  await app.ids['movie-form'].fire('submit'); assert.equal(app.calls.filter(call=>call.opt.method==='PUT').length,2);
});

test('oversized images make no save request and new-movie reset revokes the local preview', async () => {
  const app=await boot();
  app.ids['poster-file'].files=[{name:'huge.png',type:'image/png',size:media.MAX_POSTER_BYTES+1}]; await app.ids['poster-file'].fire('change');
  assert.equal(app.ids['poster-cancel'].hidden,true); assert.equal(app.calls.filter(call=>call.opt.method==='POST').length,0);
  app.ids['poster-file'].files=[new File(['png'], 'new.png', {type:'image/png'})]; await app.ids['poster-file'].fire('change');
  await app.ids['new-movie'].fire('click'); assert(app.revoked.includes('blob:local-poster')); assert.equal(app.ids['poster-preview'].hidden,true);
});

test('Admin preview uses a YouTube player, clears changed links and rejects invalid video URLs', async () => {
  const app=await boot();
  app.ids['trailer-url'].value='https://youtu.be/M7lc1UVf-VE'; await app.ids['preview-trailer'].fire('click');
  const iframe=descendants(app.ids['trailer-preview']).find(node=>node.tagName==='IFRAME'); assert(iframe);
  assert.equal(iframe.referrerPolicy,'strict-origin-when-cross-origin');
  app.ids['trailer-url'].value='https://youtube.com/watch?v=bad'; await app.ids['trailer-url'].fire('input');
  assert.equal(app.ids['trailer-preview'].hidden,true); await app.ids['preview-trailer'].fire('click'); assert.equal(app.ids['trailer-preview'].hidden,true);
});

test('direct video preview uses native controls and retains a fallback link after playback errors', async () => {
  const app=await boot(); const region=new Node();
  app.media.renderTrailer(region,'https://cdn.example.com/trailer.mp4?token=abc','Movie trailer',true);
  const video=descendants(region).find(node=>node.tagName==='VIDEO'); assert(video); assert.equal(video.controls,true);
  await video.fire('error'); assert(!descendants(region).some(node=>node.tagName==='VIDEO'));
  assert(descendants(region).some(node=>node.className==='trailer-external'&&node.href==='https://cdn.example.com/trailer.mp4?token=abc'));
});
