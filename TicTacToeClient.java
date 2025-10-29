package TicTacToee;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TicTacToeClient extends JFrame {
    // Network settings
    private static final int PORT = 5567;
    private static final String SERVER_ADDRESS = "localhost";
    
    // Network components
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientId;
    private String gameId;
    private char marker; // 'X' or 'O'
    private String playerName = "";
    private String opponentName = "Opponent";
    
    // Game state
    private boolean myTurn = false;
    private boolean gameActive = false;
    private int wins = 0;
    private int losses = 0;
    private int ties = 0;
    private char[] board = new char[9];
    private int[] winningLine = null;
    
    // UI Theme 
    private ThemeManager themeManager;
    private enum UIMode { LIGHT, DARK }
    private UIMode currentMode = UIMode.LIGHT;
    
    // Sound settings
    private boolean soundEnabled = true;
    private float soundVolume = 0.7f; // 0.0 to 1.0
    
    // Animation settings
    private boolean animationsEnabled = true;
    private int animationSpeed = 300; // ms
    
    // Reconnection
    private java.util.Timer reconnectTimer; 
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    
    // UI Tabs
    private JTabbedPane tabbedPane;
    private enum Tab { GAME, LOBBY, LEADERBOARD, PROFILE }
    
    // Game UI components
    private GameBoardPanel gamePanel;
    private JLabel statusLabel;
    private JLabel scoreLabel;
    private GradientButton connectButton;
    private GradientButton findGameButton;
    private GradientButton rematchButton;
    private ToggleButton soundToggleButton;
    private ToggleButton themeToggleButton;
    private JTextArea chatArea;
    private JTextField chatInput;
    private JPanel connectionPanel;
    private StatusIndicator connectionStatus;
    private JButton settingsButton;
    private JPanel gameControlPanel;
    
    // Lobby components
    private DefaultListModel<PlayerInfo> playerListModel;
    private JList<PlayerInfo> playerList;
    private JTextArea lobbyChatArea;
    private JTextField lobbyChatInput;
    private JComboBox<String> lobbySelector;
    private Map<String, String> lobbies = new HashMap<>(); // id -> name
    
    // Animation
    private javax.swing.Timer gameTimer; 
    private float fadeInProgress = 0.0f;
    
    // Profile components
    private JPanel statsPanel;
    private JLabel ratingLabel;
    private List<GameHistoryItem> gameHistory = new ArrayList<>();
    
    // Leaderboard
    private List<PlayerInfo> leaderboardPlayers = new ArrayList<>();
    private JPanel leaderboardPanel;
    
    // Player list in lobby
    private List<PlayerInfo> lobbyPlayers = new ArrayList<>();
    
    public TicTacToeClient() {
        themeManager = new ThemeManager(currentMode);
        
        setTitle("Tic Tac Toe Multiplayer");
        setSize(900, 700);
        setMinimumSize(new Dimension(800, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(themeManager.getBackgroundColor());
        setLayout(new BorderLayout());
        
        // Initialize board with empty spaces
        for (int i = 0; i < 9; i++) {
            board[i] = ' ';
        }
        
        // Get player name
        playerName = JOptionPane.showInputDialog(this, 
            "Enter your name:", 
            "Player Name", 
            JOptionPane.QUESTION_MESSAGE);
            
        if (playerName == null || playerName.trim().isEmpty()) {
            playerName = "Player" + (int)(Math.random() * 1000);
        }
        
        initializeGUI();
        initializeGameTimer();
        
        // Add window listener to handle closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
        
        // Setup reconnect timer
        reconnectTimer = new java.util.Timer();
        
        setLocationRelativeTo(null);
        setIconImage(createAppIcon().getImage());
        setVisible(true);
    }
    
    private void initializeGameTimer() {
        gameTimer = new javax.swing.Timer(16, e -> {
            if (fadeInProgress < 1.0f) {
                fadeInProgress += 0.025f;
                if (fadeInProgress > 1.0f) fadeInProgress = 1.0f;
                gamePanel.repaint();
            } else {
                ((javax.swing.Timer)e.getSource()).stop();
            }
        });
    }
    
    private ImageIcon createAppIcon() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw background circle
        g2d.setColor(new Color(52, 152, 219)); // Blue
        g2d.fillOval(0, 0, size, size);
        
        // Draw 'T' for Tic Tac Toe
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 22));
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString("T", (size - fm.stringWidth("T")) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
        
        g2d.dispose();
        return new ImageIcon(image);
    }
    
    private void initializeGUI() {
        // Main container with margins
        JPanel mainContainer = new JPanel(new BorderLayout(5, 5));
        mainContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainContainer.setBackground(themeManager.getBackgroundColor());
        
        // Header panel (top)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(themeManager.getBackgroundColor());
        
        // Left side: Status
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(themeManager.getBackgroundColor());
        statusLabel = new JLabel("Not connected to server");
        statusLabel.setFont(themeManager.getFont(ThemeManager.FontType.HEADING));
        statusLabel.setForeground(themeManager.getTextColor());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        // Right side: Connection status
        connectionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        connectionPanel.setBackground(themeManager.getBackgroundColor());
        connectionStatus = new StatusIndicator(StatusIndicator.Status.DISCONNECTED);
        connectionPanel.add(connectionStatus);
        
        // Add toggle buttons for theme and sound
        themeToggleButton = new ToggleButton("🌙", "☀️", currentMode == UIMode.DARK);
        themeToggleButton.addActionListener(e -> toggleTheme());
        
        soundToggleButton = new ToggleButton("🔊", "🔇", !soundEnabled);
        soundToggleButton.addActionListener(e -> toggleSound());
        
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        controlsPanel.setBackground(themeManager.getBackgroundColor());
        controlsPanel.add(soundToggleButton);
        controlsPanel.add(themeToggleButton);
        controlsPanel.add(connectionStatus);
        
        headerPanel.add(statusPanel, BorderLayout.WEST);
        headerPanel.add(controlsPanel, BorderLayout.EAST);
        
        // Score panel
        JPanel scorePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        scorePanel.setBackground(themeManager.getBackgroundColor());
        scoreLabel = new JLabel("Games: 0   Wins: 0   Losses: 0   Ties: 0");
        scoreLabel.setFont(themeManager.getFont(ThemeManager.FontType.HEADING_SMALL));
        scoreLabel.setForeground(themeManager.getTextColor());
        scorePanel.add(scoreLabel);
        
        // Combine header elements
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(themeManager.getBackgroundColor());
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(scorePanel, BorderLayout.SOUTH);
        topPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        
        mainContainer.add(topPanel, BorderLayout.NORTH);
        
        // Create the tabbed pane for different views
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        tabbedPane.setBackground(themeManager.getBackgroundColor());
        tabbedPane.setForeground(themeManager.getTextColor());
        
        // Game Tab
        JPanel gameTab = createGameTab();
        tabbedPane.addTab("Game", gameTab);
        
        // Lobby Tab
        JPanel lobbyTab = createLobbyTab();
        tabbedPane.addTab("Lobby", lobbyTab);
        
        // Leaderboard Tab
        JScrollPane leaderboardTab = createLeaderboardTab();
        tabbedPane.addTab("Leaderboard", leaderboardTab);
        
        // Profile Tab
        JPanel profileTab = createProfileTab();
        tabbedPane.addTab("Profile", profileTab);
        
        mainContainer.add(tabbedPane, BorderLayout.CENTER);
        
        // Control panel (bottom)
        gameControlPanel = createGameControlPanel();
        mainContainer.add(gameControlPanel, BorderLayout.SOUTH);
        
        add(mainContainer);
    }
    
    private JPanel createGameTab() {
        JPanel gameTabPanel = new JPanel(new BorderLayout(10, 10));
        gameTabPanel.setBackground(themeManager.getBackgroundColor());
        
        // Game Board Panel (center)
        gamePanel = new GameBoardPanel();
        
        // Add padding around game board
        JPanel gameBoardContainer = new JPanel(new BorderLayout());
        gameBoardContainer.setBackground(themeManager.getBackgroundColor());
        gameBoardContainer.add(gamePanel, BorderLayout.CENTER);
        gameBoardContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Chat Panel (right)
        JPanel chatPanel = createChatPanel();
        
        // Split the main content area to add chat panel
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gameBoardContainer, chatPanel);
        splitPane.setResizeWeight(0.7);
        splitPane.setDividerSize(5);
        splitPane.setBorder(null);
        splitPane.setBackground(themeManager.getBackgroundColor());
        
        gameTabPanel.add(splitPane, BorderLayout.CENTER);
        return gameTabPanel;
    }
    
    private JPanel createLobbyTab() {
        JPanel lobbyTabPanel = new JPanel(new BorderLayout(10, 10));
        lobbyTabPanel.setBackground(themeManager.getBackgroundColor());
        lobbyTabPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Top panel for lobby selection
        JPanel lobbySelectionPanel = new JPanel(new BorderLayout(5, 0));
        lobbySelectionPanel.setBackground(themeManager.getBackgroundColor());
        
        JLabel lobbyLabel = new JLabel("Select Lobby:");
        lobbyLabel.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        lobbyLabel.setForeground(themeManager.getTextColor());
        
        lobbySelector = new JComboBox<>();
        lobbySelector.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        lobbySelector.setBackground(themeManager.getComponentBackgroundColor());
        lobbySelector.setForeground(themeManager.getTextColor());
        lobbySelector.addActionListener(e -> {
            if (lobbySelector.getSelectedItem() != null) {
                String selected = lobbySelector.getSelectedItem().toString();
                for (Map.Entry<String, String> entry : lobbies.entrySet()) {
                    if (entry.getValue().equals(selected)) {
                        if (out != null) {
                            out.println("JOIN_LOBBY:" + entry.getKey());
                        }
                        break;
                    }
                }
            }
        });
        
        JButton refreshLobbiesButton = new JButton("⟳");
        refreshLobbiesButton.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        refreshLobbiesButton.addActionListener(e -> {
            if (out != null) {
                out.println("LIST_LOBBIES");
            }
        });
        
        lobbySelectionPanel.add(lobbyLabel, BorderLayout.WEST);
        lobbySelectionPanel.add(lobbySelector, BorderLayout.CENTER);
        lobbySelectionPanel.add(refreshLobbiesButton, BorderLayout.EAST);
        
        // Center panel with player list and chat
        JSplitPane lobbySplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        lobbySplitPane.setResizeWeight(0.3);
        lobbySplitPane.setDividerSize(5);
        
        // Player list (left)
        JPanel playerListPanel = new JPanel(new BorderLayout());
        playerListPanel.setBackground(themeManager.getBackgroundColor());
        playerListPanel.setBorder(new TitledBorder(new LineBorder(themeManager.getBorderColor()), "Players in Lobby"));
        
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        playerList.setCellRenderer(new PlayerListCellRenderer());
        playerList.setBackground(themeManager.getComponentBackgroundColor());
        playerList.setForeground(themeManager.getTextColor());
        JScrollPane playerScrollPane = new JScrollPane(playerList);
        
        playerListPanel.add(playerScrollPane, BorderLayout.CENTER);
        
        // Lobby chat (right)
        JPanel lobbyChatPanel = new JPanel(new BorderLayout(0, 5));
        lobbyChatPanel.setBackground(themeManager.getBackgroundColor());
        lobbyChatPanel.setBorder(new TitledBorder(new LineBorder(themeManager.getBorderColor()), "Lobby Chat"));
        
        lobbyChatArea = new JTextArea();
        lobbyChatArea.setEditable(false);
        lobbyChatArea.setLineWrap(true);
        lobbyChatArea.setWrapStyleWord(true);
        lobbyChatArea.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        lobbyChatArea.setBackground(themeManager.getComponentBackgroundColor());
        lobbyChatArea.setForeground(themeManager.getTextColor());
        JScrollPane lobbyChatScrollPane = new JScrollPane(lobbyChatArea);
        
        JPanel lobbyChatInputPanel = new JPanel(new BorderLayout());
        lobbyChatInputPanel.setBackground(themeManager.getBackgroundColor());
        
        lobbyChatInput = new JTextField();
        lobbyChatInput.setBorder(new LineBorder(themeManager.getBorderColor(), 1));
        lobbyChatInput.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        lobbyChatInput.addActionListener(e -> sendLobbyChatMessage());
        lobbyChatInput.setEnabled(false);
        lobbyChatInput.setBackground(themeManager.getComponentBackgroundColor());
        lobbyChatInput.setForeground(themeManager.getTextColor());
        
        JButton lobbySendButton = new JButton("Send");
        lobbySendButton.addActionListener(e -> sendLobbyChatMessage());
        lobbySendButton.setEnabled(false);
        lobbySendButton.setBackground(themeManager.getPrimaryButtonColor());
        lobbySendButton.setForeground(Color.WHITE);
        
        lobbyChatInputPanel.add(lobbyChatInput, BorderLayout.CENTER);
        lobbyChatInputPanel.add(lobbySendButton, BorderLayout.EAST);
        
        lobbyChatPanel.add(lobbyChatScrollPane, BorderLayout.CENTER);
        lobbyChatPanel.add(lobbyChatInputPanel, BorderLayout.SOUTH);
        
        lobbySplitPane.setLeftComponent(playerListPanel);
        lobbySplitPane.setRightComponent(lobbyChatPanel);
        
        lobbyTabPanel.add(lobbySelectionPanel, BorderLayout.NORTH);
        lobbyTabPanel.add(lobbySplitPane, BorderLayout.CENTER);
        
        return lobbyTabPanel;
    }
    
    private JScrollPane createLeaderboardTab() {
        leaderboardPanel = new JPanel();
        leaderboardPanel.setLayout(new BoxLayout(leaderboardPanel, BoxLayout.Y_AXIS));
        leaderboardPanel.setBackground(themeManager.getBackgroundColor());
        leaderboardPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JLabel titleLabel = new JLabel("Top Players");
        titleLabel.setFont(themeManager.getFont(ThemeManager.FontType.HEADING));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(themeManager.getTextColor());
        
        JButton refreshButton = new JButton("Refresh Leaderboard");
        refreshButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        refreshButton.setBackground(themeManager.getPrimaryButtonColor());
        refreshButton.setForeground(Color.WHITE);
        refreshButton.addActionListener(e -> {
            if (out != null) {
                out.println("GET_LEADERBOARD");
            }
        });
        
        leaderboardPanel.add(Box.createVerticalStrut(10));
        leaderboardPanel.add(titleLabel);
        leaderboardPanel.add(Box.createVerticalStrut(10));
        leaderboardPanel.add(refreshButton);
        leaderboardPanel.add(Box.createVerticalStrut(20));
        
        // Leaderboard will be populated when data is received from server
        
        JScrollPane scrollPane = new JScrollPane(leaderboardPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        
        return scrollPane;
    }
    
    private JPanel createProfileTab() {
        JPanel profileTabPanel = new JPanel(new BorderLayout(10, 10));
        profileTabPanel.setBackground(themeManager.getBackgroundColor());
        profileTabPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Profile header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(themeManager.getBackgroundColor());
        
        JPanel profilePanel = new JPanel();
        profilePanel.setLayout(new BoxLayout(profilePanel, BoxLayout.Y_AXIS));
        profilePanel.setBackground(themeManager.getBackgroundColor());
        
        JLabel nameLabel = new JLabel(playerName);
        nameLabel.setFont(themeManager.getFont(ThemeManager.FontType.HEADING));
        nameLabel.setForeground(themeManager.getTextColor());
        profilePanel.add(nameLabel);
        
        ratingLabel = new JLabel("Rating: 1200");  // Default value
        ratingLabel.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        ratingLabel.setForeground(themeManager.getTextColor());
        profilePanel.add(ratingLabel);
        
        // Profile picture placeholder
        JPanel avatarPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int size = Math.min(getWidth(), getHeight()) - 10;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                // Draw circle
                g2d.setColor(themeManager.getPrimaryColor());
                g2d.fillOval(x, y, size, size);
                
                // Draw first letter of name
                g2d.setColor(Color.WHITE);
                g2d.setFont(themeManager.getFont(ThemeManager.FontType.HEADING).deriveFont((float)(size * 0.6)));
                FontMetrics fm = g2d.getFontMetrics();
                String text = playerName.substring(0, 1).toUpperCase();
                g2d.drawString(text, 
                             x + (size - fm.stringWidth(text)) / 2, 
                             y + (size + fm.getAscent() - fm.getDescent()) / 2);
                g2d.dispose();
            }
            
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(80, 80);
            }
        };
        avatarPanel.setBackground(themeManager.getBackgroundColor());
        
        headerPanel.add(avatarPanel, BorderLayout.WEST);
        headerPanel.add(profilePanel, BorderLayout.CENTER);
        
        // Stats panel
        statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBackground(themeManager.getComponentBackgroundColor());
        statsPanel.setBorder(new TitledBorder(new LineBorder(themeManager.getBorderColor()), "Statistics"));
        
        // Add stats, will be updated when connected
        JLabel gamesPlayedLabel = new JLabel("Games Played: 0");
        gamesPlayedLabel.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        gamesPlayedLabel.setForeground(themeManager.getTextColor());
        statsPanel.add(gamesPlayedLabel);
        
        JLabel winRateLabel = new JLabel("Win Rate: 0%");
        winRateLabel.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        winRateLabel.setForeground(themeManager.getTextColor());
        statsPanel.add(winRateLabel);
        
        // Game history panel
        JPanel historyPanel = new JPanel();
        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setBackground(themeManager.getComponentBackgroundColor());
        historyPanel.setBorder(new TitledBorder(new LineBorder(themeManager.getBorderColor()), "Recent Games"));
        
        // This will be populated when we get data from server
        JLabel noGamesLabel = new JLabel("No games played yet.");
        noGamesLabel.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        noGamesLabel.setForeground(themeManager.getTextColor());
        noGamesLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        historyPanel.add(Box.createVerticalGlue());
        historyPanel.add(noGamesLabel);
        historyPanel.add(Box.createVerticalGlue());
        
        JButton refreshStats = new JButton("Refresh Stats");
        refreshStats.setAlignmentX(Component.CENTER_ALIGNMENT);
        refreshStats.setBackground(themeManager.getPrimaryButtonColor());
        refreshStats.setForeground(Color.WHITE);
        refreshStats.addActionListener(e -> {
            if (out != null) {
                out.println("GET_STATS");
                out.println("GET_HISTORY");
            }
        });
        
        // Combine everything
        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.setBackground(themeManager.getBackgroundColor());
        contentPanel.add(statsPanel, BorderLayout.NORTH);
        contentPanel.add(historyPanel, BorderLayout.CENTER);
        contentPanel.add(refreshStats, BorderLayout.SOUTH);
        
        profileTabPanel.add(headerPanel, BorderLayout.NORTH);
        profileTabPanel.add(contentPanel, BorderLayout.CENTER);
        
        return profileTabPanel;
    }
    
    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout(0, 10));
        chatPanel.setBackground(themeManager.getBackgroundColor());
        chatPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        JLabel chatLabel = new JLabel("Game Chat");
        chatLabel.setFont(themeManager.getFont(ThemeManager.FontType.HEADING_SMALL));
        chatLabel.setHorizontalAlignment(SwingConstants.CENTER);
        chatLabel.setForeground(themeManager.getTextColor());
        chatPanel.add(chatLabel, BorderLayout.NORTH);
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        chatArea.setBackground(themeManager.getComponentBackgroundColor());
        chatArea.setForeground(themeManager.getTextColor());
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(new LineBorder(themeManager.getBorderColor(), 1));
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        
        JPanel chatInputPanel = new JPanel(new BorderLayout());
        chatInputPanel.setBackground(themeManager.getBackgroundColor());
        
        chatInput = new JTextField();
        chatInput.setBorder(new LineBorder(themeManager.getBorderColor(), 1));
        chatInput.setFont(themeManager.getFont(ThemeManager.FontType.REGULAR));
        chatInput.setBackground(themeManager.getComponentBackgroundColor());
        chatInput.setForeground(themeManager.getTextColor());
        chatInput.addActionListener(e -> sendChatMessage());
        chatInput.setEnabled(false);
        
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendChatMessage());
        sendButton.setEnabled(false);
        sendButton.setBackground(themeManager.getPrimaryButtonColor());
        sendButton.setForeground(Color.WHITE);
        
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);
        return chatPanel;
    }
    
    private JPanel createGameControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        controlPanel.setBackground(themeManager.getBackgroundColor());
        controlPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
        
        connectButton = new GradientButton("Connect to Server");
        connectButton.addActionListener(e -> connectToServer());
        
        findGameButton = new GradientButton("Find Game");
        findGameButton.addActionListener(e -> findGame());
        findGameButton.setEnabled(false);
        
        rematchButton = new GradientButton("Rematch");
        rematchButton.addActionListener(e -> requestRematch());
        rematchButton.setEnabled(false);
        
        settingsButton = new GradientButton("⚙️ Settings");
        settingsButton.addActionListener(e -> showSettings());
        
        controlPanel.add(connectButton);
        controlPanel.add(findGameButton);
        controlPanel.add(rematchButton);
        controlPanel.add(settingsButton);
        
        return controlPanel;
    }
    
    private void toggleTheme() {
        currentMode = (currentMode == UIMode.LIGHT) ? UIMode.DARK : UIMode.LIGHT;
        themeManager.setMode(currentMode);
        SwingUtilities.invokeLater(this::applyTheme);
    }
    
    private void applyTheme() {
        // Update main components
        getContentPane().setBackground(themeManager.getBackgroundColor());
        tabbedPane.setBackground(themeManager.getBackgroundColor());
        tabbedPane.setForeground(themeManager.getTextColor());
        
        // Update game panel
        gamePanel.setBackground(themeManager.getBoardBackgroundColor());
        
        // Update labels
        statusLabel.setForeground(themeManager.getTextColor());
        scoreLabel.setForeground(themeManager.getTextColor());
        
        // Update text components
        chatArea.setBackground(themeManager.getComponentBackgroundColor());
        chatArea.setForeground(themeManager.getTextColor());
        chatInput.setBackground(themeManager.getComponentBackgroundColor());
        chatInput.setForeground(themeManager.getTextColor());
        
        lobbyChatArea.setBackground(themeManager.getComponentBackgroundColor());
        lobbyChatArea.setForeground(themeManager.getTextColor());
        lobbyChatInput.setBackground(themeManager.getComponentBackgroundColor());
        lobbyChatInput.setForeground(themeManager.getTextColor());
        
        // Update player list
        playerList.setBackground(themeManager.getComponentBackgroundColor());
        playerList.setForeground(themeManager.getTextColor());
        
        // Update panels
        gameControlPanel.setBackground(themeManager.getBackgroundColor());
        
        // Repaint everything
        repaint();
        revalidate();
    }
    
    private void toggleSound() {
        soundEnabled = !soundEnabled;
        soundToggleButton.setSelected(!soundEnabled);
    }
    
    private void showSettings() {
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Sound settings
        JPanel soundPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox soundToggle = new JCheckBox("Enable Sound Effects", soundEnabled);
        
        // Volume slider
        JSlider volumeSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, (int)(soundVolume * 100));
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setMinorTickSpacing(5);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);
        
        soundPanel.add(soundToggle);
        
        JPanel volumePanel = new JPanel(new BorderLayout());
        volumePanel.setBorder(new TitledBorder("Volume"));
        volumePanel.add(volumeSlider);
        
        // Animation settings
        JPanel animPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox animToggle = new JCheckBox("Enable Animations", animationsEnabled);
        animPanel.add(animToggle);
        
        // Server settings
        JPanel serverPanel = new JPanel(new BorderLayout(5, 5));
        serverPanel.setBorder(new TitledBorder("Server Connection"));
        
        JPanel addressPanel = new JPanel(new BorderLayout(5, 0));
        addressPanel.add(new JLabel("Server Address:"), BorderLayout.WEST);
        JTextField serverField = new JTextField(SERVER_ADDRESS);
        addressPanel.add(serverField, BorderLayout.CENTER);
        
        JPanel portPanel = new JPanel(new BorderLayout(5, 0));
        portPanel.add(new JLabel("Port:"), BorderLayout.WEST);
        JTextField portField = new JTextField(String.valueOf(PORT));
        portPanel.add(portField, BorderLayout.CENTER);
        
        serverPanel.add(addressPanel, BorderLayout.NORTH);
        serverPanel.add(portPanel, BorderLayout.SOUTH);
        
        // Player settings
        JPanel playerPanel = new JPanel(new BorderLayout(5, 0));
        playerPanel.setBorder(new TitledBorder("Player Profile"));
        playerPanel.add(new JLabel("Your Name:"), BorderLayout.WEST);
        JTextField nameField = new JTextField(playerName);
        playerPanel.add(nameField, BorderLayout.CENTER);
        
        // Add all panels to settings
        settingsPanel.add(soundPanel);
        settingsPanel.add(volumePanel);
        settingsPanel.add(Box.createVerticalStrut(10));
        settingsPanel.add(animPanel);
        settingsPanel.add(Box.createVerticalStrut(10));
        settingsPanel.add(serverPanel);
        settingsPanel.add(Box.createVerticalStrut(10));
        settingsPanel.add(playerPanel);
        
        int result = JOptionPane.showConfirmDialog(this, settingsPanel, 
                "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                
        if (result == JOptionPane.OK_OPTION) {
            soundEnabled = soundToggle.isSelected();
            soundVolume = volumeSlider.getValue() / 100.0f;
            animationsEnabled = animToggle.isSelected();
            
            String newName = nameField.getText().trim();
            if (!newName.isEmpty() && !newName.equals(playerName)) {
                playerName = newName;
                if (out != null && clientId != null) {
                    out.println("NAME:" + playerName);
                }
                setTitle("Tic Tac Toe Multiplayer - " + playerName);
            }
        }
    }
    
    private void sendChatMessage() {
        if (chatInput.getText().trim().isEmpty() || out == null || !gameActive) {
            return;
        }
        
        String message = chatInput.getText().trim();
        out.println("GAME_CHAT:" + message);
        addChatMessage("You", message);
        chatInput.setText("");
    }
    
    private void sendLobbyChatMessage() {
        if (lobbyChatInput.getText().trim().isEmpty() || out == null) {
            return;
        }
        
        String message = lobbyChatInput.getText().trim();
        out.println("LOBBY_CHAT:" + message);
        lobbyChatInput.setText("");
    }
    
    private void addChatMessage(String sender, String message) {
        chatArea.append(sender + ": " + message + "\n");
        // Scroll to bottom
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
    
    private void addLobbyChatMessage(String sender, String message) {
        lobbyChatArea.append(sender + ": " + message + "\n");
        // Scroll to bottom
        lobbyChatArea.setCaretPosition(lobbyChatArea.getDocument().getLength());
    }
    
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Start a thread to listen for server messages
            Thread networkThread = new Thread(this::listenForServerMessages);
            networkThread.setDaemon(true);
            networkThread.start();
            
            // Update connection status
            connectionStatus.setStatus(StatusIndicator.Status.CONNECTED);
            
            statusLabel.setText("Connected to server. Click 'Find Game' to start.");
            connectButton.setEnabled(false);
            findGameButton.setEnabled(true);
            lobbyChatInput.setEnabled(true);
            
            // Send player name
            out.println("NAME:" + playerName);
            
            // Request lobby list
            out.println("LIST_LOBBIES");
            
            // Request stats
            out.println("GET_STATS");
            
            // Request leaderboard
            out.println("GET_LEADERBOARD");
            
            // Set window title with player name
            setTitle("Tic Tac Toe Multiplayer - " + playerName);
            
            // Stop any reconnect attempts
            stopReconnectTimer();
            reconnectAttempts = 0;
            
            playSound("connect");
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Error connecting to server: " + e.getMessage(),
                "Connection Error", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Failed to connect to server");
            
            // Start reconnect timer if not already running
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                startReconnectTimer();
            }
        }
    }
    
    private void startReconnectTimer() {
        connectionStatus.setStatus(StatusIndicator.Status.RECONNECTING);
        reconnectTimer = new java.util.Timer();
        reconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    reconnectAttempts++;
                    statusLabel.setText("Reconnecting... Attempt " + reconnectAttempts);
                    connectToServer();
                });
            }
        }, 5000);
    }
    
    private void stopReconnectTimer() {
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }
    }
    
    private void findGame() {
        if (out != null) {
            out.println("FIND_GAME");
            statusLabel.setText("Finding a game...");
            findGameButton.setEnabled(false);
            chatInput.setEnabled(false);
            
            tabbedPane.setSelectedIndex(Tab.GAME.ordinal());
            
            playSound("search");
        }
    }
    
    private void requestRematch() {
        if (out != null && gameId != null) {
            out.println("REMATCH:" + gameId);
            rematchButton.setEnabled(false);
            statusLabel.setText("Requesting rematch...");
            
            playSound("button");
        }
    }
    
    private void makeMove(int position) {
        if (gameActive && myTurn && board[position] == ' ') {
            out.println("MOVE:" + position);
            
            // Temporarily disable moves until server confirms
            myTurn = false;
            
            // Disable all buttons temporarily
            gamePanel.disableAllCells();
            
            playSound("move");
        }
    }
    
    private void playSound(String soundType) {
        if (!soundEnabled) return;
        
        new Thread(() -> {
            try {
                String soundFile = "";
                
                switch (soundType) {
                    case "move":
                        soundFile = "/sounds/move.wav";
                        break;
                    case "win":
                        soundFile = "/sounds/win.wav";
                        break;
                    case "lose":
                        soundFile = "/sounds/lose.wav";
                        break;
                    case "tie":
                        soundFile = "/sounds/tie.wav";
                        break;
                    case "connect":
                        soundFile = "/sounds/connect.wav";
                        break;
                    case "disconnect":
                        soundFile = "/sounds/disconnect.wav";
                        break;
                    case "search":
                        soundFile = "/sounds/search.wav";
                        break;
                    case "button":
                        soundFile = "/sounds/button.wav";
                        break;
                    case "error":
                        soundFile = "/sounds/error.wav";
                        break;
                }
                
                // This would normally load the sound from resources
                // For this example, we'll just print what sound would play
                System.out.println("Playing sound: " + soundType + " (file: " + soundFile + ")");
                
            } catch (Exception e) {
                System.err.println("Error playing sound: " + e.getMessage());
            }
        }).start();
    }
    
    private void listenForServerMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Received from server: " + message);
                processServerMessage(message);
            }
        } catch (IOException e) {
            handleDisconnect();
        }
    }
    
    private void handleDisconnect() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Disconnected from server");
            connectionStatus.setStatus(StatusIndicator.Status.DISCONNECTED);
            resetGame();
            connectButton.setEnabled(true);
            findGameButton.setEnabled(false);
            rematchButton.setEnabled(false);
            chatInput.setEnabled(false);
            lobbyChatInput.setEnabled(false);
            
            // Start reconnect timer if not already at max attempts
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                startReconnectTimer();
            }
            
            playSound("disconnect");
        });
    }
    
    private void processServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.startsWith("CONNECTED:")) {
                clientId = message.substring(10);
                statusLabel.setText("Connected as: " + playerName);
            } 
            else if (message.startsWith("SERVER_INFO:")) {
                String info = message.substring(12);
                addSystemMessage("Server: " + info);
            } 
            else if (message.equals("WAITING")) {
                statusLabel.setText("Waiting for an opponent...");
            } 
            else if (message.startsWith("GAME_STARTED:")) {
                // Format: GAME_STARTED:X:game_id:opponent_name or GAME_STARTED:O:game_id:opponent_name
                String[] parts = message.split(":");
                marker = parts[1].charAt(0);
                gameId = parts[2];
                opponentName = parts.length > 3 ? parts[3] : "Opponent";
                
                gameActive = true;
                statusLabel.setText("Game started! You are '" + marker + "' vs " + opponentName);
                
                // Clear chat
                chatArea.setText("");
                chatInput.setEnabled(true);
                
                // Add system message to chat
                addSystemMessage("Game started. You are playing as '" + marker + "' against " + opponentName);
                
                // Reset the board
                resetBoard();
                
                // Reset winning line
                winningLine = null;
                
                // Ensure we're on the game tab
                tabbedPane.setSelectedIndex(Tab.GAME.ordinal());
                
                // Start fade-in animation
                fadeInProgress = 0.0f;
                gameTimer.start();
                
                playSound("connect");
            } 
            else if (message.equals("YOUR_TURN")) {
                myTurn = true;
                statusLabel.setText("Your turn! (You are '" + marker + "')");
                
                // Enable valid moves
                gamePanel.enableValidMoves();
                
                playSound("button");
            } 
            else if (message.startsWith("BOARD:")) {
                // Format: BOARD:XO OX OXO (spaces for empty cells)
                String boardState = message.substring(6);
                
                for (int i = 0; i < 9 && i < boardState.length(); i++) {
                    char oldValue = board[i];
                    char newValue = boardState.charAt(i);
                    board[i] = newValue;
                }
                
                // Update the game board display
                gamePanel.updateBoard();
                
                // If it's my turn, enable buttons for valid moves
                if (myTurn) {
                    gamePanel.enableValidMoves();
                } else {
                    // Disable all buttons when it's not my turn
                    gamePanel.disableAllCells();
                }
            } 
            else if (message.startsWith("GAME_OVER:")) {
                // Format: GAME_OVER:X:0-4-8 or GAME_OVER:O:2-4-6 or GAME_OVER:TIE
                String[] parts = message.split(":");
                String result = parts[1];
                gameActive = false;
                myTurn = false;
                
                // Get winning line if provided
                if (parts.length > 2 && !result.equals("TIE")) {
                    String[] lineIndices = parts[2].split("-");
                    winningLine = new int[lineIndices.length];
                    for (int i = 0; i < lineIndices.length; i++) {
                        winningLine[i] = Integer.parseInt(lineIndices[i]);
                    }
                }
                
                // Update game board to show winning line
                gamePanel.setWinningLine(winningLine);
                
                // Disable all buttons
                gamePanel.disableAllCells();
                
                // Update score
                if (result.equals("TIE")) {
                    ties++;
                    statusLabel.setText("Game over: It's a tie!");
                    addSystemMessage("Game ended in a tie.");
                    playSound("tie");
                } else if (result.charAt(0) == marker) {
                    wins++;
                    statusLabel.setText("Game over: You won!");
                    addSystemMessage("You won the game!");
                    playSound("win");
                } else {
                    losses++;
                    statusLabel.setText("Game over: You lost!");
                    addSystemMessage("You lost the game.");
                    playSound("lose");
                }
                
                updateScoreLabel();
                
                findGameButton.setEnabled(true);
                rematchButton.setEnabled(true);
                chatInput.setEnabled(false);
            } 
            // Process other server messages...
            // This is abbreviated to focus on the timer issue
        });
    }
    
    private void updateLeaderboard(String leaderboardData) {
        // Implementation for updating leaderboard
    }
    
    private void updateGameHistory(String historyData) {
        // Implementation for updating game history
    }
    
    private void updateProfileStats(int rating) {
        // Implementation for updating profile stats
    }
    
    private void updateScoreLabel() {
        int totalGames = wins + losses + ties;
        scoreLabel.setText("Games: " + totalGames + 
                           "   Wins: " + wins + 
                           "   Losses: " + losses + 
                           "   Ties: " + ties);
    }
    
    private void addSystemMessage(String message) {
        if (chatArea != null) {
            chatArea.append("System: " + message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }
    
    private void resetBoard() {
        for (int i = 0; i < 9; i++) {
            board[i] = ' ';
        }
        winningLine = null;
        gamePanel.updateBoard();
        gamePanel.disableAllCells();
    }
    
    private void resetGame() {
        gameActive = false;
        myTurn = false;
        resetBoard();
    }
    
    private void disconnect() {
        if (out != null) {
            out.println("QUIT");
        }
        
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }
        
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connections: " + e.getMessage());
        }
    }
    
    // Game board panel with custom drawing
    private class GameBoardPanel extends JPanel {
        private final int CELL_PADDING = 15;
        private final Color GRID_COLOR = themeManager.getGridColor();
        private final Color BOARD_COLOR = themeManager.getBoardBackgroundColor();
        private final Color X_COLOR = themeManager.getXColor();
        private final Color O_COLOR = themeManager.getOColor();
        private final Color HOVER_COLOR = themeManager.getHoverColor();
        private final Color WIN_LINE_COLOR = themeManager.getWinLineColor();
        
        private int hoverCell = -1;
        
        public GameBoardPanel() {
            setBackground(BOARD_COLOR);
            setLayout(new GridLayout(3, 3, 6, 6));
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!gameActive || !myTurn) return;
                    
                    // Convert mouse click to board position
                    int cellWidth = getWidth() / 3;
                    int cellHeight = getHeight() / 3;
                    
                    int col = e.getX() / cellWidth;
                    int row = e.getY() / cellHeight;
                    
                    int position = row * 3 + col;
                    
                    // Make move if valid
                    if (position >= 0 && position < 9 && board[position] == ' ') {
                        makeMove(position);
                    }
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverCell = -1;
                    repaint();
                }
            });
            
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (!gameActive || !myTurn) return;
                    
                    // Convert mouse position to board position
                    int cellWidth = getWidth() / 3;
                    int cellHeight = getHeight() / 3;
                    
                    int col = e.getX() / cellWidth;
                    int row = e.getY() / cellHeight;
                    
                    int position = row * 3 + col;
                    
                    // Only show hover effect for empty cells
                    if (position >= 0 && position < 9 && board[position] == ' ') {
                        hoverCell = position;
                    } else {
                        hoverCell = -1;
                    }
                    
                    repaint();
                }
            });
        }
        
        public void updateBoard() {
            repaint();
        }
        
        public void enableValidMoves() {
            repaint();
        }
        
        public void disableAllCells() {
            hoverCell = -1;
            repaint();
        }
        
        public void setWinningLine(int[] line) {
            winningLine = line;
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            
            // Set rendering hints for better quality
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int width = getWidth();
            int height = getHeight();
            int cellWidth = width / 3;
            int cellHeight = height / 3;
            
            // Draw the board background
            g2d.setColor(BOARD_COLOR);
            g2d.fillRect(0, 0, width, height);
            
            // Draw hover effect
            if (gameActive && myTurn && hoverCell != -1 && board[hoverCell] == ' ') {
                int row = hoverCell / 3;
                int col = hoverCell % 3;
                
                g2d.setColor(HOVER_COLOR);
                g2d.fillRect(col * cellWidth, row * cellHeight, cellWidth, cellHeight);
                
                // Draw preview marker (semi-transparent)
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                drawMarker(g2d, col, row, marker, marker == 'X' ? X_COLOR : O_COLOR);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }
            
            // Draw the grid lines
            g2d.setColor(GRID_COLOR);
            g2d.setStroke(new BasicStroke(6));
            
            // Vertical lines
            g2d.drawLine(cellWidth, CELL_PADDING, cellWidth, height - CELL_PADDING);
            g2d.drawLine(2 * cellWidth, CELL_PADDING, 2 * cellWidth, height - CELL_PADDING);
            
            // Horizontal lines
            g2d.drawLine(CELL_PADDING, cellHeight, width - CELL_PADDING, cellHeight);
            g2d.drawLine(CELL_PADDING, 2 * cellHeight, width - CELL_PADDING, 2 * cellHeight);
            
            // Draw the markers with fade-in effect
            for (int i = 0; i < 9; i++) {
                if (board[i] != ' ') {
                    int row = i / 3;
                    int col = i % 3;
                    
                    Color markerColor = board[i] == 'X' ? X_COLOR : O_COLOR;
                    
                    // Apply fade-in animation
                    if (animationsEnabled) {
                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeInProgress));
                    }
                    
                    drawMarker(g2d, col, row, board[i], markerColor);
                    
                    // Reset composite
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                }
            }
            
            // Draw the winning line if applicable
            if (winningLine != null && winningLine.length > 0) {
                g2d.setColor(WIN_LINE_COLOR);
                g2d.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                
                // Calculate the center points of the winning cells
                int startPos = winningLine[0];
                int endPos = winningLine[2];
                
                int startRow = startPos / 3;
                int startCol = startPos % 3;
                int endRow = endPos / 3;
                int endCol = endPos % 3;
                
                int startX = startCol * cellWidth + cellWidth / 2;
                int startY = startRow * cellHeight + cellHeight / 2;
                int endX = endCol * cellWidth + cellWidth / 2;
                int endY = endRow * cellHeight + cellHeight / 2;
                
                g2d.drawLine(startX, startY, endX, endY);
            }
            
            g2d.dispose();
        }
        
        private void drawMarker(Graphics2D g2d, int col, int row, char marker, Color color) {
            int cellWidth = getWidth() / 3;
            int cellHeight = getHeight() / 3;
            
            int x = col * cellWidth;
            int y = row * cellHeight;
            
            int padding = cellWidth / 5;  // 20% padding
            
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            
            if (marker == 'X') {
                // Draw X
                g2d.drawLine(x + padding, y + padding, x + cellWidth - padding, y + cellHeight - padding);
                g2d.drawLine(x + cellWidth - padding, y + padding, x + padding, y + cellHeight - padding);
            } else if (marker == 'O') {
                // Draw O
                g2d.drawOval(x + padding, y + padding, cellWidth - 2 * padding, cellHeight - 2 * padding);
            }
        }
    }
    
    // Class for handling themes & styles
    private class ThemeManager {
        private UIMode mode;
        
        // Colors for light mode
        private final Color LIGHT_BACKGROUND = new Color(240, 240, 240);
        private final Color LIGHT_COMPONENT_BG = new Color(255, 255, 255);
        private final Color LIGHT_TEXT = new Color(50, 50, 50);
        private final Color LIGHT_BORDER = new Color(200, 200, 200);
        private final Color LIGHT_BOARD_BG = new Color(255, 255, 255);
        private final Color LIGHT_GRID = new Color(180, 180, 180);
        
        // Colors for dark mode
        private final Color DARK_BACKGROUND = new Color(30, 30, 30);
        private final Color DARK_COMPONENT_BG = new Color(50, 50, 50);
        private final Color DARK_TEXT = new Color(240, 240, 240);
        private final Color DARK_BORDER = new Color(70, 70, 70);
        private final Color DARK_BOARD_BG = new Color(40, 40, 40);
        private final Color DARK_GRID = new Color(70, 70, 70);
        
        // Accent colors (same for both modes)
        private final Color PRIMARY_COLOR = new Color(41, 128, 185); // Blue
        private final Color SECONDARY_COLOR = new Color(46, 204, 113); // Green
        private final Color ACCENT_COLOR = new Color(230, 126, 34); // Orange
        private final Color X_COLOR = new Color(41, 128, 185); // Blue
        private final Color O_COLOR = new Color(231, 76, 60); // Red
        private final Color HOVER_COLOR = new Color(240, 240, 240, 100);
        private final Color WIN_LINE_COLOR = new Color(46, 204, 113, 200); // Green
        
        public enum FontType {
            HEADING, HEADING_SMALL, REGULAR, REGULAR_BOLD
        }
        
        public ThemeManager(UIMode mode) {
            this.mode = mode;
        }
        
        public void setMode(UIMode mode) {
            this.mode = mode;
        }
        
        public Color getBackgroundColor() {
            return mode == UIMode.LIGHT ? LIGHT_BACKGROUND : DARK_BACKGROUND;
        }
        
        public Color getComponentBackgroundColor() {
            return mode == UIMode.LIGHT ? LIGHT_COMPONENT_BG : DARK_COMPONENT_BG;
        }
        
        public Color getTextColor() {
            return mode == UIMode.LIGHT ? LIGHT_TEXT : DARK_TEXT;
        }
        
        public Color getBorderColor() {
            return mode == UIMode.LIGHT ? LIGHT_BORDER : DARK_BORDER;
        }
        
        public Color getBoardBackgroundColor() {
            return mode == UIMode.LIGHT ? LIGHT_BOARD_BG : DARK_BOARD_BG;
        }
        
        public Color getGridColor() {
            return mode == UIMode.LIGHT ? LIGHT_GRID : DARK_GRID;
        }
        
        public Color getPrimaryColor() {
            return PRIMARY_COLOR;
        }
        
        public Color getSecondaryColor() {
            return SECONDARY_COLOR;
        }
        
        public Color getAccentColor() {
            return ACCENT_COLOR;
        }
        
        public Color getXColor() {
            return X_COLOR;
        }
        
        public Color getOColor() {
            return O_COLOR;
        }
        
        public Color getHoverColor() {
            return HOVER_COLOR;
        }
        
        public Color getWinLineColor() {
            return WIN_LINE_COLOR;
        }
        
        public Color getPrimaryButtonColor() {
            return PRIMARY_COLOR;
        }
        
        public Font getFont(FontType type) {
            switch (type) {
                case HEADING:
                    return new Font("Segoe UI", Font.BOLD, 18);
                case HEADING_SMALL:
                    return new Font("Segoe UI", Font.BOLD, 16);
                case REGULAR:
                    return new Font("Segoe UI", Font.PLAIN, 14);
                case REGULAR_BOLD:
                    return new Font("Segoe UI", Font.BOLD, 14);
                default:
                    return new Font("Segoe UI", Font.PLAIN, 14);
            }
        }
    }
    
    // Custom UI components
    private class GradientButton extends JButton {
        private Color startColor = new Color(41, 128, 185); // Blue
        private Color endColor = new Color(52, 152, 219); // Lighter blue
        private Color textColor = Color.WHITE;
        private Color disabledColor = new Color(200, 200, 200);
        
        public GradientButton(String text) {
            super(text);
            setForeground(textColor);
            setFont(themeManager.getFont(ThemeManager.FontType.REGULAR_BOLD));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(new EmptyBorder(10, 20, 10, 20));
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int width = getWidth();
            int height = getHeight();
            
            // Create gradient
            GradientPaint gp;
            if (isEnabled()) {
                gp = new GradientPaint(0, 0, startColor, 0, height, endColor);
            } else {
                gp = new GradientPaint(0, 0, disabledColor, 0, height, disabledColor);
            }
            
            g2d.setPaint(gp);
            
            // Draw rounded button body
            g2d.fillRoundRect(0, 0, width, height, 10, 10);
            
            // Draw highlighted border when mouse over
            if (getModel().isRollover() && isEnabled()) {
                g2d.setColor(new Color(255, 255, 255, 50));
                g2d.setStroke(new BasicStroke(3));
                g2d.drawRoundRect(1, 1, width - 3, height - 3, 10, 10);
            }
            
            // Draw text
            FontMetrics fm = g2d.getFontMetrics();
            Rectangle stringBounds = fm.getStringBounds(getText(), g2d).getBounds();
            
            int textX = (width - stringBounds.width) / 2;
            int textY = (height - stringBounds.height) / 2 + fm.getAscent();
            
            g2d.setColor(textColor);
            g2d.drawString(getText(), textX, textY);
            
            g2d.dispose();
        }
    }
    
    private class ToggleButton extends JButton {
        private final String onText;
        private final String offText;
        private boolean selected;
        
        public ToggleButton(String onText, String offText, boolean initialState) {
            this.onText = onText;
            this.offText = offText;
            this.selected = initialState;
            
            setText(selected ? onText : offText);
            setFont(new Font("Segoe UI", Font.BOLD, 16));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            
            addActionListener(e -> {
                selected = !selected;
                setText(selected ? onText : offText);
            });
        }
        
        public boolean isSelected() {
            return selected;
        }
        
        public void setSelected(boolean selected) {
            this.selected = selected;
            setText(selected ? onText : offText);
        }
    }
    
    private class StatusIndicator extends JComponent {
        public enum Status {
            CONNECTED, DISCONNECTED, RECONNECTING
        }
        
        private Status status;
        private javax.swing.Timer pulseTimer;
        private float pulseAlpha = 0.0f;
        private boolean pulseDirection = true;
        
        public StatusIndicator(Status initialStatus) {
            this.status = initialStatus;
            setPreferredSize(new Dimension(24, 24));
            
            // Start pulse animation for reconnecting status
            pulseTimer = new javax.swing.Timer(50, e -> {
                if (pulseDirection) {
                    pulseAlpha += 0.05f;
                    if (pulseAlpha >= 1.0f) {
                        pulseAlpha = 1.0f;
                        pulseDirection = false;
                    }
                } else {
                    pulseAlpha -= 0.05f;
                    if (pulseAlpha <= 0.3f) {
                        pulseAlpha = 0.3f;
                        pulseDirection = true;
                    }
                }
                repaint();
            });
        }
        
        public void setStatus(Status status) {
            this.status = status;
            
            // Start/stop pulse animation
            if (status == Status.RECONNECTING) {
                if (!pulseTimer.isRunning()) {
                    pulseTimer.start();
                }
            } else {
                if (pulseTimer.isRunning()) {
                    pulseTimer.stop();
                }
            }
            
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int size = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            
            // Draw status indicator
            switch (status) {
                case CONNECTED:
                    g2d.setColor(new Color(39, 174, 96)); // Green
                    break;
                case DISCONNECTED:
                    g2d.setColor(new Color(231, 76, 60)); // Red
                    break;
                case RECONNECTING:
                    // Use pulse effect for reconnecting
                    g2d.setColor(new Color(230, 126, 34, (int)(pulseAlpha * 255))); // Orange with pulse
                    break;
            }
            
            g2d.fillOval(x, y, size, size);
            
            // Draw border
            g2d.setColor(new Color(0, 0, 0, 30));
            g2d.setStroke(new BasicStroke(1));
            g2d.drawOval(x, y, size, size);
            
            g2d.dispose();
        }
    }
    
    // Data classes
    private class PlayerInfo {
        private String id;
        private String name;
        private int wins;
        private int losses;
        private int ties;
        private int rating = 1200;
        private int rank;
        
        public PlayerInfo(String id, String name, int wins, int losses, int ties) {
            this.id = id;
            this.name = name;
            this.wins = wins;
            this.losses = losses;
            this.ties = ties;
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
        
        public double getWinRate() {
            return getTotalGames() > 0 ? (double) wins / getTotalGames() * 100 : 0;
        }
        
        public int getRating() {
            return rating;
        }
        
        public void setRating(int rating) {
            this.rating = rating;
        }
        
        public int getRank() {
            return rank;
        }
        
        public void setRank(int rank) {
            this.rank = rank;
        }
        
        @Override
        public String toString() {
            return name + " (W: " + wins + ", L: " + losses + ")";
        }
    }
    
    private class PlayerListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof PlayerInfo) {
                PlayerInfo player = (PlayerInfo) value;
                label.setText(player.getName());
                
                if (isSelected) {
                    label.setBackground(themeManager.getPrimaryColor());
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(themeManager.getComponentBackgroundColor());
                    label.setForeground(themeManager.getTextColor());
                }
            }
            
            return label;
        }
    }
    
    private class GameHistoryItem {
        private String gameId;
        private String opponent;
        private String result;
        
        public GameHistoryItem(String gameId, String opponent, String result) {
            this.gameId = gameId;
            this.opponent = opponent;
            this.result = result;
        }
        
        public String getGameId() {
            return gameId;
        }
        
        public String getOpponent() {
            return opponent;
        }
        
        public String getResult() {
            return result;
        }
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(TicTacToeClient::new);
    }
}
