export function presetArc(preset){return /\barch(?:ed)?\b/i.test(preset?.name||'')?120:0;}
/** Tangent-aligned letters along a circular baseline, bounded for box resizing. */
export function arcLayout(advances,height,degrees){
 const total=advances.reduce((sum,n)=>sum+n,0),angle=Math.abs(degrees)*Math.PI/180;
 if(!total||!angle)return null;
 const radius=total/angle,sign=Math.sign(degrees),glyphs=[];let cursor=0;
 let minX=Infinity,maxX=-Infinity,minY=Infinity,maxY=-Infinity;
 for(const width of advances){
  const theta=(cursor+width/2-total/2)/radius,rotation=sign*theta;
  const x=radius*Math.sin(theta),y=sign*radius*(1-Math.cos(theta));
  glyphs.push({x,y,rotation});cursor+=width;
  const c=Math.cos(rotation),s=Math.sin(rotation);
  for(const dx of [-width/2,width/2])for(const dy of [-height/2,height/2]){
   const px=x+c*dx-s*dy,py=y+s*dx+c*dy;
   minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
  }
 }
 return {glyphs,width:maxX-minX,height:maxY-minY,centerX:(minX+maxX)/2,centerY:(minY+maxY)/2};
}

export function presetSize(preset,type){
 const match=(preset?.name||'').match(/(\d+(?:\.\d+)?)\s*(?:in(?:ches)?|["”])?\s*[x×]\s*(\d+(?:\.\d+)?)/i);
 return {widthIn:preset?.widthIn|| (type==='IMAGE'?4:match?Number(match[2]):0),heightIn:preset?.heightIn||(type==='IMAGE'?4:match?Number(match[1]):0)};
}
