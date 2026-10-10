package com.aresstack.enterpriseai.source.ndv.pal.core.api;

public interface IRegional {
   String getCodePage();

   boolean isConvErr();

   boolean isRetain();

   boolean isUtf8();

   void setCodePage(String var1);
}
