package com.aresstack.enterpriseai.source.ndv.pal.core.api;

import com.aresstack.enterpriseai.source.ndv.pal.core.impl.type.IPalType;

public interface IPalTypeDbgStatus extends IPalType {
   boolean isCtxModified();

   boolean isAivModified();

   boolean isGdaModified();

   boolean isTerminated();
}
