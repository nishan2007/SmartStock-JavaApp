const millimeters={MM:1,CM:10,IN:25.4};
export function physicalModelSize(dimensions,volume,units){
  if(!Object.hasOwn(millimeters,units))return null;
  const factor=millimeters[units];
  const dimensionsMm=dimensions.map(value=>value*factor);
  if(dimensionsMm.some(value=>!Number.isFinite(value)))return null;
  const volumeCm3=volume==null?null:volume*factor**3/1000;
  return {
    dimensionsMm,
    volumeCm3:Number.isFinite(volumeCm3)?volumeCm3:null
  };
}
