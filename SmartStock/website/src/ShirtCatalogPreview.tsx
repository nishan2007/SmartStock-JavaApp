import {useEffect,useRef,useState} from 'react';
import {renderShirt,newDesign,type Shirt} from './shirtDesign';

/** Use the designer renderer so catalogue previews keep the garment shading. */
export function ShirtCatalogPreview({shirt}:{shirt:Shirt}){
 const canvas=useRef<HTMLCanvasElement>(null),[failed,setFailed]=useState(false);
 const colorName=shirt.variants[0]?.color||'';
 const swatch=shirt.config.colors[colorName];
 const namedColors:Record<string,string>={'armygreen':'#4b5320','olivegreen':'#808000','lightgray':'#d3d3d3','lightgrey':'#d3d3d3','darkgray':'#505050','darkgrey':'#505050','burgundy':'#800020','cream':'#fffdd0','cyanblue':'#00b7eb','darkblue':'#00008b','darkgreen':'#006400','darknavyblue':'#000033','lavenderblue':'#ccccff','lightpink':'#ffb6c1','lightbrown':'#b5651d','limegreen':'#32cd32','mintgreen':'#98ff98','mustard':'#d4a017','peach':'#ffdab9','rosepink':'#ff66cc','tealblue':'#367588','tealgreen':'#008080','turquoise':'#40e0d0'};
 const color=swatch&&swatch.toLowerCase()!=='#d1d5db'?swatch:namedColors[colorName.toLowerCase().replace(/[\s-]/g,'')]||swatch||'#d1d5db';
 useEffect(()=>{
  let cancelled=false;setFailed(false);
  const target=document.createElement('canvas');target.width=target.height=400;
  renderShirt(target,shirt,{...newDesign(shirt),areas:{}},'FRONT',color,new Map()).then(()=>{
   if(!cancelled&&canvas.current)canvas.current.getContext('2d')?.drawImage(target,0,0);
  }).catch(()=>{if(!cancelled)setFailed(true);});
  return()=>{cancelled=true;};
 },[shirt,color]);
 return <div className="shirt-catalog-preview">{failed?<p>Preview unavailable</p>:<canvas ref={canvas} width={400} height={400} role="img" aria-label={`${shirt.name}${colorName?` in ${colorName}`:''}`}/>}</div>;
}
