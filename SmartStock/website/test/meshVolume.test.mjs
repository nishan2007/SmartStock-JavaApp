import test from 'node:test';
import assert from 'node:assert/strict';
import {enclosedMeshVolume} from '../src/meshVolume.mjs';
import {inspectObj} from '../src/obj.mjs';
import {inspectStl} from '../src/stl.mjs';

const a=[0,0,0],b=[1,0,0],c=[0,1,0],d=[0,0,1];
const tetra=[[a,c,b],[a,b,d],[a,d,c],[b,c,d]];
test('closed tetrahedron has an enclosed geometric volume in model units',()=>{
  assert.ok(Math.abs(enclosedMeshVolume(tetra)-1/6)<1e-12);
  const obj=new TextEncoder().encode('v 0 0 0\nv 1 0 0\nv 0 1 0\nv 0 0 1\nf 1 3 2\nf 1 2 4\nf 1 4 3\nf 2 3 4\n');
  assert.ok(Math.abs(inspectObj(obj.buffer,{preview:true}).volume-1/6)<1e-12);
  const stl=new TextEncoder().encode('solid tetra\n'+tetra.map(face=>'facet normal 0 0 0\nouter loop\n'+face.map(point=>`vertex ${point.join(' ')}`).join('\n')+'\nendloop\nendfacet').join('\n')+'\nendsolid tetra');
  assert.ok(Math.abs(inspectStl(stl.buffer,{preview:true}).volume-1/6)<1e-12);
  const binary=new ArrayBuffer(84+tetra.length*50),view=new DataView(binary);
  view.setUint32(80,tetra.length,true);
  tetra.forEach((face,i)=>face.forEach((point,j)=>point.forEach((value,axis)=>view.setFloat32(84+i*50+12+j*12+axis*4,value,true))));
  assert.ok(Math.abs(inspectStl(binary,{preview:true}).volume-1/6)<1e-12);
});
test('open, degenerate, and inconsistently wound meshes do not get a volume estimate',()=>{
  assert.equal(enclosedMeshVolume(tetra.slice(0,3)),null);
  assert.equal(enclosedMeshVolume([[a,b,b]]),null);
  assert.equal(enclosedMeshVolume([tetra[0],...tetra.slice(1).map(face=>[...face].reverse())]),null);
});
