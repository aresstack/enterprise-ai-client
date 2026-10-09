// Test-PAC: ein nicht routbarer Sentinel-Proxy (RFC 5737) beweist, dass das Skript wirklich ausgewertet wurde.
function FindProxyForURL(url, host) {
  if (shExpMatch(host, "*.example.test")) { return "DIRECT"; }
  if (isPlainHostName(host) || dnsDomainIs(host, ".intern")) { return "DIRECT"; }
  return "PROXY 192.0.2.123:18080; DIRECT";
}
