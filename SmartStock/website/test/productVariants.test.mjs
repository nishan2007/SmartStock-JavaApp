import {test} from 'node:test';
import {strict as assert} from 'node:assert';
import {publishedVariants,variantLabel} from '../src/productVariants.mjs';

test('only published siblings supplied by the store catalog become variant choices',()=>{
  const red={id:1,name:'Shirt Red',groupId:'group-a',optionNames:['Color','Size'],variantOptions:{Color:'Red',Size:'M'}};
  const blue={id:2,name:'Shirt Blue',groupId:'group-a',optionNames:['Color','Size'],variantOptions:{Color:'Blue',Size:'M'}};
  const other={id:3,name:'Other',groupId:'group-b',variantOptions:{Color:'Black'}};
  assert.deepEqual(publishedVariants(red,[red,other,blue]),[blue,red]);
  assert.equal(variantLabel(red),'Red · M');
  assert.deepEqual(publishedVariants({...red,groupId:null},[red,blue]),[]);
});
