package dev.bootshift.web;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal parser for a complete text/event-stream body, enough for tests. */
record SseEvents(List<Event> events) {

    record Event(long id, String name, JsonNode data) {
    }

    static SseEvents parse(String body, JsonMapper json) {
        List<Event> events = new ArrayList<>();
        long id = -1;
        String name = "message";
        StringBuilder data = new StringBuilder();
        for (String line : (body + "\n\n").split("\r?\n", -1)) {
            if (line.isEmpty()) {
                if (!data.isEmpty()) {
                    events.add(new Event(id, name, json.readTree(data.toString())));
                }
                id = -1;
                name = "message";
                data.setLength(0);
            } else if (line.startsWith("id:")) {
                id = Long.parseLong(line.substring(3).trim());
            } else if (line.startsWith("event:")) {
                name = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                data.append(line.substring(5).trim());
            }
        }
        return new SseEvents(events);
    }

    /** The {@code to} state of every STATE_CHANGED event, in order. */
    List<String> states() {
        return events.stream()
                .filter(e -> e.name().equals("STATE_CHANGED"))
                .map(e -> e.data().get("payload").get("to").asString())
                .toList();
    }
}
