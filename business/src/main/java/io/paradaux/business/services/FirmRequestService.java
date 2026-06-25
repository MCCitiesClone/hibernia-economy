package io.paradaux.business.services;

import java.util.UUID;

public interface FirmRequestService {

    void offerEmployment(String firmName, UUID playerId, UUID actorId);
    void rescindEmploymentOffer(String firmName, UUID playerId, UUID actorId);
    void acceptEmploymentOffer(String firmName, UUID playerId, UUID actorId);
    void rejectEmploymentOffer(String firmName, UUID playerId, UUID actorId);

    String beginTransferProprietorship(String firmName, UUID newProprietorId, UUID actorId);
    boolean confirmTransferProprietorship(String firmName, UUID newProprietorId, String code, UUID actorId);
    void cancelTransferProprietorship(String firmName, UUID newProprietorId, UUID actorId);

    UUID completeTransferProprietorship(String firmName, UUID newProprietorId);
    UUID rejectTransferProprietorship(String firmName, UUID newProprietorId, UUID actorId);

}
