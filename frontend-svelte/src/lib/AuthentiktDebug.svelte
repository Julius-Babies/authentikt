<script lang="ts">
    import type {Authentikt} from "$lib/AuthentiktConfiguration.svelte";
    import {JsonView} from "@zerodevx/svelte-json-view";

    let {
        authentikt,
    }: {
        authentikt: Authentikt;
    } = $props();

    const currentFlow = $derived(authentikt.currentFlow);

    let debugPosition = $state({x: 300, y: 400});
    let dragOffset = {x: 0, y: 0};
    let dragging = $state(false);

    function onPointerDown(event: PointerEvent) {
        event.preventDefault();
        dragging = true;
        dragOffset.x = event.clientX - debugPosition.x;
        dragOffset.y = event.clientY - debugPosition.y;
        (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    }

    function onPointerMove(event: PointerEvent) {
        if (!dragging) return;
        event.preventDefault();
        debugPosition.x = event.clientX - dragOffset.x;
        debugPosition.y = event.clientY - dragOffset.y;
    }

    function onPointerUp(event: PointerEvent) {
        dragging = false;
        (event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId);
    }
</script>

<div
        class="absolute flex flex-col bg-white border border-orange-400 z-50"
        style="top: {debugPosition.y}px; left: {debugPosition.x}px; touch-action: none;"
>
    <div
            aria-hidden="true"
            class="relative h-11 w-full select-none overflow-hidden border-b border-orange-300 bg-orange-50"
            class:cursor-grab={!dragging}
            class:cursor-grabbing={dragging}
            onpointerdown={onPointerDown}
            onpointermove={onPointerMove}
            onpointerup={onPointerUp}
    >
        <!-- diagonal stripe overlay -->
        <svg class="absolute inset-0 h-full w-full opacity-15" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <pattern id="diag" x="0" y="0" width="18" height="18"
                         patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                    <rect width="9" height="18" fill="#c05a00"/>
                </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#diag)"/>
        </svg>

        <!-- left: label -->
        <div class="relative z-10 flex h-full items-center gap-2 px-3">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <circle cx="7" cy="7" r="6.25" stroke="#b45309" stroke-width="1.5"/>
                <rect x="6.25" y="3.5" width="1.5" height="4.5" rx="0.75" fill="#b45309"/>
                <circle cx="7" cy="10" r="0.85" fill="#b45309"/>
            </svg>
            <span class="text-[11px] font-medium uppercase tracking-wider text-amber-800">
              Authenti.kt Debug
            </span>
        </div>
    </div>

    <div class="flex flex-col p-2">
        <h2>State</h2>
        <JsonView json={currentFlow}/>
    </div>
</div>
