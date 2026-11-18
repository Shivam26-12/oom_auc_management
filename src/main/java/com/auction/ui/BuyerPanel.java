package com.auction.ui;

import com.auction.dao.AuctionDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.Buyer;
import com.auction.service.AuctionService;
import com.auction.service.BidService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public final class BuyerPanel extends JPanel {
        private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        private static final String[] COLUMNS = {
                        "Auction ID", "Product Name", "Category", "Starting Price",
                        "Current Highest Bid", "Auction End Time", "Status"
        };

        private final Buyer buyer;
        private final AuctionService auctionService;
        private final BidService bidService;
        private final JTextField searchField = new JTextField(22);
        private final JButton searchButton = UiStyle.primaryButton("Search");
        private final JButton clearButton = UiStyle.secondaryButton("Clear search");
        private final JButton refreshButton = UiStyle.secondaryButton("Refresh");
        private final JButton detailsButton = UiStyle.secondaryButton("View details");
        private final JButton placeBidButton = UiStyle.primaryButton("Place Bid");
        private final JButton myBidsButton = UiStyle.secondaryButton("My Bids");
        private final JButton resultsButton = UiStyle.secondaryButton("Auction Results");
        private final JLabel statusLabel = UiStyle.subtitle("Loading active auctions...");
        private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                        return false;
                }
        };
        private final JTable auctionTable = new JTable(tableModel);
        private List<Auction> displayedAuctions = List.of();

        public BuyerPanel(Buyer buyer) {
                this(buyer, new AuctionService(), new BidService());
        }

        public BuyerPanel(Buyer buyer, AuctionService auctionService) {
                this(buyer, auctionService, new BidService());
        }

        public BuyerPanel(Buyer buyer, AuctionService auctionService, BidService bidService) {
                this.buyer = buyer;
                this.auctionService = auctionService;
                this.bidService = bidService;
                setLayout(new BorderLayout(0, 18));
                setBackground(UiStyle.BACKGROUND);
                buildInterface();
                loadAuctions("");
        }

        private void buildInterface() {
                JPanel top = new JPanel(new BorderLayout(0, 14));
                top.setOpaque(false);
                JPanel heading = new JPanel(new BorderLayout(0, 5));
                heading.setOpaque(false);
                heading.add(UiStyle.title("Browse auctions"), BorderLayout.NORTH);
                heading.add(UiStyle.subtitle("Welcome, " + buyer.getName() + "  |  Buyer account"), BorderLayout.CENTER);
                top.add(heading, BorderLayout.NORTH);

                JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                controls.setOpaque(false);
                UiStyle.styleField(searchField);
                searchField.setToolTipText("Search product names");
                searchField.setPreferredSize(new Dimension(250, 40));
                searchField.addActionListener(event -> searchAuctions());
                searchButton.addActionListener(event -> searchAuctions());
                clearButton.addActionListener(event -> {
                        searchField.setText("");
                        loadAuctions("");
                });
                refreshButton.addActionListener(event -> loadAuctions(searchField.getText().trim()));
                controls.add(searchField);
                controls.add(searchButton);
                controls.add(clearButton);
                controls.add(refreshButton);
                top.add(controls, BorderLayout.CENTER);
                add(top, BorderLayout.NORTH);

                auctionTable.setFont(UiStyle.BODY_FONT);
                auctionTable.setForeground(UiStyle.TEXT);
                auctionTable.setRowHeight(30);
                auctionTable.setFillsViewportHeight(true);
                auctionTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
                auctionTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
                auctionTable.getTableHeader().setReorderingAllowed(false);
                auctionTable.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mouseClicked(java.awt.event.MouseEvent event) {
                                if (event.getClickCount() == 2 && auctionTable.getSelectedRow() >= 0) {
                                        showSelectedDetails();
                                }
                        }
                });

                JPanel tableArea = new JPanel(new BorderLayout(0, 8));
                tableArea.setOpaque(false);
                JScrollPane scrollPane = new JScrollPane(auctionTable);
                scrollPane.setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));
                tableArea.add(scrollPane, BorderLayout.CENTER);
                JPanel footer = new JPanel(new BorderLayout());
                footer.setOpaque(false);
                footer.add(statusLabel, BorderLayout.WEST);
                detailsButton.addActionListener(event -> showSelectedDetails());
                placeBidButton.addActionListener(event -> openPlaceBidDialog());
                myBidsButton.addActionListener(event -> showMyBids());
                resultsButton.addActionListener(event -> showAuctionResults());
                placeBidButton.setEnabled(false);
                auctionTable.getSelectionModel().addListSelectionListener(event -> {
                        if (!event.getValueIsAdjusting()) {
                                updatePlaceBidButton();
                        }
                });
                JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                actions.setOpaque(false);
                actions.add(myBidsButton);
                actions.add(resultsButton);
                actions.add(placeBidButton);
                actions.add(detailsButton);
                footer.add(actions, BorderLayout.EAST);
                tableArea.add(footer, BorderLayout.SOUTH);
                add(tableArea, BorderLayout.CENTER);
        }

        private void searchAuctions() {
                loadAuctions(searchField.getText().trim());
        }

        private void loadAuctions(String keyword) {
                if (buyer.getId() <= 0) {
                        displayedAuctions = List.of();
                        tableModel.setRowCount(0);
                        statusLabel.setText("Sign in with a Buyer account to browse auctions.");
                        return;
                }
                setLoading(true);
                statusLabel.setText("Loading active auctions...");
                new SwingWorker<List<AuctionRow>, Void>() {
                        @Override
                        protected List<AuctionRow> doInBackground() throws Exception {
                                List<Auction> auctions = keyword.isBlank()
                                                ? auctionService.getActiveAuctions()
                                                : auctionService.searchAuctions(keyword);
                                List<AuctionRow> rows = new ArrayList<>(auctions.size());
                                for (Auction auction : auctions) {
                                        BigDecimal highestBid = auctionService.getCurrentHighestBid(auction.getId());
                                        rows.add(new AuctionRow(auction, highestBid));
                                }
                                return rows;
                        }

                        @Override
                        protected void done() {
                                setLoading(false);
                                try {
                                        List<AuctionRow> rows = get();
                                        displayedAuctions = rows.stream().map(AuctionRow::auction).toList();
                                        tableModel.setRowCount(0);
                                        for (AuctionRow row : rows) {
                                                Auction auction = row.auction();
                                                tableModel.addRow(new Object[]{auction.getId(), auction.getProductName(),
                                                                auction.getCategory(), formatPrice(auction.getStartingPrice()),
                                                                formatPrice(row.currentHighestBid()), formatDate(auction),
                                                                auction.getStatus().name()});
                                        }
                                        statusLabel.setText(rows.isEmpty()
                                                        ? "No active auctions match your search."
                                                        : rows.size() + (rows.size() == 1 ? " active auction" : " active auctions"));
                                        updatePlaceBidButton();
                                } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        statusLabel.setText("Loading was interrupted.");
                                } catch (ExecutionException exception) {
                                        statusLabel.setText("Auction listings could not be loaded.");
                                        showError("Unable to load auction listings. Please try again.");
                                        System.err.println("Auction listing load failed: " + exception.getCause().getMessage());
                                }
                        }
                }.execute();
        }

        private void showSelectedDetails() {
                int selectedRow = auctionTable.getSelectedRow();
                if (selectedRow < 0 || selectedRow >= displayedAuctions.size()) {
                        JOptionPane.showMessageDialog(this, "Select an auction to view its details.",
                                        "Auction details", JOptionPane.INFORMATION_MESSAGE);
                        return;
                }
                int auctionId = displayedAuctions.get(selectedRow).getId();
                detailsButton.setEnabled(false);
                new SwingWorker<AuctionDetails, Void>() {
                        @Override
                        protected AuctionDetails doInBackground() throws Exception {
                                Auction auction = auctionService.getAuctionDetails(auctionId)
                                                .orElseThrow(() -> new IllegalArgumentException("Auction is no longer available"));
                                return new AuctionDetails(auction, auctionService.getCurrentHighestBid(auctionId),
                                                bidService.countBidsByAuction(auctionId),
                                                bidService.getWinningBidDetails(auctionId));
                        }

                        @Override
                        protected void done() {
                                detailsButton.setEnabled(true);
                                try {
                                        showDetailsDialog(get());
                                        loadAuctions(searchField.getText().trim());
                                } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        showError("Loading auction details was interrupted.");
                                } catch (ExecutionException exception) {
                                        showError("Unable to load this auction's details.");
                                }
                        }
                }.execute();
        }

        private void updatePlaceBidButton() {
                int selectedRow = auctionTable.getSelectedRow();
                boolean canBid = selectedRow >= 0 && selectedRow < displayedAuctions.size()
                                && displayedAuctions.get(selectedRow).getStatus() == AuctionStatus.ACTIVE;
                placeBidButton.setEnabled(canBid);
        }

        private void openPlaceBidDialog() {
                int selectedRow = auctionTable.getSelectedRow();
                if (selectedRow < 0 || selectedRow >= displayedAuctions.size()) {
                        JOptionPane.showMessageDialog(this, "Select an active auction first.",
                                        "Place Bid", JOptionPane.INFORMATION_MESSAGE);
                        return;
                }
                int auctionId = displayedAuctions.get(selectedRow).getId();
                placeBidButton.setEnabled(false);
                new SwingWorker<AuctionRow, Void>() {
                        @Override
                        protected AuctionRow doInBackground() throws Exception {
                                Auction auction = auctionService.getAuctionDetails(auctionId)
                                                .orElseThrow(() -> new IllegalArgumentException("Auction was not found"));
                                return new AuctionRow(auction, auctionService.getCurrentHighestBid(auctionId));
                        }

                        @Override
                        protected void done() {
                                updatePlaceBidButton();
                                try {
                                        AuctionRow row = get();
                                        if (row.auction().getStatus() != AuctionStatus.ACTIVE) {
                                                loadAuctions(searchField.getText().trim());
                                                showError("This auction is no longer accepting bids.");
                                                return;
                                        }
                                        submitBid(row);
                                } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        showError("Loading the auction was interrupted.");
                                } catch (ExecutionException exception) {
                                        showError("Unable to load the selected auction.");
                                }
                        }
                }.execute();
        }

        private void submitBid(AuctionRow auctionRow) {
                Auction auction = auctionRow.auction();
                JTextField amountField = new JTextField(16);
                JPanel bidForm = new JPanel(new GridBagLayout());
                bidForm.setBackground(UiStyle.SURFACE);
                GridBagConstraints constraints = new GridBagConstraints();
                constraints.gridx = 0;
                constraints.gridy = 0;
                constraints.gridwidth = 2;
                constraints.anchor = GridBagConstraints.WEST;
                constraints.fill = GridBagConstraints.HORIZONTAL;
                constraints.insets = new Insets(0, 0, 10, 0);
                bidForm.add(UiStyle.title(auction.getProductName()), constraints);
                addBidDetail(bidForm, constraints, "Starting price", formatPrice(auction.getStartingPrice()));
                addBidDetail(bidForm, constraints, "Current highest bid", formatPrice(auctionRow.currentHighestBid()));
                constraints.gridy++;
                constraints.gridwidth = 1;
                constraints.insets = new Insets(6, 0, 0, 12);
                bidForm.add(new JLabel("Your bid"), constraints);
                constraints.gridx = 1;
                constraints.weightx = 1;
                constraints.insets = new Insets(6, 0, 0, 0);
                UiStyle.styleField(amountField);
                bidForm.add(amountField, constraints);

                int choice = JOptionPane.showConfirmDialog(this, bidForm, "Place Bid",
                                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (choice != JOptionPane.OK_OPTION) {
                        return;
                }
                double amount;
                try {
                        amount = Double.parseDouble(amountField.getText().trim());
                        if (!Double.isFinite(amount) || amount <= 0) {
                                throw new NumberFormatException("Bid amount must be finite and positive");
                        }
                } catch (NumberFormatException exception) {
                        showError("Enter a finite bid amount greater than zero.");
                        return;
                }

                setLoading(true);
                new SwingWorker<Bid, Void>() {
                        @Override
                        protected Bid doInBackground() throws Exception {
                                return bidService.placeBid(buyer, auction.getId(), amount);
                        }

                        @Override
                        protected void done() {
                                setLoading(false);
                                try {
                                        Bid bid = get();
                                        JOptionPane.showMessageDialog(BuyerPanel.this,
                                                        "Your bid was placed successfully.", "Bid accepted",
                                                        JOptionPane.INFORMATION_MESSAGE);
                                        loadAuctions(searchField.getText().trim());
                                } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        showError("Bid submission was interrupted.");
                                } catch (ExecutionException exception) {
                                        Throwable cause = exception.getCause();
                                        showError(cause instanceof IllegalArgumentException
                                                        ? cause.getMessage()
                                                        : "Unable to place your bid. Please try again.");
                                }
                        }
                }.execute();
        }

        private void showMyBids() {
                myBidsButton.setEnabled(false);
                new SwingWorker<List<Object[]>, Void>() {
                        @Override
                        protected List<Object[]> doInBackground() throws Exception {
                                List<Object[]> rows = new ArrayList<>();
                                for (Bid bid : bidService.getBuyerHistory(buyer)) {
                                        Auction auction = auctionService.getAuctionDetails(bid.getAuctionId()).orElse(null);
                                        rows.add(new Object[]{bid.getId(), bid.getAuctionId(),
                                                        auction == null ? "Auction unavailable" : auction.getProductName(),
                                                        formatPrice(bid.getAmount()), bid.getBidTime().format(DATE_FORMAT),
                                                        auction == null ? "UNKNOWN" : auction.getStatus().name()});
                                }
                                return rows;
                        }

                        @Override
                        protected void done() {
                                myBidsButton.setEnabled(true);
                                try {
                                        showTableDialog("My Bids", new String[]{"Bid ID", "Auction ID", "Product Name",
                                                        "Bid Amount", "Bid Date and Time", "Auction Status"}, get(),
                                                        "You have not placed any bids yet.");
                                } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        showError("Loading your bid history was interrupted.");
                                } catch (ExecutionException exception) {
                                        showError("Unable to load your bid history.");
                                }
                        }
                }.execute();
        }

        private void showAuctionResults() {
                resultsButton.setEnabled(false);
                new SwingWorker<List<Object[]>, Void>() {
                        @Override
                        protected List<Object[]> doInBackground() throws Exception {
                                List<Object[]> rows = new ArrayList<>();
                                for (BidService.BuyerAuctionResult result : bidService.getBuyerAuctionResults(buyer)) {
                                        Auction auction = result.auction();
                                        String outcome = switch (auction.getStatus()) {
                                                case ACTIVE -> "ACTIVE";
                                                case SOLD -> result.winningBid().filter(
                                                        bid -> bid.getBuyerId() == buyer.getId()).isPresent() ? "WON" : "LOST";
                                                case UNSOLD -> "UNSOLD";
                                                case CANCELLED -> "CANCELLED";
                                        };
                                        rows.add(new Object[]{auction.getId(), auction.getProductName(),
                                                        formatPrice(result.highestPersonalBid()),
                                                        auction.getStatus().name(), outcome});
                                }
                                return rows;
                        }

                        @Override
                        protected void done() {
                                resultsButton.setEnabled(true);
                                try {
                                        showTableDialog("Auction Results", new String[]{"Auction ID", "Product Name",
                                                        "Your Highest Bid", "Auction Status", "Result"}, get(),
                                                        "There are no auction results to show yet.");
                                } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        showError("Loading auction results was interrupted.");
                                } catch (ExecutionException exception) {
                                        showError("Unable to load your auction results.");
                                }
                        }
                }.execute();
        }

        private void showTableDialog(String title, String[] columns, List<Object[]> rows, String emptyMessage) {
                if (rows.isEmpty()) {
                        JOptionPane.showMessageDialog(this, emptyMessage, title, JOptionPane.INFORMATION_MESSAGE);
                        return;
                }
                DefaultTableModel model = new DefaultTableModel(columns, 0) {
                        @Override
                        public boolean isCellEditable(int row, int column) {
                                return false;
                        }
                };
                rows.forEach(model::addRow);
                JTable table = new JTable(model);
                table.setFont(UiStyle.BODY_FONT);
                table.setRowHeight(28);
                table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
                table.getTableHeader().setReorderingAllowed(false);
                JScrollPane scrollPane = new JScrollPane(table);
                scrollPane.setPreferredSize(new Dimension(760, Math.min(360, 60 + rows.size() * 28)));
                JOptionPane.showMessageDialog(this, scrollPane, title, JOptionPane.PLAIN_MESSAGE);
        }

        private void showDetailsDialog(AuctionDetails details) {
                Auction auction = details.auction();
                JDialog dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(this),
                                "Auction details", JDialog.ModalityType.APPLICATION_MODAL);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                JPanel content = new JPanel(new GridBagLayout());
                content.setBackground(UiStyle.SURFACE);
                content.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
                GridBagConstraints constraints = new GridBagConstraints();
                constraints.gridx = 0;
                constraints.gridy = 0;
                constraints.gridwidth = 2;
                constraints.weightx = 1;
                constraints.anchor = GridBagConstraints.WEST;
                constraints.fill = GridBagConstraints.HORIZONTAL;
                constraints.insets = new Insets(0, 0, 16, 0);
                content.add(UiStyle.title(auction.getProductName()), constraints);
                addDetail(content, constraints, "Description", auction.getDescription());
                addDetail(content, constraints, "Category", auction.getCategory());
                addDetail(content, constraints, "Seller ID", Integer.toString(auction.getSellerId()));
                addDetail(content, constraints, "Starting price", formatPrice(auction.getStartingPrice()));
                addDetail(content, constraints, "Current highest bid", formatPrice(details.currentHighestBid()));
                addDetail(content, constraints, "Total bids", Integer.toString(details.bidCount()));
                addDetail(content, constraints, "Auction end", formatDate(auction));
                addDetail(content, constraints, "Status", auction.getStatus().name());
                if (auction.getStatus() == AuctionStatus.SOLD && details.winningBid().isPresent()) {
                        BidService.WinningBidDetails winner = details.winningBid().orElseThrow();
                        addDetail(content, constraints, "Winning bid", formatPrice(winner.bid().getAmount()));
                        addDetail(content, constraints, "Winner", winner.buyerName());
                } else if (auction.getStatus() == AuctionStatus.UNSOLD) {
                        addDetail(content, constraints, "Result", "No valid bids were placed. This auction ended without a winner.");
                }

                JButton close = UiStyle.secondaryButton("Close");
                close.addActionListener(event -> dialog.dispose());
                constraints.gridy++;
                constraints.gridwidth = 2;
                constraints.insets = new Insets(8, 0, 0, 0);
                content.add(close, constraints);
                dialog.setContentPane(content);
                dialog.pack();
                dialog.setMinimumSize(new Dimension(460, 420));
                dialog.setLocationRelativeTo(this);
                dialog.setVisible(true);
        }

        private static void addBidDetail(JPanel content, GridBagConstraints constraints, String label, String value) {
                constraints.gridy++;
                constraints.gridwidth = 1;
                constraints.weightx = 0;
                constraints.insets = new Insets(0, 0, 8, 12);
                JLabel key = new JLabel(label);
                key.setFont(new Font("Segoe UI", Font.BOLD, 13));
                key.setForeground(UiStyle.TEXT);
                content.add(key, constraints);
                constraints.gridx = 1;
                constraints.weightx = 1;
                constraints.insets = new Insets(0, 0, 8, 0);
                JLabel detail = new JLabel(value);
                detail.setFont(UiStyle.BODY_FONT);
                detail.setForeground(UiStyle.MUTED);
                content.add(detail, constraints);
                constraints.gridx = 0;
        }

        private static void addDetail(JPanel content, GridBagConstraints constraints, String label, String value) {
                constraints.gridy++;
                constraints.gridwidth = 1;
                constraints.weightx = 0;
                constraints.insets = new Insets(0, 0, 11, 16);
                JLabel name = new JLabel(label);
                name.setFont(new Font("Segoe UI", Font.BOLD, 13));
                name.setForeground(UiStyle.TEXT);
                content.add(name, constraints);

                constraints.gridx = 1;
                constraints.weightx = 1;
                constraints.insets = new Insets(0, 0, 11, 0);
                if ("Description".equals(label)) {
                        JTextArea description = new JTextArea(value == null ? "Not provided" : value, 3, 32);
                        description.setEditable(false);
                        description.setLineWrap(true);
                        description.setWrapStyleWord(true);
                        description.setFont(UiStyle.BODY_FONT);
                        description.setBackground(UiStyle.SURFACE);
                        content.add(description, constraints);
                } else {
                        JLabel detail = new JLabel(value == null || value.isBlank() ? "Not provided" : value);
                        detail.setFont(UiStyle.BODY_FONT);
                        detail.setForeground(UiStyle.MUTED);
                        content.add(detail, constraints);
                }
                constraints.gridx = 0;
        }

        private void setLoading(boolean loading) {
                searchButton.setEnabled(!loading);
                clearButton.setEnabled(!loading);
                refreshButton.setEnabled(!loading);
                auctionTable.setEnabled(!loading);
                detailsButton.setEnabled(!loading);
                placeBidButton.setEnabled(!loading && auctionTable.getSelectedRow() >= 0
                                && auctionTable.getSelectedRow() < displayedAuctions.size()
                                && displayedAuctions.get(auctionTable.getSelectedRow()).getStatus() == AuctionStatus.ACTIVE);
                myBidsButton.setEnabled(!loading);
                resultsButton.setEnabled(!loading);
        }

        private void showError(String message) {
                JOptionPane.showMessageDialog(this, message, "Auctions", JOptionPane.ERROR_MESSAGE);
        }

        private static String formatPrice(BigDecimal price) {
                return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(price);
        }

        private static String formatDate(Auction auction) {
                return auction.getEndTime().format(DATE_FORMAT);
        }

        private record AuctionRow(Auction auction, BigDecimal currentHighestBid) {
        }

        private record AuctionDetails(Auction auction, BigDecimal currentHighestBid, int bidCount,
                                      java.util.Optional<BidService.WinningBidDetails> winningBid) {
        }
}