import test from 'node:test';
import assert from 'node:assert/strict';
import {groupServiceItems,layoutServiceGroups} from '../src/serviceItemGroups.mjs';
test('saved types group main items and preserve variant galleries and catalogue order',()=>{
  const items=[{id:1,itemType:'Pillows'},{id:2,itemType:'Cups'},{id:3,itemType:' cups ',variants:[{id:7}]},{id:4,itemType:''},{id:5}];
  const groups=groupServiceItems(items);
  assert.deepEqual(groups.map(g=>g.name),['Cups','Pillows','Other items']);
  assert.deepEqual(groups.map(g=>g.items.map(i=>i.id)),[[2,3],[1],[4,5]]);
  assert.deepEqual(groups[0].items[1].variants,[{id:7}]);
  assert.equal(items[2].itemType,' cups ');
});
test('compact pairs fit single/single, single/double, double/double and single/triple',()=>{
  const group=n=>({items:Array.from({length:n},()=>({}))});
  for(const [a,b,spans]of [[1,1,[6,6]],[1,2,[4,8]],[2,1,[8,4]],[2,2,[6,6]],[1,3,[3,9]],[3,1,[9,3]]]){
    assert.deepEqual(layoutServiceGroups([group(a),group(b)]).map(g=>g.span),spans);
  }
  assert.deepEqual(layoutServiceGroups([group(2),group(3)]).map(g=>g.span),[12,12]);
  assert.deepEqual(layoutServiceGroups([{items:[{name:'Plaque',variants:[{}]}]},group(1)]).map(g=>g.span),[12,12]);
  assert.deepEqual(layoutServiceGroups([group(1),group(1),group(1)]).map(g=>g.span),[6,6,12]);
});
test('empty and unclassified catalogues remain usable without inferring types from names',()=>{
  assert.deepEqual(groupServiceItems([]),[]);
  assert.deepEqual(groupServiceItems([{name:'Coffee cup'}]).map(g=>g.name),['Other items']);
});
