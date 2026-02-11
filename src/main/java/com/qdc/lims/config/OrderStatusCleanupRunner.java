package com.qdc.lims.config;

import com.qdc.lims.repository.LabOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup cleanup to normalize stale IN_PROGRESS locks back to PENDING.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderStatusCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusCleanupRunner.class);

    private final LabOrderRepository labOrderRepository;

    public OrderStatusCleanupRunner(LabOrderRepository labOrderRepository) {
        this.labOrderRepository = labOrderRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = labOrderRepository.normalizeStaleInProgressToPending();
        if (updated > 0) {
            log.info("Order status cleanup complete. Reset {} stale IN_PROGRESS orders to PENDING.", updated);
        }
    }
}
