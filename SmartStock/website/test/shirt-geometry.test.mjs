import test from 'node:test';
import assert from 'node:assert/strict';
import {layerCorners,resizeLayer} from '../src/shirtGeometry.mjs';

const close=(a,b)=>assert.ok(Math.abs(a-b)<1e-6,`${a} != ${b}`);
for(const type of ['TEXT','IMAGE'])for(const rotation of [0,25,-35])for(let corner=0;corner<4;corner++){
 test(`${type}: corner ${corner} resizes rotated ${rotation}° artwork around its opposite corner`,()=>{
  const layer={type,x:.3,y:.3,w:.3,h:.2,rotation},aspect=11/8.5;
  const corners=layerCorners(layer,aspect),anchor=corners[(corner+2)%4];
  for(const scale of [.6,1.4]){
   const point={x:anchor.x+(corners[corner].x-anchor.x)*scale,y:anchor.y+(corners[corner].y-anchor.y)*scale};
   const resized=resizeLayer(layer,corner,point,aspect);
   close(resized.w,layer.w*scale);close(resized.h,layer.h*scale);
   const fixed=layerCorners(resized,aspect)[(corner+2)%4];close(fixed.x,anchor.x);close(fixed.y,anchor.y);
  }
 });
}
test('resizing stays within the print area and preset dimensions',()=>{
 const layer={type:'IMAGE',x:.2,y:.2,w:.3,h:.3,rotation:0};
 const large=resizeLayer(layer,2,{x:5,y:5},1,{w:.5,h:.4});
 assert.ok(large.w<=.5&&large.h<=.4);
 for(let corner=0;corner<4;corner++){
  const resized=resizeLayer({...layer,rotation:25},corner,{x:5,y:-5},1);
  assert.ok(layerCorners(resized).every(p=>p.x>=-1e-6&&p.y>=-1e-6&&p.x<=1+1e-6&&p.y<=1+1e-6));
 }
});
