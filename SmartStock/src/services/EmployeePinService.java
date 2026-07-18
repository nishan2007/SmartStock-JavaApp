package services;

import managers.SessionManager;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public final class EmployeePinService {
    private EmployeePinService() {
    }

    public static boolean offerSetupIfMissing(Component parent, Connection conn,
                                              LocalAuthCacheService.CachedUser user) {
        if (!LocalAuthCacheService.shouldUseLocalAuthCache()) {
            return true;
        }
        try {
            if (LocalAuthCacheService.hasEmployeePin(conn, user.userId())) {
                return true;
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Employee PIN status could not be checked: " + ex.getMessage(),
                    "Employee PIN", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return promptAndSave(parent, conn, user, "Create Employee PIN",
                "Create a 4–8 digit PIN for badge access.");
    }

    public static void changeCurrentEmployeePin(Component parent) {
        if (!LocalAuthCacheService.shouldUseLocalAuthCache()) {
            JOptionPane.showMessageDialog(parent,
                    "Employee badge PINs are available on SmartStock server/client deployments.",
                    "Employee PIN", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Integer userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            JOptionPane.showMessageDialog(parent, "No employee is signed in.", "Employee PIN",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        promptAndSaveRemote(parent,"Change Employee PIN","Choose a new 4–8 digit PIN for badge access.");
    }

    private static boolean promptAndSaveRemote(Component parent,String title,String message){
        JPasswordField pinField=new JPasswordField(),confirmField=new JPasswordField();JPanel panel=new JPanel(new GridLayout(0,1,4,4));
        panel.add(new JLabel(message));panel.add(new JLabel("PIN"));panel.add(pinField);panel.add(new JLabel("Confirm PIN"));panel.add(confirmField);
        if(JOptionPane.showConfirmDialog(parent,panel,title,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return false;
        char[]pin=pinField.getPassword(),confirm=confirmField.getPassword();try{
            if(!validPin(pin)||!Arrays.equals(pin,confirm)){JOptionPane.showMessageDialog(parent,"Use exactly 4–8 digits and confirm the PIN exactly.",title,JOptionPane.WARNING_MESSAGE);return false;}
            LanApiClient.changeEmployeePin(pin);JOptionPane.showMessageDialog(parent,"Employee PIN saved.",title,JOptionPane.INFORMATION_MESSAGE);return true;
        }catch(Exception ex){JOptionPane.showMessageDialog(parent,"Employee PIN could not be saved: "+ex.getMessage(),title,JOptionPane.ERROR_MESSAGE);return false;}
        finally{Arrays.fill(pin,'\0');Arrays.fill(confirm,'\0');}
    }

    private static boolean promptAndSave(Component parent, Connection conn,
                                         LocalAuthCacheService.CachedUser user,
                                         String title, String message) {
        JPasswordField pinField = new JPasswordField();
        JPasswordField confirmField = new JPasswordField();
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel(message));
        panel.add(new JLabel("PIN"));
        panel.add(pinField);
        panel.add(new JLabel("Confirm PIN"));
        panel.add(confirmField);
        int choice = JOptionPane.showConfirmDialog(parent, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }

        char[] pin = pinField.getPassword();
        char[] confirm = confirmField.getPassword();
        try {
            if (!validPin(pin) || !Arrays.equals(pin, confirm)) {
                JOptionPane.showMessageDialog(parent,
                        "Use exactly 4–8 digits and confirm the PIN exactly.",
                        title, JOptionPane.WARNING_MESSAGE);
                return false;
            }
            LocalAuthCacheService.saveEmployeePin(conn, user, pin);
            JOptionPane.showMessageDialog(parent, "Employee PIN saved.", title,
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Employee PIN could not be saved: " + ex.getMessage(),
                    title, JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            Arrays.fill(pin, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    public static boolean validPin(char[] pin) {
        if (pin == null || pin.length < 4 || pin.length > 8) {
            return false;
        }
        for (char value : pin) {
            if (!Character.isDigit(value)) {
                return false;
            }
        }
        return true;
    }

    private static LocalAuthCacheService.CachedUser loadCurrentUser(Connection conn, int userId) throws SQLException {
        String sql = """
                SELECT u.user_id, u.username, u.full_name, u.email, u.badge_id,
                       COALESCE(r.role_name, 'USER') AS role_name,
                       l.location_id, l.name AS location_name,
                       COALESCE(l.timezone, '') AS location_timezone
                FROM users u
                LEFT JOIN roles r ON r.role_id = u.role_id
                JOIN user_locations ul ON ul.user_id = u.user_id
                JOIN locations l ON l.location_id = ul.location_id
                WHERE u.user_id = ?
                ORDER BY CASE WHEN l.location_id = ? THEN 0 ELSE 1 END, l.name
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (SessionManager.getCurrentLocationId() == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, SessionManager.getCurrentLocationId());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new LocalAuthCacheService.CachedUser(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("badge_id"),
                        rs.getString("role_name"),
                        rs.getInt("location_id"),
                        rs.getString("location_name"),
                        rs.getString("location_timezone")
                );
            }
        }
    }
}
