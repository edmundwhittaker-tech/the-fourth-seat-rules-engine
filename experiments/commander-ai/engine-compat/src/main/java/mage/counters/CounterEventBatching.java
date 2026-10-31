package mage.counters;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import mage.game.Game;
import mage.game.GameImpl;
import mage.game.events.GameEvent;
/** Uses only build-time proofs of irrelevant individual-counter callbacks.
 * It never calls a callback to speculate about its behavior on a changed game state.
 */
public final class CounterEventBatching {
 private CounterEventBatching(){}
 public static boolean ignores(Object observer,String method){
  if(observer==null||System.getSecurityManager()!=null)return false;
  try{Method m=observer.getClass().getMethod(method,GameEvent.class,Game.class);
   String key=method+":"+m.getDeclaringClass().getName();
   if(!CounterObserverRegistry.IGNORES.contains(key))return false;
   String[] required=CounterObserverRegistry.REQUIRED.get(key);
   if(required!=null)for(String entry:required){int hash=entry.lastIndexOf('#');Class<?> owner=m.getDeclaringClass();
    while(owner!=null&&!owner.getName().equals(entry.substring(0,hash)))owner=owner.getSuperclass();
    if(owner==null)return false;Field field=owner.getDeclaredField(entry.substring(hash+1));
    if(!Modifier.isFinal(field.getModifiers()))return false;field.setAccessible(true);if(field.get(observer)==null)return false;
   }
   return true;
  }catch(ReflectiveOperationException|RuntimeException|LinkageError e){return false;}
 }
 public static boolean standardDispatch(Game game){
  try{return game.getClass().getMethod("fireEvent",GameEvent.class).getDeclaringClass()==GameImpl.class
    &&game.getClass().getMethod("replaceEvent",GameEvent.class).getDeclaringClass()==GameImpl.class
    &&game.getClass().getMethod("getState").getDeclaringClass()==GameImpl.class;
  }catch(ReflectiveOperationException|RuntimeException|LinkageError e){return false;}
 }
}
