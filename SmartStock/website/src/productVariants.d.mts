export type VariantProduct={id:number;name:string;size?:string;color?:string;groupId?:string|null;optionNames?:string[];variantOptions?:Record<string,string>};
export declare function publishedVariants<T extends VariantProduct>(product:T,products:T[]):T[];
export declare function variantLabel(product:VariantProduct):string;
