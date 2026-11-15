package org.geysermc.floodgate.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.junit.jupiter.api.Test;

class SimpleFloodgateApiTest {
    private final Map<UUID, FloodgatePlayer> players = new ConcurrentHashMap<>();
    private final Map<UUID, FloodgatePlayer> pendingRemove = new LinkedHashMap<>();
    private final List<FloodgatePlayer> known = new ArrayList<>();
    private final List<UUID> javaIds = new ArrayList<>();
    private final List<UUID> correctIds = new ArrayList<>();

    @Test
    void getPlayerMatchesOldBehaviour() {
        for (long seed = 0; seed < 200; seed++) {
            run(new Random(seed));
        }
    }

    private void run(Random random) {
        players.clear();
        pendingRemove.clear();
        known.clear();
        javaIds.clear();
        correctIds.clear();
        SimpleFloodgateApi api = new SimpleFloodgateApi();

        for (int step = 0; step < 300; step++) {
            int action = random.nextInt(10);
            if (action < 4 || known.isEmpty()) {
                FloodgatePlayer player = newPlayer(random);
                api.addPlayer(player);
                players.put(player.getJavaUniqueId(), player);
            } else if (action < 8) {
                FloodgatePlayer player = known.get(random.nextInt(known.size()));
                api.setPendingRemove(player);
                pendingRemove.put(player.getJavaUniqueId(), player);
                players.remove(player.getJavaUniqueId(), player);
            } else {
                UUID correctUuid = pick(random, correctIds);
                FloodgatePlayer removed = correctUuid.getMostSignificantBits() == 0
                        ? pendingRemove.get(correctUuid)
                        : api.getPendingRemovePlayer(correctUuid);
                api.playerRemoved(correctUuid);
                if (removed != null) pendingRemove.remove(removed.getJavaUniqueId(), removed);
            }
            assertSame(api, random);
        }
    }

    private void assertSame(SimpleFloodgateApi api, Random random) {
        Set<UUID> queries = new HashSet<>(javaIds);
        queries.addAll(correctIds);
        queries.add(new UUID(random.nextLong() | 1, random.nextLong()));
        queries.add(new UUID(0, random.nextLong()));
        for (UUID uuid : queries) {
            Set<FloodgatePlayer> expected = oldGetPlayer(uuid);
            FloodgatePlayer actual = api.getPlayer(uuid);
            assertTrue(expected.isEmpty() ? actual == null : expected.contains(actual),
                    uuid + " expected " + expected + " got " + actual);
            assertTrue(api.isFloodgatePlayer(uuid) == !expected.isEmpty());
        }
    }

    private Set<FloodgatePlayer> oldGetPlayer(UUID uuid) {
        FloodgatePlayer selfPlayer = players.get(uuid);
        if (selfPlayer != null) return Collections.singleton(selfPlayer);
        if (uuid.getMostSignificantBits() == 0) {
            FloodgatePlayer pending = pendingRemove.get(uuid);
            return pending == null ? Collections.emptySet() : Collections.singleton(pending);
        }
        Set<FloodgatePlayer> matches = matching(players.values(), uuid);
        return matches.isEmpty() ? matching(pendingRemove.values(), uuid) : matches;
    }

    private static Set<FloodgatePlayer> matching(Iterable<FloodgatePlayer> source, UUID uuid) {
        Set<FloodgatePlayer> matches = new HashSet<>();
        for (FloodgatePlayer player : source) {
            if (player.getCorrectUniqueId().equals(uuid)) matches.add(player);
        }
        return matches;
    }

    private FloodgatePlayer newPlayer(Random random) {
        UUID javaUuid = random.nextInt(4) == 0 && !javaIds.isEmpty()
                ? pick(random, javaIds)
                : new UUID(0, random.nextLong());
        UUID correctUuid;
        int kind = random.nextInt(10);
        if (kind < 5) correctUuid = javaUuid;
        else if (kind < 7 && !correctIds.isEmpty()) correctUuid = pick(random, correctIds);
        else if (kind < 8) correctUuid = new UUID(0, random.nextLong());
        else correctUuid = new UUID(random.nextLong() | 1, random.nextLong());

        FloodgatePlayer player = player(javaUuid, correctUuid);
        known.add(player);
        javaIds.add(javaUuid);
        correctIds.add(correctUuid);
        return player;
    }

    private static UUID pick(Random random, List<UUID> uuids) {
        return uuids.get(random.nextInt(uuids.size()));
    }

    private static FloodgatePlayer player(UUID javaUuid, UUID correctUuid) {
        String name = "player(" + javaUuid + " -> " + correctUuid + ")";
        return (FloodgatePlayer) Proxy.newProxyInstance(
                FloodgatePlayer.class.getClassLoader(),
                new Class<?>[]{FloodgatePlayer.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getJavaUniqueId": return javaUuid;
                        case "getCorrectUniqueId": return correctUuid;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return name;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
