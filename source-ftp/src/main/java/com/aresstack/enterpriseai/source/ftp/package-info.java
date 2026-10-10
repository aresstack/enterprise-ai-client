/**
 * Adapter: FTP-Server, vor allem MVS/z/OS, als Wissensquelle (COBOL-Quellen in PDS-Membern).
 *
 * <p>Öffentlich ist nur der Quelltyp ({@link com.aresstack.enterpriseai.source.ftp.FtpSourceProvider}); die
 * Connector-Registry (resource-holkas) bedient das Schema {@code ftp} über den Quellen-Port, die Oberfläche sieht nur
 * „+ Quelle“. Apache Commons Net bleibt paketintern, Zugangsdaten kommen nur beim Verbindungsaufbau über den
 * Security-Port.
 *
 * <p>Herkunft: MVS-Pfade aus corenth ({@code holkas.mvs}), Sitzungsschnitt nach corenth ({@code holkas.ftp}),
 * Verbindung, MVS-Auflistung und Satzstruktur aus MainframeMate ({@code CommonsNetFtpFileService},
 * {@code RecordStructureCodec}).
 */
package com.aresstack.enterpriseai.source.ftp;
