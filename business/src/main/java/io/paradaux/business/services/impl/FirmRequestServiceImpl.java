package io.paradaux.business.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.paradaux.hibernia.framework.exceptions.BadCommandException;
import io.paradaux.hibernia.framework.exceptions.InternalException;
import io.paradaux.hibernia.framework.exceptions.NoPermissionException;
import io.paradaux.hibernia.framework.exceptions.NotFoundException;
import io.paradaux.business.mappers.FirmRequestMapper;
import io.paradaux.business.model.Firm;
import io.paradaux.business.model.RolePermission;
import io.paradaux.business.services.FirmAccountService;
import io.paradaux.business.services.FirmRequestService;
import io.paradaux.business.services.FirmService;
import io.paradaux.business.services.FirmStaffService;
import org.apache.ibatis.exceptions.PersistenceException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.UUID;

import static io.paradaux.hibernia.framework.utils.StringUtils.random32;

@Singleton
public class FirmRequestServiceImpl implements FirmRequestService {

    private static final int OFFER_EXPIRATION_MINUTES = 5;
    private final FirmRequestMapper requests;
    private final FirmService firms;
    private final FirmStaffService staff;
    private final FirmAccountService accounts;

    @Inject
    public FirmRequestServiceImpl(FirmRequestMapper requests, FirmService firms, FirmStaffService staff,
                                  FirmAccountService accounts) {
        this.requests = requests;
        this.firms = firms;
        this.staff = staff;
        this.accounts = accounts;
    }

    @Override
    public void offerEmployment(String firmName, UUID targetId, UUID actorId) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OFFER_EXPIRATION_MINUTES);
        Firm firm = firms.getFirmByNameOrId(firmName);

        if (firm == null) {
            throw new NotFoundException("Firm not found.");
        }

        if (!staff.hasPermission(firm.getFirmId(), actorId, RolePermission.ADMIN)) {
            throw new NoPermissionException("You do not have permission to do this.");
        }

        if (staff.isEmployedBy(firm.getFirmId(), targetId)) {
            throw new BadCommandException("This user is already employed.");
        }

        if(targetId.equals(actorId)) {
            throw new BadCommandException("You cannot do this to yourself.");
        }

        if (requests.hasPendingJobOffer(firm.getFirmId(), targetId.toString())) {
            throw new BadCommandException("This user already has a pending invite to join this firm.");
        }

        int rowsAffected = requests.createInvite(firm.getFirmId(), targetId.toString(), actorId.toString(), expiresAt);

        if (rowsAffected != 1) {
            throw new InternalException("Internal exception occurred while trying to offer employment.");
        }
    }

    @Override
    public void rescindEmploymentOffer(String firmName, UUID playerId, UUID actorId) {
        Firm firm = firms.getFirmByNameOrId(firmName);

        if (firm == null) {
            throw new NotFoundException("Firm not found.");
        }

        if (!staff.hasPermission(firm.getFirmId(), actorId, RolePermission.ADMIN)) {
            throw new NoPermissionException("You do not have permission to do this.");
        }

        if (staff.isEmployedBy(firm.getFirmId(), playerId)) {
            throw new BadCommandException("This user is already employed.");
        }

        if(playerId.equals(actorId)) {
            throw new BadCommandException("You cannot do this to yourself.");
        }

        if (!requests.hasPendingJobOffer(firm.getFirmId(), playerId.toString())) {
            throw new BadCommandException("This user does not have a pending offer.");
        }

        int rows = requests.rescindInvite(firm.getFirmId(), playerId.toString());
        if (rows != 1) {
            throw new InternalException("Failed to rescind invite, was there a pending invite to begin with?");
        }
    }

    @Override
    public void acceptEmploymentOffer(String firmName, UUID playerId) {
        Firm firm = firms.getFirmByNameOrId(firmName);

        if (firm == null) {
            throw new NotFoundException("Firm not found.");
        }

        if (!requests.hasPendingJobOffer(firm.getFirmId(), playerId.toString())) {
            throw new BadCommandException("This user does not have a pending offer.");
        }

        // Lock the invite row
        String inviterStr = requests.lockPendingInviter(firm.getFirmId(), playerId.toString());
        if (inviterStr == null) {
            throw new BadCommandException("No valid invite to accept.");
        }
        UUID inviter = UUID.fromString(inviterStr);

        // Flip status
        int updated = requests.acceptInvite(firm.getFirmId(), playerId.toString());
        if (updated != 1) {
            throw new InternalException("Failed to accept invite after lock.");
        }


        // Hire WITHOUT re-checking inviter's permissions — the invite row
        // itself is the artifact of the INVITE-time permission check.
        staff.hireEmployeeFromInvite(firm.getFirmId(), playerId, inviter);
    }

    @Override
    public void rejectEmploymentOffer(String firmName, UUID playerId) {
        Firm firm = firms.getFirmByNameOrId(firmName);

        if (firm == null) {
            throw new NotFoundException("Firm not found.");
        }

        if (!requests.hasPendingJobOffer(firm.getFirmId(), playerId.toString())) {
            throw new BadCommandException("This user does not have a pending offer.");
        }

        int rows = requests.rejectInvite(firm.getFirmId(), playerId.toString());
        if (rows != 1) {
            throw new InternalException("Failed to reject invite (not pending or already expired).");
        }
    }

    @Override
    public String beginTransferProprietorship(String firmName, UUID newProprietorId, UUID actorId) {
        Firm firm = firms.getFirmByNameOrId(firmName);
        if (firm == null) {
            throw new NotFoundException("Firm not found.");
        }

        // Only the current proprietor may initiate a transfer of their firm.
        // Without this any holder of business.transfer.begin could spawn
        // transfer requests against firms they don't own.
        if (!firm.getProprietorUuid().equalsIgnoreCase(actorId.toString())) {
            throw new NoPermissionException("Only the current proprietor can transfer a firm.");
        }

        if(newProprietorId.equals(actorId)) {
            throw new BadCommandException("You cannot do this to yourself.");
        }

        String code = random32();
        try {
            requests.createTransferRequest(firm.getFirmId(), newProprietorId.toString(), code ,LocalDateTime.now().plusMinutes(OFFER_EXPIRATION_MINUTES));
        } catch (PersistenceException ex) {
            if (ex.getCause() instanceof SQLIntegrityConstraintViolationException) {
                throw new BadCommandException("You already have a pending transfer request. Cancel the previous or wait for it to be approved.");
            }
        }
        return code;
    }

    @Override
    public boolean confirmTransferProprietorship(String firmName, UUID newProprietorId, String code, UUID actorId) {
        Firm firm = firms.getFirmByNameOrId(firmName);

        if (firm == null) {
            throw new NotFoundException("Firm not found.");
        }

        // Only the current proprietor can confirm the transfer they began.
        // The token is a soft secret, but defense-in-depth keeps anyone with
        // the `business.transfer.confirm` permission and a leaked code from
        // hijacking ownership.
        if (!firm.getProprietorUuid().equalsIgnoreCase(actorId.toString())) {
            throw new NoPermissionException("Only the current proprietor can confirm a transfer.");
        }

        int rows = requests.confirmTransfer(firm.getFirmId(), newProprietorId.toString(), code);
        return rows > 0;
    }

    @Override
    public void cancelTransferProprietorship(String firmName, UUID newProprietorId, UUID actorId) {
        Firm firm = firms.getFirmByNameOrId(firmName);

        if (firm == null) {
            throw new NotFoundException("Firm not found.");
        }

        requests.rejectTransfer(firm.getFirmId(), newProprietorId.toString());
    }

    @Override
    public UUID completeTransferProprietorship(String firmName, UUID newProprietorId) {
        Firm firm = firms.getFirmByNameOrId(firmName);
        if (firm == null) {
            throw new NotFoundException("Firm not found.");
        }

        // Gate on a CONFIRMED transfer: acceptTransfer only flips a CONFIRMED,
        // non-expired request to ACCEPTED and returns 1. If there is none for
        // this new proprietor, refuse — otherwise updateProprietor would hand
        // the firm over with no began/confirmed transfer at all (firm theft).
        if (requests.acceptTransfer(firm.getFirmId(), newProprietorId.toString()) != 1) {
            throw new BadCommandException(
                    "No confirmed proprietorship transfer to accept for this firm.");
        }

        if (staff.isEmployedBy(firm.getFirmId(), newProprietorId)) {
            staff.resignFromFirm(firmName, newProprietorId);
        }

        firms.updateProprietor(firm.getFirmId(), newProprietorId);

        // Reflect the ownership change in Treasury: hand the firm's accounts to
        // the new proprietor and re-sync access. Done after updateProprietor so
        // the sync derives the new proprietor as owner. Without this the new
        // owner is locked out of the firm's accounts and the previous owner
        // keeps owner-level access (PAR-141).
        accounts.reassignAccountsToNewProprietor(firm.getFirmId(), newProprietorId);

        return UUID.fromString(firm.getProprietorUuid());
    }

    @Override
    public UUID rejectTransferProprietorship(String firmName, UUID newProprietorId) {
        Firm firm = firms.getFirmByNameOrId(firmName);
        if (firm == null) {
            throw new NotFoundException("Firm not found.");
        }
        requests.rejectTransfer(firm.getFirmId(), newProprietorId.toString());
        return UUID.fromString(firm.getProprietorUuid());
    }
}
