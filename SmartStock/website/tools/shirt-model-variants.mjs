// Rebuild derivatives from the licensed, Blender-authored crew mesh.
import fs from 'node:fs';
import path from 'node:path';
import * as THREE from 'three';
const directory=path.resolve(import.meta.dirname,'../public/assets/shirts');
const original=fs.readFileSync(path.join(directory,'crew.glb'));
function variant(style){
 const length=original.readUInt32LE(12),json=JSON.parse(original.subarray(20,20+length)),binary=Buffer.from(original.subarray(28+length));
 const chunks=[binary];let offset=binary.length;
 function accessor(index){const a=json.accessors[index],view=json.bufferViews[a.bufferView];return {a,data:new (a.componentType===5123?Uint16Array:Float32Array)(binary.buffer,binary.byteOffset+(view.byteOffset||0)+(a.byteOffset||0),a.count*({VEC3:3,VEC2:2,SCALAR:1}[a.type]))};}
 const positions=accessor(0),normals=accessor(1),uv=accessor(2),indices=accessor(3);
 const smooth=(a,b,t)=>{const x=THREE.MathUtils.clamp((t-a)/(b-a),0,1);return x*x*(3-2*x);};
 for(let i=0;i<positions.a.count;i++){
  const x=positions.data[i*3],y=positions.data[i*3+1],z=positions.data[i*3+2];
  const influence=Math.max(0,1-Math.abs(x)/(style==='polo'?.035:.075))*smooth(.09,.20,y)*smooth(-.015,.035,z);
  positions.data[i*3+1]-=(style==='polo'?.045:.075)*influence;
 }
 const body=new THREE.BufferGeometry();body.setAttribute('position',new THREE.BufferAttribute(positions.data,3));body.setAttribute('uv',new THREE.BufferAttribute(uv.data,2));body.setIndex(new THREE.BufferAttribute(indices.data,1));body.computeVertexNormals();normals.data.set(body.getAttribute('normal').array);
 body.computeBoundingBox();positions.a.min=body.boundingBox.min.toArray();positions.a.max=body.boundingBox.max.toArray();
 const bodyMesh=new THREE.Mesh(body,new THREE.MeshBasicMaterial({side:THREE.DoubleSide}));bodyMesh.updateMatrixWorld();
 const ray=new THREE.Raycaster();
 function surface(x,y){ray.set(new THREE.Vector3(x,y,1),new THREE.Vector3(0,0,-1));return ray.intersectObject(bodyMesh)[0]?.point.z??.07;}
 function append(array,type,componentType=5126){
  const bytes=Buffer.from(array.buffer,array.byteOffset,array.byteLength),pad=(4-offset%4)%4;if(pad){chunks.push(Buffer.alloc(pad));offset+=pad;}
  const view=json.bufferViews.push({buffer:0,byteOffset:offset,byteLength:bytes.length})-1;chunks.push(bytes);offset+=bytes.length;
  return json.accessors.push({bufferView:view,componentType,count:array.length/({VEC3:3,VEC2:2,SCALAR:1}[type]),type})-1;
 }
 function addMesh(name,geometry,material){
  geometry.computeVertexNormals();geometry.computeBoundingBox();
  const position=append(geometry.getAttribute('position').array,'VEC3');json.accessors[position].min=geometry.boundingBox.min.toArray();json.accessors[position].max=geometry.boundingBox.max.toArray();
  const attributes={POSITION:position,NORMAL:append(geometry.getAttribute('normal').array,'VEC3')};
  if(geometry.getAttribute('uv'))attributes.TEXCOORD_0=append(geometry.getAttribute('uv').array,'VEC2');
  const primitive={attributes,material};if(geometry.index)primitive.indices=append(new Uint32Array(geometry.index.array),'SCALAR',5125);
  const mesh=json.meshes.push({name,primitives:[primitive]})-1,node=json.nodes.push({mesh,name})-1;json.scenes[0].nodes.push(node);
 }
 if(style==='polo'){
  const collarMaterial=json.materials.push({name:'Polo collar',doubleSided:true,normalTexture:json.materials[0].normalTexture,pbrMetallicRoughness:{baseColorFactor:[1,1,1,1],metallicFactor:0,roughnessFactor:.85}})-1;
  const buttonMaterial=json.materials.push({name:'Buttons',pbrMetallicRoughness:{baseColorFactor:[.8,.79,.73,1],metallicFactor:0,roughnessFactor:.55}})-1;
  // Trace the actual neckline, rather than suspending flat leaves in front of it.
  const welded=[],vertexIds=[],unique=new Map(),edges=new Map();
  for(let i=0;i<positions.a.count;i++){
   const point=new THREE.Vector3().fromArray(positions.data,i*3),key=point.toArray().map(n=>Math.round(n*1e5)).join(',');
   if(!unique.has(key)){unique.set(key,welded.length);welded.push(point);}vertexIds.push(unique.get(key));
  }
  for(let i=0;i<indices.data.length;i+=3)for(let k=0;k<3;k++){
   const key=[vertexIds[indices.data[i+k]],vertexIds[indices.data[i+(k+1)%3]]].sort((a,b)=>a-b).join(',');edges.set(key,(edges.get(key)||0)+1);
  }
  const rimIds=new Set();for(const [key,count] of edges)if(count===1)for(const id of key.split(',').map(Number)){
   const p=welded[id];if(p.y>.15&&Math.abs(p.x)<.09&&Math.abs(p.z)<.08)rimIds.add(id);
  }
  const rim=[...rimIds].map(id=>welded[id]).sort((a,b)=>Math.atan2(a.z,a.x)-Math.atan2(b.z,b.x));
  if(rim.length<50)throw new Error('Polo neckline boundary is incomplete');
  const pos=[],uvs=[],idx=[],rows=8;
  for(let i=0;i<=rim.length;i++){
   const inner=rim[i%rim.length],front=THREE.MathUtils.smoothstep(inner.z,-.005,.055);
   // The front opening splits the collar into two tips, stitched to the neck.
   const opening=front*THREE.MathUtils.smoothstep(.027-Math.abs(inner.x),0,.019);
   const spread=.032+front*.018-opening*.022;
   const radius=Math.hypot(inner.x,inner.z);
   let outerX=inner.x*(1+spread/radius),outerY=inner.y-(.022+front*.045),outerZ=inner.z*(1+spread/radius);
   ray.set(new THREE.Vector3(outerX,1,outerZ),new THREE.Vector3(0,-1,0));
   const shoulder=ray.intersectObject(bodyMesh)[0];
   if(shoulder&&front<.5)outerY=THREE.MathUtils.lerp(Math.min(inner.y-.008,shoulder.point.y+.006),outerY,front*2);
   const facing=THREE.MathUtils.smoothstep(inner.z,.012,.05);
   if(facing>0){
    const chest=surface(outerX,outerY);
    outerZ=THREE.MathUtils.lerp(outerZ,chest+.007,facing);
   }
   for(let j=0;j<=rows;j++){
    const v=j/rows,fold=.25,t=v<=fold?v/fold:(v-fold)/(1-fold);
    const foldX=inner.x*1.04,foldY=inner.y+.008,foldZ=inner.z*1.04;
    const x=v<=fold?THREE.MathUtils.lerp(inner.x,foldX,t):THREE.MathUtils.lerp(foldX,outerX,t);
    const y=v<=fold?THREE.MathUtils.lerp(inner.y+.001,foldY,t):THREE.MathUtils.lerp(foldY,outerY,t)+.004*Math.sin(Math.PI*t);
    const z=v<=fold?THREE.MathUtils.lerp(inner.z,foldZ,t):THREE.MathUtils.lerp(foldZ,outerZ,t);
    pos.push(x,y,z);uvs.push(i/rim.length,v);
   }
  }
  for(let i=0;i<rim.length;i++)for(let j=0;j<rows;j++){if(rim[i].z>-.005&&j>=2)continue;const a=i*(rows+1)+j,b=a+rows+1;idx.push(a,b,a+1,b,b+1,a+1);}
  const collar=new THREE.BufferGeometry();collar.setAttribute('position',new THREE.Float32BufferAttribute(pos,3));collar.setAttribute('uv',new THREE.Float32BufferAttribute(uvs,2));collar.setIndex(idx);addMesh('Stitched folded polo collar',collar,collarMaterial);
  for(const sign of [-1,1]){
   const edge=rim.filter(p=>p.x*sign>.018&&p.z>-.012).sort((a,b)=>Math.atan2(a.z,Math.abs(a.x))-Math.atan2(b.z,Math.abs(b.x)));
   const vertices=[],tex=[],faces=[],columns=32,depth=12;
   for(let i=0;i<=columns;i++){
    const u=i/columns,index=u*(edge.length-1),a=edge[Math.floor(index)],b=edge[Math.min(edge.length-1,Math.floor(index)+1)],neck=a.clone().lerp(b,index-Math.floor(index));
    const fold=new THREE.Vector3(neck.x*1.04,neck.y+.008,neck.z*1.04);
    const t=u<.7?u/.7:(u-.7)/.3;
    const x=sign*(u<.7?THREE.MathUtils.lerp(.105,.045,t):THREE.MathUtils.lerp(.045,.022,t));
    const y=u<.7?THREE.MathUtils.lerp(.224,.153,t):THREE.MathUtils.lerp(.153,.182,t);
    const target=new THREE.Vector3(x,y,surface(x,y)+.004);
    for(let j=0;j<=depth;j++){
     const v=j/depth,point=fold.clone().lerp(target,v);
     // Keep the face just above the garment without the old floating planes.
     ray.set(new THREE.Vector3(point.x,point.y,1),new THREE.Vector3(0,0,-1));const hit=ray.intersectObject(bodyMesh)[0];if(v>.15&&hit&&hit.point.z>0)point.z=Math.max(point.z,hit.point.z+.003);
     point.z+=.003*Math.sin(Math.PI*v);vertices.push(...point.toArray());tex.push(u,v);
    }
   }
   for(let i=0;i<columns;i++)for(let j=0;j<depth;j++){const a=i*(depth+1)+j,b=a+depth+1;faces.push(a,b,a+1,b,b+1,a+1);}
   const leaf=new THREE.BufferGeometry();leaf.setAttribute('position',new THREE.Float32BufferAttribute(vertices,3));leaf.setAttribute('uv',new THREE.Float32BufferAttribute(tex,2));leaf.setIndex(faces);addMesh('Attached folded collar point',leaf,collarMaterial);
  }
  const patch=new THREE.PlaneGeometry(.021,.09,4,32),p=patch.getAttribute('position');
  for(let i=0;i<p.count;i++){const x=p.getX(i),y=p.getY(i)+.134;p.setXYZ(i,x,y,surface(x,y)+.0015);}addMesh('Button placket',patch,collarMaterial);
  for(const y of [.105,.133,.159]){const button=new THREE.SphereGeometry(.003,12,8);button.scale(1,1,.4);button.translate(0,y,surface(0,y)+.0035);addMesh('Button',button,buttonMaterial);}
 }
 json.asset.copyright='Poimandres / Anderson Mancini; MIT; derivative neckline/collar by Deckers';
 json.asset.extras={style,source:'https://github.com/pmndrs/examples/tree/main/examples/t-shirt-configurator',license:'MIT'};
 const bin=Buffer.concat(chunks),binPadding=(4-bin.length%4)%4;json.buffers[0].byteLength=bin.length+binPadding;
 const encoded=Buffer.from(JSON.stringify(json)),padding=(4-encoded.length%4)%4,j=Buffer.concat([encoded,Buffer.alloc(padding,32)]),header=Buffer.alloc(20),binHeader=Buffer.alloc(8);
 header.writeUInt32LE(0x46546c67,0);header.writeUInt32LE(2,4);header.writeUInt32LE(28+j.length+bin.length+binPadding,8);header.writeUInt32LE(j.length,12);header.writeUInt32LE(0x4e4f534a,16);binHeader.writeUInt32LE(bin.length+binPadding);binHeader.writeUInt32LE(0x004e4942,4);
 const out=Buffer.concat([header,j,binHeader,bin,Buffer.alloc(binPadding)]);fs.writeFileSync(path.join(directory,style+'.glb'),out);console.log(`${style}: ${out.length} bytes`);
}
variant('vneck');variant('polo');
