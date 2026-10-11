package me.JAKOP.hinexFeatures.mod.deathgame;

import java.util.List;

public final class TombGame {

    public enum State { READY, RUNNING, WON, DEAD }

    public enum Death { SPIKES, TRAP, TIDE, TIME }

    public interface Listener {
        void onMove(int dir);

        void onImpact(int dir, int x, int y, boolean bump);

        void onDot(int x, int y);

        void onDeath(Death death);

        void onWin();
    }

    public static final float SPEED = 21.0F;
    public static final float TRAP_PERIOD = 1.7F;
    public static final float TRAP_OUT = 0.38F;

    public final TombLevel level;
    public final TombLevel.Difficulty difficulty;
    public final float timeLimit;
    private final Listener listener;
    private final boolean[] taken;

    private State state = State.READY;
    private Death death;
    private float time;
    private float px;
    private float py;
    private int cellX;
    private int cellY;
    private boolean moving;
    private int moveDir = -1;
    private int targetX;
    private int targetY;
    private int queued = -1;
    private float tide;
    private int maxY;
    private int collected;
    private int moves;

    public TombGame(TombLevel level, TombLevel.Difficulty difficulty, float timeLimit, Listener listener) {
        this.level = level;
        this.difficulty = difficulty;
        this.timeLimit = timeLimit;
        this.listener = listener;
        this.taken = new boolean[level.width * level.height];
        this.cellX = level.startX;
        this.cellY = level.startY;
        this.px = cellX;
        this.py = cellY;
        this.maxY = cellY;
        this.tide = level.startY - 4.5F;
    }

    public void start() {
        if (state == State.READY) state = State.RUNNING;
    }

    public void input(int dir) {
        if (state != State.RUNNING) return;
        if (moving) {
            queued = dir;
        } else {
            tryMove(dir);
        }
    }

    public void update(float dt) {
        if (state != State.RUNNING) return;
        while (dt > 0.0F && state == State.RUNNING) {
            float step = Math.min(dt, 1.0F / 120.0F);
            dt -= step;
            step(step);
        }
    }

    private void step(float dt) {
        time += dt;
        if (moving) advance(SPEED * dt);
        if (state != State.RUNNING) return;

        if (!moving && trapOut(cellX, cellY, time)) {
            die(Death.TRAP);
            return;
        }

        if (time > difficulty.grace()) {
            float gap = py - tide;
            float speed = difficulty.tideBase() + difficulty.tideAccel() * (time - difficulty.grace());
            if (gap > difficulty.tideGap()) speed *= 1.0F + (gap - difficulty.tideGap()) * 0.45F;
            tide += speed * dt;
        }
        if (tide >= py + 0.05F) {
            die(Death.TIDE);
        } else if (time >= timeLimit) {
            die(Death.TIME);
        }
    }

    private void advance(float distance) {
        while (distance > 0.0F && moving && state == State.RUNNING) {
            int nx = cellX + TombLevel.DX[moveDir];
            int ny = cellY + TombLevel.DY[moveDir];
            float toNext = Math.abs(nx - px) + Math.abs(ny - py);
            if (distance < toNext) {
                px += TombLevel.DX[moveDir] * distance;
                py += TombLevel.DY[moveDir] * distance;
                return;
            }
            distance -= toNext;
            px = nx;
            py = ny;
            cellX = nx;
            cellY = ny;
            enterCell();
            if (state == State.RUNNING && cellX == targetX && cellY == targetY) arrive();
        }
    }

    private void enterCell() {
        maxY = Math.max(maxY, cellY);
        int index = cellX + cellY * level.width;
        if (level.dot(cellX, cellY) && !taken[index]) {
            taken[index] = true;
            collected++;
            listener.onDot(cellX, cellY);
        }
        if (level.isExit(cellX, cellY)) {
            state = State.WON;
            moving = false;
            listener.onWin();
            return;
        }
        if (trapOut(cellX, cellY, time)) die(Death.TRAP);
    }

    private void arrive() {
        moving = false;
        moves++;
        boolean spiked = level.spike(cellX, cellY, moveDir);
        listener.onImpact(moveDir, cellX, cellY, false);
        if (spiked) {
            die(Death.SPIKES);
            return;
        }
        if (queued >= 0) {
            int next = queued;
            queued = -1;
            tryMove(next);
        }
    }

    private void tryMove(int dir) {
        int[] result = level.slide(cellX, cellY, dir);
        if (result[2] == TombLevel.NONE) {
            listener.onImpact(dir, cellX, cellY, true);
            return;
        }
        moving = true;
        moveDir = dir;
        targetX = result[0];
        targetY = result[1];
        listener.onMove(dir);
    }

    public void forceDeath() {
        if (state == State.READY) state = State.RUNNING;
        die(Death.TIME);
    }

    private void die(Death cause) {
        if (state != State.RUNNING) return;
        state = State.DEAD;
        death = cause;
        moving = false;
        listener.onDeath(cause);
    }

    public float trapExtension(int x, int y, float at) {
        if (!level.hasTrap(x, y)) return 0.0F;
        float cycle = at / TRAP_PERIOD + level.trapPhase(x, y);
        float phase = cycle - (float) Math.floor(cycle);
        if (phase < TRAP_OUT) return Math.min(1.0F, phase / 0.05F);
        if (phase > 0.9F) return (phase - 0.9F) / 0.1F * 0.25F;
        return 0.0F;
    }

    public boolean trapOut(int x, int y, float at) {
        if (!level.hasTrap(x, y)) return false;
        float cycle = at / TRAP_PERIOD + level.trapPhase(x, y);
        return cycle - (float) Math.floor(cycle) < TRAP_OUT;
    }

    public int autopilot() {
        if (state != State.RUNNING || moving) return -1;
        List<Integer> path = level.solve(cellX, cellY);
        if (path.isEmpty()) return -1;
        int dir = path.getFirst();
        int x = cellX;
        int y = cellY;
        float at = time;
        while (level.open(x + TombLevel.DX[dir], y + TombLevel.DY[dir])) {
            x += TombLevel.DX[dir];
            y += TombLevel.DY[dir];
            at += 1.0F / SPEED;
            if (trapOut(x, y, at) || trapOut(x, y, at + 0.03F)) return -1;
            if (level.isExit(x, y)) break;
        }
        return dir;
    }

    public State state() {
        return state;
    }

    public Death death() {
        return death;
    }

    public float time() {
        return time;
    }

    public float remaining() {
        return Math.max(0.0F, timeLimit - time);
    }

    public float x() {
        return px;
    }

    public float y() {
        return py;
    }

    public int cellX() {
        return cellX;
    }

    public int cellY() {
        return cellY;
    }

    public boolean moving() {
        return moving;
    }

    public int moveDir() {
        return moveDir;
    }

    public float tide() {
        return tide;
    }

    public boolean taken(int x, int y) {
        return taken[x + y * level.width];
    }

    public int collected() {
        return collected;
    }

    public int moves() {
        return moves;
    }

    public float progress() {
        if (state == State.WON) return 1.0F;
        float span = Math.max(1, level.exitY - level.startY);
        return Math.max(0.0F, Math.min(1.0F, (maxY - level.startY) / span));
    }
}
