import type {Layer} from './shirtDesign';
export function layerCorners(layer:Layer,aspect?:number):{x:number;y:number}[];
export function resizeLayer(layer:Layer,corner:number,point:{x:number;y:number},aspect?:number,maximum?:{w:number;h:number}):Layer;
