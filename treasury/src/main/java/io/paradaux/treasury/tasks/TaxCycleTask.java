package io.paradaux.treasury.tasks;

import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.api.TaxApi;
import io.paradaux.treasury.event.TaxCycleEvent;
import io.paradaux.treasury.model.tax.TaxCycleReport;
import io.paradaux.treasury.model.tax.TaxCycleType;
import io.paradaux.treasury.services.TaxCycleRegistry;
import io.paradaux.treasury.services.TaxWebhookService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Schedules and fires {@link TaxCycleEvent} for a single {@link TaxCycleType}.
 *
 * <p>Treasury creates one instance per enabled cycle type on startup. Each task
 * calculates the ticks until the next occurrence of its configured fire time,
 * waits that long, then fires the Bukkit event and reschedules itself for the
 * next period.
 *
 * <p>Consuming plugins listen for {@link TaxCycleEvent} and call
 * {@link TaxApi#collectTax} or {@link TaxApi#collectBatch} inside the handler.
 * Treasury only drives the schedule.
 */
@Slf4j
public class TaxCycleTask extends BukkitRunnable {

    private static final long TICKS_PER_SECOND = 20L;

    private final Plugin plugin;
    private final TaxApi taxApi;
    private final TaxCycleRegistry cycleRegistry;
    private final TaxWebhookService webhookService;
    private final TaxCycleType cycleType;
    private final int fireHour;
    private final int fireDayOfWeek;   // ISO 1 = Monday … 7 = Sunday (weekly only)
    private final int fireDayOfMonth;  // 1–28 (monthly only)

    private TaxCycleTask(Plugin plugin, TaxApi taxApi, TaxCycleRegistry cycleRegistry,
                         TaxWebhookService webhookService, TaxCycleType cycleType,
                         int fireHour, int fireDayOfWeek, int fireDayOfMonth) {
        this.plugin         = plugin;
        this.taxApi         = taxApi;
        this.cycleRegistry  = cycleRegistry;
        this.webhookService = webhookService;
        this.cycleType      = cycleType;
        this.fireHour       = fireHour;
        this.fireDayOfWeek  = fireDayOfWeek;
        this.fireDayOfMonth = fireDayOfMonth;
    }

    // ---- Factory methods ----

    public static TaxCycleTask daily(Plugin plugin, TaxApi taxApi, TaxCycleRegistry registry,
                                     TaxWebhookService webhookService, int fireHour) {
        return new TaxCycleTask(plugin, taxApi, registry, webhookService,
                TaxCycleType.DAILY, fireHour, 1, 1);
    }

    public static TaxCycleTask weekly(Plugin plugin, TaxApi taxApi, TaxCycleRegistry registry,
                                      TaxWebhookService webhookService, int fireHour, int fireDayOfWeek) {
        return new TaxCycleTask(plugin, taxApi, registry, webhookService,
                TaxCycleType.WEEKLY, fireHour, fireDayOfWeek, 1);
    }

    public static TaxCycleTask monthly(Plugin plugin, TaxApi taxApi, TaxCycleRegistry registry,
                                       TaxWebhookService webhookService, int fireHour, int fireDayOfMonth) {
        return new TaxCycleTask(plugin, taxApi, registry, webhookService,
                TaxCycleType.MONTHLY, fireHour, 1, fireDayOfMonth);
    }

    // ---- Scheduling ----

    public void scheduleNext() {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = nextFireTime(now);
        Instant nextInstant = next.toInstant();

        long seconds = Math.max(1, ChronoUnit.SECONDS.between(now, next));
        long delayTicks = seconds * TICKS_PER_SECOND;

        log.info("{} cycle scheduled — next fire {} (~{}s from now)",
                cycleType, nextInstant, seconds);

        cycleRegistry.notifyNextFire(cycleType, nextInstant);
        runTaskLaterAsynchronously(plugin, delayTicks);
    }

    @Override
    public void run() {
        Instant periodStart = Instant.now().truncatedTo(ChronoUnit.HOURS);
        log.info("Firing {} tax cycle (period start: {})", cycleType, periodStart);

        cycleRegistry.startSession(cycleType, periodStart, false, null);

        TaxCycleEvent event = new TaxCycleEvent(cycleType, periodStart, taxApi);
        Bukkit.getPluginManager().callEvent(event);

        TaxCycleReport report = cycleRegistry.endSession();
        if (report != null) {
            log.info("{} cycle complete: collected={} skipped={} failed={} total={}",
                    cycleType, report.collectedCount(), report.skippedCount(),
                    report.failedCount(), report.totalCollected());
            webhookService.sendCycleReport(report);
        }

        TaxCycleTask next = new TaxCycleTask(plugin, taxApi, cycleRegistry, webhookService,
                cycleType, fireHour, fireDayOfWeek, fireDayOfMonth);
        next.scheduleNext();
    }

    // ---- Delay calculation ----

    private ZonedDateTime nextFireTime(ZonedDateTime now) {
        return switch (cycleType) {
            case DAILY   -> nextDailyFire(now);
            case WEEKLY  -> nextWeeklyFire(now);
            case MONTHLY -> nextMonthlyFire(now);
        };
    }

    private ZonedDateTime nextDailyFire(ZonedDateTime now) {
        ZonedDateTime candidate = now.toLocalDate().atStartOfDay(now.getZone()).plusHours(fireHour);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private ZonedDateTime nextWeeklyFire(ZonedDateTime now) {
        ZonedDateTime candidate = now.toLocalDate().atStartOfDay(now.getZone()).plusHours(fireHour);
        int daysUntil = (fireDayOfWeek - now.getDayOfWeek().getValue() + 7) % 7;
        candidate = candidate.plusDays(daysUntil);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate;
    }

    private ZonedDateTime nextMonthlyFire(ZonedDateTime now) {
        int clampedDay = Math.min(fireDayOfMonth, now.toLocalDate().lengthOfMonth());
        ZonedDateTime candidate = now.toLocalDate().withDayOfMonth(clampedDay)
                .atStartOfDay(now.getZone()).plusHours(fireHour);
        if (!candidate.isAfter(now)) {
            ZonedDateTime nextMonth = now.plusMonths(1);
            int nextClampedDay = Math.min(fireDayOfMonth, nextMonth.toLocalDate().lengthOfMonth());
            candidate = nextMonth.toLocalDate().withDayOfMonth(nextClampedDay)
                    .atStartOfDay(now.getZone()).plusHours(fireHour);
        }
        return candidate;
    }
}
