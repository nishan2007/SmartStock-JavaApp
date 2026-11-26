import React,{useEffect,useState} from 'react';

export function ServiceProductImage({item,storeId}:{item:{id:number;name:string;image?:boolean};storeId:number}){
  const [failed,setFailed]=useState(false);
  useEffect(()=>setFailed(false),[item.id,item.image,storeId]);
  if(!item.image||failed)return null;
  return <div className="service-product-photo"><img loading="lazy" src={`/shop/image?storeId=${storeId}&kind=custom-item&id=${item.id}`} alt={item.name} onError={()=>setFailed(true)}/></div>;
}
