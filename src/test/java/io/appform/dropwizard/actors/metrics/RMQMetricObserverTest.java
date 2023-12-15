package io.appform.dropwizard.actors.metrics;

import com.codahale.metrics.MetricRegistry;
import io.appform.dropwizard.actors.common.RMQOperations;
import io.appform.dropwizard.actors.config.MetricConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RMQMetricObserverTest {

    private RMQMetricObserver publishMetricObserver;
    private RMQConfig config;
    private final MetricRegistry metricRegistry = new MetricRegistry();

    @BeforeEach
    public void setup() {
        this.config = RMQConfig.builder()
                .brokers(new ArrayList<>())
                .userName("")
                .threadPoolSize(1)
                .password("")
                .secure(false)
                .startupGracePeriodSeconds(1)
                .metricConfig(MetricConfig.builder().enabledForAll(true).build())
                .build();
        this.publishMetricObserver = new RMQMetricObserver(config, metricRegistry);
    }

    @Test
    void testExecuteWhenMetricNotApplicable() {
        val config = this.config;
        config.setMetricConfig(MetricConfig.builder().enabledForAll(false).build());
        val publishMetricObserver = new RMQMetricObserver(config, metricRegistry);
        val headers = new HashMap<String, Object>();
        assertEquals(terminate(headers),
                publishMetricObserver.executePublish(PublishObserverContext.builder().build(), this::terminate, headers));
    }

    @Test
    void testExecuteWithNoException() {
        val context = PublishObserverContext.builder()
                .operation(RMQOperations.PUBLISH.name())
                .queueName("default")
                .build();
        val headers = new HashMap<String, Object>();
        assertEquals(terminate(headers), publishMetricObserver.executePublish(context, this::terminate, headers));
        val key = MetricKeyData.builder().operation(context.getOperation()).queueName(context.getQueueName()).build();
        validateMetrics(publishMetricObserver.getMetricCache().get(key), 1, 0);
    }

    @Test
    void testExecuteWithException() {
        val context = PublishObserverContext.builder()
                .operation(RMQOperations.PUBLISH.name())
                .queueName("default")
                .build();
        assertThrows(RuntimeException.class, () -> publishMetricObserver.executePublish(context, this::terminateWithException, new HashMap<>()));
        val key = MetricKeyData.builder().operation(context.getOperation()).queueName(context.getQueueName()).build();
        validateMetrics(publishMetricObserver.getMetricCache().get(key), 0, 1);
    }

    private void validateMetrics(final MetricData metricData,
                                 final int successCount,
                                 final int failedCount) {
        assertEquals(1, metricData.getTotal().getCount());
        assertEquals(1, metricData.getTimer().getCount());
        assertEquals(successCount, metricData.getSuccess().getCount());
        assertEquals(failedCount, metricData.getFailed().getCount());
    }

    private Integer terminate(HashMap<String, Object> headers) {
        return 1;
    }

    private Integer terminateWithException(HashMap<String, Object> headers) {
        throw new RuntimeException();
    }
}
