package org.example.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.module.api.Module;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.example.ExamplePlugin;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;

public class ExampleModule extends Module {
    final Timer timer = Timers.tickTimer();

    @Override
    public boolean enabledSetting() {
        return ExamplePlugin.PLUGIN_CONFIG.exampleModule.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleBotTick)
        );
    }

    private void handleBotTick(ClientBotTick event) {
        if (timer.tick(ExamplePlugin.PLUGIN_CONFIG.exampleModule.delayTicks)) {
            info("Hello from ExampleModule!");
        }
    }
}
