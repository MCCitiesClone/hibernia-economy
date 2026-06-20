package io.paradaux.treasuryrestapi.dto;

/** Admin account owner change. {@code owner} is a UUID or a player name. */
public record AccountOwnerRequest(String owner) {}
