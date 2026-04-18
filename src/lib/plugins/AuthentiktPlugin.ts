import type {SvelteComponent} from "svelte";

export abstract class AuthentiktPlugin {
    namespace: string;
    component: SvelteComponent;

    protected constructor(namespace: string, component: SvelteComponent) {
        this.namespace = namespace;
        this.component = component;
    }
}