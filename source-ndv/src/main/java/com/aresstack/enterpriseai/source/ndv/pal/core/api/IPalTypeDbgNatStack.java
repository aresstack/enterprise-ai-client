package com.aresstack.enterpriseai.source.ndv.pal.core.api;

import com.aresstack.enterpriseai.source.ndv.pal.core.impl.type.IPalType;

public interface IPalTypeDbgNatStack extends IPalType {
   boolean isUnicode();

   boolean isData();

   boolean isDataFormatted();

   boolean isCommand();

   String getStackEntry();
}
