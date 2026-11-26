import {isPlaqueItem} from './serviceItemGallery.mjs';

/** Group by the saved main-item classification, retaining catalogue order within each type. */
export function groupServiceItems(items) {
  const groups=new Map();
  for(const item of items){
    const name=(item.itemType||'').trim();
    const key=name.toLocaleLowerCase('en');
    if(!groups.has(key))groups.set(key,{key,name:name||'Other items',items:[]});
    groups.get(key).items.push(item);
  }
  return [...groups.values()].sort((a,b)=>!a.key?1:!b.key?-1:a.name.localeCompare(b.name,'en',{sensitivity:'base',numeric:true}));
}

/** Pair adjacent compact sections without changing their reading order. */
export function layoutServiceGroups(groups){
  const result=[];
  const compact=group=>group.items.length<=3&&!group.items.some(item=>isPlaqueItem(item)&&item.variants?.length);
  for(let i=0;i<groups.length;i++){
    const first=groups[i],second=groups[i+1];
    if(second&&compact(first)&&compact(second)&&first.items.length+second.items.length<=4){
      const total=first.items.length+second.items.length;
      const span=12*first.items.length/total;
      result.push({...first,span},{...second,span:12-span});i++;
    }else result.push({...first,span:12});
  }
  return result;
}
