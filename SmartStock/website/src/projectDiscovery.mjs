const words = value => String(value || '').toLowerCase().trim();
const terms = value => new Set((Array.isArray(value) ? value : [value]).flatMap(item => words(item).split(/[^a-z0-9]+/)).filter(Boolean));
const overlap = (a, b) => [...terms(a)].filter(term => terms(b).has(term)).length;
const recent = (a, b) => Date.parse(b.updatedAt || '') - Date.parse(a.updatedAt || '') || a.title.localeCompare(b.title);

export function latest3DPrints(projects) {
  return projects.filter(project => /\b3d\b|additive|filament|resin/i.test([project.category, project.productionMethod, ...(project.tags || [])].join(' '))).sort(recent).slice(0, 3);
}

export function capabilityProject(projects, serviceSlug) {
  return projectForServices(projects, serviceSlug ? [serviceSlug] : []);
}

export function projectForServices(projects, serviceSlugs) {
  if (!serviceSlugs.length) return null;
  return projects.filter(project => project.cover && serviceSlugs.includes(project.serviceSlug))
    .sort((a, b) => Number(b.featured) - Number(a.featured) || recent(a, b))[0] || null;
}

export function relatedProjects(project, projects) {
  return projects.filter(candidate => candidate.id !== project.id).map(candidate => ({
    candidate,
    score: (words(candidate.category) === words(project.category) ? 5 : 0)
      + (words(candidate.productionMethod) === words(project.productionMethod) && candidate.productionMethod ? 3 : 0)
      + overlap(candidate.tags, project.tags) * 2 + overlap(candidate.materials, project.materials)
  })).filter(entry => entry.score > 0).sort((a, b) => b.score - a.score || recent(a.candidate, b.candidate)).slice(0, 3).map(entry => entry.candidate);
}

export function alternativeMethods(project, projects) {
  const seen = new Set();
  return projects.filter(candidate => candidate.id !== project.id && words(candidate.category) === words(project.category)
    && candidate.productionMethod && words(candidate.productionMethod) !== words(project.productionMethod))
    .sort(recent).filter(candidate => {
      const method = words(candidate.productionMethod);
      if (seen.has(method)) return false;
      seen.add(method);
      return true;
    }).slice(0, 3);
}

export function projectsForProduct(product, projects) {
  const meaningful = value => [...terms(value)].filter(term => term.length >= 3 && !['the','and','for','custom','premium','deckers','product','products'].includes(term));
  const nameTerms = meaningful(product.name);
  const category = words(product.category);
  return projects.map(project => {
    const title = new Set(meaningful(project.title));
    const tags = new Set(meaningful(project.tags));
    const score = (category && category === words(project.category) ? 5 : 0)
      + nameTerms.filter(term => title.has(term)).length * 4
      + nameTerms.filter(term => tags.has(term)).length * 3
      + meaningful(product.category).filter(term => tags.has(term)).length * 2;
    return {project,score};
  }).filter(entry => entry.score >= 3).sort((a,b) => b.score - a.score || recent(a.project,b.project)).slice(0,3).map(entry => entry.project);
}
