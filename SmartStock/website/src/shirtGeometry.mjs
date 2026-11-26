// Coordinates are relative to the print area; aspect is its rendered height / width.
export function layerCorners(layer,aspect=1){
 const angle=layer.rotation*Math.PI/180,c=Math.cos(angle),s=Math.sin(angle);
 return [[-1,-1],[1,-1],[1,1],[-1,1]].map(([sx,sy])=>({
  x:layer.x+layer.w/2+c*sx*layer.w/2-s*sy*layer.h*aspect/2,
  y:layer.y+layer.h/2+(s*sx*layer.w/2+c*sy*layer.h*aspect/2)/aspect
 }));
}
export function resizeLayer(layer,corner,point,aspect=1,maximum={w:1,h:1}){
 const anchor=layerCorners(layer,aspect)[(corner+2)%4];
 const [sx,sy]=[[-1,-1],[1,-1],[1,1],[-1,1]][corner];
 const angle=layer.rotation*Math.PI/180,c=Math.cos(angle),s=Math.sin(angle);
 const dx=point.x-anchor.x,dy=(point.y-anchor.y)*aspect;
 const w=Math.max(.01,Math.min(maximum.w,sx*(c*dx+s*dy)));
 const h=Math.max(.01,Math.min(maximum.h,sy*(-s*dx+c*dy)/aspect));
 function sized(t){
  const width=layer.w+(w-layer.w)*t,height=layer.h+(h-layer.h)*t;
  return {...layer,w:width,h:height,x:anchor.x+(c*sx*width-s*sy*height*aspect)/2-width/2,
   y:anchor.y+(s*sx*width+c*sy*height*aspect)/(2*aspect)-height/2};
 }
 const fits=l=>layerCorners(l,aspect).every(p=>p.x>=-1e-8&&p.y>=-1e-8&&p.x<=1+1e-8&&p.y<=1+1e-8);
 if(fits(sized(1)))return sized(1);
 let low=0,high=1;
 for(let i=0;i<32;i++){const mid=(low+high)/2;if(fits(sized(mid)))low=mid;else high=mid;}
 return sized(low);
}
