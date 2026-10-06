import React,{useEffect,useState} from 'react';
import {ImageOff} from 'lucide-react';
import type {Product} from './ProductPage';

export function ProductImage({product,storeId,large=false}:{product:Product;storeId:number;large?:boolean}){
  const [failed,setFailed]=useState(false);
  useEffect(()=>setFailed(false),[product.id,product.image,storeId]);
  if(!product.image||failed)return <span className="product-image-fallback" aria-label="Product photo unavailable"><ImageOff aria-hidden="true"/><span>Photo coming soon</span></span>;
  return <img loading={large?'eager':'lazy'} src={`/shop/image?storeId=${storeId}&id=${product.id}`} alt={product.name} onError={()=>setFailed(true)}/>;
}
