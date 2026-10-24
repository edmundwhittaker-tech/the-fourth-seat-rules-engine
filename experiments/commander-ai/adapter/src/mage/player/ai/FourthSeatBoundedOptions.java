package mage.player.ai;

import java.util.*;
import mage.abilities.Ability;
import mage.abilities.Mode;
import mage.constants.TargetController;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.game.stack.StackObject;
import mage.target.Target;
import mage.target.TargetPermanent;

/** Bounded modal target representatives. Null means the engine fallback is required;
 * an empty list means supported but no legal selection. Original is never mutated. */
final class FourthSeatBoundedOptions {
    private FourthSeatBoundedOptions() {}
    static List<Ability> generate(Ability original, Game game) {
        if (!original.isModal() || original.getModes().size() > 4
                || original.getModes().getChooseController() != TargetController.YOU
                || original.getModes().isMayChooseSameModeMoreThanOnce()
                || original.getModes().getMaxPawPrints() > 0
                || !original.getManaCosts().getVariableCosts().isEmpty()
                || !original.getCosts().getTargets().isEmpty()) return null;
        Ability blueprint = original.copy();
        blueprint.getModes().clearSelectedModes();
        List<Mode> modes = new ArrayList<Mode>(blueprint.getModes().getAvailableModes(blueprint, game));
        for (Mode mode : modes) for (Target target : mode.getTargets())
            if (!(target instanceof TargetPermanent) || target.getMinNumberOfTargets() != 1
                    || target.getMaxNumberOfTargets() != 1) return null;
        List<Ability> result = new ArrayList<Ability>();
        int min = original.getModes().getMinModes(), max = original.getModes().getMaxModes(game, original);
        for (int mask = 1; mask < (1 << modes.size()); mask++) {
            int size = Integer.bitCount(mask);
            if (size < min || size > max) continue;
            for (int variant = 0; variant < 4; variant++) {
                Ability copy = original.copy();
                copy.getModes().clearSelectedModes();
                boolean valid = true;
                for (int i = 0; i < modes.size(); i++) if ((mask & (1 << i)) != 0) {
                    UUID modeId = modes.get(i).getId();
                    copy.getModes().addSelectedMode(modeId);
                    Mode mode = copy.getModes().get(modeId);
                    copy.getModes().setActiveMode(modeId);
                    boolean good = mode.getEffects().getOutcome(copy).isGood();
                    for (Target target : mode.getTargets()) {
                        target.clearChosen();
                        List<UUID> ids = new ArrayList<UUID>(target.possibleTargets(copy.getControllerId(), copy, game));
                        final Ability selected = copy;
                        Collections.sort(ids, Comparator.comparingDouble((UUID id) -> -value(id, good, selected, game)).thenComparing(UUID::toString));
                        if (ids.isEmpty()) { valid = false; break; }
                        UUID id = ids.get(Math.min(variant, ids.size() - 1));
                        if (!target.canTarget(copy.getControllerId(), id, copy, game)) { valid = false; break; }
                        target.addTarget(id, copy, game);
                    }
                    if (!valid) break;
                }
                if (valid) { copy.getModes().setPreselected(true); result.add(copy); }
                if (result.size() >= 64) return result;
            }
        }
        return result;
    }
    private static double value(UUID id, boolean good, Ability source, Game game) {
        Permanent p = game.getPermanent(id);
        if (p == null) return 0;
        boolean own = source.getControllerId().equals(p.getControllerId());
        double score = (own == good ? 1 : -1) * (p.getManaValue() + Math.max(0, p.getPower().getValue()) + 1);
        if (own && good) for (StackObject pending : game.getStack()) {
            if (source.getControllerId().equals(pending.getControllerId())) continue;
            if (pending.getStackAbility() != null) for (Target target : pending.getStackAbility().getTargets())
                if (target.getTargets().contains(id)) score += 100;
        }
        return score;
    }
}
