package me.yic.xconomy.data.syncdata;

import me.yic.xconomy.info.SyncChannalType;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

public class SyncData {

    private final UUID uuid;

    public SyncData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public SyncChannalType getSyncType() {
        return SyncChannalType.OFF;
    }

    public String getSign() {
        return "";
    }

    public String getServerKey() {
        return "";
    }

    public ByteArrayOutputStream toByteArray(String syncversion) {
        return new ByteArrayOutputStream();
    }
}
