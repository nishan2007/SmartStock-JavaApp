import test from 'node:test';import assert from 'node:assert/strict';import {loadCart,changeQuantity} from '../src/cart.mjs';
test('persisted carts discard invalid or excessive quantities',()=>{assert.deepEqual(loadCart({getItem:()=>'{"1":2,"2":-1,"3":1000,"bad":5,"4":"3"}'}),{'1':2});assert.deepEqual(loadCart({getItem:()=>'{broken'}),{});});
test('removing a line preserves the remaining cart and does not mutate the original',()=>{const bag={1:2,2:3};assert.deepEqual(changeQuantity(bag,1,0),{2:3});assert.deepEqual(bag,{1:2,2:3});});
