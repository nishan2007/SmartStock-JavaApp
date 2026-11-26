// Build-time SVG rasterisation only; sharp is not an application dependency.
const sharp=require('sharp');
const path=require('node:path');
const fs=require('node:fs/promises');
(async()=>{
 const output=path.resolve(process.argv[2]||path.join(__dirname,'../src/main/resources/gy/deckers/studio/icons'));
 const source=await fs.readFile(path.join(output,'SmartStudio.svg'));
 if(/<image\b|data:image/i.test(source.toString()))throw Error('The approved icon must remain vector-only.');
 for(const size of [16,24,32,48,64,128,256,512,1024]){
  await sharp(source).resize(size,size).png().toFile(path.join(output,`SmartStudio-${size}.png`));
 }
 for(const name of ['SmartStudioIconLight.png','SmartStudioIconDark.png'])await fs.copyFile(path.join(output,'SmartStudio-1024.png'),path.join(output,name));
 console.log('Rendered the approved Creative Brush SVG at 16–1024 px.');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
