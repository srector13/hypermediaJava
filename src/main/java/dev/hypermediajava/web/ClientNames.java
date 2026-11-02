package dev.hypermediajava.web;

import java.util.List;

/** Friendly, deterministic names for anonymous browser tabs ("Swift Otter") so the activity feed reads well. */
public final class ClientNames {

    private static final List<String> ADJECTIVES = List.of(
            "Swift", "Quiet", "Brave", "Clever", "Sunny", "Lucky", "Calm", "Bold", "Witty", "Nimble",
            "Cosmic", "Gentle", "Rapid", "Mellow", "Zesty", "Fuzzy");
    private static final List<String> ANIMALS = List.of(
            "Otter", "Falcon", "Panda", "Lynx", "Heron", "Koala", "Fox", "Orca", "Badger", "Gecko",
            "Puffin", "Bison", "Marten", "Ibis", "Yak", "Wombat");

    private ClientNames() {
    }

    public static String of(String clientId) {
        if (clientId == null || clientId.isBlank()) return "Someone";
        int h = Math.abs(clientId.hashCode());
        return ADJECTIVES.get(h % ADJECTIVES.size()) + " " + ANIMALS.get((h / 31) % ANIMALS.size());
    }
}
