# Pong Duo

Pong à deux joueurs sur le même téléphone Android.

## Comment jouer
- Posez le téléphone à plat entre vous deux (en portrait).
- **Joueur 1** (cyan) contrôle la raquette du bas, **Joueur 2** (rose) celle du haut.
- Glissez le doigt dans **votre moitié** de l'écran : la raquette suit votre doigt. Les deux joueurs peuvent jouer en même temps (multitouch).
- Plus la balle touche le bord de la raquette, plus l'angle de renvoi est fort. La balle accélère à chaque échange.
- Premier à **7 points** gagne. Touchez l'écran pour rejouer.

## Obtenir l'APK

### Option A — Android Studio
1. Ouvrir le dossier `PongDuo` dans Android Studio.
2. Brancher le téléphone (débogage USB activé) et cliquer sur ▶ Run.
   Ou : menu *Build › Build APK(s)* puis copier l'APK sur le téléphone.

### Option B — Sans rien installer (GitHub)
1. Créer un dépôt GitHub et y envoyer le contenu de ce dossier (y compris `.github/`).
2. Onglet **Actions** → la compilation démarre toute seule (~3 min).
3. Télécharger l'artefact **PongDuo-apk**, le dézipper et ouvrir l'APK sur le téléphone
   (autoriser l'installation d'applications de sources inconnues).

### Option C — Ligne de commande
Avec le SDK Android installé (`ANDROID_HOME` défini) : `./gradlew assembleRelease`
→ `app/build/outputs/apk/release/app-release.apk`

Android 8.0 minimum.
