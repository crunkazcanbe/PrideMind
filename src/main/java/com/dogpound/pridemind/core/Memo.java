package com.dogpound.pridemind.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Answer memory: the same question inside its cache time gets the same answer instantly (least-recently-used, capped). */
final class Memo {
    private static final class Hit { final String answer; final long until; Hit(String a, long u) { answer = a; until = u; } }

    private final int cap;
    private final LinkedHashMap<String, Hit> map;

    Memo(int cap) {
        this.cap = cap;
        this.map = new LinkedHashMap<String, Hit>(1024, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Hit> e) { return size() > Memo.this.cap; }
        };
    }

    synchronized String get(String key) {
        Hit h = map.get(key);
        if (h == null) return null;
        if (h.until < System.currentTimeMillis()) { map.remove(key); return null; }
        return h.answer;
    }

    synchronized void put(String key, String answer, long ttlMs) { map.put(key, new Hit(answer, System.currentTimeMillis() + ttlMs)); }

    synchronized int size() { return map.size(); }

    synchronized void clear() { map.clear(); }
}
