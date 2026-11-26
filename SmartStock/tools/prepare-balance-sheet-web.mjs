// Stage only this app's allowlisted public assets, never store data or credentials.
import {mkdir,copyFile} from 'node:fs/promises';
const source=new URL('../src/employee-web/',import.meta.url),target=new URL('../target/balance-sheet-web-assets/',import.meta.url);
await mkdir(new URL('icons/',target),{recursive:true});
for(const file of ['balance-sheet.html','balance-sheet.js','balance-sheet.css','balance-sheet.webmanifest',
  'icons/balance-sheet.svg','icons/balance-sheet-180.png','icons/balance-sheet-192.png','icons/balance-sheet-512.png'])
  await copyFile(new URL(file,source),new URL(file,target));
console.log('Balance Sheet assets ready in SmartStock/target/balance-sheet-web-assets');
