package com.dogpound.pridemind.mob;

import java.util.Arrays;
import java.util.List;

/** What a mob can decide. The brain only picks one; vanilla AI and IntentTask carry it out. */
enum Intent {
    ATTACK, STALK, FLEE, CALL_HELP, GROUP_UP, IGNORE;

    static final List<String> HOSTILE = Arrays.asList("ATTACK", "STALK", "FLEE", "CALL_HELP", "GROUP_UP", "IGNORE");
    static final List<String> PASSIVE = Arrays.asList("FLEE", "GROUP_UP", "IGNORE");

    boolean moves() { return this == STALK || this == FLEE || this == GROUP_UP; }

    /** built-in reflexes: the answer when no model is connected or it's too busy — already smarter than vanilla */
    static Intent reflex(Situation s) {
        if (!s.hostile) {
            if (s.reason.startsWith("got hurt")) return FLEE;
            if (s.group >= 2 && s.dist <= 12 && !"food".equals(s.held)) return GROUP_UP;
            return IGNORE;
        }
        if (s.hp <= 25) return s.group >= 3 ? CALL_HELP : FLEE;           // badly hurt: run, or bring the horde
        if (s.playerHp <= 25) return ATTACK;                              // the player is nearly dead: press on
        if (s.reason.startsWith("got hurt") && s.group >= 2) return CALL_HELP;
        if (s.hp <= 50 && !"nothing".equals(s.held) && s.group == 0) return STALK;   // armed player, alone and hurt: wait for a chance
        if (!s.night && s.dist > 12) return STALK;
        return ATTACK;
    }
}
