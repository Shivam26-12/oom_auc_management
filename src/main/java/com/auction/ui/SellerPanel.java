package com.auction.ui;

import com.auction.dao.AuctionDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Seller;
import com.auction.service.AuctionService;
import com.auction.service.BidService;

import javax.swing.BorderFactory;
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
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public final class SellerPanel extends JPanel {
        private static final String[] CATEGORIES = {
                        "Select category", "Electronics", "Vehicles", "Fashion", "Furniture", "Collectibles", "Other"
        };
        private static final String[] COLUMNS = {
                        "Auction ID", "Product Name", "Category", "Starting Price",
                        "Current Highest Bid", "Number of Bids", "End Date", "Status", "Winning Buyer"
        };
        private static final DateTimeFormatter INPUT_DATE_FORMAT = new DateTimeFormatterBuilder()
                        .appendPattern("uuuu-MM-dd HH")
                        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                        .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
                            .toFormatter(Locale.ROOT)
                            .withResolverStyle(ResolverStyle.STRICT);
        private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        private final Seller seller;
        private final AuctionService auctionService;
        private final BidService bidService;
        private final JButton createButton = UiStyle.primaryButton("Create New Auction");
        private final JButton refreshButton = UiStyle.secondaryButton("Refresh");
        private final JButton viewBidsButton = UiStyle.secondaryButton("View Bids");
        private final JLabel statusLabel = UiStyle.subtitle("Loading your auctions...");
        private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                        return false;
                }
        };
        private final JTable auctionTable = new JTable(tableModel);

        public SellerPanel(Seller seller) {
                this(seller, new AuctionService(), new BidService());
        }

        public SellerPanel(Seller seller, AuctionService auctionService) {
                this(seller, auctionService, new BidService());
        }

        public SellerPanel(Seller seller, AuctionService auctionService, BidService bidService) {
                this.seller = seller;
                this.auctionService = auctionService;
                this.bidService = bidService;
                setLayout(new BorderLayout(0, 18));
                setBackground(UiStyle.BACKGROUND);
                buildInterface();
                loadAuctions();
        }

        private void buildInterface() {
                JPanel top = new JPanel(new BorderLayout(0, 14));
                top.setOpaque(false);
                JPanel heading = new JPanel(new BorderLayout(0, 5));
                heading.setOpaque(false);
                heading.add(UiStyle.title("My auctions"), BorderLayout.NORTH);
                heading.add(UiStyle.subtitle("Welcome, " + seller.getName() + "  |  Seller account"), BorderLayout.CENTER);
                top.add(heading, BorderLayout.NORTH);

                JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                controls.setOpaque(false);
                createButton.addActionListener(event -> openCreateAuctionDialog());
                refreshButton.addActionListener(event -> loadAuctions());
                viewBidsButton.addActionListener(event -> showSelectedAuctionBids());
                viewBidsButton.setEnabled(false);
                controls.add(createButton);
                controls.add(refreshButton);
                controls.add(viewBidsButton);
                top.add(controls, BorderLayout.CENTER);
                add(top, BorderLayout.NORTH);

                auctionTable.setFont(UiStyle.BODY_FONT);
                auctionTable.setForeground(UiStyle.TEXT);
                auctionTable.setRowHeight(30);
                auctionTable.setFillsViewportHeight(true);
                auctionTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
                auctionTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
                auctionTable.getTableHeader().setReorderingAllowed(false);
                auctionTable.getSelectionModel().addListSelectionListener(event -> {
                        if (!event.getValueIsAdjusting()) {
                                viewBidsButton.setEnabled(auctionTable.getSelectedRow() >= 0);
                        }
                });
                JScrollPane scrollPane = new JScrollPane(auctionTable);
                scrollPane.setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));

                JPanel tableArea = new JPanel(new BorderLayout(0, 8));
                tableArea.setOpaque(false);
                tableArea.add(scrollPane, BorderLayout.CENTER);
                tableArea.add(statusLabel, BorderLayout.SOUTH);
                add(tableArea, BorderLayout.CENTER);
        }

        private void openCreateAuctionDialog() {
                JDialog dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(this),
                                "Create New Auction", JDialog.ModalityType.APPLICATION_MODAL);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                JPanel form = new JPanel(new GridBagLayout());
                form.setBackground(UiStyle.SURFACE);
                form.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

                JTextField productNameField = new JTextField(24);
                JTextArea descriptionArea = new JTextArea(4, 24);
                JComboBox<String> categoryBox = new JComboBox<>(CATEGORIES);
                JTextField priceField = new JTextField(24);
                JTextField endTimeField = new JTextField(24);
                UiStyle.styleField(productNameField);
                UiStyle.styleField(priceField);
                UiStyle.styleField(endTimeField);
                descriptionArea.setFont(UiStyle.BODY_FONT);
                descriptionArea.setLineWrap(true);
                descriptionArea.setWrapStyleWord(true);
                descriptionArea.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(UiStyle.BORDER), BorderFactory.createEmptyBorder(8, 9, 8, 9)));
                categoryBox.setFont(UiStyle.BODY_FONT);
                categoryBox.setBackground(UiStyle.SURFACE);

                GridBagConstraints constraints = new GridBagConstraints();
                constraints.gridx = 0;
                constraints.gridy = 0;
                constraints.anchor = GridBagConstraints.WEST;
                constraints.fill = GridBagConstraints.HORIZONTAL;
                constraints.weightx = 1;
                constraints.insets = new Insets(0, 0, 12, 0);
                form.add(UiStyle.title("Create New Auction"), constraints);
                addFormRow(form, constraints, "Product name", productNameField);
                addFormRow(form, constraints, "Description", new JScrollPane(descriptionArea));
                addFormRow(form, constraints, "Category", categoryBox);
                addFormRow(form, constraints, "Starting price", priceField);
                addFormRow(form, constraints, "Auction end date", endTimeField);

                constraints.gridy++;
                constraints.insets = new Insets(-6, 0, 14, 0);
                JLabel dateHint = UiStyle.subtitle("Format: yyyy-MM-dd HH  (example: 2026-12-31 18)");
                form.add(dateHint, constraints);

                JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                buttons.setOpaque(false);
                JButton cancelButton = UiStyle.secondaryButton("Cancel");
                JButton submitButton = UiStyle.primaryButton("Create auction");
                cancelButton.addActionListener(event -> dialog.dispose());
                submitButton.addActionListener(event -> submitAuction(dialog, productNameField, descriptionArea,
                                categoryBox, priceField, endTimeField, submitButton));
                buttons.add(cancelButton);
                buttons.add(submitButton);
                constraints.gridy++;
                constraints.insets = new Insets(0, 0, 0, 0);
                form.add(buttons, constraints);

                dialog.setContentPane(form);
                dialog.pack();
                dialog.setMinimumSize(new Dimension(490, 570));
                dialog.setLocationRelativeTo(this);
                dialog.setVisible(true);
        }

        private void submitAuction(JDialog dialog, JTextField productNameField, JTextArea descriptionArea,
                                                           JComboBox<String> categoryBox, JTextField priceField,
                                                           JTextField endTimeField, JButton submitButton) {
                String productName = productNameField.getText().trim();
                String description = descriptionArea.getText().trim();
                Object categorySelection = categoryBox.getSelectedItem();
                String category = categorySelection == null ? "" : categorySelection.toString();
                String priceText = priceField.getText().trim();
                String endTimeText = endTimeField.getText().trim();

                if (productName.isEmpty() || description.isEmpty() || priceText.isEmpty() || endTimeText.isEmpty()) {
                        showError("Complete all required fields.", dialog);
                        return;
                }
                if (categoryBox.getSelectedIndex() <= 0) {
                        showError("Select a product category.", dialog);
                        return;
                }

                BigDecimal startingPrice;
                try {
                        double numericPrice = Double.parseDouble(priceText);
                        if (!Double.isFinite(numericPrice) || numericPrice <= 0) {
                                throw new NumberFormatException("Price must be finite and positive");
                        }
                        startingPrice = new BigDecimal(priceText);
                        if (startingPrice.signum() <= 0) {
                                throw new NumberFormatException("Price must be positive");
                        }
                } catch (NumberFormatException exception) {
                        showError("Starting price must be a finite number greater than zero.", dialog);
                        return;
                }

                LocalDateTime endTime;
                try {
                        endTime = LocalDateTime.parse(endTimeText, INPUT_DATE_FORMAT);
                } catch (DateTimeParseException exception) {
                        showError("Enter the end date using yyyy-MM-dd HH, for example 2026-12-31 18.", dialog);
                        return;
                }
                if (!endTime.isAfter(LocalDateTime.now())) {
                        showError("Auction end date must be in the future.", dialog);
                        return;
                }

                Auction auction;
                try {
                        auction = new Auction(0, seller.getId(), productName, description, category, startingPrice,
                                        endTime, AuctionStatus.ACTIVE, LocalDateTime.now());
                } catch (IllegalArgumentException | NullPointerException exception) {
                        showError(exception.getMessage(), dialog);
                        return;
                }

                submitButton.setEnabled(false);
                new SwingWorker<Auction, Void>() {
                        @Override
                        protected Auction doInBackground() throws Exception {
                                return auctionService.createAuction(seller, auction);
                        }

                        @Override
                        protected void done() {
                                submitButton.setEnabled(true);
                                try {
                                        Auction savedAuction = get();
                                        JOptionPane.showMessageDialog(dialog,
                                                        "Auction created successfully. Listing ID: " + savedAuction.getId(),
                                                        "Auction created", JOptionPane.INFORMATION_MESSAGE);
                                        dialog.dispose();
                                        loadAuctions();
                                } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        showError("Auction creation was interrupted.", dialog);
                                } catch (ExecutionException exception) {
                                        Throwable cause = exception.getCause();
                                        if (cause instanceof IllegalArgumentException) {
                                                showError(cause.getMessage(), dialog);
                                        } else {
                                                showError("Unable to save the auction. Please try again.", dialog);
                                                System.err.println("Auction creation failed: " + cause.getMessage());
                                        }
                                }
                        }
                }.execute();
        }

        private void loadAuctions() {
                if (seller.getId() <= 0) {
                        tableModel.setRowCount(0);
                        statusLabel.setText("Sign in with a Seller account to view your auctions.");
                        return;
                }
                setLoading(true);
                statusLabel.setText("Loading your auctions...");
                new SwingWorker<List<BidService.SellerAuctionSummary>, Void>() {
                        @Override
                        protected List<BidService.SellerAuctionSummary> doInBackground() throws Exception {
                                return bidService.getSellerAuctionSummaries(seller);
                        }

                        @Override
                        protected void done() {
                                setLoading(false);
                                try {
                                        List<BidService.SellerAuctionSummary> rows = get();
                                        tableModel.setRowCount(0);
                                        for (BidService.SellerAuctionSummary row : rows) {
                                                Auction auction = row.auction();
                                                tableModel.addRow(new Object[]{auction.getId(), auction.getProductName(),
                                                                auction.getCategory(), formatPrice(auction.getStartingPrice()),
                                                                formatPrice(row.currentHighestBid()), row.bidCount(),
                                                                auction.getEndTime().format(DISPLAY_DATE_FORMAT),
                                                                auction.getStatus().name(), row.winningBuyerName() == null
                                                                                ? "-" : row.winningBuyerName()});
                                        }
                                        statusLabel.setText(rows.isEmpty()
                                                        ? "You have not created any auctions yet."
                                                        : rows.size() + (rows.size() == 1 ? " auction" : " auctions"));
                                } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        statusLabel.setText("Loading was interrupted.");
                                } catch (ExecutionException exception) {
                                        statusLabel.setText("Your auctions could not be loaded.");
                                                                                showError("Unable to load your auctions. Please try again.", SellerPanel.this);
                                        System.err.println("Seller auction load failed: " + exception.getCause().getMessage());
                                }
                        }
                }.execute();
        }

        private void showSelectedAuctionBids() {
                int selectedRow = auctionTable.getSelectedRow();
                if (selectedRow < 0) {
                        JOptionPane.showMessageDialog(this, "Select one of your auctions to view its bids.",
                                        "Auction bids", JOptionPane.INFORMATION_MESSAGE);
                        return;
                }
                int modelRow = auctionTable.convertRowIndexToModel(selectedRow);
                int auctionId = ((Number) tableModel.getValueAt(modelRow, 0)).intValue();
                viewBidsButton.setEnabled(false);
                new SwingWorker<List<BidService.SellerBidDetails>, Void>() {
                        @Override
                        protected List<BidService.SellerBidDetails> doInBackground() throws Exception {
                                return bidService.getBidsForSellerAuction(seller, auctionId);
                        }

                        @Override
                        protected void done() {
                                viewBidsButton.setEnabled(auctionTable.getSelectedRow() >= 0);
                                try {
                                        List<BidService.SellerBidDetails> bids = get();
                                        if (bids.isEmpty()) {
                                                JOptionPane.showMessageDialog(SellerPanel.this,
                                                                "No bids have been placed on this auction.",
                                                                "Auction bids", JOptionPane.INFORMATION_MESSAGE);
                                                return;
                                        }
                                        DefaultTableModel model = new DefaultTableModel(
                                                        new String[]{"Bid ID", "Buyer Name", "Bid Amount", "Bid Time"}, 0) {
                                                @Override
                                                public boolean isCellEditable(int row, int column) {
                                                        return false;
                                                }
                                        };
                                        for (BidService.SellerBidDetails detail : bids) {
                                                model.addRow(new Object[]{detail.bid().getId(), detail.buyerName(),
                                                                formatPrice(detail.bid().getAmount()),
                                                                detail.bid().getBidTime().format(DISPLAY_DATE_FORMAT)});
                                        }
                                        JTable table = new JTable(model);
                                        table.setFont(UiStyle.BODY_FONT);
                                        table.setRowHeight(28);
                                        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
                                        JScrollPane scrollPane = new JScrollPane(table);
                                        scrollPane.setPreferredSize(new Dimension(560,
                                                        Math.min(340, 60 + bids.size() * 28)));
                                        JOptionPane.showMessageDialog(SellerPanel.this, scrollPane,
                                                        "Auction bids", JOptionPane.PLAIN_MESSAGE);
                                } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        showError("Loading auction bids was interrupted.", SellerPanel.this);
                                } catch (ExecutionException exception) {
                                        Throwable cause = exception.getCause();
                                        showError(cause instanceof IllegalArgumentException
                                                        ? cause.getMessage()
                                                        : "Unable to load bids for this auction.", SellerPanel.this);
                                }
                        }
                }.execute();
        }

        private void setLoading(boolean loading) {
                createButton.setEnabled(!loading);
                refreshButton.setEnabled(!loading);
                viewBidsButton.setEnabled(!loading && auctionTable.getSelectedRow() >= 0);
        }

        private static void addFormRow(JPanel form, GridBagConstraints constraints, String label, java.awt.Component input) {
                constraints.gridy++;
                constraints.insets = new Insets(0, 0, 5, 0);
                JLabel fieldLabel = new JLabel(label);
                fieldLabel.setFont(UiStyle.BODY_FONT);
                fieldLabel.setForeground(UiStyle.TEXT);
                form.add(fieldLabel, constraints);
                constraints.gridy++;
                constraints.insets = new Insets(0, 0, 12, 0);
                input.setPreferredSize(new Dimension(350, input instanceof JScrollPane ? 98 : 40));
                form.add(input, constraints);
        }

        private static String formatPrice(BigDecimal price) {
                return java.text.NumberFormat.getCurrencyInstance(Locale.getDefault()).format(price);
        }

        private static void showError(String message, java.awt.Component parent) {
                JOptionPane.showMessageDialog(parent, message, "Create auction", JOptionPane.ERROR_MESSAGE);
        }

}