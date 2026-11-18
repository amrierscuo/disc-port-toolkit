# Disc Port Toolkit | Guía de inicio

[English](../../README.md) · [Italiano](it.md) · [Français](fr.md) · [Deutsch](de.md) · Español · [日本語](ja.md)

Con tu disco PAL de BLiNX: The Time Sweeper puedes compilar Clone 1 para Windows o uno de los dos perfiles Android para uso personal.

| | Archivos necesarios | Funciones |
| --- | --- | --- |
| Windows (Clone 1) | Usa tu disco PAL completo. | Ejecuta Clone 1 en Windows con los archivos de tu juego PAL. |
| Android Original | Usa tu disco PAL completo. | Abre directamente el juego PAL con los controles táctiles en pantalla. |
| Android Full | Discos PAL y demo OXM12 completos, 263 recursos privados de interfaz y contenido multimedia, dos bibliotecas ARM64 y una clave de firma local. | El menú permite elegir entre Original Game y la demo OXM12. |

## Primeros pasos

En Windows de 64 bits, abre la carpeta del toolkit y haz doble clic en `START_HERE.cmd`. Elige Windows Clone 1 o Android Original. Selecciona la imagen de la partición de juego PAL. Si ya has extraído el disco, puedes dejar su carpeta en cualquier unidad, incluso fuera del toolkit. Ábrela en el selector y elige `default.xbe`, junto a `media/`. Conserva todos los archivos y subcarpetas de `media/` en sus ubicaciones originales.

Cada paso largo tiene su propia barra. El signo ~ indica un porcentaje estimado. El tiempo transcurrido se actualiza aunque la herramienta no muestre mensajes. La barra llega al 100 % cuando el paso termina correctamente.

En Windows se abre el juego. Para Android, copia el APK generado al teléfono y ábrelo.

Si falla la verificación del disco, actualiza el toolkit y vuelve a intentarlo con el mismo disco. El asistente reutiliza las herramientas ya instaladas. [Solución de problemas](../TROUBLESHOOTING.md#pal-disc-files-differ-from-the-reference)

| | |
| --- | --- |
| Windows | `START_HERE.cmd windows "D:\Discs\Blinx.iso"` |
| Android Original | `START_HERE.cmd android-original "D:\Discs\Blinx.iso"` |
| Android Full | `build_android_full.cmd` |

Para Android Original, Pillow 12.0.0 genera las 20 imágenes del mando a partir de dos texturas de tu disco PAL.

[Vista previa con selector de idioma](../../preview/index.html) · [Guía técnica en inglés](../../README.md) · [Detalles de compilación](../FINAL_AUDIT.md) · [Licencias](../../LICENSES.md)
