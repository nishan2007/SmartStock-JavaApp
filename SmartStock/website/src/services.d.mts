export type Service={slug:string;name:string;headline:string;description:string;examples:string};
export const services:Service[];
export function serviceFromPath(pathname:string):Service|null;
export function serviceForTopic(topic:string):Service|null;
export function availableServices(unavailable?:string[]):Service[];
export function serviceAvailable(topic:string,unavailable?:string[]):boolean;
