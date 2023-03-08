package com.bawnorton.mcgpt.command;

import dev.architectury.event.events.common.CommandRegistrationEvent;

public abstract class Commands {
    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> {
            AuthCommand.register(dispatcher);
            AskCommand.register(dispatcher);
            PreviousConversationCommand.register(dispatcher);
            NewConversationCommand.register(dispatcher);
        });
    }
}
