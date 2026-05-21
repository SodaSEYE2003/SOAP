import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;
import org.apache.axis.encoding.XMLType;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ChatUserClient - Client SOAP du ChatRoom
 *
 * Architecture SOAP (différence avec RMI) :
 *  - RMI   : le serveur appelait directement displayMessage() sur le client (push)
 *  - SOAP  : le client interroge le serveur toutes les secondes (polling/pull)
 *            via un appel SOAP à getMessages()
 *
 * Utilise Apache Axis 1.4 comme client SOAP.
 */
public class ChatUserClient {

    // -----------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------
    private static final String ENDPOINT =
        "http://localhost:8080/axis/services/ChatRoomService";
    private static final String NAMESPACE =
        "http://localhost:8080/axis/services/ChatRoomService";
    private static final int POLLING_INTERVAL_MS = 1000; // 1 seconde

    // -----------------------------------------------------------------
    // État
    // -----------------------------------------------------------------
    private String pseudo = null;

    // -----------------------------------------------------------------
    // Composants Swing
    // -----------------------------------------------------------------
    private JFrame     window    = new JFrame("ChatRoom SOAP");
    private JTextArea  txtOutput = new JTextArea();
    private JTextField txtMessage= new JTextField();
    private JButton    btnSend   = new JButton("Envoyer");
    private JButton    btnQuit   = new JButton("Quitter");
    private JLabel     lblStatus = new JLabel("Déconnecté");
    private JList<String> userList = new JList<>();
    private DefaultListModel<String> userModel = new DefaultListModel<>();

    // Timer pour le polling
    private Timer pollingTimer = null;

    // -----------------------------------------------------------------
    // Constructeur
    // -----------------------------------------------------------------
    public ChatUserClient() {
        createIHM();
        requestPseudo();
        connectToServer();
        startPolling();
    }

    // -----------------------------------------------------------------
    // Interface graphique
    // -----------------------------------------------------------------
    private void createIHM() {
        // Zone de messages (centre)
        txtOutput.setEditable(false);
        txtOutput.setBackground(new Color(245, 245, 245));
        txtOutput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        txtOutput.setLineWrap(true);
        txtOutput.setWrapStyleWord(true);
        JScrollPane scrollOutput = new JScrollPane(txtOutput);

        // Liste des utilisateurs (droite)
        userList.setModel(userModel);
        userList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JScrollPane scrollUsers = new JScrollPane(userList);
        scrollUsers.setPreferredSize(new Dimension(150, 0));
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.add(new JLabel(" Connectés :"), BorderLayout.NORTH);
        usersPanel.add(scrollUsers, BorderLayout.CENTER);

        // Panel central
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                               scrollOutput, usersPanel);
        splitPane.setDividerLocation(500);

        // Zone de saisie (bas)
        btnSend.setBackground(new Color(70, 130, 180));
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnQuit.setBackground(new Color(200, 80, 80));
        btnQuit.setForeground(Color.WHITE);
        btnQuit.setFocusPainted(false);

        JPanel southPanel = new JPanel(new BorderLayout(5, 0));
        southPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        southPanel.add(btnQuit,     BorderLayout.WEST);
        southPanel.add(txtMessage,  BorderLayout.CENTER);
        southPanel.add(btnSend,     BorderLayout.EAST);

        // Barre de statut (haut)
        lblStatus.setFont(new Font("SansSerif", Font.ITALIC, 11));
        lblStatus.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        lblStatus.setForeground(Color.GRAY);

        // Assemblage
        JPanel mainPanel = (JPanel) window.getContentPane();
        mainPanel.add(lblStatus,   BorderLayout.NORTH);
        mainPanel.add(splitPane,   BorderLayout.CENTER);
        mainPanel.add(southPanel,  BorderLayout.SOUTH);

        // Événements
        btnSend.addActionListener(e -> sendMessage());
        btnQuit.addActionListener(e -> quit());
        txtMessage.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                if (e.getKeyChar() == '\n') sendMessage();
            }
        });
        window.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { quit(); }
        });
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.setSize(700, 500);
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        txtMessage.requestFocus();
    }

    // -----------------------------------------------------------------
    // Demander un pseudo
    // -----------------------------------------------------------------
    private void requestPseudo() {
        pseudo = JOptionPane.showInputDialog(window,
                "Entrez votre pseudo :", "ChatRoom SOAP",
                JOptionPane.OK_OPTION);
        if (pseudo == null || pseudo.trim().isEmpty()) {
            System.exit(0);
        }
        pseudo = pseudo.trim();
        window.setTitle("ChatRoom SOAP — " + pseudo);
    }

    // -----------------------------------------------------------------
    // Connexion au service SOAP
    // -----------------------------------------------------------------
    private void connectToServer() {
        try {
            String result = (String) callService("subscribe",
                    new Object[]{pseudo},
                    new QName[]{new QName("pseudo")},
                    new Class[]{String.class},
                    String.class);

            if ("OK".equals(result)) {
                lblStatus.setText("Connecté en tant que : " + pseudo
                        + "  |  Serveur : " + ENDPOINT);
                lblStatus.setForeground(new Color(0, 128, 0));
                appendMessage("=== Connecté au ChatRoom SOAP ===");
                appendMessage("=== Vos messages apparaîtront ici ===");
            } else if ("PSEUDO_TAKEN".equals(result)) {
                JOptionPane.showMessageDialog(window,
                        "Le pseudo '" + pseudo + "' est déjà utilisé.\nChoisissez-en un autre.",
                        "Pseudo indisponible", JOptionPane.WARNING_MESSAGE);
                requestPseudo();
                connectToServer();
            } else {
                showError("Erreur lors de la connexion : " + result);
            }
        } catch (Exception e) {
            showError("Impossible de se connecter au serveur SOAP :\n" + e.getMessage()
                    + "\n\nVérifiez que Tomcat + Axis sont démarrés.");
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------
    // Polling : récupérer les nouveaux messages toutes les secondes
    // -----------------------------------------------------------------
    private void startPolling() {
        pollingTimer = new Timer(true); // daemon thread
        pollingTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                try {
                    // Récupérer les nouveaux messages
                    Object[] newMessages = (Object[]) callService("getMessages",
                            new Object[]{pseudo},
                            new QName[]{new QName("pseudo")},
                            new Class[]{String.class},
                            Object[].class);

                    if (newMessages != null && newMessages.length > 0) {
                        for (Object msg : newMessages) {
                            appendMessage((String) msg);
                        }
                    }

                    // Mettre à jour la liste des utilisateurs
                    Object[] connectedUsers = (Object[]) callService("getUsers",
                            new Object[]{},
                            new QName[]{},
                            new Class[]{},
                            Object[].class);

                    if (connectedUsers != null) {
                        SwingUtilities.invokeLater(() -> {
                            userModel.clear();
                            for (Object u : connectedUsers) {
                                userModel.addElement((String) u);
                            }
                        });
                    }
                } catch (Exception e) {
                    // Erreur réseau temporaire, on réessaie au prochain tick
                    System.err.println("[Polling] Erreur : " + e.getMessage());
                }
            }
        }, 500, POLLING_INTERVAL_MS);
    }

    // -----------------------------------------------------------------
    // Envoyer un message
    // -----------------------------------------------------------------
    private void sendMessage() {
        String text = txtMessage.getText().trim();
        if (text.isEmpty()) return;
        try {
            callService("postMessage",
                    new Object[]{pseudo, text},
                    new QName[]{new QName("pseudo"), new QName("message")},
                    new Class[]{String.class, String.class},
                    String.class);
            txtMessage.setText("");
            txtMessage.requestFocus();
        } catch (Exception e) {
            showError("Erreur lors de l'envoi : " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Quitter proprement
    // -----------------------------------------------------------------
    private void quit() {
        try {
            if (pollingTimer != null) pollingTimer.cancel();
            callService("unsubscribe",
                    new Object[]{pseudo},
                    new QName[]{new QName("pseudo")},
                    new Class[]{String.class},
                    null);
        } catch (Exception e) {
            // Serveur peut-être arrêté, on ignore
        }
        System.exit(0);
    }

    // -----------------------------------------------------------------
    // Appel générique à un service SOAP via Axis
    // -----------------------------------------------------------------
    private Object callService(String methodName, Object[] params,
                                QName[] paramNames, Class[] paramTypes,
                                Class returnType) throws Exception {
        Service service = new Service();
        Call call = (Call) service.createCall();

        call.setTargetEndpointAddress(new java.net.URL(ENDPOINT));
        call.setOperationName(new QName(NAMESPACE, methodName));

        // Déclarer les paramètres
        for (int i = 0; i < paramNames.length; i++) {
            call.addParameter(paramNames[i].getLocalPart(),
                    XMLType.XSD_STRING, ParameterMode.IN);
        }

        // Déclarer le type de retour
        if (returnType == String.class) {
            call.setReturnType(XMLType.XSD_STRING);
        } else if (returnType == Object[].class) {
            call.setReturnType(XMLType.SOAP_ARRAY);
        }

        return call.invoke(params);
    }

    // -----------------------------------------------------------------
    // Utilitaires
    // -----------------------------------------------------------------
    private void appendMessage(final String msg) {
        SwingUtilities.invokeLater(() -> {
            txtOutput.append(msg + "\n");
            txtOutput.setCaretPosition(txtOutput.getDocument().getLength());
        });
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(window, msg, "Erreur", JOptionPane.ERROR_MESSAGE);
    }

    // -----------------------------------------------------------------
    // Point d'entrée
    // -----------------------------------------------------------------
    public static void main(String[] args) {
        // Appliquer le look & feel système
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new ChatUserClient());
    }
}
