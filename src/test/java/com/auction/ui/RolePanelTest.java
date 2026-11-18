package com.auction.ui;

import com.auction.model.Admin;
import com.auction.model.Buyer;
import com.auction.model.Seller;
import com.auction.dao.UserDAO;
import com.auction.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JTable;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePanelTest {
    @Test
    void buyerPanelShowsAccountBrowsingControlsAndAuctionColumns() throws Exception {
        assertPanelContains(() -> new BuyerPanel(new Buyer("Buyer One", "buyer@example.test")),
            "Welcome, Buyer One", "Buyer account", "Search", "Clear search", "Refresh", "View details",
            "Place Bid", "My Bids", "Auction Results",
                "Auction ID", "Current Highest Bid", "Auction End Time");
    }

    @Test
    void sellerPanelShowsAuctionCreationControlsAndAuctionColumns() throws Exception {
        assertPanelContains(() -> new SellerPanel(new Seller("Seller One", "seller@example.test")),
            "Welcome, Seller One", "Seller account", "Create New Auction", "Refresh", "View Bids", "Auction ID",
                "Current Highest Bid", "End Date", "Status");
    }

    @Test
    void adminPanelShowsAccountAndFutureFeaturePlaceholders() throws Exception {
        assertPanelContains(() -> new AdminPanel(new Admin("Admin One", "admin@example.test")),
                "Welcome, Admin One", "Administrator account", "User management", "Auction monitoring",
                "Listing moderation");
    }

    @Test
    void buyerSidebarButtonChangesTheDashboardPage() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicReference<Boolean> navigated = new AtomicReference<>(false);
        SwingUtilities.invokeAndWait(() -> {
            DashboardFrame dashboard = new DashboardFrame(
                    new Buyer("Buyer One", "buyer@example.test"), new AuthService(new UserDAO()));
            JPanel cardPanel = findCardPanel(dashboard);
            JButton auctionsButton = findButton(dashboard, "Auctions");
            if (cardPanel != null && auctionsButton != null) {
                auctionsButton.doClick();
                navigated.set(containsVisibleLabel(cardPanel, "Auctions"));
            }
            dashboard.dispose();
        });
        assertTrue(navigated.get(), "Clicking the Auctions sidebar button should show its page");
    }

    private static void assertPanelContains(Supplier<JPanel> panelSupplier, String... expectedText) throws Exception {
        AtomicReference<String> componentText = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentText.set(readLabels(panelSupplier.get())));
        for (String expected : expectedText) {
            assertTrue(componentText.get().contains(expected), "Panel is missing text: " + expected);
        }
    }

    private static String readLabels(Container container) {
        StringBuilder text = new StringBuilder();
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label) {
                text.append(label.getText()).append(' ');
            }
            if (component instanceof JButton button) {
                text.append(button.getText()).append(' ');
            }
            if (component instanceof JTable table) {
                for (int column = 0; column < table.getColumnCount(); column++) {
                    text.append(table.getColumnName(column)).append(' ');
                }
            }
            if (component instanceof Container child) {
                text.append(readLabels(child));
            }
        }
        return text.toString();
    }

    private static JPanel findCardPanel(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JPanel panel && panel.getLayout() instanceof CardLayout) {
                return panel;
            }
            if (component instanceof Container child) {
                JPanel result = findCardPanel(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                JButton result = findButton(child, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static boolean containsVisibleLabel(Container container, String text) {
        if (!container.isVisible()) {
            return false;
        }
        for (Component component : container.getComponents()) {
            if (!component.isVisible()) {
                continue;
            }
            if (component instanceof JLabel label && text.equals(label.getText())) {
                return true;
            }
            if (component instanceof Container child && containsVisibleLabel(child, text)) {
                return true;
            }
        }
        return false;
    }
}