# CLAUDE.md — TikSave

## Informations du projet (vérifiées dans le dépôt)
| Élément | Valeur |
|---|---|
| Nom | TikSave |
| Stack | Android natif Java 17, sans dépendances externes · AGP 8.7.3 · Gradle 8.9 · compileSdk/targetSdk 35 · minSdk 26 |
| applicationId / namespace | `be.rst.tiksave` — **NE JAMAIS CHANGER** |
| Workflow | `.github/workflows/build.yml` — push sur `main` ou manuel → `gradle assembleRelease` → GitHub Release `v<run_number>` avec l'asset `TikSave.apk` |
| Signature | `release.jks` recréé depuis le secret `KEYSTORE_BASE64`, alias `rst`, mot de passe store + clé = secret `KEYSTORE_PASSWORD`. `*.jks` dans `.gitignore`. Debug signé avec la même clé |
| Version | `versionCode` = `github.run_number` (via `-PversionCode`), `versionName` = `1.<versionCode>`. Ne pas renommer ni recréer le workflow (le compteur repartirait de 1) |
| Données locales | SharedPreferences `stats` (compteurs) et `debug` (clés `log`, `dump`). Médias : `Movies/TikTok/<id>.mp4`, `Pictures/TikTok/<id>_n.jpg` |
| Fichiers clés | `app/src/main/java/be/rst/tiksave/` : `MainActivity` (UI, splash, stats, diagnostic), `ClipActivity` (Partager → TikSave / Coller), `Downloader` (lien → tikwm.com → DownloadManager) |
| Dépendance externe | API `https://www.tikwm.com/api/` |

## Collaboration
- L'utilisateur décrit les fonctionnalités en français (vibecoding). Prendre en charge la technique et répondre brièvement en français.
- Comprendre la demande et les fichiers concernés, sans relire tout le dépôt à chaque tâche.
- Faire des modifications ciblées et réutiliser l'existant. Pas de sous-agents ni d'opérations coûteuses sans bénéfice clair.
- Pour une décision importante ou ambiguë : proposer brièvement une solution et demander confirmation.

## Sécurité des mises à jour Android
- Ne jamais changer applicationId, clé de signature, certificat ou keystore sans accord explicite.
- Vérifier le mécanisme de signature avant toute mise à jour. Si la clé est indisponible, le signaler au lieu d'en créer une nouvelle.
- Incrémenter versionCode pour toute mise à jour distribuée (déjà automatique via `run_number`).
- Préserver les données locales et la compatibilité des mises à jour. Ne pas renommer les SharedPreferences `stats`/`debug` ni leurs clés sans migration.
- Ne jamais désinstaller l'app pour contourner un problème de signature.
- Ne jamais publier keystore, mots de passe, jetons ou secrets (dépôt, logs, réponses). Utiliser les secrets GitHub Actions.

## GitHub et compilation
- Respecter le workflow existant. Ne le modifier qu'en cas de nécessité, en l'expliquant.
- Avant un push : résumer les fichiers modifiés et demander une autorisation explicite.
- Rappel : tout push sur `main` déclenche une compilation **et** publie une Release.
- En cas d'échec de compilation : analyser les lignes d'erreur utiles et corriger la cause, sans relancer en boucle.
- Ne supprimer ni historique Git, ni branche, ni données sans accord.

## Livraison de l'APK — lien direct prioritaire
- Après une compilation réussie, donner en priorité l'URL directe de l'asset : `https://github.com/bralexandre1006-jpg/DownloadTiktok/releases/download/v<N>/TikSave.apk` (vérifier qu'il existe).
- Ne pas créer de Release ni publier sans autorisation explicite.
- Si l'APK n'existe que comme artifact Actions : préciser que c'est un ZIP, et dire si une connexion GitHub est requise ou si le lien est temporaire.
- Si aucun lien direct n'est utilisable : le dire clairement et donner le lien le plus précis vers l'artifact du run.
- Ne jamais fabriquer d'URL. Vérifier la réussite du build et l'existence du fichier avant de l'annoncer.
- Compte rendu final : d'abord `Télécharger l'APK : [lien]`, puis la version et un bref résumé.

## Design et UX
- Interface cohérente, soignée, moderne, accessible et fluide.
- Priorité à la compatibilité Xiaomi 17T Pro : encoche/caméra, insets, edge-to-edge.
- Respecter la direction artistique existante (style « GOY », couleurs dans `res/values/colors.xml`, drawables `bg_*`). Demander des références si une refonte est ambiguë.
- Soigner typographie, espacements, états de chargement/erreur, navigation et animations raisonnables.
- Ne pas refondre des écrans non concernés par la demande.

## Optimisation du développement et des tokens
- Modifications minimales et ciblées. Réutiliser les composants, fonctions et dépendances existants.
- Recherche ciblée plutôt que lecture intégrale. Ne pas relire un fichier inchangé.
- Pas de refactorisation non demandée, pas de fichiers inutiles, pas de compilations multiples ni de recherches externes sans raison.
- Faire uniquement les tests nécessaires, sans négliger les contrôles importants.
- Ne jamais sacrifier le design, les animations, l'UX ou les fonctionnalités pour économiser des tokens.
- Ne supprimer ni modifier aucune fonctionnalité existante sans nécessité ou demande explicite.
- Ces règles ne changent pas le modèle Claude utilisé.
- Appliquer ces principes automatiquement, sans rappel.
