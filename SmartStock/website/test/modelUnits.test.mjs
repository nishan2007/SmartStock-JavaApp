import test from 'node:test';
import assert from 'node:assert/strict';
import {physicalModelSize} from '../src/modelUnits.mjs';

test('selected model units convert geometric measurements without claiming material usage',()=>{
  assert.deepEqual(physicalModelSize([2,3,4],24,'CM'),{dimensionsMm:[20,30,40],volumeCm3:24});
  const inches=physicalModelSize([1,2,3],null,'IN');
  assert.deepEqual(inches.dimensionsMm.map(value=>Number(value.toFixed(1))),[25.4,50.8,76.2]);
  assert.equal(inches.volumeCm3,null);
  assert.equal(physicalModelSize([1,2,3],1,''),null);
  assert.equal(physicalModelSize([1,2,3],1,'__proto__'),null);
  assert.equal(physicalModelSize([Number.MAX_VALUE,2,3],1,'IN'),null);
});
