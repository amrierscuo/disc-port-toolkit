# Disc Port Toolkit | Guide de démarrage

[English](../../README.md) · [Italiano](it.md) · Français · [Deutsch](de.md) · [Español](es.md) · [日本語](ja.md)

Utilisez votre disque PAL de BLiNX: The Time Sweeper pour compiler Clone 1 pour Windows ou l’un des deux profils Android pour votre usage personnel.

| | Fichiers requis | Fonctionnalités |
| --- | --- | --- |
| Windows (Clone 1) | Utilisez votre disque PAL complet. | Lancez Clone 1 sous Windows à partir des fichiers de votre jeu PAL. |
| Android Original | Utilisez votre disque PAL complet. | Lancez directement le jeu PAL avec les commandes tactiles à l’écran. |
| Android Full | Disques PAL et démo OXM12 complets, 263 ressources privées d’interface et de médias, deux bibliothèques ARM64 et une clé de signature locale. | Le menu permet de choisir entre Original Game et la démo OXM12. |

## Premiers pas

Sous Windows 64 bits, ouvrez le dossier du toolkit et double-cliquez sur `START_HERE.cmd`. Choisissez Windows Clone 1 ou Android Original. Sélectionnez l’image de la partition de jeu PAL. Si vous avez déjà extrait le disque, son dossier peut rester sur le lecteur de votre choix, même en dehors du toolkit. Ouvrez-le dans le sélecteur et choisissez `default.xbe`, à côté de `media/`. Gardez tous les fichiers et sous-dossiers de `media/` à leur emplacement d’origine.

Chaque étape longue a sa propre barre. Le signe ~ indique un pourcentage estimé. Le temps écoulé reste visible pendant les étapes silencieuses. La barre atteint 100 % quand l’étape réussit.

Sous Windows, le jeu se lance. Pour Android, copiez l’APK obtenu sur le téléphone et ouvrez-le.

Si la vérification échoue, mettez le toolkit à jour et réessayez avec le même disque. Le lanceur réutilise les outils déjà installés. [Dépannage](../TROUBLESHOOTING.md#pal-disc-files-differ-from-the-reference)

| | |
| --- | --- |
| Windows | `START_HERE.cmd windows "D:\Discs\Blinx.iso"` |
| Android Original | `START_HERE.cmd android-original "D:\Discs\Blinx.iso"` |
| Android Full | `build_android_full.cmd` |

Pour Android Original, Pillow 12.0.0 génère les 20 images de la manette à partir de deux textures de votre disque PAL.

[Aperçu avec choix de langue](../../preview/index.html) · [Guide technique en anglais](../../README.md) · [Détails de compilation](../FINAL_AUDIT.md) · [Licences](../../LICENSES.md)
