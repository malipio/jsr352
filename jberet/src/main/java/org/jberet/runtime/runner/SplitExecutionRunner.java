/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jberet.runtime.runner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.StepExecution;

import org.jberet.job.Flow;
import org.jberet.job.Split;
import org.jberet.runtime.FlowExecutionImpl;
import org.jberet.runtime.context.AbstractContext;
import org.jberet.runtime.context.SplitContextImpl;

import static org.jberet.util.BatchLogger.LOGGER;

public final class SplitExecutionRunner extends CompositeExecutionRunner<SplitContextImpl> implements Runnable {
    private static final long SPLIT_FLOW_TIMEOUT_SECONDS = 300;
    private Split split;

    public SplitExecutionRunner(SplitContextImpl splitContext, CompositeExecutionRunner enclosingRunner) {
        super(splitContext, enclosingRunner);
        this.split = splitContext.getSplit();
    }

    @Override
    protected List<?> getJobElements() {
        return split.getFlow();
    }

    @Override
    public void run() {
        batchContext.setBatchStatus(BatchStatus.STARTED);
        List<Flow> flows = split.getFlow();
        CountDownLatch latch = new CountDownLatch(flows.size());
        try {
            for (Flow f : flows) {
                runFlow(f, latch);
            }
            latch.await(SPLIT_FLOW_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            //check FlowResults from each flow
            List<FlowExecutionImpl> fes = batchContext.getFlowExecutions();
            for (int i = 0; i < fes.size(); i++) {
                if (fes.get(i).getBatchStatus().equals(BatchStatus.FAILED)) {
                    batchContext.setBatchStatus(BatchStatus.FAILED);
                    for (AbstractContext c : batchContext.getOuterContexts()) {
                        c.setBatchStatus(BatchStatus.FAILED);
                    }
                    break;
                }
            }
            if (batchContext.getBatchStatus().equals(BatchStatus.STARTED)) {
                batchContext.setBatchStatus(BatchStatus.COMPLETED);
            }
        } catch (Throwable e) {
            LOGGER.failToRunJob(e, jobContext.getJobName(), split.getId(), split);
            for (AbstractContext c : batchContext.getOuterContexts()) {
                c.setBatchStatus(BatchStatus.FAILED);
            }
        }

        if (batchContext.getBatchStatus() == BatchStatus.COMPLETED) {
            String next = split.getNext();  //split has no transition elements
            if (next != null) {
                //the last StepExecution of each flow is needed if the next element after this split is a decision
                List<FlowExecutionImpl> fes = batchContext.getFlowExecutions();
                StepExecution[] stepExecutions = new StepExecution[fes.size()];
                for (int i = 0; i < fes.size(); i++) {
                    stepExecutions[i] = fes.get(i).getLastStepExecution();
                }
                enclosingRunner.runJobElement(next, stepExecutions);
            }
        }
    }

}