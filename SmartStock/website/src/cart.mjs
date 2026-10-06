export function loadCart(storage) {
  try {const data=JSON.parse(storage.getItem('deckers.bag')||'{}');return Object.fromEntries(Object.entries(data).filter(([id,n])=>/^\d+$/.test(id)&&Number.isInteger(n)&&n>0&&n<=999));}catch{return {};}
}
export function changeQuantity(cart,id,quantity) {
  const next={...cart};if(quantity<=0)delete next[id];else next[id]=Math.min(999,Math.trunc(quantity));return next;
}
