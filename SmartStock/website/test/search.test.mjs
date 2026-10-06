import {test} from 'node:test';import {strict as assert} from 'node:assert';
import {searchCatalog} from '../src/search.mjs';
test('global search separates products, services and categories',()=>{
 const results=searchCatalog('business',[{name:'Card stock',sku:'C1',category:'Business Printing'},{name:'Black ink',sku:'B1',category:'Office'}]);
 assert.equal(results.products.length,1);assert.equal(results.products[0].name,'Card stock');
 assert(results.services.some(s=>s.name==='Business Printing'));assert.deepEqual(results.categories,['Business Printing']);
});
test('global search includes SKU and matches all terms',()=>{
 const products=[{name:'Black toner',sku:'CAN-220',category:'Ink'},{name:'Black ink',sku:'HP-1',category:'Ink'}];
 assert.deepEqual(searchCatalog('can black',products).products.map(p=>p.sku),['CAN-220']);
 assert.equal(searchCatalog('   ',products).products.length,0);
});
test('global search finds published projects by material and tags',()=>{
 const projects=[{title:'Company polo',summary:'A stitched team uniform',category:'Apparel',materials:'Cotton',productionMethod:'Embroidery',tags:['corporate']}];
 assert.deepEqual(searchCatalog('cotton',[],undefined,projects).projects,projects);
 assert.deepEqual(searchCatalog('corporate',[],undefined,projects).projects,projects);
 assert.equal(searchCatalog('private',[],undefined,projects).projects.length,0);
});
