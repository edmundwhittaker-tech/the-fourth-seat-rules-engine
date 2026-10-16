import type { FeedbackTextFn } from './types.ts';
const labels: Record<string,string> = {
  "choose_option": "Choose an option",
  "declare_attackers": "Declare attackers",
  "declare_blockers": "Declare blockers",
  "mulligan_btn": "Mulligan",
  "mulligan_keep": "Keep hand",
  "yes": "Yes",
  "no": "No",
  "voting_title": "Vote",
  "mulligan_title": "Opening hand",
  "who_starts": "Choose the starting player",
  "confirmation": "Your decision",
  "choose_discard": "Choose a card to discard",
  "trigger_title": "Order triggered abilities",
  "library_order_title": "Order cards",
  "choose_target": "Choose a target",
  "choose_cards": "Choose cards",
  "planeswalker_title": "Choose a loyalty ability",
  "choose_ability": "Choose an ability",
  "choose_pile": "Choose a pile",
  "pile_1": "Pile 1",
  "pile_2": "Pile 2",
  "pile_summary": "{fallback} · {count} cards",
  "pay_mana": "Pay mana",
  "confirm": "Confirm",
  "cancel": "Cancel",
  "amount_title": "Choose an amount",
  "multi_amount_title": "Distribute amounts",
  "choose_mode": "Choose a mode",
  "choose_color": "Choose a colour",
  "choose_number": "Choose a number",
  "choose_name": "Choose a name",
  "choose_between": "Choose an option",
  "choose_order": "Order cards",
  "choose_target_amount": "Choose a target amount",
  "choose_player_title": "Choose a player",
  "target_fallback": "Target {index}",
  "amount_fallback": "Amount {index}"
};
export const defaultText: FeedbackTextFn = (_namespace,key,params) => {
  let value = labels[key] ?? key.replaceAll('_',' ');
  for (const [name,replacement] of Object.entries(params ?? {})) value=value.replaceAll('{'+name+'}',String(replacement));
  return value;
};

