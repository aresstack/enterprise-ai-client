package com.aresstack.enterpriseai.security.keepassrpc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KeePassRPC über WebSocket: SRP-Pairing, Key-Challenge-Response-Login und verschlüsseltes JSON-RPC.
 *
 * <p>Protokoll und Nachrichtenformat aus MainframeMate {@code KeePassRpcClient} und
 * {@code KeePassRpcPairingDialog} (dort gegen KeePass 2.x/KeePassRPC erprobt, Protokoll {@code 1.7.2}, Referenz:
 * <a href="https://forum.kee.pm/t/keepassrpc-technical-detail/2364">KeePassRPC technical detail</a> und der
 * Go-Client {@code gkp}). Übernommen ist nur das Lesen von Einträgen; Anlegen/Ändern/Löschen aus MainframeMate
 * braucht dieses Projekt nicht. Abweichungen: kein Logging von Nachrichteninhalten (MainframeMate loggte
 * entschlüsselte Antworten auf FINE, also inklusive Passwörtern), Einträge nur bei exakt passendem Titel statt
 * "erster Treffer der URL-Suche", Base64 über {@code java.util.Base64} statt {@code javax.xml.bind}.
 */
final class WebSocketKeePassRpcTransport implements KeePassRpcTransport {

    /** Protokollversion {1,7,2} gepackt als (1&lt;&lt;16)|(7&lt;&lt;8)|2. */
    static final int PROTOCOL_VERSION = (1 << 16) | (7 << 8) | 2;
    private static final int SECURITY_LEVEL = 2;

    private final KeePassRpcConfig config;
    private final SecureRandom random = new SecureRandom();
    private final Gson gson = new Gson();

    WebSocketKeePassRpcTransport(KeePassRpcConfig config) {
        this.config = config;
    }

    @Override
    public char[] pair(KeePassPairingCallback callback) throws KeePassRpcException {
        try (KeePassRpcChannel channel = KeePassRpcChannel.open(config)) {
            KeePassRpcCrypto.SrpClient srp = new KeePassRpcCrypto.SrpClient(random);
            JsonObject identify = new JsonObject();
            identify.addProperty("stage", "identifyToServer");
            identify.addProperty("I", config.clientId());
            identify.addProperty("A", srp.aHex());
            identify.addProperty("securityLevel", SECURITY_LEVEL);
            JsonObject challenge = parse(channel.exchange(setup("srp", identify), "SRP identifyToServer"));
            rejectError(challenge, "SRP identifyToServer");
            JsonObject serverSrp = object(challenge, "srp");
            if (serverSrp == null || !serverSrp.has("s") || !serverSrp.has("B")) {
                throw protocol("unerwartete SRP-Antwort (kein s/B)");
            }

            char[] password = callback.requestPairingPassword(config.clientDisplayName());
            if (password == null) {
                return null;
            }
            KeePassRpcCrypto.Proof proof;
            try {
                proof = srp.prove(serverSrp.get("s").getAsString(), serverSrp.get("B").getAsString(), password);
            } finally {
                Arrays.fill(password, '\0');
            }
            if (!channel.isOpen()) {
                throw new KeePassRpcException(KeePassRpcException.Kind.NOT_AVAILABLE,
                        "KeePassRPC hat die Pairing-Verbindung während der Eingabe geschlossen");
            }

            JsonObject proofToServer = new JsonObject();
            proofToServer.addProperty("stage", "proofToServer");
            proofToServer.addProperty("M", proof.mHex());
            proofToServer.addProperty("securityLevel", SECURITY_LEVEL);
            JsonObject verify = parse(channel.exchange(setup("srp", proofToServer), "SRP proofToServer"));
            rejectError(verify, "SRP proofToServer");
            JsonObject verifySrp = object(verify, "srp");
            if (verifySrp != null && verifySrp.has("M2")
                    && !verifySrp.get("M2").getAsString().equalsIgnoreCase(proof.expectedM2())) {
                throw new KeePassRpcException(KeePassRpcException.Kind.AUTH_FAILED,
                        "Server-Beweis M2 stimmt nicht; Pairing verworfen");
            }
            return proof.sessionKey();
        }
    }

    @Override
    public Session open(char[] sessionKey) throws KeePassRpcException {
        KeePassRpcChannel channel = KeePassRpcChannel.open(config);
        try {
            authenticate(channel, sessionKey);
            return new RpcSession(channel, Arrays.copyOf(sessionKey, sessionKey.length));
        } catch (KeePassRpcException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    /** Key-Challenge-Response mit dem beim Pairing gespeicherten Schlüssel. */
    private void authenticate(KeePassRpcChannel channel, char[] sessionKey) throws KeePassRpcException {
        KeePassRpcCrypto.keyBytes(sessionKey); // Formatprüfung vor dem ersten Netzwerkverkehr
        JsonObject identify = new JsonObject();
        identify.addProperty("username", config.clientId());
        identify.addProperty("securityLevel", SECURITY_LEVEL);
        JsonObject challenge = parse(channel.exchange(setup("key", identify), "KCR identify"));
        rejectError(challenge, "KCR identify");
        JsonObject serverKey = object(challenge, "key");
        if (serverKey == null || !serverKey.has("sc")) {
            throw protocol("unerwartete KCR-Antwort (kein key.sc)");
        }
        String sc = serverKey.get("sc").getAsString();
        String cc = KeePassRpcCrypto.newClientChallenge(random);

        JsonObject response = new JsonObject();
        response.addProperty("cc", cc);
        response.addProperty("cr", KeePassRpcCrypto.challengeResponse("1", sessionKey, sc, cc));
        response.addProperty("securityLevel", SECURITY_LEVEL);
        JsonObject verify = parse(channel.exchange(setup("key", response), "KCR proof"));
        rejectError(verify, "KCR proof");
        JsonObject verifyKey = object(verify, "key");
        if (verifyKey != null && verifyKey.has("sr")
                && !verifyKey.get("sr").getAsString().equalsIgnoreCase(
                        KeePassRpcCrypto.challengeResponse("0", sessionKey, sc, cc))) {
            throw new KeePassRpcException(KeePassRpcException.Kind.AUTH_FAILED, "Server-Antwort sr stimmt nicht");
        }
    }

    private String setup(String section, JsonObject content) {
        JsonObject message = new JsonObject();
        message.addProperty("protocol", "setup");
        message.addProperty("version", PROTOCOL_VERSION);
        message.addProperty("clientTypeId", config.clientId());
        message.addProperty("clientDisplayName", config.clientDisplayName());
        message.addProperty("clientDisplayDescription", config.clientDisplayName());
        JsonArray features = new JsonArray();
        features.add("KPRPC_FEATURE_VERSION_1_6");
        features.add("KPRPC_FEATURE_WARN_USER_WHEN_FEATURE_MISSING");
        features.add("KPRPC_ENTRIES_WITH_NO_URL");
        message.add("features", features);
        message.add(section, content);
        return gson.toJson(message);
    }

    /** Fehler im Setup-Protokoll bedeuten: Pairing bzw. Schlüssel abgelehnt. */
    private static void rejectError(JsonObject message, String step) throws KeePassRpcException {
        if (message.has("error") && !message.get("error").isJsonNull()) {
            throw new KeePassRpcException(KeePassRpcException.Kind.AUTH_FAILED,
                    "KeePassRPC lehnt " + step + " ab (" + errorCode(message.get("error")) + ")");
        }
    }

    /** Nur Code/Typ eines Serverfehlers, nie der komplette Text. */
    private static String errorCode(JsonElement error) {
        if (error.isJsonObject()) {
            JsonObject object = error.getAsJsonObject();
            if (object.has("code")) {
                return "Code " + object.get("code").getAsString();
            }
        }
        return error.isJsonPrimitive() ? error.getAsString() : "Fehlerobjekt";
    }

    static JsonObject parse(String json) throws KeePassRpcException {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw protocol("Antwort ist kein JSON-Objekt");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw protocol("Antwort ist kein gültiges JSON");
        }
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static KeePassRpcException protocol(String message) {
        return new KeePassRpcException(KeePassRpcException.Kind.PROTOCOL, message);
    }

    @Override
    public String toString() {
        return "KeePassRPC " + config.host() + ":" + config.port();
    }

    /** Authentifizierte Verbindung mit verschlüsseltem JSON-RPC. */
    private final class RpcSession implements Session {

        private final KeePassRpcChannel channel;
        private final char[] sessionKey;
        private final AtomicInteger ids = new AtomicInteger(1);

        RpcSession(KeePassRpcChannel channel, char[] sessionKey) {
            this.channel = channel;
            this.sessionKey = sessionKey;
        }

        @Override
        public KeePassEntry findEntryByTitle(String title) throws KeePassRpcException {
            // FindLogins(unsanitizedURLs, actionURL, httpRealm, loginSearchType, requireFullURLMatches,
            //            uniqueID, dbFileName, freeTextSearch, username)
            JsonArray params = new JsonArray();
            JsonArray urls = new JsonArray();
            urls.add(title);
            params.add(urls);
            params.add("");
            params.add("");
            params.add("LSTall");
            params.add(false);
            params.add("");
            params.add("");
            params.add(title);
            params.add("");
            KeePassEntry entry = KeePassRpcEntries.exactTitleMatch(call("FindLogins", params), title);
            if (entry != null) {
                return entry;
            }
            // FindLogins sucht über URLs und Freitext; Einträge ohne passende URL findet erst GetAllEntries.
            return KeePassRpcEntries.exactTitleMatch(call("GetAllEntries", new JsonArray()), title);
        }

        private JsonElement call(String method, JsonArray params) throws KeePassRpcException {
            JsonObject rpc = new JsonObject();
            rpc.addProperty("jsonrpc", "2.0");
            rpc.addProperty("method", method);
            rpc.add("params", params);
            rpc.addProperty("id", ids.getAndIncrement());
            KeePassRpcCrypto.Sealed sealed = KeePassRpcCrypto.encrypt(sessionKey,
                    gson.toJson(rpc).getBytes(StandardCharsets.UTF_8), random);

            JsonObject container = new JsonObject();
            Base64.Encoder base64 = Base64.getEncoder();
            container.addProperty("message", base64.encodeToString(sealed.ciphertext));
            container.addProperty("iv", base64.encodeToString(sealed.iv));
            container.addProperty("hmac", base64.encodeToString(sealed.hmac));
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("protocol", "jsonrpc");
            wrapper.addProperty("version", PROTOCOL_VERSION);
            wrapper.add("jsonrpc", container);

            JsonObject reply = parse(channel.exchange(gson.toJson(wrapper), method));
            if (reply.has("protocol") && "error".equals(reply.get("protocol").getAsString())) {
                throw protocol("KeePassRPC meldet Fehler bei " + method
                        + (reply.has("error") ? " (" + errorCode(reply.get("error")) + ")" : ""));
            }
            JsonObject encrypted = object(reply, "jsonrpc");
            if (encrypted == null || !encrypted.has("message") || !encrypted.has("iv") || !encrypted.has("hmac")) {
                throw protocol("unverschlüsselte oder unvollständige Antwort auf " + method);
            }
            byte[] plaintext;
            try {
                Base64.Decoder decoder = Base64.getDecoder();
                plaintext = KeePassRpcCrypto.decrypt(sessionKey, new KeePassRpcCrypto.Sealed(
                        decoder.decode(encrypted.get("message").getAsString()),
                        decoder.decode(encrypted.get("iv").getAsString()),
                        decoder.decode(encrypted.get("hmac").getAsString())));
            } catch (IllegalArgumentException e) {
                throw protocol("ungültiges Base64 in der Antwort auf " + method);
            }
            JsonObject response;
            try {
                response = parse(new String(plaintext, StandardCharsets.UTF_8));
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
            if (response.has("error") && !response.get("error").isJsonNull()) {
                throw protocol("KeePassRPC-Fehler bei " + method + " (" + errorCode(response.get("error")) + ")");
            }
            JsonElement result = response.get("result");
            return result == null ? new JsonArray() : result;
        }

        @Override
        public void close() {
            Arrays.fill(sessionKey, '\0');
            channel.close();
        }
    }
}
