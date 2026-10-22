import {useEffect,useRef,useState} from 'react';
import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {DecalGeometry} from 'three/addons/geometries/DecalGeometry.js';
import {RoomEnvironment} from 'three/addons/environments/RoomEnvironment.js';
import {renderShirt,type Shirt,type ShirtDesign} from './shirtDesign';

function modelPath(shirt:Shirt){return `/shop/assets/shirts/${shirt.config.shirtType==='COLLARED'?'polo':shirt.config.neckline==='V'?'vneck':'crew'}.glb`;}
// Cache source bytes only: every viewer owns its geometry and textures.
const sources=new Map<string,Promise<ArrayBuffer>>();
function source(path:string){let request=sources.get(path);if(!request){request=fetch(path).then(r=>{if(!r.ok)throw new Error('Garment model unavailable');return r.arrayBuffer();}).catch(e=>{sources.delete(path);throw e;});sources.set(path,request);}return request;}
function dispose(root:THREE.Object3D){
 const geometries=new Set<THREE.BufferGeometry>(),materials=new Set<THREE.Material>(),textures=new Set<THREE.Texture>();
 root.traverse(node=>{if(node instanceof THREE.Mesh){geometries.add(node.geometry);for(const material of Array.isArray(node.material)?node.material:[node.material])materials.add(material);}});
 for(const material of materials)for(const value of Object.values(material))if(value instanceof THREE.Texture)textures.add(value);
 geometries.forEach(g=>g.dispose());materials.forEach(m=>m.dispose());textures.forEach(t=>{t.dispose();if(typeof ImageBitmap!=='undefined'&&t.image instanceof ImageBitmap)t.image.close();});
}

export default function ShirtPreview3D({shirt,design,color,artwork,side,onEdit}:{shirt:Shirt;design:ShirtDesign;color:string;artwork:Map<string,string>;side:'FRONT'|'BACK';onEdit:()=>void}){
 const host=useRef<HTMLDivElement>(null),view=useRef<{camera:THREE.PerspectiveCamera;controls:OrbitControls;render:()=>void}|null>(null);
 const latest=useRef({design,color,artwork});latest.current={design,color,artwork};const repaint=useRef<(()=>Promise<void>)|null>(null);
 const [failed,setFailed]=useState(false),[loading,setLoading]=useState(true);
 useEffect(()=>{
  const node=host.current!;if(!node)return;let renderer:THREE.WebGLRenderer;
  setFailed(false);setLoading(true);node.dataset.model=modelPath(shirt);delete node.dataset.ready;
  try{renderer=new THREE.WebGLRenderer({antialias:true,alpha:true});}catch{setFailed(true);setLoading(false);return;}
  let disposed=false;const scene=new THREE.Scene();
  renderer.setPixelRatio(Math.min(window.devicePixelRatio,2));renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.NeutralToneMapping;renderer.toneMappingExposure=1;
  renderer.domElement.setAttribute('aria-label','Rotatable 3D shirt preview');node.appendChild(renderer.domElement);
  const camera=new THREE.PerspectiveCamera(34,1,.1,30);camera.position.set(0,0,4.2);
  const controls=new OrbitControls(camera,renderer.domElement);controls.enablePan=false;controls.minDistance=2.6;controls.maxDistance=6;controls.minPolarAngle=Math.PI*.18;controls.maxPolarAngle=Math.PI*.82;
  const pmrem=new THREE.PMREMGenerator(renderer),room=new RoomEnvironment(),environment=pmrem.fromScene(room,.04);scene.environment=environment.texture;scene.environmentIntensity=.4;room.dispose();pmrem.dispose();
  scene.add(new THREE.HemisphereLight(0xffffff,0x767b85,.7));const key=new THREE.DirectionalLight(0xfff6e9,1.7);key.position.set(-3,4,5);scene.add(key);
  const fill=new THREE.DirectionalLight(0xe5efff,.7);fill.position.set(3,1,-4);scene.add(fill);
  const render=()=>{if(!disposed)renderer.render(scene,camera);};controls.addEventListener('change',render);view.current={camera,controls,render};
  const resize=new ResizeObserver(()=>{const w=node.clientWidth;if(w){renderer.setSize(w,w,false);camera.aspect=1;camera.updateProjectionMatrix();render();}});resize.observe(node);
  async function load(){try{
   const bytes=await source(modelPath(shirt));if(disposed)return;
   const gltf=await new GLTFLoader().parseAsync(bytes.slice(0),'');const garment=gltf.scene;
   if(disposed){dispose(garment);return;}
   const box=new THREE.Box3().setFromObject(garment),center=box.getCenter(new THREE.Vector3()),scale=2/box.getSize(new THREE.Vector3()).y;
   garment.scale.setScalar(scale);garment.position.copy(center).multiplyScalar(-scale);garment.updateMatrixWorld(true);
   let body:THREE.Mesh|undefined;const dry=shirt.config.fabric==='DRY_FIT';
   garment.traverse(object=>{if(object instanceof THREE.Mesh){
    if(object.name==='T_Shirt_male')body=object;
    for(const material of Array.isArray(object.material)?object.material:[object.material])if(material instanceof THREE.MeshStandardMaterial&&material.name!=='Buttons'){
     material.color.set(color);material.roughness=dry?.66:.9;material.metalness=0;material.normalScale.setScalar(dry?.48:.85);material.aoMapIntensity=.7;
     if(material.normalMap)material.normalMap.anisotropy=Math.min(8,renderer.capabilities.getMaxAnisotropy());
    }
   }});
   scene.add(garment);if(!body)throw new Error('Garment print surface missing');
   const printSurface=body;let revision=0,decals=new THREE.Group();scene.add(decals);
   const paint=async()=>{delete node.dataset.ready;const ticket=++revision,{design,color,artwork}=latest.current;
    garment.traverse(object=>{if(object instanceof THREE.Mesh)for(const m of Array.isArray(object.material)?object.material:[object.material])if(m instanceof THREE.MeshStandardMaterial&&m.name!=='Buttons')m.color.set(color);});
    const next=new THREE.Group();
   await Promise.all(([false,true] as const).map(async rear=>{
    const canvas=document.createElement('canvas');canvas.width=canvas.height=1024;
    await renderShirt(canvas,shirt,design,rear?'BACK':'FRONT',color,artwork,false,undefined,'ARTWORK');
    if(disposed||ticket!==revision)return;
    const texture=new THREE.CanvasTexture(canvas);texture.colorSpace=THREE.SRGBColorSpace;texture.anisotropy=renderer.capabilities.getMaxAnisotropy();
    // Projection follows folds, retaining the editor's normalized print layout.
    const geometry=new DecalGeometry(printSurface,new THREE.Vector3(0,0,rear?-.41:.41),new THREE.Euler(0,rear?Math.PI:0,0),new THREE.Vector3(2.9,2.9,.65));
    const material=new THREE.MeshStandardMaterial({map:texture,transparent:true,depthWrite:false,polygonOffset:true,polygonOffsetFactor:-4,roughness:dry?.67:.9,side:THREE.FrontSide});
    const decal=new THREE.Mesh(geometry,material);decal.renderOrder=1;next.add(decal);
   }));
   if(disposed||ticket!==revision){dispose(next);return;}
   scene.remove(decals);dispose(decals);decals=next;scene.add(decals);render();node.dataset.ready='true';setLoading(false);
   };repaint.current=async()=>{try{await paint();}catch{if(!disposed){setFailed(true);setLoading(false);}}};await repaint.current();
  }catch{if(!disposed){setFailed(true);setLoading(false);}}}void load();
  const lost=(event:Event)=>{event.preventDefault();setFailed(true);setLoading(false);};renderer.domElement.addEventListener('webglcontextlost',lost);
  return()=>{disposed=true;view.current=null;repaint.current=null;resize.disconnect();controls.dispose();renderer.domElement.removeEventListener('webglcontextlost',lost);dispose(scene);environment.dispose();renderer.dispose();renderer.domElement.remove();};
 },[shirt]);
 useEffect(()=>{void repaint.current?.();},[design,color,artwork,shirt]);
 function face(rear:boolean){const v=view.current;if(!v)return;v.camera.position.set(0,0,rear?-4.2:4.2);v.controls.target.set(0,0,0);v.controls.update();v.render();}
 useEffect(()=>face(side==='BACK'),[side,shirt]);
 return <div className="shirt-3d"><div ref={host} className="shirt-3d-stage" hidden={failed}/>{loading&&<p role="status">Updating 3D preview…</p>}{failed?<p role="status">3D preview is unavailable on this device. <button type="button" onClick={onEdit}>Use artwork editor</button></p>:<><div className="shirt-tabs"><button type="button" onClick={()=>face(false)}>Front view</button><button type="button" onClick={()=>face(true)}>Back view</button></div><p>Drag to rotate · Pinch or scroll to zoom. Use Edit artwork to position your design.</p></>}</div>;
}
