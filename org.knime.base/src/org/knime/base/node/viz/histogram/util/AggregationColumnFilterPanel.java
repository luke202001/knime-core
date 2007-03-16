/*
 * --------------------------------------------------------------------- *
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2007
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * --------------------------------------------------------------------- *
 */
package org.knime.base.node.viz.histogram.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.node.util.ColumnFilter;
import org.knime.core.node.util.DataColumnSpecListCellRenderer;


/**
 * Panel is used to select the aggregation columns of a histogram node.
 * 
 * 
 * @author Tobias Koetter, University of Konstanz
 */
public class AggregationColumnFilterPanel extends JPanel {

    private static final long serialVersionUID = -4925781595750481201L;

    /** Settings key for the excluded columns. */
    public static final String INCLUDED_COLUMNS = "included_columns";

    /** Settings key for the excluded columns. */
    public static final String EXCLUDED_COLUMNS = "excluded_columns";

    /** Include list. */
    private final JList m_inclList;

    /** Include model. */
    private final DefaultListModel m_inclMdl;

    /** Exclude list. */
    private final JList m_exclList;

    /** Exclude model. */
    private final DefaultListModel m_exclMdl;

    /** Remove button. */
    private final JButton m_remButton;

    /** Add button. */
    private final JButton m_addButton;
    
    /** List of DataCellColumnSpecss to keep initial ordering of DataCells. */
    private final LinkedHashSet<DataColumnSpec> m_order = 
        new LinkedHashSet<DataColumnSpec>();

    /** Border of the include panel, keep it so we can change the title. */
    private final TitledBorder m_includeBorder;

    /** Border of the include panel, keep it so we can change the title. */
    private final TitledBorder m_excludeBorder;

    /**
     * Show only columns of types that are compatible to this filter.
     */
    private final ColumnFilter m_columnFilter;

    private List<ChangeListener>m_listeners;
    
    /**
     * Line border for include columns.
     */
    private static final Border INCLUDE_BORDER = 
        BorderFactory.createLineBorder(new Color(0, 221, 0), 2);
    
    /**
     * Line border for exclude columns.
     */
    private static final Border EXCLUDE_BORDER = 
        BorderFactory.createLineBorder(new Color(240, 0, 0), 2);

    /**
     * Creates a new filter column panel with three component which are the
     * include list, button panel to shift elements between the two lists, and
     * the exclude list.
     * @param label the label of this component
     * @param listDimension the dimension of the list fields
     * @param filter the column filter
     */
    public AggregationColumnFilterPanel(final String label, 
            final Dimension listDimension, final ColumnFilter filter) {
        m_columnFilter = filter;

        // exclude list
        m_exclMdl = new DefaultListModel();
        m_exclList = new JList(m_exclMdl);
        m_exclList.setSelectionMode(
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        m_exclList.setCellRenderer(new DataColumnSpecListCellRenderer());
        final JScrollPane jspExcl = new JScrollPane(m_exclList);
        jspExcl.setMinimumSize(listDimension);
        jspExcl.setMaximumSize(listDimension);

        JPanel excludePanel = new JPanel(new BorderLayout());
        m_excludeBorder = BorderFactory.createTitledBorder(
                EXCLUDE_BORDER, " Exclude ");
        excludePanel.setBorder(m_excludeBorder);
        excludePanel.add(jspExcl, BorderLayout.CENTER);
        
        // button panel
        final JPanel buttonPan = new JPanel();
        buttonPan.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
        buttonPan.setLayout(new BoxLayout(buttonPan, BoxLayout.Y_AXIS));
        buttonPan.add(new JPanel());
        
        m_addButton = new JButton("add >>");
        m_addButton.setMaximumSize(new Dimension(125, 25));
        buttonPan.add(m_addButton);
        m_addButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent ae) {
                onAddIt();
            }
        });
        buttonPan.add(new JPanel());

        buttonPan.add(new JPanel());

        m_remButton = new JButton("<< remove");
        m_remButton.setMaximumSize(new Dimension(125, 25));
        buttonPan.add(m_remButton);
        m_remButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent ae) {
                onRemIt();
            }
        });
        buttonPan.add(new JPanel());

        buttonPan.add(new JPanel());

        // include list
        m_inclMdl = new DefaultListModel();
        m_inclList = new JList(m_inclMdl);
        m_inclList.setSelectionMode(
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        m_inclList.setCellRenderer(new AggregationColumnIconRenderer());
        final JScrollPane jspIncl = new JScrollPane(m_inclList);
        jspIncl.setMinimumSize(listDimension);
        jspIncl.setMaximumSize(listDimension);
        JPanel includePanel = new JPanel(new BorderLayout());
        m_includeBorder = BorderFactory.createTitledBorder(
                INCLUDE_BORDER, " Include ");
        includePanel.setBorder(m_includeBorder);
        includePanel.add(jspIncl, BorderLayout.CENTER);


        JPanel buttonPan2 = new JPanel(new GridLayout());
        Border border = BorderFactory.createTitledBorder(" Select ");
        buttonPan2.setBorder(border);
        buttonPan2.add(buttonPan);

        // adds include, button, exclude component
        JPanel center = new JPanel(new BorderLayout());
        super.setLayout(new BorderLayout());
        center.add(excludePanel, BorderLayout.CENTER);
        center.add(buttonPan2, BorderLayout.EAST);
        super.add(center, BorderLayout.WEST);
        super.add(includePanel, BorderLayout.CENTER);
    } // ColumnFilterPanel()
    
    /**
     * Enables or disables all components on this panel.
     * @see javax.swing.JComponent#setEnabled(boolean)
     */
    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        m_inclList.setEnabled(enabled);
        m_exclList.setEnabled(enabled);
        m_remButton.setEnabled(enabled);
        m_addButton.setEnabled(enabled);
    }
    
    /**
     * Adds a listener which gets informed whenever the column filtering 
     * changes.
     * @param listener the listener
     */
    public void addChangeListener(final ChangeListener listener) {
        if (m_listeners == null) {
            m_listeners = new ArrayList<ChangeListener>();
        }
        m_listeners.add(listener);
    }
    
    /**
     * Removes the given listener from this filter column panel.
     * @param listener the listener.
     */
    public void removeChangeListener(final ChangeListener listener) {
        if (m_listeners != null) {
            m_listeners.remove(listener);
        }
    }
    
    /**
     * Removes all column filter change listener. 
     *
     */
    public void removeAllColumnFilterChangeListener() {
        if (m_listeners != null) {
            m_listeners.clear();
        }
    }
    
    private void fireFilteringChangedEvent() {
        if (m_listeners != null) {
            for (ChangeListener listener : m_listeners) {
                listener.stateChanged((new ChangeEvent(this)));
            }
        }
    }

    /**
     * Called by the 'remove >>' button to exclude the selected elements from
     * the include list.
     */
    protected void onRemIt() {
        // add all selected elements from the include to the exclude list
        Object[] includes = m_inclList.getSelectedValues();
        HashSet<DataColumnSpec> hash = new HashSet<DataColumnSpec>();
        for (Object include : includes) {
            hash.add(((AggregationColumnIcon)include).getColumnSpec());
        }
        for (Enumeration<?> e = m_exclMdl.elements(); e.hasMoreElements();) {
            hash.add((DataColumnSpec)e.nextElement());
        }
        for (int i = 0; i < includes.length; i++) {
            m_inclMdl.removeElement(includes[i]);
        }
        m_exclMdl.removeAllElements();
        for (DataColumnSpec c : m_order) {
            if (hash.contains(c)) {
                m_exclMdl.addElement(c);
            }
        }
        fireFilteringChangedEvent();
    }

    /**
     * Called by the '<< add' button to include the selected elements from the
     * exclude list.
     */
    protected void onAddIt() {
        // add all selected elements from the exclude to the include list
        Object[] excludes = m_exclList.getSelectedValues();
        HashSet<DataColumnSpec> hash = new HashSet<DataColumnSpec>();
        for (Object exlude : excludes) {
            hash.add((DataColumnSpec)exlude);
        }
        for (Enumeration<?> e = m_inclMdl.elements(); e.hasMoreElements();) {
            hash.add(((AggregationColumnIcon)e.nextElement()).getColumnSpec());
        }
        for (int i = 0; i < excludes.length; i++) {
            m_exclMdl.removeElement(excludes[i]);
        }
        m_inclMdl.removeAllElements();
        int aggrColIdx = 0;
        final int noOfAggrCols = hash.size();
        for (DataColumnSpec c : m_order) {
            if (hash.contains(c)) {
                final Color color = generateColor(aggrColIdx++, noOfAggrCols);
                m_inclMdl.addElement(new AggregationColumnIcon(c, color));
            }
        }
        fireFilteringChangedEvent();
    }

    private static Color generateColor(final int idx, final int size) {
        // use Color, half saturated, half bright for base color
        return Color.getColor(null, Color.HSBtoRGB((float)idx / (float)size,
                1.0f, 1.0f));
    }
    
    /**
     * Updates this filter panel by removing all current selections from the
     * include and exclude list. The include list will contain all column names
     * from the spec afterwards.
     * 
     * @param spec the spec to retrieve the column names from
     * @param cells an array of data cells to either include.
     */
    public void update(final DataTableSpec spec,
            final ColorNameColumn... cells) {
        this.update(spec, Arrays.asList(cells));
    }

    /**
     * Updates this filter panel by removing all current selections from the
     * include and exclude list. The include list will contains all column names
     * from the specification afterwards.
     * 
     * @param spec the specification to retrieve the column names from
     * @param incl the list of columns to include
     */
    public void update(final DataTableSpec spec,
            final Collection<ColorNameColumn> incl) {
        assert (spec != null && incl != null);
        m_order.clear();
        m_inclMdl.removeAllElements();
        m_exclMdl.removeAllElements();
        final Set<String> inclNames = new HashSet<String>(incl.size());
        for (ColorNameColumn colorCol : incl) {
            inclNames.add(colorCol.getColumnName());
        }
        final int noOfColumns = spec.getNumColumns();
        int aggrColIdx = 0;
        final int noOfAggrCols = incl.size();
        for (int i = 0; i < noOfColumns; i++) {
            final DataColumnSpec cSpec = spec.getColumnSpec(i);
            if (!m_columnFilter.includeColumn(cSpec)) {
                continue;
            }
            final String c = cSpec.getName();
            m_order.add(cSpec);
            if (inclNames.contains(c)) {
                final Color color = 
                    generateColor(aggrColIdx++, noOfAggrCols);
                m_inclMdl.addElement(
                        new AggregationColumnIcon(cSpec, color));
            } else {
                m_exclMdl.addElement(cSpec);
            }
        }
        repaint();
    }

    /**
     * Returns all columns from the exclude list.
     * 
     * @return a set of all columns from the exclude list
     */
    public Set<String> getExcludedColumnSet() {
        final Set<String> list = new LinkedHashSet<String>();
        for (int i = 0; i < m_exclMdl.getSize(); i++) {
            Object o = m_exclMdl.getElementAt(i);
            String cell = ((DataColumnSpec)o).getName();
            list.add(cell);
        }
        return list;
    }

    /**
     * Returns all columns from the include list.
     * 
     * @return a list of all columns from the include list
     */
    public ColorNameColumn[] getIncludedColorNameColumns() {
        if (m_inclList == null || m_inclMdl.getSize() < 1) {
            return null;
        }
        final int noOfElements = m_inclMdl.getSize();
        final ColorNameColumn[] list = new ColorNameColumn[noOfElements];
        for (int i = 0; i < noOfElements; i++) {
            AggregationColumnIcon o = 
                (AggregationColumnIcon) m_inclMdl.getElementAt(i);
            list[i] = new ColorNameColumn(o.getColor(), 
                    o.getColumnSpec().getName());
        }
        return list;
    }

    /**
     * Returns the data type for the given cell retrieving it from the initial
     * {@link DataTableSpec}. If this name could not found, return
     * <code>null</code>.
     * 
     * @param name the column name to get the data type for
     * @return the data type or <code>null</code>
     */
    public DataType getType(final String name) {
        for (DataColumnSpec spec : m_order) {
            if (spec.getName().equals(name)) {
                return spec.getType();
            }
        }
        return null;
    }

    /**
     * Sets the title of the include panel.
     * 
     * @param title the new title
     */
    public final void setIncludeTitle(final String title) {
        m_includeBorder.setTitle(title);
    }

    /**
     * Sets the title of the exclude panel.
     * 
     * @param title the new title
     */
    public final void setExcludeTitle(final String title) {
        m_excludeBorder.setTitle(title);
    }

    /**
     * Setter for the original "remove" button.
     * 
     * @param text the new button title
     */
    public void setRemoveButtonText(final String text) {
        m_remButton.setText(text);
    }

    /**
     * Setter for the original "Add" button.
     * 
     * @param text the new button title
     */
    public void setAddButtonText(final String text) {
        m_addButton.setText(text);
    }
}
