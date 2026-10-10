package com.aresstack.enterpriseai.source.ndv.pal.core.api;

public interface IPalTimeoutHandler {
   boolean continueOperation();

   void addResultListener(IPalTimeoutResultListener var1);
}
