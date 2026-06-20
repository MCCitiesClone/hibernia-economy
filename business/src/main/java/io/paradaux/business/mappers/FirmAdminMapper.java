package io.paradaux.business.mappers;

import org.apache.ibatis.annotations.Delete;

public interface FirmAdminMapper {

    @Delete("""
        DELETE FROM firm
        WHERE proprietor_uuid_bin = uuid_to_bin(#{uuid})
    """)
    void deleteAllFirms(String uuid);

}
