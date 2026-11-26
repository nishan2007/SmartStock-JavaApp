export function isPlaqueItem(item){return item.designer==='PLAQUE'||/plaque/i.test(item.name||'');}
export function serviceItemGallery(item,storeId){
  const photos=[];
  const add=(row,variantId)=>{
    const count=Number.isInteger(row.photoCount)?Math.max(0,row.photoCount):row.image?1:0;
    for(let index=0;index<count;index++){
      const legacyPlaque=variantId&&isPlaqueItem(item)&&row.photoCount===undefined;
      const src=`/shop/image?storeId=${storeId}&kind=${legacyPlaque?'plaque':'custom-item'}&id=${item.id}${variantId?`&variantId=${variantId}`:''}${index?`&photoIndex=${index}`:''}`;
      photos.push({key:`${variantId||'main'}:${index}`,src,label:`${row.name}${index?` · photo ${index+1}`:''}`,variantId:variantId||null});
    }
  };
  add(item,null);for(const variant of item.variants||[])add(variant,variant.id);
  return photos;
}
export function variantDimensions(variants){
  const colors=[...new Set(variants.map(v=>(v.color||'').trim()).filter(Boolean))];
  const sizes=[...new Set(variants.map(v=>(v.size||'').trim()).filter(Boolean))];
  const identities=variants.map(v=>`${(v.color||'').trim()}\u0000${(v.size||'').trim()}`);
  return {colors,sizes,color:colors.length>1,size:sizes.length>1,named:colors.length<=1&&sizes.length<=1||new Set(identities).size!==variants.length||variants.some(v=>!v.color&&!v.size)};
}
export function selectVariantOption(variants,current,field,value){
  const other=field==='color'?'size':'color';
  const matches=variants.filter(v=>(v[field]||'').trim()===value);
  return matches.find(v=>current&&(v[other]||'').trim()===(current[other]||'').trim())||matches[0]||null;
}
