export function inspectStl(buffer: ArrayBuffer): {triangles:number;dimensions:number[]};
export function inspectStl(buffer: ArrayBuffer, options: {preview:true}): {triangles:number;dimensions:number[];center:number[];preview:number[][][];volume:number|null};
