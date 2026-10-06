export type GuideQuestion={key:string;label:string;hint:string};
export function questionsFor(topic:string,project?:{category?:string;productionMethod?:string;tags?:string[]}|null):GuideQuestion[];
export function descriptionWithAnswers(description:string,answers:Record<string,string>,questions:GuideQuestion[]):string;
export function repeatDraft(description:string,questions:GuideQuestion[]):{description:string;answers:Record<string,string>;modelUnits:string};
