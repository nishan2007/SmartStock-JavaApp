import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
/** Read-only publisher fallback through the same signed download path as installed clients. */
public class ReadModelCatalogue {
 public static void main(String[]args)throws Exception {
  if(args.length!=2||!args[0].matches("models/(catalogue-v1\\.json|catalogues/[a-f0-9]{64}\\.json)"))throw new IllegalArgumentException("Only model catalogue JSON may be read.");
  System.setProperty("smartstock.environment","production");
  var type=Class.forName("services.R2UpdateUrlSigner");var sign=type.getDeclaredMethod("createDownloadUrl",String.class,String.class);sign.setAccessible(true);
  String signed=(String)sign.invoke(null,"r2:smartstock-updates",args[0]);
  var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build().send(HttpRequest.newBuilder(URI.create(signed)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
  try(var input=response.body()){
   if(response.statusCode()!=200)throw new IllegalStateException("Signed model catalogue request failed (HTTP "+response.statusCode()+").");
   byte[] bytes=input.readNBytes(65537);if(bytes.length>65536)throw new IllegalStateException("Model catalogue exceeds 64 KiB.");Files.write(Path.of(args[1]),bytes);
  }
 }
}
