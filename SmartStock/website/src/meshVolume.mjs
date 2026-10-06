// A geometric estimate is useful only for a small, closed, consistently wound mesh.
export function enclosedMeshVolume(faces){
  if(!faces.length||faces.length>25000)return null;
  const parent=faces.map((_,i)=>i),edges=new Map();
  const root=i=>{while(parent[i]!==i){parent[i]=parent[parent[i]];i=parent[i];}return i;};
  const join=(a,b)=>{parent[root(a)]=root(b);};
  for(let i=0;i<faces.length;i++){
    const face=faces[i];if(face.length!==3)return null;
    const points=face.map(point=>point.join(','));
    if(new Set(points).size!==3)return null;
    for(let side=0;side<3;side++){
      const from=points[side],to=points[(side+1)%3],key=from<to?`${from}|${to}`:`${to}|${from}`;
      const previous=edges.get(key);
      if(!previous)edges.set(key,{face:i,from,to,count:1});
      else if(previous.count===1&&previous.from===to&&previous.to===from){previous.count=2;join(i,previous.face);}
      else return null;
    }
  }
  if([...edges.values()].some(edge=>edge.count!==2))return null;
  const anchors=new Map(),volumes=new Map();
  for(let i=0;i<faces.length;i++){
    const component=root(i),face=faces[i];
    if(!anchors.has(component))anchors.set(component,face[0]);
    const origin=anchors.get(component),[a,b,c]=face.map(point=>point.map((value,axis)=>value-origin[axis]));
    const signed=(a[0]*(b[1]*c[2]-b[2]*c[1])-a[1]*(b[0]*c[2]-b[2]*c[0])+a[2]*(b[0]*c[1]-b[1]*c[0]))/6;
    volumes.set(component,(volumes.get(component)||0)+signed);
  }
  const result=[...volumes.values()].reduce((total,value)=>total+Math.abs(value),0);
  return Number.isFinite(result)&&result>0?result:null;
}
