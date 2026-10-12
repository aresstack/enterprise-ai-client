# Zauberer des Einrichtungsassistenten

Die Originale der Zauberer-Bilder, unverändert (PNG, 1086 × 1448, transparenter Hintergrund):

| Datei | Schritt |
|---|---|
| `wizard-00-mascot.png` | allgemeiner Zauberer (Rückfall, wenn ein Schrittbild fehlt) |
| `wizard-01-keepass.png` | KeePass |
| `wizard-02-ai-service.png` | KI-Dienst |
| `wizard-03-models.png` | Modelle |
| `wizard-04-confluence.png` | Confluence |
| `wizard-05-complete.png` | Abschluss |
| `wizard-06-speech.png` | Spracheingabe |

Die Anwendung lädt verkleinerte Fassungen gleichen Namens aus
`proasteion/exedra/src/main/resources/com/aresstack/enterpriseai/proasteion/exedra/ui/setup/wizard/`
(480 × 640, RGBA, Lanczos), damit das Jar klein bleibt und das Laden schnell ist; sie reichen für die doppelte
Pixeldichte der größten Anzeige. Die Originale liegen hier außerhalb des Jars. Neu erzeugen, z. B. mit Pillow:

```python
from PIL import Image
Image.open(original).resize((480, 640), Image.LANCZOS).save(target, optimize=True)
```
