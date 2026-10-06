import {enclosedMeshVolume} from './meshVolume.mjs';

export function inspectStl(buffer, {preview = false} = {}) {
  const bytes = new Uint8Array(buffer);
  if (bytes.length < 15) throw new Error('This STL file is too small to inspect.');
  const view = new DataView(buffer);
  const binary = bytes.length >= 84 && 84 + view.getUint32(80, true) * 50 === bytes.length;
  let triangles = 0;
  const sample = [];
  const solidFaces = [];
  const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  const vertex = (x, y, z) => {
    for (const [axis, value] of [x, y, z].entries()) {
      if (!Number.isFinite(value)) throw new Error('The STL contains invalid coordinates.');
      min[axis] = Math.min(min[axis], value);
      max[axis] = Math.max(max[axis], value);
    }
  };
  if (binary) {
    triangles = view.getUint32(80, true);
    const stride = Math.max(1, Math.ceil(triangles / 1200));
    for (let i = 0; i < triangles; i++) {
      const offset = 84 + i * 50;
      const face = [];
      for (let j = 0; j < 3; j++) {
        const point = [view.getFloat32(offset + 12 + j * 12, true), view.getFloat32(offset + 16 + j * 12, true), view.getFloat32(offset + 20 + j * 12, true)];
        vertex(...point);
        if (preview && i % stride === 0) face.push(point);
      }
      if (face.length) sample.push(face);
      if(preview&&triangles<=25000)solidFaces.push([0,1,2].map(j=>[view.getFloat32(offset+12+j*12,true),view.getFloat32(offset+16+j*12,true),view.getFloat32(offset+20+j*12,true)]));
    }
  } else {
    const source = new TextDecoder('utf-8', {fatal: true}).decode(bytes);
    if (!/^\s*solid\b/i.test(source) || !/\bendsolid\b/i.test(source)) throw new Error('This STL file could not be inspected.');
    const values = [...source.matchAll(/^\s*vertex\s+([+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?)\s+([+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?)\s+([+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?)\s*$/gim)];
    if (!values.length || values.length % 3 || (source.match(/\bvertex\b/gi)||[]).length !== values.length) throw new Error('This STL has incomplete triangle data.');
    triangles = values.length / 3;
    const stride = Math.max(1, Math.ceil(triangles / 1200));
    for (let i = 0; i < values.length; i++) {
      const match = values[i], point = [Number(match[1]), Number(match[2]), Number(match[3])];
      vertex(...point);
      if (preview && Math.floor(i / 3) % stride === 0) {
        if (i % 3 === 0) sample.push([]);
        sample[sample.length - 1].push(point);
      }
      if(preview&&triangles<=25000){
        if(i%3===0)solidFaces.push([]);
        solidFaces[solidFaces.length-1].push(point);
      }
    }
  }
  if (!triangles) throw new Error('This STL has no triangles.');
  const result = {triangles, dimensions:max.map((value, axis) => value - min[axis])};
  if (preview) return {...result, center:max.map((value, axis) => (value + min[axis]) / 2), preview:sample, volume:enclosedMeshVolume(solidFaces)};
  return result;
}
