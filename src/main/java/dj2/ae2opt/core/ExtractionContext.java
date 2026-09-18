package dj2.ae2opt.core;


public final class ExtractionContext {

    private static final ThreadLocal<State> STATE = new ThreadLocal<State>() {
        @Override
        protected State initialValue() {
            return new State();
        }
    };

    private ExtractionContext() {
    }

    public static long enterPoweredExtraction() {
        State state = STATE.get();
        long id = ++state.nextId;
        if (id == 0L) {
            id = ++state.nextId;
        }
        state.push(id);
        return id;
    }

    public static void exitPoweredExtraction() {
        STATE.get().pop();
    }

    public static long currentPoweredExtractionId() {
        return STATE.get().current();
    }


    public static int resetAtServerTickEnd() {
        return STATE.get().resetDepth();
    }

    private static final class State {
        private long nextId;
        private long[] stack = new long[4];
        private int depth;

        private void push(long id) {
            if (this.depth == this.stack.length) {
                long[] grown = new long[this.stack.length * 2];
                System.arraycopy(this.stack, 0, grown, 0, this.stack.length);
                this.stack = grown;
            }
            this.stack[this.depth++] = id;
        }

        private void pop() {
            if (this.depth > 0) {
                this.depth--;
            }
        }

        private long current() {
            return this.depth == 0 ? 0L : this.stack[this.depth - 1];
        }

        private int resetDepth() {
            int old = this.depth;
            this.depth = 0;
            return old;
        }
    }
}
