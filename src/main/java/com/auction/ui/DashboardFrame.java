package com.auction.ui;

import com.auction.model.Admin;
import com.auction.model.Buyer;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.model.UserRole;
import com.auction.service.AuctionService;
import com.auction.service.AuthService;
import com.auction.service.BidService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.CardLayout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DashboardFrame extends JFrame {
    private final User user;
    private final AuthService authService;
    private final AuctionService auctionService;
    private final BidService bidService;
    private final com.auction.service.AdminService adminService;
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final Map<String, JButton> navigationButtons = new LinkedHashMap<>();

    public DashboardFrame(User user, AuthService authService) {
        this(user, authService, new AuctionService(), new BidService(), new com.auction.service.AdminService());
    }

    public DashboardFrame(User user, AuthService authService, AuctionService auctionService) {
        this(user, authService, auctionService, new BidService(), new com.auction.service.AdminService());
    }

    public DashboardFrame(User user, AuthService authService, AuctionService auctionService,
                          BidService bidService) {
        this(user, authService, auctionService, bidService, new com.auction.service.AdminService());
    }

    public DashboardFrame(User user, AuthService authService, AuctionService auctionService,
                          BidService bidService, com.auction.service.AdminService adminService) {
        super("Online Auction Management System");
        this.user = user;
        this.authService = authService;
        this.auctionService = auctionService;
        this.bidService = bidService;
        this.adminService = adminService;
        if (user == null || authService == null || auctionService == null || bidService == null || adminService == null) {
            throw new IllegalArgumentException("An authenticated user and required services are required");
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(850, 600));
        setSize(1020, 700);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UiStyle.BACKGROUND);
        buildInterface();
    }

    private void buildInterface() {
        add(createHeader(), BorderLayout.NORTH);
        add(createSidebar(), BorderLayout.WEST);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(UiStyle.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));
        pages.setBackground(UiStyle.BACKGROUND);
        pages.add(createRolePanel(), "Overview");
        for (String page : sidebarItems(user.getRole())) {
            if (!"Overview".equals(page)) {
                pages.add(createDashboardPage(page), page);
            }
        }
        content.add(pages, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        showPage("Overview");
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(UiStyle.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiStyle.BORDER),
                BorderFactory.createEmptyBorder(16, 24, 16, 24)));

        JPanel branding = new JPanel(new GridBagLayout());
        branding.setOpaque(false);
        GridBagConstraints brandConstraints = new GridBagConstraints();
        brandConstraints.gridx = 0;
        brandConstraints.gridy = 0;
        brandConstraints.anchor = GridBagConstraints.WEST;
        JLabel brand = new JLabel("OAMS");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 20));
        brand.setForeground(UiStyle.PRIMARY_DARK);
        branding.add(brand, brandConstraints);
        brandConstraints.gridy = 1;
        JLabel subtitle = new JLabel("Online Auction Management System");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(UiStyle.MUTED);
        branding.add(subtitle, brandConstraints);
        header.add(branding, BorderLayout.WEST);

        JPanel account = new JPanel(new GridBagLayout());
        account.setOpaque(false);
        GridBagConstraints accountConstraints = new GridBagConstraints();
        accountConstraints.gridy = 0;
        accountConstraints.gridx = 0;
        accountConstraints.anchor = GridBagConstraints.EAST;
        JLabel name = new JLabel(user.getName());
        name.setFont(new Font("Segoe UI", Font.BOLD, 14));
        name.setForeground(UiStyle.TEXT);
        account.add(name, accountConstraints);
        accountConstraints.gridy++;
        JLabel role = new JLabel(user.getRole().name());
        role.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        role.setForeground(UiStyle.PRIMARY);
        account.add(role, accountConstraints);
        accountConstraints.gridy = 0;
        accountConstraints.gridx = 1;
        accountConstraints.insets = new Insets(0, 18, 0, 0);
        JButton logoutButton = UiStyle.secondaryButton("Log out");
        logoutButton.addActionListener(event -> logout());
        account.add(logoutButton, accountConstraints);
        header.add(account, BorderLayout.EAST);
        return header;
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new GridBagLayout());
        sidebar.setBackground(new java.awt.Color(238, 243, 249));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiStyle.BORDER));
        sidebar.setPreferredSize(new Dimension(210, 0));

        JPanel items = new JPanel(new GridBagLayout());
        items.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(0, 0, 20, 0);
        JLabel sectionTitle = new JLabel("WORKSPACE");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        sectionTitle.setForeground(UiStyle.MUTED);
        items.add(sectionTitle, constraints);

        for (String item : sidebarItems(user.getRole())) {
            constraints.gridy++;
            constraints.insets = new Insets(0, 0, 7, 0);
            JButton button = UiStyle.navigationButton(item, "Overview".equals(item));
            button.addActionListener(event -> showPage(item));
            navigationButtons.put(item, button);
            items.add(button, constraints);
        }
        GridBagConstraints outer = new GridBagConstraints();
        outer.gridx = 0;
        outer.gridy = 0;
        outer.anchor = GridBagConstraints.NORTHWEST;
        outer.fill = GridBagConstraints.HORIZONTAL;
        outer.weightx = 1;
        outer.insets = new Insets(28, 22, 0, 12);
        sidebar.add(items, outer);
        return sidebar;
    }

    private JPanel createDashboardPage(String page) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 18));
        wrapper.setBackground(UiStyle.BACKGROUND);
        wrapper.add(UiStyle.title(page), BorderLayout.NORTH);
        JPanel inner = switch (user.getRole()) {
            case BUYER -> createBuyerPage(page);
            case SELLER -> createSellerPage(page);
            case ADMIN -> createAdminPage(page);
        };
        wrapper.add(inner, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createBuyerPage(String page) {
        if (user instanceof Buyer buyer) {
            return switch (page) {
                case "Auctions" -> new BuyerPanel(buyer, auctionService, bidService);
                case "My bids" -> buildBuyerTablePage("My Bids",
                        new String[]{"Bid ID", "Auction ID", "Product Name", "Bid Amount", "Bid Date and Time", "Auction Status"},
                        () -> loadBuyerBidRows(buyer));
                case "Auction history" -> buildBuyerTablePage("Auction Results",
                        new String[]{"Auction ID", "Product Name", "Your Highest Bid", "Auction Status", "Result"},
                        () -> loadBuyerResultRows(buyer));
                default -> throw new IllegalArgumentException("Unknown Buyer dashboard page: " + page);
            };
        }
        throw new IllegalStateException("BUYER role does not match the account type");
    }

    private java.util.List<Object[]> loadBuyerBidRows(Buyer buyer) throws java.sql.SQLException {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (var bid : bidService.getBuyerHistory(buyer)) {
            var auction = auctionService.getAuctionDetails(bid.getAuctionId()).orElse(null);
            rows.add(new Object[]{
                    bid.getId(), bid.getAuctionId(),
                    auction == null ? "Auction unavailable" : auction.getProductName(),
                    bid.getAmount(), bid.getBidTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    auction == null ? "UNKNOWN" : auction.getStatus().name()
            });
        }
        return rows;
    }

    private java.util.List<Object[]> loadBuyerResultRows(Buyer buyer) throws java.sql.SQLException {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (var result : bidService.getBuyerAuctionResults(buyer)) {
            rows.add(new Object[]{
                    result.auction().getId(), result.auction().getProductName(),
                    result.highestPersonalBid(), result.auction().getStatus().name(),
                    switch (result.auction().getStatus()) {
                        case ACTIVE -> "ACTIVE";
                        case SOLD -> result.winningBid().filter(bid -> bid.getBuyerId() == buyer.getId()).isPresent() ? "WON" : "LOST";
                        case UNSOLD -> "UNSOLD";
                        case CANCELLED -> "CANCELLED";
                    }
            });
        }
        return rows;
    }

    private JPanel createSellerPage(String page) {
        if (user instanceof Seller seller) {
            return switch (page) {
                case "Create auction" -> new SellerPanel(seller, auctionService, bidService);
                case "My auctions" -> new SellerPanel(seller, auctionService, bidService);
                case "Results" -> new SellerPanel(seller, auctionService, bidService);
                default -> throw new IllegalArgumentException("Unknown Seller dashboard page: " + page);
            };
        }
        throw new IllegalStateException("SELLER role does not match the account type");
    }

    private JPanel createAdminPage(String page) {
        if (user instanceof Admin admin) {
            return switch (page) {
                case "Users" -> new AdminPanel(admin, adminService, auctionService);
                case "Auction monitoring" -> new AdminPanel(admin, adminService, auctionService);
                case "Moderation" -> new AdminPanel(admin, adminService, auctionService);
                default -> throw new IllegalArgumentException("Unknown Admin dashboard page: " + page);
            };
        }
        throw new IllegalStateException("ADMIN role does not match the account type");
    }

    private JPanel createPlaceholderPage(String page) {
        JPanel view = new JPanel(new BorderLayout(0, 18));
        view.setBackground(UiStyle.BACKGROUND);
        view.add(UiStyle.title(page), BorderLayout.NORTH);
        view.add(UiStyle.section(page, placeholderDescription(page)), BorderLayout.CENTER);
        return view;
    }

    private JPanel buildBuyerTablePage(String title, String[] columns, java.util.concurrent.Callable<java.util.List<Object[]>> loader) {
        JPanel view = new JPanel(new BorderLayout(0, 16));
        view.setBackground(UiStyle.BACKGROUND);
        view.add(UiStyle.title(title), BorderLayout.NORTH);
        JTable table = new JTable(new javax.swing.table.DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        table.setFont(UiStyle.BODY_FONT);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));
        view.add(scroll, BorderLayout.CENTER);
        new javax.swing.SwingWorker<java.util.List<Object[]>, Void>() {
            @Override
            protected java.util.List<Object[]> doInBackground() throws Exception {
                return loader.call();
            }

            @Override
            protected void done() {
                try {
                    java.util.List<Object[]> rows = get();
                    javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) table.getModel();
                    model.setRowCount(0);
                    for (Object[] row : rows) {
                        model.addRow(row);
                    }
                } catch (Exception exception) {
                    javax.swing.JOptionPane.showMessageDialog(DashboardFrame.this,
                            "Unable to load this buyer data.", "Buyer dashboard", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
        return view;
    }

    private void showPage(String page) {
        pageLayout.show(pages, page);
        navigationButtons.forEach((name, button) -> {
            boolean selected = name.equals(page);
            button.setBackground(selected ? new java.awt.Color(222, 235, 250)
                    : new java.awt.Color(238, 243, 249));
            button.setForeground(selected ? UiStyle.PRIMARY_DARK : UiStyle.TEXT);
        });
    }

    private String placeholderDescription(String page) {
        return switch (user.getRole()) {
            case BUYER -> switch (page) {
                case "Auctions" -> "Auction listings will be available in Phase 5.";
                case "My bids" -> "Your bidding history will be available in Phase 6.";
                case "Auction history" -> "Auction results and winning status will be available in Phase 6.";
                default -> throw new IllegalArgumentException("Unknown Buyer dashboard page: " + page);
            };
            case SELLER -> switch (page) {
                case "Create auction" -> "Auction creation will be available in Phase 5.";
                case "My auctions" -> "Your auction listings and activity will be available in Phase 5.";
                case "Results" -> "Final auction results will be available in Phase 6.";
                default -> throw new IllegalArgumentException("Unknown Seller dashboard page: " + page);
            };
            case ADMIN -> switch (page) {
                case "Users" -> "User administration will be available in Phase 7.";
                case "Auction monitoring" -> "Auction status monitoring will be available in Phase 7.";
                case "Moderation" -> "Listing moderation will be available in Phase 7.";
                default -> throw new IllegalArgumentException("Unknown Admin dashboard page: " + page);
            };
        };
    }

    private JPanel createRolePanel() {
        return switch (user.getRole()) {
            case BUYER -> {
                if (user instanceof Buyer buyer) {
                    yield new BuyerPanel(buyer, auctionService, bidService);
                }
                throw new IllegalStateException("BUYER role does not match the account type");
            }
            case SELLER -> {
                if (user instanceof Seller seller) {
                    yield new SellerPanel(seller, auctionService, bidService);
                }
                throw new IllegalStateException("SELLER role does not match the account type");
            }
            case ADMIN -> {
                if (user instanceof Admin admin) {
                    yield new AdminPanel(admin, adminService, auctionService);
                }
                throw new IllegalStateException("ADMIN role does not match the account type");
            }
        };
    }

    private void logout() {
        new LoginFrame(authService).setVisible(true);
        dispose();
    }

    private static String[] sidebarItems(UserRole role) {
        return switch (role) {
            case BUYER -> new String[]{"Overview", "Auctions", "My bids", "Auction history"};
            case SELLER -> new String[]{"Overview", "Create auction", "My auctions", "Results"};
            case ADMIN -> new String[]{"Overview", "Users", "Auction monitoring", "Moderation"};
        };
    }
}