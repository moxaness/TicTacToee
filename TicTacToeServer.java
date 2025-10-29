package TicTacToee;

import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

public class TicTacToeServer {
    // Server configuration
    private static final int PORT = 5567;
    private static final int MAX_CLIENTS = 100;
    private static final Logger logger = Logger.getLogger("TicTacToeServer");
    
    // Data structures for game management
    private static final Map<String, GameLobby> lobbies = new ConcurrentHashMap<>();
    private static final Map<String, Player> activePlayers = new ConcurrentHashMap<>();
    private static final Map<String, Game> activeGames = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> playerGameHistory = new ConcurrentHashMap<>();
    private static final Set<String> waitingPlayers = ConcurrentHashMap.newKeySet();
    
    // Statistics
    private static AtomicInteger totalGamesPlayed = new AtomicInteger(0);
    private static AtomicInteger currentConnections = new AtomicInteger(0);
    private static Timestamp serverStartTime = new Timestamp(System.currentTimeMillis());

    public static void main(String[] args) {
        setupLogger();
        logger.info("Tic Tac Toe Server starting on port " + PORT);
        
        // Create default lobby
        GameLobby defaultLobby = new GameLobby("Main Lobby", "The main lobby for all players");
        lobbies.put(defaultLobby.getId(), defaultLobby);
        
        // Schedule periodic tasks
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(TicTacToeServer::printServerStats, 5, 60, TimeUnit.SECONDS);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Server started successfully. Waiting for connections...");
            
            while (true) {
                try {
                    // Accept new client connection
                    Socket clientSocket = serverSocket.accept();
                    currentConnections.incrementAndGet();
                    
                    // Log connection
                    String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    logger.info("New connection from: " + clientAddress);
                    
                    // Check if server is full
                    if (currentConnections.get() > MAX_CLIENTS) {
                        logger.warning("Server is full. Rejecting connection from: " + clientAddress);
                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        out.println("ERROR:Server is full. Please try again later.");
                        clientSocket.close();
                        currentConnections.decrementAndGet();
                        continue;
                    }
                    
                    // Create and start a new thread to handle this client
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    Thread clientThread = new Thread(clientHandler);
                    clientThread.setDaemon(true);
                    clientThread.start();
                    
                } catch (IOException e) {
                    logger.severe("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.severe("Error starting server: " + e.getMessage());
        }
    }
    
    private static void setupLogger() {
        try {
            // Create file handler
            FileHandler fileHandler = new FileHandler("server_log.txt", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            
            // Create console handler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            logger.addHandler(consoleHandler);
            
            // Set log level
            logger.setLevel(Level.INFO);
            
        } catch (IOException e) {
            System.err.println("Error setting up logger: " + e.getMessage());
        }
    }
    
    private static void printServerStats() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String uptime = calculateUptime();
        
        logger.info("\n----- SERVER STATISTICS -----\n" +
                   "Server started: " + sdf.format(serverStartTime) + "\n" +
                   "Uptime: " + uptime + "\n" +
                   "Current connections: " + currentConnections.get() + "\n" +
                   "Total games played: " + totalGamesPlayed.get() + "\n" +
                   "Active games: " + activeGames.size() + "\n" +
                   "Total registered players: " + activePlayers.size() + "\n" +
                   "Players waiting for match: " + waitingPlayers.size() + "\n" +
                   "-----------------------------");
    }
    
    private static String calculateUptime() {
        long diffInMillies = System.currentTimeMillis() - serverStartTime.getTime();
        long days = diffInMillies / (24 * 60 * 60 * 1000);
        diffInMillies %= (24 * 60 * 60 * 1000);
        long hours = diffInMillies / (60 * 60 * 1000);
        diffInMillies %= (60 * 60 * 1000);
        long minutes = diffInMillies / (60 * 1000);
        
        return days + " days, " + hours + " hours, " + minutes + " minutes";
    }

    // Class to represent a player
    private static class Player {
        private String id;
        private String name;
        private ClientHandler clientHandler;
        private int rating = 1200; // ELO rating
        private int wins = 0;
        private int losses = 0;
        private int ties = 0;
        private String currentLobbyId;
        private String currentGameId;
        private Timestamp lastActivity;
        
        public Player(String id, String name, ClientHandler clientHandler) {
            this.id = id;
            this.name = name;
            this.clientHandler = clientHandler;
            this.lastActivity = new Timestamp(System.currentTimeMillis());
            this.currentLobbyId = lobbies.values().iterator().next().getId(); // Join default lobby
        }
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public ClientHandler getClientHandler() {
            return clientHandler;
        }
        
        public int getRating() {
            return rating;
        }
        
        public void updateRating(int change) {
            this.rating += change;
        }
        
        public void incrementWins() {
            wins++;
        }
        
        public void incrementLosses() {
            losses++;
        }
        
        public void incrementTies() {
            ties++;
        }
        
        public int getWins() {
            return wins;
        }
        
        public int getLosses() {
            return losses;
        }
        
        public int getTies() {
            return ties;
        }
        
        public int getTotalGames() {
            return wins + losses + ties;
        }
        
        public String getCurrentLobbyId() {
            return currentLobbyId;
        }
        
        public void setCurrentLobbyId(String lobbyId) {
            this.currentLobbyId = lobbyId;
        }
        
        public String getCurrentGameId() {
            return currentGameId;
        }
        
        public void setCurrentGameId(String gameId) {
            this.currentGameId = gameId;
        }
        
        public void updateLastActivity() {
            this.lastActivity = new Timestamp(System.currentTimeMillis());
        }
        
        public String getStats() {
            double winRate = getTotalGames() > 0 ? (double) wins / getTotalGames() * 100 : 0;
            return String.format("Name: %s | Rating: %d | W/L/T: %d/%d/%d | Win Rate: %.1f%%", 
                                name, rating, wins, losses, ties, winRate);
        }
    }
    
    // Class for game lobbies
    private static class GameLobby {
        private String id;
        private String name;
        private String description;
        private Set<String> playerIds = ConcurrentHashMap.newKeySet();
        private Timestamp createdAt;
        
        public GameLobby(String name, String description) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.description = description;
            this.createdAt = new Timestamp(System.currentTimeMillis());
        }
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void addPlayer(String playerId) {
            playerIds.add(playerId);
            
            // Notify all players in lobby about new player
            broadcastToLobby("LOBBY_JOIN:" + playerId + ":" + 
                            activePlayers.getOrDefault(playerId, new Player(playerId, "Unknown", null)).getName());
        }
        
        public void removePlayer(String playerId) {
            playerIds.remove(playerId);
            
            // Notify all players in lobby about player leaving
            broadcastToLobby("LOBBY_LEAVE:" + playerId);
        }
        
        public Set<String> getPlayerIds() {
            return playerIds;
        }
        
        public void broadcastToLobby(String message) {
            for (String playerId : playerIds) {
                Player player = activePlayers.get(playerId);
                if (player != null && player.getClientHandler() != null) {
                    player.getClientHandler().sendMessage(message);
                }
            }
        }
        
        public void broadcastChat(String senderId, String message) {
            Player sender = activePlayers.get(senderId);
            if (sender != null) {
                String chatMessage = "LOBBY_CHAT:" + sender.getName() + ":" + message;
                broadcastToLobby(chatMessage);
            }
        }
    }
    
    // Class to handle individual client connections
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private Player player;
        private String playerId;
        private boolean authenticated = false;
        private AtomicBoolean running = new AtomicBoolean(true);
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.playerId = UUID.randomUUID().toString();
        }
        
        @Override
        public void run() {
            try {
                // Set up input and output streams
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Create a new player
                player = new Player(playerId, "Player" + playerId.substring(0, 4), this);
                activePlayers.put(playerId, player);
                
                // Send welcome message
                sendMessage("CONNECTED:" + playerId);
                sendMessage("SERVER_INFO:Welcome to Tic Tac Toe Server! Server time: " + 
                          new Date().toString());
                
                // Join default lobby
                GameLobby defaultLobby = lobbies.values().iterator().next();
                defaultLobby.addPlayer(playerId);
                
                // Tell the player about the lobby they joined
                sendMessage("JOINED_LOBBY:" + defaultLobby.getId() + ":" + defaultLobby.getName());
                
                // Send list of players in lobby
                sendLobbyPlayerList(defaultLobby);
                
                String inputLine;
                // Process client messages
                while (running.get() && (inputLine = in.readLine()) != null) {
                    processCommand(inputLine);
                    player.updateLastActivity();
                }
            } catch (IOException e) {
                logger.info("Connection lost with player: " + playerId + " - " + e.getMessage());
            } finally {
                cleanup();
            }
        }
        
        private void sendLobbyPlayerList(GameLobby lobby) {
            StringBuilder playerList = new StringBuilder("PLAYER_LIST:");
            for (String pid : lobby.getPlayerIds()) {
                Player p = activePlayers.get(pid);
                if (p != null) {
                    playerList.append(pid).append(":").append(p.getName()).append(":");
                    playerList.append(p.getWins()).append(":").append(p.getLosses()).append(":").append(p.getTies()).append("|");
                }
            }
            sendMessage(playerList.toString());
        }
        
        // Process commands received from the client
        private void processCommand(String command) {
            logger.fine("Received from " + playerId + ": " + command);
            
            if (command.startsWith("NAME:")) {
                String name = command.substring(5).trim();
                if (!name.isEmpty()) {
                    player.setName(name);
                    
                    // Notify lobby of name change
                    GameLobby lobby = lobbies.get(player.getCurrentLobbyId());
                    if (lobby != null) {
                        lobby.broadcastToLobby("PLAYER_UPDATE:" + playerId + ":name:" + name);
                    }
                    
                    logger.info("Player " + playerId + " set name to: " + name);
                }
            } 
            else if (command.startsWith("CHAT:")) {
                String message = command.substring(5);
                GameLobby lobby = lobbies.get(player.getCurrentLobbyId());
                if (lobby != null) {
                    lobby.broadcastChat(playerId, message);
                }
            } 
            else if (command.startsWith("LOBBY_CHAT:")) {
                String message = command.substring(11);
                GameLobby lobby = lobbies.get(player.getCurrentLobbyId());
                if (lobby != null) {
                    lobby.broadcastChat(playerId, message);
                }
            } 
            else if (command.startsWith("GAME_CHAT:")) {
                String message = command.substring(10);
                handleGameChat(message);
            } 
            else if (command.equals("LIST_LOBBIES")) {
                sendLobbyList();
            } 
            else if (command.startsWith("JOIN_LOBBY:")) {
                String lobbyId = command.substring(11);
                joinLobby(lobbyId);
            } 
            else if (command.equals("FIND_GAME")) {
                findGame();
            } 
            else if (command.startsWith("MOVE:")) {
                makeMove(command.substring(5));
            } 
            else if (command.startsWith("REMATCH:")) {
                handleRematchRequest(command.substring(8));
            } 
            else if (command.equals("REMATCH_ACCEPT")) {
                handleRematchAccept();
            } 
            else if (command.equals("REMATCH_DECLINE")) {
                handleRematchDecline();
            } 
            else if (command.equals("GET_STATS")) {
                sendPlayerStats();
            } 
            else if (command.equals("GET_LEADERBOARD")) {
                sendLeaderboard();
            } 
            else if (command.equals("GET_HISTORY")) {
                sendGameHistory();
            } 
            else if (command.equals("QUIT")) {
                cleanup();
            }
        }
        
        private void sendLobbyList() {
            StringBuilder lobbyList = new StringBuilder("LOBBY_LIST:");
            for (GameLobby lobby : lobbies.values()) {
                lobbyList.append(lobby.getId()).append(":")
                         .append(lobby.getName()).append(":")
                         .append(lobby.getDescription()).append(":")
                         .append(lobby.getPlayerIds().size()).append("|");
            }
            sendMessage(lobbyList.toString());
        }
        
        private void joinLobby(String lobbyId) {
            if (!lobbies.containsKey(lobbyId)) {
                sendMessage("ERROR:Lobby does not exist");
                return;
            }
            
            // Leave current lobby
            GameLobby currentLobby = lobbies.get(player.getCurrentLobbyId());
            if (currentLobby != null) {
                currentLobby.removePlayer(playerId);
            }
            
            // Join new lobby
            GameLobby newLobby = lobbies.get(lobbyId);
            newLobby.addPlayer(playerId);
            player.setCurrentLobbyId(lobbyId);
            
            // Notify player
            sendMessage("JOINED_LOBBY:" + newLobby.getId() + ":" + newLobby.getName());
            
            // Send player list
            sendLobbyPlayerList(newLobby);
        }
        
        private void handleGameChat(String message) {
            if (player.getCurrentGameId() == null) {
                return;
            }
            
            Game game = activeGames.get(player.getCurrentGameId());
            if (game != null) {
                game.broadcastChat(player.getId(), message);
            }
        }
        
        private void findGame() {
            // If player is already in a game, do nothing
            if (player.getCurrentGameId() != null) {
                sendMessage("ERROR:You are already in a game");
                return;
            }
            
            // If player is already waiting, remove them
            if (waitingPlayers.contains(playerId)) {
                waitingPlayers.remove(playerId);
                sendMessage("ERROR:Canceled matchmaking");
                return;
            }
            
            // If there are no waiting players, this player waits
            if (waitingPlayers.isEmpty()) {
                waitingPlayers.add(playerId);
                sendMessage("WAITING");
                logger.info("Player " + player.getName() + " (" + playerId + ") is waiting for an opponent");
            } else {
                // Get best match from waiting players based on rating
                String opponentId = findBestMatch();
                waitingPlayers.remove(opponentId);
                
                // Create new game
                createGame(player, activePlayers.get(opponentId));
            }
        }
        
        private String findBestMatch() {
            // Simple implementation: just get the first waiting player
            // Could be improved with rating-based matchmaking
            return waitingPlayers.iterator().next();
        }
        
        private void createGame(Player player1, Player player2) {
            // Generate game ID
            String gameId = UUID.randomUUID().toString();
            
            // Create new game
            Game game = new Game(gameId, player1.getId(), player2.getId());
            activeGames.put(gameId, game);
            
            // Update player game IDs
            player1.setCurrentGameId(gameId);
            player2.setCurrentGameId(gameId);
            
            // Log game creation
            logger.info("Game " + gameId + " created between " + player1.getName() + " and " + player2.getName());
            
            // Store in history
            recordGameInHistory(player1.getId(), gameId);
            recordGameInHistory(player2.getId(), gameId);
            
            // Track total games
            totalGamesPlayed.incrementAndGet();
            
            // Start the game
            game.start();
        }
        
        private void recordGameInHistory(String playerId, String gameId) {
            playerGameHistory.computeIfAbsent(playerId, k -> new ArrayList<>()).add(gameId);
        }
        
        private void makeMove(String positionStr) {
            if (player.getCurrentGameId() == null) {
                sendMessage("ERROR:You are not in a game");
                return;
            }
            
            Game game = activeGames.get(player.getCurrentGameId());
            if (game == null) {
                sendMessage("ERROR:Game not found");
                player.setCurrentGameId(null);
                return;
            }
            
            try {
                int position = Integer.parseInt(positionStr);
                game.makeMove(player.getId(), position);
            } catch (NumberFormatException e) {
                sendMessage("ERROR:Invalid position format");
            }
        }
        
        private void handleRematchRequest(String gameId) {
            Game game = activeGames.get(gameId);
            if (game == null || !game.isGameOver()) {
                sendMessage("ERROR:Invalid game for rematch");
                return;
            }
            
            // Get opponent
            String opponentId = game.getOpponentId(player.getId());
            Player opponent = activePlayers.get(opponentId);
            
            if (opponent == null || opponent.getCurrentGameId() == null) {
                sendMessage("ERROR:Opponent not available for rematch");
                return;
            }
            
            // Send rematch request to opponent
            opponent.getClientHandler().sendMessage("REMATCH_REQUESTED:" + player.getName());
            sendMessage("REMATCH_SENT:" + opponent.getName());
            
            // Store who requested the rematch
            game.setRematchRequester(player.getId());
        }
        
        private void handleRematchAccept() {
            if (player.getCurrentGameId() == null) {
                sendMessage("ERROR:No active game for rematch");
                return;
            }
            
            Game currentGame = activeGames.get(player.getCurrentGameId());
            if (currentGame == null || !currentGame.isGameOver()) {
                sendMessage("ERROR:Invalid game state for rematch");
                return;
            }
            
            // Check if this player is the one who received the request
            String requesterId = currentGame.getRematchRequester();
            if (requesterId == null || requesterId.equals(player.getId())) {
                sendMessage("ERROR:No rematch request to accept");
                return;
            }
            
            // Get the requester player
            Player requester = activePlayers.get(requesterId);
            if (requester == null) {
                sendMessage("ERROR:Requester not available");
                return;
            }
            
            // Notify both players
            sendMessage("REMATCH_ACCEPTED");
            requester.getClientHandler().sendMessage("REMATCH_ACCEPTED");
            
            // Create a new game with swapped markers
            createGame(requester, player);
        }
        
        private void handleRematchDecline() {
            if (player.getCurrentGameId() == null) {
                return;
            }
            
            Game currentGame = activeGames.get(player.getCurrentGameId());
            if (currentGame == null) {
                return;
            }
            
            // Check if this player is the one who received the request
            String requesterId = currentGame.getRematchRequester();
            if (requesterId == null || requesterId.equals(player.getId())) {
                return;
            }
            
            // Get the requester player
            Player requester = activePlayers.get(requesterId);
            if (requester == null) {
                return;
            }
            
            // Notify the requester
            requester.getClientHandler().sendMessage("REMATCH_DECLINED");
            
            // Clear rematch state
            currentGame.setRematchRequester(null);
        }
        
        private void sendPlayerStats() {
            sendMessage("PLAYER_STATS:" + 
                      player.getWins() + ":" + 
                      player.getLosses() + ":" + 
                      player.getTies() + ":" + 
                      player.getRating());
        }
        
        private void sendLeaderboard() {
            // Get top 10 players by rating
            List<Player> topPlayers = activePlayers.values().stream()
                .filter(p -> p.getTotalGames() >= 5) // Minimum games to be ranked
                .sorted(Comparator.comparingInt(Player::getRating).reversed())
                .limit(10)
                .toList();
            
            StringBuilder leaderboard = new StringBuilder("LEADERBOARD:");
            for (int i = 0; i < topPlayers.size(); i++) {
                Player p = topPlayers.get(i);
                leaderboard.append(i+1).append(":")
                          .append(p.getName()).append(":")
                          .append(p.getRating()).append(":")
                          .append(p.getWins()).append(":")
                          .append(p.getLosses()).append(":")
                          .append(p.getTies()).append("|");
            }
            
            sendMessage(leaderboard.toString());
        }
        
        private void sendGameHistory() {
            List<String> history = playerGameHistory.getOrDefault(playerId, new ArrayList<>());
            StringBuilder historyMsg = new StringBuilder("GAME_HISTORY:");
            
            // Get the last 10 games
            int startIndex = Math.max(0, history.size() - 10);
            for (int i = startIndex; i < history.size(); i++) {
                String gameId = history.get(i);
                Game game = activeGames.get(gameId);
                
                // If game is still active
                if (game != null) {
                    String opponentId = game.getOpponentId(playerId);
                    Player opponent = activePlayers.get(opponentId);
                    String opponentName = opponent != null ? opponent.getName() : "Unknown";
                    
                    historyMsg.append(gameId).append(":")
                             .append(opponentName).append(":")
                             .append(game.getStatus()).append("|");
                }
            }
            
            sendMessage(historyMsg.toString());
        }
        
        // Clean up when client disconnects
        private void cleanup() {
            running.set(false);
            
            try {
                // Leave current lobby
                GameLobby lobby = lobbies.get(player.getCurrentLobbyId());
                if (lobby != null) {
                    lobby.removePlayer(playerId);
                }
                
                // Leave current game
                if (player.getCurrentGameId() != null) {
                    Game game = activeGames.get(player.getCurrentGameId());
                    if (game != null) {
                        game.playerDisconnected(playerId);
                    }
                }
                
                // Remove from waiting players
                waitingPlayers.remove(playerId);
                
                // Close connections
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
                
                // Remove player from active players
                activePlayers.remove(playerId);
                currentConnections.decrementAndGet();
                
                logger.info("Player " + player.getName() + " (" + playerId + ") disconnected");
                
            } catch (IOException e) {
                logger.warning("Error during cleanup for player " + playerId + ": " + e.getMessage());
            }
        }
        
        // Send a message to this client
        public void sendMessage(String message) {
            if (out != null && running.get()) {
                out.println(message);
            }
        }
    }
    
    // Class to represent and manage a single game
    private static class Game {
        private String gameId;
        private String player1Id; // X
        private String player2Id; // O
        private char[] board;
        private char currentTurn; // 'X' or 'O'
        private boolean gameOver;
        private String winner = null; // Player ID of winner, null if tie or game in progress
        private Timestamp startTime;
        private Timestamp endTime;
        private String rematchRequester;
        private int[] winningLine;
        
        public Game(String gameId, String player1Id, String player2Id) {
            this.gameId = gameId;
            this.player1Id = player1Id;
            this.player2Id = player2Id;
            this.board = new char[9];
            Arrays.fill(board, ' ');
            this.currentTurn = 'X'; // X goes first
            this.gameOver = false;
            this.startTime = new Timestamp(System.currentTimeMillis());
        }
        
        public void start() {
            // Notify players about game start
            sendToPlayer1("GAME_STARTED:X:" + gameId + ":" + getPlayer2().getName());
            sendToPlayer2("GAME_STARTED:O:" + gameId + ":" + getPlayer1().getName());
            
            // Notify player 1 (X) to make the first move
            sendToPlayer1("YOUR_TURN");
            
            // Send initial board state
            sendBoardToPlayers();
        }
        
        // Process a move from a player
        public synchronized void makeMove(String playerId, int position) {
            // Determine player's marker
            char playerMarker = (playerId.equals(player1Id)) ? 'X' : 'O';
            String playerTurnId = currentTurn == 'X' ? player1Id : player2Id;
            
            if (gameOver) {
                sendToPlayer(playerId, "ERROR:Game is over");
                return;
            }
            
            if (!playerId.equals(playerTurnId)) {
                sendToPlayer(playerId, "ERROR:Not your turn");
                return;
            }
            
            if (position < 0 || position > 8) {
                sendToPlayer(playerId, "ERROR:Invalid position");
                return;
            }
            
            if (board[position] != ' ') {
                sendToPlayer(playerId, "ERROR:Position already taken");
                return;
            }
            
            // Make the move
            board[position] = playerMarker;
            
            // Send the updated board to both players
            sendBoardToPlayers();
            
            // Check for win or draw
            checkGameStatus();
        }
        
        private void sendBoardToPlayers() {
            String boardMsg = "BOARD:" + String.valueOf(board);
            sendToPlayer1(boardMsg);
            sendToPlayer2(boardMsg);
        }
        
        private void checkGameStatus() {
            CheckWinResult result = checkWinner();
            
            if (result.hasWinner() || result.isTie()) {
                gameOver = true;
                endTime = new Timestamp(System.currentTimeMillis());
                
                if (result.isTie()) {
                    // It's a tie
                    sendToPlayer1("GAME_OVER:TIE");
                    sendToPlayer2("GAME_OVER:TIE");
                    
                    // Update player statistics
                    Player p1 = getPlayer1();
                    Player p2 = getPlayer2();
                    if (p1 != null) p1.incrementTies();
                    if (p2 != null) p2.incrementTies();
                } else {
                    // Someone won
                    winningLine = result.getWinningLine();
                    winner = currentTurn == 'X' ? player1Id : player2Id;
                    
                    String winLineStr = String.join("-", Arrays.stream(winningLine)
                                                         .mapToObj(String::valueOf)
                                                         .toArray(String[]::new));
                    
                    sendToPlayer1("GAME_OVER:" + result.getWinner() + ":" + winLineStr);
                    sendToPlayer2("GAME_OVER:" + result.getWinner() + ":" + winLineStr);
                    
                    // Update player statistics
                    Player winnerPlayer = activePlayers.get(winner);
                    Player loserPlayer = activePlayers.get(getOpponentId(winner));
                    
                    if (winnerPlayer != null) {
                        winnerPlayer.incrementWins();
                        winnerPlayer.updateRating(+15);  // Simple rating adjustment
                    }
                    
                    if (loserPlayer != null) {
                        loserPlayer.incrementLosses();
                        loserPlayer.updateRating(-10);  // Simple rating adjustment
                    }
                }
            } else {
                // Switch turns
                currentTurn = (currentTurn == 'X') ? 'O' : 'X';
                
                // Notify the next player it's their turn
                if (currentTurn == 'X') {
                    sendToPlayer1("YOUR_TURN");
                } else {
                    sendToPlayer2("YOUR_TURN");
                }
            }
        }
        
        // Class to hold check winner result
        private static class CheckWinResult {
            private char winner;
            private int[] winningLine;
            
            public CheckWinResult(char winner, int[] winningLine) {
                this.winner = winner;
                this.winningLine = winningLine;
            }
            
            public boolean hasWinner() {
                return winner != ' ' && winner != 'T';
            }
            
            public boolean isTie() {
                return winner == 'T';
            }
            
            public char getWinner() {
                return winner;
            }
            
            public int[] getWinningLine() {
                return winningLine;
            }
        }
        
        // Check if there's a winner or a tie
        private CheckWinResult checkWinner() {
            // Check rows
            for (int i = 0; i < 9; i += 3) {
                if (board[i] != ' ' && board[i] == board[i+1] && board[i] == board[i+2]) {
                    return new CheckWinResult(board[i], new int[]{i, i+1, i+2});
                }
            }
            
            // Check columns
            for (int i = 0; i < 3; i++) {
                if (board[i] != ' ' && board[i] == board[i+3] && board[i] == board[i+6]) {
                    return new CheckWinResult(board[i], new int[]{i, i+3, i+6});
                }
            }
            
            // Check diagonals
            if (board[0] != ' ' && board[0] == board[4] && board[0] == board[8]) {
                return new CheckWinResult(board[0], new int[]{0, 4, 8});
            }
            if (board[2] != ' ' && board[2] == board[4] && board[2] == board[6]) {
                return new CheckWinResult(board[2], new int[]{2, 4, 6});
            }
            
            // Check for tie (board full)
            boolean boardFull = true;
            for (char cell : board) {
                if (cell == ' ') {
                    boardFull = false;
                    break;
                }
            }

            if (boardFull) {
                return new CheckWinResult('T', null); // Tie
            }
            
            // No winner yet
            return new CheckWinResult(' ', null);
        }
        
        // Handle player disconnection
        public void playerDisconnected(String playerId) {
            if (gameOver) {
                return;
            }
            
            gameOver = true;
            endTime = new Timestamp(System.currentTimeMillis());
            
            // Find opponent
            String opponentId = getOpponentId(playerId);
            
            // Set opponent as winner
            winner = opponentId;
            
            // Update statistics
            Player disconnectedPlayer = activePlayers.get(playerId);
            Player opponentPlayer = activePlayers.get(opponentId);
            
            if (disconnectedPlayer != null) {
                disconnectedPlayer.incrementLosses();
                disconnectedPlayer.updateRating(-15);
                disconnectedPlayer.setCurrentGameId(null);
            }
            
            if (opponentPlayer != null) {
                opponentPlayer.incrementWins();
                opponentPlayer.updateRating(+10);
                // Don't clear opponent's game ID yet in case they want to view the result
            }
            
            // Notify the opponent
            Player opponent = activePlayers.get(opponentId);
            if (opponent != null && opponent.getClientHandler() != null) {
                opponent.getClientHandler().sendMessage("OPPONENT_DISCONNECTED");
            }
        }
        
        public void broadcastChat(String senderId, String message) {
            Player sender = activePlayers.get(senderId);
            if (sender != null) {
                String chatMessage = "GAME_CHAT:" + sender.getName() + ":" + message;
                
                sendToPlayer1(chatMessage);
                sendToPlayer2(chatMessage);
            }
        }
        
        private void sendToPlayer(String playerId, String message) {
            Player player = activePlayers.get(playerId);
            if (player != null && player.getClientHandler() != null) {
                player.getClientHandler().sendMessage(message);
            }
        }
        
        private void sendToPlayer1(String message) {
            sendToPlayer(player1Id, message);
        }
        
        private void sendToPlayer2(String message) {
            sendToPlayer(player2Id, message);
        }
        
        private Player getPlayer1() {
            return activePlayers.get(player1Id);
        }
        
        private Player getPlayer2() {
            return activePlayers.get(player2Id);
        }
        
        public String getOpponentId(String playerId) {
            return playerId.equals(player1Id) ? player2Id : player1Id;
        }
        
        public boolean isGameOver() {
            return gameOver;
        }
        
        public String getWinner() {
            return winner;
        }
        
        public String getStatus() {
            if (!gameOver) {
                return "IN_PROGRESS";
            } else if (winner == null) {
                return "TIE";
            } else if (winner.equals(player1Id)) {
                return "PLAYER1_WON";
            } else {
                return "PLAYER2_WON";
            }
        }
        
        public void setRematchRequester(String playerId) {
            this.rematchRequester = playerId;
        }
        
        public String getRematchRequester() {
            return rematchRequester;
        }
    }
}

