export type PlaqueShape={path:string;base:string;color?:string;ornament?:string;inlay?:string};
export const plaqueShapes:Record<string,PlaqueShape>;
export function plaqueShape(name:string):string|null;
export function templateInches(variant:{templateWidth?:number;templateHeight?:number;templateUnit?:string}):{width:number;height:number}|null;
export function physicalSize(size:string):{width:number;height:number}|null;
export function fitPlaqueLayer<T extends {w:number;h:number;x:number;y:number;rotation:number}>(layer:T,area:{width:number;height:number}):T;
