// Prepare only the Inventory app and its bundled barcode decoder for publication.
import {mkdir,copyFile} from 'node:fs/promises';
const source=new URL('../src/mobile-web/',import.meta.url),target=new URL('../target/inventory-web-assets/',import.meta.url);
await mkdir(target,{recursive:true});
for(const file of ['inventory.html','inventory.js','inventory.css','inventory.webmanifest','inventory-icon.svg',
 'inventory-icon-180.png','inventory-icon-192.png','inventory-icon-512.png','orders-barcode.js','orders-barcode-LICENSE.txt'])
 await copyFile(new URL(file,source),new URL(file,target));
console.log('Inventory assets ready in SmartStock/target/inventory-web-assets');
