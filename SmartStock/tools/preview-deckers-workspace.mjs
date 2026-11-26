// Local UI verification fixture. Never connects to a store, Supabase, or a live database.
// node SmartStock/tools/preview-deckers-workspace.mjs [port]
import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../src');
const port=Number(process.argv[2]||4173), prefix='/employees/mobile-management/';
const names={PAYROLL:'Payroll',ORDERS:'Orders',INVENTORY:'Inventory',EDIT_ITEMS:'Edit Items',CUSTOMERS:'Customer Accounts',PHONE_MINUTES:'Phone Minutes',BALANCE_SHEET:'Balance Sheet',EMPLOYEE_WEB:'Employee History',OVERSEAS_ORDERS:'Overseas Orders',SCHEDULER:'Scheduler',MOBILE_ITEMS:'Add Item',RECEIVING:'Receiving Inventory',IMAGE_STUDIO:'Image Studio',CUSTOM_ORDER_DESIGN:'Order Design',CUSTOM_ORDER_SPOILS:'Custom Order Spoils'};
const local=new Set(['MOBILE_ITEMS','RECEIVING','EDIT_ITEMS','IMAGE_STUDIO','CUSTOM_ORDER_DESIGN','CUSTOM_ORDER_SPOILS']);
const assets={'logo.png':'Images/Deckers.png','icon-180.png':'shared-mobile/deckers-180.png','icon-192.png':'shared-mobile/deckers-192.png','icon-512.png':'shared-mobile/deckers-512.png','icons/ORDERS.png':'mobile-web/orders-icon-v2-192.png','icons/INVENTORY.png':'mobile-web/inventory-icon-192.png','icons.ttf':'mobile-web/minutes-icons.ttf','icons-LICENSE.txt':'mobile-web/minutes-icons-LICENSE.txt'};
const fixture=path.resolve(root,'../../tmp/deckers-screen-fixtures');
const permissions=['PAYROLL_DASHBOARD','ACCESS_EMPLOYEE_WEB','MANAGE_CUSTOM_ORDERS','ORDERS_MANAGER_DASHBOARD','VIEW_INVENTORY','NEW_ITEM','EDIT_ITEM','CUSTOMER_ACCOUNTS','BALANCE_SHEET','RECEIVING_INVENTORY','VIEW_RECEIVING_HISTORY','ACCESS_SCHEDULER_WEB','VIEW_EMPLOYEE_SCHEDULE','VIEW_EMPLOYEE_HISTORY','VIEW_ALL_EMPLOYEES','RECORD_CUSTOM_ORDER_SPOILS','MANAGE_CUSTOM_ORDER_ITEMS'];
const sessions=new Map();let counter=0;
const customerSample={customerId:901,accountNumber:'BA-00142',name:'Morgan Ellis',active:true,creditLimit:600000,
  currentBalance:524060,customOrderDue:0,totalDue:524060,availableCredit:75940,customerSince:2022};
const customerSale={eventId:'SALE:10482',locationId:901,transactionType:'SALE',documentNumber:'S-10482',
  storeName:'Main Store',paymentMethod:'ACCOUNT',documentStatus:'COMPLETED',amount:18500,itemSummary:'2 × Polo shirt, 1 × Embroidery',
  createdAtEpochMillis:Date.parse('2026-11-25T14:24:00Z')};
const customerOrder={eventId:'CUSTOM_ORDER:2086',locationId:901,transactionType:'CUSTOM_ORDER',documentNumber:'O-2086',
  storeName:'Main Store',paymentMethod:'CARD',documentStatus:'DELIVERED',amount:36000,itemSummary:'6 × Custom polo',
  createdAtEpochMillis:Date.parse('2026-11-20T14:24:00Z')};
const customerOrderDetails={
  summary:{customer_name:'Morgan Ellis',order_number:'O-2086',location_name:'Main Store',created_at:'2026-11-20T14:24:00Z',
    due_date:'2026-11-24',delivered_at:'2026-11-24T19:10:00Z',taken_by_name:'Alex Reed',assigned_to_name:'Jamie Cole',
    status:'DELIVERED',total_amount:36000,amount_paid:36000,balance_due:0,payment_status:'PAID',payment_method:'CARD',
    order_notes:'Call when ready for pickup.'},
  lines:Array.from({length:6},(_,index)=>({custom_order_line_id:index+1,item_name:'Custom polo',item_size:index<4?'M':'L',
    item_color:'Navy',unit_price:6000,line_total:6000,customization_details:'White logo, left chest',
    order_instructions:'Use the approved logo proof.',production_status:'FINISHED',delivery_status:'COLLECTED'})),
  files:[{file_id:'00000000-0000-0000-0000-000000000001',filename:'logo-proof.pdf'}],
  canOpenFiles:false,
  payments:[{created_at:'2026-11-24T19:10:00Z',payment_action:'PAYMENT',payment_amount:18000,payment_method:'CARD',payment_reference:'P-7842'},
    {created_at:'2026-11-20T14:24:00Z',payment_action:'PAYMENT',payment_amount:18000,payment_method:'CARD',payment_reference:'P-7810'}],
  deliveries:[{delivered_at:'2026-11-24T19:10:00Z',item_name:'Custom polo',delivered_by_name:'Jamie Cole',delivery_notes:'Collected at Main Store'}],
  production:[{created_at:'2026-11-24T15:45:00Z',item_name:'Custom polo',new_status:'READY',updated_by_name:'Alex Reed'}],
  activity:[{created_at:'2026-11-20T14:24:00Z',action_type:'CREATE',user_name:'Alex Reed'}]
};
const customerTransactions=[customerSale,customerOrder,...Array.from({length:10},(_,index)=>({
  eventId:'LEDGER:'+(50-index),locationId:901,transactionType:index%2?'MANUAL_CHARGE':'PAYMENT',
  paymentId:index%2?'':'P-'+(50-index),amount:index%2?3500:-12000,storeName:'Main Store',paymentMethod:index%2?'':'CASH',
  createdAtEpochMillis:Date.parse('2026-11-19T14:00:00Z')-index*86400000
}))];
const customerSaleDetails={summary:{receipt_number:'S-10482',created_at:'2026-11-25T14:24:00Z',store_name:'Main Store',
  cashier:'Alex Reed',payment_method:'ACCOUNT',payment_status:'UNPAID',subtotal_amount:18500,total_amount:18500,amount_paid:0},
  items:[{item_name:'Polo shirt',quantity:2,unit_price:7500,line_total:15000},
    {item_name:'Embroidery',quantity:1,unit_price:3500,line_total:3500}]};
const payrollSample=()=>[
 {userId:1,name:'Jamie Adams',hours:40,total:24000,paid:0,balance:24000,stores:[901]},
 {userId:2,name:'Maya Singh',hours:38,total:22800,paid:10000,balance:12800,stores:[902]},
 {userId:3,name:'Daniel Lewis',hours:40,total:26000,paid:26000,balance:0,stores:[901]}
].map(row=>({...row,start:'2026-11-16',end:'2026-11-22',photo:''})).concat([{userId:2,name:'Maya Singh',hours:36,total:21600,paid:21600,balance:0,stores:[902],start:'2026-11-09',end:'2026-11-15',photo:''}]);
const server=http.createServer(async(req,res)=>{
  const url=new URL(req.url,'http://127.0.0.1:'+port);
  const reply=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(status<400?{ok:true,data}:{ok:false,error:data}));};
  let id=(req.headers.cookie||'').match(/workspace_fixture=(\d+)/)?.[1], session=sessions.get(id);
  if(req.method==='GET'&&url.pathname===prefix){
    if(!session||url.searchParams.has('scenario')){id=String(++counter);const scenario=url.searchParams.get('scenario')||'home';session={approved:scenario!=='unapproved',signedIn:!['login','mfa'].includes(scenario),limited:scenario==='limited',mfa:scenario==='mfa',empty:scenario==='empty',store:scenario==='store'};sessions.set(id,session);res.setHeader('Set-Cookie',`workspace_fixture=${id}; Path=/; SameSite=Lax; HttpOnly`);}
  }
  try{
    if(req.method==='POST'){
      let raw='';for await(const chunk of req)raw+=chunk;
      const body=JSON.parse(raw||'{}');
      if(url.pathname==='/fixture-network-probe')return reply(200,{verified:true});
      if(!session?.approved)return reply(401,{code:'DEVICE_REQUIRED',message:'Pair this browser in Mobile Management.'});
      if(url.pathname==='/api/v1/session')return reply(401,{code:'LOGIN_REQUIRED',message:'Sign in to this store to continue.'});
      if(url.pathname.endsWith('/auth/login')){if(session.mfa)return reply(200,{mfaRequired:true,preauth:'fixture-only'});session.signedIn=true;return reply(200,{csrfToken:'fixture-only'});}
      if(url.pathname.endsWith('/auth/verify')){if(body.code!=='123456')return reply(401,{code:'AUTH_FAILED',message:'The authenticator code was not accepted.'});session.signedIn=true;return reply(200,{csrfToken:'fixture-only'});}
      if(!session?.signedIn)return reply(401,{code:'SESSION_REQUIRED',message:'Sign in to continue.'});
      if(url.pathname.endsWith('/auth/session'))return reply(200,{csrfToken:'fixture-only',permissions,userId:901,locationId:901,rememberDevice:true});
      if(url.pathname.endsWith('/auth/logout')){session.signedIn=false;return reply(200,{});}
      if(url.pathname===prefix+'state')return reply(200,{screens:true,name:'Preview iPhone',employeeName:'Jamie Davis',apps:session.empty?[]:Object.entries(names).filter(([key])=>!session.limited||['INVENTORY','RECEIVING'].includes(key)).map(([key,name])=>({key,name,icon:`icons/${key}.png`,localOnly:local.has(key)&&!session.store}))});
      if(url.pathname===prefix+'network-challenge')return reply(200,{challenge:'fixture-only',probeUrl:session.store?'https://127.0.0.1:'+port+'/mobile-management/network-probe':'https://127.0.0.1:1/mobile-management/network-probe'});
      if(url.pathname===prefix+'screen')return reply(200,JSON.parse(await fs.readFile(path.join(fixture,body.app+'.json'),'utf8')));
      if(url.pathname===prefix+'screen-api'){
        const route=body.route;
        if(route==='payroll/dashboard'){session.payroll??=payrollSample();const periods=[...new Set(session.payroll.map(row=>row.start+':'+row.end))].sort().reverse(),selectedPeriod=body.payload.period||periods[0];return reply(200,{rows:session.payroll.filter(row=>row.start+':'+row.end===selectedPeriod),periods,selectedPeriod,stores:[{id:901,name:'Main store'},{id:902,name:'Second store'}],today:'2026-11-21',currentStore:901,defaultStore:session.store&&body.challenge==='fixture-only'?901:0});}
        if(route==='payroll/pay'){
          const p=body.payload;session.payroll??=payrollSample();session.payrollRequests??=new Set();
          if(!session.payrollRequests.has(p.idempotencyKey)){const row=session.payroll.find(row=>row.userId===p.userId&&row.start===p.start&&row.end===p.end);if(!row||Number(row.paid)!==Number(p.expectedPaid))return reply(409,{message:'Payroll changed. Refresh.'});row.paid+=Number(p.amount);row.balance-=Number(p.amount);session.payrollRequests.add(p.idempotencyKey);}
          return reply(200,{recorded:true});
        }
        if(route==='customers/history'){
          const query=String(body.payload.query||'').toLowerCase();
          const matches=query?customerTransactions.filter(row=>[row.documentNumber,row.paymentId,
            row===customerSale?'polo shirt embroidery':'',row===customerOrder?'navy custom polo':''].some(value=>value?.toLowerCase().includes(query))):customerTransactions;
          return reply(200,{transactions:query?matches:matches.slice(0,10),count:matches.length,hasMore:false});
        }
        if(route==='customers/transaction'){
          const row=customerTransactions.find(transaction=>transaction.eventId===body.payload.eventId);
          if(!row)return reply(404,{message:'This transaction was not found.'});
          return reply(200,{transaction:row,...(row===customerSale?{sale:customerSaleDetails}:row===customerOrder?{order:customerOrderDetails}:{})});
        }
        const responses={
          'inventory/search':{locationId:901,products:[],items:[],rows:[],total:0},
          'customers/list':{accounts:session.empty?[]:[customerSample],customerTypes:[]},
          'customers/details':customerSample,
          'minutes/bootstrap':{branding:{name:'Deckers'},drawers:[],customers:[],paymentMethods:[]},'minutes/history':{entries:[],rows:[],hasMore:false},
          'bootstrap':{userId:901,name:'Jamie Davis',canBrowse:true,canEdit:false,canOverseas:true,overseasStores:[{id:901,name:'Preview store'}]},'employees':{employees:[],hasMore:false},
          'orders/dashboard':{dashboard:{metrics:{overdue:0,dueToday:0,ready:0,unpaid:0},actions:[]},employees:[],branding:{name:'Deckers'}},
          'overseas':{permissions:['OVERSEAS_ORDERS_QUOTE'],rows:[]},
          'balance-sheet':{workspaceId:'fixture-workspace',revision:1,storeZoneId:'America/Guyana',drafts:[],sheet:{balanceBf:0,totalIncome:0,totalReceivables:0,totalExpenses:0,totalPayables:0,cashInHand:0,balanceCf:0},rows:[]},
          'scheduler/bootstrap':{locations:[{locationId:901,name:'Preview store'}],permissions,editable:false,selfOnly:true,displayName:'Jamie Davis'},
          'scheduler/snapshot':{days:[],holidays:[],employees:[],shifts:[],revision:'fixture',selfOnly:true},
          'mobile/bootstrap':{permissions,locationId:901,departments:[],vendors:[],itemTypes:[],brands:[],shelves:[],deviceApps:[body.app]},
          'mobile/orders/lookup':{found:true,order:{orderId:901}},
          'mobile/design/state':{documents:[{id:'fixture-design',lineId:1,width:24,height:18,unit:'IN',revision:1,orderStatus:'NEW',document:{background:'#ffffff',objects:[]}}],files:[],proofs:[]},
          'mobile/custom/state':{items:[],variants:[],categories:[],sizeSets:[]},
          'mobile/studio/branding':{name:'Deckers'},'mobile/studio/catalog':{items:[],hasMore:false},
          'mobile/spoils/state':{reasons:[],entries:[]},'mobile/spoils/search':{orders:[]}
        };
        return reply(200,responses[route]||{});
      }
      if(url.pathname===prefix+'open')return reply(200,{url:'/fixture-app?app='+encodeURIComponent(body.app)});
      return reply(404,{code:'NOT_FOUND',message:'Fixture route not found.'});
    }
    if(url.pathname==='/employees/health')return reply(200,{available:true});
    if(url.pathname==='/employees/branding')return reply(200,{name:'Deckers'});
    if(url.pathname==='/fixture-app'){res.writeHead(200,{'Content-Type':'text/html'});return res.end('<h1>App navigation verified</h1><a href="'+prefix+'">Return to Deckers workspace</a>');}
    const nativeAssets={'/receiving.html':'mobile-web/receiving.html','/receiving.js':'mobile-web/receiving.js','/receiving.css':'mobile-web/receiving.css','/inventory-icon.svg':'mobile-web/inventory-icon.svg'};
    if(url.pathname.startsWith('/mobile-management/'))url.pathname=prefix+url.pathname.slice('/mobile-management/'.length);
    if(!url.pathname.startsWith(prefix)&&!nativeAssets[url.pathname])return reply(404,{code:'NOT_FOUND',message:'Not found.'});
    const route=url.pathname.slice(prefix.length);
    if(route.startsWith('screen-modules/')){const content=await fs.readFile(path.join(fixture,route.slice(15)));res.writeHead(200,{'Content-Type':'application/javascript'});return res.end(content);}
    const screenResource=route.startsWith('screen-assets/')&&!route.includes('..')?route.slice(14):null;
    const iconResource=route.startsWith('icons/')&&!route.includes('..')?'shared-mobile/'+route:null;
    const resource=screenResource||iconResource||nativeAssets[url.pathname]||assets[route]||({'':'shared-mobile/index.html','app.css':'shared-mobile/app.css','signin.css':'shared-mobile/signin.css','screens.js':'shared-mobile/screens.js','app.js':'shared-mobile/app.js','reconnect.js':'shared-mobile/reconnect.js','manifest.webmanifest':'shared-mobile/manifest.webmanifest'})[route];
    if(!resource)return reply(404,{code:'NOT_FOUND',message:'Asset not found.'});
    const content=await fs.readFile(path.join(root,resource));
    const ext=path.extname(resource), type={'.html':'text/html','.js':'application/javascript','.css':'text/css','.png':'image/png','.svg':'image/svg+xml','.ttf':'font/ttf','.webmanifest':'application/manifest+json','.txt':'text/plain'}[ext];
    res.writeHead(200,{'Content-Type':type,'Cache-Control':'no-store'});res.end(content);
  }catch(error){console.error(error.message);reply(500,{code:'FIXTURE_ERROR',message:'UI fixture failed.'});}
});
server.listen(port,'127.0.0.1',()=>console.log(`Deckers UI fixture: http://127.0.0.1:${port}${prefix}`));
