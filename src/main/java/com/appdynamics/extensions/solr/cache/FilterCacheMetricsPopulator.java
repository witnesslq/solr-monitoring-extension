package com.appdynamics.extensions.solr.cache;

import com.appdynamics.extensions.solr.helpers.SolrUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

public class FilterCacheMetricsPopulator {
    private static final String METRIC_SEPARATOR = "|";
    private static final int PERCENT_MULTIPLIER = 100;
    private static final Logger logger = LoggerFactory.getLogger(FilterCacheMetricsPopulator.class);
    private Map<String, JsonNode> solrMBeansHandlersMap;
    private String collection;

    public FilterCacheMetricsPopulator (Map<String, JsonNode> solrMBeansHandlersMap, String collection) {
        this.solrMBeansHandlersMap = solrMBeansHandlersMap;
        this.collection = collection;
    }

    public Map<String, Long> populate() {
        Map<String, Long> filterCacheMetrics = new HashMap<String, Long>();
        String metricPath = METRIC_SEPARATOR + "Cores" + METRIC_SEPARATOR + collection + METRIC_SEPARATOR + "CACHE" +
                METRIC_SEPARATOR;
        String filterCachePath = metricPath + "FilterCache" + METRIC_SEPARATOR;
        JsonNode cacheNode = solrMBeansHandlersMap.get("CACHE");
        JsonNode filterCacheStats = cacheNode.path("filterCache").path("stats");

        if (!filterCacheStats.isMissingNode()) {
            filterCacheMetrics.put(filterCachePath + "HitRatio %", SolrUtils.convertDoubleToLong(SolrUtils.multipyBy
                    (filterCacheStats.path("hitratio").asDouble(), PERCENT_MULTIPLIER)));
            filterCacheMetrics.put(filterCachePath + "HitRatioCumulative %", SolrUtils.convertDoubleToLong(SolrUtils
                    .multipyBy(filterCacheStats.path("cumulative_hitratio").asDouble(), PERCENT_MULTIPLIER)));
            filterCacheMetrics.put(filterCachePath + "CacheSize (Bytes)", SolrUtils.convertDoubleToLong(filterCacheStats.path("size").asDouble()));
        } else {
            logger.info("filterCache is disabled in solrconfig.xml");
        }
        return filterCacheMetrics;
    }
}