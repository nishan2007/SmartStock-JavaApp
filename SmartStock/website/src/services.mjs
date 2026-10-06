export const services = [
  {slug:'3d-printing',name:'3D Printing',headline:'Your idea, made real.',description:'Upload a model or describe your idea. Our team will review the details and follow up with a quote.',examples:'3D printed objects, prototypes, and personal pieces.'},
  {slug:'custom-printing',name:'Custom Printing',headline:'Make it yours.',description:'Tell us what you need printed and how you want it to look. We will help choose a suitable process and prepare a quote.',examples:'Print projects for work, celebrations, and everyday life.'},
  {slug:'custom-apparel',name:'Custom Apparel',headline:'Wear your idea.',description:'Bring your artwork, logo, or concept to a wearable project. Share the garment, quantity, sizes, and finish you have in mind.',examples:'Custom shirts, teamwear, and uniforms.'},
  {slug:'embroidery',name:'Embroidery',headline:'A detail that lasts.',description:'Tell us about the garment, artwork, placement, and quantity. Our team will review the stitching requirements before quoting.',examples:'Polos, hats, uniforms, and branded apparel.'},
  {slug:'signs-banners',name:'Signs & Banners',headline:'Get seen.',description:'Share where your sign or banner will be used, its size, quantity, and artwork. We will recommend a suitable production approach.',examples:'Event banners, business signs, and displays.'},
  {slug:'business-printing',name:'Business Printing',headline:'Put your brand to work.',description:'Tell us about the printed materials your business needs. Our team will review format, quantity, artwork, and timing.',examples:'Business cards, forms, stationery, and branded print.'},
  {slug:'personalized-gifts',name:'Personalized Gifts',headline:'Made for someone.',description:'Share the occasion and the personal details you would like to add. We will help turn your idea into a thoughtful project.',examples:'Names, messages, photos, and custom keepsakes.'},
  {slug:'business-cards',name:'Business Cards',headline:'Make a strong first impression.',description:'Tell us about your business cards, including size, quantity, artwork, and finish. Our team will review the details and prepare a quote.',examples:'Cards for new businesses, teams, and events.'},
  {slug:'banner-printing',name:'Banner Printing',headline:'Make your message unmissable.',description:'Share the banner size, where it will be displayed, quantity, and artwork. We will review production options and prepare a quote.',examples:'Banners for openings, events, promotions, and celebrations.'},
  {slug:'custom-t-shirts',name:'Custom T-Shirts',headline:'Your design, out in the world.',description:'Tell us about the shirts, sizes, colors, quantity, and artwork you have in mind. Our team will review a suitable production method.',examples:'Shirts for teams, businesses, events, and personal projects.'},
  {slug:'laser-engraving',name:'Laser Engraving',headline:'Make the details personal.',description:'Describe the item, material, artwork, quantity, and placement you have in mind. Our team will review whether it is suitable for engraving.',examples:'Names, logos, messages, and personalized details.'},
  {slug:'stationery',name:'Stationery',headline:'The little details carry your brand.',description:'Tell us which stationery pieces you need, how many, and what artwork you have. We will review the format and prepare a quote.',examples:'Letterheads, envelopes, forms, and everyday business materials.'},
  {slug:'large-format-printing',name:'Large Format Printing',headline:'Think bigger.',description:'Share the dimensions, display setting, artwork, and quantity for your large format project. Our team will review suitable materials and production options.',examples:'Posters, displays, and oversized printed pieces.'},
  {slug:'signs',name:'Signs',headline:'Point the way. Stand out.',description:'Tell us where the sign will be used, its dimensions, artwork, and quantity. We will review suitable materials and prepare a quote.',examples:'Business signs, event signage, and displays.'}
];
export function serviceFromPath(pathname) {
  const match=/^\/shop\/services\/([a-z0-9-]+)\/?$/.exec(pathname);
  return services.find(service=>service.slug===match?.[1])||null;
}
export function serviceForTopic(topic) { return services.find(service=>service.name===topic)||null; }
export function availableServices(unavailable=[]){
  const hidden=new Set(unavailable);
  return services.filter(service=>!hidden.has(service.slug));
}
export function serviceAvailable(topic,unavailable=[]){
  const service=serviceForTopic(topic);
  return !service||!unavailable.includes(service.slug);
}
