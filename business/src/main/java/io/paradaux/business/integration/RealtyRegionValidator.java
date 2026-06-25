package io.paradaux.business.integration;

import com.google.inject.Singleton;
import io.paradaux.business.services.RegionValidator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validates region/plot ids against the Realty plugin's {@code RealtyBackend}
 * service, obtained at runtime from Bukkit's {@code ServicesManager}.
 *
 * <p><b>Why reflection.</b> Realty is a vendored submodule but it is not
 * published to any Maven repository the {@code business} build resolves against
 * (its artifacts default to an unset {@code deployUrl}), so we cannot add a
 * {@code compileOnly} dependency without breaking CI and fresh checkouts the way
 * we do for Treasury/CarbonChat. Realty is therefore a soft dependency reached
 * purely reflectively; when it is absent every lookup reports "no provider"
 * ({@link Optional#empty()}) and the caller fails open.
 *
 * <p>A firm HQ "plot" is a WorldGuard region id with no world attached, so we
 * probe every loaded world and treat the region as valid if Realty knows it in
 * any of them ({@code RealtyBackend.getRegionState(regionId, worldId) != null}).
 *
 * <p>This class is intentionally excluded from the coverage gate (Bukkit +
 * reflection glue that cannot be exercised without a running server with Realty
 * installed); the decision logic that can be tested lives in
 * {@code FirmAreaShopServiceImpl}.
 */
@Singleton
public class RealtyRegionValidator implements RegionValidator {

    private static final String BACKEND_CLASS = "io.github.md5sha256.realty.api.RealtyBackend";
    private static final Logger LOG = Logger.getLogger("Business");

    /** Cached reflective handle to {@code RealtyBackend#getRegionState}; resolved lazily. */
    private Method getRegionState;
    private boolean resolutionAttempted;

    @Override
    public Optional<Boolean> validate(String regionId) {
        Object backend = resolveBackend();
        if (backend == null) {
            return Optional.empty();
        }

        Method method = getRegionState;
        if (method == null) {
            return Optional.empty();
        }

        try {
            for (World world : Bukkit.getWorlds()) {
                Object state = method.invoke(backend, regionId, world.getUID());
                if (state != null) {
                    return Optional.of(true);
                }
            }
            // Realty is present and recognised no world holding this region.
            return Optional.of(false);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOG.log(Level.WARNING, "Realty region lookup failed for '" + regionId
                    + "'; treating as unvalidated.", ex);
            return Optional.empty();
        }
    }

    /**
     * @return the live {@code RealtyBackend} service instance, or {@code null}
     *         when Realty is not installed / not yet registered.
     */
    private Object resolveBackend() {
        try {
            Class<?> backendClass = Class.forName(BACKEND_CLASS);
            RegisteredServiceProvider<?> rsp =
                    Bukkit.getServicesManager().getRegistration(backendClass);
            if (rsp == null) {
                return null;
            }
            Object provider = rsp.getProvider();
            if (!resolutionAttempted) {
                resolutionAttempted = true;
                getRegionState = backendClass.getMethod("getRegionState", String.class, java.util.UUID.class);
            }
            return provider;
        } catch (ClassNotFoundException ex) {
            return null; // Realty not on the classpath at all.
        } catch (NoSuchMethodException ex) {
            resolutionAttempted = true;
            LOG.log(Level.WARNING, "RealtyBackend#getRegionState not found; "
                    + "Realty API may have changed. HQ validation disabled.", ex);
            return null;
        }
    }
}
