/*
 * ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2008
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
package org.knime.base.node.viz.property.color;

import org.knime.core.node.GenericNodeFactory;
import org.knime.core.node.GenericNodeView;
import org.knime.core.node.NodeDialogPane;

/**
 * The color manager factory which creates a
 * {@link org.knime.base.node.viz.property.color.ColorManager2NodeDialogPane}.
 * 
 * @see ColorManager2NodeModel
 * @see ColorManager2NodeDialogPane
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
public class ColorManager2NodeFactory extends 
    GenericNodeFactory<ColorManager2NodeModel> {
    
    /**
     * {@inheritDoc}
     */
    @Override
    public ColorManager2NodeModel createNodeModel() {
        return new ColorManager2NodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new ColorManager2NodeDialogPane();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GenericNodeView<ColorManager2NodeModel> createNodeView(
            final int index, final ColorManager2NodeModel nodeModel) {
        throw new IllegalStateException();
    }
}
