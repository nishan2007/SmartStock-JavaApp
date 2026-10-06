import test from 'node:test';
import assert from 'node:assert/strict';
import {routeFromHash,routeFromLocation,projectFromHash,projectFromPath,productFromPath,serviceFromPath,editorialFromPath} from '../src/routes.mjs';
test('deep links restore durable screens and reject unknown routes', () => {
  for (const route of ['shop','bag','account','login','stores','help']) assert.equal(routeFromHash('#'+route),route);
  assert.equal(routeFromHash('#unknown'),'home');
  assert.equal(routeFromHash('#made'),'made');
  assert.equal(routeFromHash('#project/00000000-0000-0000-0000-000000000001'),'project');
  assert.equal(projectFromHash('#project/00000000-0000-0000-0000-000000000001'),'00000000-0000-0000-0000-000000000001');
  assert.equal(projectFromHash('#project/private-not-an-id'),'');
});
test('clean project URLs select the store and project on reload', () => {
  const path='/shop/projects/2/00000000-0000-0000-0000-000000000001';
  assert.deepEqual(projectFromPath(path),{storeId:2,id:'00000000-0000-0000-0000-000000000001'});
  assert.equal(routeFromLocation(path,''),'project');
  assert.equal(projectFromPath('/shop/projects/0/00000000-0000-0000-0000-000000000001'),null);
});
test('clean product URLs select a published product and store on reload',()=>{
  assert.deepEqual(productFromPath('/shop/products/2/17'),{storeId:2,id:17});
  assert.equal(routeFromLocation('/shop/products/2/17',''),'product');
  assert.equal(productFromPath('/shop/products/0/17'),null);
  assert.equal(productFromPath('/shop/products/2/not-a-product'),null);
});
test('discovery pages have durable clean URLs',()=>{
  for(const page of ['services','business','about']){
    assert.equal(editorialFromPath(`/shop/${page}`),page);
    assert.equal(routeFromLocation(`/shop/${page}`,''),page);
  }
  assert.equal(editorialFromPath('/shop/unknown'),'');
});
test('service pages have durable clean URLs',()=>{
  for(const [slug,name] of [['3d-printing','3D Printing'],['custom-printing','Custom Printing'],['custom-apparel','Custom Apparel'],['embroidery','Embroidery'],['signs-banners','Signs & Banners'],['business-printing','Business Printing'],['personalized-gifts','Personalized Gifts'],['business-cards','Business Cards'],['banner-printing','Banner Printing'],['custom-t-shirts','Custom T-Shirts'],['laser-engraving','Laser Engraving'],['stationery','Stationery'],['large-format-printing','Large Format Printing'],['signs','Signs']]){
    assert.equal(serviceFromPath(`/shop/services/${slug}`),name);
    assert.equal(routeFromLocation(`/shop/services/${slug}`,''),'create');
  }
  assert.equal(serviceFromPath('/shop/services/unknown'),'');
});
test('refresh never restores an unreviewed checkout or empty confirmation', () => {
  assert.equal(routeFromHash('#checkout'),'bag');
  assert.equal(routeFromHash('#confirmation'),'account');
  assert.equal(routeFromHash('#checkout',true),'checkout');
});
