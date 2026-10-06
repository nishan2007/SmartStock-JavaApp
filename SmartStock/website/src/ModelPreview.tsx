import React,{useEffect,useRef,useState} from 'react';

export type ModelMesh={center:number[];dimensions:number[];preview:number[][][];volume?:number|null};

export function ModelPreview({mesh}:{mesh:ModelMesh}){
  const canvas=useRef<HTMLCanvasElement>(null),drag=useRef<{x:number;y:number}|null>(null);
  const [yaw,setYaw]=useState(.7),[pitch,setPitch]=useState(.35);
  useEffect(()=>{
    const element=canvas.current;if(!element)return;
    const width=element.clientWidth||480,height=element.clientHeight||300,dpr=Math.min(window.devicePixelRatio||1,2);
    element.width=Math.round(width*dpr);element.height=Math.round(height*dpr);
    const ctx=element.getContext('2d');if(!ctx)return;
    ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,width,height);
    const scale=Math.min(width,height)*.68/Math.max(...mesh.dimensions,1e-9);
    const cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
    const transform=(point:number[])=>{
      const x=point[0]-mesh.center[0],y=point[1]-mesh.center[1],z=point[2]-mesh.center[2];
      const rx=x*cy-z*sy,rz=x*sy+z*cy,ry=y*cp-rz*sp,depth=y*sp+rz*cp;
      return {x:width/2+rx*scale,y:height/2-ry*scale,z:depth};
    };
    const faces=mesh.preview.map(face=>face.map(transform)).filter(face=>face.length===3).map(face=>{
      const [a,b,c]=face,normal=(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
      return {face,depth:(a.z+b.z+c.z)/3,shade:Math.min(.9,Math.max(.25,Math.abs(normal)/(scale*scale*2)))};
    }).sort((a,b)=>a.depth-b.depth);
    for(const {face,shade} of faces){
      ctx.beginPath();ctx.moveTo(face[0].x,face[0].y);ctx.lineTo(face[1].x,face[1].y);ctx.lineTo(face[2].x,face[2].y);ctx.closePath();
      ctx.fillStyle=`rgba(140, 100, 190, ${.35+shade*.55})`;ctx.fill();ctx.strokeStyle='rgba(230, 216, 250, .35)';ctx.lineWidth=.6;ctx.stroke();
    }
  },[mesh,yaw,pitch]);
  return <div className="model-preview"><div className="model-preview-top"><strong>Your model</strong><span>Drag to rotate</span></div><canvas ref={canvas} role="img" aria-label="Rotatable preview of your uploaded 3D model" onPointerDown={e=>{drag.current={x:e.clientX,y:e.clientY};e.currentTarget.setPointerCapture(e.pointerId);}} onPointerMove={e=>{if(!drag.current)return;setYaw(old=>old+(e.clientX-drag.current!.x)*.01);setPitch(old=>Math.max(-1.4,Math.min(1.4,old+(e.clientY-drag.current!.y)*.01)));drag.current={x:e.clientX,y:e.clientY};}} onPointerUp={()=>{drag.current=null;}} onPointerCancel={()=>{drag.current=null;}}/><div className="model-preview-controls"><button type="button" onClick={()=>setYaw(old=>old-.4)} aria-label="Rotate model left">↶</button><button type="button" onClick={()=>setYaw(old=>old+.4)} aria-label="Rotate model right">↷</button><small>Preview only · confirm scale and printability with the store</small></div></div>;
}
