package me.JAKOP.hinexFeatures.mod.deathgame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class TombLevel {

    public static final int UP = 0;
    public static final int RIGHT = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 3;
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DY = {1, 0, -1, 0};

    public static final int STOP = 0;
    public static final int EXIT = 1;
    public static final int DEATH = 2;
    public static final int NONE = 3;

    public static final int WIDTH = 15;

    public final int width;
    public final int height;
    private final boolean[] open;
    private final byte[] spikes;
    private final byte[] traps;
    private final float[] trapPhase;
    private final boolean[] dots;
    public final int startX;
    public final int startY;
    public final int exitX;
    public final int exitY;
    public final int dotCount;

    private TombLevel(int width, int height, boolean[] open, byte[] spikes, byte[] traps, float[] trapPhase, boolean[] dots,
                      int startX, int startY, int exitX, int exitY) {
        this.width = width;
        this.height = height;
        this.open = open;
        this.spikes = spikes;
        this.traps = traps;
        this.trapPhase = trapPhase;
        this.dots = dots;
        this.startX = startX;
        this.startY = startY;
        this.exitX = exitX;
        this.exitY = exitY;
        int count = 0;
        for (boolean dot : dots) if (dot) count++;
        this.dotCount = count;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public boolean open(int x, int y) {
        return inside(x, y) && open[x + y * width];
    }

    public boolean spike(int x, int y, int dir) {
        return inside(x, y) && (spikes[x + y * width] & (1 << dir)) != 0;
    }

    public boolean trap(int x, int y, int dir) {
        return inside(x, y) && (traps[x + y * width] & (1 << dir)) != 0;
    }

    public boolean hasTrap(int x, int y) {
        return inside(x, y) && traps[x + y * width] != 0;
    }

    public float trapPhase(int x, int y) {
        return inside(x, y) ? trapPhase[x + y * width] : 0.0F;
    }

    public boolean dot(int x, int y) {
        return inside(x, y) && dots[x + y * width];
    }

    public boolean isExit(int x, int y) {
        return x == exitX && y == exitY;
    }

    public int[] slide(int x, int y, int dir) {
        int cx = x;
        int cy = y;
        while (open(cx + DX[dir], cy + DY[dir])) {
            cx += DX[dir];
            cy += DY[dir];
            if (isExit(cx, cy)) return new int[]{cx, cy, EXIT};
        }
        if (cx == x && cy == y) return new int[]{x, y, NONE};
        return new int[]{cx, cy, spike(cx, cy, dir) ? DEATH : STOP};
    }

    public List<Integer> solve(int fromX, int fromY) {
        int cells = width * height;
        int[] previous = new int[cells];
        int[] via = new int[cells];
        Arrays.fill(previous, -2);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = fromX + fromY * width;
        previous[start] = -1;
        queue.add(start);
        while (!queue.isEmpty()) {
            int state = queue.poll();
            int x = state % width;
            int y = state / width;
            for (int dir = 0; dir < 4; dir++) {
                int[] result = slide(x, y, dir);
                if (result[2] == NONE || result[2] == DEATH) continue;
                int next = result[0] + result[1] * width;
                if (previous[next] != -2) continue;
                previous[next] = state;
                via[next] = dir;
                if (result[2] == EXIT) {
                    List<Integer> path = new ArrayList<>();
                    for (int cur = next; previous[cur] != -1; cur = previous[cur]) path.addFirst(via[cur]);
                    return path;
                }
                queue.add(next);
            }
        }
        return List.of();
    }

    public record Difficulty(String name, double spikeChance, double trapChance, double branches,
                             float tideBase, float tideAccel, float tideGap, float grace) {

        public static Difficulty of(String name) {
            return switch (name) {
                case "easy" -> new Difficulty("easy", 0.07, 0.03, 0.030, 0.8F, 0.012F, 12.0F, 3.5F);
                case "hard" -> new Difficulty("hard", 0.20, 0.08, 0.042, 1.35F, 0.030F, 7.0F, 2.5F);
                default -> new Difficulty("normal", 0.13, 0.05, 0.036, 1.05F, 0.020F, 9.0F, 3.0F);
            };
        }
    }

    public static TombLevel generate(long seed, int length, Difficulty difficulty) {
        Random random = new Random(seed);
        int height = Math.max(24, length) + 2;
        TombLevel best = null;
        int bestTraps = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 80; attempt++) {
            Builder builder = new Builder(WIDTH, height, random);
            if (!builder.carveMain()) continue;
            builder.carveBranches(difficulty.branches);
            TombLevel level = builder.decorate(difficulty);
            int traps = level.countTraps();
            if (traps < 0) continue;
            if (level.solve(level.startX, level.startY).size() < height / 7) continue;
            if (traps == 0) return level;
            if (traps < bestTraps) {
                best = level;
                bestTraps = traps;
            }
        }
        if (best != null) return best;
        Builder builder = new Builder(WIDTH, height, random);
        builder.straight();
        return builder.decorate(new Difficulty("easy", 0, 0, 0, 0.8F, 0.012F, 12.0F, 3.5F));
    }

    private int countTraps() {
        int cells = width * height;
        boolean[] reached = new boolean[cells];
        List<int[]> edges = new ArrayList<>();
        List<Integer> winners = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = startX + startY * width;
        reached[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int state = queue.poll();
            for (int dir = 0; dir < 4; dir++) {
                int[] result = slide(state % width, state / width, dir);
                if (result[2] == NONE || result[2] == DEATH) continue;
                if (result[2] == EXIT) {
                    winners.add(state);
                    continue;
                }
                int next = result[0] + result[1] * width;
                edges.add(new int[]{state, next});
                if (!reached[next]) {
                    reached[next] = true;
                    queue.add(next);
                }
            }
        }
        if (winners.isEmpty()) return -1;
        List<List<Integer>> reverse = new ArrayList<>(cells);
        for (int i = 0; i < cells; i++) reverse.add(null);
        for (int[] edge : edges) {
            List<Integer> list = reverse.get(edge[1]);
            if (list == null) reverse.set(edge[1], list = new ArrayList<>(2));
            list.add(edge[0]);
        }
        boolean[] winning = new boolean[cells];
        for (int winner : winners) {
            if (!winning[winner]) {
                winning[winner] = true;
                queue.add(winner);
            }
        }
        while (!queue.isEmpty()) {
            List<Integer> from = reverse.get(queue.poll());
            if (from == null) continue;
            for (int state : from) {
                if (!winning[state]) {
                    winning[state] = true;
                    queue.add(state);
                }
            }
        }
        if (!winning[start]) return -1;
        int traps = 0;
        for (int i = 0; i < cells; i++) if (reached[i] && !winning[i]) traps++;
        return traps;
    }

    private static final class Builder {
        final int width;
        final int height;
        final Random random;
        final boolean[] open;
        final boolean[] locked;
        final boolean[] path;
        final List<int[]> stops = new ArrayList<>();
        int startX;
        int startY;
        int exitX;
        int exitY;

        Builder(int width, int height, Random random) {
            this.width = width;
            this.height = height;
            this.random = random;
            this.open = new boolean[width * height];
            this.locked = new boolean[width * height];
            this.path = new boolean[width * height];
        }

        boolean interior(int x, int y) {
            return x >= 1 && y >= 1 && x <= width - 2 && y <= height - 2;
        }

        int index(int x, int y) {
            return x + y * width;
        }

        boolean isOpen(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && open[index(x, y)];
        }

        void carve(int x, int y) {
            open[index(x, y)] = true;
        }

        boolean carveMain() {
            startX = width / 2;
            startY = 1;
            carve(startX, startY);
            path[index(startX, startY)] = true;
            stops.add(new int[]{startX, startY, -1});
            int x = startX;
            int y = startY;
            int last = -1;
            int failures = 0;
            for (int guard = 0; guard < 900; guard++) {
                if (y >= height - 6 && lineFree(x, y, height - 2 - y)) {
                    for (int yy = y + 1; yy <= height - 2; yy++) {
                        carve(x, yy);
                        path[index(x, yy)] = true;
                    }
                    exitX = x;
                    exitY = height - 2;
                    return true;
                }
                int dir = pickDirection(last, y);
                int length = pickLength(x, y, dir);
                if (length < 0) {
                    if (++failures > 60) return false;
                    continue;
                }
                for (int k = 1; k <= length; k++) {
                    carve(x + DX[dir] * k, y + DY[dir] * k);
                    path[index(x + DX[dir] * k, y + DY[dir] * k)] = true;
                }
                x += DX[dir] * length;
                y += DY[dir] * length;
                int sx = x + DX[dir];
                int sy = y + DY[dir];
                if (interior(sx, sy)) locked[index(sx, sy)] = true;
                stops.add(new int[]{x, y, dir});
                last = dir;
            }
            return false;
        }

        boolean lineFree(int x, int y, int length) {
            for (int k = 1; k <= length; k++) {
                if (locked[index(x, y + k)]) return false;
            }
            return true;
        }

        int pickDirection(int last, int y) {
            double up = 4.0;
            double side = 2.3;
            double down = y > 6 ? 0.8 : 0.0;
            double[] weights = {up, side, down, side};
            if (last >= 0) {
                weights[last] = 0.0;
                weights[(last + 2) % 4] = 0.0;
            }
            double total = 0;
            for (double w : weights) total += w;
            double roll = random.nextDouble() * total;
            for (int dir = 0; dir < 4; dir++) {
                roll -= weights[dir];
                if (roll < 0) return dir;
            }
            return UP;
        }

        int pickLength(int x, int y, int dir) {
            int max = dir == UP ? 7 : dir == DOWN ? 4 : 11;
            List<Integer> good = new ArrayList<>();
            List<Integer> shortOnes = new ArrayList<>();
            for (int length = 1; length <= max; length++) {
                int cx = x + DX[dir] * length;
                int cy = y + DY[dir] * length;
                if (!interior(cx, cy) || locked[index(cx, cy)]) break;
                int sx = cx + DX[dir];
                int sy = cy + DY[dir];
                if (interior(sx, sy) && isOpen(sx, sy)) continue;
                if (length >= 2) good.add(length);
                else shortOnes.add(length);
            }
            List<Integer> options = good.isEmpty() ? shortOnes : good;
            if (options.isEmpty()) return -1;
            int a = options.get(random.nextInt(options.size()));
            int b = options.get(random.nextInt(options.size()));
            return Math.abs(a - 4) <= Math.abs(b - 4) ? a : b;
        }

        void carveBranches(double density) {
            int count = (int) Math.round(width * height * density);
            List<Integer> cells = new ArrayList<>();
            for (int i = 0; i < open.length; i++) if (open[i]) cells.add(i);
            for (int n = 0; n < count && !cells.isEmpty(); n++) {
                int from = cells.get(random.nextInt(cells.size()));
                int x = from % width;
                int y = from / width;
                if (y >= height - 4) continue;
                int dir = random.nextInt(4);
                int length = 2 + random.nextInt(dir == UP || dir == DOWN ? 2 : 4);
                for (int k = 1; k <= length; k++) {
                    int cx = x + DX[dir] * k;
                    int cy = y + DY[dir] * k;
                    if (!interior(cx, cy) || locked[index(cx, cy)] || cy >= height - 3) break;
                    carve(cx, cy);
                    cells.add(index(cx, cy));
                    x = cx;
                    y = cy;
                }
                if (random.nextFloat() < 0.18F) {
                    int px = x + (random.nextBoolean() ? 1 : -1);
                    int py = y + (random.nextBoolean() ? 1 : -1);
                    for (int[] cell : new int[][]{{px, y}, {x, py}, {px, py}}) {
                        if (interior(cell[0], cell[1]) && !locked[index(cell[0], cell[1])] && cell[1] < height - 3) {
                            carve(cell[0], cell[1]);
                        }
                    }
                }
            }
        }

        void straight() {
            Arrays.fill(open, false);
            startX = width / 2;
            startY = 1;
            for (int y = 1; y <= height - 2; y++) carve(startX, y);
            exitX = startX;
            exitY = height - 2;
        }

        TombLevel decorate(Difficulty difficulty) {
            int cells = width * height;
            byte[] spikes = new byte[cells];
            byte[] traps = new byte[cells];
            float[] phase = new float[cells];
            boolean[] dots = new boolean[cells];
            boolean[] safeFace = new boolean[cells * 4];
            boolean[] restCell = new boolean[cells];
            for (int[] stop : stops) {
                restCell[index(stop[0], stop[1])] = true;
                if (stop[2] >= 0) safeFace[index(stop[0], stop[1]) * 4 + stop[2]] = true;
            }
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    int i = index(x, y);
                    if (!open[i] || (x == exitX && y == exitY)) continue;
                    boolean nearStart = y <= startY + 3;
                    for (int dir = 0; dir < 4; dir++) {
                        if (isOpen(x + DX[dir], y + DY[dir]) || safeFace[i * 4 + dir] || nearStart) continue;
                        if (y >= height - 3) continue;
                        double roll = random.nextDouble();
                        if (roll < difficulty.spikeChance) {
                            spikes[i] |= (byte) (1 << dir);
                        } else if (!restCell[i] && roll < difficulty.spikeChance + difficulty.trapChance) {
                            traps[i] |= (byte) (1 << dir);
                            phase[i] = random.nextFloat();
                        }
                    }
                    boolean isStart = x == startX && y == startY;
                    if (!isStart) dots[i] = random.nextFloat() < (path[i] ? 0.7F : 0.45F);
                }
            }
            return new TombLevel(width, height, open.clone(), spikes, traps, phase, dots, startX, startY, exitX, exitY);
        }
    }
}
