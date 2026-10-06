const question=(key,label,hint)=>({key,label,hint});
export function questionsFor(topic,project){
  const source=[topic,project?.category,project?.productionMethod,...(project?.tags||[])].filter(Boolean).join(' ').toLowerCase();
  if(/embroider|stitched/.test(source))return [
    question('garment','Garment or item','For example: polo, cap, or jacket'),
    question('sizes','Sizes and quantities','For example: 5 medium and 5 large'),
    question('placement','Embroidery placement','For example: left chest or front of cap')
  ];
  if(/apparel|shirt|uniform|dtf|screen print|vinyl/.test(source))return [
    question('garment','Garment type','For example: crew-neck T-shirt or polo'),
    question('sizes','Sizes and quantities','For example: 5 medium and 5 large'),
    question('placement','Design placement','For example: front, back, or sleeve')
  ];
  if(/3d|additive|filament|resin/.test(source))return [
    question('text','Names or text to include','Optional'),
    question('scale','Measurement units and intended scale','For example: 150 mm wide'),
    question('use','How will you use it?','For example: display, prototype, or functional part')
  ];
  if(/business card/.test(source))return [
    question('sides','One-sided or two-sided?','For example: two-sided'),
    question('finish','Preferred finish','For example: matte or glossy'),
    question('artwork','Artwork readiness','For example: print-ready file or design help needed')
  ];
  if(/sign|banner|large format/.test(source))return [
    question('location','Where will it be displayed?','For example: indoors or outdoors'),
    question('mounting','How should it be installed?','For example: wall mounted or hanging'),
    question('artwork','Artwork readiness','For example: print-ready file or design help needed')
  ];
  return [];
}
export function descriptionWithAnswers(description,answers,questions){
  const details=questions.map(q=>[q.label,String(answers[q.key]||'').trim()]).filter(([,value])=>value).map(([label,value])=>`${label}: ${value}`);
  return details.length?description.trim()+`\n\nProject preferences:\n${details.join('\n')}`:description.trim();
}
export function repeatDraft(description,questions){
  const withoutQuality=description.replace(/\nPrint quality preference: (Standard|Prioritize fine detail)$/,'');
  const unitMatch=withoutQuality.match(/\nModel units: (millimeters|centimeters|inches)$/);
  const modelUnits=unitMatch?{millimeters:'MM',centimeters:'CM',inches:'IN'}[unitMatch[1]]:'';
  const withoutUnits=unitMatch?withoutQuality.slice(0,-unitMatch[0].length):withoutQuality;
  const marker='\n\nProject preferences:\n',index=withoutUnits.lastIndexOf(marker);
  if(index<0)return {description:withoutUnits,answers:{},modelUnits};
  const answerLines=withoutUnits.slice(index+marker.length).split('\n');
  const answers={};
  for(const line of answerLines){
    const question=questions.find(item=>line.startsWith(item.label+': '));
    if(!question)return {description:withoutUnits,answers:{},modelUnits};
    answers[question.key]=line.slice(question.label.length+2);
  }
  return {description:withoutUnits.slice(0,index),answers,modelUnits};
}
