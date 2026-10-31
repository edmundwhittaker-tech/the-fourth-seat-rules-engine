"use client";

import { enginePhase, turnPhases } from '../../lib/rules/interaction';
import type { GameView } from '../../lib/rules/views';

export default function EngineTurnBar({game}:{game:GameView}) {
  const current = enginePhase(game);
  return <div className="engine-turn-bar">
    <nav aria-label="Game phases"><ol>{turnPhases.map((phase,index)=><li key={phase} aria-current={index===current.index?'step':undefined} className={index===current.index?'current':''}>{phase}</li>)}</ol></nav>
    <span className="engine-current-step" role="status">{current.step}</span>

  </div>;
}
