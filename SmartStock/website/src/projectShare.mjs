export function projectShareLinks(title,url){
  const message=`See ${title} at Deckers. Get inspired and make it yours: ${url}`;
  return {
    message,
    whatsapp:`https://wa.me/?text=${encodeURIComponent(message)}`,
    facebook:`https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}`
  };
}
