import {enclosedMeshVolume} from './meshVolume.mjs';

export function inspectObj(buffer, {preview = false} = {}) {
  const source = new TextDecoder('utf-8', {fatal: true}).decode(buffer);
  const vertices = [], faces = [];
  const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  for (const raw of source.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const fields = line.split(/\s+/);
    if (fields[0] === 'v') {
      if (fields.length < 4) throw new Error('This OBJ has an incomplete vertex.');
      const point = fields.slice(1, 4).map(Number);
      if (point.some(value => !Number.isFinite(value))) throw new Error('This OBJ contains invalid coordinates.');
      if (vertices.length >= 300000) throw new Error('This OBJ has too many vertices to preview.');
      point.forEach((value, axis) => {min[axis] = Math.min(min[axis], value); max[axis] = Math.max(max[axis], value);});
      vertices.push(point);
    } else if (fields[0] === 'f') {
      const refs = fields.slice(1);
      if (refs.length < 3 || refs.length > 128) throw new Error('This OBJ has an invalid face.');
      const indices = refs.map(ref => {
        const token = ref.split('/')[0];
        if (!/^-?[1-9]\d*$/.test(token)) throw new Error('This OBJ has an invalid face reference.');
        const index = Number(token);
        const resolved = index < 0 ? vertices.length + index : index - 1;
        if (!Number.isSafeInteger(resolved) || resolved < 0 || resolved >= vertices.length) throw new Error('This OBJ refers to a missing vertex.');
        return resolved;
      });
      if (faces.length + indices.length - 2 > 300000) throw new Error('This OBJ has too many faces to preview.');
      for (let i = 1; i < indices.length - 1; i++) faces.push([indices[0], indices[i], indices[i + 1]]);
    }
  }
  if (!vertices.length || !faces.length) throw new Error('This OBJ has no printable mesh faces.');
  const result = {triangles:faces.length, dimensions:max.map((value, axis) => value - min[axis])};
  if (!preview) return result;
  const stride = Math.max(1, Math.ceil(faces.length / 1200));
  return {...result, center:max.map((value, axis) => (value + min[axis]) / 2),
    preview:faces.filter((_, index) => index % stride === 0).map(face => face.map(index => vertices[index])),
    volume:faces.length<=25000?enclosedMeshVolume(faces.map(face=>face.map(index=>vertices[index]))):null};
}
