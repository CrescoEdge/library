package io.cresco.library.metrics;


/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.codahale.metrics.MetricRegistry;
import io.cresco.library.plugin.PluginBuilder;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

/**
 * @author Jon Schneider
 */
public class CrescoMeterRegistry extends DropwizardMeterRegistry {
    private final CrescoReporter reporter;

    public CrescoMeterRegistry(PluginBuilder plugin,String instanceName) {
        this(plugin, instanceName, CrescoConfig.DEFAULT,Clock.SYSTEM);
    }

    public CrescoMeterRegistry(PluginBuilder plugin, String instanceName, CrescoConfig config, Clock clock) {
        this(plugin,instanceName,config, clock, HierarchicalNameMapper.DEFAULT);
    }

    public CrescoMeterRegistry(PluginBuilder plugin, String instanceName, CrescoConfig config, Clock clock, HierarchicalNameMapper nameMapper) {
        this(plugin,instanceName,config, clock, nameMapper, new MetricRegistry());
    }

    public CrescoMeterRegistry(PluginBuilder plugin, String instanceName, CrescoConfig config, Clock clock, HierarchicalNameMapper nameMapper, MetricRegistry metricRegistry) {
        this(plugin,instanceName, config, clock, nameMapper, metricRegistry, defaultCrescoReporter(plugin,instanceName, config, metricRegistry));
    }

    public CrescoMeterRegistry(PluginBuilder plugin, String instanceName, CrescoConfig config, Clock clock, HierarchicalNameMapper nameMapper, MetricRegistry metricRegistry,
                               CrescoReporter crescoReporter) {
        super(config, metricRegistry, nameMapper, clock);
        this.reporter = crescoReporter;
        this.reporter.start();
    }

    private static CrescoReporter defaultCrescoReporter(PluginBuilder plugin, String instanceName, CrescoConfig config, MetricRegistry metricRegistry) {
        return CrescoReporter.forRegistry(plugin, metricRegistry)
                .inDomain(instanceName)
                .build();
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start();
    }

    @Override
    public void close() {
        stop();
        super.close();
    }

    @Override
    protected Double nullGaugeValue() {
        return Double.NaN;
    }
}