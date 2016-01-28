package com.netflix.evcache.metrics;

public interface EVCacheMetricsMBean {

    long getGetCalls();

    long getBulkCalls();

    long getSetCalls();

    long getCacheHits();

    long getCacheMiss();

    long getGetDuration();

    long getBulkDuration();
    
    long getBulkHits();
    
    long getBulkMiss();
    
    double getHitRate();
    
    double getBulkHitRate();
}