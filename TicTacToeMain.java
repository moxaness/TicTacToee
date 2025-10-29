package TicTacToee;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

public class TicTacToeMain extends JFrame {
    
    public TicTacToeMain() {
        setTitle("Tic Tac Toe Launcher");
        setSize(450, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Header
        JLabel headerLabel = new JLabel("Tic Tac Toe Multiplayer", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        
        // Buttons panel
        JPanel buttonsPanel = new JPanel(new GridLayout(3, 1, 0, 10));
        
        JButton startServerButton = createStyledButton("Start Server");
        startServerButton.addActionListener(e -> startServer());
        
        JButton startClientButton = createStyledButton("Start Client");
        startClientButton.addActionListener(e -> startClient());
        
        JButton exitButton = createStyledButton("Exit");
        exitButton.addActionListener(e -> System.exit(0));
        
        buttonsPanel.add(startServerButton);
        buttonsPanel.add(startClientButton);
        buttonsPanel.add(exitButton);
        
        // Footer
        JLabel footerLabel = new JLabel("© 2023 Multiplayer Tic Tac Toe", SwingConstants.CENTER);
        footerLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        
        mainPanel.add(headerLabel, BorderLayout.NORTH);
        mainPanel.add(buttonsPanel, BorderLayout.CENTER);
        mainPanel.add(footerLabel, BorderLayout.SOUTH);
        
        add(mainPanel);
        setVisible(true);
    }
    
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 16));
        button.setPreferredSize(new Dimension(200, 50));
        button.setFocusPainted(false);
        
        // Add a gradient look to the buttons
        button.setBackground(new Color(41, 128, 185));
        button.setForeground(Color.WHITE);
        button.setBorderPainted(false);
        
        // Add hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(52, 152, 219));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(41, 128, 185));
            }
        });
        
        return button;
    }
    
    private void startServer() {
        new Thread(() -> {
            try {
                JOptionPane.showMessageDialog(this, 
                    "Server starting on port 5000.\nWaiting for player connections...",
                    "Server Starting", JOptionPane.INFORMATION_MESSAGE);
                
                TicTacToeServer.main(new String[]{});
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Error starting server: " + e.getMessage(),
                    "Server Error", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }
    
    private void startClient() {
        SwingUtilities.invokeLater(TicTacToeClient::new);
    }
    
    public static void main(String[] args) {
        try {
            // Set system look and feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Custom decorations for buttons and components
        UIManager.put("Button.arc", 15);
        UIManager.put("Component.arc", 10);
        UIManager.put("ProgressBar.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        
        SwingUtilities.invokeLater(TicTacToeMain::new);
    }
}

