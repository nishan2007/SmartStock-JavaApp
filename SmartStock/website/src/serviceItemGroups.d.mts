export function groupServiceItems<T extends {itemType?:string}>(items:T[]):{key:string;name:string;items:T[]}[];
export function layoutServiceGroups<T extends {items:{variants?:unknown[]}[]}>(groups:T[]):(T&{span:number})[];
