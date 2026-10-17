package mage.player.ai;

import mage.Mana;
import mage.abilities.Ability;
import mage.abilities.costs.Cost;
import mage.abilities.costs.mana.ManaCost;
import mage.abilities.costs.mana.ManaCostImpl;
import mage.abilities.mana.ManaOptions;
import mage.constants.ColoredManaSymbol;
import mage.game.Game;
import mage.players.ManaPool;
import java.util.ArrayList;
import java.util.List;

/** Complete conjunctive producer input for disposable availability snapshots ONLY.
 * Never dispatch this wrapper or install it on a live ability. */
final class FourthSeatAtomicManaCost extends ManaCostImpl {
    private final ManaCost original;
    FourthSeatAtomicManaCost(ManaCost original) {
        this.original = original.copy();
        this.cost = original.getMana().copy();
        this.options = original.getOptions(false).copy();
        this.text = original.getText();
    }
    private FourthSeatAtomicManaCost(FourthSeatAtomicManaCost other) {
        super(other);
        this.original = other.original.copy();
    }
    @Override public FourthSeatAtomicManaCost copy() { return new FourthSeatAtomicManaCost(this); }
    @Override public int manaValue() { return original.manaValue(); }
    @Override public List<Mana> getManaOptions() { return new ArrayList<Mana>(options.copy()); }
    @Override public ManaCost getUnpaid() { return copy(); }
    @Override public boolean containsColor(ColoredManaSymbol color) { return original.containsColor(color); }
    @Override public boolean testPay(Mana mana) { return original.testPay(mana); }
    @Override public void assignPayment(Game game, Ability ability, ManaPool pool, Cost costs) {
        throw new IllegalStateException("Snapshot-only cost cannot be paid");
    }
}
