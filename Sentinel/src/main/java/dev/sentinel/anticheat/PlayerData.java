package dev.sentinel.anticheat;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.UUID;

public final class PlayerData {
    public long lastTeleport, lastVelocity, lastMoveMs, lastIce, windowStart, lastAttack, invOpenAt;
    public boolean invOpen;
    public int moveCount, airTicks;
    public double lastY;
    public Location lastGround;
    public double speedBuf, timerBuf, flyBuf, jesusBuf, phaseBuf, noFallBuf, invMoveBuf,
            dirBuf, auraBuf, wallBuf, scaffoldBuf;
    public UUID lastTarget;
    public final ArrayDeque<Long> clicks = new ArrayDeque<>();
    public final ArrayDeque<Long> breaks = new ArrayDeque<>();
    public final ArrayDeque<Long> places = new ArrayDeque<>();
    public final ArrayDeque<Long> invClicks = new ArrayDeque<>();
    public final ArrayDeque<Integer> xray = new ArrayDeque<>();
}
