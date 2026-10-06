import type {Project} from './Made';
export function latest3DPrints(projects:Project[]):Project[];
export function capabilityProject(projects:Project[],serviceSlug:string|undefined):Project|null;
export function projectForServices(projects:Project[],serviceSlugs:string[]):Project|null;
export function relatedProjects(project:Project,projects:Project[]):Project[];
export function alternativeMethods(project:Project,projects:Project[]):Project[];
export function projectsForProduct(product:{name:string;category:string},projects:Project[]):Project[];
