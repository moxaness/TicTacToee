Résumé du Projet : Morpion Multijoueur en Java

Ce projet est une application de jeu de morpion (Tic-Tac-Toe) multijoueur complète, construite en Java. Il utilise les Sockets Java pour la communication réseau et Java Swing pour l'interface utilisateur graphique.

L'application est divisée en trois fichiers principaux :

1. TicTacToeServer.java :
C'est le serveur backend qui gère la logique du jeu et les connexions des clients.
* Il est multi-threadé, créant un nouveau thread "ClientHandler" pour chaque joueur connecté.
* Il gère plusieurs salons de jeu (lobbies), des parties actives et une liste de joueurs en attente en utilisant des structures de données concurrentes.
* Il traite toutes les commandes de jeu des clients, telles que "NAME:", "FIND_GAME", "MOVE:", et "REMATCH:".
* Il contient des classes internes pour "Player" (pour stocker les statistiques comme le classement ELO, les victoires, les défaites) et "Game" (pour gérer l'état du plateau, les tours et la logique de victoire/égalité).
* Le serveur enregistre les événements et affiche périodiquement des statistiques (temps de fonctionnement, connexions actives, etc.).

2. TicTacToeClient.java :
C'est l'application client GUI avec laquelle les joueurs interagissent.
* Il se connecte au serveur en utilisant un Socket sur le port 5567.
* Il dispose d'une interface à onglets (Jeu, Salon, Classement, Profil) construite avec Java Swing.
* Un "GameBoardPanel" personnalisé (classe interne) utilise Graphics2D pour dessiner le plateau, les marqueurs et la ligne de victoire, et il gère les clics de souris pour les coups.
* Il exécute un thread séparé pour écouter en continu les messages du serveur (par ex., "GAME_STARTED:", "BOARD:", "YOUR_TURN", "GAME_OVER:").
* Il inclut un "ThemeManager" pour basculer entre les modes Clair (Light) et Sombre (Dark).
* Il propose un chat pour le salon et en jeu, un indicateur d'état de connexion et une logique de reconnexion automatique.

3. TicTacToeMain.java :
C'est un simple utilitaire de lancement.
* Il fournit une petite fenêtre Swing avec deux boutons : "Start Server" et "Start Client".
* "Start Server" exécute la méthode `TicTacToeServer.main()`.
* "Start Client" crée une nouvelle instance de la GUI `TicTacToeClient`.