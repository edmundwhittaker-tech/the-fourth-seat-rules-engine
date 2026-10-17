package mage.player.ai;

import java.util.UUID;
import mage.abilities.Ability;
import mage.abilities.common.LandfallAbility;
import mage.abilities.effects.common.DoubleCountersSourceEffect;
import mage.abilities.keyword.TrampleAbility;
import mage.counters.CounterType;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.game.permanent.PermanentImpl;
import mage.players.Player;

/** Public future-growth signal, never a claim that a sick creature can attack now. */
final class FourthSeatCounterGrowthPolicy {
    private FourthSeatCounterGrowthPolicy() {}
    static boolean hasOverwhelmingMultiplicativeThreat(UUID playerId, Game game) {
        long opposition = 0;
        for (UUID id : game.getOpponents(playerId, true)) {
            Player opponent = game.getPlayer(id);
            if (opponent == null || opponent.hasLeft() || opponent.hasLost()) continue;
            opposition += Math.max(0, opponent.getLife());
            for (Permanent p : game.getBattlefield().getAllActivePermanents(id))
                if (!p.isFaceDown(game) && p.isCreature(game)) opposition += Math.max(0, p.getToughness().getValue());
        }
        long threshold = Math.max(64L, 2L * opposition);
        for (Permanent p : game.getBattlefield().getAllActivePermanents(playerId)) {
            if (p.isFaceDown(game) || !p.isCreature(game) || !(p instanceof PermanentImpl)
                    || p.getPower().getValue() < threshold || p.getCounters(game).getCount(CounterType.P1P1) <= 0
                    || !p.hasAbility(TrampleAbility.getInstance(), game)) continue;
            boolean doubling = false;
            for (Ability a : p.getAbilities(game)) if (a instanceof LandfallAbility
                    && a.getEffects().stream().anyMatch(e -> e instanceof DoubleCountersSourceEffect)) doubling = true;
            if (!doubling) continue;
            PermanentImpl future = (PermanentImpl) p.copy();
            future.removeSummoningSickness();
            boolean connected = true;
            for (UUID opponent : game.getOpponents(playerId, true))
                if (!future.canAttackInPrinciple(opponent, game)) connected = false;
            if (connected) return true;
        }
        return false;
    }
}
