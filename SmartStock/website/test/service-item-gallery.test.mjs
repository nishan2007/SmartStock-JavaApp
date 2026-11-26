import test from 'node:test';
import assert from 'node:assert/strict';
import {serviceItemGallery,variantDimensions,selectVariantOption} from '../src/serviceItemGallery.mjs';
test('gallery includes ordered main, additional and every variant photo',()=>{
  const photos=serviceItemGallery({id:1,name:'Cup',image:true,photoCount:2,variants:[{id:10,name:'Pink',image:true,photoCount:2},{id:11,name:'Black',image:true},{id:12,name:'No photo',image:false}]},3);
  assert.equal(photos.length,5);assert.deepEqual(photos.map(p=>p.variantId),[null,null,10,10,11]);
  assert.ok(photos[1].src.endsWith('photoIndex=1'));assert.ok(photos[3].src.includes('variantId=10&photoIndex=1'));
});
test('legacy plaque snapshots retain working photo routes',()=>{
  assert.ok(serviceItemGallery({id:1,name:'Sublimation plaque',variants:[{id:2,name:'Heart',image:true}]},1)[0].src.includes('kind=plaque'));
});
test('linked colour and size choices select only existing combinations',()=>{
  const variants=[{id:1,color:'Pink',size:'Small'},{id:2,color:'Pink',size:'Large'},{id:3,color:'Blue',size:'Large'}];
  assert.deepEqual(variantDimensions(variants),{colors:['Pink','Blue'],sizes:['Small','Large'],color:true,size:true,named:false});
  assert.equal(selectVariantOption(variants,variants[0],'color','Blue').id,3);
  assert.equal(selectVariantOption(variants,variants[2],'color','Pink').id,2);
  assert.equal(selectVariantOption(variants,undefined,'size','Missing'),null);
  assert.equal(variantDimensions([{color:'Pink'},{color:'Blue'}]).size,false);
  assert.equal(variantDimensions([{size:'Small'},{size:'Large'}]).color,false);
  assert.equal(variantDimensions([{name:'Heart',size:'5x5'},{name:'Square',size:'5x5'}]).named,true);
});
