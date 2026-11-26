/** Front outlines traced from the catalog photographs; depth and placement are preview estimates. */
export const plaqueShapes={
 diamond:{path:'M .5 0 Q .54 0 .58 .05 L .96 .46 Q 1 .5 .96 .55 L .56 .97 Q .5 1 .44 .97 L .04 .55 Q 0 .5 .04 .46 L .44 .05 Q .47 0 .5 0 Z',base:'wood'},
 star:{path:'M .48 0 L .65 .19 L 1 .18 L .87 .97 L .4 1 L .15 .48 L .25 .3 L 0 .19 L .35 .19 Z',base:'black'},
 flame:{path:'M .6 .14 C .98 .36 1 .72 .68 1 L .28 1 L 0 .46 Z',ornament:'M .61 0 L .69 .12 L .98 .12 L .76 .23 L .86 .43 L .57 .29 L .33 .36 L .4 .19 L .18 .12 L .5 .12 Z',base:'glass',inlay:'M .5 .26 C .88 .48 .82 .76 .28 .9 L .02 .46 Z',color:'#638ab8'},
 pentagon:{path:'M .32 0 L .96 .43 L 1 .68 L .75 1 L .23 1 L 0 .69 Z',base:'glass'},
 heart:{path:'M .5 .12 C .23 -.12 0 .02 0 .35 C 0 .64 .28 .86 .5 1 C .72 .86 1 .64 1 .35 C 1 .02 .77 -.12 .5 .12 Z',base:'glass'},
 pointed:{path:'M 0 .01 Q .54 -.02 1 .3 L .83 1 L .28 1 Z',base:'glass'},
 curved:{path:'M 0 .1 Q .13 -.08 .4 .07 L 1 .32 L .78 1 L .27 1 Z',base:'glass'},
 rounded:{path:'M .5 0 Q .54 0 .59 .03 L .95 .23 Q 1 .27 1 .33 Q .98 .76 .78 .98 Q .76 1 .71 1 L .29 1 Q .24 1 .22 .98 Q .02 .76 0 .33 Q 0 .27 .05 .23 L .41 .03 Q .46 0 .5 0 Z',base:'stand'},
 shield:{path:'M .5 0 L .82 .14 Q .84 .28 1 .3 Q .97 .67 .85 1 L .15 1 Q .03 .67 0 .3 Q .16 .28 .18 .14 Z',base:'stand'}
};
export function plaqueShape(name){const value=name.toLowerCase();if(value.includes('blue flame'))return 'flame';if(value.includes('diamond'))return 'diamond';if(value.includes('star'))return 'star';if(value.includes('heart'))return 'heart';if(value.includes('irregular pentagon'))return 'pentagon';if(value.includes('rounded pentagon'))return 'rounded';if(value.includes('pointed top'))return 'pointed';if(value.includes('curved top'))return 'curved';if(value.includes('shield'))return 'shield';return null;}
export function templateInches(variant){const factor={IN:1,INCH:1,INCHES:1,MM:1/25.4,CM:1/2.54}[String(variant.templateUnit||'').toUpperCase()];const w=Number(variant.templateWidth),h=Number(variant.templateHeight);return factor&&Number.isFinite(w)&&Number.isFinite(h)&&w>0&&h>0&&w*factor<=100&&h*factor<=100?{width:w*factor,height:h*factor}:null;}
export function physicalSize(size){const match=/^\s*(\d+(?:\.\d+)?)\s*[x×]\s*(\d+(?:\.\d+)?)\s*$/.exec(size||'');return match&&Number(match[1])>0&&Number(match[2])>0?{width:Number(match[1]),height:Number(match[2])}:null;}
/** Keep rotated artwork wholly inside the actual rectangular template dimensions. */
export function fitPlaqueLayer(layer,area){const w=Math.max(.05,Math.min(area.width,Number(layer.w)||1)),h=Math.max(.05,Math.min(area.height,Number(layer.h)||1));const rotation=Math.max(-180,Math.min(180,Number(layer.rotation)||0)),r=rotation*Math.PI/180;const bw=Math.abs(w*Math.cos(r))+Math.abs(h*Math.sin(r)),bh=Math.abs(w*Math.sin(r))+Math.abs(h*Math.cos(r));const scale=Math.min(1,area.width/bw,area.height/bh),rw=bw*scale/2,rh=bh*scale/2;return {...layer,w:w*scale,h:h*scale,rotation,x:Math.max(rw,Math.min(area.width-rw,Number(layer.x)||area.width/2)),y:Math.max(rh,Math.min(area.height-rh,Number(layer.y)||area.height/2))};}
