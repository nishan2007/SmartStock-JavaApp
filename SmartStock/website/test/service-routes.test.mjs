import test from 'node:test';import assert from 'node:assert/strict';
import {routeFromHash,topicFromHash} from '../src/routes.mjs';
import {availableServices,serviceAvailable} from '../src/services.mjs';
test('service pages retain their topic on reload',()=>{
 assert.equal(routeFromHash('#create/3D%20Printing'),'create');
 assert.equal(topicFromHash('#create/3D%20Printing'),'3D Printing');
 assert.equal(topicFromHash('#create/Business%20Printing'),'Business Printing');
 assert.equal(topicFromHash('#create/%E0%A4%A'),'3D Printing');
});
test('store availability removes paused services from discovery and leaves custom ideas open',()=>{
 const active=availableServices(['embroidery','3d-printing']);
 assert.equal(active.some(service=>service.slug==='embroidery'),false);
 assert.equal(active.some(service=>service.slug==='3d-printing'),false);
 assert.equal(serviceAvailable('Embroidery',['embroidery']),false);
 assert.equal(serviceAvailable('Custom Project',['embroidery']),true);
 assert.equal(serviceAvailable('Embroidery',[]),true);
});
