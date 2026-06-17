import java.util.*;

class Client {
    private final HttpSnakeFieldAPI api;
    private final String teamName;

    public Client(String serverUrl, String teamName, String gameName, String password) {
        this.teamName = teamName;
        this.api = new HttpSnakeFieldAPI(serverUrl, teamName, gameName, password);
    }

    public void run() {
        try {
            Direction currentDirection = Direction.EAST;
            api.setDirection(currentDirection);

            while (true) {
                try { Thread.sleep(500); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }

                GameField field = api.getField();

                Direction next = findBestDirection(field, currentDirection);
                if (next == null || isOpposite(next, currentDirection)) {
                    next = currentDirection;
                }
                api.setDirection(next);
                currentDirection = next;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Direction getTrueDirection(Snake mySnake, Size fieldSize) {
        if (mySnake.body().size() < 2) return Direction.EAST;
        Position head = mySnake.body().get(0);
        Position neck = mySnake.body().get(1);

        int dx = head.x() - neck.x();
        int dy = head.y() - neck.y();

        if (dx > fieldSize.width() / 2) dx -= fieldSize.width();
        if (dx < -fieldSize.width() / 2) dx += fieldSize.width();
        if (dy > fieldSize.height() / 2) dy -= fieldSize.height();
        if (dy < -fieldSize.height() / 2) dy += fieldSize.height();

        if (dx == 1) return Direction.EAST;
        if (dx == -1) return Direction.WEST;
        if (dy == 1) return Direction.SOUTH;
        if (dy == -1) return Direction.NORTH;

        return Direction.EAST;
    }

    private boolean isOpposite(Direction a, Direction b) {
        return (a == Direction.NORTH && b == Direction.SOUTH) ||
                (a == Direction.SOUTH && b == Direction.NORTH) ||
                (a == Direction.EAST  && b == Direction.WEST)  ||
                (a == Direction.WEST  && b == Direction.EAST);
    }

    private Direction findBestDirection(GameField field, Direction currentDirection) {
        Snake mySnake = getMySnake(field);
        if (mySnake == null || mySnake.body().isEmpty()) return currentDirection;

        Position head = mySnake.body().get(0);
        int bodySize = mySnake.body().size();
        int threshold = Math.max(6, bodySize / 3);

        Direction trueDirection = getTrueDirection(mySnake, field.size());

        int longestEnemy = field.snakesPerTeamName().values().stream()
                .filter(s -> s != mySnake)
                .mapToInt(s -> s.body().size())
                .max().orElse(0);
        boolean needToGrow = bodySize <= longestEnemy;

        List<Item> apples = field.items().stream()
                .filter(i -> "apple".equalsIgnoreCase(i.type()))
                .sorted(Comparator.comparingInt(a -> distance(head, a.position(), field.size())))
                .toList();

        int W = field.size().width();
        int H = field.size().height();
        int maxCells = W * H;

        Direction best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Direction dir : Direction.values()) {
            if (isOpposite(dir, trueDirection)) continue;
            if (!isSafeMove(head, dir, field, mySnake)) continue;

            Position next = getNextPosition(head, dir, field.size());
            List<Position> simBody = simulateMove(mySnake.body(), next);

            int flood1 = floodFillWithTail(next, simBody, field, mySnake, maxCells);
            if (flood1 < threshold) continue;

            int worstFlood = flood1;
            boolean anyNextSafe = false;

            for (Direction dir2 : Direction.values()) {
                if (isOpposite(dir2, dir)) continue;
                Position next2 = getNextPosition(next, dir2, field.size());
                if (isBodyCollision(next2, simBody)) continue;
                if (isEnemyCollision(next2, field, mySnake)) continue;

                List<Position> body2 = simulateMove(simBody, next2);
                int flood2 = floodFillWithTail(next2, body2, field, mySnake, maxCells);
                if (flood2 < threshold) continue;
                anyNextSafe = true;

                for (Direction dir3 : Direction.values()) {
                    if (isOpposite(dir3, dir2)) continue;
                    Position next3 = getNextPosition(next2, dir3, field.size());
                    if (isBodyCollision(next3, body2)) continue;
                    if (isEnemyCollision(next3, field, mySnake)) continue;

                    List<Position> body3 = simulateMove(body2, next3);
                    int flood3 = floodFillWithTail(next3, body3, field, mySnake, maxCells);
                    worstFlood = Math.min(worstFlood, flood3);
                }
            }

            if (!anyNextSafe) continue;
            if (worstFlood < threshold) continue;

            double score = worstFlood * 5.0 + flood1 * 2.0;

            if (!apples.isEmpty()) {
                int appleDist = distance(next, apples.get(0).position(), field.size());
                if (needToGrow && flood1 > bodySize) {
                    score += 30.0 / (appleDist + 1);
                } else if (!needToGrow && flood1 > bodySize * 3) {
                    score += 10.0 / (appleDist + 1);
                }
            }

            for (Snake enemy : field.snakesPerTeamName().values()) {
                if (enemy == mySnake || enemy.body().isEmpty()) continue;
                int hd = distance(next, enemy.body().get(0), field.size());
                if (hd == 1) score -= 500.0;
                else if (hd == 2) score -= 100.0;
                else if (hd == 3) score -= 30.0;
                else if (hd == 4) score -= 10.0;
            }

            for (Snake enemy : field.snakesPerTeamName().values()) {
                if (enemy == mySnake) continue;
                for (Position seg : enemy.body()) {
                    int d = distance(next, seg, field.size());
                    if (d == 1) score -= 15.0;
                    else if (d == 2) score -= 5.0;
                }
            }

            int distToEdgeX = Math.min(next.x(), W - 1 - next.x());
            int distToEdgeY = Math.min(next.y(), H - 1 - next.y());
            if (distToEdgeX == 0 || distToEdgeY == 0) score -= 20.0;
            else if (distToEdgeX == 1 || distToEdgeY == 1) score -= 8.0;

            if (score > bestScore) {
                bestScore = score;
                best = dir;
            }
        }


        if (best == null) {
            int maxFlood = -1;
            for (Direction dir : Direction.values()) {
                if (isOpposite(dir, trueDirection)) continue;
                if (!isSafeMove(head, dir, field, mySnake)) continue;
                Position next = getNextPosition(head, dir, field.size());
                List<Position> simBody = simulateMove(mySnake.body(), next);
                int f = floodFillWithTail(next, simBody, field, mySnake, maxCells);
                if (f > maxFlood) { maxFlood = f; best = dir; }
            }
        }

        return best != null ? best : trueDirection; // Im Zweifel geradeaus weiter, aber NIEMALS rückwärts
    }

    private int floodFillWithTail(Position start, List<Position> ownBody,
                                  GameField field, Snake mySnake, int maxCells) {
        Set<Position> blocked = new HashSet<>();
        for (int i = 0; i < ownBody.size() - 1; i++) blocked.add(ownBody.get(i));
        for (Snake snake : field.snakesPerTeamName().values()) {
            if (snake == mySnake) continue;
            List<Position> eb = snake.body();
            for (int i = 0; i < eb.size() - 1; i++) blocked.add(eb.get(i));
        }
        if (blocked.contains(start)) return 0;

        Queue<Position> queue = new LinkedList<>();
        Set<Position> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        int count = 0;
        while (!queue.isEmpty() && count < maxCells) {
            Position cur = queue.poll();
            count++;
            for (Direction dir : Direction.values()) {
                Position next = getNextPosition(cur, dir, field.size());
                if (!visited.contains(next) && !blocked.contains(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return count;
    }

    private boolean isSafeMove(Position head, Direction dir, GameField field, Snake mySnake) {
        Position next = getNextPosition(head, dir, field.size());


        List<Position> body = mySnake.body();
        for (int i = 0; i < body.size() - 1; i++) {
            if (next.equals(body.get(i))) return false;
        }


        for (Snake snake : field.snakesPerTeamName().values()) {
            if (snake == mySnake) continue;
            List<Position> eb = snake.body();
            for (int i = 0; i < eb.size() - 1; i++) {
                if (next.equals(eb.get(i))) return false;
            }
        }
        return true;
    }

    private boolean isEnemyCollision(Position pos, GameField field, Snake mySnake) {
        for (Snake snake : field.snakesPerTeamName().values()) {
            if (snake == mySnake) continue;
            List<Position> eb = snake.body();
            for (int i = 0; i < eb.size() - 1; i++) {
                if (pos.equals(eb.get(i))) return true;
            }
        }
        return false;
    }

    private List<Position> simulateMove(List<Position> body, Position newHead) {
        List<Position> result = new ArrayList<>();
        result.add(newHead);
        for (int i = 0; i < body.size() - 1; i++) result.add(body.get(i));
        return result;
    }

    private boolean isBodyCollision(Position pos, List<Position> body) {
        for (int i = 0; i < body.size() - 1; i++) {
            if (pos.equals(body.get(i))) return true;
        }
        return false;
    }

    private Snake getMySnake(GameField field) {
        return field.snakesPerTeamName().get(teamName);
    }

    private Position getNextPosition(Position current, Direction dir, Size fieldSize) {
        return switch (dir) {
            case NORTH -> new Position(current.x(), (current.y() - 1 + fieldSize.height()) % fieldSize.height());
            case SOUTH -> new Position(current.x(), (current.y() + 1) % fieldSize.height());
            case EAST  -> new Position((current.x() + 1) % fieldSize.width(), current.y());
            case WEST  -> new Position((current.x() - 1 + fieldSize.width()) % fieldSize.width(), current.y());
        };
    }

    private int distance(Position a, Position b, Size fieldSize) {
        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());
        return Math.min(dx, fieldSize.width() - dx) + Math.min(dy, fieldSize.height() - dy);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Client <team_name> <game_name> [password] [server_url]");
            return;
        }
        String teamName = args[0];
        String gameName = args[1];
        String password = args.length > 2 ? args[2] : "test";
        String serverUrl = args.length > 3 ? args[3] : "http://localhost:3030";
        new Client(serverUrl, teamName, gameName, password).run();
    }
}
