/**
 * Copyright 2013 AppDynamics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appdynamics.extensions.solr;

import java.util.Map;

import com.singularity.ee.util.httpclient.*;
import org.apache.commons.httpclient.HttpClient;
import org.apache.log4j.Logger;

import com.appdynamics.extensions.solr.stats.CacheStats;
import com.appdynamics.extensions.solr.stats.CoreStats;
import com.appdynamics.extensions.solr.stats.MemoryStats;
import com.appdynamics.extensions.solr.stats.QueryStats;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import com.singularity.ee.util.log4j.Log4JLogger;

public class SolrMonitor extends AManagedMonitor {

	private static Logger LOG = Logger.getLogger("com.singularity.extensions.SolrMonitor");

	private static final String METRIC_PATH_PREFIX = "Custom Metrics|Solr|";
	private static final String PING_URI = "/solr/admin/ping";

	private String host;
	private String port;

	private IHttpClientWrapper httpClient;

	public SolrMonitor() {
		String msg = "Using Monitor Version [" + getImplementationVersion() + "]";
		LOG.info(msg);
		System.out.println(msg);
	}

	/*
	 * Main execution method that uploads the metrics to AppDynamics Controller
	 * (non-Javadoc)
	 *
	 * @see
	 * com.singularity.ee.agent.systemagent.api.ITask#execute(java.util.Map,
	 * com.singularity.ee.agent.systemagent.api.TaskExecutionContext)
	 */
	public TaskOutput execute(Map<String, String> taskArguments, TaskExecutionContext arg1) throws TaskExecutionException {

		host = taskArguments.get("host");
		port = taskArguments.get("port");


        if (httpClient == null) {
            if (Boolean.getBoolean("com.appdynamics.extensions.solr.useproxy")) {
                httpClient = HttpClientWrapper.getInstance();
            } else {
                HttpClient client = HttpClientWrapper.getDefaultHttpClient();
                client.getHostConfiguration().setProxyHost(null);
                httpClient = new SimpleHttpClientWrapper(client);
            }
        }

		// Checks Solr health status. If up, fetches all metrics
		try {
			pingSolr();
		} catch (Exception e) {
			LOG.error("Connection to Solr failed", e);
			return new TaskOutput("Connection to Solr failed");
		}

		// Fetches and prints core metrics (number of docs, deleted docs) to
		// Controller
		try {
			CoreStats coreStats = new CoreStats(host, port, httpClient);
			coreStats.populateStats();
			printMetrics(coreStats);
		} catch (Exception e) {
			LOG.error("Error Retrieving Core Stats", e);
		}

		// Fetches query metrics
		try {
			QueryStats queryStats = new QueryStats(host, port, httpClient);
			queryStats.populateStats();
			printMetrics(queryStats);
		} catch (Exception e) {
			LOG.error("Error Retrieving Query Stats", e);
		}

		// Fetches JVM Memory and System Memory Stats
		try {
			MemoryStats memoryStats = new MemoryStats(host, port, httpClient);
			memoryStats.populateStats();
			printMetrics(memoryStats);
		} catch (Exception e) {
			LOG.error("Error Retrieving Memory Stats", e);
		}

		// Fetches Cache metrics
		try {
			CacheStats cacheStats = new CacheStats(host, port, httpClient);
			cacheStats.populateStats();
			printMetrics(cacheStats);
		} catch (Exception e) {
			LOG.error("Error Retrieving Cache Stats", e);
		}

		return new TaskOutput("End of execute method");
	}

	/**
	 * Checks Solr health status. If up proceeds further to fetch desired
	 * metrics
	 */
	private void pingSolr() {
		IHttpClientWrapper httpClient = HttpClientWrapper.getInstance();
		HttpExecutionRequest request = new HttpExecutionRequest(pingURL(), "", HttpOperation.GET);
		HttpExecutionResponse response = httpClient.executeHttpOperation(request, new Log4JLogger(LOG));
		if (response.getStatusCode() == 200) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Connected to Solr successfully");
			}
		} else {
			throw new RuntimeException("Error in connecting to " + pingURL() + " with HTTP status code " + response.getStatusCode());
		}
	}

	private void printMetrics(CoreStats stats) {
		String metricPath = "Core|";
		printMetric(metricPath, "Number of Docs", stats.getNumDocs());
		printMetric(metricPath, "Max Docs", stats.getMaxDocs());
		printMetric(metricPath, "Deleted Docs", stats.getDeletedDocs());
	}

	private void printMetrics(CacheStats cacheStats) {
		String metricPath = "Cache|";
		String queryCachePath = metricPath + "QueryResultCache|";
		String documentCachePath = metricPath + "DocumentCache|";
		String fieldCachePath = metricPath + "FieldValueCache|";
		String filterCachePath = metricPath + "FilterCache|";

		printMetric(queryCachePath, "HitRatio", cacheStats.getQueryResultCacheHitRatio());
		printMetric(queryCachePath, "HitRatioCumulative", cacheStats.getQueryResultCacheHitRatioCumulative());
		printMetric(queryCachePath, "CacheSize (Bytes)", cacheStats.getQueryResultCacheSize());
		printMetric(documentCachePath, "HitRatio", cacheStats.getDocumentCacheHitRatio());
		printMetric(documentCachePath, "HitRatioCumulative", cacheStats.getDocumentCacheHitRatioCumulative());
		printMetric(documentCachePath, "CacheSize (Bytes)", cacheStats.getDocumentCacheSize());
		printMetric(fieldCachePath, "HitRatio", cacheStats.getFieldValueCacheHitRatio());
		printMetric(fieldCachePath, "HitRatioCumulative", cacheStats.getFieldValueCacheHitRatioCumulative());
		printMetric(fieldCachePath, "CacheSize (Bytes)", cacheStats.getFieldValueCacheSize());
		printMetric(filterCachePath, "HitRatio", cacheStats.getFilterCacheHitRatio());
		printMetric(filterCachePath, "HitRatioCumulative", cacheStats.getFilterCacheHitRatioCumulative());
		printMetric(filterCachePath, "CacheSize (Bytes)", cacheStats.getFilterCacheSize());
	}

	private void printMetrics(MemoryStats memoryStats) {
		String metricPath = "Memory|";
		String jvmPath = metricPath + "JVMMemory|";
		String systemPath = metricPath + "SystemMemory|";

		printMetric(jvmPath, "Used (MB)", memoryStats.getJvmMemoryUsed());
		printMetric(jvmPath, "Free (MB)", memoryStats.getJvmMemoryFree());
		printMetric(jvmPath, "Total (MB)", memoryStats.getJvmMemoryTotal());
		printMetric(systemPath, "Free Physical Memory(MB)", memoryStats.getFreePhysicalMemorySize());
		printMetric(systemPath, "Total Physical Memory(MB)", memoryStats.getTotalPhysicalMemorySize());
		printMetric(systemPath, "Committed Virtual Memory(MB)", memoryStats.getCommittedVirtualMemorySize());
		printMetric(systemPath, "Free Swap Size (MB)", memoryStats.getFreeSwapSpaceSize());
		printMetric(systemPath, "Total Swap Size (MB)", memoryStats.getTotalSwapSpaceSize());
		printMetric(systemPath, "Open File Descriptor Count", memoryStats.getOpenFileDescriptorCount());
		printMetric(systemPath, "Max File Descriptor Count", memoryStats.getMaxFileDescriptorCount());

	}

	private void printMetrics(QueryStats stats) {
		String metricPath = "Query|";
		printMetric(metricPath, "Average Rate (requests per second)", stats.getAvgRate());
		printMetric(metricPath, "5 Minute Rate (requests per second)", stats.getRate5min());
		printMetric(metricPath, "15 Minute Rate (requests per second)", stats.getRate15min());
		printMetric(metricPath, "Average Time Per Request (milliseconds)", stats.getAvgTimePerRequest());
		printMetric(metricPath, "Median Request Time (milliseconds)", stats.getMedianRequestTime());
		printMetric(metricPath, "95th Percentile Request Time (milliseconds)", stats.getPcRequestTime95th());

	}

	/**
	 * Prints Metrics to AppDynamics Metric Browser
	 *
	 * @param metricPath
	 * @param metricName
	 * @param metricValue
	 */
	private void printMetric(String metricPath, String metricName, Object metricValue) {
		printMetric(getMetricPrefix() + metricPath, metricName, metricValue, MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
	}

	private void printMetric(String metricPath, String metricName, Object metricValue, String aggregation, String timeRollup, String cluster) {
		MetricWriter metricWriter = super.getMetricWriter(metricPath + metricName, aggregation, timeRollup, cluster);
		if (metricValue instanceof Double) {
			metricWriter.printMetric(String.valueOf(Math.round((Double) metricValue)));
		} else if (metricValue instanceof Float) {
			metricWriter.printMetric(String.valueOf(Math.round((Float) metricValue)));
		} else {
			metricWriter.printMetric(String.valueOf(metricValue));
		}
	}

	private String getMetricPrefix() {
		return METRIC_PATH_PREFIX;
	}

	public static String getImplementationVersion() {
		return SolrMonitor.class.getPackage().getImplementationTitle();
	}

	public String pingURL() {
		return "http://" + host + ":" + port + PING_URI;
	}

}
