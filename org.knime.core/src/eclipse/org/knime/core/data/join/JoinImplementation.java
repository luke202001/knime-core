/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 *
 * History
 *   23.11.2009 (Heiko Hofer): created
 */
package org.knime.core.data.join;

import java.lang.management.ManagementFactory;
import java.util.LinkedList;
import java.util.List;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.knime.core.data.join.JoinSpecification.InputTable;
import org.knime.core.data.join.results.JoinResult;
import org.knime.core.data.join.results.JoinResult.OutputCombined;
import org.knime.core.data.join.results.JoinResult.OutputSplit;
import org.knime.core.data.util.memory.MemoryAlertSystem;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.CanceledExecutionException.CancelChecker;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeProgressMonitor;

/**
 * A join implementation executes the join by iterating over the provided tables and generating output rows from
 * matching input rows. Implementations differ in their speed/memory-usage tradeoff and also in the type of join
 * predicates they support, e.g., {@link NestedLoopJoin} supports only equijoins.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 * TODO remove javadoc warning to fix serious ones.
 * @since 4.2
 */
@SuppressWarnings("javadoc")
public abstract class JoinImplementation {

    /** This can change and the auxiliary data structures should be updated accordingly. */
    protected final JoinSpecification m_joinSpecification;

    /** Logger to print debug info to. */
    static final NodeLogger LOGGER = NodeLogger.getLogger(JoinImplementation.class);

    protected JoinProgressMonitor m_progress;

    protected final ExecutionContext m_exec;

    private boolean m_enableHiliting = false;

    protected int m_maxOpenFiles = 300;

    double m_memoryLimitFraction = 0.9;

    protected BufferedDataTable m_left;

    protected BufferedDataTable m_right;

    /**
     * @throws InvalidSettingsException
     */
    protected JoinImplementation(final JoinSpecification settings, final ExecutionContext exec) {
        m_exec = exec;
        m_joinSpecification = settings;
        m_left = settings.getSettings(InputTable.LEFT).getTable()
            .orElseThrow(() -> new IllegalStateException("No left input table provided."));
        m_right = settings.getSettings(InputTable.RIGHT).getTable()
            .orElseThrow(() -> new IllegalStateException("No right input table provided."));
        m_progress = new JoinProgressMonitor();
    }

    public abstract JoinResult<OutputCombined> joinOutputCombined() throws CanceledExecutionException, InvalidSettingsException;

    public abstract JoinResult<OutputSplit> joinOutputSplit() throws CanceledExecutionException, InvalidSettingsException;

    /**
     * @return the logical aspects of the join, such as whether to output unmatched rows, etc.
     * @see JoinSpecification
     */
    public JoinSpecification getJoinSpecification() {
        return m_joinSpecification;
    }

    /**
     * @return the enableHiliting
     */
    public boolean isEnableHiliting() {
        return m_enableHiliting;
    }

    /**
     * @return the maxOpenFiles
     */
    public int getMaxOpenFiles() {
        return m_maxOpenFiles;
    }

    /**
     * @return the memoryLimitFraction
     */
    public double getMemoryLimitFraction() {
        return m_memoryLimitFraction;
    }

    /**
     * @param maxOpenFiles the maximum number of intermediate files to use during joining.
     */
    public JoinImplementation setMaxOpenFiles(final int maxOpenFiles) {
        m_maxOpenFiles = maxOpenFiles;
        return this;
    }

    /**
     * @param memoryLimitFraction the memoryLimitFraction to set
     */
    public JoinImplementation setMemoryLimitFraction(final double memoryLimitFraction) {
        m_memoryLimitFraction = memoryLimitFraction;
        return this;
    }

    /**
     * @param enableHiliting the enableHiliting to set
     */
    public JoinImplementation setEnableHiliting(final boolean enableHiliting) {
        m_enableHiliting = enableHiliting;
        return this;
    }

    /**
     * @return the progress
     */
    protected JoinProgressMonitor getProgress() {
        return m_progress;
    }

    /**
     * @param progress the progress to set
     */
    protected void setProgress(final JoinProgressMonitor progress) {
        this.m_progress = progress;
    }

    /**
     * @return
     */
    public ExecutionContext getExecutionContext() {
        return m_exec;
    }

    class JoinProgressMonitor implements HybridHashJoinMXBean{

        private final NodeProgressMonitor m_monitor;

        private final CancelChecker m_canceled;

        /**
         * Number of elapsed milliseconds before checking memory utilization again in {@link #isMemoryLow(long)}
         */
        long m_checkBackAfter = 0;

        private boolean m_recommendedUsingMoreMemory = false;

        /** For testing only: if true, triggers flushing to disk behavior in the hybrid hash join */
        boolean m_assumeMemoryLow = false;

        /** For testing only: if true, triggers flushing to disk behavior in the hybrid hash join */
        int m_desiredPartitionsOnDisk = 0;

        // For bean inspection only
        int m_numBuckets;

        int m_numHashPartitionsOnDisk = 0;

        long m_probeRowsProcessedInMemory = 0;

        long m_probeRowsProcessedFromDisk = 0;

        /** @see #getProbeBucketSizes() */
        long[] m_probeBucketSizes = new long[0];

        /** @see #getHashBucketSizes() */
        long[] m_hashBucketSizes = new long[0];

        double m_probeBucketSizeAverage;

        double m_hashBucketSizeAverage;

        double m_probeBucketSizeCoV;

        double m_hashBucketSizeCoV;

        public JoinProgressMonitor() {

            m_monitor = m_exec.getProgressMonitor();
            m_canceled = CancelChecker.checkCanceledPeriodically(m_exec);

            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            try {
                ObjectName name = new ObjectName("org.knime.base.node.preproc.joiner3.jmx:type=HybridHashJoin");
                try {
                    server.unregisterMBean(name);
                } catch (InstanceNotFoundException e) {
                }
                server.registerMBean(this, name);
            } catch (Exception e) { System.err.println(e.getMessage()); }
        }

        /**
         * Polling the state of the memory system is an expensive operation that should be performed only every now and then.
         * @param checkBackInMs ignore queries after this one for the next x milliseconds
         * @return whether heap space is running out
         */
        public boolean isMemoryLow(final long checkBackInMs) {
            if (System.currentTimeMillis() >= m_checkBackAfter ||
                    getNumPartitionsOnDisk() < m_desiredPartitionsOnDisk) { // ignore throttling for testing with desiredPartitionsOnDisk
                m_checkBackAfter = System.currentTimeMillis() + checkBackInMs;
                return MemoryAlertSystem.getUsage() > m_memoryLimitFraction ||
                    m_assumeMemoryLow ||
                    // when not enough partitions are on disk yet
                    getNumPartitionsOnDisk() < m_desiredPartitionsOnDisk;
            }
            return false;
        }

        public void reset() {
            m_recommendedUsingMoreMemory = false;
            m_monitor.setProgress(0);

            m_numHashPartitionsOnDisk = 0;
            m_probeRowsProcessedInMemory = 0;
            m_probeRowsProcessedFromDisk = 0;
        }

        CancelChecker getCancelChecker() {
            return m_canceled;
        }

        boolean isCanceled() {
            try {
                m_canceled.checkCanceled();
            } catch (CanceledExecutionException ex) {
                return true;
            }
            return false;
        }

        /**
         * @param progress between 0 and 1
         * @throws CanceledExecutionException
         */
        public void setProgressAndCheckCanceled(final double progress) throws CanceledExecutionException {
            m_canceled.checkCanceled();
            m_monitor.setProgress(progress);
        }

        /** inform this object about the actual number of partition pairs that are flushed to disk */
        public void setNumPartitionsOnDisk(final int n) {
            if (!m_recommendedUsingMoreMemory && n > 0) {
                LOGGER.warn("Run KNIME with more main memory to speed up the join. "
                    + "The smaller table is too large to execute the join in memory. ");
                m_recommendedUsingMoreMemory = true;
            }
            m_numHashPartitionsOnDisk = n;
        }

//        /**
//         * Compute bucket size statistics to see how well the hash function distributes groups to buckets.
//         *
//         * @param probeBuckets the number of rows in the probe table partitions on disk
//         * @param hashBucketsOnDisk the number of rows in the hash input table partitions on disk
//         */
//        public void setBucketSizes(final DiskBucket[] probeBuckets, final DiskBucket[] hashBucketsOnDisk) {
//
//            m_probeBucketSizes = Arrays.stream(probeBuckets)
//                .mapToLong(b -> b.m_workingTable.getTable().map(BufferedDataTable::size).orElse(0L)).toArray();
//            //                DescriptiveStatistics stats =
//            //                    new DescriptiveStatistics(Arrays.stream(probeBucketSizes).mapToDouble(l -> l).toArray());
//            //                probeBucketSizeAverage = stats.getMean();
//            //                probeBucketSizeCoV = stats.getStandardDeviation() / stats.getMean();
//            m_hashBucketSizes = Arrays.stream(hashBucketsOnDisk).filter(Objects::nonNull)
//                .mapToLong(b -> b.m_workingTable.getTable().map(BufferedDataTable::size).orElse(0L)).toArray();
//            //                DescriptiveStatistics stats =
//            //                    new DescriptiveStatistics(Arrays.stream(hashBucketSizes).mapToDouble(l -> l).toArray());
//            //                hashBucketSizeAverage = stats.getMean();
//            //                hashBucketSizeCoV = stats.getStandardDeviation() / stats.getMean();
//        }

        public void setMessage(final String message) { m_monitor.setMessage(message); }

        public void incProbeRowsProcessedInMemory() { m_probeRowsProcessedInMemory++; }
        public void incProbeRowsProcessedFromDisk() { m_probeRowsProcessedFromDisk++; }

        @Override public int getNumBuckets() { return m_numBuckets; }
        @Override public int getNumPartitionsOnDisk() { return m_numHashPartitionsOnDisk; }
        @Override public long[] getProbeBucketSizes() { return m_probeBucketSizes; }
        @Override public long[] getHashBucketSizes() { return m_hashBucketSizes; }
        @Override public double getAverageProbeBucketSize() { return m_probeBucketSizeAverage; }
        @Override public double getAverageHashBucketSize() { return m_hashBucketSizeAverage; }
        @Override public double getProbeBucketSizeCoV() { return m_probeBucketSizeCoV; }
        @Override public double getHashBucketSizeCoV() { return m_hashBucketSizeCoV; }


        @Override public long getProbeRowsProcessedInMemory() { return m_probeRowsProcessedInMemory; }
        /** The number of times a probe row was processed and the hash input counterpart was on disk */
        @Override public long getProbeRowsProcessedFromDisk() { return m_probeRowsProcessedFromDisk; }

        @Override public void setDesiredPartitionsOnDisk(final int n) { m_desiredPartitionsOnDisk = n; }
        @Override public void setAssumeMemoryLow(final boolean assume) { m_assumeMemoryLow = assume; }

    }

    /**
     * Allows filling the heap to test low memory situations.
     * @author Carl Witt, KNIME AG, Zurich, Switzerland
     */
    public static class FillMemoryForTesting implements FillMemoryForTestingMXBean {

        static {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            try {
                ObjectName name = new ObjectName("org.knime.base.node.preproc.joiner3.jmx:type=MemoryFiller");
                try {
                    server.unregisterMBean(name);
                } catch (InstanceNotFoundException e) {
                }
                server.registerMBean(new FillMemoryForTesting(), name);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }

        List<double[]> memoryConsumer = new LinkedList<>();

        @Override
        public void fillHeap(final float targetPercentage) {
            while (MemoryAlertSystem.getUsage() < targetPercentage) {
                // allocate 50 MB
                memoryConsumer.add(new double[6_250_000]);
            }
        }

        @Override
        public void releaseTestAllocations() {
            memoryConsumer.clear();
        }
    }

    public static interface FillMemoryForTestingMXBean{
        void fillHeap(float targetPercentage);
        void releaseTestAllocations();
    }

    public interface HybridHashJoinMXBean {
        /** @return the number of partition pairs that have been flushed to disk. */
        int getNumPartitionsOnDisk();

        /**
         * @param n set a goal for the number of partition pairs that are handled via disk. As long as
         *            {@link #getNumPartitionsOnDisk()} is smaller that that, the monitor will report low memory
         *            condition.
         */
        void setDesiredPartitionsOnDisk(int n);
        /** @return number of rows from the probe table that have been joined with an in-memory hash index */
        long getProbeRowsProcessedInMemory();
        /** @return number of rows from the probe table that have been joined by reading their partition from disk */
        long getProbeRowsProcessedFromDisk();
        /** @return {@link DiskBackedHashPartitions#m_numPartitions} */
        int getNumBuckets();
        /** @return the number of rows in the probe table partitions on disk */
        long[] getProbeBucketSizes();
        /** @return the number of rows in the hash input table partitions on disk */
        long[] getHashBucketSizes();
        double getAverageProbeBucketSize();
        double getAverageHashBucketSize();
        double getProbeBucketSizeCoV();
        double getHashBucketSizeCoV();
        void setAssumeMemoryLow(boolean assume);
    }
}
