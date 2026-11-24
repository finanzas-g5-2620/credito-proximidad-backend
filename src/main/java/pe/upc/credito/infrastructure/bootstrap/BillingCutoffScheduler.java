package pe.upc.credito.infrastructure.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import pe.upc.credito.application.service.SettlementService;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.demo-clock.enabled", havingValue = "false", matchIfMissing = true)
public class BillingCutoffScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingCutoffScheduler.class);

    private final StoreRepository stores;
    private final SettlementService settlements;

    public BillingCutoffScheduler(StoreRepository stores, SettlementService settlements) {
        this.stores = stores;
        this.settlements = settlements;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Lima")
    public void closeCompletedCycles() {
        for (var store : stores.findAll()) {
            if (!"ACTIVE".equals(store.getStatus())) {
                continue;
            }
            try {
                settlements.runCutoffForStore(store.getStoreId());
            } catch (RuntimeException error) {
                log.error("Automatic billing cutoff failed for store {}", store.getStoreId(), error);
            }
        }
    }
}
