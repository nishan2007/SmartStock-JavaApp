import test from 'node:test';
import assert from 'node:assert/strict';
import {latest3DPrints,relatedProjects,alternativeMethods,projectsForProduct,capabilityProject,projectForServices} from '../src/projectDiscovery.mjs';

const project=(id,category,method,tags,updatedAt)=>({id,title:id,category,productionMethod:method,tags,materials:'cotton',updatedAt});
test('recent 3D prints are selected by method and recency, not featured position',()=>{
  const items=[project('shirt','Apparel','DTF',[],'2026-09-27'),project('old','3D Printing','FDM',[],'2026-09-25'),project('new','Custom','Resin 3D print',[],'2026-09-27')];
  assert.deepEqual(latest3DPrints(items).map(item=>item.id),['new','old']);
});
test('service discovery uses only a covered project linked to that service',()=>{
  const unrelated={...project('unrelated','Apparel','Embroidery',[],'2026-09-28'),cover:'photo',serviceSlug:'embroidery',featured:true};
  const plain={...project('plain','Signs','Print',[],'2026-09-27'),cover:'photo',serviceSlug:'signs-banners',featured:false};
  const featured={...project('featured','Signs','Print',[],'2026-09-26'),cover:'photo',serviceSlug:'signs-banners',featured:true};
  const missingCover={...project('missing','Signs','Print',[],'2026-09-29'),cover:'',serviceSlug:'signs-banners',featured:true};
  assert.equal(capabilityProject([unrelated,plain,featured,missingCover],'signs-banners')?.id,'featured');
  assert.equal(capabilityProject([unrelated],'signs-banners'),null);
  assert.equal(projectForServices([unrelated,plain,featured],['signs-banners','business-printing'])?.id,'featured');
});
test('related projects prefer matching methods and tags and omit current project',()=>{
  const current=project('current','Apparel','Embroidery',['team'],'2026-09-26');
  const close=project('close','Apparel','Embroidery',['team'],'2026-09-25');
  const other=project('other','Signs','Vinyl',[],'2026-09-27');
  other.materials='vinyl';
  assert.deepEqual(relatedProjects(current,[current,other,close]).map(item=>item.id),['close']);
});
test('other methods come from real projects in the same category without duplicates',()=>{
  const current=project('current','Apparel','Embroidery',[],'2026-09-27');
  const a=project('a','Apparel','DTF',[],'2026-09-26');
  const b=project('b','Apparel','DTF',[],'2026-09-25');
  const c=project('c','Signs','Vinyl',[],'2026-09-27');
  assert.deepEqual(alternativeMethods(current,[current,b,c,a]).map(item=>item.id),['a']);
});
test('product inspiration uses published project content without claiming it was made using the product',()=>{
  const projects=[project('polo','Apparel','Embroidery',['uniform'],'2026-09-26'),project('banner','Signs','Vinyl',[],'2026-09-27')];
  projects[0].title='Company Polo Shirts';
  projects[1].title='Window Banner';
  assert.deepEqual(projectsForProduct({name:'Polo Shirt',category:'Apparel'},projects).map(item=>item.id),['polo']);
  assert.deepEqual(projectsForProduct({name:'Printer Cartridge',category:'Ink'},projects),[]);
});
