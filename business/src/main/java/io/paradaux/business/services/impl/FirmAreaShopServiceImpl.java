package io.paradaux.business.services.impl;

import com.google.inject.Singleton;
import io.paradaux.business.services.FirmAreaShopService;

@Singleton
public class FirmAreaShopServiceImpl implements FirmAreaShopService {

    @Override
    public boolean isValidPlot(String plotName) {
        return true;
    }
}
