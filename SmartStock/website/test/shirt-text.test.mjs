import test from 'node:test';
import assert from 'node:assert/strict';
import {presetArc,arcLayout,presetSize} from '../src/shirtText.mjs';
test('explicit arch preset names start curved, ordinary names stay straight',()=>{
 for(const name of ['ARCH','Arch vinyl','Arched text'])assert.equal(presetArc({name}),120);
 for(const name of ['Chest Logo','March team','Per Line'])assert.equal(presetArc({name}),0);
});
test('arch text raises the middle and rotates letters along the curve',()=>{
 const layout=arcLayout([10,10,10,10,10],20,120);
 assert.ok(layout.glyphs[0].y>layout.glyphs[2].y);
 assert.ok(layout.glyphs[0].rotation<0&&layout.glyphs[4].rotation>0);
 assert.equal(layout.glyphs[2].rotation,0);
 assert.ok(layout.width>0&&layout.height>20);
 assert.ok(Math.abs(layout.glyphs[0].x+layout.glyphs[4].x)<1e-8);
});
test('opposite curves mirror and empty or straight text has no arc layout',()=>{
 const up=arcLayout([10,15,10],20,90),down=arcLayout([10,15,10],20,-90);
 up.glyphs.forEach((g,i)=>assert.equal(g.y===0?0:-g.y,down.glyphs[i].y===0?0:down.glyphs[i].y));
 assert.equal(arcLayout([],20,120),null);assert.equal(arcLayout([10],20,0),null);
});

test('preset guide dimensions use height × width and logos have a four-inch maximum',()=>{
 assert.deepEqual(presetSize({name:'Per line 2inx10in'},'TEXT'),{widthIn:10,heightIn:2});
 assert.deepEqual(presetSize({name:'Logo'},'IMAGE'),{widthIn:4,heightIn:4});
 assert.deepEqual(presetSize({name:'Line',widthIn:8,heightIn:3},'TEXT'),{widthIn:8,heightIn:3});
});

test('saved shared preset dimensions override names and default logo size',()=>{
 assert.deepEqual(presetSize({name:'2inx10in',widthIn:8,heightIn:3},'TEXT'),{widthIn:8,heightIn:3});
 assert.deepEqual(presetSize({name:'Logo',widthIn:6,heightIn:5},'IMAGE'),{widthIn:6,heightIn:5});
});
