package com.aresstack.enterpriseai.source.betaview;

import java.io.IOException;
import java.util.Map;

interface BetaViewClient {
    BetaViewSession login(String user, char[] password) throws IOException;

    String getText(BetaViewSession session, String relativePath) throws IOException;

    byte[] getBinary(BetaViewSession session, String relativePath) throws IOException;

    String postFormText(BetaViewSession session, String relativePath, Map<String, String> fields) throws IOException;

    byte[] postFormBinary(BetaViewSession session, String relativePath, Map<String, String> fields) throws IOException;

    DownloadResult postFormDownload(BetaViewSession session, String relativePath, Map<String, String> fields) throws IOException;
}
