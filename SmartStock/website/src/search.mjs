export const serviceTopics = [
  {name:'3D Printing',terms:'3d model stl obj prototype printing'},
  {name:'Business Cards',terms:'business cards calling cards'},
  {name:'Banner Printing',terms:'banner printing event birthday promotion'},
  {name:'Custom T-Shirts',terms:'custom shirts tshirts t-shirts team apparel'},
  {name:'Large Format Printing',terms:'large format posters display oversized'},
  {name:'Stationery',terms:'letterhead envelopes forms office stationery'},
  {name:'Signs',terms:'business signs signage displays'},
  {name:'Custom Apparel',terms:'custom shirts tshirts t-shirts uniforms clothing'},
  {name:'Embroidery',terms:'embroidered polos hats uniforms stitching'},
  {name:'Signs & Banners',terms:'signs banners window graphics large format'},
  {name:'Business Printing',terms:'business cards invoices receipt books stationery'},
  {name:'Stickers & Labels',terms:'stickers labels packaging'},
  {name:'Personalized Gifts',terms:'custom gifts presents engraving'},
  {name:'Photo Printing',terms:'photos prints pictures'},
  {name:'Laser Engraving',terms:'laser engraving personalized'},
  {name:'Events',terms:'birthday wedding school event decorations'}
];
export function searchCatalog(query, products, services=serviceTopics, projects=[]){
  const words=query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  if(!words.length)return {products:[],services:[],categories:[],projects:[]};
  const matches=value=>words.every(word=>String(value||'').toLocaleLowerCase().includes(word));
  const productResults=products.filter(p=>matches([p.name,p.sku,p.description,p.category,p.size,p.color].join(' '))).slice(0,8);
  const serviceResults=services.filter(s=>matches(s.name+' '+s.terms)).slice(0,6);
  const categories=[...new Set(products.map(p=>p.category).filter(Boolean))].filter(matches).slice(0,6);
  const projectResults=projects.filter(p=>matches([p.title,p.summary,p.category,p.materials,p.productionMethod,p.customization,...(p.tags||[])].join(' '))).slice(0,6);
  return {products:productResults,services:serviceResults,categories,projects:projectResults};
}
