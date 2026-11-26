import {test} from 'node:test';import {strict as assert} from 'node:assert';
import {searchCatalog} from '../src/search.mjs';
test('shop search finds a paper format through catalog aliases',()=>{
 const paper={name:'Bond paper',size:'8.5 x 11',searchAliases:'letter size us letter 8.5 by 11'};
 const frame={name:'Photo frame',size:'8.5 x 11'};
 assert.deepEqual(searchCatalog('letter size',[paper,frame]).products,[paper]);
});
test('product tags are searchable with all query terms and older catalogs still work',()=>{
 const products=[{name:'White polo',tags:['School Wear','Embroidery Ready']},{name:'Plain cup'}];
 assert.deepEqual(searchCatalog('school polo',products).products,[products[0]]);
 assert.deepEqual(searchCatalog('EMBROIDERY',products).products,[products[0]]);
 assert.equal(searchCatalog('school cup',products).products.length,0);
});
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
