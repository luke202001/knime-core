/*
 * ----------------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2009
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
 * ----------------------------------------------------------------------------
 */
package org.knime.core.node.port.database;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.RowIterator;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.DataContainer;
import org.knime.core.data.date.DateAndTimeCell;
import org.knime.core.data.date.DateAndTimeValue;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.FileUtil;

/**
 * Creates a connection to read from database.
 *
 * @author Thomas Gabriel, University of Konstanz
 */
public final class DatabaseReaderConnection {

    private static final NodeLogger LOGGER =
            NodeLogger.getLogger(DatabaseReaderConnection.class);

    private DataTableSpec m_spec;

    private DatabaseQueryConnectionSettings m_conn;

    private Statement m_stmt;

    /**
     * Creates a empty handle for a new connection.
     * @param conn a database connection object
     */
    public DatabaseReaderConnection(
            final DatabaseQueryConnectionSettings conn) {
        setDBQueryConnection(conn);
    }

    /**
     * Sets anew connection object.
     * @param conn the connection
     */
    public void setDBQueryConnection(
            final DatabaseQueryConnectionSettings conn) {
        m_conn = conn;
        m_spec = null;
        m_stmt = null;
    }

    /**
     * @return connection settings object
     */
    public DatabaseQueryConnectionSettings getQueryConnection() {
        return m_conn;
    }

    /**
     * Returns a data table spec that reflects the meta data form the database
     * result set.
     * @return data table spec
     * @throws SQLException if the connection to the database could not be
     *         established
     */
    public DataTableSpec getDataTableSpec() throws SQLException {
        if (m_spec == null || m_stmt == null) {
            try {
                final int hashAlias = System.identityHashCode(this);
                String pQuery = "SELECT * FROM (" + m_conn.getQuery()
                    + ") table_" + hashAlias + " WHERE 1 = 0";
                ResultSet result = null;
                final Connection conn = m_conn.createConnection();
                try {
                    // try to see if prepared statements are supported
                    m_stmt = conn.prepareStatement(pQuery);
                    if (DatabaseConnectionSettings.FETCH_SIZE != null) {
                        m_stmt.setFetchSize(
                                DatabaseConnectionSettings.FETCH_SIZE);
                    }
                    ((PreparedStatement) m_stmt).execute();
                    m_spec = createTableSpec(
                            ((PreparedStatement) m_stmt).getMetaData());
                } catch (Exception e) {
                    LOGGER.warn("PreparedStatment not support by database: "
                            + e.getMessage(), e);
                    // otherwise use standard statement
                    m_stmt = conn.createStatement();
                    if (DatabaseConnectionSettings.FETCH_SIZE != null) {
                        m_stmt.setFetchSize(
                                DatabaseConnectionSettings.FETCH_SIZE);
                    }
                    result = m_stmt.executeQuery(pQuery);
                    m_spec = createTableSpec(result.getMetaData());
                } finally {
                    if (result != null) {
                        result.close();
                    }
                    // ensure we have a non-prepared statement to access data
                    if (m_stmt != null && m_stmt instanceof PreparedStatement) {
                        m_stmt = conn.createStatement();
                        if (DatabaseConnectionSettings.FETCH_SIZE != null) {
                            m_stmt.setFetchSize(
                                    DatabaseConnectionSettings.FETCH_SIZE);
                        }
                    }
                }
            } catch (SQLException sql) {
                if (m_stmt != null) {
                    try {
                        m_stmt.close();
                    } catch (SQLException e) {
                        LOGGER.debug(e);
                    }
                    m_stmt = null;
                }
                throw sql;
            } catch (Throwable t) {
                throw new SQLException(t);
            }
        }
        return m_spec;
    }

    /**
     * Read data from database.
     * @param exec used for progress info
     * @return buffered data table read from database
     * @throws CanceledExecutionException if canceled in between
     * @throws SQLException if the connection could not be opened
     */
    public BufferedDataTable createTable(final ExecutionContext exec)
            throws CanceledExecutionException, SQLException {
        try {
            final DataTableSpec spec = getDataTableSpec();
            final ResultSet result = m_stmt.executeQuery(m_conn.getQuery());
            return exec.createBufferedDataTable(new DataTable() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public DataTableSpec getDataTableSpec() {
                    return spec;
                }
                /**
                 * {@inheritDoc}
                 */
                @Override
                public RowIterator iterator() {
                    return new DBRowIterator(result);
                }

            }, exec);
        } finally {
            if (m_stmt != null) {
                m_stmt.close();
                m_stmt = null;
            }
        }
    }

    /**
     * @param cachedNoRows number of rows cached for data preview
     * @return buffered data table read from database
     * @throws SQLException if the connection could not be opened
     */
    DataTable createTable(final int cachedNoRows) throws SQLException {
        try {
            final DataTableSpec spec = getDataTableSpec();
            final String query;
            if (cachedNoRows < 0) {
                query = m_conn.getQuery();
            } else {
                final int hashAlias = System.identityHashCode(this);
                query = "SELECT * FROM (" + m_conn.getQuery()
                    + ") table_" + hashAlias;
                m_stmt.setMaxRows(cachedNoRows);
            }
            m_stmt.execute(query);
            final ResultSet result = m_stmt.getResultSet();
            DBRowIterator it = new DBRowIterator(result);
            DataContainer buf = new DataContainer(spec);
            while (it.hasNext()) {
                buf.addRowToTable(it.next());
            }
            buf.close();
            return buf.getTable();
        } finally {
            if (m_stmt != null) {
                m_stmt.close();
                m_stmt = null;
            }
        }
    }

    private DataTableSpec createTableSpec(final ResultSetMetaData meta)
            throws SQLException {
        int cols = meta.getColumnCount();
        if (cols == 0) {
            return new DataTableSpec("database");
        }
        DataTableSpec spec = null;
        for (int i = 0; i < cols; i++) {
            int dbIdx = i + 1;
            String name = meta.getColumnName(dbIdx);
            int type = meta.getColumnType(dbIdx);
            DataType newType;
            switch (type) {
                // all types that can be interpreted as integer
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.INTEGER:
                case Types.BIT:
                case Types.BOOLEAN:
                    newType = IntCell.TYPE;
                    break;
                // all types that can be interpreted as double
                case Types.FLOAT:
                case Types.DOUBLE:
                case Types.NUMERIC:
                case Types.DECIMAL:
                case Types.REAL:
                case Types.BIGINT:
                    newType = DoubleCell.TYPE;
                    break;
                case Types.TIME:
                case Types.DATE:
                case Types.TIMESTAMP:
                    newType = DateAndTimeCell.TYPE;
                    break;
                default:
                    newType = StringCell.TYPE;
            }
            if (spec == null) {
                spec = new DataTableSpec("database",
                        new DataColumnSpecCreator(
                        name, newType).createSpec());
            } else {
                name = DataTableSpec.getUniqueColumnName(spec, name);
                spec = new DataTableSpec("database", spec,
                       new DataTableSpec(new DataColumnSpecCreator(
                               name, newType).createSpec()));
            }
        }
        return spec;
    }

    /**
     * RowIterator via a database ResultSet.
     */
    private class DBRowIterator extends RowIterator {

        private final ResultSet m_result;

        private boolean m_hasExceptionReported = false;

        private int m_rowCounter = 0;

        /** FIXME: Some database (such as sqlite do NOT support) methods like
         * #getAsciiStream nor #getBinaryStream and will fail with an
         * SQLException. To prevent this exception for each ResultSet's value,
         * this flag for each column indicated that this exception has been
         * thrown and we directly can access the value via #getString.
         */
        private final boolean[] m_streamException;

        /**
         * Creates new iterator.
         * @param result result set to iterate
         */
        DBRowIterator(final ResultSet result) {
            m_result = result;
            m_streamException = new boolean[m_spec.getNumColumns()];
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            boolean ret = false;
            try {
                ret = m_result.next();
            } catch (SQLException sql) {
                ret = false;
            }
            if (!ret) {
                try {
                    m_result.close();
                } catch (SQLException e) {
                    LOGGER.error("SQL Exception while closing result set: ", e);
                }
            }
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DataRow next() {
            DataCell[] cells = new DataCell[m_spec.getNumColumns()];
            for (int i = 0; i < cells.length; i++) {
                DataType type = m_spec.getColumnSpec(i).getType();
                int dbType = Types.NULL;
                try {
                    dbType = m_result.getMetaData().getColumnType(i + 1);
                    if (type.isCompatible(IntValue.class)) {
                        int integer;
                        switch (dbType) {
                            // all types that can be interpreted as integer
                            case Types.TINYINT:
                                integer = m_result.getByte(i + 1);
                                break;
                            case Types.SMALLINT:
                                integer = m_result.getShort(i + 1);
                                break;
                            case Types.INTEGER:
                                integer = m_result.getInt(i + 1);
                                break;
                            case Types.BIT:
                            case Types.BOOLEAN:
                                integer = m_result.getBoolean(i + 1) ? 1 : 0;
                                break;
                                default: integer = m_result.getInt(i + 1);
                        }
                        if (wasNull()) {
                            cells[i] = DataType.getMissingCell();
                        } else {
                            cells[i] = new IntCell(integer);
                        }
                    } else if (type.isCompatible(DoubleValue.class)) {
                        double dbl;
                        switch (dbType) {
                            // all types that can be interpreted as double
                            case Types.REAL:
                                dbl = m_result.getFloat(i + 1);
                                break;
                            case Types.FLOAT:
                            case Types.DOUBLE:
                                dbl = m_result.getDouble(i + 1);
                                break;
                            case Types.DECIMAL:
                            case Types.NUMERIC:
                                dbl =
                                    m_result.getBigDecimal(i + 1).doubleValue();
                                break;
                            case Types.BIGINT:
                                dbl = m_result.getLong(i + 1);
                                break;
                                default: dbl = m_result.getDouble(i + 1);
                        }
                        if (wasNull()) {
                            cells[i] = DataType.getMissingCell();
                        } else {
                            cells[i] = new DoubleCell(dbl);
                        }
                    } else if (type.isCompatible(DateAndTimeValue.class)) {
                        DateAndTimeCell date;
                        switch (dbType) {
                            case Types.DATE: date = readDate(i); break;
                            case Types.TIME: date = readTime(i); break;
                            case Types.TIMESTAMP:
                                date = readTimestamp(i); break;
                                default: date = null;
                        }
                        if (date == null) {
                            cells[i] = DataType.getMissingCell();
                        } else {
                            cells[i] = date;
                        }
                    } else {
                        String s;
                        switch (dbType) {
                            case Types.CLOB: s = readClob(i); break;
                            case Types.BLOB: s = readBlob(i); break;
                            case Types.ARRAY: s = readArray(i); break;
                            case Types.CHAR:
                            case Types.VARCHAR:
                                s = readString(i); break;
                            case Types.LONGVARCHAR:
                                s = readAsciiStream(i); break;
                            case Types.BINARY:
                            case Types.VARBINARY:
                                s = readBytes(i); break;
                            case Types.LONGVARBINARY:
                                s = readBinaryStream(i); break;
                            case Types.REF: s = readRef(i); break;
                            case Types.NCHAR:
                            case Types.NVARCHAR:
                            case Types.LONGNVARCHAR: s = readNString(i); break;
                            case Types.NCLOB: s = readNClob(i); break;
                            case Types.DATALINK: s = readURL(i); break;
                            case Types.STRUCT:
                            case Types.JAVA_OBJECT:
                                s = readObject(i); break;
                                default: s = readObject(i); break;

                        }
                        if (s == null) {
                            cells[i] = DataType.getMissingCell();
                        } else {
                            cells[i] = new StringCell(s);
                        }
                    }
                } catch (SQLException sqle) {
                    handlerException(
                            "SQL Exception reading Object of type \""
                            + dbType + "\": ", sqle);
                } catch (IOException ioe) {
                    handlerException(
                            "I/O Exception reading Object of type \""
                            + dbType + "\": ", ioe);
                }
            }
            int rowId = m_rowCounter;
            try {
                rowId = m_result.getRow();
            } catch (SQLException sqle) {
                 handlerException(
                         "SQL Exception while retrieving row id: ", sqle);
            }
            m_rowCounter++;
            return new DefaultRow(RowKey.createRowKey(rowId), cells);
        }

        private String readClob(final int i)
                throws IOException, SQLException {
            Clob clob = m_result.getClob(i + 1);
            if (wasNull() || clob == null) {
                return null;
            } else {
                Reader reader = clob.getCharacterStream();
                StringWriter writer = new StringWriter();
                FileUtil.copy(reader, writer);
                reader.close();
                writer.close();
                return writer.toString();
            }
        }

        private String readNClob(final int i)
                throws IOException, SQLException {
            NClob nclob = m_result.getNClob(i + 1);
            if (wasNull() || nclob == null) {
                return null;
            } else {
                Reader reader = nclob.getCharacterStream();
                StringWriter writer = new StringWriter();
                FileUtil.copy(reader, writer);
                reader.close();
                writer.close();
                return writer.toString();
            }
        }

        private String readBlob(final int i)
                throws IOException, SQLException {
           Blob blob = m_result.getBlob(i + 1);
           if (wasNull() || blob == null) {
               return null;
           } else {
               InputStreamReader reader =
                   // TODO: using default encoding
                   new InputStreamReader(blob.getBinaryStream());
               StringWriter writer = new StringWriter();
               FileUtil.copy(reader, writer);
               reader.close();
               writer.close();
               return writer.toString();
           }
        }

        private String readAsciiStream(final int i)
                throws IOException, SQLException {
            if (m_streamException[i]) {
                return readString(i);
            }
            try {
                InputStream is = m_result.getAsciiStream(i + 1);
                if (wasNull() || is == null) {
                    return null;
                } else {
                    InputStreamReader reader =
                    // TODO: using default encoding
                            new InputStreamReader(is);
                    StringWriter writer = new StringWriter();
                    FileUtil.copy(reader, writer);
                    reader.close();
                    writer.close();
                    return writer.toString();
                }
            } catch (SQLException sql) {
                m_streamException[i] = true;
                handlerException("Can't read from ASCII stream, "
                        + "trying to read string... ", sql);
                return readString(i);
            }
        }

        private String readString(final int i) throws SQLException {
            String s = m_result.getString(i + 1);
            if (wasNull() || s == null) {
                return null;
            } else {
                return s;
            }
        }

        private String readBytes(final int i) throws SQLException {
            byte[] bytes = m_result.getBytes(i + 1);
            if (wasNull() || bytes == null) {
                return null;
            } else {
                return new String(bytes);
            }
        }

        private String readBinaryStream(final int i)
                throws IOException, SQLException {
            if (m_streamException[i]) {
                return readString(i);
            }
            try {
                InputStream is = m_result.getBinaryStream(i + 1);
                if (wasNull() || is == null) {
                    return null;
                } else {
                    InputStreamReader reader =
                    // TODO: using default encoding
                            new InputStreamReader(is);
                    StringWriter writer = new StringWriter();
                    FileUtil.copy(reader, writer);
                    reader.close();
                    writer.close();
                    return writer.toString();
                }
            } catch (SQLException sql) {
                m_streamException[i] = true;
                handlerException("Can't read from binary stream, "
                        + "trying to read string... ", sql);
                return readString(i);
            }
        }

        private String readNString(final int i) throws SQLException {
            String str = m_result.getNString(i + 1);
            if (wasNull() || str == null) {
                return null;
            } else {
                return str;
            }
        }

        private DateAndTimeCell readDate(final int i) throws SQLException {
            Date date = m_result.getDate(i + 1);
            if (wasNull() || date == null) {
                return null;
            } else {
                return new DateAndTimeCell(date.getTime(), true, false, false);
            }
        }

        private DateAndTimeCell readTime(final int i) throws SQLException {
            Time time = m_result.getTime(i + 1);
            if (wasNull() || time == null) {
                return null;
            } else {
                return new DateAndTimeCell(time.getTime(), false, true, true);
            }
        }

        private DateAndTimeCell readTimestamp(final int i) throws SQLException {
            Timestamp timestamp = m_result.getTimestamp(i + 1);
            if (wasNull() || timestamp == null) {
                return null;
            } else {
                return new DateAndTimeCell(
                        timestamp.getTime(), true, true, true);
            }
        }

        private String readArray(final int i) throws SQLException {
            Array array = m_result.getArray(i + 1);
            if (wasNull() || array == null) {
                return null;
            } else {
                return array.getArray().toString();
            }
        }

        private String readRef(final int i) throws SQLException {
            Ref ref = m_result.getRef(i + 1);
            if (wasNull() || ref == null) {
                return null;
            } else {
                return ref.getObject().toString();
            }
        }

        private String readURL(final int i) throws SQLException {
            URL url = m_result.getURL(i + 1);
            if (url == null || wasNull()) {
                return null;
            } else {
                return url.toString();
            }
        }

        private String readObject(final int i) throws SQLException {
            Object o = m_result.getObject(i + 1);
            if (o == null || wasNull()) {
                return null;
            } else {
                return o.toString();
            }
        }

        private boolean wasNull() {
            try {
                return m_result.wasNull();
            } catch (SQLException sqle) {
                handlerException("SQL Exception: ", sqle);
                return true;
            }
        }

        private void handlerException(final String msg, final Exception e) {
            if (m_hasExceptionReported) {
                LOGGER.debug(msg + e.getMessage(), e);
            } else {
                m_hasExceptionReported = true;
                LOGGER.error(msg + e.getMessage()
                        + " - all further errors are suppressed "
                        + "and reported on debug level only", e);
            }
        }
    }
}
