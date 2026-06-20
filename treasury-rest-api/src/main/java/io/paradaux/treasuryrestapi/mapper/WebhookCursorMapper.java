package io.paradaux.treasuryrestapi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Single-row global tail watermark for the webhook dispatcher (webhook_cursor.id = 1). */
@Mapper
public interface WebhookCursorMapper {

    @Select("SELECT last_dispatched_txn_id FROM webhook_cursor WHERE id = 1")
    Long get();

    /** Monotonic advance — never moves the cursor backwards. */
    @Update("UPDATE webhook_cursor SET last_dispatched_txn_id = #{txnId} "
            + "WHERE id = 1 AND last_dispatched_txn_id < #{txnId}")
    int advanceTo(@Param("txnId") long txnId);
}
