package com.oolonghoo.wooeco.migration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MigrationResult {
    private boolean success;
    private String sourceName;
    private int totalPlayers;
    private int migratedPlayers;
    private int skippedPlayers;
    private int failedPlayers;
    private int totalNonPlayerAccounts;
    private int migratedNonPlayerAccounts;
    private int totalRecords;
    private int migratedRecords;
    private long durationMs;
    private final List<String> errors = new ArrayList<>();

    public static MigrationResult success(String sourceName) {
        MigrationResult r = new MigrationResult();
        r.success = true;
        r.sourceName = sourceName;
        return r;
    }

    public static MigrationResult failure(String sourceName, String error) {
        MigrationResult r = new MigrationResult();
        r.success = false;
        r.sourceName = sourceName;
        r.errors.add(error);
        return r;
    }

    public void incrementMigratedPlayer() { migratedPlayers++; }
    public void incrementSkippedPlayer() { skippedPlayers++; }
    public void incrementFailedPlayer() { failedPlayers++; }
    public void incrementMigratedNonPlayerAccount() { migratedNonPlayerAccounts++; }
    public void incrementMigratedRecord() { migratedRecords++; }
    public void addError(String error) { errors.add(error); }

    public boolean isSuccess() { return success; }
    public String getSourceName() { return sourceName; }
    public int getTotalPlayers() { return totalPlayers; }
    public void setTotalPlayers(int totalPlayers) { this.totalPlayers = totalPlayers; }
    public int getMigratedPlayers() { return migratedPlayers; }
    public int getSkippedPlayers() { return skippedPlayers; }
    public int getFailedPlayers() { return failedPlayers; }
    public int getTotalNonPlayerAccounts() { return totalNonPlayerAccounts; }
    public void setTotalNonPlayerAccounts(int totalNonPlayerAccounts) { this.totalNonPlayerAccounts = totalNonPlayerAccounts; }
    public int getMigratedNonPlayerAccounts() { return migratedNonPlayerAccounts; }
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
    public int getMigratedRecords() { return migratedRecords; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public List<String> getErrors() { return errors; }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(sourceName).append(", ");
        sb.append("Players: ").append(migratedPlayers).append("/").append(totalPlayers);
        if (skippedPlayers > 0) sb.append(" (skipped: ").append(skippedPlayers).append(")");
        if (failedPlayers > 0) sb.append(" (failed: ").append(failedPlayers).append(")");
        if (totalNonPlayerAccounts > 0) sb.append(", Non-player accounts: ").append(migratedNonPlayerAccounts).append("/").append(totalNonPlayerAccounts);
        if (totalRecords > 0) sb.append(", Records: ").append(migratedRecords).append("/").append(totalRecords);
        sb.append(", Duration: ").append(durationMs).append("ms");
        return sb.toString();
    }
}
