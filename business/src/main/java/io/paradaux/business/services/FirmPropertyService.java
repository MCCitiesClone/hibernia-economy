package io.paradaux.business.services;

import java.math.BigDecimal;
import java.util.Optional;

public interface FirmPropertyService {

    Optional<String> getString(int firmId, String key);
    Optional<Integer> getInteger(int firmId, String key);
    Optional<BigDecimal> getBigDecimal(int firmId, String key);
    Optional<Boolean> getBoolean(int firmId, String key);

    void setString(int firmId, String key, String value);
    void setInteger(int firmId, String key, int value);
    void setBigDecimal(int firmId, String key, BigDecimal value);
    void setBoolean(int firmId, String key, boolean value);

    void delete(int firmId, String key);
}
