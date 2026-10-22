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
  const collarMaterial=json.materials.push({name:'Polo fabric',doubleSided:true,normalTexture:json.materials[0].normalTexture,pbrMetallicRoughness:{baseColorFactor:[1,1,1,1],metallicFactor:0,roughnessFactor:.85}})-1;
  const buttonMaterial=json.materials.push({name:'Buttons',pbrMetallicRoughness:{baseColorFactor:[.8,.79,.73,1],metallicFactor:0,roughnessFactor:.55}})-1;
  // Back collar band and two broad front leaves with pointed tips.
  const pos=[],uvs=[],idx=[],steps=120,rows=12;
  for(let i=0;i<=steps;i++)for(let j=0;j<=rows;j++){
   const t=Math.PI+Math.PI*i/steps,v=j/rows,front=Math.max(0,Math.sin(t));
   const x=(.071+v*.034)*Math.cos(t),y=.25-.026*front-v*(.012+.065*front)+.012*Math.sin(Math.PI*v),z=(.061+v*.03)*Math.sin(t);
   pos.push(x,y,front>.1?Math.max(z,surface(x,y)+.004):z);uvs.push(i/steps,j/rows);
  }
  for(let i=0;i<steps;i++)for(let j=0;j<rows;j++){const a=i*(rows+1)+j,b=a+rows+1;idx.push(a,b,a+1,b,b+1,a+1);}
  const collar=new THREE.BufferGeometry();collar.setAttribute('position',new THREE.Float32BufferAttribute(pos,3));collar.setAttribute('uv',new THREE.Float32BufferAttribute(uvs,2));collar.setIndex(idx);addMesh('Folded polo collar',collar,collarMaterial);
  for(const sign of [-1,1]){
   const flap=new THREE.PlaneGeometry(1,1,24,24),fp=flap.getAttribute('position');
   for(let i=0;i<fp.count;i++){
    const u=fp.getX(i)+.5,v=.5-fp.getY(i);
    const x=sign*THREE.MathUtils.lerp(THREE.MathUtils.lerp(.024,.075,u),THREE.MathUtils.lerp(.031,.089,u),v);
    const y=THREE.MathUtils.lerp(THREE.MathUtils.lerp(.212,.23,u),THREE.MathUtils.lerp(.185,.16,u),v);
    const z=Math.max(THREE.MathUtils.lerp(.075,.11,v),surface(x,y)+.006)+.009*Math.sin(Math.PI*v);
    fp.setXYZ(i,x,y,z);
   }
   addMesh('Pointed collar leaf',flap,collarMaterial);
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
