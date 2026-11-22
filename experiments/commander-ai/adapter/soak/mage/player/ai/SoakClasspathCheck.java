package mage.player.ai;
import mage.cards.Sets;
/** Forces lazy expansion discovery before a game so duplicate classpaths fail early. */
public final class SoakClasspathCheck {
 public static void main(String[] args) {
  int count=Sets.getInstance().size();
  if(count==0)throw new IllegalStateException("No real expansion sets loaded");
  System.out.println("CLASSPATH_OK expansionSets="+count);
 }
}
