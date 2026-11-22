"use client";

import { createContext } from 'react';
import type { ReactNode } from 'react';
export type EngineControls = {
  statusBar?: ReactNode;
  menuContent?: ReactNode;
  openCardChoices?: () => void;
  cardChoiceZones?: Set<string>;
  endGame?: () => void;
  endTurn?: () => void;
  endingTurn?: boolean;
  lifecycleBusy?: boolean;
  playerChoiceActive?: boolean;
  playerChoiceLabel?: string;
  playerTargetIds?: Set<string>;
  selectPlayer?: (id:string) => void;
  selectable: Set<string>;
  select: (id:string) => void;
  prompt: ReactNode;
  canEndTurn: boolean;
};
export const EngineControlsContext = createContext<EngineControls|null>(null);
