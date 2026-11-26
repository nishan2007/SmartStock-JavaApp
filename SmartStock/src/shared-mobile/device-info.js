'use strict';
(() => {
  const text=value=>typeof value==='string'?value.slice(0,160):'';
  async function collect() {
    const nav=window.navigator||{},ua=nav.userAgent||'',screen=window.screen||{};
    const ipad=/iPad/.test(ua)||nav.platform==='MacIntel'&&nav.maxTouchPoints>1;
    const ios=ipad||/iPhone|iPod/.test(ua),android=/Android/.test(ua);
    const family=ipad?'iPad':/iPhone/.test(ua)?'iPhone':/iPod/.test(ua)?'iPod':android?'Android device':'Computer';
    let browser='',browserVersion='';
    for(const [name,pattern] of [['Edge',/(?:EdgA|EdgiOS|Edg)\/([\d.]+)/],['Firefox',/(?:Firefox|FxiOS)\/([\d.]+)/],['Chrome',/(?:Chrome|CriOS)\/([\d.]+)/],['Safari',/Version\/([\d.]+).*Safari/]]) {
      const match=ua.match(pattern);if(match){browser=name;browserVersion=match[1];break;}
    }
    const os=ios?ua.match(/(?:CPU (?:iPhone )?OS|iPhone OS) ([\d_]+)/):android?ua.match(/Android ([\d.]+)/):ua.match(/(?:Windows NT |Mac OS X )([\d._]+)/);
    let hints={};
    if(nav.userAgentData?.getHighEntropyValues)try {
      hints=await Promise.race([nav.userAgentData.getHighEntropyValues(['model','platformVersion','fullVersionList']),new Promise(resolve=>setTimeout(()=>resolve({}),1000))]);
    }catch{/* Unsupported/denied client hints must not prevent sign-in. */}
    const brands=hints.fullVersionList||nav.userAgentData?.brands||[];
    const brand=brands.find(item=>/Google Chrome|Microsoft Edge|Opera/.test(item.brand));
    let timeZone='';try{timeZone=Intl.DateTimeFormat().resolvedOptions().timeZone||'';}catch{}
    return {
      deviceFamily:family,model:text(hints.model),
      platform:text(hints.platform||nav.userAgentData?.platform||(ios?'iOS / iPadOS':android?'Android':/Windows/.test(ua)?'Windows':/Mac/.test(ua)?'macOS':nav.platform||'')),
      osVersion:text(hints.platformVersion||os?.[1]?.replaceAll('_','.')||''),
      browser:text(brand?.brand||browser),browserVersion:text(brand?.version||browserVersion),
      screenWidth:Number(screen.width)||0,screenHeight:Number(screen.height)||0,
      pixelRatio:Number(window.devicePixelRatio)||1,touchPoints:Number(nav.maxTouchPoints)||0,
      displayMode:nav.standalone===true||window.matchMedia?.('(display-mode: standalone)').matches?'Home-screen app':'Browser tab',
      language:text(nav.language),timeZone:text(timeZone)
    };
  }
  window.DeckersDeviceInfo={collect};
})();
