export function loadCart(storage:Pick<Storage,'getItem'>):Record<string,number>;
export function changeQuantity(cart:Record<string,number>,id:number,quantity:number):Record<string,number>;
