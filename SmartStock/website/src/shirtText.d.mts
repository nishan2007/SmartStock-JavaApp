export function presetArc(preset?:{name:string}):number;
export function arcLayout(advances:number[],height:number,degrees:number):null|{glyphs:{x:number;y:number;rotation:number}[];width:number;height:number;centerX:number;centerY:number};

export function presetSize(preset:undefined|{name:string;widthIn?:number;heightIn?:number},type:string):{widthIn:number;heightIn:number};
