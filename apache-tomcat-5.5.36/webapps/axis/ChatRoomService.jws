import java.util.*;

/**
 * ChatRoomService - Web Service SOAP pour le ChatRoom
 * Déployé sur Apache Axis 1.4 / Tomcat
 *
 * Différence avec RMI :
 *  - RMI : le serveur appelait directement les clients (push)
 *  - SOAP : les clients interrogent le serveur périodiquement (polling/pull)
 *
 * Opérations exposées :
 *  - subscribe(pseudo)     : rejoindre le salon
 *  - unsubscribe(pseudo)   : quitter le salon
 *  - postMessage(pseudo, message) : envoyer un message
 *  - getMessages(pseudo)   : récupérer les nouveaux messages depuis la dernière lecture
 *  - getUsers()            : liste des utilisateurs connectés
 */
public class ChatRoomService {

    // -----------------------------------------------------------------
    // État partagé (statique = partagé entre toutes les requêtes Axis)
    // -----------------------------------------------------------------

    /** Liste de tous les messages du salon (horodatés) */
    private static final List<String> messages = new ArrayList<String>();

    /** Pseudos des utilisateurs connectés */
    private static final Set<String> users = new LinkedHashSet<String>();

    /**
     * Index du dernier message lu par chaque utilisateur.
     * Permet de ne renvoyer que les nouveaux messages (polling incrémental).
     */
    private static final Map<String, Integer> lastRead = new HashMap<String, Integer>();

    // -----------------------------------------------------------------
    // Opérations du Web Service
    // -----------------------------------------------------------------

    /**
     * Inscrire un utilisateur dans le salon.
     * @param pseudo le pseudo choisi
     * @return "OK" si inscription réussie, "PSEUDO_TAKEN" si déjà pris
     */
    public synchronized String subscribe(String pseudo) {
        if (pseudo == null || pseudo.trim().isEmpty()) {
            return "ERROR: pseudo vide";
        }
        pseudo = pseudo.trim();
        if (users.contains(pseudo)) {
            return "PSEUDO_TAKEN";
        }
        users.add(pseudo);
        lastRead.put(pseudo, messages.size()); // commence à lire APRÈS les anciens messages
        addMessage("*** " + pseudo + " a rejoint le salon (" + users.size() + " connecté(s)) ***");
        return "OK";
    }

    /**
     * Désinscrire un utilisateur du salon.
     * @param pseudo le pseudo à retirer
     */
    public synchronized void unsubscribe(String pseudo) {
        if (users.remove(pseudo)) {
            lastRead.remove(pseudo);
            addMessage("*** " + pseudo + " a quitté le salon (" + users.size() + " connecté(s)) ***");
        }
    }

    /**
     * Poster un message dans le salon (sera diffusé à tous via polling).
     * @param pseudo  l'expéditeur
     * @param message le contenu du message
     * @return "OK" ou message d'erreur
     */
    public synchronized String postMessage(String pseudo, String message) {
        if (!users.contains(pseudo)) {
            return "ERROR: utilisateur non connecté";
        }
        if (message == null || message.trim().isEmpty()) {
            return "ERROR: message vide";
        }
        addMessage("[" + pseudo + "] " + message.trim());
        return "OK";
    }

    /**
     * Récupérer les nouveaux messages depuis la dernière lecture (polling).
     * @param pseudo l'utilisateur qui interroge
     * @return tableau de String avec les nouveaux messages (vide si aucun)
     */
    public synchronized String[] getMessages(String pseudo) {
        if (!users.contains(pseudo)) {
            return new String[0];
        }
        int from = lastRead.containsKey(pseudo) ? lastRead.get(pseudo) : 0;
        int to   = messages.size();
        if (from >= to) {
            return new String[0];
        }
        List<String> newMsgs = messages.subList(from, to);
        lastRead.put(pseudo, to);
        return newMsgs.toArray(new String[0]);
    }

    /**
     * Obtenir la liste des utilisateurs connectés.
     * @return tableau de pseudos
     */
    public synchronized String[] getUsers() {
        return users.toArray(new String[0]);
    }

    // -----------------------------------------------------------------
    // Méthode interne
    // -----------------------------------------------------------------
    private void addMessage(String msg) {
        // Horodatage simple
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
        String timestamped = "[" + sdf.format(new java.util.Date()) + "] " + msg;
        messages.add(timestamped);
        System.out.println("[ChatRoomService] " + timestamped);
    }
}
