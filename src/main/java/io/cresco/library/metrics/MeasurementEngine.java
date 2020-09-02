package io.cresco.library.metrics;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MeasurementEngine {

    private PluginBuilder plugin;
    private CLogger logger;
    private CrescoMeterRegistry crescoMeterRegistry;

    private Gson gson;
    private Map<String, CMetric> metricMap;
    private Map<String,AtomicInteger> gaugeMapInteger;
    private Map<String, AtomicLong> gaugeMapLong;
    private Map<String, AtomicDouble> gaugeMapDouble;

    public MeasurementEngine(PluginBuilder plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger(MeasurementEngine.class.getName(), CLogger.Level.Info);

        this.metricMap = new ConcurrentHashMap<>();
        this.gaugeMapInteger = new ConcurrentHashMap<>();
        this.gaugeMapLong = new ConcurrentHashMap<>();
        this.gaugeMapDouble = new ConcurrentHashMap<>();
        gson = new Gson();
        crescoMeterRegistry = plugin.getCrescoMeterRegistry();

    }

    public CrescoMeterRegistry getCrescoMeterRegistry() {
        return crescoMeterRegistry;
    }

    public List<Map<String,String>> getMetricGroupList(String group) {
        List<Map<String,String>> returnList = null;
        try {
            returnList = new ArrayList<>();

            for (Map.Entry<String, CMetric> entry : metricMap.entrySet()) {
                CMetric metric = entry.getValue();
                if(metric.group.equals(group)) {
                    returnList.add(writeMetricMap(metric));
                }
            }
        } catch(Exception ex) {
            logger.error(ex.getMessage());
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            logger.error(sw.toString());
        }
        return returnList;
    }

    public Map<String,String> writeMetricMap(CMetric metric) {

        Map<String,String> metricValueMap = null;

        try {

            metricValueMap = new HashMap<>();

            switch (metric.measureClass) {

                case TIMER:
                    TimeUnit timeUnit = crescoMeterRegistry.get(metric.name).timer().baseTimeUnit();
                    metricValueMap.put("name",metric.name);
                    metricValueMap.put("class",metric.getMeterTypeString());
                    metricValueMap.put("type",metric.type.toString());
                    metricValueMap.put("mean",String.valueOf(crescoMeterRegistry.get(metric.name).timer().mean(timeUnit)));
                    metricValueMap.put("max",String.valueOf(crescoMeterRegistry.get(metric.name).timer().max(timeUnit)));
                    metricValueMap.put("totaltime",String.valueOf(crescoMeterRegistry.get(metric.name).timer().totalTime(timeUnit)));
                    metricValueMap.put("count",String.valueOf(crescoMeterRegistry.get(metric.name).timer().count()));
                    break;

                case GAUGE_INT:
                    metricValueMap.put("name",metric.name);
                    metricValueMap.put("class",metric.getMeterTypeString());
                    metricValueMap.put("type",metric.type.toString());
                    metricValueMap.put("value",String.valueOf(gaugeMapInteger.get(metric.name).get()));
                    break;

                case GAUGE_LONG:
                    metricValueMap.put("name",metric.name);
                    metricValueMap.put("class",metric.getMeterTypeString());
                    metricValueMap.put("type",metric.type.toString());
                    metricValueMap.put("value",String.valueOf(gaugeMapLong.get(metric.name).get()));
                    break;

                case GAUGE_DOUBLE:
                    metricValueMap.put("name",metric.name);
                    metricValueMap.put("class",metric.getMeterTypeString());
                    metricValueMap.put("type",metric.type.toString());
                    metricValueMap.put("value",String.valueOf(gaugeMapDouble.get(metric.name).get()));
                    break;

                case GAUGE_AUTO:
                    metricValueMap.put("name",metric.name);
                    metricValueMap.put("class",metric.getMeterTypeString());
                    metricValueMap.put("type",metric.type.toString());
                    metricValueMap.put("value",String.valueOf(crescoMeterRegistry.get(metric.name).gauge().value()));
                    break;

                case DISTRIBUTION_SUMMARY:
                    metricValueMap.put("name",metric.name);
                    metricValueMap.put("class",metric.getMeterTypeString());
                    metricValueMap.put("type",metric.type.toString());
                    metricValueMap.put("mean",String.valueOf(crescoMeterRegistry.get(metric.name).summary().mean()));
                    metricValueMap.put("max",String.valueOf(crescoMeterRegistry.get(metric.name).summary().max()));
                    metricValueMap.put("totalAmount",String.valueOf(crescoMeterRegistry.get(metric.name).summary().totalAmount()));
                    metricValueMap.put("count",String.valueOf(crescoMeterRegistry.get(metric.name).summary().count()));
                    break;

                case COUNTER:
                    metricValueMap.put("name",metric.name);
                    metricValueMap.put("class",metric.getMeterTypeString());
                    metricValueMap.put("type",metric.type.toString());
                    try {
                        metricValueMap.put("count", String.valueOf(crescoMeterRegistry.get(metric.name).functionCounter().count()));
                    } catch (Exception ex) {
                        metricValueMap.put("count", String.valueOf(crescoMeterRegistry.get(metric.name).counter().count()));
                    }
                    break;

                default:
                    logger.error("NO WRITER FOUND " + metric.measureClass);
            }

        } catch(Exception ex) {
            logger.error(ex.getMessage());
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            logger.error(sw.toString());
        }
        return metricValueMap;
    }

    public Boolean setDistributionSummary(String name, String description, String group) {

        if(metricMap.containsKey(name)) {
            return false;
        } else {
            DistributionSummary.builder(name).description(description).register(crescoMeterRegistry);
            metricMap.put(name,new CMetric(name,description,group, CMetric.MeasureClass.DISTRIBUTION_SUMMARY));
            return true;
        }
    }

    public DistributionSummary getDistributionSummary(String name) {
        if(metricMap != null) {
            if (metricMap.containsKey(name)) {
                if(this.crescoMeterRegistry != null) {
                    return this.crescoMeterRegistry.summary(name);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public void updateDistributionSummary(String name, double value) {

        DistributionSummary tmpDistributionSummary = getDistributionSummary(name);
        if(tmpDistributionSummary != null) {
            tmpDistributionSummary.record(value);
        }
    }

    public Boolean setTimer(String name, String description, String group) {

        if(metricMap.containsKey(name)) {
            return false;
        } else {
            Timer.builder(name).description(description).register(crescoMeterRegistry);
            metricMap.put(name,new CMetric(name,description,group, CMetric.MeasureClass.TIMER));
            return true;
        }
    }

    public Timer getTimer(String name) {
        if(metricMap != null) {
            if (metricMap.containsKey(name)) {
                if(this.crescoMeterRegistry != null) {
                    return this.crescoMeterRegistry.timer(name);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public Boolean setGauge(String name, String description, String group, CMetric.MeasureClass measureClass) {
        boolean isSet = false;
        try {
            if (metricMap.containsKey(name)) {
                return false;
            } else {

                switch (measureClass) {

                    case GAUGE_INT:
                        AtomicInteger myIntGauge = crescoMeterRegistry.gauge(name, new AtomicInteger(0));
                        gaugeMapInteger.put(name, myIntGauge);
                        break;
                    case GAUGE_LONG:
                        AtomicLong myLongGauge = crescoMeterRegistry.gauge(name, new AtomicLong(0));
                        gaugeMapLong.put(name, myLongGauge);
                        break;
                    case GAUGE_DOUBLE:
                        AtomicDouble myDoubleGauge = crescoMeterRegistry.gauge(name, new AtomicDouble(0));
                        gaugeMapDouble.put(name, myDoubleGauge);
                        break;
                }

                metricMap.put(name, new CMetric(name, description, group, measureClass));
                isSet = true;

            }
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            logger.error(sw.toString());
        }
        return isSet;

    }

    public void updateTimer(String name, long timeStamp) {

        Timer tmpTimer = getTimer(name);
        if(tmpTimer != null) {
            tmpTimer.record(System.nanoTime() - timeStamp, TimeUnit.NANOSECONDS);
        }
    }

    public void updateIntGauge(String name, int value) {

        if (gaugeMapInteger.containsKey(name)) {
            gaugeMapInteger.get(name).set(value);
        }

    }

    public void updateLongGauge(String name, long value) {

        if (gaugeMapLong.containsKey(name)) {
            gaugeMapLong.get(name).set(value);
        }

    }

    public void updateDoubleGauge(String name, double value) {

        if (gaugeMapDouble.containsKey(name)) {
            gaugeMapDouble.get(name).set(value);
        }

    }

    public Gauge getGauge(String name) {
        if(metricMap.containsKey(name)) {
            return this.crescoMeterRegistry.get(name).gauge();
        } else {
            return null;
        }
    }

    public Gauge getGaugeRaw(String name) {
            return this.crescoMeterRegistry.get(name).gauge();
    }

}
