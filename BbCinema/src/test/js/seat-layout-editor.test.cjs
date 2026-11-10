const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const context = {};
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/seat-layout-editor.js'), 'utf8'), context);
const m = context.BbcinemaSeatLayoutModel;
const plain = value => JSON.parse(JSON.stringify(value));
const types = [
  {id:1,typeName:'STANDARD',seatSpan:1,defaultSurcharge:0},
  {id:2,typeName:'VIP',seatSpan:1,defaultSurcharge:20000},
  {id:3,typeName:'COUPLE',seatSpan:2,defaultSurcharge:50000}
];
function room(rows=4,cols=12) { return {auditoriumId:1,totalRows:rows,totalCols:cols,revision:'v1',structureEditable:true,settings:{seatWidth:38,seatHeight:36,columnGap:7,rowGap:7,screenPosition:'TOP'},seats:[]}; }
function cells(y, start=0,end=12) { return new Set(Array.from({length:end-start},(_,i)=>m.cellKey(start+i,y))); }

test('different seat types can be painted across rows and preserve actual grid gaps',()=>{
  let layout=room();
  layout=m.paint(layout,cells(0),types[0],1);
  layout=m.paint(layout,cells(1,0,5),types[1],1);
  layout=m.paint(layout,cells(1,7,12),types[1],1);
  layout=m.paint(layout,cells(3),types[2],2);
  assert.equal(layout.seats.length,28);
  assert.equal(layout.seats.filter(s=>s.seatTypeId===3).length,6);
  assert(!m.occupancy(layout).has('5,1'));
  assert(!m.occupancy(layout).has('6,1'));
  assert.equal(layout.seats.find(s=>s.gridY===1&&s.gridX===7).colNumber,6);
  m.validate(layout);
});

test('selecting a covered column includes the whole double seat',()=>{
  const layout=m.paint(room(),cells(0),types[2],2);
  const selection=m.expandSelection(layout,new Set(['1,0']));
  assert.deepEqual([...selection].sort(),['0,0','1,0']);
  assert.equal(m.selectedSeats(layout,selection).length,1);
});

test('Ctrl/Cmd selection toggles a whole wide seat without immediately adding it back',()=>{
  const layout=m.paint(room(),cells(0),types[2],2);
  let selected=m.expandSelection(layout,new Set(['1,0']));
  selected=m.toggleSelection(layout,selected,'0,0');
  assert.equal(selected.size,0);
  selected=m.toggleSelection(layout,selected,'1,0');
  assert.deepEqual([...selected].sort(),['0,0','1,0']);
});

test('painting multiple empty rows reserves existing custom row labels and creates distinct new labels',()=>{
  let layout=m.paint(room(3,4),cells(2,0,4),types[0],1);
  layout.seats.forEach(s=>s.rowLabel='B');
  const selected=new Set([...cells(0,0,4),...cells(1,0,4)]);
  const result=m.paint(layout,selected,types[1],1);
  const labels=[...new Map(result.seats.map(s=>[s.gridY,s.rowLabel])).entries()].sort((a,b)=>a[0]-b[0]);
  assert.deepEqual(plain(labels),[[0,'A'],[1,'C'],[2,'B']]);
  m.validate(result);
});

test('resizing cannot overwrite an unselected neighbouring seat',()=>{
  const layout=m.paint(room(1,6),cells(0,0,6),types[0],1);
  const before=plain(layout);
  assert.throws(()=>m.resizeSeats(layout,new Set(['0,0']),2),/chồng nhau|chạm ghế/);
  assert.deepEqual(plain(layout),before);
});

test('selected neighbouring seats of one type can be merged into wider seats while billing type stays single',()=>{
  const layout=m.paint(room(1,6),cells(0,0,6),types[1],1);
  layout.seats.forEach((s,i)=>s.id=i+1);
  const result=m.resizeSeats(layout,cells(0,0,6),2);
  assert.equal(result.seats.length,3);
  assert.deepEqual(plain(result.seats.map(s=>s.id)),[1,3,5]);
  assert(result.seats.every(s=>s.widthUnits===2&&s.seatSpan===1&&s.seatTypeId===2));
  assert.equal(m.payload(result).seats[0].widthUnits,2);
});

test('renumbering is continuous across aisles and custom row labels are allowed',()=>{
  const layout=m.paint(room(2,8),new Set(['0,1','1,1','5,1','6,1']),types[0],1);
  layout.seats.forEach(s=>s.rowLabel='VIP1');
  const result=m.renumber(layout,new Set([1]),10);
  assert.deepEqual(plain(result.seats.map(s=>s.colNumber)),[10,11,12,13]);
  assert.deepEqual(plain(result.seats.map(s=>s.gridX)),[0,1,5,6]);
  m.validate(result);
});

test('presets create a real aisle, VIP zone and full double-seat footprints',()=>{
  const result=m.preset(room(8,16),types,'standard');
  const occupied=m.occupancy(result);
  for(let y=0;y<8;y++){assert(!occupied.has(`7,${y}`));assert(!occupied.has(`8,${y}`));}
  assert(result.seats.some(s=>s.seatTypeId===1));
  assert(result.seats.some(s=>s.seatTypeId===2));
  assert(result.seats.some(s=>s.seatTypeId===3&&s.widthUnits===2));
  assert(result.seats.every(s=>s.id===null));
  m.validate(result);
});

test('odd double-seat regions and shrinking through seats fail without changing the source',()=>{
  const layout=room(2,7);
  assert.throws(()=>m.paint(layout,cells(0,0,7),types[2],2),/chia hết/);
  assert.equal(layout.seats.length,0);
  const filled=m.paint(layout,cells(0,0,7),types[0],1);
  assert.throws(()=>m.validate({...filled,totalCols:5}),/ngoài sơ đồ/);
});

test('empty cells remain blank after removing seats and physical status changes retain IDs',()=>{
  const layout=m.paint(room(1,4),cells(0,0,4),types[0],1);
  layout.seats.forEach((s,i)=>s.id=100+i);
  const broken=m.setStatus(layout,new Set(['0,0']),'BROKEN');
  assert.equal(broken.seats[0].id,100);
  assert.equal(broken.seats[0].status,'BROKEN');
  const removed=m.remove(broken,new Set(['1,0','2,0']));
  assert.deepEqual(plain(removed.seats.map(s=>s.gridX)),[0,3]);
  assert.equal(m.payload({...removed,seats:[]}).seats.length,0);
});
