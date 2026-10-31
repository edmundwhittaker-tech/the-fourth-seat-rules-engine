package mage;

import mage.abilities.Ability;
import mage.game.Game;
import mage.constants.Zone;

import java.io.Serializable;

/**
 *
 * @author LevelX2
 */
public class ApprovingObject implements Serializable {
        
    private final Ability approvingAbility;
    private final MageObjectReference approvingMageObjectReference;
    
    public ApprovingObject(Ability source, Game game) {
        this.approvingAbility = source;
        // Resolved permissions can outlive the source card's owner leaving the
        // game. Preserve their captured identity instead of looking up an object
        // that multiplayer elimination has legitimately removed. Do not discard
        // the permission or change its duration/replacement effects.
        int capturedZcc = source.getStackMomentSourceZCC();
        int retainedZcc = source.getSourceId() == null ? 0
                : game.getState().getZoneChangeCounter(source.getSourceId());
        MageObject stackLki = source.getSourceId() == null ? null
                : game.getLastKnownInformation(source.getSourceId(), Zone.STACK);
        if (source.getSourceId() != null
                && capturedZcc > 0 && retainedZcc >= capturedZcc
                && game.getObject(source.getSourceId()) == null
                && (stackLki == null || stackLki.getZoneChangeCounter(game) < retainedZcc)) {
            this.approvingMageObjectReference = new MageObjectReference(
                    source.getSourceId(),
                    retainedZcc, game);
        } else {
            this.approvingMageObjectReference = new MageObjectReference(source.getSourceId(), game);
        }
    }

    public Ability getApprovingAbility() {
        return approvingAbility;
    }

    public MageObjectReference getApprovingMageObjectReference() {
        return approvingMageObjectReference;
    }
        
}
