/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Jun 30, 2020 (benlaney): created
 */
package org.knime.core.node.workflow;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Functional wrapper class for what was a simple String layout representation before version 4.2.0. This class can be
 * referenced throughout the SubNode layout life-cycle. Added functionality include the ability to compare an updated
 * layout with it's originally layout, detection of layouts created and saved with KAP pre-v4.2.0 as well as a default
 * layout seed for creating new SubNode layouts moving forward.
 *
 * @author benlaney
 * @since 4.2
 */
public final class SubnodeContainerLayoutStringProvider {

    /**
     * The default layout string for new components. Added for v4.2.0, but it can be updated as needed for future
     * changes. This is not truly a valid layout within the SubNode layout framework, but it is valid JSON and will be
     * used as the seed to generate a new, full layout (with page content, etc.) by the framework.
     */
    private static final String DEFAULT_LATEST_LAYOUT_STRING = "{\"parentLayoutLegacyMode\":false}";

    private static final String DEFAULT_PRE_V42_LAYOUT_STRING = "";

    private final boolean m_isEmptyLayoutPreV42; // July 1st, 2020; pre-V4.2.0 release

    private String m_currentLayoutString;

    private final String m_loadedLayoutString;

    /**
     * Creates a default layout string provider for newly created components (SubNodeContainers).
     */
    public SubnodeContainerLayoutStringProvider() {
        m_currentLayoutString = DEFAULT_LATEST_LAYOUT_STRING;
        m_loadedLayoutString = DEFAULT_LATEST_LAYOUT_STRING;
        m_isEmptyLayoutPreV42 = false;
    }

    /**
     * Creates a layout string provider for components (SubNodeContainers) loaded from a saved layout string
     * representation. If the component was saved without a layout (which was the default before V4.2.0) it will detect
     * this and ensure default layout creation later in the component life-cycle maintains the expected,
     * backwards-compatible behavior.
     *
     * @param currentLayout the existing layout string as read in from the saved component settings.
     */
    public SubnodeContainerLayoutStringProvider(final String currentLayout) {
        if (StringUtils.isEmpty(currentLayout)) {
            m_currentLayoutString = DEFAULT_PRE_V42_LAYOUT_STRING;
            m_loadedLayoutString = DEFAULT_PRE_V42_LAYOUT_STRING;
            m_isEmptyLayoutPreV42 = true;
        } else {
            m_currentLayoutString = currentLayout;
            m_loadedLayoutString = currentLayout;
            m_isEmptyLayoutPreV42 = false;
        }
    }

    /**
     * @return the current string representation of the layout.
     */
    public String getLayoutString() {
        return m_currentLayoutString;
    }

    /**
     * @param layoutString current layoutString to set.
     */
    public void setLayoutString(final String layoutString) {
        m_currentLayoutString = layoutString;
    }

    /**
     * @return if the layout was originally created from an empty Pre-V4.2.0 SubNode loaded from settings.
     */
    public boolean wasLoadedEmptyPreV42Layout() {
        return m_isEmptyLayoutPreV42;
    }

    /**
     * Checks the layout is one of the default placeholder layouts. If true, the layout should be updated before its
     * functionally accessed. If not, this indicates downstream layout processing has occurred.
     *
     * @return true if the layout is equal to one of the default layouts.
     * @since 4.2
     */
    public boolean isPlaceholderLayout() {
        return m_currentLayoutString.contentEquals(DEFAULT_PRE_V42_LAYOUT_STRING)
            || m_currentLayoutString.contentEquals(DEFAULT_LATEST_LAYOUT_STRING);
    }

    /**
     * Checks the original layout (as loaded from a saved workflow or created for a new component) contains the provided
     * search term string.
     *
     * @param searchTerm the term to search for in the original layout.
     * @return if the original layout contains the provided search term string.
     */
    public boolean checkOriginalContains(final String searchTerm) {
        if (searchTerm == null) {
            return false;
        }
        return m_loadedLayoutString.contains(searchTerm);
    }

    /**
     * Checks if the layout is null or empty.
     *
     * @return if the current layout of this provider is null or empty.
     */
    public boolean isEmptyLayout() {
        return StringUtils.isEmpty(m_currentLayoutString);
    }

    /**
     * Creates a new instance of this class with an identical internal state. Should be used when copying components.
     *
     * @return a new SubnodeContainerLayoutProvider instance.
     */
    public SubnodeContainerLayoutStringProvider copy() {
        SubnodeContainerLayoutStringProvider newLayoutStringProvider =
            new SubnodeContainerLayoutStringProvider(m_loadedLayoutString);
        newLayoutStringProvider.setLayoutString(m_currentLayoutString);
        return newLayoutStringProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        SubnodeContainerLayoutStringProvider other = (SubnodeContainerLayoutStringProvider)obj;
        return StringUtils.equals(m_currentLayoutString, other.m_currentLayoutString);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(m_currentLayoutString)
                .toHashCode();
    }
}
