package com.aresstack.enterpriseai.source.ndv.pal.core.api;

public interface ILimit {
   int getFlags();

   int getMaximumCPUTime();

   int getPageDataSet();

   int getProcessingLoopLimit();

   void setProcessingLoopLimit(int var1);
}
