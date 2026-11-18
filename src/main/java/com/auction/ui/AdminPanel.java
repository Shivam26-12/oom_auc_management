package com.auction.ui;

import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.User;
import com.auction.service.AdminService;
import com.auction.service.AuctionService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class AdminPanel extends JPanel {
    private final Admin admin;
    private final AdminService adminService;
    private final AuctionService auctionService;
    private final JTextField userSearchField = new JTextField(18);
    private final JTextField auctionSearchField = new JTextField(18);
    private final JButton refreshUsersButton = UiStyle.secondaryButton("Refresh Users");
    private final JButton refreshAuctionsButton = UiStyle.secondaryButton("Refresh Auctions");
    private final JButton cancelAuctionButton = UiStyle.primaryButton("Cancel Auction");
    private final DefaultTableModel usersTableModel = new DefaultTableModel(
            new String[]{"User ID", "Name", "Email", "Role", "Registered"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel auctionsTableModel = new DefaultTableModel(
            new String[]{"Auction ID", "Product Name", "Seller ID", "Starting Price", "Current Highest Bid",
                    "End Time", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public AdminPanel(Admin admin) {
        this(admin, new AdminService(), new AuctionService());
    }

    public AdminPanel(Admin admin, AdminService adminService, AuctionService auctionService) {
        this.admin = admin;
        this.adminService = adminService;
        this.auctionService = auctionService;
        setLayout(new BorderLayout(0, 18));
        setBackground(UiStyle.BACKGROUND);
        buildInterface();
        loadStatistics();
        loadUsers();
        loadAuctions();
    }

    private void buildInterface() {
        JPanel heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.setOpaque(false);
        heading.add(UiStyle.title("Welcome, " + admin.getName()));
        JLabel role = UiStyle.subtitle("Administrator account");
        role.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(Box.createVerticalStrut(7));
        heading.add(role);
        add(heading, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        content.add(buildStatsPanel());
        content.add(Box.createVerticalStrut(16));
        JPanel userSection = new JPanel();
        userSection.setLayout(new BoxLayout(userSection, BoxLayout.Y_AXIS));
        userSection.setOpaque(false);
        userSection.add(UiStyle.subtitle("User management"));
        userSection.add(buildUsersPanel());
        content.add(userSection);
        content.add(Box.createVerticalStrut(16));
        JPanel auctionSection = new JPanel();
        auctionSection.setLayout(new BoxLayout(auctionSection, BoxLayout.Y_AXIS));
        auctionSection.setOpaque(false);
        auctionSection.add(UiStyle.subtitle("Auction monitoring"));
        auctionSection.add(buildAuctionsPanel());
        content.add(auctionSection);
        content.add(Box.createVerticalStrut(16));
        JPanel moderationSection = new JPanel();
        moderationSection.setLayout(new BoxLayout(moderationSection, BoxLayout.Y_AXIS));
        moderationSection.setOpaque(false);
        moderationSection.add(UiStyle.subtitle("Listing moderation"));
        moderationSection.add(UiStyle.section("Listing moderation", "Manage auctions and review closed listings."));
        content.add(moderationSection);
        add(content, BorderLayout.CENTER);
    }

    private JPanel buildStatsPanel() {
        JPanel statsPanel = new JPanel(new GridBagLayout());
        statsPanel.setOpaque(false);
        statsPanel.setBorder(BorderFactory.createTitledBorder("Dashboard Overview"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 8, 8, 8);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;

        JPanel[] statCards = new JPanel[]{
                statCard("Total Users", "0"),
                statCard("Buyers", "0"),
                statCard("Sellers", "0"),
                statCard("Admins", "0"),
                statCard("Total Auctions", "0"),
                statCard("Active", "0"),
                statCard("Sold", "0"),
                statCard("Unsold", "0"),
                statCard("Cancelled", "0")
        };

        for (int i = 0; i < statCards.length; i++) {
            constraints.gridx = i % 3;
            constraints.gridy = i / 3;
            statsPanel.add(statCards[i], constraints);
        }
        return statsPanel;
    }

    private JPanel statCard(String label, String value) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(UiStyle.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyle.BORDER),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JLabel title = new JLabel(label);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(UiStyle.MUTED);
        JLabel number = new JLabel(value);
        number.setFont(new Font("Segoe UI", Font.BOLD, 20));
        number.setForeground(UiStyle.PRIMARY_DARK);
        card.add(title, BorderLayout.NORTH);
        card.add(number, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder("User Management"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        userSearchField.setToolTipText("Search by name or email");
        UiStyle.styleField(userSearchField);
        JButton searchUsersButton = UiStyle.primaryButton("Search Users");
        searchUsersButton.addActionListener(event -> loadUsers());
        refreshUsersButton.addActionListener(event -> loadUsers());
        userSearchField.addActionListener(event -> loadUsers());
        controls.add(new JLabel("Search: "));
        controls.add(userSearchField);
        controls.add(searchUsersButton);
        controls.add(refreshUsersButton);
        panel.add(controls, BorderLayout.NORTH);

        JTable userTable = new JTable(usersTableModel);
        userTable.setFont(UiStyle.BODY_FONT);
        userTable.setRowHeight(28);
        userTable.setFillsViewportHeight(true);
        userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        userTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane userScroll = new JScrollPane(userTable);
        userScroll.setPreferredSize(new Dimension(900, 150));
        panel.add(userScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAuctionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder("Auction Management"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        auctionSearchField.setToolTipText("Search product name or category");
        UiStyle.styleField(auctionSearchField);
        JButton searchAuctionButton = UiStyle.primaryButton("Search Auctions");
        searchAuctionButton.addActionListener(event -> loadAuctions());
        refreshAuctionsButton.addActionListener(event -> loadAuctions());
        cancelAuctionButton.addActionListener(event -> cancelSelectedAuction());
        auctionSearchField.addActionListener(event -> loadAuctions());
        controls.add(new JLabel("Search: "));
        controls.add(auctionSearchField);
        controls.add(searchAuctionButton);
        controls.add(refreshAuctionsButton);
        controls.add(cancelAuctionButton);
        panel.add(controls, BorderLayout.NORTH);

        JTable auctionTable = new JTable(auctionsTableModel);
        auctionTable.setFont(UiStyle.BODY_FONT);
        auctionTable.setRowHeight(28);
        auctionTable.setFillsViewportHeight(true);
        auctionTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        auctionTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane auctionScroll = new JScrollPane(auctionTable);
        auctionScroll.setPreferredSize(new Dimension(900, 200));
        panel.add(auctionScroll, BorderLayout.CENTER);
        return panel;
    }

    private void loadStatistics() {
        new SwingWorker<AdminService.DashboardStatistics, Void>() {
            @Override
            protected AdminService.DashboardStatistics doInBackground() throws Exception {
                return adminService.getDashboardStatistics();
            }

            @Override
            protected void done() {
                try {
                    AdminService.DashboardStatistics stats = get();
                    updateStatCards(stats);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    JOptionPane.showMessageDialog(AdminPanel.this, "Unable to load dashboard statistics.",
                            "Admin dashboard", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void updateStatCards(AdminService.DashboardStatistics stats) {
        Component[] cards = getComponents();
        if (cards.length > 0 && cards[0] instanceof JPanel content) {
            List<JLabel> labels = new ArrayList<>();
            collectLabels(content, labels);
            if (labels.size() >= 9) {
                labels.get(0).setText(String.valueOf(stats.totalUsers()));
                labels.get(1).setText(String.valueOf(stats.totalBuyers()));
                labels.get(2).setText(String.valueOf(stats.totalSellers()));
                labels.get(3).setText(String.valueOf(stats.totalAdmins()));
                labels.get(4).setText(String.valueOf(stats.totalAuctions()));
                labels.get(5).setText(String.valueOf(stats.activeAuctions()));
                labels.get(6).setText(String.valueOf(stats.soldAuctions()));
                labels.get(7).setText(String.valueOf(stats.unsoldAuctions()));
                labels.get(8).setText(String.valueOf(stats.cancelledAuctions()));
            }
        }
    }

    private void collectLabels(Component component, List<JLabel> labels) {
        if (component instanceof JLabel label) {
            labels.add(label);
        }
        if (component instanceof JPanel panel) {
            for (Component child : panel.getComponents()) {
                collectLabels(child, labels);
            }
        }
    }

    private void loadUsers() {
        new SwingWorker<List<User>, Void>() {
            @Override
            protected List<User> doInBackground() throws Exception {
                return adminService.searchUsers(userSearchField.getText());
            }

            @Override
            protected void done() {
                usersTableModel.setRowCount(0);
                try {
                    List<User> users = get();
                    for (User user : users) {
                        usersTableModel.addRow(new Object[]{
                                user.getId(), user.getName(), user.getEmail(), user.getRole().name(),
                                user.getCreatedAt() == null ? "-" : user.getCreatedAt().toString()
                        });
                    }
                    if (users.isEmpty()) {
                        JOptionPane.showMessageDialog(AdminPanel.this, "No users match your search.",
                                "User management", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    JOptionPane.showMessageDialog(AdminPanel.this, "Unable to load users.",
                            "User management", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadAuctions() {
        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                List<Object[]> rows = new ArrayList<>();
                for (Auction auction : adminService.searchAuctions(auctionSearchField.getText())) {
                    rows.add(new Object[]{
                            auction.getId(), auction.getProductName(), auction.getSellerId(),
                            auction.getStartingPrice(), auctionService.getCurrentHighestBid(auction.getId()),
                            auction.getEndTime().toString(), auction.getStatus().name()
                    });
                }
                return rows;
            }

            @Override
            protected void done() {
                auctionsTableModel.setRowCount(0);
                try {
                    List<Object[]> rows = get();
                    for (Object[] row : rows) {
                        auctionsTableModel.addRow(row);
                    }
                    if (rows.isEmpty()) {
                        JOptionPane.showMessageDialog(AdminPanel.this, "No auctions match your search.",
                                "Auction management", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    JOptionPane.showMessageDialog(AdminPanel.this, "Unable to load auctions.",
                            "Auction management", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void cancelSelectedAuction() {
        int selectedRow = getSelectedAuctionRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select an ACTIVE auction to cancel.",
                    "Cancel auction", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int auctionId = ((Number) auctionsTableModel.getValueAt(selectedRow, 0)).intValue();
        String status = String.valueOf(auctionsTableModel.getValueAt(selectedRow, 6));
        if (!AuctionStatus.ACTIVE.name().equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Only ACTIVE auctions may be cancelled.",
                    "Cancel auction", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Cancel auction #" + auctionId + "? This action cannot be undone.",
                "Cancel auction", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                adminService.cancelAuction(auctionId, admin);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(AdminPanel.this, "Auction cancelled successfully.",
                            "Cancel auction", JOptionPane.INFORMATION_MESSAGE);
                    loadAuctions();
                    loadStatistics();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            cause instanceof IllegalArgumentException ? cause.getMessage() : "Unable to cancel the auction.",
                            "Cancel auction", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private int getSelectedAuctionRow() {
        JTable table = findAuctionTable();
        if (table == null) {
            return -1;
        }
        return table.getSelectedRow();
    }

    private JTable findAuctionTable() {
        for (Component component : getComponents()) {
            if (component instanceof JPanel panel) {
                return findAuctionTable(panel);
            }
        }
        return null;
    }

    private JTable findAuctionTable(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTable table) {
                return table;
            }
            if (component instanceof Container child) {
                JTable found = findAuctionTable(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}