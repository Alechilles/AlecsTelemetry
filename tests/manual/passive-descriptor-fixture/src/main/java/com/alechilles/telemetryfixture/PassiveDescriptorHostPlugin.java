package com.alechilles.telemetryfixture;

import com.alechilles.passivedescriptorlibrary.EmbeddedDescriptorLibrary;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/**
 * Physical host for the manual passive-descriptor fixture.
 *
 * <p>It intentionally has no Beacon dependency or API calls. The
 * embedded library marker proves that its classes and descriptor were merged
 * into this final plugin archive.</p>
 */
public final class PassiveDescriptorHostPlugin extends JavaPlugin {

    public PassiveDescriptorHostPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().atInfo().log(
                "Passive descriptor host fixture loaded embedded library " + EmbeddedDescriptorLibrary.MARKER
        );
    }
}
