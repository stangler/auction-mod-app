package com.example.auction.market;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MobConstants {

    private MobConstants() {}

    public static final String[] MOB_NAMES = {
        "村人A", "村人B", "村人C", "村人D", "村人E",
        "行商人", "ウィッチ", "略奪者", "ピリジャー"
    };

    private static final Set<String> MOB_NAME_SET =
        Arrays.stream(MOB_NAMES).collect(Collectors.toUnmodifiableSet());

    public static UUID mobUUID(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    public static boolean isMobName(String name) {
        return MOB_NAME_SET.contains(name);
    }
}
