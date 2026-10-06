import {test} from 'node:test';
import {deepStrictEqual, strictEqual} from 'node:assert';
import {reorderBag} from '../src/reorder.mjs';

test('reorder uses current store availability and keeps existing items', () => {
  const result = reorderBag(
    [{id:1,name:'Shirt',quantity:2},{id:2,name:'Ink',quantity:3},{id:3,name:'Old item',quantity:1}],
    [{id:1,canOrder:true},{id:2,canOrder:false}], {'1':1,'4':2});
  deepStrictEqual(result.bag, {'1':3,'4':2});
  strictEqual(result.added, 2);
  deepStrictEqual(result.unavailable, ['Ink','Old item']);
});

test('reorder never exceeds the cart limit', () => {
  const result = reorderBag([{id:1,name:'Paper',quantity:10}], [{id:1,canOrder:true}], {'1':995});
  strictEqual(result.bag[1],999);
  strictEqual(result.added,4);
});
