export type SearchProduct={name:string;sku?:string;description?:string;category:string;size?:string;color?:string};
export type SearchService={name:string;terms:string};
export type SearchProject={title:string;summary?:string;category?:string;materials?:string;productionMethod?:string;customization?:string;tags?:string[]};
export const serviceTopics:SearchService[];
export function searchCatalog<T extends SearchProduct,P extends SearchProject>(query:string,products:T[],services?:SearchService[],projects?:P[]):{products:T[];services:SearchService[];categories:string[];projects:P[]};
