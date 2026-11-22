import type {ImportResult} from '../../lib/types';

/** The lookup returns oracle IDs (or printing IDs); the picker and save API use card names. */
export function defaultCommanderName(result:ImportResult):string {
  for(const id of result.commanderCandidates){
    const match=result.cards.find(card=>(card.oracleId??card.id)===id);
    if(match)return match.name;
  }
  return '';
}
