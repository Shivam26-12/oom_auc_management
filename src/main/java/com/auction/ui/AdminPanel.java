package com.auction.ui;

import com.auction.model.Admin;
import com.auction.model.AuctionStatus;
import com.auction.model.User;
import com.auction.service.AdminService;
import com.auction.service.AdminService.AdminAuctionDetail;
import com.auction.service.AdminService.AdminAuctionRow;
import com.auction.service.AdminService.BidDetail;
import com.auction.service.AdminService.DashboardStatistics;
import com.auction.service.AdminService.UserSummary;
import com.auction.service.AuctionService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class AdminPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Admin admin;
    private final AdminService adminService;
    private final AuctionService auctionService;
    private final String viewMode; // "Overview", "Users", "Auctions", "Moderation"

    // Stat-card value labels
    private final JLabel totalUsersLabel = statValueLabel("0");
    private final JLabel buyersLabel = statValueLabel("0");
    private final JLabel sellersLabel = statValueLabel("0");
    private final JLabel adminsLabel = statValueLabel("0");
    private final JLabel totalAuctionsLabel = statValueLabel("0");
    private final JLabel activeLabel = statValueLabel("0");
    private final JLabel soldLabel = statValueLabel("0");
    private final JLabel unsoldLabel = statValueLabel("0");
    private final JLabel cancelledLabel = statValueLabel("0");

    // User Management controls
    private final JTextField userSearchField = new JTextField(16);
    private final JComboBox<String> userRoleFilter = new JComboBox<>(new String[]{"All Roles", "BUYER", "SELLER", "ADMIN"});
    private final JButton searchUsersButton = UiStyle.primaryButton("Search Users");
    private final JButton refreshUsersButton = UiStyle.secondaryButton("Refresh Users");
    private final JButton viewUserDetailsButton = UiStyle.secondaryButton("View User Details");

    private final DefaultTableModel usersTableModel = new DefaultTableModel(
            new String[]{"User ID", "Name", "Email", "Role", "Registered"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable userTable = new JTable(usersTableModel);

    // Auction Management controls
    private final JTextField auctionSearchField = new JTextField(16);
    private final JComboBox<String> auctionStatusFilter = new JComboBox<>(new String[]{"All Statuses", "ACTIVE", "SOLD", "UNSOLD", "CANCELLED"});
    private final JButton searchAuctionButton = UiStyle.primaryButton("Search Auctions");
    private final JButton refreshAuctionsButton = UiStyle.secondaryButton("Refresh Auctions");
    private final JButton viewAuctionDetailsButton = UiStyle.secondaryButton("View Details");
    private final JButton cancelAuctionButton = UiStyle.primaryButton("Cancel Auction");

    private final DefaultTableModel auctionsTableModel = new DefaultTableModel(
            new String[]{"Auction ID", "Product Name", "Seller", "Starting Price", "Current Highest Bid",
                    "End Time", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable auctionTable = new JTable(auctionsTableModel);

    public AdminPanel(Admin admin) {
        this(admin, new AdminService(), new AuctionService(), "Overview");
    }

    public AdminPanel(Admin admin, AdminService adminService, AuctionService auctionService) {
        this(admin, adminService, auctionService, "Overview");
    }

    public AdminPanel(Admin admin, AdminService adminService, AuctionService auctionService, String viewMode) {
        this.admin = admin;
        this.adminService = adminService;
        this.auctionService = auctionService;
        this.viewMode = viewMode == null ? "Overview" : viewMode;

        setLayout(new BorderLayout(0, 18));
        setBackground(UiStyle.BACKGROUND);

        configureTables();
        buildInterface();

        // Load initial data
        if ("Users".equals(this.viewMode)) {
            loadUsers(false);
        } else if ("Auctions".equals(this.viewMode)) {
            loadAuctions(false);
        } else {
            loadStatistics();
            loadUsers(false);
            loadAuctions(false);
        }
    }

    private void configureTables() {
        userTable.setFont(UiStyle.BODY_FONT);
        userTable.setRowHeight(28);
        userTable.setFillsViewportHeight(true);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        userTable.getTableHeader().setReorderingAllowed(false);

        auctionTable.setFont(UiStyle.BODY_FONT);
        auctionTable.setRowHeight(28);
        auctionTable.setFillsViewportHeight(true);
        auctionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        auctionTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        auctionTable.getTableHeader().setReorderingAllowed(false);
    }

    private void buildInterface() {
        // Top Heading (required by RolePanelTest: "Welcome, " and "Administrator account")
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

        if ("Users".equals(viewMode)) {
            content.add(buildUsersSection(400));
        } else if ("Auctions".equals(viewMode)) {
            content.add(buildAuctionsSection(400));
        } else if ("Moderation".equals(viewMode)) {
            content.add(buildModerationSection());
            content.add(Box.createVerticalStrut(16));
            content.add(buildAuctionsSection(350));
        } else {
            // "Overview" mode contains all sections
            content.add(buildStatsPanel());
            content.add(Box.createVerticalStrut(16));
            content.add(buildUsersSection(180));
            content.add(Box.createVerticalStrut(16));
            content.add(buildAuctionsSection(200));
            content.add(Box.createVerticalStrut(16));
            content.add(buildModerationSection());
        }

        // Put content inside JScrollPane so it NEVER overflows or crushes tables
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel buildStatsPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 8));
        container.setOpaque(false);
        container.setBorder(BorderFactory.createTitledBorder("Dashboard Overview"));

        JPanel headerBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerBar.setOpaque(false);
        JButton refreshStatsButton = UiStyle.secondaryButton("Refresh Stats");
        refreshStatsButton.addActionListener(event -> loadStatistics());
        headerBar.add(refreshStatsButton);
        container.add(headerBar, BorderLayout.NORTH);

        JPanel statsGrid = new JPanel(new GridBagLayout());
        statsGrid.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;

        JPanel[] statCards = new JPanel[]{
                statCard("Total Users", totalUsersLabel),
                statCard("Buyers", buyersLabel),
                statCard("Sellers", sellersLabel),
                statCard("Admins", adminsLabel),
                statCard("Total Auctions", totalAuctionsLabel),
                statCard("Active", activeLabel),
                statCard("Sold", soldLabel),
                statCard("Unsold", unsoldLabel),
                statCard("Cancelled", cancelledLabel)
        };

        for (int i = 0; i < statCards.length; i++) {
            constraints.gridx = i % 3;
            constraints.gridy = i / 3;
            statsGrid.add(statCards[i], constraints);
        }
        container.add(statsGrid, BorderLayout.CENTER);
        return container;
    }

    private static JLabel statValueLabel(String value) {
        JLabel number = new JLabel(value);
        number.setFont(new Font("Segoe UI", Font.BOLD, 22));
        number.setForeground(UiStyle.PRIMARY_DARK);
        return number;
    }

    private static JPanel statCard(String label, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(UiStyle.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyle.BORDER),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel title = new JLabel(label);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(UiStyle.MUTED);
        card.add(title, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildUsersSection(int tableHeight) {
        JPanel userSection = new JPanel();
        userSection.setLayout(new BoxLayout(userSection, BoxLayout.Y_AXIS));
        userSection.setOpaque(false);

        // Required text for RolePanelTest: "User management"
        userSection.add(UiStyle.subtitle("User management"));

        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder("User Management"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        userSearchField.setToolTipText("Search by Name, Email, or User ID");
        UiStyle.styleField(userSearchField);

        controls.add(new JLabel("Search: "));
        controls.add(userSearchField);
        controls.add(new JLabel("Role: "));
        controls.add(userRoleFilter);
        controls.add(searchUsersButton);
        controls.add(refreshUsersButton);
        controls.add(viewUserDetailsButton);

        searchUsersButton.addActionListener(event -> loadUsers(true));
        refreshUsersButton.addActionListener(event -> {
            userSearchField.setText("");
            userRoleFilter.setSelectedIndex(0);
            loadUsers(false);
        });
        userSearchField.addActionListener(event -> loadUsers(true));
        userRoleFilter.addActionListener(event -> loadUsers(false));
        viewUserDetailsButton.addActionListener(event -> showSelectedUserDetails());

        panel.add(controls, BorderLayout.NORTH);

        JScrollPane userScroll = new JScrollPane(userTable);
        userScroll.setPreferredSize(new Dimension(900, tableHeight));
        userScroll.setMinimumSize(new Dimension(600, 120));
        panel.add(userScroll, BorderLayout.CENTER);

        userSection.add(panel);
        return userSection;
    }

    private JPanel buildAuctionsSection(int tableHeight) {
        JPanel auctionSection = new JPanel();
        auctionSection.setLayout(new BoxLayout(auctionSection, BoxLayout.Y_AXIS));
        auctionSection.setOpaque(false);

        // Required text for RolePanelTest: "Auction monitoring"
        auctionSection.add(UiStyle.subtitle("Auction monitoring"));

        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder("Auction Management"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        auctionSearchField.setToolTipText("Search product name, category, or seller");
        UiStyle.styleField(auctionSearchField);

        controls.add(new JLabel("Search: "));
        controls.add(auctionSearchField);
        controls.add(new JLabel("Status: "));
        controls.add(auctionStatusFilter);
        controls.add(searchAuctionButton);
        controls.add(refreshAuctionsButton);
        controls.add(viewAuctionDetailsButton);
        controls.add(cancelAuctionButton);

        searchAuctionButton.addActionListener(event -> loadAuctions(true));
        refreshAuctionsButton.addActionListener(event -> {
            auctionSearchField.setText("");
            auctionStatusFilter.setSelectedIndex(0);
            loadAuctions(false);
        });
        auctionSearchField.addActionListener(event -> loadAuctions(true));
        auctionStatusFilter.addActionListener(event -> loadAuctions(false));
        viewAuctionDetailsButton.addActionListener(event -> showSelectedAuctionDetails());
        cancelAuctionButton.addActionListener(event -> cancelSelectedAuction());

        panel.add(controls, BorderLayout.NORTH);

        JScrollPane auctionScroll = new JScrollPane(auctionTable);
        auctionScroll.setPreferredSize(new Dimension(900, tableHeight));
        auctionScroll.setMinimumSize(new Dimension(600, 140));
        panel.add(auctionScroll, BorderLayout.CENTER);

        auctionSection.add(panel);
        return auctionSection;
    }

    private JPanel buildModerationSection() {
        JPanel moderationSection = new JPanel();
        moderationSection.setLayout(new BoxLayout(moderationSection, BoxLayout.Y_AXIS));
        moderationSection.setOpaque(false);

        // Required text for RolePanelTest: "Listing moderation"
        moderationSection.add(UiStyle.subtitle("Listing moderation"));

        JPanel card = new JPanel(new BorderLayout(10, 8));
        card.setBackground(UiStyle.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyle.BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JLabel desc = new JLabel("Manage auctions, review closed listings, and moderate marketplace activity.");
        desc.setFont(UiStyle.BODY_FONT);
        desc.setForeground(UiStyle.MUTED);
        card.add(desc, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        actions.setOpaque(false);

        JButton showActive = UiStyle.secondaryButton("Filter Active Listings");
        showActive.addActionListener(e -> {
            auctionStatusFilter.setSelectedItem("ACTIVE");
            loadAuctions(false);
        });

        JButton showCancelled = UiStyle.secondaryButton("Filter Cancelled Listings");
        showCancelled.addActionListener(e -> {
            auctionStatusFilter.setSelectedItem("CANCELLED");
            loadAuctions(false);
        });

        JButton showSold = UiStyle.secondaryButton("Filter Sold Listings");
        showSold.addActionListener(e -> {
            auctionStatusFilter.setSelectedItem("SOLD");
            loadAuctions(false);
        });

        JButton resetAll = UiStyle.secondaryButton("Show All Listings");
        resetAll.addActionListener(e -> {
            auctionStatusFilter.setSelectedItem("All Statuses");
            auctionSearchField.setText("");
            loadAuctions(false);
        });

        actions.add(showActive);
        actions.add(showCancelled);
        actions.add(showSold);
        actions.add(resetAll);
        card.add(actions, BorderLayout.CENTER);

        moderationSection.add(card);
        return moderationSection;
    }

    private void loadStatistics() {
        new SwingWorker<DashboardStatistics, Void>() {
            @Override
            protected DashboardStatistics doInBackground() throws Exception {
                return adminService.getDashboardStatistics();
            }

            @Override
            protected void done() {
                try {
                    updateStatCards(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            "Unable to load dashboard statistics: " + rootMessage(exception),
                            "Admin dashboard", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void updateStatCards(DashboardStatistics stats) {
        totalUsersLabel.setText(String.valueOf(stats.totalUsers()));
        buyersLabel.setText(String.valueOf(stats.totalBuyers()));
        sellersLabel.setText(String.valueOf(stats.totalSellers()));
        adminsLabel.setText(String.valueOf(stats.totalAdmins()));
        totalAuctionsLabel.setText(String.valueOf(stats.totalAuctions()));
        activeLabel.setText(String.valueOf(stats.activeAuctions()));
        soldLabel.setText(String.valueOf(stats.soldAuctions()));
        unsoldLabel.setText(String.valueOf(stats.unsoldAuctions()));
        cancelledLabel.setText(String.valueOf(stats.cancelledAuctions()));
    }

    private void loadUsers(boolean showEmptyMessage) {
        String term = userSearchField.getText();
        String roleFilter = (String) userRoleFilter.getSelectedItem();

        new SwingWorker<List<User>, Void>() {
            @Override
            protected List<User> doInBackground() throws Exception {
                return adminService.searchUsers(term, roleFilter);
            }

            @Override
            protected void done() {
                usersTableModel.setRowCount(0);
                try {
                    List<User> users = get();
                    for (User user : users) {
                        usersTableModel.addRow(new Object[]{
                                user.getId(),
                                user.getName(),
                                user.getEmail(),
                                user.getRole().name(),
                                user.getCreatedAt() == null ? "-" : user.getCreatedAt().format(DATE_FORMAT)
                        });
                    }
                    if (showEmptyMessage && users.isEmpty()) {
                        JOptionPane.showMessageDialog(AdminPanel.this, "No users match your search criteria.",
                                "User management", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            "Unable to load users: " + rootMessage(exception),
                            "User management", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadAuctions(boolean showEmptyMessage) {
        String term = auctionSearchField.getText();
        String statusFilter = (String) auctionStatusFilter.getSelectedItem();

        new SwingWorker<List<AdminAuctionRow>, Void>() {
            @Override
            protected List<AdminAuctionRow> doInBackground() throws Exception {
                return adminService.getEnrichedAuctions(term, statusFilter);
            }

            @Override
            protected void done() {
                auctionsTableModel.setRowCount(0);
                try {
                    List<AdminAuctionRow> rows = get();
                    for (AdminAuctionRow a : rows) {
                        auctionsTableModel.addRow(new Object[]{
                                a.id(),
                                a.productName(),
                                a.sellerName() + " (#" + a.sellerId() + ")",
                                "$" + a.startingPrice(),
                                "$" + a.currentHighestBid(),
                                a.endTime() == null ? "-" : a.endTime().format(DATE_FORMAT),
                                a.status().name()
                        });
                    }
                    if (showEmptyMessage && rows.isEmpty()) {
                        JOptionPane.showMessageDialog(AdminPanel.this, "No auctions match your search criteria.",
                                "Auction management", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            "Unable to load auctions: " + rootMessage(exception),
                            "Auction management", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showSelectedUserDetails() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a user from the table first.",
                    "View User Details", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int userId = ((Number) usersTableModel.getValueAt(selectedRow, 0)).intValue();

        new SwingWorker<UserSummary, Void>() {
            @Override
            protected UserSummary doInBackground() throws Exception {
                return adminService.getUserSummary(userId);
            }

            @Override
            protected void done() {
                try {
                    UserSummary summary = get();
                    displayUserDetailDialog(summary);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            "Unable to retrieve user details: " + rootMessage(exception),
                            "User Details", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void displayUserDetailDialog(UserSummary summary) {
        User user = summary.user();
        Window parent = javax.swing.SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parent, "User Details — " + user.getName(), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(440, 360);
        dialog.setLocationRelativeTo(parent);

        JPanel panel = new JPanel(new BorderLayout(14, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        panel.setBackground(UiStyle.BACKGROUND);

        JPanel info = new JPanel(new GridLayout(6, 2, 8, 10));
        info.setOpaque(false);

        addDetailRow(info, "User ID:", String.valueOf(user.getId()));
        addDetailRow(info, "Name:", user.getName());
        addDetailRow(info, "Email:", user.getEmail());
        addDetailRow(info, "Role:", user.getRole().name());
        addDetailRow(info, "Registered Date:", user.getCreatedAt() == null ? "-" : user.getCreatedAt().format(DATE_FORMAT));

        if (user.getRole() == com.auction.model.UserRole.SELLER) {
            addDetailRow(info, "Auctions Created:", String.valueOf(summary.auctionCount()));
        } else if (user.getRole() == com.auction.model.UserRole.BUYER) {
            addDetailRow(info, "Bids Placed:", String.valueOf(summary.bidCount()));
        } else {
            addDetailRow(info, "Account Type:", "Administrator");
        }

        panel.add(info, BorderLayout.CENTER);

        JButton closeButton = UiStyle.primaryButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);
        buttonPanel.add(closeButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    private void showSelectedAuctionDetails() {
        int selectedRow = auctionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an auction from the table first.",
                    "View Auction Details", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int auctionId = ((Number) auctionsTableModel.getValueAt(selectedRow, 0)).intValue();

        new SwingWorker<AdminAuctionDetail, Void>() {
            @Override
            protected AdminAuctionDetail doInBackground() throws Exception {
                return adminService.getAuctionFullDetails(auctionId);
            }

            @Override
            protected void done() {
                try {
                    AdminAuctionDetail detail = get();
                    displayAuctionDetailDialog(detail);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            "Unable to retrieve auction details: " + rootMessage(exception),
                            "Auction Details", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void displayAuctionDetailDialog(AdminAuctionDetail detail) {
        Window parent = javax.swing.SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parent, "Auction Details — #" + detail.auction().getId() + " " + detail.auction().getProductName(), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(620, 560);
        dialog.setLocationRelativeTo(parent);

        JPanel mainPanel = new JPanel(new BorderLayout(12, 14));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        mainPanel.setBackground(UiStyle.BACKGROUND);

        // Auction summary grid
        JPanel topInfo = new JPanel(new GridLayout(6, 2, 8, 8));
        topInfo.setOpaque(false);
        topInfo.setBorder(BorderFactory.createTitledBorder("Auction Information"));

        addDetailRow(topInfo, "Auction ID:", String.valueOf(detail.auction().getId()));
        addDetailRow(topInfo, "Product Name:", detail.auction().getProductName());
        addDetailRow(topInfo, "Category:", detail.auction().getCategory() == null ? "-" : detail.auction().getCategory());
        addDetailRow(topInfo, "Status:", detail.auction().getStatus().name());
        addDetailRow(topInfo, "Seller:", detail.seller() == null ? "ID #" + detail.auction().getSellerId() : detail.seller().getName() + " (" + detail.seller().getEmail() + ")");
        addDetailRow(topInfo, "Starting Price:", "$" + detail.auction().getStartingPrice());
        addDetailRow(topInfo, "Highest Bid:", "$" + detail.highestBid());
        addDetailRow(topInfo, "Total Bids:", String.valueOf(detail.bidCount()));
        addDetailRow(topInfo, "Start Time:", detail.auction().getCreatedAt() == null ? "-" : detail.auction().getCreatedAt().format(DATE_FORMAT));
        addDetailRow(topInfo, "End Time:", detail.auction().getEndTime() == null ? "-" : detail.auction().getEndTime().format(DATE_FORMAT));

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.add(topInfo);
        centerPanel.add(Box.createVerticalStrut(10));

        // Description
        if (detail.auction().getDescription() != null && !detail.auction().getDescription().isBlank()) {
            JPanel descPanel = new JPanel(new BorderLayout(4, 4));
            descPanel.setOpaque(false);
            descPanel.setBorder(BorderFactory.createTitledBorder("Description"));
            JTextArea descArea = new JTextArea(detail.auction().getDescription(), 2, 30);
            descArea.setEditable(false);
            descArea.setLineWrap(true);
            descArea.setWrapStyleWord(true);
            descArea.setFont(UiStyle.BODY_FONT);
            descArea.setBackground(UiStyle.SURFACE);
            descPanel.add(new JScrollPane(descArea), BorderLayout.CENTER);
            centerPanel.add(descPanel);
            centerPanel.add(Box.createVerticalStrut(10));
        }

        // Bidding history
        JPanel bidsPanel = new JPanel(new BorderLayout(4, 4));
        bidsPanel.setOpaque(false);
        bidsPanel.setBorder(BorderFactory.createTitledBorder("Bidding History (" + detail.bids().size() + " bids)"));

        DefaultTableModel bidsModel = new DefaultTableModel(
                new String[]{"Bid ID", "Buyer Name", "Buyer Email", "Amount", "Bid Time"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        for (BidDetail b : detail.bids()) {
            bidsModel.addRow(new Object[]{
                    b.bidId(),
                    b.buyerName(),
                    b.buyerEmail(),
                    "$" + b.amount(),
                    b.bidTime() == null ? "-" : b.bidTime().format(DATE_FORMAT)
            });
        }

        JTable bidsTable = new JTable(bidsModel);
        bidsTable.setFont(UiStyle.BODY_FONT);
        bidsTable.setRowHeight(24);
        bidsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        JScrollPane bidsScroll = new JScrollPane(bidsTable);
        bidsScroll.setPreferredSize(new Dimension(560, 120));
        bidsPanel.add(bidsScroll, BorderLayout.CENTER);
        centerPanel.add(bidsPanel);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);

        if (detail.auction().getStatus() == AuctionStatus.ACTIVE) {
            JButton cancelBtn = UiStyle.primaryButton("Cancel This Auction");
            cancelBtn.addActionListener(e -> {
                dialog.dispose();
                cancelSelectedAuction();
            });
            buttonPanel.add(cancelBtn);
        }

        JButton closeBtn = UiStyle.secondaryButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeBtn);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(mainPanel);
        dialog.setVisible(true);
    }

    private static void addDetailRow(JPanel panel, String label, String value) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(UiStyle.MUTED);
        JLabel v = new JLabel(value);
        v.setFont(UiStyle.BODY_FONT);
        v.setForeground(UiStyle.TEXT);
        panel.add(l);
        panel.add(v);
    }

    private void cancelSelectedAuction() {
        int selectedRow = auctionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select an ACTIVE auction from the table to cancel.",
                    "Cancel auction", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int auctionId = ((Number) auctionsTableModel.getValueAt(selectedRow, 0)).intValue();
        String productName = String.valueOf(auctionsTableModel.getValueAt(selectedRow, 1));
        String status = String.valueOf(auctionsTableModel.getValueAt(selectedRow, 6));

        if (!AuctionStatus.ACTIVE.name().equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Only ACTIVE auctions may be cancelled. Auction #" + auctionId + " is " + status + ".",
                    "Cancel auction", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cancel auction #" + auctionId + " (" + productName + ")?\nThis action cannot be undone.",
                "Confirm Auction Cancellation", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        cancelAuctionButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                adminService.cancelAuction(auctionId, admin);
                return null;
            }

            @Override
            protected void done() {
                cancelAuctionButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            "Auction #" + auctionId + " has been successfully cancelled.",
                            "Cancel auction", JOptionPane.INFORMATION_MESSAGE);
                    loadAuctions(false);
                    loadStatistics();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            cause instanceof IllegalArgumentException ? cause.getMessage() : "Unable to cancel the auction: " + rootMessage(exception),
                            "Cancel auction", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static String rootMessage(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause != null ? cause.getMessage() : exception.getMessage();
    }
}
