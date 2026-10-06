import {test} from 'node:test';
import {deepStrictEqual, strictEqual, throws} from 'node:assert';
import {inspectStl} from '../src/stl.mjs';

test('ASCII STL inspection reports bounds and facets', () => {
  const data = new TextEncoder().encode('solid sample\nfacet normal 0 0 1\nouter loop\nvertex 0 0 0\nvertex 2 0 0\nvertex 0 3 4\nendloop\nendfacet\nendsolid sample');
  deepStrictEqual(inspectStl(data.buffer), {triangles:1,dimensions:[2,3,4]});
});

test('binary STL inspection reads vertices even when header begins with solid', () => {
  const data = new ArrayBuffer(134), view = new DataView(data);
  new Uint8Array(data).set(new TextEncoder().encode('solid binary'));
  view.setUint32(80,1,true);
  [[0,0,0],[4,0,0],[0,5,6]].forEach((point,i) => point.forEach((value,j) => view.setFloat32(96+i*12+j*4,value,true)));
  const result = inspectStl(data);
  strictEqual(result.triangles,1);
  deepStrictEqual(result.dimensions,[4,5,6]);
  deepStrictEqual(inspectStl(data,{preview:true}).preview, [[[0,0,0],[4,0,0],[0,5,6]]]);
});

test('invalid and incomplete models are rejected', () => {
  throws(() => inspectStl(new Uint8Array(6).buffer));
  const data = new TextEncoder().encode('solid bad\nvertex 0 0 0\nendsolid bad');
  throws(() => inspectStl(data.buffer));
});
