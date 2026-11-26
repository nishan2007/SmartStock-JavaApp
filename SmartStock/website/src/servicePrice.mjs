/** Missing or variable prices require a quote; zero is a valid fixed price. */
export function servicePriceLabel(item,currency='GYD'){
  if(typeof item.price!=='number'||!Number.isFinite(item.price)||item.price<0)return 'Ask for a quote';
  const price=new Intl.NumberFormat('en-GY',{style:'currency',currency,maximumFractionDigits:0}).format(item.price);
  return item.priceFrom?`From ${price}`:price;
}
