export type AreaName='FRONT'|'CHEST'|'BACK';
export type Layer={type:'TEXT'|'IMAGE';presetId?:number;printMode?:'SINGLE'|'FULL';vinylColor?:string;x:number;y:number;w:number;h:number;rotation:number;text?:string;font?:string;color?:string;fileId?:string};
export type ShirtAddOn={availableColors?:Record<string,string>;id:number;materialId:number;name:string;material:string;price:number;mode?:'SINGLE'|'FULL';pricingMode?:'FIXED_PRESET'|'PER_LINE';kind:'TEXT'|'IMAGE'|'BOTH';widthIn?:number;heightIn?:number};
export type PrintArea={mode:'SINGLE'|'FULL';vinylColor:string;layers:Layer[]};
export type ShirtDesign={version:1;itemId:number;styleKey?:string;groups:{variantId:number;quantity:number}[];areas:Partial<Record<AreaName,PrintArea>>};
export type ShirtVariant={id:number;name:string;size:string;color:string;price:number};
export type Boundary={x:number;y:number;w:number;h:number;widthIn:number;heightIn:number;singlePresetId:number;fullPresetId:number};
export type Shirt={id:number;name:string;description:string;variants:ShirtVariant[];addOns?:ShirtAddOn[];config:{useSystemPresets?:boolean;shirtType?:'TEE'|'COLLARED';neckline?:'ROUND'|'V'|'COLLAR';fabric?:'NORMAL'|'DRY_FIT';websiteEnabled:boolean;frontMockup:string;backMockup:string;colors:Record<string,string>;vinylColors:string[];areas:Record<AreaName,Boundary>};guides:Record<AreaName,Record<'SINGLE'|'FULL',{id:number;materialId:number;name:string;material:string;price:number}>>};
export function shirtStyle(shirt:Shirt){return shirt.config.shirtType==='COLLARED'?'Collared shirt':shirt.config.neckline==='V'?'T-shirt · V-neck':'T-shirt · Round neck';}
export type ShirtEstimate={fingerprint:string;itemId:number;itemName:string;quantity:number;subtotal:number;vat:number;rounding:number;total:number;lines:(ShirtVariant&{quantity:number;printPrice:number;total:number})[];charges:{area:AreaName;layerIndex?:number;description?:string;mode:string;name:string;material:string;price:number}[]};
export const areaNames:AreaName[]=['FRONT','CHEST','BACK'];
export const areaLabels={FRONT:'Large front',CHEST:'Left chest',BACK:'Large back'};
export const fonts=['Deckers Sans','Deckers Serif','Deckers Mono'];
export function newDesign(shirt:Shirt):ShirtDesign{return {version:1,itemId:shirt.id,styleKey:shirt.config.shirtType==='COLLARED'?'COLLARED:COLLAR':`TEE:${shirt.config.neckline||'ROUND'}`,groups:[{variantId:shirt.variants[0].id,quantity:1}],areas:{}};}
export function areaOf(design:ShirtDesign,name:AreaName,shirt:Shirt):PrintArea{return design.areas[name]||{mode:'FULL',vinylColor:shirt.config.vinylColors[0],layers:[]};}
export type Artwork={fileId:string;file:File};
export type Draft={design:ShirtDesign;files:Artwork[];instructions:string;phone:string;date:string;requestId:string};
function db():Promise<IDBDatabase>{return new Promise((resolve,reject)=>{const request=indexedDB.open('deckers-shirt-drafts',1);request.onupgradeneeded=()=>request.result.createObjectStore('drafts');request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});}
export async function loadDraft(key:string):Promise<Draft|undefined>{const database=await db();return new Promise((resolve,reject)=>{const tx=database.transaction('drafts');const request=tx.objectStore('drafts').get(key);request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);tx.oncomplete=()=>database.close();});}
export async function saveDraft(key:string,draft:Draft|null){const database=await db();return new Promise<void>((resolve,reject)=>{const tx=database.transaction('drafts','readwrite');const store=tx.objectStore('drafts');if(draft)store.put(draft,key);else store.delete(key);tx.oncomplete=()=>{database.close();resolve();};tx.onerror=()=>{database.close();reject(tx.error);};});}
async function image(source:string):Promise<HTMLImageElement>{return new Promise((resolve,reject)=>{const img=new Image();img.onload=()=>resolve(img);img.onerror=reject;img.src=source;});}
/** The production preview and editor share one renderer; transforms use print-area coordinates. */
export async function renderShirt(canvas:HTMLCanvasElement,shirt:Shirt,design:ShirtDesign,side:'FRONT'|'BACK',color:string,artwork:Map<string,string>,boundaries=false,selected?:{area:AreaName;index:number},textureOnly:boolean|'ARTWORK'=false){
 await Promise.all(fonts.map(font=>document.fonts.load('20px "'+font+'"')));await document.fonts.ready;
 const ctx=canvas.getContext('2d');if(!ctx)return;const width=canvas.width;ctx.clearRect(0,0,width,width);
 if(textureOnly){if(textureOnly!=='ARTWORK'){ctx.fillStyle=color;ctx.fillRect(0,0,width,width);}}else{
 const mockup=await image(side==='FRONT'?shirt.config.frontMockup:shirt.config.backMockup);
 // The bundled neutral mockup is mid-gray. Normalize its lighting before
 // multiplying the fabric swatch so pale garments do not become dark gray.
 const lighting=document.createElement('canvas');lighting.width=lighting.height=width;
 const light=lighting.getContext('2d')!;light.drawImage(mockup,0,0,width,width);
 const pixels=light.getImageData(0,0,width,width);
 for(let i=0;i<pixels.data.length;i+=4){const value=Math.min(255,(.2126*pixels.data[i]+.7152*pixels.data[i+1]+.0722*pixels.data[i+2])*255/185);pixels.data[i]=pixels.data[i+1]=pixels.data[i+2]=value;}
 light.putImageData(pixels,0,0);
 const tint=document.createElement('canvas');tint.width=tint.height=width;const tc=tint.getContext('2d')!;
 tc.drawImage(mockup,0,0,width,width);tc.globalCompositeOperation='source-in';tc.fillStyle=color;tc.fillRect(0,0,width,width);
 tc.globalCompositeOperation='multiply';tc.drawImage(lighting,0,0,width,width);tc.globalCompositeOperation='destination-in';tc.drawImage(mockup,0,0,width,width);ctx.drawImage(tint,0,0);}

 for(const name of areaNames.filter(n=>side==='BACK'?n==='BACK':n!=='BACK')){
  const box=shirt.config.areas[name],area=areaOf(design,name,shirt);const bx=box.x*width,by=box.y*width,bw=box.w*width,bh=box.h*width;
  ctx.save();ctx.beginPath();ctx.rect(bx,by,bw,bh);ctx.clip();
  for(let i=0;i<area.layers.length;i++){
   const l=area.layers[i],mode=l.printMode||area.mode,vinylColor=l.vinylColor||area.vinylColor;const x=bx+l.x*bw,y=by+l.y*bh,w=l.w*bw,h=l.h*bh;ctx.save();ctx.translate(x+w/2,y+h/2);ctx.rotate(l.rotation*Math.PI/180);
   if(l.type==='TEXT'){ctx.fillStyle=mode==='SINGLE'?vinylColor:l.color||'#000000';ctx.textAlign='center';ctx.textBaseline='middle';let size=h*.8;ctx.font=`${size}px "${l.font||fonts[0]}"`;const measure=ctx.measureText(l.text||'').width;if(measure>w){size*=w/measure;ctx.font=`${size}px "${l.font||fonts[0]}"`;}ctx.fillText(l.text||'',0,0);}
   else if(l.fileId&&artwork.has(l.fileId)){const img=await image(artwork.get(l.fileId)!);if(mode==='SINGLE'){const mask=document.createElement('canvas');mask.width=Math.max(1,Math.round(w));mask.height=Math.max(1,Math.round(h));const mc=mask.getContext('2d')!;mc.drawImage(img,0,0,mask.width,mask.height);mc.globalCompositeOperation='source-in';mc.fillStyle=vinylColor;mc.fillRect(0,0,mask.width,mask.height);ctx.drawImage(mask,-w/2,-h/2,w,h);}else ctx.drawImage(img,-w/2,-h/2,w,h);}
   if(boundaries&&selected?.area===name&&selected.index===i){ctx.strokeStyle='#f36d21';ctx.lineWidth=2;ctx.strokeRect(-w/2,-h/2,w,h);}ctx.restore();
  }ctx.restore();if(boundaries){ctx.save();ctx.strokeStyle='#777';ctx.setLineDash([6,5]);ctx.strokeRect(bx,by,bw,bh);ctx.font='14px "Deckers Sans"';ctx.fillStyle='#444';ctx.fillText(areaLabels[name],bx,by-8);ctx.restore();}
 }
}
