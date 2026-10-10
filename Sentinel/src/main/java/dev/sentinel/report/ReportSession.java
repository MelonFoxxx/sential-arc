package dev.sentinel.report;

import java.util.UUID;

/** Чернетка жалоби, яку гравець заповнює в меню. */
public final class ReportSession {
    public UUID targetUuid;
    public String targetName;
    public int reasonIndex = -1;
    public String comment = "";
}
