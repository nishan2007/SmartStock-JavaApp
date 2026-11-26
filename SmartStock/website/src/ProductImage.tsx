import React,{useEffect,useState} from 'react';
import {ImageOff} from 'lucide-react';
import type {Product} from './ProductPage';

export function isOutOfStock(product:Product){
  return product.availability==='Out of Stock';
}

export function ProductAvailability({product}:{product:Product}){
  return isOutOfStock(product)
    ? <span className="out-of-stock-label">Out of stock</span>
    : product.availability;
}

export function ProductImage({product,storeId,large=false,showStockWatermark=false}:{product:Product;storeId:number;large?:boolean;showStockWatermark?:boolean}){
  const [failed,setFailed]=useState(false);
  useEffect(()=>setFailed(false),[product.id,product.image,storeId]);
  const image=!product.image||failed ? <span className="product-image-fallback" aria-label="Product photo unavailable"><ImageOff aria-hidden="true"/><span>Photo coming soon</span></span>
    : <img loading={large?'eager':'lazy'} src={`/shop/image?storeId=${storeId}&id=${product.id}`} alt={product.name} onError={()=>setFailed(true)}/>;
  return <>{image}{showStockWatermark&&isOutOfStock(product)&&
    <span className="out-of-stock-watermark" aria-hidden="true">Out of stock</span>}</>;
}
