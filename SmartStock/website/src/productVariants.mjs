export function publishedVariants(product,products){
  if(!product.groupId)return [];
  return products.filter(item=>item.groupId===product.groupId)
    .sort((a,b)=>variantLabel(a).localeCompare(variantLabel(b))||a.id-b.id);
}

export function variantLabel(product){
  const options=product.variantOptions||{};
  const names=(product.optionNames||[]).filter(name=>typeof name==='string'&&name.trim());
  const values=names.map(name=>options[name]).filter(value=>typeof value==='string'&&value.trim());
  if(values.length)return values.join(' · ');
  return [product.size,product.color].filter(Boolean).join(' · ')||product.name;
}
