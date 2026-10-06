import test from 'node:test';
import assert from 'node:assert/strict';
import {inspectObj} from '../src/obj.mjs';

const bytes = source => new TextEncoder().encode(source).buffer;
test('OBJ preview triangulates polygons and resolves negative face indices', () => {
  const model = inspectObj(bytes('v 0 0 0\nv 2 0 0\nv 2 3 0\nv 0 3 0\nf -4 -3 -2 -1\n'), {preview:true});
  assert.equal(model.triangles, 2);
  assert.deepEqual(model.dimensions, [2,3,0]);
  assert.deepEqual(model.center, [1,1.5,0]);
  assert.equal(model.preview.length, 2);
});
test('OBJ rejects missing vertices and non-mesh files', () => {
  assert.throws(() => inspectObj(bytes('v 0 0 0\nf 1 2 3\n')), /missing vertex/);
  assert.throws(() => inspectObj(bytes('v 0 0 0\n')), /no printable mesh/);
  assert.throws(() => inspectObj(bytes('v 0 NaN 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n')), /invalid coordinates/);
});
