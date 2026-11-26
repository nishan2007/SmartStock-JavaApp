const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict'),{test}=require('node:test'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'../../src/scheduler-web/app.js'),'utf8');
function render(mode,start,end,days,holidays=[]) {
 const text=[],rects=[];const ctx={font:'',measureText(s){return {width:String(s).length*(parseInt(this.font.match(/(\d+)px/)?.[1]||'24')*.52)}},fillText(s,x,y){text.push({text:String(s),x,y})},fillRect(x,y,w,h){rects.push({x,y,w,h})},drawImage(){}};
 const canvas={getContext:()=>ctx};const context=vm.createContext({document:{createElement:()=>canvas},console});
 vm.runInContext(source.slice(0,source.indexOf('let exportedImage=null;'))+`function copy(d){return new Date(d.getFullYear(),d.getMonth(),d.getDate(),12)}function add(d,n){const x=copy(d);x.setDate(x.getDate()+n);return x}function iso(d){return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0')}`,context);
 context.snapshot={days,holidays};context.selection={mode,start:new Date(start+'T12:00:00'),end:new Date(end+'T12:00:00'),store:'Main Store'};
 vm.runInContext('scheduleImage(snapshot,selection,null)',context);
 for(const t of text){assert.ok(t.y>=0&&t.y<canvas.height,'Text outside page: '+t.text);assert.ok(t.x>=0&&t.x<canvas.width)}
 return {text:text.map(t=>t.text).join('\n'),canvas,rects};
}
const assignment={userId:42,displayName:'Alicia',shiftStartTime:'08:30',shiftEndTime:'16:30',lunchStartTime:'12:15'};
test('weekly rows use only employees and dates in authorized loaded period',()=>{
 const result=render('week','2026-11-23','2026-11-29',[{date:'2026-11-23',assignments:[assignment]},{date:'2026-11-29',assignments:[{...assignment,shiftStartTime:'09:00'}]},{date:'2026-11-30',assignments:[{...assignment,displayName:'Outside period'}]}]);
 assert.equal((result.text.match(/Alicia/g)||[]).length,1);assert.match(result.text,/8:30 AM/);assert.match(result.text,/9 AM/);assert.match(result.text,/Lunch 12:15 PM/);assert.match(result.text,/OFF/);assert.doesNotMatch(result.text,/Outside period|Closed/);
});
test('semi-monthly includes every day of a 16 day half-month and manual holiday assignments',()=>{
 const result=render('semi','2026-12-16','2026-12-31',[{date:'2026-12-31',assignments:[assignment]}],[{holidayDate:'2026-12-31',name:'Special hours'}]);
 for(let day=16;day<=31;day++)assert.match(result.text,new RegExp(day+' Dec'));
 assert.match(result.text,/Special hours/);assert.match(result.text,/Alicia/);assert.doesNotMatch(result.text,/1 Jan/);
});
test('empty schedules and long employee names stay within image bounds',()=>{
 assert.match(render('week','2026-11-23','2026-11-29',[]).text,/No employees scheduled/);
 const name='Employee '.repeat(20),result=render('semi','2026-11-16','2026-11-30',[{date:'2026-11-16',assignments:[{...assignment,displayName:name,lunchStartTime:null}]}]);
 assert.match(result.text,/Lunch not set/);assert.ok(result.canvas.height>800);
});
