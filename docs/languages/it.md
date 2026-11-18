# Disc Port Toolkit | Guida rapida

[English](../../README.md) · Italiano · [Français](fr.md) · [Deutsch](de.md) · [Español](es.md) · [日本語](ja.md)

Con il tuo disco PAL di BLiNX: The Time Sweeper puoi compilare Clone 1 per Windows o uno dei due profili Android per uso personale.

| | Cosa occorre | Cosa include |
| --- | --- | --- |
| Windows (Clone 1) | Usa il tuo disco PAL completo. | Avvia Clone 1 per Windows con i file della tua copia PAL. |
| Android Original | Usa il tuo disco PAL completo. | Avvia direttamente il gioco PAL con i comandi a schermo. |
| Android Full | Dischi PAL e demo OXM12 completi, 263 risorse private dell’interfaccia e dei contenuti multimediali, due librerie ARM64 e una chiave di firma locale. | Dal menu puoi scegliere fra Original Game e la demo OXM12. |

## Per iniziare

Su Windows a 64 bit apri la cartella del toolkit e fai doppio clic su `START_HERE.cmd`. Scegli Windows Clone 1 oppure Android Original. Seleziona l’immagine della partizione di gioco PAL. Se hai già estratto il disco, puoi lasciare la cartella su qualsiasi unità, anche fuori dal toolkit. Aprila nel selettore e scegli `default.xbe`, accanto a `media/`. Mantieni al loro posto tutti i file e le sottocartelle di `media/`.

Ogni fase lenta ha una barra. Il simbolo ~ indica una percentuale stimata. Il tempo trascorso si aggiorna anche mentre lo strumento lavora in silenzio. La fase raggiunge il 100% quando termina con successo.

Su Windows si apre il gioco. Per Android copia sul telefono l’APK ottenuto e aprilo.

Se la verifica del disco si ferma, aggiorna il toolkit e riprova con lo stesso disco. Il launcher riusa gli strumenti già installati. [Risoluzione dei problemi](../TROUBLESHOOTING.md#pal-disc-files-differ-from-the-reference)

| | |
| --- | --- |
| Windows | `START_HERE.cmd windows "D:\Discs\Blinx.iso"` |
| Android Original | `START_HERE.cmd android-original "D:\Discs\Blinx.iso"` |
| Android Full | `build_android_full.cmd` |

Per Android Original, Pillow 12.0.0 genera le 20 immagini del pad da due texture del tuo disco PAL.

[Anteprima con menu delle lingue](../../preview/index.html) · [Guida tecnica in inglese](../../README.md) · [Dettagli della build](../FINAL_AUDIT.md) · [Licenze](../../LICENSES.md)
