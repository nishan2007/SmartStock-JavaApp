const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
function setup(postError, camera={}, searchItems){
 const nodes=new Map(),requests=[];let failPost=true;
 const node=id=>{if(!nodes.has(id))nodes.set(id,{value:'',textContent:'',innerHTML:'',hidden:false,disabled:false,dataset:{},children:[],classList:{toggle(){}},replaceChildren(){this.innerHTML='';this.children=[]},close(){this.open=false},showModal(){this.open=true},addEventListener(){},focus(){},append(child){this.children.push(child)},setAttribute(){},scrollIntoView(){}});return nodes.get(id)};
 let nextElement=0;
 const context={URLSearchParams,document:{getElementById:node,createElement:()=>node('generated'+nextElement++)},location:{hash:'',pathname:'/receiving.html'},history:{replaceState(){}},window:{addEventListener(){}},crypto:{randomUUID:()=> 'batch-key-1'},console,fetch:async(url,options)=>{requests.push({url,options});let data={};if(url.endsWith('/session'))data={csrfToken:'csrf',permissions:['RECEIVING_INVENTORY','EDIT_ITEM'],locationId:4};if(url.endsWith('/search'))data={items:searchItems?.(JSON.parse(options.body).query)??[{itemType:'Product',itemId:7,name:'<Item>',code:'00123',quantityOnHand:5}]};if(url.endsWith('/gallery'))data={photos:['https://example.invalid/front.jpg','https://example.invalid/back.jpg']};if(url.endsWith('/add-barcode'))data={barcode:'00043'};if(url.endsWith('/post')){if(postError)return {ok:false,status:409,json:async()=>({ok:false,error:{code:postError,message:'Stock changed'}})};if(failPost){failPost=false;throw Error('Connection lost')}data={receiveId:'R-123',lineCount:1}}return {ok:true,status:200,json:async()=>({ok:true,data})}}};
 vm.runInNewContext(fs.readFileSync('SmartStock/src/mobile-web/receiving.js','utf8'),{...context,...camera});
 return {node,requests};
}
const tick=()=>new Promise(r=>setImmediate(r));
test('receiving retains the same body and key after a lost response and clears a confirmed batch',async()=>{
 const {node,requests}=setup();await tick();node('query').value='00123';await node('searchForm').onsubmit({preventDefault(){}});
 assert.equal(node('selected').hidden,false);
 assert.match(node('lines').innerHTML,/No items yet/);
 node('quantityForm').onsubmit({preventDefault(){}});
 assert.match(node('lines').innerHTML,/&lt;Item&gt;/);assert.match(node('lines').innerHTML,/00123/);
 node('batchForm').onsubmit({preventDefault(){}});await node('post').onclick();assert.equal(node('searchButton').disabled,true);
 await node('post').onclick();const posts=requests.filter(r=>r.url.endsWith('/post'));
 assert.equal(posts.length,2);assert.equal(posts[0].options.body,posts[1].options.body);assert.equal(posts[0].options.headers['Idempotency-Key'],posts[1].options.headers['Idempotency-Key']);
 assert.equal(posts[0].options.headers['X-CSRF-Token'],'csrf');
 const body=JSON.parse(posts[0].options.body);assert.equal(body.lines[0].countedStock,5);assert.equal(body.lines[0].expectedSystemStock,5);assert.equal(body.lines[0].quantity,1);
 assert.match(node('notice').textContent,/R-123/);assert.match(node('lines').innerHTML,/No items yet/);
});

test('camera barcode searches immediately, preserves leading zeros and releases the camera',async()=>{
 let decoded,stopped=0,trackStopped=false;
 const controls={stop(){stopped++;}};
 const {node,requests}=setup(undefined,{
  navigator:{mediaDevices:{async getUserMedia(){return {getTracks:()=>[{stop(){trackStopped=true;}}]};}}},
  ZXingBrowser:{BrowserMultiFormatOneDReader:class{
   async decodeFromVideoElement(video,callback){assert.ok(video.srcObject);decoded=callback;return controls;}
  }}
 });
 await tick();
 await node('scanBarcode').onclick();
 assert.equal(node('scanner').open,true);
 decoded({getText:()=> '00123'},null,controls);
 decoded({getText:()=> '99999'},null,controls);
 await tick();
 assert.equal(node('scanner').open,false);
 assert.equal(stopped,1);
 assert.equal(trackStopped,true);
 const searches=requests.filter(request=>request.url.endsWith('/search'));
 assert.deepEqual(searches.map(request=>JSON.parse(request.options.body).query),['00123']);
 assert.equal(node('selected').hidden,false);
});

test('closing while camera permission is pending releases the late stream',async()=>{
 let grant,trackStopped=false;
 const {node}=setup(undefined,{
  navigator:{mediaDevices:{getUserMedia(){return new Promise(resolve=>{grant=resolve;});}}},
  ZXingBrowser:{BrowserMultiFormatOneDReader:class{}}
 });
 await tick();
 const opening=node('scanBarcode').onclick();
 node('closeScanner').onclick();
 grant({getTracks:()=>[{stop(){trackStopped=true;}}]});
 await opening;
 assert.equal(trackStopped,true);
 assert.equal(node('scannerVideo').srcObject,null);
 assert.equal(node('scanner').open,false);
});

test('review prompts for each missing sales shelf and posts the entries with the delivery',async()=>{
 const products=[
  {itemType:'Product',itemId:1,name:'First',code:'001',quantityOnHand:5,salesShelf:''},
  {itemType:'Product',itemId:2,name:'Already assigned',code:'002',quantityOnHand:3,salesShelf:'A1'},
  {itemType:'Custom Item',itemId:3,name:'Custom',code:'003',quantityOnHand:0},
  {itemType:'Product',itemId:4,name:'Last',code:'004',quantityOnHand:2,salesShelf:''}
 ];
 const {node,requests}=setup(undefined,{},query=>products.filter(product=>product.code===query));
 const submit={preventDefault(){}};
 await tick();
 for(const product of products){
  node('query').value=product.code;
  await node('searchForm').onsubmit(submit);
  node('quantityForm').onsubmit(submit);
 }
 node('batchForm').onsubmit(submit);
 assert.equal(node('shelfDialog').open,true);
 assert.equal(node('shelfItem').textContent,'First');
 node('salesShelf').value='  ';
 node('shelfForm').onsubmit(submit);
 assert.equal(node('shelfItem').textContent,'First');
 node('salesShelf').value=' B2 ';
 node('shelfForm').onsubmit(submit);
 assert.equal(node('shelfItem').textContent,'Last');
 node('salesShelf').value='C3';
 node('shelfForm').onsubmit(submit);
 assert.equal(node('shelfDialog').open,false);
 assert.equal(node('confirm').open,true);
 await node('post').onclick();
 await node('post').onclick();
 const posts=requests.filter(request=>request.url.endsWith('/post'));
 assert.equal(posts[0].options.body,posts[1].options.body);
 assert.deepEqual(JSON.parse(posts[0].options.body).lines.map(line=>line.salesShelf),['B2',undefined,undefined,'C3']);
});


test('concurrent stock change discards the stale batch and requires fresh item lookup',async()=>{
 const {node}=setup('STOCK_CHANGED');await tick();node('query').value='00123';await node('searchForm').onsubmit({preventDefault(){}});
 node('quantityForm').onsubmit({preventDefault(){}});
 node('batchForm').onsubmit({preventDefault(){}});await node('post').onclick();
 assert.match(node('notice').textContent,/Stock changed/);assert.match(node('lines').innerHTML,/No items yet/);
 assert.equal(node('confirm').open,false);assert.equal(node('searchButton').disabled,false);
});

test('a scan opens the gallery and a new barcode targets the selected item independently of quantity',async()=>{
 const {node,requests}=setup();await tick();
 node('query').value='00123';await node('searchForm').onsubmit({preventDefault(){}});await tick();
 assert.equal(node('photoCount').textContent,'1 of 2');
 node('nextPhoto').onclick();assert.equal(node('photoCount').textContent,'2 of 2');
 node('previousPhoto').onclick();assert.equal(node('photoCount').textContent,'1 of 2');
 node('addBarcode').onclick();node('barcode').value='00043';
 await node('barcodeForm').onsubmit({preventDefault(){}});
 const request=requests.find(r=>r.url.endsWith('/add-barcode'));
 assert.deepEqual(JSON.parse(request.options.body),{itemType:'Product',itemId:7,barcode:'00043'});
 assert.equal(node('barcodeDialog').open,false);
 assert.match(node('lines').innerHTML,/No items yet/);
 node('quantity').value='12';node('quantityForm').onsubmit({preventDefault(){}});
 assert.equal(node('total').textContent,'1 item · 12 units');
});
