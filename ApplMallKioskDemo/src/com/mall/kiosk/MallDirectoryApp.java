package com.mall.kiosk;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MallDirectoryApp {
    private static final Map<String, StoreInfo> STORES = new LinkedHashMap<>();
    private static final AtomicInteger TICKET_COUNTER = new AtomicInteger(100);

    static {
        STORES.put("TechHub Electronics", new StoreInfo("Level 2, North Wing", "Near escalators by the Food Court", "9:00 AM – 9:00 PM"));
        STORES.put("Urban Threads", new StoreInfo("Level 1, Central Atrium", "Across from the main fountain", "10:00 AM – 9:00 PM"));
        STORES.put("GreenLeaf Cafe & Bakery", new StoreInfo("Level 1, East Promenade", "Outdoor patio entrance", "8:00 AM – 8:00 PM"));
        STORES.put("KidsPlay & Books", new StoreInfo("Level 2, South Wing", "Next to Family Restrooms", "10:00 AM – 8:30 PM"));
        STORES.put("Apex Fitness Gear", new StoreInfo("Level 3, West Wing", "Beside the cinema lobby", "10:00 AM – 9:00 PM"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            createAndShowGui();
        });
    }

    private static void createAndShowGui() {
        JFrame frame = new JFrame("Grand Plaza Mall — Kiosk & Guest Services");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(760, 480);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 14));
        tabs.addTab("Store Directory", buildDirectoryPanel());
        tabs.addTab("Mall Services", buildServicesPanel(frame));

        frame.add(tabs);
        frame.setVisible(true);
    }

    private static JPanel buildDirectoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel header = new JLabel("Find a Store");
        header.setFont(new Font("SansSerif", Font.BOLD, 20));
        panel.add(header, BorderLayout.NORTH);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        STORES.keySet().forEach(listModel::addElement);

        JList<String> storeList = new JList<>(listModel);
        storeList.setFont(new Font("SansSerif", Font.PLAIN, 15));
        storeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        storeList.setSelectedIndex(0);

        JTextArea detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        detailsArea.setMargin(new Insets(12, 12, 12, 12));
        detailsArea.setBackground(new Color(248, 250, 252));

        Runnable updateDetails = () -> {
            String selected = storeList.getSelectedValue();
            if (selected != null && STORES.containsKey(selected)) {
                StoreInfo info = STORES.get(selected);
                detailsArea.setText(
                        "Store: " + selected + "\n\n" +
                        "Location: " + info.location + "\n" +
                        "Directions: " + info.directions + "\n" +
                        "Hours Today: " + info.hours
                );
            }
        };

        storeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetails.run();
            }
        });
        updateDetails.run();

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(storeList),
                new JScrollPane(detailsArea)
        );
        splitPane.setDividerLocation(260);
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private static JPanel buildServicesPanel(JFrame parentFrame) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel header = new JLabel("Request Guest Assistance");
        header.setFont(new Font("SansSerif", Font.BOLD, 20));
        panel.add(header, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        String[] services = {
                "Stroller Loan (Central Concierge)",
                "Lost & Found Report",
                "Security Escort to Parking",
                "Gift Wrapping & Parcel Hold"
        };

        for (String serviceName : services) {
            JButton btn = new JButton(serviceName);
            btn.setFont(new Font("SansSerif", Font.PLAIN, 15));
            btn.addActionListener(e -> {
                int ticketNum = TICKET_COUNTER.incrementAndGet();
                String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
                JOptionPane.showMessageDialog(
                        parentFrame,
                        "Service Request Logged!\n\n" +
                        "Ticket #: SVC-" + ticketNum + "\n" +
                        "Service: " + serviceName + "\n" +
                        "Logged at: " + timeStr + "\n\n" +
                        "A staff member has been notified.",
                        "Request Confirmation",
                        JOptionPane.INFORMATION_MESSAGE
                );
            });
            formPanel.add(btn);
        }

        panel.add(formPanel, BorderLayout.CENTER);
        return panel;
    }

    private record StoreInfo(String location, String directions, String hours) {}
}