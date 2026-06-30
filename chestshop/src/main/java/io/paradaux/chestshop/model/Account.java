package io.paradaux.chestshop.model;

import io.paradaux.chestshop.utils.NameUtil;

import java.util.Date;
import java.util.UUID;

/**
 * A row of the {@code accounts} table ({@code users.db}): the username ↔ UUID ↔
 * shortened-name mapping a shop sign resolves an owner against. A plain POJO mapped by
 * {@link io.paradaux.chestshop.mappers.AccountMapper} (MyBatis); {@code lastSeen} is
 * stored as epoch-millis and {@code uuid} as its string form (PAR-282).
 *
 * @author Andrzej Pomirski (Acrobot)
 */
public class Account {

    private String name;
    private String shortName;
    private UUID uuid;
    private Date lastSeen;
    private boolean ignoreMessages;

    public Account() {
        // empty constructor, needed for MyBatis result mapping
    }

    public Account(String name, UUID uuid) {
        this(name, NameUtil.stripUsername(name), uuid);
    }

    public Account(String name, String shortName, UUID uuid) {
        this.name = name;
        this.shortName = shortName;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Date getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Date lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isIgnoringMessages() {
        return ignoreMessages;
    }

    public void setIgnoreMessages(boolean ignoreMessages) {
        this.ignoreMessages = ignoreMessages;
    }
}
