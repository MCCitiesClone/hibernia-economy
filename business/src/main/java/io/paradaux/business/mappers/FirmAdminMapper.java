package io.paradaux.business.mappers;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface FirmAdminMapper {

    /**
     * Soft-archive every active firm owned by the player. The economy uses a
     * universal soft-delete/audit model — a firm is archived, never hard-deleted
     * (a hard DELETE would orphan its accounts and erase the audit trail). Returns
     * the number of firms archived (ADT-70).
     */
    @Update("""
        UPDATE firm
           SET is_archived = 1,
               default_account_id = NULL,
               updated_at = CURRENT_TIMESTAMP
         WHERE proprietor_uuid_bin = uuid_to_bin(#{uuid})
           AND is_archived = 0
    """)
    int archiveAllFirms(@Param("uuid") String uuid);

}
