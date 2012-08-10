/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.runtimes.dflt.runtime.system.transaction;

import static org.apache.isis.core.commons.ensure.Ensure.ensureThatArg;
import static org.apache.isis.core.commons.ensure.Ensure.ensureThatState;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

import org.apache.log4j.Logger;

import org.apache.isis.core.commons.components.TransactionScopedComponent;
import org.apache.isis.core.commons.ensure.Ensure;
import org.apache.isis.core.commons.lang.ToString;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.ResolveState;
import org.apache.isis.runtimes.dflt.runtime.persistence.ObjectPersistenceException;
import org.apache.isis.runtimes.dflt.runtime.persistence.objectstore.transaction.CreateObjectCommand;
import org.apache.isis.runtimes.dflt.runtime.persistence.objectstore.transaction.DestroyObjectCommand;
import org.apache.isis.runtimes.dflt.runtime.persistence.objectstore.transaction.PersistenceCommand;
import org.apache.isis.runtimes.dflt.runtime.persistence.objectstore.transaction.SaveObjectCommand;
import org.apache.isis.runtimes.dflt.runtime.persistence.objectstore.transaction.TransactionalResource;

/**
 * Used by the {@link IsisTransactionManager} to captures a set of changes to be
 * applied.
 * 
 * <p>
 * The protocol by which the {@link IsisTransactionManager} interacts and uses
 * the {@link IsisTransaction} is not API, because different approaches are
 * used. For the server-side <tt>ObjectStoreTransactionManager</tt>, each object
 * is wrapped in a command generated by the underlying <tt>ObjectStore</tt>. for
 * the client-side <tt>ClientSideTransactionManager</tt>, the transaction simply
 * holds a set of events.
 * 
 * <p>
 * Note that methods such as <tt>flush()</tt>, <tt>commit()</tt> and
 * <tt>abort()</tt> are not part of the API. The place to control transactions
 * is through the {@link IsisTransactionManager transaction manager}, because
 * some implementations may support nesting and such like. It is also the job of
 * the {@link IsisTransactionManager} to ensure that the underlying persistence
 * mechanism (for example, the <tt>ObjectAdapterStore</tt>) is also committed.
 */
public class IsisTransaction implements TransactionScopedComponent {

    public static enum State {
        /**
         * Started, still in progress.
         * 
         * <p>
         * May {@link IsisTransaction#flush() flush},
         * {@link IsisTransaction#commit() commit} or
         * {@link IsisTransaction#abort() abort}.
         */
        IN_PROGRESS(true, true, true, false),
        /**
         * Started, but has hit an exception.
         * 
         * <p>
         * May not {@link IsisTransaction#flush()} or
         * {@link IsisTransaction#commit() commit} (will throw an
         * {@link IllegalStateException}), but can only
         * {@link IsisTransaction#abort() abort}.
         * 
         * <p>
         * Similar to <tt>setRollbackOnly</tt> in EJBs.
         */
        MUST_ABORT(false, false, true, false),
        /**
         * Completed, having successfully committed.
         * 
         * <p>
         * May not {@link IsisTransaction#flush()} or
         * {@link IsisTransaction#abort() abort} or
         * {@link IsisTransaction#commit() commit} (will throw
         * {@link IllegalStateException}).
         */
        COMMITTED(false, false, false, true),
        /**
         * Completed, having aborted.
         * 
         * <p>
         * May not {@link IsisTransaction#flush()},
         * {@link IsisTransaction#commit() commit} or
         * {@link IsisTransaction#abort() abort} (will throw
         * {@link IllegalStateException}).
         */
        ABORTED(false, false, false, true);

        private final boolean canFlush;
        private final boolean canCommit;
        private final boolean canAbort;
        private final boolean isComplete;

        private State(final boolean canFlush, final boolean canCommit, final boolean canAbort, final boolean isComplete) {
            this.canFlush = canFlush;
            this.canCommit = canCommit;
            this.canAbort = canAbort;
            this.isComplete = isComplete;
        }

        /**
         * Whether it is valid to {@link IsisTransaction#flush() flush} this
         * {@link IsisTransaction transaction}.
         */
        public boolean canFlush() {
            return canFlush;
        }

        /**
         * Whether it is valid to {@link IsisTransaction#commit() commit} this
         * {@link IsisTransaction transaction}.
         */
        public boolean canCommit() {
            return canCommit;
        }

        /**
         * Whether it is valid to {@link IsisTransaction#abort() abort} this
         * {@link IsisTransaction transaction}.
         */
        public boolean canAbort() {
            return canAbort;
        }

        /**
         * Whether the {@link IsisTransaction transaction} is complete (and so a
         * new one can be started).
         */
        public boolean isComplete() {
            return isComplete;
        }

    }


    private static final Logger LOG = Logger.getLogger(IsisTransaction.class);


    private final TransactionalResource objectStore;
    private final List<PersistenceCommand> commands = Lists.newArrayList();
    private final IsisTransactionManager transactionManager;
    private final MessageBroker messageBroker;
    private final UpdateNotifier updateNotifier;
    private final List<ObjectPersistenceException> exceptions = Lists.newArrayList();

    private State state;

    private RuntimeException cause;

    public IsisTransaction(final IsisTransactionManager transactionManager, final MessageBroker messageBroker, final UpdateNotifier updateNotifier, final TransactionalResource objectStore) {
        
        ensureThatArg(transactionManager, is(not(nullValue())), "transaction manager is required");
        ensureThatArg(messageBroker, is(not(nullValue())), "message broker is required");
        ensureThatArg(updateNotifier, is(not(nullValue())), "update notifier is required");

        this.transactionManager = transactionManager;
        this.messageBroker = messageBroker;
        this.updateNotifier = updateNotifier;

        this.state = State.IN_PROGRESS;

        this.objectStore = objectStore;
        if (LOG.isDebugEnabled()) {
            LOG.debug("new transaction " + this);
        }
    }

    // ////////////////////////////////////////////////////////////////
    // State
    // ////////////////////////////////////////////////////////////////

    public State getState() {
        return state;
    }

    private void setState(final State state) {
        this.state = state;
    }

    
    // //////////////////////////////////////////////////////////
    // Commands
    // //////////////////////////////////////////////////////////

    /**
     * Add the non-null command to the list of commands to execute at the end of
     * the transaction.
     */
    public void addCommand(final PersistenceCommand command) {
        if (command == null) {
            return;
        }

        final ObjectAdapter onObject = command.onAdapter();

        // Saves are ignored when preceded by another save, or a delete
        if (command instanceof SaveObjectCommand) {
            if (alreadyHasCreate(onObject) || alreadyHasSave(onObject)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ignored command as object already created/saved" + command);
                }
                return;
            }

            if (alreadyHasDestroy(onObject)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ignored command " + command + " as object no longer exists");
                }
                return;
            }
        }

        // Destroys are ignored when preceded by a create, or another destroy
        if (command instanceof DestroyObjectCommand) {
            if (alreadyHasCreate(onObject)) {
                removeCreate(onObject);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ignored both create and destroy command " + command);
                }
                return;
            }

            if (alreadyHasSave(onObject)) {
                removeSave(onObject);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("removed prior save command " + command);
                }
            }

            if (alreadyHasDestroy(onObject)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ignored command " + command + " as command already recorded");
                }
                return;
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("add command " + command);
        }
        commands.add(command);
    }



    /////////////////////////////////////////////////////////////////////////
    // for worm-hole handling of exceptions
    /////////////////////////////////////////////////////////////////////////

    public void ensureExceptionsListIsEmpty() {
        Ensure.ensureThatArg(exceptions.isEmpty(), is(true), "exceptions list is not empty");
    }

    public void addException(ObjectPersistenceException exception) {
        exceptions.add(exception);
    }
    
    public List<ObjectPersistenceException> getExceptionsIfAny() {
        return Collections.unmodifiableList(exceptions);
    }

    

    // ////////////////////////////////////////////////////////////////
    // flush
    // ////////////////////////////////////////////////////////////////

    public final void flush() {
        ensureThatState(getState().canFlush(), is(true), "state is: " + getState());
        if (LOG.isDebugEnabled()) {
            LOG.debug("flush transaction " + this);
        }

        try {
            doFlush();
        } catch (final RuntimeException ex) {
            setState(State.MUST_ABORT);
            setAbortCause(ex);
            throw ex;
        }
    }

    /**
     * Mandatory hook method for subclasses to persist all pending changes.
     * 
     * <p>
     * Called by both {@link #commit()} and by {@link #flush()}:
     * <table>
     * <tr>
     * <th>called from</th>
     * <th>next {@link #getState() state} if ok</th>
     * <th>next {@link #getState() state} if exception</th>
     * </tr>
     * <tr>
     * <td>{@link #commit()}</td>
     * <td>{@link State#COMMITTED}</td>
     * <td>{@link State#ABORTED}</td>
     * </tr>
     * <tr>
     * <td>{@link #flush()}</td>
     * <td>{@link State#IN_PROGRESS}</td>
     * <td>{@link State#MUST_ABORT}</td>
     * </tr>
     * </table>
     */
    private void doFlush() {

        if (commands.size() > 0) {
            objectStore.execute(Collections.unmodifiableList(commands));

            for (final PersistenceCommand command : commands) {
                if (command instanceof DestroyObjectCommand) {
                    final ObjectAdapter adapter = command.onAdapter();
                    adapter.setVersion(null);
                    adapter.changeState(ResolveState.DESTROYED);
                }
            }
            commands.clear();
        }
    }


    
    // ////////////////////////////////////////////////////////////////
    // commit
    // ////////////////////////////////////////////////////////////////

    public final void commit() {
        ensureThatState(getState().canCommit(), is(true), "state is: " + getState());
        ensureThatState(exceptions.isEmpty(), is(true), "cannot commit: " + exceptions.size() + " exceptions have been raised");

        if (LOG.isDebugEnabled()) {
            LOG.debug("commit transaction " + this);
        }

        if (getState() == State.COMMITTED) {
            if (LOG.isInfoEnabled()) {
                LOG.info("already committed; ignoring");
            }
            return;
        }
        
        try {
            doFlush();
            setState(State.COMMITTED);
        } catch (final RuntimeException ex) {
            setAbortCause(ex);
            throw ex;
        }
    }

    
    // ////////////////////////////////////////////////////////////////
    // abort
    // ////////////////////////////////////////////////////////////////

    public final void abort() {
        ensureThatState(getState().canAbort(), is(true), "state is: " + getState());
        if (LOG.isInfoEnabled()) {
            LOG.info("abort transaction " + this);
        }

        setState(State.ABORTED);
    }

    

    protected void setAbortCause(final RuntimeException cause) {
        this.cause = cause;
    }

    /**
     * The cause (if any) for the transaction being aborted.
     * 
     * <p>
     * Will be set if an exception is thrown while {@link #flush() flush}ing,
     * {@link #commit() commit}ting or {@link #abort() abort}ing.
     */
    public RuntimeException getAbortCause() {
        return cause;
    }

    
    
    // //////////////////////////////////////////////////////////
    // Helpers
    // //////////////////////////////////////////////////////////

    private boolean alreadyHasCommand(final Class<?> commandClass, final ObjectAdapter onObject) {
        return getCommand(commandClass, onObject) != null;
    }

    private boolean alreadyHasCreate(final ObjectAdapter onObject) {
        return alreadyHasCommand(CreateObjectCommand.class, onObject);
    }

    private boolean alreadyHasDestroy(final ObjectAdapter onObject) {
        return alreadyHasCommand(DestroyObjectCommand.class, onObject);
    }

    private boolean alreadyHasSave(final ObjectAdapter onObject) {
        return alreadyHasCommand(SaveObjectCommand.class, onObject);
    }

    private PersistenceCommand getCommand(final Class<?> commandClass, final ObjectAdapter onObject) {
        for (final PersistenceCommand command : commands) {
            if (command.onAdapter().equals(onObject)) {
                if (commandClass.isAssignableFrom(command.getClass())) {
                    return command;
                }
            }
        }
        return null;
    }

    private void removeCommand(final Class<?> commandClass, final ObjectAdapter onObject) {
        final PersistenceCommand toDelete = getCommand(commandClass, onObject);
        commands.remove(toDelete);
    }

    private void removeCreate(final ObjectAdapter onObject) {
        removeCommand(CreateObjectCommand.class, onObject);
    }

    private void removeSave(final ObjectAdapter onObject) {
        removeCommand(SaveObjectCommand.class, onObject);
    }

    // ////////////////////////////////////////////////////////////////
    // toString
    // ////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return appendTo(new ToString(this)).toString();
    }

    protected ToString appendTo(final ToString str) {
        str.append("state", state);
        str.append("commands", commands.size());
        return str;
    }


    // ////////////////////////////////////////////////////////////////
    // Depenendencies (from constructor)
    // ////////////////////////////////////////////////////////////////

    /**
     * The owning {@link IsisTransactionManager transaction manager}.
     * 
     * <p>
     * Injected in constructor
     */
    public IsisTransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * The {@link MessageBroker} for this transaction.
     * 
     * <p>
     * Injected in constructor
     */
    public MessageBroker getMessageBroker() {
        return messageBroker;
    }

    /**
     * The {@link UpdateNotifier} for this transaction.
     * 
     * <p>
     * Injected in constructor
     */
    public UpdateNotifier getUpdateNotifier() {
        return updateNotifier;
    }

    
}
