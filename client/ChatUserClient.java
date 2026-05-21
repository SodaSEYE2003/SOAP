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

public class ChatUserClient {

    private static final String ENDPOINT  =
        "http://localhost:8080/axis/services/ChatRoomService";
    private static final String NAMESPACE =
        "http://localhost:8080/axis/services/ChatRoomService";
    private static final int POLLING_INTERVAL_MS = 1000;

    private String title  = "Logiciel de discussion en ligne";
    private String pseudo = null;

    private JFrame    window     = new JFrame(this.title);
    private JTextArea txtOutput  = new JTextArea();
    private JTextField txtMessage = new JTextField();
    private JButton   btnSend    = new JButton("Envoyer");
    private JButton   btnQuit    = new JButton("Quitter");
    private JList<String> userList  = new JList<>();
    private DefaultListModel<String> userModel = new DefaultListModel<>();

    private Timer pollingTimer = null;

    public ChatUserClient() {
        this.createIHM();
        this.requestPseudo();
        this.connectToServer();
        this.startPolling();
    }

    public void createIHM() {
        JPanel panel = (JPanel) this.window.getContentPane();

        // Zone de messages (centre)
        JScrollPane sclPane = new JScrollPane(txtOutput);
        panel.add(sclPane, BorderLayout.CENTER);

        // Liste des utilisateurs (droite)
        userList.setModel(userModel);
        JScrollPane sclUsers = new JScrollPane(userList);
        sclUsers.setPreferredSize(new Dimension(150, 0));
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.add(new JLabel("Connectes :"), BorderLayout.NORTH);
        usersPanel.add(sclUsers, BorderLayout.CENTER);
        panel.add(usersPanel, BorderLayout.EAST);

        // Zone de saisie (bas)
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(this.btnQuit,    BorderLayout.WEST);
        southPanel.add(this.txtMessage, BorderLayout.CENTER);
        southPanel.add(this.btnSend,    BorderLayout.EAST);
        panel.add(southPanel, BorderLayout.SOUTH);

        // Evenements
        window.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                window_windowClosing(e);
            }
        });
        btnSend.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                btnSend_actionPerformed(e);
            }
        });
        btnQuit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                quit();
            }
        });
        txtMessage.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent event) {
                if (event.getKeyChar() == '\n')
                    btnSend_actionPerformed(null);
            }
        });

        // Attributs
        this.txtOutput.setBackground(new Color(220, 220, 220));
        this.txtOutput.setEditable(false);
        this.window.setSize(600, 400);
        this.window.setVisible(true);
        this.txtMessage.requestFocus();
    }

    public void requestPseudo() {
        this.pseudo = JOptionPane.showInputDialog(
                this.window, "Entrez votre pseudo : ",
                this.title, JOptionPane.OK_OPTION);
        if (this.pseudo == null || this.pseudo.trim().isEmpty())
            System.exit(0);
        this.pseudo = this.pseudo.trim();
        this.window.setTitle(this.title + " - " + this.pseudo);
    }

    public void connectToServer() {
        try {
            String result = (String) callService("subscribe",
                    new Object[]{pseudo},
                    new QName[]{new QName("pseudo")},
                    new Class[]{String.class},
                    String.class);

            if ("OK".equals(result)) {
                txtOutput.append("=== Connecte au ChatRoom SOAP ===\n");
                txtOutput.append("=== Bienvenue " + pseudo + " ! ===\n");
            } else if ("PSEUDO_TAKEN".equals(result)) {
                JOptionPane.showMessageDialog(window,
                        "Le pseudo '" + pseudo + "' est deja utilise.\nChoisissez-en un autre.",
                        "Pseudo indisponible", JOptionPane.WARNING_MESSAGE);
                requestPseudo();
                connectToServer();
            } else {
                showError("Erreur de connexion : " + result);
            }
        } catch (Exception e) {
            showError("Impossible de se connecter au serveur SOAP :\n"
                    + e.getMessage()
                    + "\n\nVerifiez que Tomcat + Axis sont demarres.");
            System.exit(1);
        }
    }

    public void startPolling() {
        pollingTimer = new Timer(true);
        pollingTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                try {
                    Object[] msgs = (Object[]) callService("getMessages",
                            new Object[]{pseudo},
                            new QName[]{new QName("pseudo")},
                            new Class[]{String.class},
                            Object[].class);
                    if (msgs != null && msgs.length > 0)
                        for (Object m : msgs) appendMessage((String) m);

                    Object[] users = (Object[]) callService("getUsers",
                            new Object[]{},
                            new QName[]{},
                            new Class[]{},
                            Object[].class);
                    if (users != null) SwingUtilities.invokeLater(() -> {
                        userModel.clear();
                        for (Object u : users) userModel.addElement((String) u);
                    });
                } catch (Exception e) {
                    System.err.println("[Polling] " + e.getMessage());
                }
            }
        }, 500, POLLING_INTERVAL_MS);
    }

    public void window_windowClosing(WindowEvent e) {
        quit();
    }

    public void btnSend_actionPerformed(ActionEvent e) {
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
        } catch (Exception ex) {
            showError("Erreur d'envoi : " + ex.getMessage());
        }
    }

    public void quit() {
        try {
            if (pollingTimer != null) pollingTimer.cancel();
            callService("unsubscribe",
                    new Object[]{pseudo},
                    new QName[]{new QName("pseudo")},
                    new Class[]{String.class},
                    null);
        } catch (Exception ignored) {}
        System.exit(0);
    }

    private Object callService(String methodName, Object[] params,
            QName[] paramNames, Class[] paramTypes,
            Class returnType) throws Exception {
        Service service = new Service();
        Call call = (Call) service.createCall();
        call.setTargetEndpointAddress(new java.net.URL(ENDPOINT));
        call.setOperationName(new QName(NAMESPACE, methodName));
        for (int i = 0; i < paramNames.length; i++)
            call.addParameter(paramNames[i].getLocalPart(),
                    XMLType.XSD_STRING, ParameterMode.IN);
        if (returnType == String.class)
            call.setReturnType(XMLType.XSD_STRING);
        else if (returnType == Object[].class)
            call.setReturnType(XMLType.SOAP_ARRAY);
        return call.invoke(params);
    }

    private void appendMessage(final String msg) {
        SwingUtilities.invokeLater(() -> {
            txtOutput.append(msg + "\n");
            txtOutput.setCaretPosition(txtOutput.getDocument().getLength());
        });
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(window, msg, "Erreur",
                JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatUserClient());
    }
}