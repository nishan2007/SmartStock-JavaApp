import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const file=readFileSync(new URL('../public/assets/shirts/polo.glb',import.meta.url));
const size=file.readUInt32LE(12),gltf=JSON.parse(file.subarray(20,20+size)),binary=file.subarray(28+size);
function accessor(index){
 const a=gltf.accessors[index],view=gltf.bufferViews[a.bufferView];
 const type=a.componentType===5125?Uint32Array:a.componentType===5123?Uint16Array:Float32Array;
 return new type(binary.buffer,binary.byteOffset+(view.byteOffset||0)+(a.byteOffset||0),a.count*(a.type==='VEC3'?3:1));
}
test('polo collar is stitched to the actual neckline and has no detached leaves',()=>{
 assert.ok(!gltf.meshes.some(m=>m.name==='Pointed collar leaf'));
 const collar=gltf.meshes.find(m=>m.name==='Stitched folded polo collar');assert.ok(collar);
 const points=accessor(collar.primitives[0].attributes.POSITION),body=accessor(gltf.meshes[0].primitives[0].attributes.POSITION);
 // Each cross-section starts on the body rim, with a 1 mm seam allowance.
 for(let i=0;i<points.length;i+=9*3){let closest=Infinity;
  for(let j=0;j<body.length;j+=3)closest=Math.min(closest,Math.hypot(points[i]-body[j],points[i+1]-body[j+1],points[i+2]-body[j+2]));
  assert.ok(closest<=.00101,`Detached collar seam: ${closest}`);
 }
 const indices=accessor(collar.primitives[0].indices);
 for(let i=0;i<indices.length;i+=3)for(let k=0;k<3;k++){
  const a=indices[i+k]*3,b=indices[i+(k+1)%3]*3;
  const length=Math.hypot(points[a]-points[b],points[a+1]-points[b+1],points[a+2]-points[b+2]);
  assert.ok(Number.isFinite(length)&&length<.04,`Collar spike: ${length}`);
 }
});
test('polo has two visible folded collar points rather than only a neckline band',()=>{
 const leaves=gltf.meshes.filter(m=>m.name==='Attached folded collar point');assert.equal(leaves.length,2);
 for(const leaf of leaves){
  const positions=accessor(leaf.primitives[0].attributes.POSITION),ys=[],xs=[];
  for(let i=0;i<positions.length;i+=3){xs.push(positions[i]);ys.push(positions[i+1]);}
  assert.ok(Math.max(...ys)-Math.min(...ys)>.07,'Collar point needs a visible fold and downward tip');
  assert.ok(Math.max(...xs)-Math.min(...xs)>.06,'Collar point must be broad enough to read as a polo collar');
 }
});
