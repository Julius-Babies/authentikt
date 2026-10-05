import { createSubscriber } from "svelte/reactivity";
import type { Authentikt } from "./AuthentiktConfiguration.svelte";

/**
 * `rate_limit` in the payload of rate-limited steps, as sent by the server.
 */
export interface RateLimitPayload {
    max_tries: number;
    period_seconds: number;
    remaining_tries: number;
    /** Seconds until the next attempt is allowed. Only present while the step is locked. */
    retry_after_seconds?: number | null;
}

/**
 * Rate limit of a step, as exposed by the plugin instances.
 */
export interface RateLimitState {
    /** Failed attempts allowed per period. */
    maxTries: number;
    /** Length of the period in seconds. */
    periodSeconds: number;
    /** Attempts left before the step is locked. */
    remainingTries: number;
    /** Whether the step is locked. Submitting is not possible until the lock expires. */
    isLocked: boolean;
    /** Seconds until the lock expires, `0` if not locked. Counts down while it is read. */
    remainingLockSeconds: number;
    /** When the lock expires, or `null` if not locked. */
    lockedUntil: Date | null;
}

/**
 * Formats a number of seconds as `m:ss`, e.g. for {@link RateLimitState.remainingLockSeconds}.
 */
export function formatLockDuration(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    return `${minutes}:${String(seconds % 60).padStart(2, "0")}`;
}

/**
 * Derives the reactive {@link RateLimitState} of a step from the `rate_limit` in its payload.
 *
 * While the step is locked and the state is read, it ticks every second. When the lock expires, the flow state is
 * reloaded to get the new number of remaining tries.
 */
export class RateLimitTracker {
    private readonly authentikt: Authentikt;
    private readonly namespace: string;
    private refreshedLock: number | null = null;

    private readonly subscribe = createSubscriber((update) => {
        const interval = setInterval(() => {
            const lockedUntil = this.lockedUntil;
            if (lockedUntil !== null && Date.now() >= lockedUntil && this.refreshedLock !== lockedUntil) {
                this.refreshedLock = lockedUntil;
                void this.authentikt.updateState();
            }
            update();
        }, 1000);
        return () => clearInterval(interval);
    });

    constructor(authentikt: Authentikt, namespace: string) {
        this.authentikt = authentikt;
        this.namespace = namespace;
    }

    private get payload(): RateLimitPayload | null {
        const step = this.authentikt.currentFlow?.step;
        if (step?.type !== "step" || step.namespace !== this.namespace) return null;
        return (step.payload?.rate_limit as RateLimitPayload | undefined) ?? null;
    }

    /** Epoch milliseconds at which the lock expires, measured with the local clock. */
    private get lockedUntil(): number | null {
        const retryAfter = this.payload?.retry_after_seconds;
        if (retryAfter == null) return null;
        return this.authentikt.stepReceivedAt + retryAfter * 1000;
    }

    /**
     * The current rate limit, or `null` if the step is not rate-limited.
     */
    get state(): RateLimitState | null {
        const payload = this.payload;
        if (!payload) return null;

        const lockedUntil = this.lockedUntil;
        if (lockedUntil !== null) this.subscribe();
        const remainingMs = lockedUntil === null ? 0 : Math.max(0, lockedUntil - Date.now());

        return {
            maxTries: payload.max_tries,
            periodSeconds: payload.period_seconds,
            remainingTries: payload.remaining_tries,
            isLocked: remainingMs > 0,
            remainingLockSeconds: Math.ceil(remainingMs / 1000),
            lockedUntil: remainingMs > 0 ? new Date(lockedUntil!) : null,
        };
    }
}
