export function isPlaqueItem(item:{name?:string;designer?:string}):boolean;
export function serviceItemGallery(item:{id:number;name:string;designer?:string;image?:boolean;photoCount?:number;variants?:{id:number;name:string;image?:boolean;photoCount?:number}[]},storeId:number):{key:string;src:string;label:string;variantId:number|null}[];
export function variantDimensions(variants:{color?:string;size?:string}[]):{colors:string[];sizes:string[];color:boolean;size:boolean;named:boolean};
export function selectVariantOption<T extends {color?:string;size?:string}>(variants:T[],current:T|undefined,field:'color'|'size',value:string):T|null;
