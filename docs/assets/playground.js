import { applyAction, getPublicState, RuleError } from "./engine.js";
import { createDemoGame } from "./demo.js";
let state = createDemoGame();
let history = [{ type: "info", text: "Local demo started. Demo decks are not shuffled." }];
const byId = (id) => {
    const el = document.getElementById(id);
    if (!el)
        throw new Error(`Missing element: ${id}`);
    return el;
};
const escapeHtml = (value) => value.replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
})[c] || c);
const formatMana = (mana) => Object.entries(mana)
    .filter(([, count]) => count > 0)
    .map(([symbol, count]) => `{${symbol}} ×${count}`).join(" ") || "empty";
function transact(command) {
    try {
        const result = applyAction(state, command);
        state = result.state;
        history.push(...result.events);
        if (history.length > 100)
            history = history.slice(-100);
        byId("error").textContent = "";
    }
    catch (error) {
        byId("error").textContent = error instanceof RuleError ? error.message : String(error);
    }
    render();
}
function passRound() {
    // Dev convenience: simulate enough passes to resolve one stack item or advance one step.
    const count = state.players.length - state.consecutivePasses;
    for (let i = 0; i < count; i++) {
        const who = state.priorityPlayerId;
        const result = applyAction(state, { type: "PASS_PRIORITY", playerId: who });
        state = result.state;
        history.push(...result.events);
    }
    if (history.length > 100)
        history = history.slice(-100);
    byId("error").textContent = "";
    render();
}
function render() {
    const visible = getPublicState(state);
    const active = state.players.find((p) => p.id === state.activePlayerId);
    const priority = state.players.find((p) => p.id === state.priorityPlayerId);
    byId("turn").textContent = `Turn ${state.turnNumber} · ${state.step}`;
    byId("active").textContent = active.name;
    byId("priority").textContent = priority.name;
    byId("passes").textContent = String(state.consecutivePasses);
    byId("stack").innerHTML = visible.stack.length
        ? visible.stack.slice().reverse().map((x) => `<div class="stack-entry">${escapeHtml(x.card)} <span>by ${escapeHtml(state.players.find((p) => p.id === x.controllerId)?.name || "?")}</span></div>`).join("")
        : `<p class="muted">The stack is empty.</p>`;
    byId("players").innerHTML = visible.players.map((p) => `
    <article class="player ${p.id === visible.priorityPlayerId ? "priority" : ""}">
      <h3>${escapeHtml(p.name)} ${p.id === visible.activePlayerId ? `<span class="chip">Active</span>` : ""}</h3>
      <div class="life">${p.life} <span>life</span></div>
      <div class="player-meta">Hand ${p.handCount} · Library ${p.libraryCount}</div>
      <div class="player-meta">Mana: ${escapeHtml(formatMana(p.mana))}</div>
      <div class="player-meta">Board: ${p.battlefield.length ? p.battlefield.map((c) => escapeHtml(c.name) + (c.tapped ? " (tapped)" : "")).join(", ") : "empty"}</div>
    </article>
  `).join("");
    const selectedTarget = byId("target").value;
    byId("target").innerHTML = state.players.map((p) => `<option value="${escapeHtml(p.id)}">${escapeHtml(p.name)}</option>`).join("");
    byId("target").value = state.players.some((p) => p.id === selectedTarget)
        ? selectedTarget : state.players.find((p) => p.id !== priority.id)?.id || priority.id;
    const hand = priority.hand.map((id) => {
        const c = state.cards[id];
        const land = c.definition.types.includes("Land");
        const verb = land ? "Play land" : "Cast spell";
        return `<div class="card-row"><div><strong>${escapeHtml(c.definition.name)}</strong><small>${escapeHtml(c.definition.types.join(" / "))} ${escapeHtml(c.definition.manaCost || "")}</small></div><button data-command="${land ? "PLAY_LAND" : "CAST_SPELL"}" data-card="${escapeHtml(id)}">${verb}</button></div>`;
    }).join("");
    const tap = priority.battlefield.filter((id) => state.cards[id].definition.producesMana).map((id) => {
        const c = state.cards[id];
        return `<div class="card-row"><div><strong>${escapeHtml(c.definition.name)}</strong><small>${c.tapped ? "Tapped" : `Produces {${c.definition.producesMana}}`}</small></div><button data-command="TAP_FOR_MANA" data-card="${escapeHtml(id)}" ${c.tapped ? "disabled" : ""}>Tap</button></div>`;
    }).join("");
    byId("hand").innerHTML = hand || `<p class="muted">No cards in hand.</p>`;
    byId("battlefield").innerHTML = tap || `<p class="muted">No mana-producing permanents.</p>`;
    byId("log").innerHTML = history.slice(-16).reverse().map((e) => `<li>${escapeHtml(e.text)}</li>`).join("");
}
byId("pass").addEventListener("click", () => transact({ type: "PASS_PRIORITY", playerId: state.priorityPlayerId }));
byId("pass-round").addEventListener("click", passRound);
byId("reset").addEventListener("click", () => {
    state = createDemoGame();
    history = [{ type: "info", text: "Local demo restarted." }];
    byId("error").textContent = "";
    render();
});
document.addEventListener("click", (event) => {
    if (!(event.target instanceof HTMLElement))
        return;
    const button = event.target.closest("button[data-command]");
    if (!button)
        return;
    const type = button.dataset.command;
    const cardId = button.dataset.card;
    if (!cardId)
        return;
    const playerId = state.priorityPlayerId;
    if (type === "PLAY_LAND")
        transact({ type, playerId, cardId });
    if (type === "TAP_FOR_MANA")
        transact({ type, playerId, cardId });
    if (type === "CAST_SPELL") {
        const selected = state.cards[cardId];
        const targetPlayerId = selected.definition.effect?.kind === "damage-player"
            ? byId("target").value : undefined;
        transact({ type, playerId, cardId, targetPlayerId });
    }
});
render();
