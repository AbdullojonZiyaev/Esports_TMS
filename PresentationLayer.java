// PresentationLayer
// Esports Tournament Management System
// ISTE 330 - Group 1
//
// START HERE - this class has the main method.
// Compile only this file:  javac PresentationLayer.java   (DataLayer.java is compiled automatically)
// Run:                     java PresentationLayer          (the MySQL driver jar in this folder is found automatically)
//
// Everything the user sees is in this class (Swing windows, pages, forms, tables, charts).
// All database work is done by DataLayer. This class never contains SQL.
//
// The design follows the group's design.md: one job per page, labels on every icon,
// plain words, one main button per page, undo after changes, a dark color theme,
// big click targets (44 px), visible keyboard focus, friendly empty / loading / error states.
//
// Test accounts: director1 / Director123   coach1 / Coach123   player1 / Player123

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.*;

public class PresentationLayer {

    // default MySQL login (same as the course examples)
    static final String MYSQL_USER = "root";
    static final String MYSQL_PASSWORD = "student";

    static final DataLayer dl = new DataLayer();

    // the logged-in user
    static int userId;
    static int profileId;          // director_id, coach_id or player_id
    static String username;
    static String role;
    static String fullName;

    // windows and the parts of the main window that change
    static JFrame loginFrame;
    static JFrame mainFrame;
    static JPanel pageHolder;
    static JPanel toastHolder;
    static JPanel navPanel;
    static JTextField topSearch;
    static String currentNav = "home";
    static boolean compact;        // narrow window: menu shows icons only, cards in 2 columns
    static javax.swing.Timer toastTimer;

    // saved settings (tour seen)
    static Preferences prefs;

    // =====================================================================
    // THEME: colors, fonts, spacing (design.md sections 3, 4 and 13)
    // =====================================================================

    static Color BG, SURFACE, TEXT, MUTED, BORDER, PRIMARY, ON_PRIMARY, PRIMARY_SOFT,
                 SUCCESS, WARNING, DANGER, ON_DANGER, INFO, ZEBRA, HOVER, CHART;
    static String family = Font.SANS_SERIF;

    // the program always uses dark colors
    static void setTheme() {
        BG = new Color(0x0F1115);      SURFACE = new Color(0x181B21);  TEXT = new Color(0xE8EAED);
        MUTED = new Color(0xA3AAB8);   BORDER = new Color(0x2C313A);   PRIMARY = new Color(0x60A5FA);
        ON_PRIMARY = new Color(0x0F1115); PRIMARY_SOFT = new Color(0x1E2A3D);
        SUCCESS = new Color(0x4ADE80); WARNING = new Color(0xFBBF24);  DANGER = new Color(0xF87171);
        ON_DANGER = new Color(0x0F1115); INFO = new Color(0x38BDF8);
        ZEBRA = new Color(0x1C1F26);   HOVER = new Color(0x262B33);    CHART = new Color(0x56B4E9);
        applyUIDefaults();
    }

    // picks the most readable font that is installed
    static void pickFont() {
        Set<String> installed = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[] {"Atkinson Hyperlegible", "Inter", "Segoe UI", "Noto Sans", "DejaVu Sans"}) {
            if (installed.contains(f)) {
                family = f;
                return;
            }
        }
    }

    // sizes from design.md: title 28, section 20, KPI 32, body 16, label 14 (nothing smaller)
    static Font font(int size, boolean bold) {
        return new Font(family, bold ? Font.BOLD : Font.PLAIN, size);
    }

    static Font body() { return font(16, false); }
    static Font label() { return font(14, false); }

    // colors and fonts for the standard Swing parts
    static void applyUIDefaults() {
        FontUIResource f = new FontUIResource(body());
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (key.toString().endsWith(".font")) {
                UIManager.put(key, f);
            }
        }
        UIManager.put("ToolTip.font", new FontUIResource(label()));
        String[] backgrounds = {"Panel.background", "OptionPane.background", "CheckBox.background",
                "RadioButton.background", "ScrollPane.background", "Viewport.background", "SplitPane.background"};
        for (String k : backgrounds) {
            UIManager.put(k, BG);
        }
        String[] foregrounds = {"Label.foreground", "CheckBox.foreground", "RadioButton.foreground",
                "TextField.foreground", "PasswordField.foreground", "ComboBox.foreground", "List.foreground",
                "Table.foreground", "OptionPane.messageForeground", "TextArea.foreground"};
        for (String k : foregrounds) {
            UIManager.put(k, TEXT);
        }
        String[] surfaces = {"TextField.background", "PasswordField.background", "ComboBox.background",
                "List.background", "Table.background", "TextArea.background", "ComboBox.buttonBackground"};
        for (String k : surfaces) {
            UIManager.put(k, SURFACE);
        }
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("PasswordField.caretForeground", TEXT);
        UIManager.put("ComboBox.selectionBackground", PRIMARY_SOFT);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("List.selectionBackground", PRIMARY_SOFT);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("TextField.selectionBackground", PRIMARY_SOFT);
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("ToolTip.background", new Color(0xE8EAED));
        UIManager.put("ToolTip.foreground", new Color(0x0F1115));
        UIManager.put("ToolTip.border", new EmptyBorder(6, 10, 6, 10));
        UIManager.put("ScrollBar.background", BG);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.track", BG);
        UIManager.put("ComboBox.disabledForeground", MUTED);
        UIManager.put("Button.focus", PRIMARY);
        ToolTipManager.sharedInstance().setInitialDelay(400);
        ToolTipManager.sharedInstance().setDismissDelay(15000);
    }

    // =====================================================================
    // START
    // =====================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception e) {
                // keep the default look
            }
            try {
                prefs = Preferences.userRoot().node("esports_tms_group1");
            } catch (Exception e) {
                prefs = null;                        // settings are only a convenience
            }
            pickFont();
            setTheme();
            if (connectToDatabase()) {
                showLoginWindow();
            } else {
                System.exit(0);
            }
        });
    }

    static boolean prefBool(String key, boolean def) {
        try {
            return prefs == null ? def : prefs.getBoolean(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    static void prefPut(String key, boolean value) {
        try {
            if (prefs != null) {
                prefs.putBoolean(key, value);
            }
        } catch (Exception e) {
            // not saved - nothing else changes
        }
    }

    // try root / student first; if that fails, ask for the MySQL login
    static boolean connectToDatabase() {
        if (dl.connect(MYSQL_USER, MYSQL_PASSWORD)) {
            return true;
        }
        Form f = new Form(null, "Connect to MySQL",
                "We could not log in to MySQL as root / student. Type the MySQL login you used for esports_tms.sql.");
        JTextField user = f.text("MySQL username", MYSQL_USER, null);
        JPasswordField pw = f.password("MySQL password (optional)", "Leave it empty if your MySQL has no password.");
        return f.open("Connect", false, () -> {
            if (!dl.connect(user.getText().trim(), new String(pw.getPassword()))) {
                throw new Problem(null, "That did not work. Check that MySQL is running and that the password is right.");
            }
        });
    }

    static void exitProgram() {
        dl.close();
        System.exit(0);
    }

    // =====================================================================
    // LOGIN WINDOW
    // =====================================================================

    static int failedLogins = 0;

    static void showLoginWindow() {
        if (loginFrame != null) {
            loginFrame.dispose();
        }
        loginFrame = new JFrame("Esports tournaments - Log in");
        loginFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        loginFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                exitProgram();
            }
        });

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(32, 32, 32, 32));

        Card card = new Card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(32, 32, 32, 32));

        JLabel logo = new JLabel("Esports tournaments", new Ico("trophy", 24, PRIMARY), SwingConstants.LEFT);
        logo.setFont(font(20, true));
        logo.setForeground(TEXT);
        logo.setIconTextGap(10);
        JLabel title = text("Welcome back", 28, true, TEXT);
        JLabel intro = wrapText("Log in to see your teams, matches and tournaments.", 300, MUTED);

        JTextField userField = field("");
        JPasswordField passField = new JPasswordField(18);
        styleField(passField);
        JCheckBox showPw = check("Show password");
        char echo = passField.getEchoChar();
        showPw.addActionListener(e -> passField.setEchoChar(showPw.isSelected() ? (char) 0 : echo));

        JLabel error = errorLabel();
        UiButton login = new UiButton("Log in", "user", "primary", null);
        UiButton register = new UiButton("Create an account", "plus", "secondary", null);
        // one account per line, so no line is cut off
        JLabel hint = new JLabel("<html>Test accounts:<br>"
                + "&nbsp;&nbsp;Director:&nbsp;&nbsp;director1 / Director123<br>"
                + "&nbsp;&nbsp;Coach:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;coach1 / Coach123<br>"
                + "&nbsp;&nbsp;Player:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;player1 / Player123</html>");
        hint.setFont(label());
        hint.setForeground(MUTED);

        addLeft(card, logo, 24);
        addLeft(card, title, 8);
        addLeft(card, intro, 24);
        addLeft(card, labelFor("Username", userField), 6);
        addLeft(card, userField, 16);
        addLeft(card, labelFor("Password", passField), 6);
        addLeft(card, passField, 8);
        addLeft(card, showPw, 12);
        addLeft(card, error, 12);
        addLeft(card, login, 12);
        addLeft(card, register, 24);
        addLeft(card, hint, 0);
        for (JComponent c : new JComponent[] {userField, passField, login, register}) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        }

        Runnable doLogin = () -> {
            error.setVisible(false);
            String name = userField.getText().trim();
            String pw = new String(passField.getPassword());
            if (name.isEmpty() || pw.isEmpty()) {
                showError(error, name.isEmpty() ? "Please type your username." : "Please type your password.");
                (name.isEmpty() ? userField : passField).requestFocus();
                return;
            }
            try {
                String[] user = dl.login(name, pw);
                if (user == null) {
                    failedLogins++;
                    passField.setText("");
                    if (failedLogins >= 3) {
                        lockLogin(login, error);
                    } else {
                        showError(error, "That username or password is not right. Please try again ("
                                + failedLogins + " of 3 tries).");
                    }
                    return;
                }
                failedLogins = 0;
                userId = Integer.parseInt(user[0]);
                role = user[1];
                profileId = Integer.parseInt(user[2]);
                fullName = user[3];
                username = name;
                passField.setText("");
                loginFrame.setVisible(false);
                showMainWindow("home");
            } catch (SQLException ex) {
                showError(error, friendly(ex));
                error.setToolTipText(ex.getMessage());
            }
        };
        login.addActionListener(e -> doLogin.run());
        passField.addActionListener(e -> doLogin.run());
        userField.addActionListener(e -> passField.requestFocus());
        register.addActionListener(e -> {
            String newUser = registerDialog(loginFrame, false);
            if (newUser != null) {
                userField.setText(newUser);
                passField.requestFocus();
                showInfo(error, "Your account is ready. Type your password to log in.");
            }
        });

        card.setPreferredSize(new Dimension(420, card.getPreferredSize().height));
        root.add(card);
        loginFrame.setContentPane(scrollBody(root));          // scrolls on a small screen, so nothing is cut off
        loginFrame.getRootPane().setDefaultButton(login);
        loginFrame.pack();
        int fit = Math.min(loginFrame.getHeight(), screenHeight());
        loginFrame.setSize(Math.max(480, loginFrame.getWidth()), fit);
        loginFrame.setMinimumSize(new Dimension(480, Math.min(fit, 400)));
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);
        userField.requestFocusInWindow();
        if (!dl.schemaIsCurrent()) {
            showError(error, "Your database is out of date, so some pages will not work. Run esports_tms.sql "
                    + "again in MySQL Workbench, then start the program again.");
            loginFrame.pack();
            loginFrame.setSize(Math.max(480, loginFrame.getWidth()), Math.min(loginFrame.getHeight(), screenHeight()));
        }
    }

    // after 3 wrong tries: wait 30 seconds (the button says why it is turned off)
    // usable screen height (without the task bar)
    static int screenHeight() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
        } catch (Exception e) {
            return 760;
        }
    }

    static void lockLogin(UiButton login, JLabel error) {
        login.setEnabled(false);
        login.setToolTipText("Too many tries. You can try again in 30 seconds.");
        showError(error, "Too many tries. Please wait 30 seconds, then try again.");
        javax.swing.Timer t = new javax.swing.Timer(30000, e -> {
            failedLogins = 0;
            login.setEnabled(true);
            login.setToolTipText(null);
            showInfo(error, "You can try again now.");
        });
        t.setRepeats(false);
        t.start();
    }

    // =====================================================================
    // BUILDING BLOCKS: icons, buttons, cards, text, fields
    // =====================================================================

    // simple outline icons drawn in code (one style, 20-24 px), always shown next to a text label
    static class Ico implements Icon {
        final String name;
        final int size;
        final Color color;          // null = the color of the component (button text color)

        Ico(String name, int size, Color color) {
            this.name = name;
            this.size = size;
            this.color = color;
        }

        Ico(String name) {
            this(name, 20, null);
        }

        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }

        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);
            g.scale(size / 20.0, size / 20.0);
            g.setColor(color != null ? color : c.getForeground());
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (name) {
                case "home":
                    g.draw(path(3, 10, 10, 3, 17, 10));
                    g.draw(new RoundRectangle2D.Double(5, 9, 10, 8, 2, 2));
                    break;
                case "trophy":
                    g.draw(new Line2D.Double(6, 3, 14, 3));
                    g.draw(new Arc2D.Double(6, -2, 8, 12, 180, 180, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(2.5, 3, 5, 5, 90, 180, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(12.5, 3, 5, 5, 270, 180, Arc2D.OPEN));
                    g.draw(new Line2D.Double(10, 10, 10, 15));
                    g.draw(new Line2D.Double(6, 17, 14, 17));
                    g.draw(new Line2D.Double(8, 15, 12, 15));
                    break;
                case "users":
                    g.draw(new Ellipse2D.Double(4, 3, 6, 6));
                    g.draw(new Arc2D.Double(1.5, 11, 11, 10, 0, 180, Arc2D.OPEN));
                    g.draw(new Ellipse2D.Double(12, 4, 5, 5));
                    g.draw(new Arc2D.Double(11, 11, 8, 8, 0, 180, Arc2D.OPEN));
                    break;
                case "user":
                    g.draw(new Ellipse2D.Double(6.5, 2.5, 7, 7));
                    g.draw(new Arc2D.Double(3.5, 11.5, 13, 11, 0, 180, Arc2D.OPEN));
                    break;
                case "calendar":
                    g.draw(new RoundRectangle2D.Double(3, 4, 14, 13, 3, 3));
                    g.draw(new Line2D.Double(3, 8, 17, 8));
                    g.draw(new Line2D.Double(7, 2, 7, 5));
                    g.draw(new Line2D.Double(13, 2, 13, 5));
                    break;
                case "search":
                    g.draw(new Ellipse2D.Double(3, 3, 10, 10));
                    g.draw(new Line2D.Double(11.5, 11.5, 17, 17));
                    break;
                case "chart":
                    g.draw(new Line2D.Double(3, 17, 17, 17));
                    g.draw(new Line2D.Double(5.5, 14, 5.5, 10));
                    g.draw(new Line2D.Double(10, 14, 10, 5));
                    g.draw(new Line2D.Double(14.5, 14, 14.5, 8));
                    break;
                case "help":
                    g.draw(new Ellipse2D.Double(2, 2, 16, 16));
                    g.draw(new Arc2D.Double(7, 5, 6, 6, -60, 240, Arc2D.OPEN));
                    g.draw(new Line2D.Double(10, 11, 10, 12));
                    g.fill(new Ellipse2D.Double(9, 14, 2, 2));
                    break;
                case "plus":
                    g.draw(new Line2D.Double(10, 4, 10, 16));
                    g.draw(new Line2D.Double(4, 10, 16, 10));
                    break;
                case "logout":
                    g.draw(path(8, 3, 4, 3, 4, 17, 8, 17));
                    g.draw(new Line2D.Double(8, 10, 17, 10));
                    g.draw(path(14, 7, 17, 10, 14, 13));
                    break;
                case "edit":
                    g.draw(path(4, 16, 4, 13, 13, 4, 16, 7, 7, 16, 4, 16));
                    break;
                case "trash":
                    g.draw(new Line2D.Double(3, 5, 17, 5));
                    g.draw(path(8, 5, 8, 3, 12, 3, 12, 5));
                    g.draw(path(5, 5, 6, 17, 14, 17, 15, 5));
                    break;
                case "check":
                    g.draw(path(4, 10, 8, 14, 16, 5));
                    break;
                case "alert":
                    g.draw(path(10, 3, 18, 17, 2, 17, 10, 3));
                    g.draw(new Line2D.Double(10, 8, 10, 12));
                    g.fill(new Ellipse2D.Double(9, 14, 2, 2));
                    break;
                case "info":
                    g.draw(new Ellipse2D.Double(2, 2, 16, 16));
                    g.draw(new Line2D.Double(10, 9, 10, 14));
                    g.fill(new Ellipse2D.Double(9, 5.5, 2, 2));
                    break;
                case "x":
                    g.draw(new Line2D.Double(5, 5, 15, 15));
                    g.draw(new Line2D.Double(15, 5, 5, 15));
                    break;
                case "back":
                    g.draw(new Line2D.Double(4, 10, 16, 10));
                    g.draw(path(9, 5, 4, 10, 9, 15));
                    break;
                case "open":
                    g.draw(new Line2D.Double(4, 10, 16, 10));
                    g.draw(path(11, 5, 16, 10, 11, 15));
                    break;
                case "undo":
                    g.draw(new Arc2D.Double(4, 5, 13, 11, 90, -220, Arc2D.OPEN));
                    g.draw(path(4, 5, 10, 5));
                    g.draw(path(7, 2, 4, 5, 7, 8));
                    break;
                case "filter":
                    g.draw(path(3, 4, 17, 4, 12, 10, 12, 16, 8, 17, 8, 10, 3, 4));
                    break;
                case "columns":
                    g.draw(new RoundRectangle2D.Double(3, 4, 14, 12, 2, 2));
                    g.draw(new Line2D.Double(8, 4, 8, 16));
                    g.draw(new Line2D.Double(12, 4, 12, 16));
                    break;
                case "up":
                    g.fill(path(6, 12, 10, 7, 14, 12, 6, 12));
                    break;
                case "down":
                    g.fill(path(6, 8, 14, 8, 10, 13, 6, 8));
                    break;
                case "dot":
                    g.fill(new Ellipse2D.Double(6, 6, 8, 8));
                    break;
                case "clock":
                    g.draw(new Ellipse2D.Double(2, 2, 16, 16));
                    g.draw(path(10, 5, 10, 10, 13, 12));
                    break;
                case "inbox":
                    g.draw(path(3, 11, 5.5, 4, 14.5, 4, 17, 11));
                    g.draw(path(3, 11, 3, 16, 17, 16, 17, 11));
                    g.draw(path(3, 11, 7, 11, 8, 13, 12, 13, 13, 11, 17, 11));
                    break;
                case "key":
                    g.draw(new Ellipse2D.Double(2, 7, 7, 7));
                    g.draw(path(9, 10.5, 18, 10.5, 18, 13));
                    g.draw(new Line2D.Double(15, 10.5, 15, 13));
                    break;
                default:
                    g.draw(new Ellipse2D.Double(4, 4, 12, 12));
            }
            g.dispose();
        }

        static Path2D path(double... p) {
            Path2D.Double s = new Path2D.Double();
            s.moveTo(p[0], p[1]);
            for (int i = 2; i < p.length; i += 2) {
                s.lineTo(p[i], p[i + 1]);
            }
            return s;
        }
    }

    // button with 4 looks: primary (filled, one per page), secondary (outlined), danger, text
    // min height 44 px, 3 px focus ring, hover and pressed states, turned-off buttons explain why
    static class UiButton extends JButton {
        String kind;
        boolean selected;          // used by segment buttons (tabs)

        UiButton(String text, String icon, String kind, Runnable action) {
            super(text, icon == null ? null : new Ico(icon));
            this.kind = kind;
            setFont(font(16, true));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setIconTextGap(8);
            setBorder(new EmptyBorder(10, 16, 10, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setForeground(colors()[1]);
            if (action != null) {
                addActionListener(e -> action.run());
            }
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
            getModel().addChangeListener(e -> setForeground(colors()[1]));
        }

        // {background, text, border}
        Color[] colors() {
            boolean on = isEnabled();
            boolean over = getModel().isRollover() && on;
            boolean down = getModel().isPressed() && on;
            switch (kind) {
                case "primary":
                    if (!on) return new Color[] {BORDER, MUTED, BORDER};
                    Color p = down ? PRIMARY.darker() : over ? blend(PRIMARY, TEXT, 0.12) : PRIMARY;
                    return new Color[] {p, ON_PRIMARY, p};
                case "danger":
                    if (!on) return new Color[] {BORDER, MUTED, BORDER};
                    Color d = down ? DANGER.darker() : over ? blend(DANGER, TEXT, 0.12) : DANGER;
                    return new Color[] {d, ON_DANGER, d};
                case "danger-outline":
                    return new Color[] {over || down ? HOVER : SURFACE, on ? DANGER : MUTED, on ? DANGER : BORDER};
                case "text":
                    return new Color[] {over || down ? HOVER : null, on ? PRIMARY : MUTED, null};
                case "nav":
                    return new Color[] {selected ? PRIMARY_SOFT : over ? HOVER : null, TEXT, null};
                case "segment":
                    return new Color[] {selected ? PRIMARY_SOFT : over ? HOVER : SURFACE, TEXT,
                            selected ? PRIMARY : BORDER};
                default:  // secondary
                    return new Color[] {over || down ? HOVER : SURFACE, on ? TEXT : MUTED, on ? MUTED : BORDER};
            }
        }

        void setSelectedLook(boolean s) {
            selected = s;
            setFont(font(16, s || !"nav".equals(kind) && !"segment".equals(kind)));
            if ("nav".equals(kind) || "segment".equals(kind)) {
                setFont(font(16, s));
            }
            getAccessibleContext().setAccessibleDescription(s ? "Current page" : null);
            repaint();
        }

        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 44), Math.max(d.height, 44));
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color[] c = colors();
            int w = getWidth();
            int h = getHeight();
            if (c[0] != null) {
                g.setColor(c[0]);
                g.fillRoundRect(2, 2, w - 4, h - 4, 12, 12);
            }
            if (c[2] != null) {
                g.setColor(c[2]);
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(2, 2, w - 5, h - 5, 12, 12);
            }
            if ("nav".equals(kind) && selected) {             // left bar: current page is not shown by color only
                g.setColor(PRIMARY);
                g.fillRoundRect(2, 8, 4, h - 16, 4, 4);
            }
            if (isFocusOwner()) {                               // visible 3 px focus ring
                g.setColor(new Color(PRIMARY.getRed(), PRIMARY.getGreen(), PRIMARY.getBlue(), 140));
                g.setStroke(new BasicStroke(3f));
                g.drawRoundRect(1, 1, w - 3, h - 3, 14, 14);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    static Color blend(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    // dark rounded panel with a thin border
    static class Card extends JPanel {
        Card() {
            setOpaque(false);
            setBorder(new EmptyBorder(24, 24, 24, 24));
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SURFACE);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            g.setColor(Boolean.TRUE.equals(getClientProperty("hover")) || isFocusOwner() ? PRIMARY : BORDER);
            g.setStroke(new BasicStroke(isFocusOwner() ? 3f : 1f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 24, 24);
            g.dispose();
        }
    }

    static JLabel text(String s, int size, boolean bold, Color color) {
        JLabel l = new JLabel(s);
        l.setFont(font(size, bold));
        l.setForeground(color);
        return l;
    }

    // text that wraps at the given width (line length stays readable)
    static JLabel wrapText(String s, int width, Color color) {
        JLabel l = new JLabel("<html><div style='width:" + width + "px'>" + escape(s) + "</div></html>");
        l.setFont(body());
        l.setForeground(color);
        return l;
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static void addLeft(JPanel p, JComponent c, int gapAfter) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(c);
        if (gapAfter > 0) {
            p.add(Box.createVerticalStrut(gapAfter));
        }
    }

    // a label above a field; screen readers read it for the field
    static JLabel labelFor(String s, JComponent field) {
        JLabel l = text(s, 14, true, TEXT);
        l.setLabelFor(field);
        return l;
    }

    static JTextField field(String value) {
        JTextField f = new JTextField(value, 20);
        styleField(f);
        return f;
    }

    static void styleField(JTextField f) {
        f.setFont(body());
        f.setBackground(SURFACE);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        Border normal = new CompoundBorder(new LineBorder(MUTED, 1, true), new EmptyBorder(9, 10, 9, 10));
        Border focused = new CompoundBorder(new LineBorder(PRIMARY, 3, true), new EmptyBorder(7, 8, 7, 8));
        f.setBorder(normal);
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { f.setBorder(focused); }
            public void focusLost(FocusEvent e) { f.setBorder(normal); }
        });
    }

    static JCheckBox check(String s) {
        JCheckBox c = new JCheckBox(s);
        c.setFont(body());
        c.setOpaque(false);
        c.setForeground(TEXT);
        c.setFocusPainted(true);
        c.setBorder(new EmptyBorder(10, 0, 10, 0));
        return c;
    }

    static JComboBox<String> combo() {
        JComboBox<String> box = new JComboBox<>();
        box.setFont(body());
        box.setBackground(SURFACE);
        box.setForeground(TEXT);
        box.setPreferredSize(new Dimension(320, 44));
        box.setMaximumRowCount(12);
        return box;
    }

    static JLabel errorLabel() {
        JLabel l = new JLabel(" ", new Ico("alert", 18, DANGER), SwingConstants.LEFT);
        l.setFont(label());
        l.setForeground(DANGER);
        l.setVisible(false);
        return l;
    }

    static void showError(JLabel l, String message) {
        l.setIcon(new Ico("alert", 18, DANGER));
        l.setForeground(DANGER);
        l.setText("<html><div style='width:320px'>" + escape(message) + "</div></html>");
        l.getAccessibleContext().setAccessibleName(message);
        l.setVisible(true);
    }

    static void showInfo(JLabel l, String message) {
        l.setIcon(new Ico("check", 18, SUCCESS));
        l.setForeground(SUCCESS);
        l.setText("<html><div style='width:320px'>" + escape(message) + "</div></html>");
        l.setVisible(true);
    }

    // plain-words message for a database problem (the technical text goes in a tooltip)
    static String friendly(SQLException e) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (state.startsWith("08") || msg.contains("Communications link")) {
            return "We can't reach the database. Check that MySQL is running, then try again.";
        }
        if (msg.contains("doesn't exist") || msg.contains("does not exist")) {
            return "The database is out of date. Run esports_tms.sql again in MySQL Workbench, "
                    + "then start the program again.";
        }
        if (msg.contains("Duplicate")) {
            return "This already exists. Please use a different name.";
        }
        if (msg.contains("foreign key") || msg.contains("FOREIGN KEY")) {
            return "This is still used somewhere else, so it can't be changed.";
        }
        return "Something went wrong. Please try again.";
    }

    // =====================================================================
    // FORMS: labels above fields, errors next to the field, check when leaving a field
    // =====================================================================

    interface Submit {
        void run() throws SQLException;
    }

    interface Rule {
        String check();            // null = fine, otherwise the message to show
    }

    // thrown while saving to show a message under one field (field null = under the whole form)
    static class Problem extends RuntimeException {
        final JComponent field;

        Problem(JComponent field, String message) {
            super(message);
            this.field = field;
        }
    }

    // radio buttons for 2-5 choices
    static class Radio extends JPanel {
        final String[] values;
        final JRadioButton[] buttons;

        Radio(String[] values, String[] shown, String selected) {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            setOpaque(false);
            this.values = values;
            buttons = new JRadioButton[values.length];
            ButtonGroup group = new ButtonGroup();
            for (int i = 0; i < values.length; i++) {
                JRadioButton b = new JRadioButton(shown[i]);
                b.setFont(body());
                b.setOpaque(false);
                b.setForeground(TEXT);
                b.setBorder(new EmptyBorder(10, 0, 10, 20));
                b.setSelected(values[i].equals(selected));
                group.add(b);
                buttons[i] = b;
                add(b);
            }
            if (selected == null && values.length > 0) {
                buttons[0].setSelected(true);
            }
        }

        String value() {
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].isSelected()) {
                    return values[i];
                }
            }
            return values[0];
        }

        String shownValue() {
            for (JRadioButton b : buttons) {
                if (b.isSelected()) {
                    return b.getText();
                }
            }
            return "";
        }

        void onChange(Runnable r) {
            for (JRadioButton b : buttons) {
                b.addActionListener(e -> r.run());
            }
        }
    }

    // a dialog with fields (one per row: label, field, hint, error) and two buttons: a verb and "Cancel"
    static class Form {
        final JDialog d;
        final JPanel body = new JPanel();
        final JLabel formError = errorLabel();
        final Map<JComponent, JLabel> errors = new HashMap<>();
        final Map<JComponent, JPanel> rows = new HashMap<>();
        final Map<JComponent, JLabel> labels = new HashMap<>();
        final List<Object[]> rules = new ArrayList<>();
        boolean ok;

        Form(Window owner, String title, String intro) {
            d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBackground(SURFACE);
            body.setBorder(new EmptyBorder(24, 24, 8, 24));
            addLeft(body, PresentationLayer.text(title, 20, true, TEXT), 8);
            if (intro != null) {
                addLeft(body, wrapText(intro, 380, MUTED), 16);
            }
        }

        JPanel row(String label, JComponent input, String hint) {
            JPanel r = new JPanel();
            r.setLayout(new BoxLayout(r, BoxLayout.Y_AXIS));
            r.setOpaque(false);
            JLabel l = labelFor(label, input);
            addLeft(r, l, 6);
            input.setAlignmentX(Component.LEFT_ALIGNMENT);
            input.setMaximumSize(new Dimension(Integer.MAX_VALUE, input.getPreferredSize().height));
            r.add(input);
            if (hint != null) {
                r.add(Box.createVerticalStrut(4));
                JLabel h = PresentationLayer.text(hint, 14, false, MUTED);
                addLeft(r, h, 0);
            }
            JLabel err = errorLabel();
            r.add(Box.createVerticalStrut(4));
            addLeft(r, err, 0);
            r.add(Box.createVerticalStrut(12));
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(r);
            errors.put(input, err);
            rows.put(input, r);
            labels.put(input, l);
            return r;
        }

        JTextField text(String label, String value, String hint) {
            JTextField f = field(value == null ? "" : value);
            row(label, f, hint);
            return f;
        }

        JPasswordField password(String label, String hint) {
            JPasswordField f = new JPasswordField(20);
            styleField(f);
            row(label, f, hint);
            return f;
        }

        // rows = {id, label}; the ids are kept in the box
        JComboBox<String> dropdown(String label, List<String[]> options, String selectedId, String hint) {
            JComboBox<String> box = combo();
            setOptions(box, options, selectedId);
            row(label, box, hint);
            return box;
        }

        Radio radios(String label, String[] values, String[] shown, String selected) {
            Radio r = new Radio(values, shown, selected);
            row(label, r, null);
            return r;
        }

        JCheckBox toggle(String s) {
            JCheckBox c = check(s);
            addLeft(body, c, 8);
            return c;
        }

        // checked when the user leaves the field, and again before saving
        void rule(JComponent field, Rule r) {
            rules.add(new Object[] {field, r});
            field.addFocusListener(new FocusAdapter() {
                public void focusLost(FocusEvent e) {
                    if (field.isShowing()) {
                        mark(field, r.check());
                    }
                }
            });
        }

        void mark(JComponent field, String message) {
            JLabel err = errors.get(field);
            if (err == null) {
                return;
            }
            if (message == null) {
                err.setVisible(false);
                field.getAccessibleContext().setAccessibleDescription(null);
            } else {
                showError(err, message);
                field.getAccessibleContext().setAccessibleDescription(message);
            }
            d.pack();
        }

        void show(JComponent field, boolean visible) {
            JPanel r = rows.get(field);
            if (r != null) {
                r.setVisible(visible);
                d.pack();
            }
        }

        void setLabel(JComponent field, String s) {
            JLabel l = labels.get(field);
            if (l != null) {
                l.setText(s);
            }
        }

        boolean open(String verb, boolean danger, Submit submit) {
            UiButton cancel = new UiButton("Cancel", null, "secondary", () -> d.dispose());
            UiButton go = new UiButton(verb, null, danger ? "danger" : "primary", null);
            go.addActionListener(e -> {
                formError.setVisible(false);
                JComponent firstBad = null;
                for (Object[] r : rules) {
                    JComponent f = (JComponent) r[0];
                    if (!f.isShowing()) {
                        continue;
                    }
                    String msg = ((Rule) r[1]).check();
                    mark(f, msg);
                    if (msg != null && firstBad == null) {
                        firstBad = f;
                    }
                }
                if (firstBad != null) {
                    firstBad.requestFocus();
                    return;
                }
                d.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                go.setText(verb + "...");
                try {
                    submit.run();
                    ok = true;
                    d.dispose();
                } catch (Problem p) {
                    if (p.field != null && errors.containsKey(p.field)) {
                        mark(p.field, p.getMessage());
                        p.field.requestFocus();
                    } else {
                        showError(formError, p.getMessage());
                        d.pack();
                    }
                } catch (SQLException ex) {
                    showError(formError, friendly(ex));
                    formError.setToolTipText(ex.getMessage());
                    d.pack();
                } finally {
                    go.setText(verb);
                    d.setCursor(Cursor.getDefaultCursor());
                }
            });
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            buttons.add(cancel);
            buttons.add(go);
            addLeft(body, formError, 12);
            buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(buttons);
            body.add(Box.createVerticalStrut(16));

            JScrollPane scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            d.setContentPane(scroll);
            d.getRootPane().setDefaultButton(go);
            escapeCloses(d);
            d.pack();
            Dimension size = d.getSize();
            int height = Math.min(size.height, Math.min(760, screenHeight()));
            int bar = height < size.height ? scroll.getVerticalScrollBar().getPreferredSize().width : 0;
            d.setSize(Math.max(480, size.width) + bar, height);
            d.setLocationRelativeTo(d.getOwner());
            d.setVisible(true);                                // waits here until the dialog closes
            return ok;
        }
    }

    static void escapeCloses(JDialog d) {
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // rows = {id, label}
    @SuppressWarnings("unchecked")
    static void setOptions(JComboBox<String> box, List<String[]> options, String selectedId) {
        box.removeAllItems();
        box.putClientProperty("options", options);
        int select = 0;
        for (int i = 0; i < options.size(); i++) {
            box.addItem(options.get(i)[1]);
            if (options.get(i)[0].equals(selectedId)) {
                select = i;
            }
        }
        if (!options.isEmpty()) {
            box.setSelectedIndex(select);
        }
    }

    @SuppressWarnings("unchecked")
    static String idOf(JComboBox<String> box) {
        List<String[]> options = (List<String[]>) box.getClientProperty("options");
        int i = box.getSelectedIndex();
        return options == null || i < 0 ? null : options.get(i)[0];
    }

    // ask once before something that can't be undone; the safe answer is the default
    static boolean confirm(String title, String message, String yes, String no, boolean danger) {
        JDialog d = new JDialog(mainFrame != null ? mainFrame : loginFrame, title, Dialog.ModalityType.APPLICATION_MODAL);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SURFACE);
        p.setBorder(new EmptyBorder(24, 24, 20, 24));
        addLeft(p, text(title, 20, true, TEXT), 12);
        addLeft(p, wrapText(message, 380, TEXT), 24);
        boolean[] answer = {false};
        UiButton keep = new UiButton(no, null, "secondary", () -> d.dispose());
        UiButton go = new UiButton(yes, null, danger ? "danger" : "primary", () -> {
            answer[0] = true;
            d.dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(keep);
        buttons.add(go);
        addLeft(p, buttons, 0);
        d.setContentPane(p);
        d.getRootPane().setDefaultButton(keep);
        escapeCloses(d);
        d.pack();
        d.setLocationRelativeTo(d.getOwner());
        SwingUtilities.invokeLater(keep::requestFocusInWindow);
        d.setVisible(true);
        return answer[0];
    }

    // =====================================================================
    // TOASTS: one message at a time at the bottom. Success hides after 5 s, errors stay.
    // =====================================================================

    static void toast(String kind, String message, Runnable undo) {
        if (toastHolder == null) {
            return;
        }
        if (toastTimer != null) {
            toastTimer.stop();
        }
        toastHolder.removeAll();
        Color c = "error".equals(kind) ? DANGER : "success".equals(kind) ? SUCCESS : INFO;
        String icon = "error".equals(kind) ? "alert" : "success".equals(kind) ? "check" : "info";
        Card t = new Card();
        t.setLayout(new BorderLayout(12, 0));
        t.setBorder(new EmptyBorder(8, 16, 8, 8));
        JLabel msg = new JLabel(message, new Ico(icon, 22, c), SwingConstants.LEFT);
        msg.setFont(body());
        msg.setForeground(TEXT);
        msg.setIconTextGap(12);
        msg.getAccessibleContext().setAccessibleName(message);
        t.add(msg, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        if (undo != null) {
            right.add(new UiButton("Undo", "undo", "text", () -> {
                toastHolder.removeAll();
                toastHolder.revalidate();
                undo.run();
            }));
        }
        UiButton close = new UiButton("Close", "x", "text", () -> {
            toastHolder.removeAll();
            toastHolder.revalidate();
            toastHolder.repaint();
        });
        close.setToolTipText("Close this message");
        right.add(close);
        t.add(right, BorderLayout.EAST);
        toastHolder.add(t, BorderLayout.CENTER);
        toastHolder.revalidate();
        toastHolder.repaint();
        if (!"error".equals(kind)) {
            JPanel holder = toastHolder;                     // the window may be closed before the timer runs
            toastTimer = new javax.swing.Timer(undo != null ? 8000 : 5000, e -> {
                holder.removeAll();
                holder.revalidate();
                holder.repaint();
            });
            toastTimer.setRepeats(false);
            toastTimer.start();
        }
    }

    // stored procedures answer "SUCCESS: ..." or "NOT DONE: ..."; this keeps only the plain words
    static String procedureText(String message) {
        String m = message.replaceFirst("^(SUCCESS|NOT DONE): ", "");
        for (String w : new String[] {"SCHEDULED", "COMPLETED", "ONGOING", "UPCOMING", "CANCELLED", "DECLINED",
                "APPROVED", "PENDING"}) {
            m = m.replace(w, w.charAt(0) + w.substring(1).toLowerCase());
        }
        return m;
    }

    static boolean succeeded(String procedureMessage) {
        return procedureMessage.startsWith("SUCCESS");
    }

    // =====================================================================
    // FRIENDLY TEXT: dates, numbers, status words, column explanations
    // =====================================================================

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    static final DateTimeFormatter ISO_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static boolean isDate(String s) {
        return s != null && s.matches("\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}(:\\d{2})?)?");
    }

    // "Today", "Tomorrow", "Thu, Nov 19" (+ year if not this year) and "at 7:00 PM" for times
    static String friendlyDate(String raw) {
        try {
            LocalDate d = LocalDate.parse(raw.substring(0, 10));
            LocalDate today = LocalDate.now();
            String day;
            if (d.equals(today)) {
                day = "Today";
            } else if (d.equals(today.minusDays(1))) {
                day = "Yesterday";
            } else if (d.equals(today.plusDays(1))) {
                day = "Tomorrow";
            } else {
                day = d.format(d.getYear() == today.getYear() ? DAY : DAY_YEAR);
            }
            if (raw.length() >= 16) {
                day += " at " + LocalTime.parse(raw.substring(11, 16)).format(TIME);
            }
            return day;
        } catch (RuntimeException e) {
            return raw;
        }
    }

    // 25000 -> "25K", 1234 -> "1.2K", 1.95 -> "1.95", 6.00 -> "6"
    static String readableNumber(double n) {
        if (Math.abs(n) >= 1000) {
            double k = n / 1000.0;
            return (k == Math.rint(k) ? String.valueOf((long) k) : String.format(Locale.US, "%.1f", k)) + "K";
        }
        if (n == Math.rint(n)) {
            return String.valueOf((long) n);
        }
        return String.format(Locale.US, "%.2f", n).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static boolean isIdColumn(String header) {
        return header.equals("ID") || header.endsWith(" ID");
    }

    // database words in capitals -> sentence case, with a color and an icon (never color alone)
    static final Map<String, String[]> WORDS = new HashMap<>();

    static {
        WORDS.put("COMPLETED", new String[] {"Completed", "success", "check"});
        WORDS.put("ONGOING", new String[] {"Ongoing", "info", "dot"});
        WORDS.put("UPCOMING", new String[] {"Upcoming", "warning", "clock"});
        WORDS.put("SCHEDULED", new String[] {"Scheduled", "warning", "clock"});
        WORDS.put("CANCELLED", new String[] {"Cancelled", "danger", "x"});
        WORDS.put("PENDING", new String[] {"Waiting", "warning", "clock"});
        WORDS.put("APPROVED", new String[] {"Approved", "success", "check"});
        WORDS.put("DECLINED", new String[] {"Declined", "danger", "x"});
        WORDS.put("CAPTAIN", new String[] {"Captain", null, null});
        WORDS.put("STARTER", new String[] {"Starter", null, null});
        WORDS.put("SUBSTITUTE", new String[] {"Substitute", null, null});
        WORDS.put("current", new String[] {"Still on the team", "success", "check"});
        WORDS.put("Yes", new String[] {"Yes", "success", "check"});
        WORDS.put("DIRECTOR", new String[] {"Director", null, null});
        WORDS.put("COACH", new String[] {"Coach", null, null});
        WORDS.put("PLAYER", new String[] {"Player", null, null});
    }

    static Color statusColor(String name) {
        if (name == null) return TEXT;
        switch (name) {
            case "success": return SUCCESS;
            case "warning": return WARNING;
            case "danger": return DANGER;
            default: return INFO;
        }
    }

    // explanations shown when the mouse is on a column heading
    static final Map<String, String> GLOSSARY = new HashMap<>();

    static {
        GLOSSARY.put("Seed", "Seed: the team's starting place in the tournament (1 = first).");
        GLOSSARY.put("Kills per death", "Kills per death: kills divided by deaths. Higher is better.");
        GLOSSARY.put("Points", "Points: 3 for each win.");
        GLOSSARY.put("Score difference", "Score difference: points scored minus points let in.");
        GLOSSARY.put("Let in", "Let in: points the other teams scored against this team.");
        GLOSSARY.put("Free places", "Free places: how many more players the team can take.");
        GLOSSARY.put("Past teams", "Past teams: teams this player has left.");
        GLOSSARY.put("Also plays for", "Other teams this player is on right now.");
        GLOSSARY.put("Same game as my team", "Yes = this team plays a game that one of your teams plays.");
        GLOSSARY.put("My request", "Your last request to join this team: Waiting, Approved, Declined or Cancelled.");
        GLOSSARY.put("Role asked", "The place on the team the player would like: Captain, Starter or Substitute.");
        GLOSSARY.put("Max teams", "The most teams that can join this tournament.");
        GLOSSARY.put("Prize (AED)", "Prize money in UAE dirhams. Point at a number to see it in full.");
    }

    // =====================================================================
    // TABLE VIEW: a list with loading, empty and error states
    // =====================================================================

    interface Loader {
        ArrayList<String[]> load() throws SQLException;
    }

    interface Opener {
        void open(String[] row, String[] headers);
    }

    static class TableView extends JPanel {
        final CardLayout cards = new CardLayout();
        final JPanel stack = new JPanel(cards);
        final JTable table;
        final JLabel count = text(" ", 14, false, MUTED);
        final JTextField quick = field("");
        final UiButton columnsButton;
        final JPanel emptyCard = new JPanel(new GridBagLayout());
        final JPanel errorCard = new JPanel(new GridBagLayout());
        String[] headers = new String[0];
        ArrayList<String[]> rows = new ArrayList<>();
        List<TableColumn> allColumns = new ArrayList<>();
        Set<String> shown = new LinkedHashSet<>();
        boolean[] numeric = new boolean[0];
        Loader loader;
        Opener opener;
        String noun = "rows";
        String emptyText = "Nothing here yet.";
        String emptyButton;
        Runnable emptyAction;
        int hoverRow = -1;

        TableView() {
            super(new BorderLayout(0, 8));
            setOpaque(false);

            table = new JTable() {
                public String getToolTipText(MouseEvent e) {
                    int r = rowAtPoint(e.getPoint());
                    int c = columnAtPoint(e.getPoint());
                    if (r < 0 || c < 0) return null;
                    Object v = getValueAt(r, c);
                    String raw = v == null ? null : v instanceof Double ? String.valueOf(v) : v.toString();
                    String shownText = cellText(v, getColumnName(c));
                    return raw == null || raw.equals(shownText) && shownText.length() < 30 ? null : raw;
                }
            };
            table.setRowHeight(40);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setFillsViewportHeight(true);
            table.setBackground(SURFACE);
            table.setForeground(TEXT);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setDefaultRenderer(Object.class, new Cell());
            table.setDefaultRenderer(Number.class, new Cell());
            table.getTableHeader().setDefaultRenderer(new Header());
            table.getTableHeader().setPreferredSize(new Dimension(0, 44));
            table.getTableHeader().setReorderingAllowed(false);
            table.addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    int r = table.rowAtPoint(e.getPoint());
                    if (r != hoverRow) {
                        hoverRow = r;
                        table.repaint();
                    }
                }
            });
            table.addMouseListener(new MouseAdapter() {
                public void mouseExited(MouseEvent e) {
                    hoverRow = -1;
                    table.repaint();
                }

                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                        openSelected();
                    }
                }
            });
            // Enter opens the selected row (instead of moving to the next row)
            table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openRow");
            table.getActionMap().put("openRow", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    openSelected();
                }
            });
            table.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
            table.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

            JScrollPane scroll = new JScrollPane(table);
            scroll.setBorder(new LineBorder(BORDER, 1, true));
            scroll.getViewport().setBackground(SURFACE);
            scroll.getVerticalScrollBar().setUnitIncrement(20);

            // tool row: number of rows, filter box, columns button
            columnsButton = new UiButton("Columns", "columns", "text", this::chooseColumns);
            columnsButton.setToolTipText("Choose which columns to show");
            JPanel tools = new JPanel(new BorderLayout(12, 0));
            tools.setOpaque(false);
            count.setFont(font(14, true));
            tools.add(count, BorderLayout.CENTER);           // CENTER: shortened with "..." on a narrow window
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            JLabel filterLabel = labelFor("Filter this list", quick);
            filterLabel.setIcon(new Ico("filter", 18, MUTED));
            quick.setColumns(14);
            quick.getDocument().addDocumentListener(onType(this::applyQuickFilter));
            right.add(filterLabel);
            right.add(quick);
            right.add(columnsButton);
            tools.add(right, BorderLayout.EAST);

            JPanel tableCard = new JPanel(new BorderLayout(0, 8));
            tableCard.setOpaque(false);
            tableCard.add(tools, BorderLayout.NORTH);
            tableCard.add(scroll, BorderLayout.CENTER);

            stack.setOpaque(false);
            emptyCard.setOpaque(false);
            errorCard.setOpaque(false);
            stack.add(tableCard, "table");
            stack.add(new Skeleton(), "loading");
            stack.add(emptyCard, "empty");
            stack.add(errorCard, "error");
            add(stack, BorderLayout.CENTER);
        }

        TableView noun(String n) {
            noun = n;
            return this;
        }

        TableView empty(String text, String button, Runnable action) {
            emptyText = text;
            emptyButton = button;
            emptyAction = action;
            return this;
        }

        TableView opener(Opener o) {
            opener = o;
            return this;
        }

        // loads in the background: skeleton first, then the list, the empty message or the error message
        void load(Loader l) {
            loader = l;
            cards.show(stack, "loading");
            new SwingWorker<ArrayList<String[]>, Void>() {
                SQLException failure;

                protected ArrayList<String[]> doInBackground() {
                    try {
                        return l.load();
                    } catch (SQLException e) {
                        failure = e;
                        return null;
                    }
                }

                protected void done() {
                    if (loader != l) {
                        return;                                  // a newer load replaced this one
                    }
                    ArrayList<String[]> data = null;
                    try {
                        data = get();
                    } catch (Exception e) {
                        failure = new SQLException(e.getMessage());
                    }
                    if (failure != null || data == null) {
                        showProblem(failure);
                    } else {
                        setData(data);
                    }
                }
            }.execute();
        }

        void reload() {
            if (loader != null) {
                load(loader);
            }
        }

        void showProblem(SQLException e) {
            errorCard.removeAll();
            JPanel p = stateBox("alert", DANGER, "We could not load this list.",
                    e == null ? "Please try again." : friendly(e), "Try again", this::reload);
            if (e != null) {
                p.setToolTipText(e.getMessage());
            }
            errorCard.add(p);
            cards.show(stack, "error");
        }

        void setData(ArrayList<String[]> data) {
            headers = data.get(0);
            rows = new ArrayList<>(data.subList(1, data.size()));
            if (rows.isEmpty()) {
                emptyCard.removeAll();
                emptyCard.add(stateBox("info", INFO, emptyText, null, emptyButton, emptyAction));
                cards.show(stack, "empty");
                return;
            }
            // numbers become real numbers so they sort correctly and line up on the right
            numeric = new boolean[headers.length];
            for (int c = 0; c < headers.length; c++) {
                boolean all = true;
                for (String[] r : rows) {
                    if (!"-".equals(r[c]) && !r[c].matches("-?\\d+(\\.\\d+)?")) {
                        all = false;
                        break;
                    }
                }
                numeric[c] = all;
            }
            final boolean[] num = numeric;
            DefaultTableModel model = new DefaultTableModel(headers, 0) {
                public boolean isCellEditable(int r, int c) {
                    return false;
                }

                public Class<?> getColumnClass(int c) {
                    return num[c] ? Number.class : String.class;
                }
            };
            for (String[] r : rows) {
                Object[] values = new Object[r.length];
                for (int c = 0; c < r.length; c++) {
                    values[c] = num[c] ? ("-".equals(r[c]) ? null : (Object) Double.valueOf(r[c])) : r[c];
                }
                model.addRow(values);
            }
            table.setModel(model);
            table.setAutoCreateRowSorter(true);
            allColumns = new ArrayList<>();
            for (int c = 0; c < table.getColumnCount(); c++) {
                allColumns.add(table.getColumnModel().getColumn(c));
            }
            // at most 7 columns at first; ID columns are only used to open rows
            if (shown.isEmpty() || !Arrays.asList(headers).containsAll(shown)) {
                shown.clear();
                for (String h : headers) {
                    if (!isIdColumn(h) && shown.size() < 7) {
                        shown.add(h);
                    }
                }
            }
            applyColumns();
            applyQuickFilter();
            cards.show(stack, "table");
        }

        int choosable() {
            int n = 0;
            for (String h : headers) {
                if (!isIdColumn(h)) n++;
            }
            return n;
        }

        void applyColumns() {
            TableColumnModel cm = table.getColumnModel();
            while (cm.getColumnCount() > 0) {
                cm.removeColumn(cm.getColumn(0));
            }
            FontMetrics fm = table.getFontMetrics(body());
            for (TableColumn col : allColumns) {
                String h = col.getHeaderValue().toString();
                if (shown.contains(h)) {
                    int w = fm.stringWidth(h) + 48;
                    int idx = col.getModelIndex();
                    for (String[] r : rows) {
                        w = Math.max(w, fm.stringWidth(cellText(numeric[idx] && !"-".equals(r[idx])
                                ? Double.valueOf(r[idx]) : r[idx], h)) + 40);
                    }
                    col.setPreferredWidth(Math.min(w, 320));
                    cm.addColumn(col);
                }
            }
            columnsButton.setText("Columns (" + shown.size() + " of " + choosable() + ")");
            columnsButton.setVisible(choosable() > 7 || shown.size() < choosable());
        }

        void chooseColumns() {
            Form f = new Form(SwingUtilities.getWindowAncestor(this), "Choose columns",
                    "Pick the columns you want to see. Fewer columns are easier to read.");
            Map<String, JCheckBox> boxes = new LinkedHashMap<>();
            for (String h : headers) {
                if (!isIdColumn(h)) {
                    JCheckBox b = f.toggle(h);
                    b.setSelected(shown.contains(h));
                    boxes.put(h, b);
                }
            }
            f.open("Show columns", false, () -> {
                Set<String> chosen = new LinkedHashSet<>();
                for (Map.Entry<String, JCheckBox> e : boxes.entrySet()) {
                    if (e.getValue().isSelected()) chosen.add(e.getKey());
                }
                if (chosen.isEmpty()) {
                    throw new Problem(null, "Please pick at least one column.");
                }
                shown.clear();
                shown.addAll(chosen);
                applyColumns();
            });
        }

        void applyQuickFilter() {
            if (!(table.getRowSorter() instanceof TableRowSorter)) {
                return;
            }
            @SuppressWarnings("unchecked")
            TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) table.getRowSorter();
            String q = quick.getText().trim().toLowerCase();
            sorter.setRowFilter(q.isEmpty() ? null : new RowFilter<TableModel, Integer>() {
                public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                    for (int c = 0; c < entry.getValueCount(); c++) {
                        Object v = entry.getValue(c);
                        if (v != null && cellText(v, headers[c]).toLowerCase().contains(q)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
            int n = table.getRowCount();
            count.setText(n + " " + (n == 1 ? singular(noun) : noun)
                    + (opener != null ? "   ·   Double-click a row to see its connections" : ""));
        }

        // the selected row as text in the order of the headers (null if nothing is selected)
        String[] selected() {
            int v = table.getSelectedRow();
            if (v < 0) return null;
            return rows.get(table.convertRowIndexToModel(v));
        }

        String value(String[] row, String header) {
            int i = Arrays.asList(headers).indexOf(header);
            return i < 0 || row == null ? null : row[i];
        }

        int selectedId(String header) {
            String v = value(selected(), header);
            return v == null || "-".equals(v) ? 0 : Integer.parseInt(v);
        }

        void openSelected() {
            String[] r = selected();
            if (r != null && opener != null) {
                opener.open(r, headers);
            }
        }

        // ---- cell and header drawing ----

        class Cell extends DefaultTableCellRenderer {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row,
                                                           int col) {
                super.getTableCellRendererComponent(t, v, sel, false, row, col);
                String h = t.getColumnName(col);
                setFont(body());
                setBorder(new EmptyBorder(0, 12, 0, 12));
                setIcon(null);
                setBackground(sel ? PRIMARY_SOFT : row == hoverRow ? HOVER : row % 2 == 1 ? ZEBRA : SURFACE);
                setForeground(TEXT);
                setHorizontalAlignment(v instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
                if (v == null || "-".equals(v)) {
                    setText("-");
                    setForeground(MUTED);
                    return this;
                }
                String s = cellText(v, h);
                String[] word = WORDS.get(v.toString());
                if (word != null && word[1] != null) {
                    setIcon(new Ico(word[2], 16, statusColor(word[1])));
                }
                setText(s);
                if (sel && col == 0) {
                    setFont(font(16, true));
                }
                return this;
            }
        }

        class Header extends DefaultTableCellRenderer {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row,
                                                           int col) {
                super.getTableCellRendererComponent(t, v, false, false, row, col);
                String h = String.valueOf(v);
                setText(h);
                setFont(font(14, true));
                setForeground(TEXT);
                setBackground(BG);
                setOpaque(true);
                setBorder(new CompoundBorder(new MatteBorder(0, 0, 2, 0, BORDER), new EmptyBorder(0, 12, 0, 12)));
                int model = t.convertColumnIndexToModel(col);
                setHorizontalAlignment(model < numeric.length && numeric[model] ? SwingConstants.RIGHT : SwingConstants.LEFT);
                setHorizontalTextPosition(SwingConstants.LEFT);
                setIcon(null);
                if (t.getRowSorter() != null) {
                    for (RowSorter.SortKey k : t.getRowSorter().getSortKeys()) {
                        if (k.getColumn() == model && k.getSortOrder() != SortOrder.UNSORTED) {
                            setIcon(new Ico(k.getSortOrder() == SortOrder.ASCENDING ? "up" : "down", 16, PRIMARY));
                        }
                    }
                }
                String tip = GLOSSARY.get(h);
                setToolTipText((tip != null ? tip + " " : "") + "Click to sort.");
                return this;
            }
        }
    }

    // what a cell shows: friendly dates, short numbers, sentence-case words
    static String cellText(Object v, String header) {
        if (v == null) return "-";
        if (v instanceof Double) {
            double d = (Double) v;
            return isIdColumn(header) || header.equals("Seed") ? String.valueOf((long) d) : readableNumber(d);
        }
        String s = v.toString();
        if (isDate(s)) return friendlyDate(s);
        String[] word = WORDS.get(s);
        return word != null ? word[0] : s;
    }

    static String singular(String noun) {
        if (noun.endsWith("ies")) return noun.substring(0, noun.length() - 3) + "y";
        if (noun.endsWith("es") && noun.endsWith("ches")) return noun.substring(0, noun.length() - 2);
        return noun.endsWith("s") ? noun.substring(0, noun.length() - 1) : noun;
    }

    static DocumentListener onType(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    // gray shapes while a list loads
    static class Skeleton extends JPanel {
        Skeleton() {
            setOpaque(false);
            getAccessibleContext().setAccessibleName("Loading");
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(MUTED);
            g.setFont(label());
            g.drawString("Loading...", 4, 18);
            g.setColor(BORDER);
            for (int i = 0; i < 6; i++) {
                int y = 36 + i * 48;
                g.fillRoundRect(0, y, getWidth() - 1, 36, 12, 12);
            }
            g.dispose();
        }
    }

    // empty or error box: icon, sentence, smaller sentence, one button
    static JPanel stateBox(String icon, Color color, String title, String detail, String button, Runnable action) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        JLabel i = new JLabel(new Ico(icon, 40, color));
        i.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(i);
        p.add(Box.createVerticalStrut(12));
        JLabel t = text(title, 20, true, TEXT);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(t);
        if (detail != null) {
            p.add(Box.createVerticalStrut(8));
            JLabel d = text(detail, 16, false, MUTED);
            d.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(d);
        }
        if (button != null && action != null) {
            p.add(Box.createVerticalStrut(16));
            UiButton b = new UiButton(button, null, "primary", action);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(b);
        }
        return p;
    }

    // =====================================================================
    // CHART: horizontal bars that start at zero, labeled directly, title = the takeaway
    // =====================================================================

    static class BarChart extends JPanel {
        final List<String[]> rows;      // {label, value}
        final String unit;
        final List<Rectangle> bars = new ArrayList<>();

        BarChart(List<String[]> rows, String unit) {
            this.rows = rows;
            this.unit = unit;
            setOpaque(false);
            setPreferredSize(new Dimension(400, 44 * rows.size() + 40));
            setToolTipText("");
            StringBuilder sb = new StringBuilder("Bar chart. ");
            for (String[] r : rows) {
                sb.append(r[0]).append(": ").append(r[1]).append(" ").append(unit).append(". ");
            }
            getAccessibleContext().setAccessibleDescription(sb.toString());
        }

        double max() {
            double m = 0;
            for (String[] r : rows) m = Math.max(m, num(r[1]));
            return m <= 0 ? 1 : m;
        }

        static double num(String s) {
            try {
                return Double.parseDouble(s);
            } catch (Exception e) {
                return 0;
            }
        }

        public String getToolTipText(MouseEvent e) {
            for (int i = 0; i < bars.size(); i++) {
                Rectangle r = bars.get(i);
                if (e.getY() >= r.y - 6 && e.getY() <= r.y + r.height + 6) {
                    return rows.get(i)[0] + ": " + rows.get(i)[1] + " " + unit;
                }
            }
            return null;
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int labelW = 0;
            g.setFont(label());
            FontMetrics fm = g.getFontMetrics();
            for (String[] r : rows) labelW = Math.max(labelW, fm.stringWidth(r[0]));
            labelW = Math.min(labelW + 16, getWidth() / 2);
            int x0 = labelW;
            int width = getWidth() - x0 - 60;
            double max = max();
            bars.clear();
            for (int i = 0; i < rows.size(); i++) {
                int y = 8 + i * 44;
                String name = rows.get(i)[0];
                // too long: cut letters until "name..." fits (each turn is 1 letter shorter, so this always ends)
                int room = labelW - 12;
                if (fm.stringWidth(name) > room) {
                    while (name.length() > 1 && fm.stringWidth(name + "...") > room) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name = name + "...";
                }
                g.setColor(TEXT);
                g.drawString(name, 0, y + 20);
                int w = (int) Math.round(width * num(rows.get(i)[1]) / max);
                g.setColor(CHART);
                g.fillRoundRect(x0, y + 4, Math.max(w, 2), 24, 8, 8);
                bars.add(new Rectangle(x0, y + 4, Math.max(w, 2), 24));
                g.setColor(TEXT);
                g.setFont(font(14, true));
                g.drawString(readableNumber(num(rows.get(i)[1])), x0 + w + 8, y + 21);
                g.setFont(label());
            }
            // axis from zero, with the unit
            int yAxis = 8 + rows.size() * 44;
            g.setColor(BORDER);
            g.drawLine(x0, 4, x0, yAxis);
            g.drawLine(x0, yAxis, x0 + width, yAxis);
            g.setColor(MUTED);
            g.drawString("0", x0 - 4, yAxis + 18);
            g.drawString(unit, x0 + width - fm.stringWidth(unit), yAxis + 18);
            g.dispose();
        }
    }

    // chart card: takeaway title, the chart, and "Show as table" for exact reading and screen readers
    static Card chartCard(String takeaway, List<String[]> rows, String nameHeader, String unit) {
        Card card = new Card();
        card.setLayout(new BorderLayout(0, 12));
        JLabel title = wrapText(takeaway, 460, TEXT);
        title.setFont(font(20, true));
        if (rows.isEmpty()) {
            card.add(title, BorderLayout.NORTH);
            card.add(stateBox("chart", MUTED, "No results yet.", "The chart fills in after the first matches.",
                    null, null), BorderLayout.CENTER);
            return card;
        }
        CardLayout cl = new CardLayout();
        JPanel views = new JPanel(cl);
        views.setOpaque(false);
        views.add(new BarChart(rows, unit), "chart");
        ArrayList<String[]> data = new ArrayList<>();
        data.add(new String[] {nameHeader, unit.substring(0, 1).toUpperCase() + unit.substring(1)});
        for (String[] r : rows) data.add(new String[] {r[0], r[1]});
        TableView tv = new TableView().noun("rows");
        tv.setData(data);
        tv.quick.getParent().setVisible(false);
        views.add(tv, "table");
        UiButton toggle = new UiButton("Show as table", "columns", "text", null);
        toggle.addActionListener(e -> {
            boolean toTable = toggle.getText().startsWith("Show as table");
            cl.show(views, toTable ? "table" : "chart");
            toggle.setText(toTable ? "Show as chart" : "Show as table");
            toggle.setIcon(new Ico(toTable ? "chart" : "columns"));
        });
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.add(title, BorderLayout.CENTER);
        top.add(toggle, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);
        card.add(views, BorderLayout.CENTER);
        return card;
    }

    // =====================================================================
    // KPI CARD: plain label, big number, a note with icon; the whole card opens a page
    // =====================================================================

    static Card kpi(String label, String number, String note, String noteIcon, Color noteColor, Runnable open) {
        Card c = new Card();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new EmptyBorder(20, 24, 20, 24));
        addLeft(c, text(label, 14, false, MUTED), 4);
        JLabel n = text(number, 32, true, TEXT);
        addLeft(c, n, 4);
        JLabel nl = new JLabel(note, new Ico(noteIcon, 16, noteColor), SwingConstants.LEFT);
        nl.setFont(label());
        nl.setForeground(noteColor == MUTED ? MUTED : noteColor);
        addLeft(c, nl, 0);
        if (open != null) {
            c.setFocusable(true);
            c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            c.setToolTipText("Open: " + label);
            c.getAccessibleContext().setAccessibleName(label + ": " + number + ". " + note);
            c.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { open.run(); }
                public void mouseEntered(MouseEvent e) { c.putClientProperty("hover", true); c.repaint(); }
                public void mouseExited(MouseEvent e) { c.putClientProperty("hover", false); c.repaint(); }
            });
            c.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) open.run();
                }
            });
            c.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { c.repaint(); }
                public void focusLost(FocusEvent e) { c.repaint(); }
            });
        }
        return c;
    }

    // =====================================================================
    // SEGMENTS (tabs) and FILTER CHIPS
    // =====================================================================

    // a row of buttons where one is chosen, e.g. tabs on a details page
    static JPanel segments(String[] names, int selected, java.util.function.IntConsumer onSelect) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        List<UiButton> buttons = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            UiButton b = new UiButton(names[i], null, "segment", null);
            b.setSelectedLook(i == selected);
            b.addActionListener(e -> {
                for (UiButton o : buttons) o.setSelectedLook(o == b);
                onSelect.accept(index);
            });
            buttons.add(b);
            p.add(b);
        }
        return p;
    }

    // a small removable label that shows an active filter
    static JComponent chip(String s, Runnable remove) {
        JPanel c = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(PRIMARY_SOFT);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g.dispose();
            }
        };
        c.setOpaque(false);
        c.setBorder(new EmptyBorder(0, 12, 0, 0));
        JLabel l = text(s, 14, true, TEXT);
        c.add(l);
        UiButton x = new UiButton("", "x", "text", remove);
        x.setToolTipText("Remove filter: " + s);
        x.getAccessibleContext().setAccessibleName("Remove filter " + s);
        x.setForeground(TEXT);
        c.add(x);
        return c;
    }

    // =====================================================================
    // MAIN WINDOW: top bar, side menu, page, message area
    // =====================================================================

    // the side menu for each role (at most 7 items, Home first, icon + label)
    static String[][] menu() {
        if (DataLayer.DIRECTOR.equals(role)) {
            return new String[][] {{"home", "Home", "home"}, {"tournaments", "Tournaments", "trophy"},
                    {"teams", "Teams", "users"}, {"matches", "Matches", "calendar"}, {"players", "Players", "user"},
                    {"find", "Find", "search"}, {"reports", "Reports", "chart"}};
        } else if (DataLayer.COACH.equals(role)) {
            return new String[][] {{"home", "Home", "home"}, {"myteams", "My teams", "users"},
                    {"findplayers", "Find players", "user"}, {"requests", "Join requests", "inbox"},
                    {"tournaments", "Tournaments", "trophy"}, {"find", "Find", "search"}, {"reports", "Reports", "chart"}};
        }
        return new String[][] {{"home", "Home", "home"}, {"profile", "My profile", "user"},
                {"myteams", "My teams", "users"}, {"mymatches", "My matches", "calendar"},
                {"findteam", "Find a team", "trophy"}, {"find", "Find", "search"}, {"reports", "Reports", "chart"}};
    }

    static void showMainWindow(String startPage) {
        // a new login starts on the first tab of every page
        findTeamTab = 0;
        requestsTab = 0;
        playerTeamsTab = 0;
        playerMatchesTab = 0;
        coachTeamIndex = 0;
        detailsTab.clear();
        Rectangle old = mainFrame != null ? mainFrame.getBounds() : null;
        if (mainFrame != null) {
            mainFrame.dispose();
        }
        mainFrame = new JFrame("Esports tournaments - " + fullName);
        mainFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        mainFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                exitProgram();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(topBar(), BorderLayout.NORTH);

        navPanel = new JPanel();
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        navPanel.setBackground(SURFACE);
        navPanel.setBorder(new CompoundBorder(new MatteBorder(0, 0, 0, 1, BORDER), new EmptyBorder(16, 12, 16, 12)));
        root.add(navPanel, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(BG);
        pageHolder = new JPanel(new BorderLayout());
        pageHolder.setBackground(BG);
        right.add(pageHolder, BorderLayout.CENTER);
        toastHolder = new JPanel(new BorderLayout());
        toastHolder.setOpaque(false);
        toastHolder.setBorder(new EmptyBorder(0, 32, 16, 32));
        right.add(toastHolder, BorderLayout.SOUTH);
        root.add(right, BorderLayout.CENTER);

        mainFrame.setContentPane(root);
        keyboardShortcuts(root);
        mainFrame.setMinimumSize(new Dimension(760, 560));
        if (old != null) {
            mainFrame.setBounds(old);
        } else {
            mainFrame.setSize(1366, 800);
            mainFrame.setLocationRelativeTo(null);
        }
        compact = mainFrame.getWidth() < 1100;
        buildNav();
        mainFrame.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                boolean c = mainFrame.getWidth() < 1100;
                if (c != compact) {
                    compact = c;
                    buildNav();
                    refresh();
                }
            }
        });
        mainFrame.setVisible(true);
        openNav(startPage);
        if (!prefBool("tour." + username, false)) {
            SwingUtilities.invokeLater(PresentationLayer::tour);
        }
    }

    static JPanel topBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBackground(SURFACE);
        bar.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(10, 20, 10, 20)));

        JLabel logo = new JLabel("Esports tournaments", new Ico("trophy", 24, PRIMARY), SwingConstants.LEFT);
        logo.setFont(font(18, true));
        logo.setForeground(TEXT);
        logo.setIconTextGap(10);
        bar.add(logo, BorderLayout.WEST);

        // search: teams, players and tournaments (Ctrl+F)
        JPanel search = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        search.setOpaque(false);
        topSearch = field("");
        topSearch.setColumns(22);
        topSearch.setToolTipText("Search for a team, player or tournament, then press Enter (Ctrl+F)");
        JLabel sl = labelFor("Search", topSearch);
        sl.setIcon(new Ico("search", 20, MUTED));
        topSearch.addActionListener(e -> {
            String q = topSearch.getText().trim();
            if (!q.isEmpty()) {
                currentNav = "find";
                highlightNav();
                goTop("Find", () -> pageFind(q));
            }
        });
        search.add(sl);
        search.add(topSearch);
        bar.add(search, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        UiButton help = new UiButton("Help", "help", "text", PresentationLayer::help);
        help.setToolTipText("Help for this page (F1)");
        right.add(help);
        JLabel who = new JLabel("<html>" + escape(fullName) + "<br><span style='font-size:11px'>"
                + WORDS.get(role)[0] + "</span></html>", new Ico("user", 20, MUTED), SwingConstants.LEFT);
        who.setFont(label());
        who.setForeground(TEXT);
        who.setBorder(new EmptyBorder(0, 12, 0, 8));
        right.add(who);
        right.add(new UiButton("Log out", "logout", "secondary", () -> {
            if (toastTimer != null) {
                toastTimer.stop();
            }
            mainFrame.dispose();
            mainFrame = null;
            toastHolder = null;
            showLoginWindow();
        }));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    static void buildNav() {
        navPanel.removeAll();
        for (String[] item : menu()) {
            UiButton b = new UiButton(compact ? "" : item[1], item[2], "nav", () -> {
                openNav(item[0]);
            });
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setBorder(new EmptyBorder(10, 16, 10, 16));
            b.putClientProperty("key", item[0]);
            b.getAccessibleContext().setAccessibleName(item[1]);
            if (compact) {
                b.setToolTipText(item[1]);
            }
            b.setMaximumSize(new Dimension(compact ? 64 : 240, 48));
            b.setPreferredSize(new Dimension(compact ? 64 : 220, 48));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            navPanel.add(b);
            navPanel.add(Box.createVerticalStrut(4));
        }
        navPanel.add(Box.createVerticalGlue());
        highlightNav();
        navPanel.revalidate();
        navPanel.repaint();
    }

    static void highlightNav() {
        if (navPanel == null) return;
        for (Component c : navPanel.getComponents()) {
            if (c instanceof UiButton) {
                UiButton b = (UiButton) c;
                b.setSelectedLook(currentNav.equals(b.getClientProperty("key")));
            }
        }
    }

    static void keyboardShortcuts(JComponent root) {
        root.registerKeyboardAction(e -> topSearch.requestFocusInWindow(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> help(), KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> goBack(), KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        String[][] m = menu();
        for (int i = 0; i < m.length; i++) {
            final String key = m[i][0];
            root.registerKeyboardAction(e -> openNav(key),
                    KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.ALT_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
    }

    // =====================================================================
    // NAVIGATION: menu pages, deeper "connection" pages, Back, breadcrumbs
    // =====================================================================

    static final ArrayList<Object[]> trail = new ArrayList<>();       // {label, Runnable that builds the page}

    static void openNav(String key) {
        currentNav = key;
        highlightNav();
        String label = "Home";
        for (String[] m : menu()) {
            if (m[0].equals(key)) label = m[1];
        }
        Runnable build;
        switch (key) {
            case "tournaments": build = PresentationLayer::pageTournaments; break;
            case "teams": build = PresentationLayer::pageTeams; break;
            case "matches": build = PresentationLayer::pageMatches; break;
            case "players": build = PresentationLayer::pagePlayers; break;
            case "find": build = () -> pageFind(""); break;
            case "reports": build = PresentationLayer::pageReports; break;
            case "myteams": build = DataLayer.COACH.equals(role) ? PresentationLayer::pageCoachTeams
                                                                  : PresentationLayer::pagePlayerTeams; break;
            case "findplayers": build = PresentationLayer::pageFindPlayers; break;
            case "profile": build = PresentationLayer::pageProfile; break;
            case "mymatches": build = PresentationLayer::pagePlayerMatches; break;
            case "findteam": build = PresentationLayer::pageFindTeam; break;
            case "requests": build = PresentationLayer::pageCoachRequests; break;
            default: build = PresentationLayer::pageHome;
        }
        goTop(label, build);
    }

    static void goTop(String label, Runnable build) {
        trail.clear();
        trail.add(new Object[] {label, build});
        build.run();
    }

    static void goDeeper(String label, Runnable build) {
        trail.add(new Object[] {label, build});
        build.run();
    }

    static void goBack() {
        if (trail.size() > 1) {
            trail.remove(trail.size() - 1);
            ((Runnable) trail.get(trail.size() - 1)[1]).run();
        }
    }

    // builds the current page again (after a change, so the new data shows at once)
    static void refresh() {
        if (!trail.isEmpty()) {
            ((Runnable) trail.get(trail.size() - 1)[1]).run();
        }
    }

    static void show(JComponent page) {
        pageHolder.removeAll();
        pageHolder.add(page, BorderLayout.CENTER);
        pageHolder.revalidate();
        pageHolder.repaint();
    }

    // a page: (back + breadcrumbs), title, one-sentence summary, buttons, then the content
    static class Page extends JPanel {
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        final JPanel top = new JPanel();

        Page(String title, String summary) {
            super(new BorderLayout(0, 16));
            setBackground(BG);
            setBorder(new EmptyBorder(24, 32, 16, 32));
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.setOpaque(false);
            if (trail.size() > 1) {
                JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                nav.setOpaque(false);
                nav.add(new UiButton("Back", "back", "text", PresentationLayer::goBack));
                if (trail.size() > 2) {                         // breadcrumbs only when 3 or more levels deep
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < trail.size(); i++) {
                        if (i > 0) sb.append("  ›  ");
                        sb.append(trail.get(i)[0]);
                    }
                    nav.add(text(sb.toString(), 14, false, MUTED));
                }
                addLeft(top, nav, 8);
            }
            JLabel t = text(title, 28, true, TEXT);
            t.getAccessibleContext().setAccessibleName("Page: " + title);
            addLeft(top, t, 6);
            if (summary != null) {
                addLeft(top, wrapText(summary, 760, MUTED), 16);
            }
            actions.setOpaque(false);
            addLeft(top, actions, 0);
            add(top, BorderLayout.NORTH);
        }

        UiButton button(String text, String icon, String kind, Runnable r) {
            UiButton b = new UiButton(text, icon, kind, r);
            actions.add(b);
            return b;
        }

        void content(JComponent c) {
            add(c, BorderLayout.CENTER);
        }
    }

    // =====================================================================
    // HELP AND FIRST-VISIT TOUR
    // =====================================================================

    static String pageHelp() {
        switch (currentNav) {
            case "tournaments": return "This page lists all tournaments. Pick one, then use the buttons above the list. "
                    + "A team can leave a tournament until it has played a match in it.";
            case "teams": return "This page lists all teams with their players and tournaments. Use the filters to find one. "
                    + "'Add a team' makes a new team and gives it a coach.";
            case "matches": return "This page lists all matches. Pick a match that is waiting, then record its result.";
            case "players": return "This page lists all players and their teams. Turn on 'Only players with no team' to find free players.";
            case "find": return "Type a name to find teams, players and tournaments. Small typing mistakes are OK.";
            case "reports": return "Pick a report at the top. You can choose a tournament or a team to see less.";
            case "myteams": return DataLayer.COACH.equals(role)
                    ? "This page shows your teams and the players on them. 'Create a team' makes a new team that you coach."
                    : "This page shows your teams. Pick a team and click 'Leave team' to leave it. "
                      + "You stay in the team's history.";
            case "findplayers": return "These players are not on your teams. Pick one and add them to a team.";
            case "profile": return "This page shows your details. You can change your email, country and password.";
            case "mymatches": return "This page shows your matches and how you played in each one.";
            case "findteam": return "These are teams you are not on. Pick one and click 'Ask to join'. The team's "
                    + "coach answers your request. See the answers on the 'My requests' tab.";
            case "requests": return "Players who asked to join your teams. Pick a request, then click Approve or Decline. "
                    + "Approving adds the player to the team.";
            default: return "Home shows the most important numbers and what to do next.";
        }
    }

    static void help() {
        if (mainFrame == null) return;
        JDialog d = new JDialog(mainFrame, "Help", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SURFACE);
        p.setBorder(new EmptyBorder(24, 24, 20, 24));
        addLeft(p, text("Help", 28, true, TEXT), 16);
        addLeft(p, text("This page", 20, true, TEXT), 6);
        addLeft(p, wrapText(pageHelp(), 440, TEXT), 16);
        addLeft(p, text("Tips", 20, true, TEXT), 6);
        String[] tips = {
            "Use the menu on the left to move between pages.",
            "Pick a row in a list, then use the buttons above the list.",
            "Double-click a row (or press Enter) to see who it is connected to.",
            "After a change you can press Undo in the message at the bottom.",
            "Keys: Ctrl+F = search, Alt+1 to Alt+7 = menu, Alt+Left = back, F1 = help."};
        for (String t : tips) {
            JLabel l = new JLabel("<html><div style='width:420px'>" + escape(t) + "</div></html>",
                    new Ico("check", 18, SUCCESS), SwingConstants.LEFT);
            l.setFont(body());
            l.setForeground(TEXT);
            addLeft(p, l, 6);
        }
        p.add(Box.createVerticalStrut(10));
        addLeft(p, text("Words", 20, true, TEXT), 6);
        for (String w : new String[] {"Seed", "Kills per death", "Points", "Free places"}) {
            addLeft(p, wrapText(GLOSSARY.get(w), 440, TEXT), 4);
        }
        addLeft(p, wrapText("Free agent: a player who is not on any team right now.", 440, TEXT), 16);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(new UiButton("Show the tour", "info", "secondary", () -> {
            d.dispose();
            tour();
        }));
        UiButton close = new UiButton("Close help", null, "primary", d::dispose);
        buttons.add(close);
        addLeft(p, buttons, 0);
        d.setContentPane(new JScrollPane(p));
        d.getRootPane().setDefaultButton(close);
        escapeCloses(d);
        d.pack();
        d.setSize(d.getWidth(), Math.min(d.getHeight(), Math.min(720, screenHeight())));
        d.setLocationRelativeTo(mainFrame);
        d.setVisible(true);
    }

    // 3 short steps, can be skipped, can be opened again from Help
    static void tour() {
        String[][] steps = {
            {"Welcome, " + fullName.split(" ")[0] + "!", "The menu on the left takes you to each page. Home shows what matters most."},
            {"Lists and buttons", "Pick a row in a list, then use the buttons above it. Double-click a row to see who it is connected to."},
            {"Help is always here", "Click Help at the top (or press F1) if you get stuck. You can undo most changes."}};
        JDialog d = new JDialog(mainFrame, "Quick tour", Dialog.ModalityType.APPLICATION_MODAL);
        int[] step = {0};
        JLabel count = text("", 14, false, MUTED);
        JLabel title = text("", 20, true, TEXT);
        JLabel body = wrapText("", 380, TEXT);
        UiButton skip = new UiButton("Skip tour", null, "secondary", d::dispose);
        UiButton next = new UiButton("Next", null, "primary", null);
        Runnable showStep = () -> {
            count.setText("Step " + (step[0] + 1) + " of 3");
            title.setText(steps[step[0]][0]);
            body.setText("<html><div style='width:380px'>" + escape(steps[step[0]][1]) + "</div></html>");
            next.setText(step[0] == 2 ? "Done" : "Next");
        };
        next.addActionListener(e -> {
            if (step[0] == 2) {
                d.dispose();
            } else {
                step[0]++;
                showStep.run();
            }
        });
        showStep.run();
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SURFACE);
        p.setBorder(new EmptyBorder(24, 24, 20, 24));
        addLeft(p, count, 6);
        addLeft(p, title, 8);
        addLeft(p, body, 24);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(skip);
        buttons.add(next);
        addLeft(p, buttons, 0);
        d.setContentPane(p);
        d.getRootPane().setDefaultButton(next);
        escapeCloses(d);
        d.pack();
        d.setSize(Math.max(d.getWidth(), 480), d.getHeight() + 20);
        d.setLocationRelativeTo(mainFrame);
        prefPut("tour." + username, true);
        d.setVisible(true);
    }

    // =====================================================================
    // SMALL PAGE HELPERS
    // =====================================================================

    // opens the details page of the row: the ID column (type given) or the first of
    // Player ID, Team ID, Tournament ID, Match ID
    static Opener opens(String idType) {
        return (row, headers) -> {
            List<String> h = Arrays.asList(headers);
            if (idType != null && h.contains("ID")) {
                openDetails(idType, Integer.parseInt(row[h.indexOf("ID")]));
                return;
            }
            String[][] order = {{"Player ID", "PLAYER"}, {"Team ID", "TEAM"}, {"Tournament ID", "TOURNAMENT"},
                    {"Match ID", "MATCH"}};
            for (String[] o : order) {
                int i = h.indexOf(o[0]);
                if (i >= 0 && !"-".equals(row[i])) {
                    openDetails(o[1], Integer.parseInt(row[i]));
                    return;
                }
            }
        };
    }

    static void openDetails(String type, int id) {
        try {
            String[] d = dl.describe(type, id);
            if (d == null) {
                toast("error", "We could not find this any more. It may have been deleted.", null);
                return;
            }
            String name = "MATCH".equals(type) ? d[0] + " vs " + d[1] : d[0];
            goDeeper(name, () -> pageDetails(type, id));
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // runs a page part that reads the database; on a problem shows a friendly error with "Try again"
    interface PagePart {
        void build() throws SQLException;
    }

    static void safely(PagePart part) {
        try {
            part.build();
        } catch (SQLException e) {
            Page p = new Page("Something went wrong", null);
            JPanel box = stateBox("alert", DANGER, "We could not load this page.", friendly(e), "Try again",
                    PresentationLayer::refresh);
            box.setToolTipText(e.getMessage());
            JPanel center = new JPanel(new GridBagLayout());
            center.setOpaque(false);
            center.add(box);
            p.content(center);
            show(p);
        }
    }

    static String firstName() {
        return fullName.split(" ")[0];
    }

    static String greeting() {
        int h = LocalTime.now().getHour();
        return (h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening") + ", " + firstName() + ". ";
    }

    // a vertical page body that scrolls
    static JScrollPane scrollBody(JComponent c) {
        WidthTracker view = new WidthTracker();                // the page gets the window's width, never wider
        view.add(c, BorderLayout.NORTH);
        JScrollPane s = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        s.setBorder(null);
        s.getViewport().setBackground(BG);
        s.getVerticalScrollBar().setUnitIncrement(20);
        return s;
    }

    static class WidthTracker extends JPanel implements Scrollable {
        WidthTracker() {
            super(new BorderLayout());
            setOpaque(false);
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static GridBagConstraints at(int x, int y, int w, double wx) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x;
        g.gridy = y;
        g.gridwidth = w;
        g.weightx = wx;
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(0, 0, 24, x == 0 && w == 1 ? 24 : 0);
        return g;
    }

    // card with a title and a table inside
    static Card tableCard(String title, TableView tv, int height) {
        Card c = new Card();
        c.setLayout(new BorderLayout(0, 12));
        c.add(text(title, 20, true, TEXT), BorderLayout.NORTH);
        tv.setPreferredSize(new Dimension(400, height));
        c.add(tv, BorderLayout.CENTER);
        return c;
    }

    // "to do" card: up to 5 items, each with one button
    static Card todoCard(String title, List<String[]> items, String buttonText,
                         java.util.function.Consumer<String[]> action, String emptyText) {
        Card c = new Card();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        addLeft(c, text(title, 20, true, TEXT), 16);
        if (items.isEmpty()) {
            JLabel l = new JLabel(emptyText, new Ico("check", 20, SUCCESS), SwingConstants.LEFT);
            l.setFont(body());
            l.setForeground(TEXT);
            addLeft(c, l, 0);
            return c;
        }
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            String[] it = items.get(i);
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            JPanel words = new JPanel();
            words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
            words.setOpaque(false);
            words.add(text(it[0], 16, true, TEXT));
            words.add(text(it[1], 14, false, MUTED));
            row.add(words, BorderLayout.CENTER);
            UiButton b = new UiButton(buttonText, null, "secondary", () -> action.accept(it));
            b.getAccessibleContext().setAccessibleName(buttonText + ": " + it[0]);
            row.add(b, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            addLeft(c, row, 12);
        }
        if (items.size() > 5) {
            addLeft(c, text("And " + (items.size() - 5) + " more.", 14, false, MUTED), 0);
        }
        return c;
    }

    static JPanel kpiRow(Card... cards) {
        JPanel p = new JPanel(new GridLayout(compact && cards.length > 2 ? 2 : 1,
                compact && cards.length > 2 ? 2 : cards.length, 16, 16));
        p.setOpaque(false);
        for (Card c : cards) p.add(c);
        return p;
    }

    // rows of a table as {id, label} for a dropdown
    static List<String[]> options(ArrayList<String[]> table, String idHeader, String... labelHeaders) {
        List<String> h = Arrays.asList(table.get(0));
        List<String[]> out = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            StringBuilder sb = new StringBuilder();
            for (String lh : labelHeaders) {
                String v = table.get(r)[h.indexOf(lh)];
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append(isDate(v) ? friendlyDate(v) : v);
            }
            out.add(new String[] {table.get(r)[h.indexOf(idHeader)], sb.toString()});
        }
        return out;
    }

    // =====================================================================
    // HOME
    // =====================================================================

    static void pageHome() {
        safely(() -> {
            String[] n = dl.homeNumbers(role, profileId);
            ArrayList<String[]> chart = dl.homeChart(role, profileId);
            JPanel col = new JPanel(new GridBagLayout());
            col.setOpaque(false);
            Page p;
            if (DataLayer.DIRECTOR.equals(role)) {
                p = new Page("Home", greeting() + "Here is how your tournaments are doing.");
                boolean waiting = !"0".equals(n[2]);
                col.add(kpiRow(
                        kpi("Tournaments running now", n[0], n[1] + " coming soon", "clock", MUTED,
                                () -> openNav("tournaments")),
                        kpi("Matches waiting for a result", n[2], waiting ? "Next one: " + friendlyDate(n[3])
                                : "All results are in", waiting ? "alert" : "check", waiting ? WARNING : SUCCESS,
                                () -> openNav("matches")),
                        kpi("Teams", n[4], "In " + n[5] + " games", "info", MUTED, () -> openNav("teams")),
                        kpi("Players", n[6], n[7] + " without a team", "info", MUTED, () -> openNav("players"))),
                        at(0, 0, 2, 1));
                String takeaway = "No standings yet.";
                if (!chart.isEmpty()) {
                    String t = chart.get(0)[2];
                    takeaway = chart.size() > 1 && chart.get(0)[1].equals(chart.get(1)[1])
                            ? chart.get(0)[0] + " and " + chart.get(1)[0] + " share the lead in " + t
                            : chart.get(0)[0] + " is on top in " + t;
                }
                ArrayList<String[]> scheduled = dl.scheduledMatches();
                List<String[]> todo = new ArrayList<>();
                for (String[] o : options(scheduled, "ID", "Home", "Away")) {
                    String[] row = scheduled.get(todo.size() + 1);
                    todo.add(new String[] {o[1].replace("  ·  ", " vs "), friendlyDate(row[3]) + "  ·  " + row[1], o[0]});
                }
                addChartAndTodo(col, chartCard(takeaway, chart, "Team", "points"),
                        todoCard("To do: record results", todo, "Record result", it -> recordResult(Integer.parseInt(it[2])),
                                "Nothing to do. All results are in."));
                TableView recent = new TableView().noun("results").opener(opens(null))
                        .empty("No results yet.", null, null);
                recent.load(() -> dl.recentResults(5));
                col.add(tableCard("Latest results", recent, 260), at(0, 2, 2, 1));
            } else if (DataLayer.COACH.equals(role)) {
                p = new Page("Home", greeting() + "Here is how your teams are doing.");
                boolean hasNext = !"0".equals(n[4]);
                String[] next = hasNext ? n[4].split("\\|") : null;
                col.add(kpiRow(
                        kpi("My teams", n[0], n[1] + " players in total", "users", MUTED, () -> openNav("myteams")),
                        kpi("Tournaments entered", n[2], n[3] + " still to come", "trophy", MUTED,
                                () -> openNav("tournaments")),
                        kpi("Next match", hasNext ? friendlyDate(next[0].substring(0, 10)) : "None yet",
                                hasNext ? next[1] : "No matches planned", "calendar", MUTED, () -> openNav("myteams")),
                        kpi("Join requests", n[5], "0".equals(n[5]) ? "Nothing to answer" : "Waiting for your answer",
                                "0".equals(n[5]) ? "check" : "alert", "0".equals(n[5]) ? SUCCESS : WARNING,
                                () -> openNav("requests"))),
                        at(0, 0, 2, 1));
                String takeaway = chart.isEmpty() ? "Your players have no match results yet."
                        : chart.get(0)[0] + " has the best kills per death on your teams";
                ArrayList<String[]> upcoming = dl.upcomingMatches(profileId);
                addChartAndTodo(col, chartCard(takeaway, chart, "Player", "kills per death"),
                        todoCard("Your next matches", matchItems(upcoming), "See match",
                                it -> openDetails("MATCH", Integer.parseInt(it[2])), "No matches planned yet."));
                TableView teams = new TableView().noun("teams").opener(opens("TEAM"));
                teams.load(() -> dl.teamsOfCoach(profileId));
                col.add(tableCard("Your teams", teams, 200), at(0, 2, 2, 1));
            } else {
                p = new Page("Home", greeting() + "Here is how you are playing.");
                boolean hasNext = !"0".equals(n[3]);
                String[] next = hasNext ? n[3].split("\\|") : null;
                col.add(kpiRow(
                        kpi("My teams", n[0], "See your teammates", "users", MUTED, () -> openNav("myteams")),
                        kpi("Matches I played", n[1], "See my statistics", "calendar", MUTED, () -> openNav("mymatches")),
                        kpi("Kills per death", readableNumber(BarChart.num(n[2])), "Higher is better", "info", MUTED,
                                () -> openNav("mymatches")),
                        kpi("Next match", hasNext ? friendlyDate(next[0].substring(0, 10)) : "None yet",
                                hasNext ? next[1] : "No matches planned", "calendar", MUTED, () -> openNav("mymatches"))),
                        at(0, 0, 2, 1));
                String takeaway = "You have no match results yet.";
                if (!chart.isEmpty()) {
                    String[] best = chart.get(0);
                    for (String[] r : chart) {
                        if (BarChart.num(r[1]) > BarChart.num(best[1])) best = r;
                    }
                    takeaway = "Your best recent match: " + readableNumber(BarChart.num(best[1])) + " kills in " + best[0];
                }
                ArrayList<String[]> mine = dl.playerMatches(profileId);
                ArrayList<String[]> waiting = new ArrayList<>();
                waiting.add(mine.get(0));
                for (int i = 1; i < mine.size(); i++) {
                    if ("SCHEDULED".equals(mine.get(i)[mine.get(i).length - 1])) waiting.add(mine.get(i));
                }
                List<String[]> items = new ArrayList<>();
                for (int i = 1; i < waiting.size(); i++) {
                    String[] r = waiting.get(i);
                    items.add(new String[] {r[4] + " vs " + r[5], friendlyDate(r[1]) + "  ·  " + r[2], r[0]});
                }
                addChartAndTodo(col, chartCard(takeaway, chart, "Match", "kills"),
                        todoCard("Your next matches", items, "See match",
                                it -> openDetails("MATCH", Integer.parseInt(it[2])), "No matches planned yet."));
                TableView tours = new TableView().noun("tournaments").opener(opens(null))
                        .empty("You have not played in a tournament yet.", null, null);
                tours.load(() -> dl.playerTournaments(profileId));
                col.add(tableCard("Tournaments you played in", tours, 220), at(0, 2, 2, 1));
            }
            GridBagConstraints filler = at(0, 9, 2, 1);
            filler.weighty = 1;                                // extra height goes below the cards, not above
            col.add(Box.createVerticalGlue(), filler);
            p.content(scrollBody(col));
            show(p);
        });
    }

    // list rows from upcomingMatches() as to-do items {title, date line, match id}
    static List<String[]> matchItems(ArrayList<String[]> upcoming) {
        List<String[]> items = new ArrayList<>();
        for (int i = 1; i < upcoming.size(); i++) {
            String[] r = upcoming.get(i);       // Match ID, Date/time, Home, Away, Round, Tournament
            items.add(new String[] {r[2] + " vs " + r[3], friendlyDate(r[1]) + "  ·  " + r[4], r[0]});
        }
        return items;
    }

    static void addChartAndTodo(JPanel col, Card chart, Card todo) {
        if (compact) {
            col.add(chart, at(0, 1, 2, 1));
            GridBagConstraints g = at(0, 3, 2, 1);
            col.add(todo, g);
        } else {
            GridBagConstraints a = at(0, 1, 1, 2);
            col.add(chart, a);
            col.add(todo, at(1, 1, 1, 1));
        }
    }

    // =====================================================================
    // DIRECTOR AND COACH: TOURNAMENTS
    // =====================================================================

    static void pageTournaments() {
        boolean director = DataLayer.DIRECTOR.equals(role);
        Page p = new Page("Tournaments", director
                ? "All tournaments. Pick one in the list, then choose what to do. Double-click to see its teams and players."
                : "All tournaments. Pick an upcoming one to enter your team. Double-click to see who plays in it.");
        TableView tv = new TableView().noun("tournaments").opener(opens("TOURNAMENT"));
        if (director) {
            tv.empty("No tournaments yet. Add your first tournament.", "Add tournament", PresentationLayer::addTournament);
            p.button("Add tournament", "plus", "primary", PresentationLayer::addTournament);
            p.button("Edit", "edit", "secondary", () -> editTournament(tv.selectedId("ID")));
            p.button("Register a team", "users", "secondary", () -> registerTeam(tv.selectedId("ID"), 0));
            p.button("Remove a team", "x", "secondary", () -> directorRemoveTeam(tv.selectedId("ID"), 0));
            p.button("Delete", "trash", "danger-outline", () -> deleteTournament(tv.selectedId("ID")));
        } else {
            tv.empty("No tournaments yet.", null, null);
            p.button("Enter my team", "plus", "primary", () -> coachRegister(0, tv.selectedId("ID")));
            p.button("Drop out", "x", "danger-outline", () -> coachDropOut(0, tv.selectedId("ID")));
        }
        p.button("See connections", "open", "secondary", () -> openOrHint(tv, "tournament"));
        tv.load(dl::listTournaments);
        p.content(tv);
        show(p);
    }

    static void openOrHint(TableView tv, String what) {
        if (tv.selected() == null) {
            toast("info", "Pick a " + what + " in the list first, then try again.", null);
        } else {
            tv.openSelected();
        }
    }

    // =====================================================================
    // FILTER BAR: name, game, "no team" - shown as removable chips
    // =====================================================================

    static class Filters {
        final JPanel panel = new JPanel();
        final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        final JTextField name = field("");
        Radio game;
        JCheckBox free;
        String[] gameNames;

        Filters(boolean withGame, String freeText, Runnable onChange) {
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(false);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            row.setOpaque(false);
            name.setColumns(16);
            row.add(labelFor("Name", name));
            row.add(name);
            if (withGame) {
                try {
                    List<String[]> games = dl.pickList("GAME");
                    String[] values = new String[games.size() + 1];
                    gameNames = new String[games.size() + 1];
                    values[0] = "0";
                    gameNames[0] = "All games";
                    for (int i = 0; i < games.size(); i++) {
                        values[i + 1] = games.get(i)[0];
                        gameNames[i + 1] = games.get(i)[1];
                    }
                    game = new Radio(values, gameNames, "0");
                    JLabel gl = PresentationLayer.text("Game", 14, true, TEXT);
                    row.add(gl);
                    row.add(game);
                } catch (SQLException e) {
                    game = null;                         // the list still works without the game filter
                }
            }
            if (freeText != null) {
                free = check(freeText);
                row.add(free);
            }
            addLeft(panel, row, 8);
            chips.setOpaque(false);
            addLeft(panel, chips, 0);

            javax.swing.Timer wait = new javax.swing.Timer(300, e -> {
                updateChips(onChange);
                onChange.run();
            });
            wait.setRepeats(false);
            name.getDocument().addDocumentListener(onType(wait::restart));
            Runnable now = () -> {
                updateChips(onChange);
                onChange.run();
            };
            if (game != null) game.onChange(now);
            if (free != null) free.addActionListener(e -> now.run());
        }

        String text() {
            return name.getText().trim();
        }

        int gameId() {
            return game == null ? 0 : Integer.parseInt(game.value());
        }

        boolean freeOnly() {
            return free != null && free.isSelected();
        }

        void updateChips(Runnable onChange) {
            chips.removeAll();
            if (!text().isEmpty()) {
                chips.add(chip("Name: " + text(), () -> name.setText("")));
            }
            if (gameId() != 0) {
                chips.add(chip("Game: " + game.shownValue(), () -> {
                    game.buttons[0].setSelected(true);
                    updateChips(onChange);
                    onChange.run();
                }));
            }
            if (freeOnly()) {
                chips.add(chip(free.getText(), () -> {
                    free.setSelected(false);
                    updateChips(onChange);
                    onChange.run();
                }));
            }
            if (chips.getComponentCount() > 1) {
                chips.add(new UiButton("Clear all filters", null, "text", () -> {
                    name.setText("");
                    if (game != null) game.buttons[0].setSelected(true);
                    if (free != null) free.setSelected(false);
                    updateChips(onChange);
                    onChange.run();
                }));
            }
            chips.revalidate();
            chips.repaint();
        }
    }

    // a page body with filters on top of a list
    static JPanel withFilters(Filters f, TableView tv) {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(f.panel, BorderLayout.NORTH);
        body.add(tv, BorderLayout.CENTER);
        return body;
    }

    // =====================================================================
    // DIRECTOR: TEAMS, MATCHES, PLAYERS
    // =====================================================================

    static void pageTeams() {
        Page p = new Page("Teams", "Every team with its players and tournaments. Double-click a team to see more.");
        TableView tv = new TableView().noun("teams").opener(opens("TEAM"))
                .empty("No teams match your filters.", null, null);
        current = new Filters(true, null, () -> tv.load(() -> dl.findTeams(fText(), fGame())));
        Filters filters = current;
        p.button("Add a team", "plus", "primary", PresentationLayer::addTeam);
        p.button("Register for a tournament", "trophy", "secondary", () -> registerTeam(0, tv.selectedId("ID")));
        p.button("See connections", "open", "secondary", () -> openOrHint(tv, "team"));
        tv.load(() -> dl.findTeams(fText(), fGame()));
        p.content(withFilters(filters, tv));
        show(p);
    }

    // the filters of the page that is open now (used by its loaders)
    static Filters current;

    static String fText() { return current == null ? "" : current.text(); }
    static int fGame() { return current == null ? 0 : current.gameId(); }
    static boolean fFree() { return current != null && current.freeOnly(); }

    static void pageMatches() {
        Page p = new Page("Matches", "All matches. Pick one that is waiting to record its result. "
                + "Double-click a match to see who played.");
        TableView tv = new TableView().noun("matches").opener(opens("MATCH"))
                .empty("No matches here.", "Schedule a match", PresentationLayer::scheduleMatch);
        Radio show = new Radio(new String[] {"all", "waiting", "done"},
                new String[] {"All matches", "Waiting for a result", "Finished"}, "all");
        JComboBox<String> tournament = combo();
        try {
            List<String[]> t = new ArrayList<>();
            t.add(new String[] {"0", "All tournaments"});
            t.addAll(dl.pickList("TOURNAMENT"));
            setOptions(tournament, t, "0");
        } catch (SQLException e) {
            setOptions(tournament, Collections.singletonList(new String[] {"0", "All tournaments"}), "0");
        }
        Loader loader = () -> {
            ArrayList<String[]> all = dl.reportSchedule(Integer.parseInt(idOf(tournament)));
            ArrayList<String[]> out = new ArrayList<>();
            out.add(all.get(0));
            for (int i = 1; i < all.size(); i++) {
                String status = all.get(i)[all.get(i).length - 1];
                if ("all".equals(show.value()) || "waiting".equals(show.value()) == "SCHEDULED".equals(status)) {
                    out.add(all.get(i));
                }
            }
            return out;
        };
        show.onChange(() -> tv.load(loader));
        tournament.addActionListener(e -> tv.load(loader));
        p.button("Record a result", "check", "primary", () -> recordResult(tv.selectedId("ID")));
        p.button("Schedule a match", "calendar", "secondary", PresentationLayer::scheduleMatch);
        p.button("See who played", "open", "secondary", () -> openOrHint(tv, "match"));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        bar.setOpaque(false);
        bar.add(text("Show", 14, true, TEXT));
        bar.add(show);
        bar.add(labelFor("Tournament", tournament));
        bar.add(tournament);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(bar, BorderLayout.NORTH);
        body.add(tv, BorderLayout.CENTER);
        tv.load(loader);
        p.content(body);
        show(p);
    }

    static void pagePlayers() {
        Page p = new Page("Players", "Every player and the teams they are on. Double-click a player to see their teams.");
        TableView tv = new TableView().noun("players").opener(opens("PLAYER"))
                .empty("No players match your filters.", null, null);
        current = new Filters(true, "Only players with no team",
                () -> tv.load(() -> dl.findPlayers(fText(), fGame(), fFree())));
        p.button("Add an account", "plus", "primary", () -> {
            if (registerDialog(mainFrame, true) != null) refresh();
        });
        p.button("See connections", "open", "secondary", () -> openOrHint(tv, "player"));
        tv.load(() -> dl.findPlayers(fText(), fGame(), fFree()));
        p.content(withFilters(current, tv));
        show(p);
    }

    // =====================================================================
    // FIND (all roles): one search for teams, players and tournaments
    // =====================================================================

    // short names people type -> the real names
    static String synonyms(String q) {
        String s = q.trim().toLowerCase();
        switch (s) {
            case "lol": case "league": return "League of Legends";
            case "valo": case "val": return "Valorant";
            case "rl": case "rocket": return "Rocket League";
            default: return q.trim();
        }
    }

    static void pageFind(String query) {
        Page p = new Page("Find", "Type a name to find teams, players and tournaments. "
                + "Small typing mistakes are OK. Double-click a result to see its connections.");
        JTextField box = field(query);
        box.setColumns(30);
        TableView tv = new TableView().noun("results");
        tv.opener((row, headers) -> openDetails(row[0].toUpperCase(), Integer.parseInt(row[1])));
        Runnable search = () -> {
            String q = synonyms(box.getText());
            if (q.isEmpty()) {
                tv.empty("Type a name above to start.", null, null);
                ArrayList<String[]> none = new ArrayList<>();
                none.add(new String[] {"Type", "ID", "Name", "About"});
                tv.setData(none);
                return;
            }
            tv.empty("Nothing found for \"" + q + "\". Try a shorter word.", null, null);
            tv.load(() -> dl.searchEverything(q));
        };
        UiButton go = p.button("Search", "search", "primary", search);
        box.addActionListener(e -> search.run());
        p.actions.removeAll();
        p.actions.add(labelFor("Search for", box));
        p.actions.add(box);
        p.actions.add(go);
        p.content(tv);
        show(p);
        search.run();
        box.requestFocusInWindow();
    }

    // =====================================================================
    // REPORTS (all roles)
    // =====================================================================

    static int reportIndex = 0;

    static void pageReports() {
        Page p = new Page("Reports", "Pick a report. Choose a tournament or team to see less. "
                + "Double-click a row to see its connections.");
        String[] names = {"Standings", "Schedule and results", "Registrations", "Team rosters", "Player statistics"};
        TableView tv = new TableView().noun("rows").opener(opens(null)).empty("This report is empty.", null, null);
        JComboBox<String> pick = combo();
        JLabel pickLabel = labelFor("Tournament", pick);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        bar.setOpaque(false);
        bar.add(pickLabel);
        bar.add(pick);
        boolean[] filling = {false};
        Runnable loadReport = () -> {
            if (filling[0]) return;
            int id = pick.isVisible() && idOf(pick) != null ? Integer.parseInt(idOf(pick)) : 0;
            switch (reportIndex) {
                case 0: tv.noun("teams"); tv.load(() -> dl.reportStandings(id)); break;
                case 1: tv.noun("matches"); tv.opener(opens("MATCH")); tv.load(() -> dl.reportSchedule(id)); break;
                case 2: tv.noun("teams"); tv.load(() -> dl.reportRegistrations(id)); break;
                case 3: tv.noun("players"); tv.load(() -> dl.reportRosters(id)); break;
                default: tv.noun("players"); tv.load(() -> dl.reportPlayerStatistics(10));
            }
            if (reportIndex != 1) tv.opener(opens(null));
        };
        Runnable fillPick = () -> {
            filling[0] = true;
            try {
                List<String[]> o = new ArrayList<>();
                if (reportIndex <= 2) {
                    o.add(new String[] {"0", "All tournaments"});
                    o.addAll(dl.pickList("TOURNAMENT"));
                    pickLabel.setText("Tournament");
                } else if (reportIndex == 3) {
                    o.add(new String[] {"0", "All teams"});
                    o.addAll(dl.pickList("TEAM"));
                    pickLabel.setText("Team");
                }
                setOptions(pick, o, "0");
            } catch (SQLException e) {
                setOptions(pick, new ArrayList<>(), null);
            }
            pick.setVisible(reportIndex <= 3);
            pickLabel.setVisible(reportIndex <= 3);
            filling[0] = false;
        };
        pick.addActionListener(e -> loadReport.run());
        p.actions.add(segments(names, reportIndex, i -> {
            reportIndex = i;
            fillPick.run();
            loadReport.run();
        }));
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(bar, BorderLayout.NORTH);
        body.add(tv, BorderLayout.CENTER);
        fillPick.run();
        loadReport.run();
        p.content(body);
        show(p);
    }

    // =====================================================================
    // COACH: MY TEAMS, FIND PLAYERS
    // =====================================================================

    static int coachTeamIndex = 0;

    static void pageCoachTeams() {
        safely(() -> {
            ArrayList<String[]> teams = dl.teamsOfCoach(profileId);     // ID, Team, Game, Coach, Region, Players
            if (teams.size() <= 1) {
                Page p = new Page("My teams", "You do not coach a team yet.");
                p.button("Create a team", "plus", "primary", PresentationLayer::addTeam);
                p.content(stateBox("users", INFO, "No teams yet.", "Create your first team. Then add players to it.",
                        "Create a team", PresentationLayer::addTeam));
                show(p);
                return;
            }
            if (coachTeamIndex == Integer.MAX_VALUE) coachTeamIndex = teams.size() - 2;   // a team was just created
            if (coachTeamIndex >= teams.size() - 1) coachTeamIndex = 0;
            String[] team = teams.get(coachTeamIndex + 1);
            int teamId = Integer.parseInt(team[0]);
            Page p = new Page("My teams", team[1] + " plays " + team[2] + " and has " + team[5]
                    + " players. Pick a player in the list, or add a new one.");
            String[] names = new String[teams.size() - 1];
            for (int i = 1; i < teams.size(); i++) names[i - 1] = teams.get(i)[1];
            TableView tv = new TableView().noun("players").opener(opens(null))
                    .empty("No players on this team yet. Add your first player.", "Add a player",
                            () -> coachAddPlayer(teamId, 0));
            p.button("Add a player", "plus", "primary", () -> coachAddPlayer(teamId, 0));
            p.button("Remove from team", "trash", "danger-outline", () -> {
                String[] r = tv.selected();
                if (r == null) {
                    toast("info", "Pick a player in the list first, then try again.", null);
                } else if (!"current".equals(tv.value(r, "Left"))) {
                    toast("info", tv.value(r, "Gamer tag") + " already left this team.", null);
                } else {
                    coachRemovePlayer(teamId, team[1], Integer.parseInt(tv.value(r, "Player ID")), tv.value(r, "Gamer tag"));
                }
            });
            p.button("Enter a tournament", "trophy", "secondary", () -> coachRegister(teamId, 0));
            p.button("Leave a tournament", "x", "secondary", () -> coachDropOut(teamId, 0));
            p.button("Team details", "open", "secondary", () -> openDetails("TEAM", teamId));
            p.button("Create a team", "plus", "secondary", PresentationLayer::addTeam);
            JPanel body = new JPanel(new BorderLayout(0, 16));
            body.setOpaque(false);
            body.add(segments(names, coachTeamIndex, i -> {
                coachTeamIndex = i;
                refresh();
            }), BorderLayout.NORTH);
            body.add(tv, BorderLayout.CENTER);
            tv.load(() -> dl.teamPlayers(teamId));
            p.content(body);
            show(p);
        });
    }

    static void pageFindPlayers() {
        Page p = new Page("Find players", "Players who are not on your teams. Players with no team are first. "
                + "Pick one and add them to a team.");
        TableView tv = new TableView().noun("players").opener(opens("PLAYER"))
                .empty("No players match your filters.", null, null);
        current = new Filters(false, "Only players with no team",
                () -> tv.load(() -> dl.availablePlayersForCoach(profileId, fText(), fFree())));
        p.button("Add to my team", "plus", "primary", () -> coachAddPlayer(0, tv.selectedId("ID")));
        p.button("See connections", "open", "secondary", () -> openOrHint(tv, "player"));
        tv.load(() -> dl.availablePlayersForCoach(profileId, fText(), fFree()));
        p.content(withFilters(current, tv));
        show(p);
    }

    // =====================================================================
    // PLAYER: PROFILE, MY TEAMS, MY MATCHES, FIND A TEAM
    // =====================================================================

    static void pageProfile() {
        safely(() -> {
            ArrayList<String[]> t = dl.playerProfile(profileId);
            Page p = new Page("My profile", "Your details. You can change your email, country and password.");
            p.button("Edit my profile", "edit", "primary", PresentationLayer::editProfile);
            p.button("Change password", "key", "secondary", PresentationLayer::changePassword);
            Card c = new Card();
            c.setLayout(new GridLayout(0, 2, 24, 16));
            for (int i = 0; i < t.get(0).length; i++) {
                c.add(text(t.get(0)[i], 14, false, MUTED));
                String v = t.get(1)[i];
                c.add(text(isDate(v) ? friendlyDate(v) : v, 16, true, TEXT));
            }
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            c.setMaximumSize(new Dimension(640, 400));
            wrap.add(c, BorderLayout.NORTH);
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            left.setOpaque(false);
            left.add(c);
            p.content(left);
            show(p);
        });
    }

    static int playerTeamsTab = 0;

    static void pagePlayerTeams() {
        Page p = new Page("My teams", playerTeamsTab == 0
                ? "Your teams, now and before. Pick a team to leave it. Double-click a row to see more."
                : "Your teammates and the tournaments you played in. Double-click a row to see more.");
        TableView tv = new TableView().opener(opens(null));
        if (playerTeamsTab == 0) {
            tv.noun("teams").empty("You are not on a team yet. Look at 'Find a team'.", "Find a team",
                    () -> openNav("findteam"));
            p.button("Find a team", "search", "primary", () -> openNav("findteam"));
            p.button("Leave team", "logout", "danger-outline", () -> {
                String[] r = tv.selected();
                if (r == null) {
                    toast("info", "Pick a team in the list first, then try again.", null);
                } else if (!"current".equals(tv.value(r, "Left"))) {
                    toast("info", "You already left " + tv.value(r, "Team") + ".", null);
                } else {
                    playerLeaveTeam(Integer.parseInt(tv.value(r, "Team ID")), tv.value(r, "Team"));
                }
            });
            p.button("See team", "open", "secondary", () -> openOrHint(tv, "team"));
            tv.load(() -> dl.playerTeams(profileId));
        } else if (playerTeamsTab == 1) {
            tv.noun("teammates").empty("You have no teammates yet.", null, null);
            tv.load(() -> dl.teammates(profileId));
        } else {
            tv.noun("tournaments").empty("You have not played in a tournament yet.", null, null);
            tv.load(() -> dl.playerTournaments(profileId));
        }
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(segments(new String[] {"My teams", "My teammates", "My tournaments"}, playerTeamsTab, i -> {
            playerTeamsTab = i;
            refresh();
        }), BorderLayout.NORTH);
        body.add(tv, BorderLayout.CENTER);
        p.content(body);
        show(p);
    }

    static int playerMatchesTab = 0;

    static void pagePlayerMatches() {
        Page p = new Page("My matches", "All matches of your teams, and how you played in each one.");
        TableView tv = new TableView().opener(opens(null));
        Runnable load = () -> {
            if (playerMatchesTab == 0) {
                tv.noun("matches").empty("Your teams have no matches yet.", null, null);
                tv.load(() -> dl.playerMatches(profileId));
            } else {
                tv.noun("matches").empty("You have no statistics yet. They appear after your first match.", null, null);
                tv.load(() -> dl.playerStatsPerMatch(profileId));
            }
        };
        p.actions.add(segments(new String[] {"Schedule and results", "My statistics"}, playerMatchesTab, i -> {
            playerMatchesTab = i;
            load.run();
        }));
        load.run();
        p.content(tv);
        show(p);
    }

    static int findTeamTab = 0;

    static void pageFindTeam() {
        Page p = new Page("Find a team", findTeamTab == 0
                ? "Teams you are not on. Teams that play your game are first. Pick a team and click 'Ask to join'."
                : "Every request you sent. The coach of the team answers it. You can take back a request "
                  + "while it is waiting.");
        TableView tv = new TableView().opener(opens(findTeamTab == 0 ? "TEAM" : null));
        if (findTeamTab == 0) {
            tv.noun("teams").empty("You are already on every team.", null, null);
            p.button("Ask to join", "plus", "primary", () -> askToJoin(tv.selectedId("ID")));
            p.button("See team and coach", "open", "secondary", () -> openOrHint(tv, "team"));
            tv.load(() -> dl.otherTeamsForPlayer(profileId));
        } else {
            tv.noun("requests").empty("You have not asked to join a team yet.", "See teams", () -> {
                findTeamTab = 0;
                refresh();
            });
            p.button("Take back request", "x", "danger-outline", () -> {
                String[] r = tv.selected();
                if (r == null) {
                    toast("info", "Pick a request in the list first, then try again.", null);
                } else {
                    cancelMyRequest(Integer.parseInt(tv.value(r, "Request ID")), tv.value(r, "Team"),
                            tv.value(r, "Status"));
                }
            });
            p.button("See team and coach", "open", "secondary", () -> openOrHint(tv, "request"));
            tv.load(() -> dl.myRequests(profileId));
        }
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(segments(new String[] {"Teams I could join", "My requests"}, findTeamTab, i -> {
            findTeamTab = i;
            refresh();
        }), BorderLayout.NORTH);
        body.add(tv, BorderLayout.CENTER);
        p.content(body);
        show(p);
    }

    // =====================================================================
    // COACH: JOIN REQUESTS
    // =====================================================================

    static int requestsTab = 0;

    static void pageCoachRequests() {
        Page p = new Page("Join requests", "Players who asked to join your teams. Pick a request, then Approve or "
                + "Decline. Double-click to see the player.");
        TableView tv = new TableView().noun("requests").opener(opens(null))
                .empty(requestsTab == 0 ? "No requests are waiting. You are all caught up." : "No requests yet.",
                        null, null);
        Runnable needPick = () -> toast("info", "Pick a request in the list first, then try again.", null);
        p.button("Approve", "check", "primary", () -> {
            int id = tv.selectedId("Request ID");
            if (id == 0) needPick.run(); else coachApprove(id);
        });
        p.button("Decline", "x", "danger-outline", () -> {
            int id = tv.selectedId("Request ID");
            if (id == 0) needPick.run(); else coachDecline(id);
        });
        p.button("See player", "open", "secondary", () -> openOrHint(tv, "request"));
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(segments(new String[] {"Waiting for me", "All requests"}, requestsTab, i -> {
            requestsTab = i;
            refresh();
        }), BorderLayout.NORTH);
        body.add(tv, BorderLayout.CENTER);
        tv.load(() -> dl.requestsForCoach(profileId, requestsTab == 0));
        p.content(body);
        show(p);
    }

    // =====================================================================
    // DETAILS: one team / player / tournament / match and everything connected to it
    // =====================================================================

    static Map<String, Integer> detailsTab = new HashMap<>();

    static void pageDetails(String type, int id) {
        safely(() -> {
            String[] d = dl.describe(type, id);
            if (d == null) {
                goBack();
                toast("error", "We could not find this any more. It may have been deleted.", null);
                return;
            }
            String title;
            String summary;
            String[] tabs;
            Loader[] loaders;
            String[] nouns;
            Page p;
            boolean director = DataLayer.DIRECTOR.equals(role);
            boolean coach = DataLayer.COACH.equals(role);
            Card coachCard = null;
            switch (type) {
                case "TEAM":
                    title = d[0];
                    summary = d[0] + " is a " + d[1] + " team from " + d[2] + ". Coach: " + d[3] + ". "
                            + d[4] + " players right now.";
                    tabs = new String[] {"Players", "Tournaments", "Matches"};
                    nouns = new String[] {"players", "tournaments", "matches"};
                    loaders = new Loader[] {() -> dl.teamPlayers(id), () -> dl.teamTournaments(id), () -> dl.teamMatches(id)};
                    p = new Page(title, summary);
                    if (director) {
                        p.button("Register for a tournament", "trophy", "primary", () -> registerTeam(0, id));
                        p.button("Remove from a tournament", "x", "secondary", () -> directorRemoveTeam(0, id));
                    } else if (coach && dl.coachOwnsTeam(profileId, id)) {
                        p.button("Add a player", "plus", "primary", () -> coachAddPlayer(id, 0));
                        p.button("Leave a tournament", "x", "secondary", () -> coachDropOut(id, 0));
                    } else if (!coach && dl.currentRole(id, profileId) == null) {
                        p.button("Ask to join", "plus", "primary", () -> askToJoin(id));
                    } else if (!coach) {
                        p.button("Leave this team", "logout", "danger-outline", () -> playerLeaveTeam(id, d[0]));
                    }
                    coachCard = coachCard(id);
                    break;
                case "PLAYER":
                    title = d[0];
                    summary = d[0] + " is " + d[1] + " from " + d[2] + ", on " + d[3]
                            + ("1".equals(d[3]) ? " team" : " teams") + " right now.";
                    tabs = new String[] {"Teams", "Tournaments", "Match statistics"};
                    nouns = new String[] {"teams", "tournaments", "matches"};
                    loaders = new Loader[] {() -> dl.playerTeams(id), () -> dl.playerTournaments(id),
                            () -> dl.playerStatsPerMatch(id)};
                    p = new Page(title, summary);
                    if (coach) {
                        p.button("Add to my team", "plus", "primary", () -> coachAddPlayer(0, id));
                    }
                    break;
                case "TOURNAMENT":
                    title = d[0];
                    summary = "A " + d[1] + " tournament at " + d[2] + ", from " + friendlyDate(d[3]) + " to "
                            + friendlyDate(d[4]) + ". It is " + cellText(d[5], "Status").toLowerCase() + ".";
                    tabs = new String[] {"Teams", "Players", "Matches"};
                    nouns = new String[] {"teams", "players", "matches"};
                    loaders = new Loader[] {() -> dl.tournamentTeams(id), () -> dl.tournamentPlayers(id),
                            () -> dl.reportSchedule(id)};
                    p = new Page(title, summary);
                    if (director) {
                        p.button("Register a team", "users", "primary", () -> registerTeam(id, 0));
                        if (!"COMPLETED".equals(d[5])) {
                            p.button("Remove a team", "x", "secondary", () -> directorRemoveTeam(id, 0));
                        }
                        p.button("Edit", "edit", "secondary", () -> editTournament(id));
                        p.button("Delete", "trash", "danger-outline", () -> deleteTournament(id));
                    } else if (coach && !"COMPLETED".equals(d[5])) {
                        if ("UPCOMING".equals(d[5])) {
                            p.button("Enter my team", "plus", "primary", () -> coachRegister(0, id));
                        }
                        p.button("Drop out", "x", "danger-outline", () -> coachDropOut(0, id));
                    }
                    break;
                default:  // MATCH
                    title = d[0] + " vs " + d[1];
                    summary = d[2] + " in " + d[3] + ", " + friendlyDate(d[4]) + ". "
                            + ("COMPLETED".equals(d[5]) ? "Final score " + d[6] + " : " + d[7] + "."
                                                        : "Waiting for a result.");
                    tabs = new String[] {"Players"};
                    nouns = new String[] {"players"};
                    loaders = new Loader[] {() -> dl.matchPlayers(id)};
                    p = new Page(title, summary);
                    if (director && "SCHEDULED".equals(d[5])) {
                        p.button("Record result", "check", "primary", () -> recordResult(id));
                    }
            }
            String key = type;
            int tab = Math.min(detailsTab.getOrDefault(key, 0), tabs.length - 1);
            TableView tv = new TableView().opener(opens("MATCH".equals(type) || "TOURNAMENT".equals(type) && tab == 2
                    ? "MATCH" : null));
            Runnable load = () -> {
                int t = Math.min(detailsTab.getOrDefault(key, 0), tabs.length - 1);
                tv.noun(nouns[t]).empty("Nothing here yet.", null, null);
                tv.opener(opens("TOURNAMENT".equals(type) && t == 2 ? "MATCH" : null));
                tv.load(loaders[t]);
            };
            JPanel body = new JPanel(new BorderLayout(0, 16));
            body.setOpaque(false);
            JPanel north = new JPanel(new BorderLayout(0, 16));
            north.setOpaque(false);
            if (coachCard != null) {
                north.add(coachCard, BorderLayout.NORTH);
            }
            if (tabs.length > 1) {
                north.add(segments(tabs, tab, i -> {
                    detailsTab.put(key, i);
                    load.run();
                }), BorderLayout.CENTER);
            }
            body.add(north, BorderLayout.NORTH);
            body.add(tv, BorderLayout.CENTER);
            load.run();
            p.content(body);
            show(p);
        });
    }

    // =====================================================================
    // INPUT CHECKS (return a message, or null when the input is fine)
    // =====================================================================

    static String checkNeed(JTextField f, String what) {
        return f.getText().trim().isEmpty() ? "Please type " + what + "." : null;
    }

    static String checkWhole(JTextField f, String what, int min, int max) {
        try {
            int n = Integer.parseInt(f.getText().trim());
            return n < min || n > max ? what + " must be from " + min + " to " + max + "." : null;
        } catch (NumberFormatException e) {
            return "Please type a whole number for " + what.toLowerCase() + ", like " + Math.max(min, 1) + ".";
        }
    }

    static String checkDate(JTextField f) {
        try {
            LocalDate.parse(f.getText().trim());
            return null;
        } catch (RuntimeException e) {
            return "Please type a date like 2027-03-15 (year-month-day).";
        }
    }

    static String checkMoney(JTextField f) {
        try {
            return new BigDecimal(f.getText().trim()).signum() < 0 ? "The prize can't be less than 0." : null;
        } catch (NumberFormatException e) {
            return "Please type an amount like 5000 or 2500.50.";
        }
    }

    static String checkEmail(JTextField f) {
        return f.getText().trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") ? null
                : "Please type an email like name@example.com.";
    }

    static int whole(JTextField f) {
        return Integer.parseInt(f.getText().trim());
    }

    static LocalDate day(JTextField f) {
        return LocalDate.parse(f.getText().trim());
    }

    // a change that can be undone: green message with an Undo button
    static void undoable(String message, Submit undo, String undoneMessage) {
        refresh();
        toast("success", message, () -> {
            try {
                undo.run();
                refresh();
                toast("info", undoneMessage, null);
            } catch (SQLException e) {
                toast("error", "We could not undo that. " + friendly(e), null);
            }
        });
    }

    // a small form to choose one item when nothing was picked in the list
    static int pickOne(String title, String question, List<String[]> options) {
        if (options.isEmpty()) {
            toast("info", "There is nothing to choose from yet.", null);
            return 0;
        }
        Form f = new Form(mainFrame, title, null);
        JComboBox<String> box = f.dropdown(question, options, null, null);
        int[] chosen = {0};
        f.open("Next", false, () -> chosen[0] = Integer.parseInt(idOf(box)));
        return chosen[0];
    }

    static List<String[]> tournamentsWithStatus(String... statuses) throws SQLException {
        ArrayList<String[]> all = dl.listTournaments();
        List<String> wanted = Arrays.asList(statuses);
        List<String[]> out = new ArrayList<>();
        for (int i = 1; i < all.size(); i++) {
            String[] r = all.get(i);
            if (wanted.contains(r[r.length - 1])) {
                out.add(new String[] {r[0], r[1]});
            }
        }
        return out;
    }

    // =====================================================================
    // CREATE AN ACCOUNT
    // From the login window: Player or Coach. Director: any role. Returns the username or null.
    // =====================================================================

    static String registerDialog(Window owner, boolean allowDirector) {
        Form f = new Form(owner, "Create an account",
                "Passwords are saved as secret codes, never as plain text. Fields marked (optional) can stay empty.");
        String[] values = allowDirector ? new String[] {"PLAYER", "COACH", "DIRECTOR"} : new String[] {"PLAYER", "COACH"};
        String[] shown = allowDirector ? new String[] {"Player", "Coach", "Director"} : new String[] {"Player", "Coach"};
        Radio kind = f.radios("I am a", values, shown, "PLAYER");
        JTextField user = f.text("Username", "", "3 to 30 letters or numbers. You use it to log in.");
        JPasswordField pw1 = f.password("Password", "At least 6 characters.");
        JPasswordField pw2 = f.password("Type the password again", null);
        JTextField email = f.text("Email", "", "Like name@example.com");
        JTextField first = f.text("First name", "", null);
        JTextField last = f.text("Last name", "", null);
        JTextField tag = f.text("Gamer tag", "", "The name other players see.");
        JTextField born = f.text("Date of birth", "", "Like 2005-04-20 (year-month-day)");
        JTextField country = f.text("Country", "", null);
        JTextField phone = f.text("Phone (optional)", "", null);
        JTextField years = f.text("Years of coaching", "0", "0 to 60");

        Runnable byRole = () -> {
            boolean player = "PLAYER".equals(kind.value());
            f.show(tag, player);
            f.show(born, player);
            f.show(country, player);
            f.show(phone, !player);
            f.show(years, "COACH".equals(kind.value()));
        };
        kind.onChange(byRole);
        byRole.run();

        f.rule(user, () -> {
            String u = user.getText().trim();
            if (!u.matches("[A-Za-z0-9_.]{3,30}")) return "Use 3 to 30 letters, numbers, '_' or '.'.";
            try {
                return dl.accountValueExists("username", u) ? "Someone already has the username " + u + ". Try another one." : null;
            } catch (SQLException e) {
                return null;
            }
        });
        f.rule(pw1, () -> pw1.getPassword().length < 6 ? "Please use at least 6 characters." : null);
        f.rule(pw2, () -> Arrays.equals(pw1.getPassword(), pw2.getPassword()) ? null
                : "The two passwords are different. Please type the same password twice.");
        f.rule(email, () -> {
            String m = checkEmail(email);
            if (m != null) return m;
            try {
                return dl.accountValueExists("email", email.getText().trim()) ? "This email already has an account." : null;
            } catch (SQLException e) {
                return null;
            }
        });
        f.rule(first, () -> checkNeed(first, "your first name"));
        f.rule(last, () -> checkNeed(last, "your last name"));
        f.rule(tag, () -> {
            String m = checkNeed(tag, "a gamer tag");
            if (m != null) return m;
            try {
                return dl.gamerTagExists(tag.getText().trim()) ? "That gamer tag is taken. Try another one." : null;
            } catch (SQLException e) {
                return null;
            }
        });
        f.rule(born, () -> {
            String m = checkDate(born);
            if (m != null) return m;
            return day(born).isBefore(LocalDate.now()) ? null : "The date of birth must be in the past.";
        });
        f.rule(country, () -> checkNeed(country, "your country"));
        f.rule(years, () -> checkWhole(years, "Years of coaching", 0, 60));

        String[] made = {null};
        f.open("Create account", false, () -> {
            String r = kind.value();
            boolean player = "PLAYER".equals(r);
            dl.createAccount(user.getText().trim(), new String(pw1.getPassword()), r, email.getText().trim(),
                    first.getText().trim(), last.getText().trim(),
                    player ? tag.getText().trim() : phone.getText().trim(),
                    player ? born.getText().trim() : years.getText().trim(),
                    player ? country.getText().trim() : null);
            made[0] = user.getText().trim();
        });
        if (made[0] != null && mainFrame != null && mainFrame.isVisible()) {
            toast("success", "Account " + made[0] + " is ready. It can log in now.", null);
        }
        return made[0];
    }

    // =====================================================================
    // DIRECTOR: TOURNAMENTS
    // =====================================================================

    static void addTournament() {
        List<String[]> games;
        try {
            games = dl.pickList("GAME");
        } catch (SQLException e) {
            toast("error", friendly(e), null);
            return;
        }
        String[] gameIds = new String[games.size()];
        String[] gameNames = new String[games.size()];
        for (int i = 0; i < games.size(); i++) {
            gameIds[i] = games.get(i)[0];
            gameNames[i] = games.get(i)[1];
        }
        LocalDate start = LocalDate.now().plusDays(30);
        Form f = new Form(mainFrame, "Add a tournament", "Good starting values are filled in. Change what you need.");
        JTextField name = f.text("Tournament name", "", "Like: Dubai Spring Cup 2027");
        Radio game = f.radios("Game", gameIds, gameNames, null);
        JTextField from = f.text("Start date", start.toString(), "Like 2027-03-15 (year-month-day)");
        JTextField to = f.text("End date", start.plusDays(2).toString(), "Like 2027-03-17");
        JTextField place = f.text("Place", "", "Like: Dubai Media City Arena, or Online");
        JTextField prize = f.text("Prize money in AED", "5000", null);
        JTextField max = f.text("Most teams that can join", "8", "2 to 64");
        f.rule(name, () -> checkNeed(name, "a name"));
        f.rule(from, () -> checkDate(from));
        f.rule(to, () -> {
            String m = checkDate(to);
            if (m != null) return m;
            return checkDate(from) == null && day(to).isBefore(day(from)) ? "The end date can't be before the start date." : null;
        });
        f.rule(place, () -> checkNeed(place, "a place"));
        f.rule(prize, () -> checkMoney(prize));
        f.rule(max, () -> checkWhole(max, "Most teams", 2, 64));
        int[] id = {0};
        if (f.open("Add tournament", false, () -> id[0] = dl.addTournament(name.getText().trim(),
                Integer.parseInt(game.value()), profileId, from.getText().trim(), to.getText().trim(),
                place.getText().trim(), new BigDecimal(prize.getText().trim()), whole(max)))) {
            undoable("Tournament added: " + name.getText().trim() + ".", () -> dl.deleteTournament(id[0]),
                    "The new tournament was removed again.");
        }
    }

    static void editTournament(int id) {
        try {
            if (id == 0) {
                id = pickOne("Edit a tournament", "Which tournament?", dl.pickList("TOURNAMENT"));
                if (id == 0) return;
            }
            String[] t = dl.getTournament(id);    // name, start, end, location, prize, max teams, status
            if (t == null) {
                toast("error", "This tournament does not exist any more.", null);
                return;
            }
            final int tid = id;
            Form f = new Form(mainFrame, "Edit tournament", t[0]);
            JTextField name = f.text("Tournament name", t[0], null);
            JTextField from = f.text("Start date", t[1], "Like 2027-03-15 (year-month-day)");
            JTextField to = f.text("End date", t[2], null);
            JTextField place = f.text("Place", t[3], null);
            JTextField prize = f.text("Prize money in AED", new BigDecimal(t[4]).stripTrailingZeros().toPlainString(), null);
            JTextField max = f.text("Most teams that can join", t[5], "2 to 64");
            Radio status = f.radios("Status", new String[] {"UPCOMING", "ONGOING", "COMPLETED"},
                    new String[] {"Upcoming", "Ongoing", "Completed"}, t[6]);
            f.rule(name, () -> checkNeed(name, "a name"));
            f.rule(from, () -> checkDate(from));
            f.rule(to, () -> {
                String m = checkDate(to);
                if (m != null) return m;
                return checkDate(from) == null && day(to).isBefore(day(from)) ? "The end date can't be before the start date." : null;
            });
            f.rule(place, () -> checkNeed(place, "a place"));
            f.rule(prize, () -> checkMoney(prize));
            f.rule(max, () -> checkWhole(max, "Most teams", 2, 64));
            if (f.open("Save changes", false, () -> dl.updateTournament(tid, name.getText().trim(), from.getText().trim(),
                    to.getText().trim(), place.getText().trim(), new BigDecimal(prize.getText().trim()), whole(max),
                    status.value()))) {
                undoable("Changes saved.", () -> dl.updateTournament(tid, t[0], t[1], t[2], t[3], new BigDecimal(t[4]),
                        Integer.parseInt(t[5]), t[6]), "The old details are back.");
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    static void deleteTournament(int id) {
        try {
            if (id == 0) {
                id = pickOne("Delete a tournament", "Which tournament?", dl.pickList("TOURNAMENT"));
                if (id == 0) return;
            }
            String[] d = dl.describe("TOURNAMENT", id);
            int[] size = dl.tournamentDeleteSize(id);
            if (d == null) return;
            boolean yes = confirm("Delete this tournament?",
                    "This deletes " + d[0] + " with its " + size[0] + " team places, " + size[1] + " matches and "
                    + size[2] + " player statistics. You can't undo this.", "Delete", "Keep it", true);
            if (yes && dl.deleteTournament(id)) {
                if (trail.size() > 1) {
                    goBack();
                } else {
                    refresh();
                }
                toast("success", "Tournament deleted: " + d[0] + ".", null);
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // register a team (stored procedure). tournamentId or teamId may be 0 = choose in the form.
    static void registerTeam(int tournamentId, int teamId) {
        try {
            Form f;
            JComboBox<String> tBox;
            JComboBox<String> teamBox;
            if (teamId != 0) {
                List<String[]> tours = dl.tournamentsForTeam(teamId);
                String[] team = dl.describe("TEAM", teamId);
                if (tours.isEmpty()) {
                    toast("info", team[0] + " can't enter a tournament right now. It is already in every upcoming "
                            + team[1] + " tournament.", null);
                    return;
                }
                f = new Form(mainFrame, "Register " + team[0], "Only upcoming " + team[1]
                        + " tournaments that this team has not entered are shown.");
                tBox = f.dropdown("Tournament", tours, null, null);
                teamBox = combo();
                setOptions(teamBox, Collections.singletonList(new String[] {String.valueOf(teamId), team[0]}), null);
            } else {
                List<String[]> tours = tournamentsWithStatus("UPCOMING");
                if (tours.isEmpty()) {
                    toast("info", "There is no upcoming tournament. Add one first.", null);
                    return;
                }
                f = new Form(mainFrame, "Register a team", "Teams join tournaments of their own game.");
                tBox = f.dropdown("Tournament", tours, String.valueOf(tournamentId), null);
                teamBox = f.dropdown("Team", new ArrayList<>(), null, "Only teams of this game that are not in it yet.");
                JComboBox<String> tb = tBox;
                JComboBox<String> mb = teamBox;
                Runnable fill = () -> {
                    try {
                        setOptions(mb, dl.teamsForTournament(Integer.parseInt(idOf(tb))), null);
                        f.mark(mb, mb.getItemCount() == 0 ? "No team can join: every team of this game is already in." : null);
                    } catch (SQLException e) {
                        f.mark(mb, friendly(e));
                    }
                };
                tBox.addActionListener(e -> fill.run());
                fill.run();
                f.rule(teamBox, () -> mb.getItemCount() == 0 ? "No team can join: every team of this game is already in." : null);
            }
            JComboBox<String> tb = tBox;
            JComboBox<String> mb = teamBox;
            String[] message = {null};
            int[] ids = new int[2];
            if (f.open("Register team", false, () -> {
                ids[0] = Integer.parseInt(idOf(tb));
                ids[1] = Integer.parseInt(idOf(mb));
                String m = dl.registerTeam(ids[0], ids[1]);          // stored procedure (CallableStatement)
                if (!succeeded(m)) {
                    throw new Problem(mb.isShowing() ? mb : null, procedureText(m));
                }
                message[0] = procedureText(m);
            })) {
                undoable(message[0], () -> dl.unregisterTeam(ids[0], ids[1]), "The team is no longer registered.");
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // =====================================================================
    // DIRECTOR: MATCHES
    // =====================================================================

    static void scheduleMatch() {
        try {
            List<String[]> tours = tournamentsWithStatus("UPCOMING", "ONGOING");
            if (tours.isEmpty()) {
                toast("info", "There is no upcoming or running tournament to plan a match in.", null);
                return;
            }
            Form f = new Form(mainFrame, "Schedule a match", "Both teams must be in the tournament.");
            JComboBox<String> tour = f.dropdown("Tournament", tours, null, null);
            JComboBox<String> home = f.dropdown("Home team", new ArrayList<>(), null, null);
            JComboBox<String> away = f.dropdown("Away team", new ArrayList<>(), null, null);
            JTextField round = f.text("Round", "Group stage", "Like: Group stage, Semi-final, Final");
            JTextField when = f.text("Date and time", LocalDate.now().plusDays(1) + " 18:00",
                    "Like 2027-02-10 15:00 (24-hour clock)");
            JTextField venue = f.text("Place", "", null);
            Runnable fill = () -> {
                try {
                    int t = Integer.parseInt(idOf(tour));
                    List<String[]> teams = options(dl.tournamentTeams(t), "Team ID", "Team");
                    setOptions(home, teams, null);
                    setOptions(away, teams, teams.size() > 1 ? teams.get(1)[0] : null);
                    String[] d = dl.describe("TOURNAMENT", t);
                    venue.setText(d == null ? "" : d[2]);
                    f.mark(home, teams.size() < 2 ? "This tournament needs at least 2 teams first." : null);
                } catch (SQLException e) {
                    f.mark(home, friendly(e));
                }
            };
            tour.addActionListener(e -> fill.run());
            fill.run();
            f.rule(home, () -> home.getItemCount() < 2 ? "This tournament needs at least 2 teams first." : null);
            f.rule(away, () -> Objects.equals(idOf(home), idOf(away)) ? "A team can't play against itself. Pick another team." : null);
            f.rule(round, () -> checkNeed(round, "a round name"));
            f.rule(when, () -> {
                try {
                    LocalDateTime.parse(when.getText().trim(), ISO_MINUTE);
                    return null;
                } catch (RuntimeException e) {
                    return "Please type a date and time like 2027-02-10 15:00.";
                }
            });
            f.rule(venue, () -> checkNeed(venue, "a place"));
            int[] id = {0};
            if (f.open("Schedule match", false, () -> id[0] = dl.scheduleMatch(Integer.parseInt(idOf(tour)),
                    Integer.parseInt(idOf(home)), Integer.parseInt(idOf(away)), round.getText().trim(),
                    LocalDateTime.parse(when.getText().trim(), ISO_MINUTE).format(ISO_MINUTE), venue.getText().trim()))) {
                undoable("Match planned for " + friendlyDate(when.getText().trim()) + ".",
                        () -> dl.deleteScheduledMatch(id[0]), "The match was removed again.");
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // record a result (stored procedure). matchId 0 = choose in the form.
    static void recordResult(int matchId) {
        try {
            ArrayList<String[]> waiting = dl.scheduledMatches();     // ID, Tournament, Round, Date/time, Home, Away
            if (waiting.size() <= 1) {
                toast("info", "No matches are waiting for a result.", null);
                return;
            }
            Map<String, String[]> teams = new HashMap<>();
            for (int i = 1; i < waiting.size(); i++) {
                teams.put(waiting.get(i)[0], new String[] {waiting.get(i)[4], waiting.get(i)[5]});
            }
            Form f = new Form(mainFrame, "Record a result", "Draws are not allowed. One team must win.");
            JComboBox<String> match = f.dropdown("Match", options(waiting, "ID", "Home", "Away", "Date/time"),
                    String.valueOf(matchId), null);
            JTextField homeScore = f.text("Home score", "", "0 to 99");
            JTextField awayScore = f.text("Away score", "", "0 to 99");
            Runnable names = () -> {
                String[] t = teams.get(idOf(match));
                if (t != null) {
                    f.setLabel(homeScore, t[0] + " score");
                    f.setLabel(awayScore, t[1] + " score");
                }
            };
            match.addActionListener(e -> names.run());
            names.run();
            f.rule(homeScore, () -> checkWhole(homeScore, "The score", 0, 99));
            f.rule(awayScore, () -> {
                String m = checkWhole(awayScore, "The score", 0, 99);
                if (m != null) return m;
                return checkWhole(homeScore, "x", 0, 99) == null && whole(homeScore) == whole(awayScore)
                        ? "Draws are not allowed. One team must win." : null;
            });
            int[] id = {0};
            String[] message = {null};
            if (f.open("Save result", false, () -> {
                id[0] = Integer.parseInt(idOf(match));
                String m = dl.recordMatchResult(id[0], whole(homeScore), whole(awayScore));   // stored procedure
                if (!succeeded(m)) {
                    throw new Problem(null, procedureText(m));
                }
                String[] t = teams.get(idOf(match));
                String winner = whole(homeScore) > whole(awayScore) ? t[0] : t[1];
                message[0] = "Result saved: " + winner + " won " + Math.max(whole(homeScore), whole(awayScore))
                        + " : " + Math.min(whole(homeScore), whole(awayScore)) + ".";
            })) {
                undoable(message[0], () -> dl.undoMatchResult(id[0]), "The result was removed. The match is waiting again.");
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // =====================================================================
    // COACH: ADD / REMOVE PLAYERS, ENTER TOURNAMENTS
    // =====================================================================

    static Radio myTeamsRadio(Form f, int teamId) throws SQLException {
        ArrayList<String[]> teams = dl.teamsOfCoach(profileId);
        String[] values = new String[teams.size() - 1];
        String[] shown = new String[teams.size() - 1];
        for (int i = 1; i < teams.size(); i++) {
            values[i - 1] = teams.get(i)[0];
            shown[i - 1] = teams.get(i)[1];
        }
        return f.radios("Team", values, shown, teamId == 0 ? null : String.valueOf(teamId));
    }

    // add a player (stored procedure)
    static void coachAddPlayer(int teamId, int playerId) {
        try {
            if (dl.teamsOfCoach(profileId).size() <= 1) {
                toast("info", "You do not coach a team yet.", null);
                return;
            }
            ArrayList<String[]> free = dl.availablePlayersForCoach(profileId, "", false);
            List<String[]> players = new ArrayList<>();
            for (int i = 1; i < free.size(); i++) {
                String[] r = free.get(i);     // ID, Gamer tag, Name, Country, Current teams, ...
                players.add(new String[] {r[0], r[1] + "  ·  " + r[2] + "  ·  "
                        + ("-".equals(r[4]) ? "No team" : "On " + r[4])});
            }
            if (players.isEmpty()) {
                toast("info", "Every player is already on one of your teams.", null);
                return;
            }
            Form f = new Form(mainFrame, "Add a player", "Players with no team are at the top of the list.");
            Radio team = myTeamsRadio(f, teamId);
            players.sort((a, b) -> Boolean.compare(!a[1].endsWith("No team"), !b[1].endsWith("No team")));
            JComboBox<String> player = f.dropdown("Player", players, String.valueOf(playerId), null);
            Radio teamRole = f.radios("Role on the team", new String[] {"STARTER", "CAPTAIN", "SUBSTITUTE"},
                    new String[] {"Starter", "Captain", "Substitute"}, "STARTER");
            int[] ids = new int[2];
            String[] message = {null};
            if (f.open("Add player", false, () -> {
                ids[0] = Integer.parseInt(team.value());
                ids[1] = Integer.parseInt(idOf(player));
                String m = dl.addPlayerToTeam(ids[0], ids[1], teamRole.value());    // stored procedure
                if (!succeeded(m)) {
                    throw new Problem(player, procedureText(m));
                }
                message[0] = player.getSelectedItem().toString().split("  ·  ")[0] + " joined " + team.shownValue() + ".";
            })) {
                undoable(message[0], () -> dl.removePlayerFromTeam(ids[0], ids[1]), "The player was taken off the team again.");
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    static void coachRemovePlayer(int teamId, String teamName, int playerId, String tag) {
        if (!confirm("Remove " + tag + " from " + teamName + "?",
                tag + " stays in the team's history. You can undo this right after.", "Remove", "Keep them", true)) {
            return;
        }
        try {
            String oldRole = dl.currentRole(teamId, playerId);
            if (dl.removePlayerFromTeam(teamId, playerId)) {
                undoable(tag + " left " + teamName + ".", () -> {
                    String m = dl.addPlayerToTeam(teamId, playerId, oldRole == null ? "STARTER" : oldRole);
                    if (!succeeded(m)) throw new SQLException(procedureText(m));
                }, tag + " is back on " + teamName + ".");
            } else {
                toast("info", tag + " is not on this team any more.", null);
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // enter a team in a tournament (stored procedure)
    static void coachRegister(int teamId, int tournamentId) {
        try {
            if (dl.teamsOfCoach(profileId).size() <= 1) {
                toast("info", "You do not coach a team yet.", null);
                return;
            }
            Form f = new Form(mainFrame, "Enter a tournament",
                    "Only upcoming tournaments of your team's game that it has not entered are shown.");
            Radio team = myTeamsRadio(f, teamId);
            JComboBox<String> tour = f.dropdown("Tournament", new ArrayList<>(), null, null);
            Runnable fill = () -> {
                try {
                    setOptions(tour, dl.tournamentsForTeam(Integer.parseInt(team.value())), String.valueOf(tournamentId));
                    f.mark(tour, tour.getItemCount() == 0 ? "This team is already in every upcoming tournament of its game." : null);
                } catch (SQLException e) {
                    f.mark(tour, friendly(e));
                }
            };
            team.onChange(fill);
            fill.run();
            f.rule(tour, () -> tour.getItemCount() == 0 ? "This team is already in every upcoming tournament of its game." : null);
            int[] ids = new int[2];
            String[] message = {null};
            if (f.open("Enter tournament", false, () -> {
                ids[0] = Integer.parseInt(idOf(tour));
                ids[1] = Integer.parseInt(team.value());
                String m = dl.registerTeam(ids[0], ids[1]);         // stored procedure
                if (!succeeded(m)) {
                    throw new Problem(tour, procedureText(m));
                }
                message[0] = team.shownValue() + " is in " + tour.getSelectedItem() + ".";
            })) {
                undoable(message[0], () -> dl.unregisterTeam(ids[0], ids[1]), "Your team left the tournament again.");
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // =====================================================================
    // CREATE A TEAM: the director picks the coach; a coach creates a team they coach
    // =====================================================================

    static void addTeam() {
        boolean director = DataLayer.DIRECTOR.equals(role);
        List<String[]> games;
        List<String[]> coaches;
        try {
            games = dl.pickList("GAME");
            coaches = director ? dl.pickList("COACH") : null;
        } catch (SQLException e) {
            toast("error", friendly(e), null);
            return;
        }
        if (director && coaches.isEmpty()) {
            toast("info", "There is no coach yet. Add a coach account first (Players > Add an account).", null);
            return;
        }
        String[] gameIds = new String[games.size()];
        String[] gameNames = new String[games.size()];
        for (int i = 0; i < games.size(); i++) {
            gameIds[i] = games.get(i)[0];
            gameNames[i] = games.get(i)[1];
        }
        Form f = new Form(mainFrame, director ? "Add a team" : "Create a team", director
                ? "Every team plays one game and has one coach."
                : "You will be the coach of the new team. Then add players to it.");
        JTextField name = f.text("Team name", "", "Like: Desert Falcons. Every team name is different.");
        Radio game = f.radios("Game", gameIds, gameNames, null);
        JComboBox<String> coach = director ? f.dropdown("Coach", coaches, null, null) : null;
        JTextField region = f.text("Region", "MENA", "Like: MENA, Europe, Asia");
        JTextField founded = f.text("Started on", LocalDate.now().toString(), "Like 2026-09-01 (year-month-day)");
        f.rule(name, () -> {
            String m = checkNeed(name, "a team name");
            if (m != null) return m;
            return name.getText().trim().length() > 50 ? "Please use 50 letters or fewer." : null;
        });
        f.rule(region, () -> {
            String m = checkNeed(region, "a region");
            if (m != null) return m;
            return region.getText().trim().length() > 30 ? "Please use 30 letters or fewer." : null;
        });
        f.rule(founded, () -> {
            String m = checkDate(founded);
            if (m != null) return m;
            return day(founded).isAfter(LocalDate.now()) ? "The start date can't be in the future." : null;
        });
        int[] id = {0};
        if (f.open(director ? "Add team" : "Create team", false, () -> {
            String n = name.getText().trim();
            if (dl.teamNameExists(n)) {
                throw new Problem(name, "There is already a team called " + n + ". Please pick another name.");
            }
            int coachId = director ? Integer.parseInt(idOf(coach)) : profileId;
            id[0] = dl.addTeam(n, Integer.parseInt(game.value()), coachId, region.getText().trim(),
                    founded.getText().trim());
        })) {
            if (!director) {
                coachTeamIndex = Integer.MAX_VALUE;           // show the new team (it is last in the list)
            }
            undoable(name.getText().trim() + " was created. Next, add players to it.",
                    () -> dl.deleteNewTeam(id[0]), "The new team was removed again.");
        }
    }

    // =====================================================================
    // PLAYER: LEAVE A TEAM
    // =====================================================================

    static void playerLeaveTeam(int teamId, String teamName) {
        if (!confirm("Leave " + teamName + "?", "You stay in the team's history and keep your match statistics. "
                + "To come back later, ask to join again. You can undo this right after.", "Leave team",
                "Stay on the team", true)) {
            return;
        }
        try {
            String oldRole = dl.currentRole(teamId, profileId);
            if (dl.removePlayerFromTeam(teamId, profileId)) {     // sets the leave date to today
                undoable("You left " + teamName + ".", () -> {
                    String m = dl.addPlayerToTeam(teamId, profileId, oldRole == null ? "STARTER" : oldRole);
                    if (!succeeded(m)) throw new SQLException(procedureText(m));
                }, "You are back on " + teamName + ".");
            } else {
                toast("info", "You are not on " + teamName + " any more.", null);
                refresh();
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // =====================================================================
    // TEAM DETAILS: who coaches this team (so a player knows who answers a join request)
    // =====================================================================

    static Card coachCard(int teamId) throws SQLException {
        String[] c = dl.teamCoach(teamId);       // name, years, phone, email, other teams
        Card card = new Card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 20, 16, 20));
        if (c == null) {
            return card;
        }
        JLabel name = new JLabel("Coach: " + c[0], new Ico("user", 20, PRIMARY), SwingConstants.LEFT);
        name.setFont(font(18, true));
        name.setForeground(TEXT);
        name.setIconTextGap(10);
        addLeft(card, name, 6);
        addLeft(card, text(c[1] + ("1".equals(c[1]) ? " year" : " years") + " of coaching   ·   Phone " + c[2]
                + "   ·   Email " + c[3], 14, false, MUTED), 4);
        addLeft(card, text("-".equals(c[4]) ? "Coaches only this team." : "Also coaches: " + c[4], 14, false, MUTED), 0);
        return card;
    }

    // =====================================================================
    // PLAYER: ASK TO JOIN A TEAM
    // =====================================================================

    static void askToJoin(int teamId) {
        try {
            if (teamId == 0) {
                ArrayList<String[]> teams = dl.otherTeamsForPlayer(profileId);
                teamId = pickOne("Ask to join a team", "Which team?", options(teams, "ID", "Team", "Game"));
                if (teamId == 0) return;
            }
            String[] team = dl.describe("TEAM", teamId);     // name, game, region, coach, players
            if (team == null) return;
            Form f = new Form(mainFrame, "Ask to join " + team[0],
                    "The coach, " + team[3] + ", answers your request. You will see the answer on 'Find a team' "
                    + "> 'My requests'.");
            Radio teamRole = f.radios("I would like to be", new String[] {"STARTER", "SUBSTITUTE", "CAPTAIN"},
                    new String[] {"Starter", "Substitute", "Captain"}, "STARTER");
            JTextField note = f.text("Message to the coach (optional)", "", "For example: when you can play. Up to 200 letters.");
            f.rule(note, () -> note.getText().trim().length() > 200 ? "Please keep the message under 200 letters." : null);
            int id = teamId;
            String[] message = {null};
            if (f.open("Send request", false, () -> {
                String m = dl.askToJoin(profileId, id, teamRole.value(), note.getText());
                if (!succeeded(m)) {
                    throw new Problem(null, procedureText(m));
                }
                message[0] = "Request sent to " + team[3] + ", the coach of " + team[0] + ".";
            })) {
                undoable(message[0], () -> dl.cancelRequest(dl.pendingRequestId(profileId, id), profileId),
                        "Your request was taken back.");
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    static void cancelMyRequest(int requestId, String teamName, String status) {
        if (!"PENDING".equals(status)) {
            toast("info", "This request was already answered, so it can't be taken back.", null);
            return;
        }
        try {
            if (dl.cancelRequest(requestId, profileId)) {
                undoable("Your request to " + teamName + " was taken back.", () -> dl.reopenRequest(requestId),
                        "Your request is waiting again.");
            } else {
                toast("info", "This request was already answered.", null);
                refresh();
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // =====================================================================
    // COACH: ANSWER JOIN REQUESTS
    // =====================================================================

    static void coachApprove(int requestId) {
        try {
            String[] r = dl.requestInfo(requestId);   // team_id, team, player_id, tag, role, status
            if (r == null || !"PENDING".equals(r[5])) {
                toast("info", "This request was already answered.", null);
                return;
            }
            Form f = new Form(mainFrame, "Approve " + r[3] + "?",
                    r[3] + " joins " + r[1] + " right away. You can change the role the player asked for.");
            Radio teamRole = f.radios("Role on the team", new String[] {"STARTER", "SUBSTITUTE", "CAPTAIN"},
                    new String[] {"Starter", "Substitute", "Captain"}, r[4]);
            String[] message = {null};
            if (f.open("Approve and add", false, () -> {
                String m = dl.approveRequest(requestId, profileId, teamRole.value());   // stored procedure
                if (!succeeded(m)) {
                    throw new Problem(null, procedureText(m));
                }
                message[0] = r[3] + " joined " + r[1] + ".";
            })) {
                int teamId = Integer.parseInt(r[0]);
                int playerId = Integer.parseInt(r[2]);
                undoable(message[0], () -> {
                    dl.removePlayerFromTeam(teamId, playerId);
                    dl.reopenRequest(requestId);
                }, r[3] + " is off the team again. The request is waiting.");
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    static void coachDecline(int requestId) {
        try {
            String[] r = dl.requestInfo(requestId);
            if (r == null || !"PENDING".equals(r[5])) {
                toast("info", "This request was already answered.", null);
                return;
            }
            if (!confirm("Decline " + r[3] + "'s request?", r[3] + " will see that the answer is no. "
                    + "You can undo this right after.", "Decline", "Keep it", true)) {
                return;
            }
            if (dl.declineRequest(requestId, profileId)) {
                undoable("You declined " + r[3] + "'s request to join " + r[1] + ".", () -> dl.reopenRequest(requestId),
                        "The request is waiting again.");
            } else {
                toast("info", "This request was already answered.", null);
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // =====================================================================
    // DROP OUT OF A TOURNAMENT (coach: own teams only; director: any team)
    // =====================================================================

    // teamId or tournamentId may be 0 = choose
    static void coachDropOut(int teamId, int tournamentId) {
        try {
            ArrayList<String[]> mine = dl.teamsOfCoach(profileId);   // ID, Team, ...
            List<String[]> pairs = new ArrayList<>();                 // {team id, tournament id, team, tournament}
            for (int i = 1; i < mine.size(); i++) {
                int t = Integer.parseInt(mine.get(i)[0]);
                if (teamId != 0 && t != teamId) continue;
                for (String[] tour : dl.openTournamentsOfTeam(t)) {
                    if (tournamentId != 0 && Integer.parseInt(tour[0]) != tournamentId) continue;
                    pairs.add(new String[] {String.valueOf(t), tour[0], mine.get(i)[1], tour[1]});
                }
            }
            if (pairs.isEmpty()) {
                toast("info", tournamentId != 0 ? "None of your teams is in this tournament (or it is over)."
                        : "Your team is not in a tournament that is still open.", null);
                return;
            }
            String[] pick = pairs.get(0);
            if (pairs.size() > 1) {
                List<String[]> choices = new ArrayList<>();
                for (int i = 0; i < pairs.size(); i++) {
                    choices.add(new String[] {String.valueOf(i + 1), pairs.get(i)[2] + "  ·  " + pairs.get(i)[3]});
                }
                int n = pickOne("Leave a tournament", "Which team and tournament?", choices);
                if (n == 0) return;
                pick = pairs.get(n - 1);
            }
            int team = Integer.parseInt(pick[0]);
            if (!dl.coachOwnsTeam(profileId, team)) {        // a coach may only change their own teams
                toast("error", "You can only change your own teams.", null);
                return;
            }
            withdraw(Integer.parseInt(pick[1]), team, pick[2], pick[3], true);
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    // tournamentId or teamId may be 0 = choose
    static void directorRemoveTeam(int tournamentId, int teamId) {
        try {
            if (tournamentId == 0) {
                List<String[]> tours = teamId == 0 ? tournamentsWithStatus("UPCOMING", "ONGOING")
                                                   : dl.openTournamentsOfTeam(teamId);
                tournamentId = pickOne("Remove a team from a tournament", "Which tournament?", tours);
                if (tournamentId == 0) return;
            }
            String[] tour = dl.describe("TOURNAMENT", tournamentId);
            if (tour == null) return;
            if (teamId == 0) {
                List<String[]> teams = dl.teamsInTournament(tournamentId);
                if (teams.isEmpty()) {
                    toast("info", "No team is in " + tour[0] + " yet.", null);
                    return;
                }
                teamId = pickOne("Remove a team from " + tour[0], "Which team?", teams);
                if (teamId == 0) return;
            }
            String[] team = dl.describe("TEAM", teamId);
            if (team == null) return;
            withdraw(tournamentId, teamId, team[0], tour[0], false);
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    static void withdraw(int tournamentId, int teamId, String teamName, String tourName, boolean coach)
            throws SQLException {
        int planned = dl.plannedMatches(tournamentId, teamId);
        String text = teamName + " will leave " + tourName + "."
                + (planned > 0 ? " Its " + planned + (planned == 1 ? " planned match" : " planned matches")
                               + " will be cancelled." : "")
                + " The team can enter again while the tournament is upcoming. You can't undo this.";
        if (!confirm(coach ? "Drop out of this tournament?" : "Remove this team from the tournament?", text,
                coach ? "Drop out" : "Remove team", "Keep it", true)) {
            return;
        }
        String m = dl.withdrawTeam(tournamentId, teamId);       // stored procedure
        if (succeeded(m)) {
            refresh();
            toast("success", teamName + " left " + tourName + ". " + procedureText(m)
                    .replace("Team removed from the tournament. ", ""), null);
        } else {
            toast("error", procedureText(m), null);
        }
    }

    // =====================================================================
    // PLAYER: PROFILE AND PASSWORD
    // =====================================================================

    static void editProfile() {
        try {
            String[] me = dl.playerProfile(profileId).get(1);   // Gamer tag, First, Last, Born, Country, Email, Username
            Form f = new Form(mainFrame, "Edit my profile", null);
            JTextField email = f.text("Email", me[5], "Like name@example.com");
            JTextField country = f.text("Country", me[4], null);
            f.rule(email, () -> {
                String m = checkEmail(email);
                if (m != null || email.getText().trim().equals(me[5])) return m;
                try {
                    return dl.accountValueExists("email", email.getText().trim()) ? "Another account uses this email." : null;
                } catch (SQLException e) {
                    return null;
                }
            });
            f.rule(country, () -> checkNeed(country, "your country"));
            if (f.open("Save changes", false, () -> dl.updatePlayerContact(profileId, userId,
                    email.getText().trim(), country.getText().trim()))) {
                undoable("Your profile is saved.", () -> dl.updatePlayerContact(profileId, userId, me[5], me[4]),
                        "Your old email and country are back.");
            }
        } catch (SQLException e) {
            toast("error", friendly(e), null);
        }
    }

    static void changePassword() {
        Form f = new Form(mainFrame, "Change password", "Your new password is saved as a secret code.");
        JPasswordField now = f.password("Current password", null);
        JPasswordField pw1 = f.password("New password", "At least 6 characters.");
        JPasswordField pw2 = f.password("Type the new password again", null);
        f.rule(now, () -> now.getPassword().length == 0 ? "Please type your current password." : null);
        f.rule(pw1, () -> pw1.getPassword().length < 6 ? "Please use at least 6 characters." : null);
        f.rule(pw2, () -> Arrays.equals(pw1.getPassword(), pw2.getPassword()) ? null
                : "The two new passwords are different.");
        if (f.open("Change password", false, () -> {
            if (dl.login(username, new String(now.getPassword())) == null) {
                throw new Problem(now, "That is not your current password.");
            }
            dl.changePassword(userId, new String(pw1.getPassword()));
        })) {
            toast("success", "Your password is changed.", null);
        }
    }
}
