"""Separate offered loop draws from actual accepted engine draw outcomes."""
def loop_diagnostics(text):
    confirmations = refusals = outcomes = 0
    accepted_episode = False
    for line in text.splitlines():
        if 'has NOT confirmed that the game is a draw because of infinite looping' in line:
            refusals += 1
            accepted_episode = False
        elif 'has confirmed that the game is a draw because of infinite looping' in line:
            confirmations += 1
            accepted_episode = True
        elif accepted_episode and 'EVENT For ' in line and 'the game is a draw.' in line:
            outcomes += 1
    return dict(infiniteLoopDrawEvents=outcomes, infiniteLoopDrawConfirmations=confirmations,
                infiniteLoopDrawRefusals=refusals, loopDraws=outcomes)
