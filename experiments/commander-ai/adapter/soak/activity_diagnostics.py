"""Review flags from synthetic game traces. Activity is not a strategic strength rating."""
import re


def activity_diagnostics(plain, seat):
    name = re.escape(seat['commander'])
    turns = len(set(re.findall(r'^TEST_ONLY_OWN_HAND turn=(\d+) player=' + name + r' cards=', plain, re.M)))
    flags = []
    if turns >= 3 and seat['spellCasts'] == 0:
        flags.append('NO_SPELLS_AFTER_THREE_TURNS')
        if seat['landPlays'] < 3:
            flags.append('LOW_LAND_DEVELOPMENT')
    return {'ownMainPhasesObserved': turns, 'activityReviewFlags': flags}
