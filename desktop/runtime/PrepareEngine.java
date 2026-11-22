import java.nio.file.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
public class PrepareEngine {
  public static void main(String[] args) throws Exception {
    Path target=Path.of(args[1]).toAbsolutePath().normalize(); Files.createDirectories(target);
    try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(Path.of(args[0])))){
      ZipEntry entry;
      while((entry=zip.getNextEntry())!=null){Path dest=target.resolve(entry.getName()).normalize();if(!dest.startsWith(target))throw new Exception("Unsafe archive entry");if(entry.isDirectory())Files.createDirectories(dest);else{Files.createDirectories(dest.getParent());Files.copy(zip,dest,StandardCopyOption.REPLACE_EXISTING);}}
    }
    var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    var config=target.resolve("config/config.xml");var doc=factory.newDocumentBuilder().parse(config.toFile());
    var server=(Element)doc.getElementsByTagName("server").item(0);
    server.setAttribute("serverAddress","127.0.0.1");server.setAttribute("port",args[2]);server.setAttribute("authenticationActivated","false");server.setAttribute("maxAiOpponents","3");server.setAttribute("maxGameThreads","1");
    int found=0;var types=doc.getElementsByTagName("playerType");for(int i=0;i<types.getLength();i++){var type=(Element)types.item(i);if(type.getAttribute("name").equals("Computer - mad")){type.setAttribute("jar","../lib/mage-player-ai-1.4.62.jar");type.setAttribute("className","mage.player.ai.ComputerPlayerFourthSeat");found++;}}
    if(found!=1)throw new Exception("AI player registration is missing or ambiguous");
    TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc),new StreamResult(config.toFile()));
    Files.writeString(target.resolve(".prepared"),"ready");
  }
}
