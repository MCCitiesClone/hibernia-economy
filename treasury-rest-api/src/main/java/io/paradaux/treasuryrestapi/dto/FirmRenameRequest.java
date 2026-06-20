package io.paradaux.treasuryrestapi.dto;

/** Admin firm-rename request body. {@code newName} follows the in-game name rules. */
public record FirmRenameRequest(String newName) {}
