package com.aresstack.enterpriseai.source.ndv.pal.core.api;

import com.aresstack.enterpriseai.source.ndv.pal.core.impl.type.IPalType;

public interface IPalTypeUtility extends IPalType {
   byte[] getUtilityRecord();

   void setUtilityRecord(byte[] var1);
}
