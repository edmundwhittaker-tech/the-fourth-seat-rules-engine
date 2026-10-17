package mage.player.ai;
import mage.cards.*;
import mage.cards.decks.Deck;
import mage.constants.*;
import mage.game.*;
import mage.game.match.MatchType;
import mage.game.mulligan.MulliganType;
import mage.players.Player;
import mage.util.RandomUtil;
import java.nio.file.*;
import java.util.*;
/** Isolated real engine game, not live server. Actual frozen card lists; no substitutes. */
public final class FourthSeatSoak {
 static final class SoakType extends MatchType { SoakType(){name="Fourth Seat Commander soak";minPlayers=maxPlayers=4;} public MatchType copy(){return new SoakType();} }
 static final class SoakGame extends GameCommanderImpl {
  final long began=System.currentTimeMillis(); int completedTurns; volatile long lastTurnProgress=began; Set<String> seenHands=new HashSet<String>();
  SoakGame(int turnLimit) {super(MultiplayerAttackOption.MULTIPLE,RangeOfInfluence.ALL,MulliganType.LONDON.getMulligan(1),100,40,7);startingPlayerSkipsDraw=false;GameOptions o=new GameOptions();o.stopOnTurn=turnLimit;o.rollbackTurnsAllowed=false;setGameOptions(o);}
  SoakGame(SoakGame other){super(other);completedTurns=other.completedTurns;}
  public SoakGame copy(){return new SoakGame(this);} public int getNumPlayers(){return 4;}public MatchType getGameType(){return new SoakType();}
  protected void endOfTurn(){super.endOfTurn();completedTurns++;lastTurnProgress=System.currentTimeMillis();System.out.println("TURN_DONE turn="+getTurnNum()+" elapsedMs="+(System.currentTimeMillis()-began)+" "+snapshot());System.out.flush();}
  String snapshot(){StringBuilder s=new StringBuilder();for(Player p:getPlayers().values())s.append("[").append(p.getName()).append(" life=").append(p.getLife()).append(" hand=").append(p.getHand().size()).append(" library=").append(p.getLibrary().size()).append(" permanents=").append(getBattlefield().getAllActivePermanents(p.getId()).size()).append(" lost=").append(p.hasLost()).append("]");return s.toString();}
  public synchronized void firePriorityEvent(UUID playerId){super.firePriorityEvent(playerId);if(playerId.equals(getActivePlayerId())&&getTurnStepType()==PhaseStep.PRECOMBAT_MAIN&&seenHands.add(getTurnNum()+":"+playerId)){Player p=getPlayer(playerId);List<String> hand=new ArrayList<String>();for(Card c:p.getHand().getCards(this))hand.add(c.getName()+"(mv="+c.getManaValue()+")");System.out.println("TEST_ONLY_OWN_HAND turn="+getTurnNum()+" player="+p.getName()+" cards="+hand);}}
  public void informPlayers(String message){super.informPlayers(message);System.out.println("EVENT "+message);}
 }
 public static void main(String[] args)throws Exception {
  long seed=Long.parseLong(args[1]);RandomUtil.setSeed(seed);int limit=Integer.parseInt(args[2]);SoakGame game=new SoakGame(limit);
  for(int i=3;i<args.length;i++){
   List<String> lines=Files.readAllLines(Paths.get(args[0],args[i]+".tsv"));String name="";for(String l:lines){String[] f=l.split("\t");if(f[0].equals("commanders"))name=f[2];}
   ComputerPlayerFourthSeat player=new ComputerPlayerFourthSeat(name,RangeOfInfluence.ALL);Deck deck=new Deck();deck.setName(name);
   for(String l:lines){String[] f=l.split("\t");for(int n=0;n<Integer.parseInt(f[1]);n++){Card c=(Card)Class.forName(f[3]).getConstructor(UUID.class,CardSetInfo.class).newInstance(player.getId(),new CardSetInfo(f[2],"TST","1",Rarity.COMMON));if(!c.getName().equals(f[2])&&!f[2].contains(" // "))throw new IllegalStateException("Card name mismatch "+f[2]+" -> "+c.getName());if(f[0].equals("cards"))deck.getCards().add(c);else deck.getSideboard().add(c);}}
   if(deck.getCards().size()+deck.getSideboard().size()!=100)throw new IllegalStateException("Not 100 cards: "+name);
   player.init(game);game.loadCards(deck.getCards(),player.getId());game.loadCards(deck.getSideboard(),player.getId());game.addPlayer(player,deck);System.out.println("DECK "+args[i]+" "+name+" cards="+deck.getCards().size()+" commander="+deck.getSideboard().size());
  }
  if(args.length!=7)throw new IllegalArgumentException("Exactly four deck indexes required");
  System.out.println("START seed="+seed+" turnLimit="+limit+" Note: UUID tie-breaking means seed alone is not full deterministic replay.");
  final Thread gameThread=Thread.currentThread();
  final Thread monitor=new Thread(()->{int diagnosticSamples=0;try{while(true){Thread.sleep(10000);long progressGap=System.currentTimeMillis()-game.lastTurnProgress;if(progressGap>=20000&&diagnosticSamples<12){diagnosticSamples++;System.out.println("SLOW_TURN_STACK sample="+diagnosticSamples+" progressGapMs="+progressGap+" threadState="+gameThread.getState());StackTraceElement[] frames=gameThread.getStackTrace();for(int f=0;f<Math.min(frames.length,24);f++)System.out.println("SLOW_TURN_FRAME "+frames[f]);}System.out.println("HEARTBEAT turn="+game.getTurnNum()+" phase="+game.getTurn().getPhase()+" elapsedMs="+(System.currentTimeMillis()-game.began));System.out.flush();}}catch(InterruptedException ignored){}});monitor.setDaemon(true);monitor.start();
  try {game.start(game.getPlayers().values().iterator().next().getId());System.out.println("RESULT status="+(game.isPaused()?"TURN_BOUND":"FINISHED")+" completedTurns="+game.completedTurns+" elapsedMs="+(System.currentTimeMillis()-game.began)+" "+game.snapshot());}finally{monitor.interrupt();game.end();}
  System.exit(0);
 }
}
