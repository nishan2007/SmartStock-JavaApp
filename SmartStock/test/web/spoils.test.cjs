const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
function page(permissions,lines){
 const nodes=new Map();
 function element(){return {style:{},value:'',textContent:'',disabled:false,classList:{remove(){},toggle(){}},setAttribute(){},before(node){nodes.set('#'+node.id,node)},replaceChildren(){},append(){}}}
 const context=vm.createContext({$:id=>{if(!nodes.has(id)&&!['#spoilAccess','#spoilRecordHeading'].includes(id))nodes.set(id,element());return nodes.get(id)},document:{createElement:element},sessionStorage:{getItem(){return null}},can:key=>permissions.includes(key),URL:{revokeObjectURL(){}},console});
 vm.runInContext(fs.readFileSync('SmartStock/src/mobile-web/spoils.js','utf8'),context);
 context.lines=lines;
 vm.runInContext('spoilState={lines};renderSpoilAccess();spoilImpact()',context);
 return nodes;
}
test('history-only users see the recording section and the missing permission',()=>{
 const nodes=page(['MANAGE_CUSTOM_ORDERS'],[{lineId:1,eligible:true}]);
 assert.match(nodes.get('#spoilAccess').textContent,/Record Custom Order Spoils permission/);
 assert.equal(nodes.get('#spoilSubmit').disabled,true);
 assert.equal(nodes.get('#spoilReason').disabled,true);
});
test('recorders start on an eligible line and can enter evidence',()=>{
 const nodes=page(['RECORD_CUSTOM_ORDER_SPOILS'],[{lineId:1,eligible:false},{lineId:2,eligible:true}]);
 assert.equal(nodes.get('#spoilLine').value,'2');
 assert.equal(nodes.get('#spoilSubmit').disabled,false);
 assert.equal(nodes.get('#spoilReason').disabled,false);
 assert.match(nodes.get('#spoilAccess').textContent,/Then tap Record spoil/);
});
test('orders without eligible lines explain why recording is unavailable',()=>{
 const nodes=page(['RECORD_CUSTOM_ORDER_SPOILS'],[{lineId:1,eligible:false}]);
 assert.match(nodes.get('#spoilAccess').textContent,/No lines on this order/);
 assert.equal(nodes.get('#spoilSubmit').disabled,true);
});

test('selected shirt details include size, colour, customization and print add-ons',()=>{
 const nodes=page(['RECORD_CUSTOM_ORDER_SPOILS'],[{lineId:2,eligible:true,item:'Polo shirt',variant:'Adult',size:'XL',color:'Navy',customization:'Name: Alex',notes:'Logo on left chest',addons:[{material:'Embroidery',size:'3 inches',description:'School crest'}],production:'IN_PROGRESS',delivery:'PENDING',returned:'NONE'}]);
 const text=nodes.get('#spoilLineDetails').textContent;
 for(const value of ['Size: XL','Colour: Navy','Name: Alex','Logo on left chest','Embroidery / 3 inches / School crest','Delivery: PENDING'])assert.ok(text.includes(value),value);
});