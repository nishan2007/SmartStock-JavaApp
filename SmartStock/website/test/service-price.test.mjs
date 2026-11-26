import {test} from 'node:test';
import assert from 'node:assert/strict';
import {servicePriceLabel} from '../src/servicePrice.mjs';

test('fixed and variant starting prices display with currency',()=>{
  assert.match(servicePriceLabel({price:2500}),/2,500/);
  assert.match(servicePriceLabel({price:2500,priceFrom:true}),/^From .*2,500/);
  assert.match(servicePriceLabel({price:0}),/0/);
});
test('missing and invalid prices require a quote',()=>{
  for(const price of [undefined,null,-1,NaN,Infinity])assert.equal(servicePriceLabel({price}),'Ask for a quote');
});
