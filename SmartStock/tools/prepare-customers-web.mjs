import {mkdir,copyFile} from 'node:fs/promises';
const source=new URL('../src/mobile-web/',import.meta.url),target=new URL('../target/customers-web-assets/',import.meta.url);
await mkdir(target,{recursive:true});
for(const file of ['customers.html','customers.js','customers.css','minutes.html','minutes.js','minutes.css','minutes-icons.ttf','minutes-icons-LICENSE.txt','minutes-logo.png'])await copyFile(new URL(file,source),new URL(file,target));
console.log('Customer Management assets prepared.');
