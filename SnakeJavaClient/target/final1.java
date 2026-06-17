import java.util.*;

class Client {

    private final HttpSnakeFieldAPI api;
    private final String teamName;

    // ─── CODE 1: STERN-VARIABLEN ───
    private int starStepsLeft = 0;

    // ─── CODE 2: ANGRIFFS- & LÄNGEN-VARIABLEN ───
    private static final int ATTACK_ITEM_TICKS = 0;
    private int attackItemTicks = 0;

    private boolean attackPreparedForNextTick = false;
    private boolean alreadyCutWithAttackItem = false;

    /*
     * Für diese InstantStack-Challenge:
     * Es gibt keine Bad Apples.
     * Deshalb setzen wir das sehr hoch.
     */
    private static final int MAX_EXTRA_LENGTH = 999;
    private int startLength = -1;
    private boolean wantsBadApple = false;

    public Client(String serverUrl, String teamName, String gameName, String password) {
        this.api = new HttpSnakeFieldAPI(serverUrl, teamName, gameName, password);
        this.teamName = teamName;
    }

    public void run() {
        try {
            api.setDirection(Direction.EAST);

            while (true) {
                Thread.sleep(500);

                GameField field = api.getField();
                Snake mySnake = field.snakesPerTeamName().get(teamName);

                // ─── CODE 1: STERN-LOGIK ÜBER INVENTAR/EFFEKT ───
                if (mySnake != null && activateStarIfPossible(mySnake)) {
                    starStepsLeft = 3; // EXAKT 3 SCHRITTE!
                }
                if (mySnake != null && hasActiveStar(mySnake)) {
                    starStepsLeft = Math.max(starStepsLeft, 3);
                }

                if (mySnake != null && mySnake.body() != null && !mySnake.body().isEmpty()) {
                    updateLengthMode(mySnake);

                    if (attackPreparedForNextTick) {
                        attackItemTicks = ATTACK_ITEM_TICKS;
                        attackPreparedForNextTick = false;
                        alreadyCutWithAttackItem = false;
                        System.out.println("ANGRIFFS-ITEM IST JETZT AKTIV!");
                    }

                    System.out.println("Length: " + mySnake.body().size());
                    System.out.println("StartLength: " + startLength);
                    System.out.println("TargetLength: " + (startLength + MAX_EXTRA_LENGTH));
                    System.out.println("WantsBadApple: " + wantsBadApple);
                    System.out.println("AttackItemTicks: " + attackItemTicks);
                    System.out.println("AttackPreparedForNextTick: " + attackPreparedForNextTick);
                    System.out.println("AlreadyCutWithAttackItem: " + alreadyCutWithAttackItem);
                    System.out.println("Inventory: " + mySnake.inventory());
                    System.out.println("Active Effects: " + mySnake.activeEffects());
                }

                Direction nextDirection = decideDirection(field);

                api.setDirection(nextDirection);

                // ─── TICK-REDUZIERUNG ───
                if (starStepsLeft > 0) {
                    starStepsLeft--;
                }

                if (attackItemTicks > 0) {
                    attackItemTicks--;
                    if (attackItemTicks == 0) {
                        alreadyCutWithAttackItem = false;
                    }
                }

                System.out.println("Richtung: " + nextDirection + " | STAR-SCHRITTE: " + starStepsLeft);
                System.out.println("--------------------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateLengthMode(Snake mySnake) {
        int currentLength = mySnake.body().size();

        if (startLength == -1) {
            startLength = currentLength;
        }

        int maxAllowedLength = startLength + MAX_EXTRA_LENGTH;
        wantsBadApple = currentLength >= maxAllowedLength;
    }

    private Direction decideDirection(GameField field) {
        Snake mySnake = field.snakesPerTeamName().get(teamName);

        if (mySnake == null || mySnake.body() == null || mySnake.body().isEmpty()) {
            return Direction.EAST;
        }

        Position myHead = mySnake.body().get(0);
        Direction trueDir = getTrueDirection(mySnake, field.size());

        // ─── CODE 1: TERMINATOR MODUS (ABSOLUTES MUSS) ───
        if (starStepsLeft > 0) {
            Direction killDir = findKillDirection(myHead, trueDir, field);
            if (killDir != null) {
                decrementStar(true);
                return killDir;
            }
        }

        boolean hasAttackPower = attackItemTicks > 0;
        Set<Position> ownBody = getOwnBodyPositions(mySnake);

        Set<Position> allEnemyBodies = getEnemyBodyPositions(field);
        Set<Position> aliveEnemyBodies = getAliveEnemyBodyPositions(field);
        Set<Position> deadEnemyBodies = new HashSet<>(allEnemyBodies);
        deadEnemyBodies.removeAll(aliveEnemyBodies);

        Set<Position> badApples = getBadApplePositions(field.items());

        /*
         * Instant Stack nur als Notfall-Item benutzen.
         */
        String instantStack = findInstantStackInInventory(mySnake);

        if (instantStack != null) {
            boolean useInstantStack = shouldUseInstantStack(
                    field, mySnake, myHead, trueDir, badApples
            );

            if (useInstantStack) {
                if (activateItemByName(instantStack)) {
                    System.out.println("INSTANT STACK AKTIVIERT!");
                }
            }
        }

        /*
         * NEU: Offensive Item-Logik ("Nur bei echter Kollision, niemals auf tote Schlangen!")
         */
        if (!hasAttackPower && !attackPreparedForNextTick) {
            Direction attackDir = tryActivateAttackItem(
                    mySnake, field, myHead, trueDir, ownBody, aliveEnemyBodies, deadEnemyBodies, badApples
            );

            if (attackDir != null) {
                return attackDir;
            }
        }

        /*
         * Nach dem ersten Schnitt vorsichtiger fahren.
         */
        if (hasAttackPower && alreadyCutWithAttackItem) {
            return decideCarefulAfterCutDirection(
                    field, mySnake, myHead, trueDir, ownBody, allEnemyBodies, badApples
            );
        }

        /*
         * Während Sword/Star aktiv ist: NUR lebende Gegner jagen.
         */
        if (hasAttackPower) {
            return decideAttackDirection(
                    field, mySnake, myHead, trueDir, ownBody, aliveEnemyBodies, deadEnemyBodies, badApples
            );
        }

        /*
         * Bad Apple Modus (Falls MAX_EXTRA_LENGTH je erreicht werden sollte)
         */
        if (wantsBadApple) {
            Direction badAppleDirection = decideBadAppleDirection(
                    field, mySnake, myHead, trueDir, badApples
            );
            if (badAppleDirection != null) {
                System.out.println("LÄNGE ZU HOCH -> BAD APPLE SUCHEN: " + badAppleDirection);
                return badAppleDirection;
            }
        }

        return decideNormalDirection(
                field, mySnake, myHead, trueDir, badApples, aliveEnemyBodies
        );
    }

    private boolean shouldUseInstantStack(
            GameField field, Snake mySnake, Position myHead, Direction trueDir, Set<Position> badApples
    ) {
        int mySize = mySnake.body().size();

        Set<Position> blocked = getBlockedPositions(field);
        Set<Position> lethalHeadOn = getLethalHeadOn(field, mySnake);

        int safeMoves = 0;
        int bestSpace = 0;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;

            Position next = wrap(move(myHead, dir), field.size());

            if (blocked.contains(next) || badApples.contains(next) || lethalHeadOn.contains(next)) {
                continue;
            }

            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(badApples);
            floodBlocked.addAll(lethalHeadOn);

            int space = countFreeFields(next, floodBlocked, field.size());

            if (space > bestSpace) {
                bestSpace = space;
            }
            safeMoves++;
        }

        if (safeMoves == 0) {
            System.out.println("INSTANT STACK NUTZEN: Kein sicherer Zug!");
            return true;
        }

        if (safeMoves == 1 && mySize >= 5) {
            System.out.println("INSTANT STACK NUTZEN: Nur noch ein sicherer Zug!");
            return true;
        }

        if (bestSpace < mySize) {
            System.out.println("INSTANT STACK NUTZEN: Zu wenig Platz!");
            return true;
        }

        if (mySize >= 6 && bestSpace < mySize + 4) {
            System.out.println("INSTANT STACK NUTZEN: Zu eng für lange Snake!");
            return true;
        }

        return false;
    }

    // NEU: Item wird NUR gezündet, wenn wir zwingend in eine LEBENDE Schlange krachen.
    private Direction tryActivateAttackItem(
            Snake mySnake, GameField field, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> aliveEnemyBodies, Set<Position> deadEnemyBodies, Set<Position> badApples
    ) {
        if (attackItemTicks > 0 || attackPreparedForNextTick) return null;
        if (mySnake.inventory() == null || mySnake.inventory().isEmpty()) return null;

        String sword = findItemNameInInventory(mySnake, true);
        String star = findItemNameInInventory(mySnake, false);
        if (sword == null && star == null) return null;

        String itemToUse = sword != null ? sword : star;

        Position front = wrap(move(myHead, trueDir), field.size());

        // 1. Wenn wir im nächsten Schritt DIREKT in einen LEBENDEN Gegner reinfahren -> Sofort zünden!
        if (aliveEnemyBodies.contains(front)) {
            if (activateItemByName(itemToUse)) {
                attackPreparedForNextTick = true;
                System.out.println("KOLLISION MIT LEBENDEM GEGNER! " + itemToUse + " VORBEREITET: " + trueDir);
                return trueDir;
            }
        }

        // 2. Wenn vorne etwas blockiert (z.B. toter Körper, eigener Körper, Rand) und wir ausweichen müssen:
        // Wir prüfen die Seiten. Wenn wir beim Ausweichen zwingend in einen LEBENDEN Gegner fahren, zünden wir!
        if (ownBody.contains(front) || badApples.contains(front) || deadEnemyBodies.contains(front)) {
            for (Direction dir : Direction.values()) {
                if (isOpposite(trueDir, dir)) continue;

                Position side = wrap(move(myHead, dir), field.size());

                if (aliveEnemyBodies.contains(side)) {
                    if (activateItemByName(itemToUse)) {
                        attackPreparedForNextTick = true;
                        System.out.println("AUSWEICH-KOLLISION MIT LEBENDEM GEGNER! " + itemToUse + " VORBEREITET IN RICHTUNG: " + dir);
                        return dir;
                    }
                }
            }
        }

        // Wenn wir nicht kollidieren, heben wir uns das Item auf.
        return null;
    }

    private Direction decideAttackDirection(
            GameField field, Snake mySnake, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> aliveEnemyBodies, Set<Position> deadEnemyBodies, Set<Position> badApples
    ) {
        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;
        Set<Position> lethalHeadOn = getLethalHeadOn(field, mySnake);

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;

            Position next = wrap(move(myHead, dir), field.size());

            // Tote Schlangen absolut meiden! Auch im Angriffsmodus!
            if (ownBody.contains(next) || badApples.contains(next) || deadEnemyBodies.contains(next)) {
                continue;
            }

            // Kopf-Gegen-Kopf auch beim Schwertangriff vermeiden!
            if (lethalHeadOn.contains(next)) {
                System.out.println("ATTACK MODUS: Kopf-Kollision absolut vermieden: " + dir);
                continue;
            }

            // Wir treffen eine LEBENDE Snake -> Perfekt! (Seitlich)
            if (aliveEnemyBodies.contains(next)) {
                System.out.println("ATTACK ITEM SCHNEIDET LEBENDE SNAKE DIREKT: " + dir);
                alreadyCutWithAttackItem = true;
                return dir;
            }

            // Jage ausschließlich die LEBENDEN Snakes!
            int score = scoreTowardsEnemy(next, aliveEnemyBodies, field.size());
            if (dir == trueDir) score += 5;

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }
            System.out.println("ATTACK Dir: " + dir + " | Score: " + score);
        }

        if (bestDirection != null) {
            System.out.println("ATTACK ITEM JAGT LEBENDE GEGNER: " + bestDirection);
            return bestDirection;
        }
        return trueDir;
    }

    private void checkAndActivateSpeedBoostTrap(Position myHead, Direction chosenDir, GameField field, Snake mySnake, Set<Position> blocked) {
        if (chosenDir == null || mySnake.inventory() == null) return;

        String boostItem = null;
        for (String item : mySnake.inventory()) {
            if (item != null && (item.toLowerCase().contains("speed") || item.toLowerCase().contains("boost"))) {
                boostItem = item;
                break;
            }
        }

        if (boostItem == null) return;

        Position step1 = wrap(move(myHead, chosenDir), field.size());
        Position step2 = wrap(move(step1, chosenDir), field.size());

        if (blocked.contains(step1) || blocked.contains(step2)) {
            return;
        }

        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;

            Position eHead = enemy.body().get(0);
            int dist = distance(myHead, eHead, field.size());

            if (dist > 0 && dist <= 3) {
                if (activateItemByName(boostItem)) {
                    System.out.println("🚀 SPEEDBOOST FALLE AKTIVIERT! Schneide Gegner den Weg ab!");
                }
                return;
            }
        }
    }

    private Direction decideCarefulAfterCutDirection(
            GameField field, Snake mySnake, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> enemyBodies, Set<Position> badApples
    ) {
        int mySize = mySnake.body().size();
        Set<Position> blocked = new HashSet<>();
        blocked.addAll(ownBody);
        blocked.addAll(enemyBodies); // enemyBodies beinhaltet auch die toten Schlangen

        Set<Position> lethalHeadOn = getLethalHeadOn(field, mySnake);

        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;
        Direction fallback = null;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;

            Position next = wrap(move(myHead, dir), field.size());

            if (blocked.contains(next) || badApples.contains(next)) {
                continue;
            }

            if (fallback == null) fallback = dir;

            if (lethalHeadOn.contains(next)) {
                System.out.println("NACH SCHNITT: Frontal-Gefahr vermeiden: " + dir);
                continue;
            }

            int score = 0;
            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(badApples);
            floodBlocked.addAll(lethalHeadOn);

            int space = countFreeFields(next, floodBlocked, field.size());
            score += space * 100;

            if (space < mySize) score -= 3000;
            if (dir == trueDir) score += 10;

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }
        }

        if (bestDirection != null) {
            return bestDirection;
        }

        if (fallback != null) return fallback;
        return trueDir;
    }

    private Direction decideBadAppleDirection(
            GameField field, Snake mySnake, Position myHead, Direction trueDir, Set<Position> badApples
    ) {
        if (badApples == null || badApples.isEmpty()) return null;

        Set<Position> blocked = getBlockedPositions(field);
        Set<Position> lethalHeadOn = getLethalHeadOn(field, mySnake);
        Position targetBadApple = findNearestBadApplePosition(myHead, badApples, field.size());

        if (targetBadApple == null) return null;

        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;
        Direction emergencyBadAppleNow = null;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;

            Position next = wrap(move(myHead, dir), field.size());

            if (blocked.contains(next)) continue;

            boolean headRisk = lethalHeadOn.contains(next);

            if (badApples.contains(next)) {
                if (!headRisk) {
                    return dir;
                }
                emergencyBadAppleNow = dir;
                continue;
            }

            if (headRisk) {
                continue;
            }

            int score = 0;
            int distBad = distance(next, targetBadApple, field.size());
            score += 8000 - distBad * 1000;

            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(lethalHeadOn);

            int space = countFreeFields(next, floodBlocked, field.size());
            score += space * 10;

            if (space < 2) score -= 1000;
            if (dir == trueDir) score += 5;

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }
        }

        if (bestDirection != null) return bestDirection;
        if (emergencyBadAppleNow != null) return emergencyBadAppleNow;
        return null;
    }

    private Direction decideNormalDirection(
            GameField field, Snake mySnake, Position myHead, Direction trueDir, Set<Position> badApples, Set<Position> aliveEnemyBodies
    ) {
        int mySize = mySnake.body().size();
        Set<Position> blocked = getBlockedPositions(field); // Enthält eigene + alle feindlichen (lebendig+tot)

        // NEU: Absolute Frontal-Kollision-Zonen berechnen!
        Set<Position> lethalHeadOn = getLethalHeadOn(field, mySnake);

        Item targetUsefulItem = findNearestUsefulItem(myHead, field.items(), field.size());

        Item targetApple = null;
        if (!wantsBadApple) {
            targetApple = findNearestGoodApple(myHead, field.items(), field.size());
        }

        Direction bestSafe = null;
        int bestSafeScore = Integer.MIN_VALUE;

        Direction fallbackBadApple = null;
        Direction fallbackLethal = null;
        Direction fallbackAny = null;

        boolean hasSwordOrStar = (findItemNameInInventory(mySnake, true) != null) || (findItemNameInInventory(mySnake, false) != null);

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;

            Position next = wrap(move(myHead, dir), field.size());

            // Tote und lebende Schlangen werden im Normal-Modus absolut gemieden
            if (blocked.contains(next)) continue;

            if (fallbackAny == null) fallbackAny = dir;

            // NEU: Biege nicht in die Richtung ab, wo der Feind frontal herkommt!
            if (lethalHeadOn.contains(next)) {
                System.out.println("FRONTAL-GEFAHR VERMIEDEN: " + dir);
                if (fallbackLethal == null) fallbackLethal = dir;
                continue;
            }

            boolean isSacrificeForSword = false;
            if (targetUsefulItem != null && isSwordItem(targetUsefulItem)) {
                if (distance(next, targetUsefulItem.position(), field.size()) == 1) {
                    isSacrificeForSword = true;
                }
            }

            if (badApples.contains(next)) {
                if (isSacrificeForSword) {
                    System.out.println("⚔️ Opfere 1 Länge (Bad Apple) für das Schwert! Richtung: " + dir);
                } else {
                    if (fallbackBadApple == null) fallbackBadApple = dir;
                    continue;
                }
            }

            int score = 0;
            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(lethalHeadOn);
            floodBlocked.addAll(badApples);

            int space = countFreeFields(next, floodBlocked, field.size());
            score += space * 100;

            if (space < mySize) score -= 5000;

            // OFFENSIVER PULL: Wenn wir ein Schwert haben, nähern wir uns den Feinden absichtlich an!
            if (hasSwordOrStar) {
                int attackScore = scoreTowardsEnemy(next, aliveEnemyBodies, field.size());
                score += attackScore / 5; // Sanfter Pull in Richtung Feind, um eine Kollision zu provozieren
            }

            if (targetUsefulItem != null) {
                int distItem = distance(next, targetUsefulItem.position(), field.size());

                if (isSwordItem(targetUsefulItem)) {
                    score += 10000 - distItem * 700;
                    if (distItem == 0) score += 15000;
                    else if (distItem == 1 && space > mySize + 2) score += 6000;
                    else if (distItem == 2 && space > mySize + 4) score += 3000;

                } else if (isInstantStackName(targetUsefulItem.type())) {
                    score += 8000 - distItem * 600;
                    if (distItem == 0) score += 12000;
                    else if (distItem == 1 && space > mySize + 2) score += 5000;
                    else if (distItem == 2 && space > mySize + 4) score += 2500;

                } else if (isSpeedBoostName(targetUsefulItem.type())) {
                    score += 5000 - distItem * 400;
                    if (distItem == 0) score += 7000;
                    else if (distItem == 1 && space > mySize + 2) score += 2500;

                } else if (isStarName(targetUsefulItem.type())){
                    score += 4000 - distItem * 300;
                    if (distItem == 0) score += 5000;
                }
            }

            if (targetApple != null) {
                int distApple = distance(next, targetApple.position(), field.size());
                score += 100 - distApple * 10;
                if (distApple == 0) score += 800;
                else if (distApple == 1 && space > mySize + 3) score += 300;
            }

            if (dir == trueDir) score += 10;

            if (score > bestSafeScore) {
                bestSafeScore = score;
                bestSafe = dir;
            }
            System.out.println("NORMAL Dir: " + dir + " | Score: " + score + " | Space: " + space + " | Length: " + mySize);
        }

        if (bestSafe != null) {
            checkAndActivateSpeedBoostTrap(myHead, bestSafe, field, mySnake, blocked);
            return bestSafe;
        }
        if (fallbackBadApple != null) return fallbackBadApple;
        if (fallbackLethal != null) return fallbackLethal;
        if (fallbackAny != null) return fallbackAny;

        return trueDir;
    }

    // NEU: Berechnet die exakte Frontal-Kollisionszone ("Kopf-gegen-Kopf")
    private Set<Position> getLethalHeadOn(GameField field, Snake mySnake) {
        Set<Position> lethal = new HashSet<>();
        int mySize = mySnake.body().size();

        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;

            Snake enemy = e.getValue();
            if (enemy == null || !enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) {
                continue;
            }

            Position enemyHead = enemy.body().get(0);
            Direction enemyDir = getTrueDirection(enemy, field.size());

            // Die predicted Next Position ist immer tödlich bei Frontal-Crash!
            Position predictedNext = wrap(move(enemyHead, enemyDir), field.size());
            lethal.add(predictedNext);
            lethal.add(enemyHead);

            // Schutzabstand bei gleich großen oder größeren Feinden
            if (enemy.body().size() >= mySize) {
                for (Direction d : Direction.values()) {
                    lethal.add(wrap(move(enemyHead, d), field.size()));
                }
            }
        }
        return lethal;
    }

    private Item findNearestUsefulItem(Position head, List<Item> items, Size size) {
        if (items == null) return null;
        Item bestItem = null;
        int bestScore = Integer.MIN_VALUE;

        for (Item item : items) {
            if (item == null || item.type() == null) continue;
            if (!isUsefulItem(item)) continue;

            int dist = distance(head, item.position(), size);
            int score = 0;

            if (isSwordName(item.type())) {
                score += 10000;
            } else if (isInstantStackName(item.type())) {
                score += 8000;
            } else if (isSpeedBoostName(item.type())) {
                score += 5000;
            } else if (isStarName(item.type())) {
                score += 4000;
            }

            score -= dist * 500;

            if (score > bestScore) {
                bestScore = score;
                bestItem = item;
            }
        }
        return bestItem;
    }

    private boolean isUsefulItem(Item item) {
        if (item == null || item.type() == null) return false;
        String t = item.type().toLowerCase();
        return isSwordName(t) || isInstantStackName(t) || isSpeedBoostName(t) || isStarName(t);
    }

    private int scoreTowardsEnemy(Position next, Set<Position> enemyBodies, Size size) {
        int bestDistance = Integer.MAX_VALUE;
        for (Position enemyPart : enemyBodies) {
            int dist = distance(next, enemyPart, size);
            if (dist < bestDistance) {
                bestDistance = dist;
            }
        }
        if (bestDistance == Integer.MAX_VALUE) return 0;
        if (bestDistance == 0) return 1_000_000;
        if (bestDistance == 1) return 500_000;
        if (bestDistance == 2) return 200_000;
        if (bestDistance == 3) return 80_000;
        if (bestDistance == 4) return 30_000;
        return 10_000 - bestDistance * 500;
    }

    // ─── CODE 1: TERMINATOR-HILFSMETHODEN (BEIBEHALTEN) ───

    private Direction findKillDirection(Position head, Direction trueDir, GameField field) {
        Set<Position> enemyTargets = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;
            enemyTargets.addAll(enemy.body());
        }

        if (enemyTargets.isEmpty()) return null;

        Direction bestKillDir = null;
        int closestDistance = Integer.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;

            Position rawNext = move(head, dir);
            Position next = wrap(rawNext, field.size());

            Set<Position> ownBody = new HashSet<>(field.snakesPerTeamName().get(teamName).body());
            if (ownBody.contains(next)) continue;

            if (enemyTargets.contains(next)) return dir;

            int currentMinDist = Integer.MAX_VALUE;
            for (Position p : enemyTargets) {
                currentMinDist = Math.min(currentMinDist, distance(next, p, field.size()));
            }

            if (currentMinDist < closestDistance) {
                closestDistance = currentMinDist;
                bestKillDir = dir;
            }
        }
        return bestKillDir;
    }

    private void decrementStar(boolean starActive) {
        if (starActive) {
            System.out.println("★ Stern-Angriff läuft (Max 3 Schritte)...");
        }
    }

    private boolean hasActiveStar(Snake me) {
        if (me.activeEffects() == null) return false;
        for (ActiveEffectInfo e : me.activeEffects()) {
            if (e.effect() != null && e.effect().toLowerCase().contains("star")) {
                return true;
            }
        }
        return false;
    }

    private boolean activateStarIfPossible(Snake me) {
        if (me.inventory() == null) return false;
        for (String item : me.inventory()) {
            if (item != null && item.toLowerCase().contains("star")) {
                try {
                    api.activateItem(item);
                    return true;
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    // ─── ALLGEMEINE HILFSMETHODEN ───

    private boolean activateItemByName(String item) {
        try {
            api.activateItem(item);
            System.out.println("ITEM AKTIVIERT: " + item);
            return true;
        } catch (Exception e) {
            System.out.println("Item konnte nicht aktiviert werden: " + item);
            return false;
        }
    }

    private String findItemNameInInventory(Snake mySnake, boolean swordOnly) {
        if (mySnake.inventory() == null) return null;
        for (String item : mySnake.inventory()) {
            if (item == null) continue;
            if (swordOnly && isSwordName(item)) return item;
            if (!swordOnly && isStarName(item)) return item;
        }
        return null;
    }

    private String findInstantStackInInventory(Snake mySnake) {
        if (mySnake.inventory() == null) return null;
        for (String item : mySnake.inventory()) {
            if (item != null && isInstantStackName(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean isInstantStackName(String name) {
        if (name == null) return false;
        String t = name.toLowerCase();
        return t.contains("instant") || t.contains("stack") || t.contains("instantstack");
    }

    private boolean isSpeedBoostName(String name) {
        if (name == null) return false;
        String t = name.toLowerCase();
        return t.contains("speed") || t.contains("boost") || t.contains("speed_boost") || t.contains("speedboost");
    }

    private boolean isSwordName(String name) {
        if (name == null) return false;
        return name.toLowerCase().contains("sword") || name.toLowerCase().contains("schwert");
    }

    private boolean isSwordItem(Item item) {
        return item != null && item.type() != null && isSwordName(item.type());
    }

    private boolean isStarName(String name) {
        if (name == null) return false;
        String t = name.toLowerCase();
        return t.contains("star") || t.contains("stern") || t.contains("power") || t.contains("invincible");
    }

    private Set<Position> getOwnBodyPositions(Snake mySnake) {
        Set<Position> own = new HashSet<>();
        if (mySnake.body() != null) own.addAll(mySnake.body());
        return own;
    }

    private Set<Position> getEnemyBodyPositions(GameField field) {
        Set<Position> enemy = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake snake = e.getValue();
            if (snake != null && snake.body() != null) enemy.addAll(snake.body());
        }
        return enemy;
    }

    private Set<Position> getAliveEnemyBodyPositions(GameField field) {
        Set<Position> enemy = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake snake = e.getValue();
            if (snake != null && snake.alive() && snake.body() != null) {
                enemy.addAll(snake.body());
            }
        }
        return enemy;
    }

    private Set<Position> getBlockedPositions(GameField field) {
        Set<Position> blocked = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            Snake snake = e.getValue();
            if (snake == null || snake.body() == null || snake.body().isEmpty()) continue;

            List<Position> body = snake.body();
            boolean isMe = e.getKey().equals(teamName);
            int limit = isMe ? body.size() - 1 : body.size();
            for (int i = 0; i < limit; i++) {
                blocked.add(body.get(i));
            }
        }
        return blocked;
    }

    private Set<Position> getBadApplePositions(List<Item> items) {
        Set<Position> bad = new HashSet<>();
        if (items == null) return bad;
        for (Item item : items) {
            if (item != null && item.type() != null && item.type().toLowerCase().contains("bad")) {
                bad.add(item.position());
            }
        }
        return bad;
    }

    private Position findNearestBadApplePosition(Position head, Set<Position> badApples, Size size) {
        Position nearest = null;
        int best = Integer.MAX_VALUE;
        for (Position badApple : badApples) {
            int d = distance(head, badApple, size);
            if (d < best) {
                best = d;
                nearest = badApple;
            }
        }
        return nearest;
    }

    private Item findNearestGoodApple(Position head, List<Item> items, Size size) {
        if (items == null) return null;
        Item nearest = null;
        int best = Integer.MAX_VALUE;

        for (Item item : items) {
            if (item == null || item.type() == null) continue;
            String t = item.type().toLowerCase();

            if (t.contains("bad")) continue;

            if (t.contains("apple") || t.contains("sword") || t.contains("star") || t.contains("Star") || t.contains("SpeedBoost") || t.contains("speed") || t.contains("Speed")) {
                int d = distance(head, item.position(), size);
                if (d < best) {
                    best = d;
                    nearest = item;
                }
            }
        }
        return nearest;
    }

    private int countFreeFields(Position start, Set<Position> blocked, Size size) {
        Set<Position> visited = new HashSet<>();
        Queue<Position> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Position cur = queue.poll();
            for (Direction d : Direction.values()) {
                Position next = wrap(move(cur, d), size);
                if (!visited.contains(next) && !blocked.contains(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return visited.size();
    }

    private Direction getTrueDirection(Snake snake, Size fieldSize) {
        if (snake.body() == null || snake.body().size() < 2) return Direction.EAST;

        Position head = snake.body().get(0);
        Position neck = snake.body().get(1);

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

    private Position move(Position p, Direction d) {
        return switch (d) {
            case NORTH -> new Position(p.x(), p.y() - 1);
            case SOUTH -> new Position(p.x(), p.y() + 1);
            case EAST -> new Position(p.x() + 1, p.y());
            case WEST -> new Position(p.x() - 1, p.y());
        };
    }

    private Position wrap(Position p, Size s) {
        int x = p.x();
        int y = p.y();
        if (x < 0) x = s.width() - 1;
        else if (x >= s.width()) x = 0;
        if (y < 0) y = s.height() - 1;
        else if (y >= s.height()) y = 0;
        return new Position(x, y);
    }

    private boolean isOpposite(Direction a, Direction b) {
        return (a == Direction.NORTH && b == Direction.SOUTH)
                || (a == Direction.SOUTH && b == Direction.NORTH)
                || (a == Direction.EAST && b == Direction.WEST)
                || (a == Direction.WEST && b == Direction.EAST);
    }

    private int distance(Position a, Position b, Size s) {
        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());
        return Math.min(dx, s.width() - dx) + Math.min(dy, s.height() - dy);
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