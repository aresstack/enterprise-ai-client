package com.aresstack.enterpriseai.source.ndv.pal.core.api;

import com.aresstack.enterpriseai.source.ndv.pal.core.impl.type.IPalType;

public interface IPalTypeDbgaRecord extends IPalType {
   String getClientId();

   String getProject();

   String getLibrary();

   String getObject();

   EDasRecordKind getKind();
}
