package com.sturgeon.xflipcamera.core;

final class PreviewPhaseStateChecks {
    static int run() {
        int checks = 0;
        PreviewPhaseState state = new PreviewPhaseState();
        check(state.phase() == 0); checks++;
        state.sample(1, false);
        check(state.phase() == 0); checks++;
        state.sample(1, false);
        check(state.phase() == 1); checks++;
        for (int i = 0; i < 20; i++) {
            state.sample(0, false);
            check(state.phase() == 1); checks++;
        }
        state.sample(2, false);
        check(state.phase() == 2); checks++;
        state.sample(0, false);
        check(state.phase() == 2); checks++;
        state.sample(0, true);
        check(state.phase() == 3); checks++;
        state.sample(0, false);
        check(state.phase() == 3); checks++;
        state.sample(1, false);
        check(state.phase() == 0); checks++;
        state.sample(1, false);
        check(state.phase() == 1); checks++;
        state.reset();
        check(state.phase() == 0); checks++;
        state.sample(1, false);
        state.sample(1, false);
        state.sample(4, false);
        check(state.phase() == 4); checks++;
        state.sample(0, false);
        check(state.phase() == 4); checks++;
        state.sample(1, false);
        check(state.phase() == 0); checks++;
        state.sample(1, false);
        check(state.phase() == 1); checks++;
        return checks;
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Preview direction changed without a positive signal");
    }
}
