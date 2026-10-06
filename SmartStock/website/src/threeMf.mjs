import {unzipSync,strFromU8} from 'fflate';
import {enclosedMeshVolume} from './meshVolume.mjs';

const CORE='http://schemas.microsoft.com/3dmanufacturing/core/2015/02';
const UNIT_MM={micron:.001,millimeter:1,centimeter:10,inch:25.4,foot:304.8,meter:1000};
const children=(node,name)=>Array.from(node.childNodes).filter(child=>child.nodeType===1&&child.namespaceURI===CORE&&child.localName===name);
const required=(node,name)=>{const value=node?.getAttribute(name);if(!value)throw new Error(`This 3MF has no ${name}.`);return value;};
const number=value=>{const parsed=Number(value);if(!Number.isFinite(parsed))throw new Error('This 3MF has invalid coordinates.');return parsed;};
const externalPath=node=>Array.from(node.attributes||[]).some(attribute=>attribute.localName==='path');
const parse=bytes=>{
  const xml=strFromU8(bytes);
  if(/<!\s*(DOCTYPE|ENTITY)/i.test(xml))throw new Error('This 3MF has unsupported XML declarations.');
  const doc=new DOMParser().parseFromString(xml,'application/xml');
  if(doc.getElementsByTagName('parsererror').length||doc.documentElement?.localName!=='model'||doc.documentElement.namespaceURI!==CORE)
    throw new Error('This 3MF model could not be read.');
  return doc.documentElement;
};
const transform=(point,text)=>{
  if(!text)return point;
  const values=text.trim().split(/\s+/).map(number);
  if(values.length!==12)throw new Error('This 3MF has an invalid build transform.');
  return [0,1,2].map(axis=>point[0]*values[axis]+point[1]*values[3+axis]+point[2]*values[6+axis]+values[9+axis]);
};

export function inspect3mf(buffer){
  const bytes=new Uint8Array(buffer);
  if(bytes.length>4_194_304||bytes.length<4||bytes[0]!==80||bytes[1]!==75)throw new Error('Choose a valid 3MF file of 4 MB or less.');
  let files;
  let expanded=0;
  try{files=unzipSync(bytes,{filter:file=>{
    const included=file.name==='_rels/.rels'||/\.model$/i.test(file.name);
    if(included){expanded+=file.originalSize;if(expanded>8_388_608)throw new Error('This 3MF model is too large to preview.');}
    return included;
  }});}catch(error){throw new Error(error instanceof Error?error.message:'This 3MF archive could not be read.');}
  const relations=files['_rels/.rels'];
  if(!relations)throw new Error('This 3MF has no model relationship.');
  const relXml=strFromU8(relations);
  if(/<!\s*(DOCTYPE|ENTITY)/i.test(relXml))throw new Error('This 3MF has unsupported XML declarations.');
  const relDoc=new DOMParser().parseFromString(relXml,'application/xml');
  if(relDoc.getElementsByTagName('parsererror').length)throw new Error('This 3MF relationship could not be read.');
  const relation=Array.from(relDoc.getElementsByTagName('*')).find(node=>node.localName==='Relationship'&&/\/3dmodel$/.test(node.getAttribute('Type')||''));
  const target=relation?.getAttribute('Target')?.replace(/^\//,'');
  if(!target||target.includes('..')||!files[target])throw new Error('This 3MF has no supported root model.');
  const root=parse(files[target]);
  if(root.getAttribute('requiredextensions'))return {supported:false,reason:'This 3MF needs a format extension. The store will inspect the uploaded file.'};
  const unit=root.getAttribute('unit')||'millimeter',unitMm=UNIT_MM[unit];
  if(!unitMm)throw new Error('This 3MF uses unsupported model units.');
  const resources=children(root,'resources')[0],build=children(root,'build')[0];
  if(!resources||!build)throw new Error('This 3MF is missing its mesh or build.');
  const objects=new Map();
  for(const object of children(resources,'object')){
    const id=required(object,'id');
    if(objects.has(id))throw new Error('This 3MF repeats an object identifier.');
    objects.set(id,object);
  }
  const faces=[];let assembly=false,externalPart=false;
  const expand=(id,transforms,path)=>{
    if(path.has(id)||path.size>=16)throw new Error('This 3MF has a cyclic or deeply nested assembly.');
    const object=objects.get(id);
    if(!object)throw new Error('This 3MF refers to a missing object.');
    const next=new Set(path);next.add(id);
    const components=children(object,'components')[0];
    if(components){
      assembly=true;
      const parts=children(components,'component');
      if(!parts.length)throw new Error('This 3MF has an empty assembly.');
      for(const part of parts){
        if(externalPath(part)){externalPart=true;return;}
        expand(required(part,'objectid'),[part.getAttribute('transform'),...transforms],next);
      }
      return;
    }
    const mesh=children(object,'mesh')[0],verticesNode=mesh&&children(mesh,'vertices')[0],trianglesNode=mesh&&children(mesh,'triangles')[0];
    if(!verticesNode||!trianglesNode)throw new Error('This 3MF has no printable mesh.');
    const vertices=children(verticesNode,'vertex').map(vertex=>['x','y','z'].map(axis=>number(required(vertex,axis))));
    const triangles=children(trianglesNode,'triangle');
    if(vertices.length>100000||faces.length+triangles.length>100000)throw new Error('This 3MF has too many faces to preview.');
    for(const triangle of triangles){
      const points=['v1','v2','v3'].map(key=>{
        const raw=required(triangle,key),index=Number(raw);
        if(!/^\d+$/.test(raw)||!Number.isSafeInteger(index)||index>=vertices.length)throw new Error('This 3MF has an invalid triangle.');
        return transforms.reduce((point,matrix)=>transform(point,matrix),vertices[index]).map(value=>value*unitMm);
      });
      faces.push(points);
    }
  };
  const items=children(build,'item');
  for(const item of items){
    if(externalPath(item))return {supported:false,reason:'This 3MF references another model part. The store will inspect the uploaded file.'};
    expand(required(item,'objectid'),[item.getAttribute('transform')],new Set());
  }
  if(externalPart)return {supported:false,reason:'This 3MF references another model part. The store will inspect the uploaded file.'};
  if(!faces.length)throw new Error('This 3MF has no printable mesh.');
  const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];
  for(const face of faces)for(const point of face)for(let axis=0;axis<3;axis++){min[axis]=Math.min(min[axis],point[axis]);max[axis]=Math.max(max[axis],point[axis]);}
  const stride=Math.max(1,Math.ceil(faces.length/1200));
  return {supported:true,unit:'millimeters',triangles:faces.length,dimensions:max.map((value,i)=>value-min[i]),
    center:max.map((value,i)=>(value+min[i])/2),preview:faces.filter((_,i)=>i%stride===0),volume:assembly||items.length>1?null:enclosedMeshVolume(faces)};
}
