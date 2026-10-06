export function reorderBag(lines, products, existing = {}) {
  const byId = new Map(products.map(product => [product.id, product]));
  const bag = {...existing};
  const unavailable = [];
  let added = 0;
  for (const line of lines) {
    const product = byId.get(line.id);
    if (!product?.canOrder || !Number.isInteger(line.quantity) || line.quantity < 1) {
      unavailable.push(line.name);
      continue;
    }
    const current = Number.isInteger(bag[line.id]) ? bag[line.id] : 0;
    const quantity = Math.min(999, current + line.quantity);
    added += quantity - current;
    bag[line.id] = quantity;
  }
  return {bag, added, unavailable};
}
