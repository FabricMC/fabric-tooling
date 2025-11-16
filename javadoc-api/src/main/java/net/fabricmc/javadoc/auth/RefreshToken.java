package net.fabricmc.javadoc.auth;

import java.util.UUID;

public record RefreshToken(UUID uuid, AuthPlatform platform, String displayName) {
}
