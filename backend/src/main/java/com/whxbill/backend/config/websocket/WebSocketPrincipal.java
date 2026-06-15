package com.whxbill.backend.config.websocket;

import java.security.Principal;

public record WebSocketPrincipal(String value) implements Principal {

    @Override
    public String getName() {
        return value;
    }
}
