# Release Candidates und Patch-Auslieferung

## RC1: Version 0.71.0

| | |
|---|---|
| Version | 0.71.0 |
| Commit | `6e067185a34661c22a95d351a6e46be5bd762b78` (Release `v0.71.0`) |
| Tag | `v0.71.0-rc1` |
| Vorführung | 2026-10-15 beim Fachbereich |
| Ergebnis | abgenommen als Release Candidate 1 |

Ab dieser Version wird nur noch über Update-Pakete (Patches) ausgeliefert: Wer 0.71.0 installiert hat, bekommt keine
neue Vollversion mehr, sondern spielt Patches über „Update einspielen …“ in Merlins Kristallhöhle ein.

### Warum 0.71.0 das tragen kann

Das Update-System war in 0.71.0 bereits vollständig und ist seitdem unverändert (kein Diff in `update/`,
`JarPatchMain` und `gradle/fat-jar.gradle` zwischen `v0.71.0` und `main` zum Zeitpunkt der Abnahme):

- Patch-Format 2 mit Version und Inhaltsprüfsumme von Basis und Ziel (`JarPatch`); der Erzeuger spielt jeden Patch
  probeweise ein und bricht ab, wenn das Ziel nicht reproduziert wird.
- Mehrere Patches auf einmal: „Update einspielen …“ nimmt mehrere Dateien, `PatchChainPlanner` verkettet sie über
  Version und Inhaltsprüfsumme, `PatchChainExecutor` wendet sie nacheinander in einem Staging-Verzeichnis an und legt
  erst am Ende die neue Jar ab.
- Ein Patch, der direkt von 0.71.0 auf eine spätere Version springt, ist für den Updater nur eine Kette mit einem
  Schritt; er prüft allein, dass die Basis zur gestarteten Jar passt.

## Patches bauen

Workflow **patch-release** (Actions › patch-release › Run workflow), Eingabe: Basisversion, z. B. `0.71.0`.

- Bricht sofort ab, wenn es kein Release `v<Basisversion>` mit Jar gibt.
- Ziel ist immer die Version auf `main` (`projectVersion` aus `gradle.properties`) und deren veröffentlichte Jar aus
  dem Release `v<Version>`, damit Patch-Ziel und ausgelieferte Vollversion inhaltsgleich sind.
- Baut den Patch mit `:proasteion:exedra:fatJarPatch` und legt ihn als Pre-Release
  `patch-<Basis>-to-<Ziel>` ab; Titel und Notizen nennen Basisversion, Zielversion und beide Commits.

Hinweis: Wer einem Nutzer sowohl den direkten Patch 0.71.0 → X als auch Einzelschritte ab 0.71.0 gibt, bekommt eine
Fehlermeldung („mehrere verschiedene Updates“), weil der Updater nicht rät. Pro Nutzer also entweder den einen
direkten Patch oder die Kette ausliefern.
