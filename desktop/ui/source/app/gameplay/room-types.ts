import type { CardData } from "../../lib/types";
import type { CardInstance, ClientGameState, ClientPlayerGameState, RoomPlayer, RoomState, RoomSyncEvent } from "../../lib/game-types";
import { type FinishedGameSummary } from "../../lib/game-summary";

type ClientPlayer = RoomPlayer & {
    connected?: boolean;
};

export type ClientPlayerState = ClientPlayerGameState;

export type ClientRoom = Omit<RoomState, "players" | "game"> & {
    viewerSeatId: string;
    hostSeatId: string;
    isHost: boolean;
    isSpectator?: boolean;
    clientProtocol?: number;
    players: ClientPlayer[];
    game: ClientGameState | null;
};

export type ApiResult = {
    room?: ClientRoom;
    result?: FinishedGameSummary;
    closed?: boolean;
    version?: number;
    error?: string;
    finished?: boolean;
    deleted?: boolean;
    acceptedActionIds?: string[];
    rejectedActionId?: string;
    timing?: {
        backendMs: number;
        persistenceMs: number;
        receivedToCommitMs?: number;
    };
};

export type RoomEventsResult = ApiResult & {
    events?: RoomSyncEvent[];
    notifiedAt?: number;
    waitedMs?: number;
};

export type ZoneBrowser = {
    zone: "graveyard" | "exile";
    seatId: string;
} | null;

export type LibraryTool = null | {
    mode: "search" | "scry" | "look" | "reveal";
    count: number;
};

export type PrivateLibraryResult = {
    cards?: CardInstance[];
    libraryCount?: number;
    error?: string;
};

export type SyncStatus = "live" | "syncing" | "reconnecting" | "offline";

export type PlayedCardNotice = {
    id: string;
    card: CardData;
    playerName: string;
    verb: "PLAYED" | "CAST";
};

export type TableAnnouncement = {
    id: string;
    text: string;
};

export type CardStatus = [
    name: string,
    amount?: number
];

export type CombatCardView = {
    attackLabel?: string;
    colourClass?: string;
    onBadgeClick?: () => void;
};

export type DragData = {
    instanceId?: string;
    from: string;
    card?: CardData;
};

export type MarqueeGesture = {
    pointerId: number;
    startX: number;
    startY: number;
    currentX: number;
    currentY: number;
    additive: boolean;
    active: boolean;
};

export type AttachPrompt = {
    attachmentId: string;
    hostId: string;
    attachmentName: string;
    hostName: string;
    x: number;
    y: number;
};
