/*
 *  Copyright (C) 2010-2014 JPEXS
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpexs.decompiler.flash.gui.proxy;

import com.jpexs.decompiler.flash.RetryTask;
import com.jpexs.decompiler.flash.RunnableIOEx;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.flash.gui.AppFrame;
import com.jpexs.decompiler.flash.gui.AppStrings;
import com.jpexs.decompiler.flash.gui.GuiAbortRetryIgnoreHandler;
import com.jpexs.decompiler.flash.gui.Main;
import com.jpexs.decompiler.flash.gui.MainFrame;
import com.jpexs.decompiler.flash.gui.View;
import com.jpexs.decompiler.flash.helpers.SWFDecompilerPlugin;
import com.jpexs.helpers.Helper;
import com.jpexs.proxy.CatchedListener;
import com.jpexs.proxy.ReplacedListener;
import com.jpexs.proxy.Replacement;
import com.jpexs.proxy.Server;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;

/**
 * Frame with Proxy
 *
 * @author JPEXS
 */
public class ProxyFrame extends AppFrame implements ActionListener, CatchedListener, MouseListener, ReplacedListener {

    static final String ACTION_SWITCH_STATE = "SWITCHSTATE";
    static final String ACTION_OPEN = "OPEN";
    static final String ACTION_CLEAR = "CLEAR";
    static final String ACTION_RENAME = "RENAME";
    static final String ACTION_REMOVE = "REMOVE";
    static final String ACTION_COPYURL = "COPYURL";
    static final String ACTION_SAVEAS = "SAVEAS";
    static final String ACTION_REPLACE = "REPLACE";

    private JTable replacementsTable;
    private JButton switchButton = new JButton(translate("proxy.start"));
    private boolean started = false;
    private JTextField portField = new JTextField("55555");
    private JCheckBox sniffSWFCheckBox = new JCheckBox("SWF", false);
    private JCheckBox sniffOSCheckBox = new JCheckBox("OctetStream", false);
    private JCheckBox sniffJSCheckBox = new JCheckBox("JS", false);
    private JCheckBox sniffXMLCheckBox = new JCheckBox("XML", false);

    /**
     * Is server running
     *
     * @return True when running
     */
    public boolean isRunning() {
        return started;
    }

    /**
     * Sets port for the proxy
     *
     * @param port Port number
     */
    public void setPort(int port) {
        portField.setText(Integer.toString(port));
    }

    private static class SizeItem implements Comparable<SizeItem> {

        String file;

        public SizeItem(String file) {
            this.file = file;
        }

        @Override
        public String toString() {
            return Helper.byteCountStr(new File(file).length(), false);
        }

        @Override
        public int compareTo(SizeItem o) {
            return (int) (new File(file).length() - new File(o.file).length());
        }

    }

    DefaultTableModel tableModel;
    private SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");

    List<Replacement> reps;

    /**
     * Constructor
     *
     * @param mainFrame
     */
    public ProxyFrame(final MainFrame mainFrame) {

        final String[] columnNames = new String[]{
            translate("column.accessed"),
            translate("column.size"),
            translate("column.url")};

        reps = Configuration.getReplacements();

        Object data[][] = new Object[reps.size()][3];

        for (int i = 0; i < reps.size(); i++) {
            Replacement r = reps.get(i);
            data[i][0] = r.lastAccess == null ? "" : format.format(r.lastAccess.getTime());
            data[i][1] = new SizeItem(r.targetFile);
            data[i][2] = r.urlPattern;
        }

        tableModel = new DefaultTableModel(data, columnNames) {

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                Class classes[] = new Class[]{String.class, SizeItem.class, String.class};
                return classes[columnIndex];
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

        };
        replacementsTable = new JTable(tableModel);

        DefaultTableCellRenderer tcr = new DefaultTableCellRenderer();
        tcr.setHorizontalAlignment(SwingConstants.RIGHT);

        replacementsTable.setDefaultRenderer(String.class, new DefaultTableCellRenderer());
        replacementsTable.setDefaultRenderer(SizeItem.class, tcr);

        replacementsTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        replacementsTable.setRowSelectionAllowed(true);

        DefaultTableColumnModel colModel = (DefaultTableColumnModel) replacementsTable.getColumnModel();
        colModel.getColumn(0).setMaxWidth(100);

        colModel.getColumn(1).setMaxWidth(200);

        replacementsTable.setAutoCreateRowSorter(true);

        replacementsTable.setAutoCreateRowSorter(false);

        replacementsTable.addMouseListener(this);
        replacementsTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        switchButton.addActionListener(this);
        switchButton.setActionCommand(ACTION_SWITCH_STATE);
        Container cnt = getContentPane();
        cnt.setLayout(new BorderLayout());
        cnt.add(new JScrollPane(replacementsTable), BorderLayout.CENTER);

        portField.setPreferredSize(new Dimension(80, portField.getPreferredSize().height));
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new FlowLayout());
        buttonsPanel.add(new JLabel(translate("port")));
        buttonsPanel.add(portField);
        buttonsPanel.add(switchButton);
        cnt.add(buttonsPanel, BorderLayout.NORTH);

        JPanel buttonsPanel23 = new JPanel();
        buttonsPanel23.setLayout(new BoxLayout(buttonsPanel23, BoxLayout.Y_AXIS));

        JPanel buttonsPanel21 = new JPanel(new FlowLayout());
        JButton openButton = new JButton(translate("open"));
        openButton.setActionCommand(ACTION_OPEN);
        openButton.addActionListener(this);
        buttonsPanel21.add(openButton);
        JButton clearButton = new JButton(translate("clear"));
        clearButton.setActionCommand(ACTION_CLEAR);
        clearButton.addActionListener(this);
        buttonsPanel21.add(clearButton);
        JButton renameButton = new JButton(translate("rename"));
        renameButton.setActionCommand(ACTION_RENAME);
        renameButton.addActionListener(this);
        buttonsPanel21.add(renameButton);
        JButton removeButton = new JButton(translate("remove"));
        removeButton.setActionCommand(ACTION_REMOVE);
        removeButton.addActionListener(this);
        buttonsPanel21.add(removeButton);

        //JPanel buttonsPanel22 = new JPanel(new FlowLayout());
        JButton copyUrlButton = new JButton(translate("copy.url"));
        copyUrlButton.setActionCommand(ACTION_COPYURL);
        copyUrlButton.addActionListener(this);
        buttonsPanel21.add(copyUrlButton);

        JButton saveAsButton = new JButton(translate("save.as"));
        saveAsButton.setActionCommand(ACTION_SAVEAS);
        saveAsButton.addActionListener(this);
        buttonsPanel21.add(saveAsButton);

        JButton replaceButton = new JButton(translate("replace"));
        replaceButton.setActionCommand(ACTION_REPLACE);
        replaceButton.addActionListener(this);
        buttonsPanel21.add(replaceButton);

        JPanel buttonsPanel3 = new JPanel();
        buttonsPanel3.setLayout(new FlowLayout());
        buttonsPanel3.add(new JLabel(translate("sniff")));
        buttonsPanel3.add(sniffSWFCheckBox);
        buttonsPanel3.add(sniffOSCheckBox);
        //buttonsPanel3.add(sniffJSCheckBox);
        //buttonsPanel3.add(sniffXMLCheckBox);

        buttonsPanel23.add(buttonsPanel21);
        //buttonsPanel23.add(buttonsPanel22);
        buttonsPanel23.add(buttonsPanel3);

        cnt.add(buttonsPanel23, BorderLayout.SOUTH);
        setSize(800, 500);
        View.centerScreen(this);
        View.setWindowIcon(this);
        setTitle(translate("dialog.title"));
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
                Main.removeTrayIcon();
                if (mainFrame != null) {
                    if (mainFrame.isVisible()) {
                        return;
                    }
                }
                Main.showModeFrame();
            }

            /**
             * Invoked when a window is iconified.
             */
            @Override
            public void windowIconified(WindowEvent e) {
                setVisible(false);
            }
        });
        List<Image> images = new ArrayList<>();
        images.add(View.loadImage("proxy16"));
        images.add(View.loadImage("proxy32"));
        setIconImages(images);
    }

    private void open() {
        if (replacementsTable.getSelectedRow() > -1) {
            Replacement r = reps.get(replacementsTable.getRowSorter().convertRowIndexToModel(replacementsTable.getSelectedRow()));
            Main.openFile(r.targetFile, r.urlPattern);
        }
    }

    private String selectExportDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(Configuration.lastExportDir.get()));
        chooser.setDialogTitle(translate("export.select.directory"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            final String selFile = Helper.fixDialogFile(chooser.getSelectedFile()).getAbsolutePath();
            Configuration.lastExportDir.set(Helper.fixDialogFile(chooser.getSelectedFile()).getAbsolutePath());
            return selFile;
        }
        return null;
    }

    /**
     * Method handling actions from buttons
     *
     * @param e event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        int sel[] = replacementsTable.getSelectedRows();
        for (int i = 0; i < sel.length; i++) {
            sel[i] = replacementsTable.getRowSorter().convertRowIndexToModel(sel[i]);
        }
        switch (e.getActionCommand()) {
            case ACTION_OPEN:
                open();
                break;
            case ACTION_SAVEAS:
                if (sel.length == 1) {
                    Replacement r = reps.get(sel[0]);
                    JFileChooser fc = new JFileChooser();
                    fc.setCurrentDirectory(new File(Configuration.lastSaveDir.get()));
                    String n = r.urlPattern;
                    if (n.contains("?")) {
                        n = n.substring(0, n.indexOf('?'));
                    }
                    if (n.contains("/")) {
                        n = n.substring(n.lastIndexOf('/'));
                    }
                    n = Helper.makeFileName(n);
                    fc.setSelectedFile(new File(Configuration.lastSaveDir.get(), n));
                    String ext = ".swf";
                    final String extension = ext;
                    FileFilter swfFilter = new FileFilter() {
                        @Override
                        public boolean accept(File f) {
                            return (f.getName().toLowerCase().endsWith(extension)) || (f.isDirectory());
                        }

                        @Override
                        public String getDescription() {
                            return AppStrings.translate("filter" + extension);
                        }
                    };
                    fc.setFileFilter(swfFilter);
                    fc.setAcceptAllFileFilterUsed(true);
                    JFrame f = new JFrame();
                    View.setWindowIcon(f);
                    if (fc.showSaveDialog(f) == JFileChooser.APPROVE_OPTION) {
                        File file = Helper.fixDialogFile(fc.getSelectedFile());
                        try {
                            Files.copy(new File(r.targetFile).toPath(), file.toPath(), REPLACE_EXISTING);
                        } catch (IOException ex) {
                            View.showMessageDialog(this, translate("error.save.as") + "\r\n" + ex.getLocalizedMessage(), AppStrings.translate("error"), JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } else {
                    GuiAbortRetryIgnoreHandler handler = new GuiAbortRetryIgnoreHandler();
                    File exportDir = new File(selectExportDir());
                    for (int s : sel) {
                        final Replacement r = reps.get(s);
                        String n = r.urlPattern;
                        if (n.contains("?")) {
                            n = n.substring(0, n.indexOf('?'));
                        }
                        if (n.contains("/")) {
                            n = n.substring(n.lastIndexOf('/'));
                        }
                        n = Helper.makeFileName(n);
                        int c = 2;
                        String n2 = n;
                        while (new File(exportDir, n2).exists()) {
                            if (n.contains(".")) {
                                n2 = n.substring(0, n.lastIndexOf('.')) + c + n.substring(n.lastIndexOf('.'));
                                c++;
                            } else {
                                n2 = n + c + ".swf";
                                c++;
                            }
                        }

                        final File outfile = new File(exportDir, n2);
                        try {
                            new RetryTask(new RunnableIOEx() {
                                @Override
                                public void run() throws IOException {
                                    Files.copy(new File(r.targetFile).toPath(), outfile.toPath(), REPLACE_EXISTING);
                                }
                            }, handler).run();
                        } catch (IOException ex) {
                            break;
                        }
                    }
                }
                break;
            case ACTION_REPLACE:
                if (sel.length > 0) {
                    Replacement r = reps.get(sel[0]);
                    JFileChooser fc = new JFileChooser();
                    fc.setCurrentDirectory(new File(Configuration.lastOpenDir.get()));
                    String n = r.urlPattern;
                    if (n.contains("?")) {
                        n = n.substring(0, n.indexOf('?'));
                    }
                    if (n.contains("/")) {
                        n = n.substring(n.lastIndexOf('/'));
                    }
                    String ext = ".swf";
                    final String extension = ext;
                    FileFilter swfFilter = new FileFilter() {
                        @Override
                        public boolean accept(File f) {
                            return (f.getName().toLowerCase().endsWith(extension)) || (f.isDirectory());
                        }

                        @Override
                        public String getDescription() {
                            return AppStrings.translate("filter" + extension);
                        }
                    };
                    fc.setFileFilter(swfFilter);
                    fc.setAcceptAllFileFilterUsed(true);
                    JFrame f = new JFrame();
                    View.setWindowIcon(f);
                    if (fc.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                        File file = Helper.fixDialogFile(fc.getSelectedFile());
                        try {
                            Files.copy(file.toPath(), new File(r.targetFile).toPath(), REPLACE_EXISTING);
                            tableModel.fireTableCellUpdated(sel[0], 1/*size*/);
                        } catch (IOException ex) {
                            View.showMessageDialog(f, translate("error.replace") + "\r\n" + ex.getLocalizedMessage(), AppStrings.translate("error"), JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
                break;
            case ACTION_COPYURL:
                String copyText = "";
                for (int sc : sel) {
                    Replacement r = reps.get(sc);
                    if (!copyText.isEmpty()) {
                        copyText += System.lineSeparator();
                    }
                    copyText += r.urlPattern;
                }

                if (!copyText.isEmpty()) {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    StringSelection stringSelection = new StringSelection(copyText);
                    clipboard.setContents(stringSelection, null);
                }
                break;
            case ACTION_RENAME:
                if (sel.length > 0) {
                    Replacement r = reps.get(sel[0]);
                    String s = View.showInputDialog("URL", r.urlPattern);
                    if (s != null) {
                        r.urlPattern = s;
                        tableModel.setValueAt(s, sel[0], 2/*url*/);
                    }
                }
                break;
            case ACTION_CLEAR:
                for (Replacement r : reps) {
                    File f;
                    try {
                        f = (new File(Main.tempFile(r.targetFile)));
                        if (f.exists()) {
                            f.delete();
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(ProxyFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                tableModel = new DefaultTableModel(0, 3);
                replacementsTable.setModel(tableModel);
                reps.clear();
                break;
            case ACTION_REMOVE:

                Arrays.sort(sel);
                for (int i = sel.length - 1; i >= 0; i--) {
                    tableModel.removeRow(sel[i]);
                    Replacement r = reps.remove(sel[i]);
                    File f = (new File(r.targetFile));
                    if (f.exists()) {
                        f.delete();
                    }
                }
                break;
            case ACTION_SWITCH_STATE:
                Main.switchProxy();
                break;
        }
    }

    /**
     * Switch proxy state
     */
    public void switchState() {
        started = !started;
        if (started) {
            int port = 0;
            try {
                port = Integer.parseInt(portField.getText());
            } catch (NumberFormatException nfe) {
            }
            if ((port <= 0) || (port > 65535)) {
                View.showMessageDialog(this, translate("error.port"), translate("error"), JOptionPane.ERROR_MESSAGE);
                started = false;
                return;
            }
            List<String> catchedContentTypes = new ArrayList<>();
            catchedContentTypes.add("application/x-shockwave-flash");
            catchedContentTypes.add("application/x-javascript");
            catchedContentTypes.add("application/javascript");
            catchedContentTypes.add("text/javascript");
            catchedContentTypes.add("application/json");
            catchedContentTypes.add("text/xml");
            catchedContentTypes.add("application/xml");
            catchedContentTypes.add("application/octet-stream");
            if(!Server.startServer(port, Configuration.getReplacements(), catchedContentTypes, this, this)){
                JOptionPane.showMessageDialog(this, translate("error.start.server").replace("%port%", ""+port),AppStrings.translate("error"),JOptionPane.ERROR_MESSAGE);
                started = false;
                return;
            }
            switchButton.setText(translate("proxy.stop"));
            portField.setEditable(false);
        } else {
            Server.stopServer();
            switchButton.setText(translate("proxy.start"));
            portField.setEditable(true);
        }
    }

    /**
     * Mouse clicked event
     *
     * @param e event
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getSource() == replacementsTable) {
            if (e.getClickCount() == 2) {
                open();
            }
        }
    }

    /**
     * Mouse pressed event
     *
     * @param e event
     */
    @Override
    public void mousePressed(MouseEvent e) {
    }

    /**
     * Mouse released event
     *
     * @param e event
     */
    @Override
    public void mouseReleased(MouseEvent e) {
    }

    /**
     * Mouse entered event
     *
     * @param e event
     */
    @Override
    public void mouseEntered(MouseEvent e) {
    }

    /**
     * Mouse exited event
     *
     * @param e event
     */
    @Override
    public void mouseExited(MouseEvent e) {
    }

    /**
     * Method called when specified contentType is received
     *
     * @param contentType Content type
     * @param url URL of the method
     * @param data Data stream
     * @return replacement data
     */
    @Override
    public byte[] catched(String contentType, String url, InputStream data) {
        boolean swfOnly = false;
        if (contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(';'));
        }
        if ((!sniffSWFCheckBox.isSelected()) && (contentType.equals("application/x-shockwave-flash"))) {
            return null;
        }
        if ((!sniffJSCheckBox.isSelected()) && (contentType.equals("application/javascript") || contentType.equals("application/x-javascript") || contentType.equals("text/javascript") || contentType.equals("application/json"))) {
            return null;
        }
        if ((!sniffXMLCheckBox.isSelected()) && (contentType.equals("application/xml") || contentType.equals("text/xml"))) {
            return null;
        }
        if ((!sniffOSCheckBox.isSelected()) && (contentType.equals("application/octet-stream"))) {
            return null;
        }

        byte[] result = null;

        boolean cont = false;
        for (Replacement r : reps) {
            if (r.matches(url)) {
                cont = true;
                break;
            }
        }
        if (!cont) {
            try {
                byte[] hdr = new byte[3];
                data.read(hdr);
                String shdr = new String(hdr);
                if (swfOnly && ((!shdr.equals("FWS")) && (!shdr.equals("CWS")) && (!shdr.equals("ZWS")))) {
                    return null; //NOT SWF
                }

                String tempFilePath = Main.tempFile(url);
                data.reset();
                byte[] dataArray = Helper.readStream(data);
                try (FileOutputStream fos = new FileOutputStream(new File(tempFilePath))) {
                    fos.write(dataArray);
                }

                result = SWFDecompilerPlugin.fireProxyFileCatched(dataArray);

                Replacement r = new Replacement(url, tempFilePath);
                r.lastAccess = Calendar.getInstance();
                reps.add(r);
                tableModel.addRow(new Object[]{
                    r.lastAccess == null ? "" : format.format(r.lastAccess.getTime()),
                    new SizeItem(r.targetFile),
                    r.urlPattern
                });
            } catch (IOException e) {
            }
        }

        return result;
    }

    /**
     * Shows or hides this {@code Window} depending on the value of parameter
     * {@code b}.
     *
     * @param b if {@code true}, makes the {@code Window} visible, otherwise
     * hides the {@code Window}. If the {@code Window} and/or its owner are not
     * yet displayable, both are made displayable. The {@code Window} will be
     * validated prior to being made visible. If the {@code Window} is already
     * visible, this will bring the {@code Window} to the front.<p>
     * If {@code false}, hides this {@code Window}, its subcomponents, and all
     * of its owned children. The {@code Window} and its subcomponents can be
     * made visible again with a call to {@code #setVisible(true)}.
     * @see java.awt.Component#isDisplayable
     * @see java.awt.Component#setVisible
     * @see java.awt.Window#toFront
     * @see java.awt.Window#dispose
     */
    @Override
    public void setVisible(boolean b) {
        if (b == true) {
            Main.addTrayIcon();
        }
        super.setVisible(b);
    }

    @Override
    public void replaced(Replacement replacement, String url, String contentType) {
        int index = reps.indexOf(replacement);
        tableModel.setValueAt(replacement.lastAccess == null ? "" : format.format(replacement.lastAccess.getTime()), index, 0);
        tableModel.setValueAt(new SizeItem(replacement.targetFile), index, 1);
        tableModel.setValueAt(replacement.urlPattern, index, 2);
    }
}
